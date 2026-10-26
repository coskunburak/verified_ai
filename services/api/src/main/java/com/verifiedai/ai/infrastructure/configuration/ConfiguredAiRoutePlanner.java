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
import com.verifiedai.ai.application.PromptRegistryValidator;
import com.verifiedai.ai.application.AiRouteEligibility;
import com.verifiedai.ai.application.AiRouteSelector;
import com.verifiedai.ai.application.AiProviderHealth;
import com.verifiedai.ai.application.AiEntitlementResolver;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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
        EXECUTABLE_CAPABILITIES =
        EnumSet.of(
            AiCapability.VISION_PARSE,
            AiCapability.PROBLEM_NORMALIZE,
            AiCapability.PROBLEM_CLASSIFY, AiCapability.SOLVE
        );

    private final Map<AiCapability, AiRoutePolicy> policies;

    private final AiRoutingProperties routing;
    private final AiProviderHealth health;
    private final AiEntitlementResolver entitlement;
    private final Clock clock;
    private final List<AiRouteEligibility> catalog;

    ConfiguredAiRoutePlanner(AiVisionRecognitionProperties vision, AiProblemParserProperties parser,
        AiProblemClassifierProperties classifier, AiRouteRegistryProperties generic,
        AiCapabilityRegistry registry, PromptRegistryValidator validator, String environment) {
        this(vision, parser, classifier, generic, registry, validator, environment,
            new AiRoutingProperties(null, null), AiProviderHealth.unknown(),
            AiEntitlementResolver.conservative(), Clock.systemUTC());
    }

    @Autowired
    ConfiguredAiRoutePlanner(
        AiVisionRecognitionProperties vision,
        AiProblemParserProperties parser,
        AiProblemClassifierProperties classifier,
        AiRouteRegistryProperties generic,
        AiCapabilityRegistry capabilityRegistry,
        PromptRegistryValidator promptRegistryValidator,
        @Value("${app.environment:local}")
        String environment,
        AiRoutingProperties routing, AiProviderHealth health, AiEntitlementResolver entitlement, Clock clock
    ) {
        this.routing = routing;
        this.health = health;
        this.entitlement = entitlement;
        this.clock = clock;
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
                    && !EXECUTABLE_CAPABILITIES
                    .contains(capability)
            ) {
                throw new IllegalStateException(
                    "AI capability "
                        + capability
                        + " is registered but cannot be enabled "
                        + "before its owning Phase 5 sprint"
                );
            }

            if (capability == AiCapability.SOLVE && configured.enabled()) {
                // No approved external model exists. A local-only test route is the sole activation path.
                boolean local = "local".equalsIgnoreCase(environment) || "test".equalsIgnoreCase(environment);
                if (!local || !generic.usageLedger().enabled() || configured.primary() == null
                    || !"LOCAL_FIXTURE".equals(configured.primary().provider())
                    || !"local-fixture-solver-v1".equals(configured.primary().model())
                    || !configured.fallbacks().isEmpty() || configured.maxCostMicros()!=0
                    || configured.releaseStage()!=AiRoutePlan.ReleaseStage.LOCAL_ONLY) {
                    throw new IllegalStateException("SOLVE activation blocked: no approved production model");
                }
            }
            if (EXECUTABLE_CAPABILITIES.contains(capability)) {
                if (!configured.enabled()) {
                    built.put(capability, AiRoutePolicy.disabled(capability));
                } else {
                    var c = configured;
                    built.put(capability, AiRoutePolicy.enabled(new AiRoutePlan(capability,
                        c.routePolicyVersion(), c.routeId(), new AiRouteTarget(c.primary().provider(), c.primary().model()),
                        c.fallbacks().stream().map(t -> new AiRouteTarget(t.provider(), t.model())).toList(),
                        c.promptId(), c.promptVersion(), c.schemaVersion(), c.timeout(), c.maxAttempts(),
                        c.maxResponseBytes(), c.maxCostMicros(), c.pricingVersion(), c.cachePolicy(), c.streamingPolicy(),
                        c.providerRetentionPolicy(), c.privacyRegion(), c.circuitBreakerKey(), c.qualityGateVersion(), c.releaseStage())));
                }
            }

            // Unrelated future capabilities retain their owning-sprint activation gate.
            if (
                !EXECUTABLE_CAPABILITIES
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

        promptRegistryValidator.validateEnabledRoutes(
            this.policies.values(),
            environment
        );
        List<AiRouteEligibility> entries = new ArrayList<>(routing.eligibility());
        for (AiRoutePolicy policy : built.values()) {
            if (!policy.enabled()) continue;
            AiRoutePlan plan = policy.routePlan();
            for (AiRouteTarget target : plan.allTargets()) {
                if (entries.stream().anyMatch(c -> c.capability() == plan.capability() && c.target().equals(target))) continue;
                if ("LOCAL_FIXTURE".equals(target.provider())) {
                    String fixtureModel = switch (plan.capability()) {
                        case SOLVE -> "local-fixture-solver-v1";
                        case VISION_PARSE -> "local-fixture-vision-v1";
                        case PROBLEM_NORMALIZE -> "local-fixture-problem-parser-v1";
                        case PROBLEM_CLASSIFY -> "local-fixture-problem-classifier-v1";
                        default -> throw new IllegalStateException("Unsupported fixture capability");
                    };
                    if (!fixtureModel.equals(target.model())) {
                        throw new IllegalStateException("Unknown local fixture model requires explicit eligibility");
                    }
                    entries.add(new AiRouteEligibility(plan.capability(), target, "local-fixture-only-v1", 0,
                        Duration.ofMillis(1), plan.capability() == AiCapability.SOLVE
                            ? Set.of("EVALUATE", "SIMPLIFY", "SOLVE_EQUATION", "SOLVE_INEQUALITY") : Set.of("UNKNOWN"), Set.of("UNKNOWN", "EASY", "MEDIUM", "HARD"),
                        Set.of("FREE", "PRO", "PRO_PLUS"), plan.privacyRegion(), plan.providerRetentionPolicy(), true));
                } else if (!"UNAVAILABLE".equals(target.provider())) {
                    throw new IllegalStateException("Enabled remote model requires explicit eligibility");
                }
            }
        }
        for (AiRouteEligibility entry : entries) {
            AiRoutePolicy policy = built.get(entry.capability());
            if (policy == null || !policy.enabled()) continue;
            AiRoutePlan plan = policy.routePlan();
            if (!plan.allTargets().contains(entry.target())
                || !entry.privacyRegion().equals(plan.privacyRegion())
                || !entry.retentionPolicy().equals(plan.providerRetentionPolicy())
                || (!"LOCAL_FIXTURE".equals(entry.target().provider())
                    && (!entry.qualityGateVersion().equals(plan.qualityGateVersion())
                        || "UNSPECIFIED".equals(entry.privacyRegion())))) {
                throw new IllegalStateException("Eligibility does not match prompt-bound route policy");
            }
        }
        this.catalog = List.copyOf(entries);
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
        return routePlan(context, null);
    }

    @Override
    public AiRoutePlan routePlan(AiRouteContext context, UUID userId) {
        AiRoutePolicy policy = policies.get(context.capability());

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

        AiRoutePlan base = policy.routePlan();
        // Preserve explicit unavailable routes as honest availability failures.
        if (base.allTargets().stream().allMatch(t -> "UNAVAILABLE".equals(t.provider()))) return base;
        String tier = userId == null || base.allTargets().stream().allMatch(t -> "LOCAL_FIXTURE".equals(t.provider()) || "UNAVAILABLE".equals(t.provider()))
            ? "FREE" : entitlement.tier(userId);
        var selected = new AiRouteSelector().select(context, tier, catalog.stream()
            .filter(c -> base.allTargets().contains(c.target()) && c.latencyAllowance().compareTo(base.timeout()) <= 0).toList(), health, clock.instant(), routing.healthMaxAge());
        long budget = context.maxCostMicros() == null ? base.maxCostMicros()
            : Math.min(base.maxCostMicros(), context.maxCostMicros());
        var affordable = selected.stream().filter(c -> c.attemptCeilingMicros() <= budget).toList();
        if (affordable.isEmpty()) throw new AiProviderException(AiProviderFailureClass.BUDGET_EXCEEDED, false, "AI request budget exhausted");
        Duration timeout = context.latencyBudget() == null || base.timeout().compareTo(context.latencyBudget()) <= 0
            ? base.timeout() : context.latencyBudget();
        return base.selected(affordable.stream().map(AiRouteEligibility::target).toList(), timeout,
            budget, affordable.getFirst().qualityGateVersion());
    }

    @Override
    public Map<AiCapability, AiRoutePolicy> policies() {
        return policies;
    }

    @Override
    public long attemptCeiling(AiRoutePlan plan, AiRouteTarget target) {
        if ("UNAVAILABLE".equals(target.provider())) return 0;
        return catalog.stream().filter(c -> c.capability() == plan.capability() && c.target().equals(target))
            .findFirst().orElseThrow(() -> new AiProviderException(AiProviderFailureClass.POLICY_BLOCKED,
                false, "Unknown model eligibility")).attemptCeilingMicros();
    }

    @Override
    public void checkAttempt(AiRouteContext context, AiRoutePlan plan, AiRouteTarget target) {
        if (!"LOCAL_FIXTURE".equals(target.provider()) && !"UNAVAILABLE".equals(target.provider())
            && !health.snapshot(target).usable(clock.instant(), routing.healthMaxAge())) {
            throw new AiProviderException(AiProviderFailureClass.PROVIDER_UNAVAILABLE, true, "Fresh provider health unavailable");
        }
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
            "production".equalsIgnoreCase(environment == null ? "" : environment.trim())
                || "prod".equalsIgnoreCase(environment == null ? "" : environment.trim());

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
