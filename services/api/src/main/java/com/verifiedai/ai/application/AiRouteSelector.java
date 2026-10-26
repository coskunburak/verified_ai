package com.verifiedai.ai.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Restrictions are intersected; ties are stable, and no rejected route becomes a fallback. */
public final class AiRouteSelector {
    private static final Set<AiCapability> EXECUTABLE = Set.of(
        AiCapability.VISION_PARSE, AiCapability.PROBLEM_NORMALIZE, AiCapability.PROBLEM_CLASSIFY, AiCapability.SOLVE);

    public List<AiRouteEligibility> select(AiRouteContext context, String trustedTier,
        List<AiRouteEligibility> candidates, AiProviderHealth health, Instant now, Duration healthMaxAge) {
        if (!EXECUTABLE.contains(context.capability())
            || (context.capability() == AiCapability.SOLVE
                ? context.routeRole() != AiRouteContext.RouteRole.PRIMARY_SOLVER
                : context.routeRole() != AiRouteContext.RouteRole.DEFAULT)) {
            throw blocked(AiProviderFailureClass.CONFIGURATION_DISABLED);
        }
        if (Boolean.TRUE.equals(context.parserReviewRequired())
            || (context.classificationConfidence() != null && context.classificationConfidence() < 0.8)
            || (context.recognitionQualityRisk() != null && !"LOW".equals(context.recognitionQualityRisk()))) {
            throw blocked(AiProviderFailureClass.REVIEW_REQUIRED);
        }
        if (context.canonicalProblemType() != null && !Set.of("ARITHMETIC_EXPRESSION", "ALGEBRAIC_EXPRESSION",
            "EQUATION", "INEQUALITY").contains(context.canonicalProblemType())) {
            throw blocked(AiProviderFailureClass.UNSUPPORTED_PAYLOAD);
        }
        String task = context.taskType() == null ? "UNKNOWN" : context.taskType();
        String difficulty = context.difficulty() == null ? "UNKNOWN" : context.difficulty();
        List<AiRouteEligibility> safe = candidates.stream()
            .filter(c -> c.approved() && c.capability() == context.capability())
            .filter(c -> c.taskTypes().contains(task) && c.difficulties().contains(difficulty))
            .filter(c -> c.entitlementTiers().contains(trustedTier))
            .filter(c -> context.privacyRegion() == null || context.privacyRegion().equals(c.privacyRegion()))
            .filter(c -> context.privacyRetentionConstraint() == null
                || context.privacyRetentionConstraint().equals(c.retentionPolicy())
                || ("NO_TRAINING_BY_DEFAULT".equals(context.privacyRetentionConstraint())
                    && "ZERO_RETENTION".equals(c.retentionPolicy())))
            .filter(c -> context.latencyBudget() == null || c.latencyAllowance().compareTo(context.latencyBudget()) <= 0)
            .filter(c -> "LOCAL_FIXTURE".equals(c.target().provider())
                || health.snapshot(c.target()).usable(now, healthMaxAge))
            .sorted(Comparator.comparingLong(AiRouteEligibility::attemptCeilingMicros)
                .thenComparing(c -> c.target().provider()).thenComparing(c -> c.target().model()))
            .toList();
        if (safe.isEmpty()) {
            throw blocked(AiProviderFailureClass.POLICY_BLOCKED);
        }
        List<AiRouteEligibility> affordable = safe.stream()
            .filter(c -> context.maxCostMicros() == null || c.attemptCeilingMicros() <= context.maxCostMicros())
            .toList();
        if (affordable.isEmpty()) {
            throw blocked(AiProviderFailureClass.BUDGET_EXCEEDED);
        }
        return affordable;
    }

    private static AiProviderException blocked(AiProviderFailureClass failure) {
        return new AiProviderException(failure, false, "AI routing policy rejected request");
    }
}
