package com.verifiedai.ai.application;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiRouteSelectorTest {
    private final Instant now = Instant.parse("2026-10-26T00:00:00Z");
    private final AiProviderHealth healthy = t -> new AiProviderHealth.Snapshot(AiProviderHealth.State.CLOSED, now, "server-monitor-v1");

    @Test
    void cheapestEligibleRouteWinsDeterministically() {
        var economy = candidate("economy-v1", 10, Set.of("UNKNOWN", "EASY"), Set.of("FREE"));
        var strong = candidate("strong-v1", 50, Set.of("UNKNOWN", "EASY", "HARD"), Set.of("FREE", "PRO"));
        assertThat(select(context(null, "PRO", null, null), "FREE", List.of(strong, economy), healthy))
            .containsExactly(economy, strong);
        assertThat(select(context(null, null, null, null), "FREE", List.of(economy, strong), healthy))
            .containsExactly(economy, strong);
    }

    @Test
    void difficultyDoesNotAuthorizePremiumAndUntrustedTierCannotUpgrade() {
        var premium = candidate("strong-v1", 50, Set.of("HARD"), Set.of("PRO"));
        assertThatThrownBy(() -> select(context("HARD", "PRO", null, null), "FREE", List.of(premium), healthy))
            .isInstanceOf(AiProviderException.class);
        assertThat(select(context("HARD", "FREE", null, null), "PRO", List.of(premium), healthy)).containsExactly(premium);
    }

    @Test
    void privacyBudgetAndFreshnessAreIntersected() {
        var route = candidate("economy-v1", 10, Set.of("UNKNOWN"), Set.of("FREE"));
        for (var state : List.of(AiProviderHealth.State.OPEN, AiProviderHealth.State.HALF_OPEN, AiProviderHealth.State.UNKNOWN)) {
            assertThatThrownBy(() -> select(context(null, null, null, null), "FREE", List.of(route),
                t -> new AiProviderHealth.Snapshot(state, now, "server"))).isInstanceOf(AiProviderException.class);
        }
        assertThatThrownBy(() -> select(context(null, null, null, null), "FREE", List.of(route),
            t -> new AiProviderHealth.Snapshot(AiProviderHealth.State.CLOSED, now.minusSeconds(31), "server")))
            .isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> select(context(null, null, "US", null), "FREE", List.of(route), healthy)).isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> select(context(null, null, "EU", 9L), "FREE", List.of(route), healthy))
            .isInstanceOf(AiProviderException.class).extracting(e -> ((AiProviderException)e).failureClass())
            .isEqualTo(AiProviderFailureClass.BUDGET_EXCEEDED);
    }

    @Test
    void unknownDifficultyDoesNotBecomeHardAndDisabledSolveCannotExecute() {
        assertThatThrownBy(() -> select(context(null, null, null, null), "FREE",
            List.of(candidate("strong-v1", 50, Set.of("HARD"), Set.of("FREE"))), healthy)).isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> select(AiRouteContext.basic(AiCapability.SOLVE, Duration.ofSeconds(1)), "PRO", List.of(), healthy))
            .isInstanceOf(AiProviderException.class).extracting(e -> ((AiProviderException)e).failureClass())
            .isEqualTo(AiProviderFailureClass.CONFIGURATION_DISABLED);
    }

    @Test
    void unapprovedModelAndUnknownTaskNeverExecute() {
        var good = candidate("v1", 10, Set.of("UNKNOWN"), Set.of("FREE"));
        var bad = new AiRouteEligibility(good.capability(), good.target(), good.qualityGateVersion(), 10, Duration.ofMillis(10),
            Set.of("UNKNOWN"), Set.of("UNKNOWN"), Set.of("FREE"), "EU", "ZERO_RETENTION", false);
        assertThatThrownBy(() -> select(context(null, null, null, null), "FREE", List.of(bad), healthy)).isInstanceOf(AiProviderException.class);
        var ctx = context(null, null, null, null);
        var unsupported = new AiRouteContext(ctx.capability(), null, "UNSUPPORTED_GEOMETRY", null, null, false, null,
            false, null, null, ctx.latencyBudget(), null, null, Map.of(), null, null, null, null);
        assertThatThrownBy(() -> select(unsupported, "FREE", List.of(good), healthy)).isInstanceOf(AiProviderException.class);
    }

    @Test
    void nanConfidenceIsRejectedRatherThanBypassingReview() {
        assertThatThrownBy(() -> new AiRouteContext(AiCapability.PROBLEM_CLASSIFY, null, null, null, Double.NaN,
            null, null, null, null, null, null, null, null, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lowConfidenceAndParserAmbiguityRequireReview() {
        var good = candidate("v1", 10, Set.of("UNKNOWN"), Set.of("FREE"));
        for (boolean review : List.of(false, true)) {
            var context = new AiRouteContext(AiCapability.VISION_PARSE, null, null, null,
                review ? 1.0 : 0.79, review, null, null, "PRO_PLUS", null, Duration.ofSeconds(1),
                null, "HEALTHY", Map.of("override", "true"), null, null, null, null);
            assertThatThrownBy(() -> select(context, "FREE", List.of(good), healthy))
                .isInstanceOf(AiProviderException.class).extracting(e -> ((AiProviderException)e).failureClass())
                .isEqualTo(AiProviderFailureClass.REVIEW_REQUIRED);
        }
    }

    @Test
    void stricterBudgetCannotIntroducePreviouslyRejectedModel() {
        var cheap = candidate("cheap-v1", 10, Set.of("UNKNOWN"), Set.of("FREE"));
        var expensive = candidate("expensive-v1", 50, Set.of("UNKNOWN"), Set.of("FREE"));
        var all = List.of(expensive, cheap);
        var broad = select(context(null, null, "EU", 100L), "FREE", all, healthy);
        for (long limit : List.of(10L, 25L, 50L)) {
            assertThat(select(context(null, null, "EU", limit), "FREE", all, healthy)).isSubsetOf(broad);
        }
    }

    private List<AiRouteEligibility> select(AiRouteContext c, String tier, List<AiRouteEligibility> entries, AiProviderHealth health) {
        return new AiRouteSelector().select(c, tier, entries, health, now, Duration.ofSeconds(30));
    }
    private AiRouteEligibility candidate(String model, long cost, Set<String> difficulty, Set<String> tiers) {
        return new AiRouteEligibility(AiCapability.VISION_PARSE, new AiRouteTarget("TEST", model), "quality-v1", cost,
            Duration.ofMillis(10), Set.of("UNKNOWN"), difficulty, tiers, "EU", "ZERO_RETENTION", true);
    }
    private AiRouteContext context(String difficulty, String claimedTier, String region, Long budget) {
        return new AiRouteContext(AiCapability.VISION_PARSE, null, null, difficulty, null, false, null, false,
            claimedTier, null, Duration.ofSeconds(1), budget, "HEALTHY", Map.of("premium", "true"), null, region, null, null);
    }
}
