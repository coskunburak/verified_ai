package com.verifiedai.ai.application;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

public final class PromptRegistryValidator {

    private final PromptRegistry promptRegistry;
    private final OutputSchemaRegistry schemaRegistry;
    private final PromptArtifactResolver promptArtifactResolver;
    private final OutputSchemaArtifactResolver schemaArtifactResolver;
    private final SolverReleaseEvidenceValidator solverReleaseEvidenceValidator;

    public PromptRegistryValidator(
        PromptRegistry promptRegistry,
        OutputSchemaRegistry schemaRegistry,
        PromptArtifactResolver promptArtifactResolver,
        OutputSchemaArtifactResolver schemaArtifactResolver
    ) {
        this(promptRegistry, schemaRegistry, promptArtifactResolver, schemaArtifactResolver,
            SolverReleaseEvidenceValidator.unavailable());
    }

    public PromptRegistryValidator(PromptRegistry promptRegistry, OutputSchemaRegistry schemaRegistry,
        PromptArtifactResolver promptArtifactResolver, OutputSchemaArtifactResolver schemaArtifactResolver,
        SolverReleaseEvidenceValidator solverReleaseEvidenceValidator) {
        this.solverReleaseEvidenceValidator = Objects.requireNonNull(solverReleaseEvidenceValidator);
        this.promptRegistry =
            Objects.requireNonNull(
                promptRegistry,
                "promptRegistry is required"
            );
        this.schemaRegistry =
            Objects.requireNonNull(
                schemaRegistry,
                "schemaRegistry is required"
            );
        this.promptArtifactResolver =
            Objects.requireNonNull(
                promptArtifactResolver,
                "promptArtifactResolver is required"
            );
        this.schemaArtifactResolver =
            Objects.requireNonNull(
                schemaArtifactResolver,
                "schemaArtifactResolver is required"
            );
    }

    public void validateEnabledRoutes(
        Collection<AiRoutePolicy> policies,
        String environment
    ) {
        Objects.requireNonNull(
            policies,
            "policies are required"
        );

        normalizedEnvironment(environment);

        for (AiRoutePolicy policy : policies) {
            if (!policy.enabled()) {
                continue;
            }

            validate(
                policy.routePlan(),
                environment
            );
        }
    }

    public void validate(
        AiRoutePlan routePlan,
        String environment
    ) {
        Objects.requireNonNull(
            routePlan,
            "routePlan is required"
        );

        try {
            PromptVersion.requireValid(
                routePlan.promptVersion()
            );
        } catch (IllegalArgumentException exception) {
            throw failure(
                PromptRegistryFailureCode
                    .AI_PROMPT_VERSION_INVALID,
                routePlan,
                "AI route prompt version is invalid"
            );
        }

        PromptRegistryEntry prompt =
            promptRegistry.require(
                routePlan.promptId(),
                routePlan.promptVersion()
            );

        if (prompt.capability() != routePlan.capability()) {
            throw failure(
                PromptRegistryFailureCode
                    .AI_PROMPT_CAPABILITY_MISMATCH,
                routePlan,
                "AI route prompt capability mismatch"
            );
        }

        if (
            !prompt.outputSchemaVersion()
                .equals(routePlan.schemaVersion())
        ) {
            throw failure(
                PromptRegistryFailureCode
                    .AI_PROMPT_SCHEMA_VERSION_MISMATCH,
                routePlan,
                "AI route schema version does not match prompt registry"
            );
        }

        if (
            !prompt.compatibleRoutePolicyVersion()
                .equals(routePlan.routePolicyVersion())
        ) {
            throw failure(
                PromptRegistryFailureCode
                    .AI_PROMPT_ROUTE_POLICY_INCOMPATIBLE,
                routePlan,
                "AI route policy version is incompatible with prompt registry"
            );
        }

        if (
            production(environment)
                && prompt.releaseStage()
                != PromptReleaseStage.PRODUCTION
        ) {
            throw failure(
                PromptRegistryFailureCode
                    .AI_PROMPT_NOT_PRODUCTION_APPROVED,
                routePlan,
                "AI route prompt is not approved for production"
            );
        }

        OutputSchemaRegistryEntry schema =
            schemaRegistry.require(
                routePlan.schemaVersion()
            );

        if (schema.semanticContract().capability() != routePlan.capability()
            || !schema.semanticContract().supports(schema.schemaVersion())) {
            throw failure(PromptRegistryFailureCode.AI_SCHEMA_CONTRACT_MISMATCH,
                routePlan, "AI output schema semantic owner does not match capability");
        }

        validateRelease(prompt, schema, routePlan, environment);
        promptArtifactResolver.load(prompt);
        schemaArtifactResolver.load(schema, prompt);
        if (routePlan.capability() == AiCapability.SOLVE
            && (prompt.releaseStage() == PromptReleaseStage.STAGING
                || prompt.releaseStage() == PromptReleaseStage.PRODUCTION)) {
            solverReleaseEvidenceValidator.validate(prompt, schema, routePlan);
        }
    }

    private static boolean production(String environment) {
        return "production".equals(normalizedEnvironment(environment));
    }

    private static String normalizedEnvironment(String environment) {
        String value = environment == null ? "" : environment.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "local", "test", "staging" -> value;
            case "prod", "production" -> "production";
            default -> throw new PromptRegistryValidationException(
                PromptRegistryFailureCode.AI_PROMPT_ENVIRONMENT_INVALID,
                "AI prompt environment must be local, test, staging, or production"
            );
        };
    }

    private static void validateRelease(
        PromptRegistryEntry prompt,
        OutputSchemaRegistryEntry schema,
        AiRoutePlan route,
        String environment
    ) {
        String env = normalizedEnvironment(environment);
        boolean local = "local".equals(env) || "test".equals(env);
        boolean eligible = switch (prompt.releaseStage()) {
            case LOCAL_ONLY, OFFLINE_EVALUATION -> local;
            case STAGING -> !"production".equals(env);
            case PRODUCTION -> true;
            default -> false;
        };
        if (!eligible || route.releaseStage() == AiRoutePlan.ReleaseStage.DISABLED
            || ("staging".equals(env)
                && route.releaseStage() != AiRoutePlan.ReleaseStage.STAGING
                && route.releaseStage() != AiRoutePlan.ReleaseStage.PRODUCTION)
            || ("production".equals(env)
                && route.releaseStage() != AiRoutePlan.ReleaseStage.PRODUCTION)
            || (route.releaseStage() == AiRoutePlan.ReleaseStage.PRODUCTION
                && prompt.releaseStage() != PromptReleaseStage.PRODUCTION)
            || (route.releaseStage() == AiRoutePlan.ReleaseStage.STAGING
                && prompt.releaseStage() != PromptReleaseStage.STAGING
                && prompt.releaseStage() != PromptReleaseStage.PRODUCTION)) {
            throw failure(PromptRegistryFailureCode.AI_PROMPT_RELEASE_STAGE_INVALID,
                route, "AI prompt or route release stage is not eligible");
        }
        if (prompt.releaseStage() == PromptReleaseStage.OFFLINE_EVALUATION) {
            // Experiment permission is not a fabricated quality approval. Model eligibility is checked by the router.
            if (route.capability() != AiCapability.SOLVE || route.releaseStage() != AiRoutePlan.ReleaseStage.LOCAL_ONLY
                || route.qualityGateVersion() == null || route.qualityGateVersion().isBlank()) {
                throw failure(PromptRegistryFailureCode.AI_PROMPT_RELEASE_STAGE_INVALID,
                    route, "Offline prompt requires an isolated governed solver evaluation");
            }
            return;
        }
        if (prompt.releaseStage() == PromptReleaseStage.LOCAL_ONLY) {
            if (route.allTargets().stream().anyMatch(target ->
                !"LOCAL_FIXTURE".equals(target.provider()))) {
                throw failure(PromptRegistryFailureCode.AI_PROMPT_RELEASE_STAGE_INVALID,
                    route, "Local-only prompt requires fixture-only targets");
            }
            return;
        }
        PromptReleaseApproval approval = prompt.approval();
        if (approval == null
            || !approval.promptSha256().equals(prompt.contentSha256())
            || !approval.schemaSha256().equals(schema.contentSha256())
            || !approval.baselineReference().equals(prompt.evaluationBaselineReference())
            || !approval.evaluationReportReference().equals(prompt.evaluationReportReference())) {
            throw failure(PromptRegistryFailureCode.AI_PROMPT_APPROVAL_MISSING,
                route, "AI release requires approval and evaluation evidence for exact artifacts");
        }
    }

    private static PromptRegistryValidationException failure(
        PromptRegistryFailureCode code,
        AiRoutePlan routePlan,
        String detail
    ) {
        return new PromptRegistryValidationException(
            code,
            detail + ": code=" + code + " capability=" + routePlan.capability()
        );
    }
}
