package com.verifiedai.ai.infrastructure.configuration;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiCapabilityDescriptor;
import com.verifiedai.ai.application.AiCapabilityRegistry;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import com.verifiedai.ai.application.AiRouteContext;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiRoutePlanner;
import com.verifiedai.ai.application.AiRoutePolicy;
import com.verifiedai.ai.application.AiRouteTarget;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class ConfiguredAiRoutePlanner
    implements AiRoutePlanner {

    private static final EnumSet<AiCapability>
        SPRINT_5_1_EXECUTABLE_CAPABILITIES =
        EnumSet.of(
            AiCapability.VISION_PARSE,
            AiCapability.PROBLEM_NORMALIZE,
            AiCapability.PROBLEM_CLASSIFY
        );

    private final Map<AiCapability, AiRoutePolicy> policies;

    ConfiguredAiRoutePlanner(
        AiVisionRecognitionProperties vision,
        AiProblemParserProperties parser,
        AiProblemClassifierProperties classifier,
        AiRouteRegistryProperties generic,
        AiCapabilityRegistry capabilityRegistry,
        @Value("${app.environment:local}")
        String environment
    ) {
        EnumMap<AiCapability, AiRoutePolicy> built =
            new EnumMap<>(AiCapability.class);

        built.put(
            AiCapability.VISION_PARSE,
            legacyPolicy(
                AiCapability.VISION_PARSE,
                vision.enabled(),
                vision.primaryProvider(),
                vision.primaryModel(),
                vision.fallbackProvider(),
                vision.fallbackModel(),
                vision.routePolicyVersion(),
                vision.routeId(),
                vision.promptId(),
                vision.promptVersion(),
                vision.schemaVersion(),
                vision.timeout(),
                vision.maxAttempts(),
                vision.maxResponseBytes(),
                vision.maxCostMicros(),
                vision.pricingVersion(),
                vision.releaseStage()
            )
        );

        built.put(
            AiCapability.PROBLEM_NORMALIZE,
            legacyPolicy(
                AiCapability.PROBLEM_NORMALIZE,
                parser.enabled(),
                parser.primaryProvider(),
                parser.primaryModel(),
                parser.fallbackProvider(),
                parser.fallbackModel(),
                parser.routePolicyVersion(),
                parser.routeId(),
                parser.promptId(),
                parser.promptVersion(),
                parser.schemaVersion(),
                parser.timeout(),
                parser.maxAttempts(),
                parser.maxResponseBytes(),
                parser.maxCostMicros(),
                parser.pricingVersion(),
                parser.releaseStage()
            )
        );

        built.put(
            AiCapability.PROBLEM_CLASSIFY,
            legacyPolicy(
                AiCapability.PROBLEM_CLASSIFY,
                classifier.enabled(),
                classifier.primaryProvider(),
                classifier.primaryModel(),
                classifier.fallbackProvider(),
                classifier.fallbackModel(),
                classifier.routePolicyVersion(),
                classifier.routeId(),
                classifier.promptId(),
                classifier.promptVersion(),
                classifier.schemaVersion(),
                classifier.timeout(),
                classifier.maxAttempts(),
                classifier.maxResponseBytes(),
                classifier.maxCostMicros(),
                classifier.pricingVersion(),
                classifier.releaseStage()
            )
        );

        for (
            Map.Entry<
                AiCapability,
                AiRouteRegistryProperties.CapabilityRoute
                > entry
            : generic.capabilities().entrySet()
        ) {
            AiCapability capability =
                entry.getKey();

            AiRouteRegistryProperties.CapabilityRoute
                configured =
                entry.getValue();

            if (
                configured.enabled()
                    && !SPRINT_5_1_EXECUTABLE_CAPABILITIES
                    .contains(capability)
            ) {
                throw new IllegalStateException(
                    "AI capability "
                        + capability
                        + " is registered but cannot be enabled "
                        + "before its owning Phase 5 sprint"
                );
            }

            /*
             * Sprint 5.1 only allows explicit disabling of future
             * capabilities. Existing capabilities continue through
             * the backwards-compatible Phase 4 configuration.
             */
            if (
                !SPRINT_5_1_EXECUTABLE_CAPABILITIES
                    .contains(capability)
            ) {
                built.put(
                    capability,
                    AiRoutePolicy.disabled(capability)
                );
            }
        }

        for (
            AiCapability capability :
            AiCapability.values()
        ) {
            built.putIfAbsent(
                capability,
                AiRoutePolicy.disabled(capability)
            );

            capabilityRegistry.require(capability);
        }

        this.policies = Map.copyOf(built);

        validateEnvironment(
            environment,
            generic,
            capabilityRegistry
        );
    }

    @Override
    public boolean enabled(
        AiCapability capability
    ) {
        AiRoutePolicy policy =
            policies.get(capability);

        return policy != null
            && policy.enabled();
    }

    @Override
    public AiRoutePlan routePlan(
        AiRouteContext context
    ) {
        AiRoutePolicy policy =
            policies.get(context.capability());

        if (
            policy == null
                || !policy.enabled()
        ) {
            throw new AiProviderException(
                AiProviderFailureClass
                    .CONFIGURATION_DISABLED,
                false,
                "AI capability is disabled"
            );
        }

        return policy.routePlan();
    }

    @Override
    public Map<AiCapability, AiRoutePolicy> policies() {
        return policies;
    }

    private AiRoutePolicy legacyPolicy(
        AiCapability capability,
        boolean enabled,
        String primaryProvider,
        String primaryModel,
        String fallbackProvider,
        String fallbackModel,
        String routePolicyVersion,
        String routeId,
        String promptId,
        String promptVersion,
        String schemaVersion,
        java.time.Duration timeout,
        int maxAttempts,
        int maxResponseBytes,
        long maxCostMicros,
        String pricingVersion,
        AiRoutePlan.ReleaseStage releaseStage
    ) {
        if (!enabled) {
            return AiRoutePolicy.disabled(
                capability
            );
        }

        List<AiRouteTarget> fallbacks =
            hasText(fallbackProvider)
                ? List.of(
                new AiRouteTarget(
                    fallbackProvider,
                    requireText(
                        fallbackModel,
                        "fallbackModel"
                    )
                )
            )
                : List.of();

        AiRoutePlan plan =
            new AiRoutePlan(
                capability,
                routePolicyVersion,
                routeId,
                new AiRouteTarget(
                    primaryProvider,
                    primaryModel
                ),
                fallbacks,
                promptId,
                promptVersion,
                schemaVersion,
                timeout,
                maxAttempts,
                maxResponseBytes,
                maxCostMicros,
                pricingVersion,
                AiRoutePlan.CachePolicy.DISABLED,
                AiRoutePlan.StreamingPolicy.DISABLED,
                "NO_TRAINING_BY_DEFAULT",
                "UNSPECIFIED",
                capability.name()
                    + ":"
                    + normalize(primaryProvider),
                null,
                releaseStage == null
                    ? AiRoutePlan.ReleaseStage.LOCAL_ONLY
                    : releaseStage
            );

        return AiRoutePolicy.enabled(plan);
    }

    private void validateEnvironment(
        String environment,
        AiRouteRegistryProperties generic,
        AiCapabilityRegistry registry
    ) {
        boolean production =
            "production".equalsIgnoreCase(
                environment
            );

        if (!production) {
            return;
        }

        for (
            AiRoutePolicy policy :
            policies.values()
        ) {
            if (!policy.enabled()) {
                continue;
            }

            AiRoutePlan plan =
                policy.routePlan();

            boolean fixtureConfigured =
                plan.allTargets()
                    .stream()
                    .anyMatch(target ->
                        "LOCAL_FIXTURE".equals(
                            target.provider()
                        )
                    );

            if (fixtureConfigured) {
                throw new IllegalStateException(
                    "LOCAL_FIXTURE AI provider cannot "
                        + "be enabled in production for "
                        + plan.capability()
                );
            }

            if (
                plan.releaseStage()
                    != AiRoutePlan.ReleaseStage.PRODUCTION
            ) {
                throw new IllegalStateException(
                    "Enabled production AI route requires "
                        + "releaseStage=PRODUCTION: "
                        + plan.capability()
                );
            }

            AiCapabilityDescriptor descriptor =
                registry.require(
                    plan.capability()
                );

            if (
                descriptor.requiresUsageLedger()
                    && !generic
                    .usageLedger()
                    .enabled()
            ) {
                throw new IllegalStateException(
                    "Production material AI capability "
                        + plan.capability()
                        + " requires usage ledger"
                );
            }
        }
    }

    private static boolean hasText(
        String value
    ) {
        return value != null
            && !value.isBlank();
    }

    private static String requireText(
        String value,
        String field
    ) {
        if (!hasText(value)) {
            throw new IllegalStateException(
                field + " is required"
            );
        }

        return value.trim();
    }

    private static String normalize(
        String value
    ) {
        return value
            .trim()
            .toUpperCase(Locale.ROOT);
    }
}
