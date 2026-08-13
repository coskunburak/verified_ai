package com.verifiedai.ai.application;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

public final class PromptRegistryValidator {

    private final PromptRegistry promptRegistry;
    private final OutputSchemaRegistry schemaRegistry;
    private final PromptArtifactResolver promptArtifactResolver;
    private final OutputSchemaArtifactResolver schemaArtifactResolver;

    public PromptRegistryValidator(
        PromptRegistry promptRegistry,
        OutputSchemaRegistry schemaRegistry,
        PromptArtifactResolver promptArtifactResolver,
        OutputSchemaArtifactResolver schemaArtifactResolver
    ) {
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

        promptArtifactResolver.load(prompt);
        schemaArtifactResolver.load(schema, prompt);
    }

    private static boolean production(
        String environment
    ) {
        if (environment == null) {
            return false;
        }

        String normalized =
            environment
                .trim()
                .toLowerCase(Locale.ROOT);

        return "production".equals(normalized)
            || "prod".equals(normalized);
    }

    private static PromptRegistryValidationException failure(
        PromptRegistryFailureCode code,
        AiRoutePlan routePlan,
        String detail
    ) {
        return new PromptRegistryValidationException(
            code,
            detail
                + ": code="
                + code
                + " capability="
                + routePlan.capability()
                + " promptId="
                + routePlan.promptId()
                + " promptVersion="
                + routePlan.promptVersion()
                + " schemaVersion="
                + routePlan.schemaVersion()
                + " routePolicyVersion="
                + routePlan.routePolicyVersion()
        );
    }
}
