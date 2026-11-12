package com.verifiedai.studyplan.spaced;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SpacedRepetitionSchedulerTest {

    private SpacedRepetitionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SpacedRepetitionScheduler();
    }

    @Test
    @DisplayName("INV-SR-001: Retention decay curve matches formula at t0, t0+H, and t->infinity")
    void testRetentionDecayFormula() {
        Instant t0 = Instant.parse("2026-11-01T10:00:00Z");
        BigDecimal initialMastery = new BigDecimal("0.8500");
        BigDecimal halfLife = new BigDecimal("4.00");

        // At t = t0, retention should equal initialMastery
        BigDecimal r0 = scheduler.computeCurrentRetention(initialMastery, halfLife, t0, t0);
        assertEquals(new BigDecimal("0.8500"), r0);

        // At t = t0 + H (4 days), retention should equal 0.05 + 0.5 * (0.85 - 0.05) = 0.4500
        Instant tHalf = t0.plus(Duration.ofDays(4));
        BigDecimal rHalf = scheduler.computeCurrentRetention(initialMastery, halfLife, t0, tHalf);
        assertEquals(new BigDecimal("0.4500"), rHalf);

        // At t -> infinity (e.g. 365 days later), retention approaches m_floor = 0.0500
        Instant tInf = t0.plus(Duration.ofDays(365));
        BigDecimal rInf = scheduler.computeCurrentRetention(initialMastery, halfLife, t0, tInf);
        assertEquals(new BigDecimal("0.0500"), rInf);
    }

    @Test
    @DisplayName("INV-SR-001: Exact due timestamp inversion where R(t_due) == 0.7200")
    void testDueTimestampInversion() {
        Instant t0 = Instant.parse("2026-11-01T10:00:00Z");
        BigDecimal initialMastery = new BigDecimal("0.9000");
        BigDecimal halfLife = new BigDecimal("6.00");

        Instant tDue = scheduler.computeDueTimestamp(initialMastery, halfLife, t0);
        assertTrue(tDue.isAfter(t0));

        // When evaluating retention at exactly tDue, retention must equal R_threshold = 0.7200
        BigDecimal retentionAtDue = scheduler.computeCurrentRetention(initialMastery, halfLife, t0, tDue);
        assertEquals(new BigDecimal("0.7200"), retentionAtDue);

        // Edge case: if initialMastery <= 0.72, due timestamp is immediately t0
        Instant immediateDue = scheduler.computeDueTimestamp(new BigDecimal("0.7100"), halfLife, t0);
        assertEquals(t0, immediateDue);
    }

    @Test
    @DisplayName("INV-SR-002: 168-hour (TRAP_IMMUNITY_7D) window calculation")
    void testTrapImmunityWindow() {
        Instant remediatedAt = Instant.parse("2026-11-01T00:00:00Z");
        Instant expectedDue = remediatedAt.plus(Duration.ofHours(168));

        Instant computedDue = scheduler.computeTrapImmunityDueTimestamp(remediatedAt);
        assertEquals(expectedDue, computedDue);

        // Test filtering boundary: 167h 59m is ineligible; 168h 00m is eligible
        Instant early = remediatedAt.plus(Duration.ofHours(167)).plus(Duration.ofMinutes(59));
        Instant exact = remediatedAt.plus(Duration.ofHours(168));

        SpacedReviewCandidate candidate = new SpacedReviewCandidate(
                UUID.randomUUID(), "MATH.ALG.QUADRATIC_DISCRIMINANT", "Quadratic Discriminant Analysis",
                SpacedTriggerType.TRAP_IMMUNITY_7D, "TRAP_SIGN_DISTRIB_NEG",
                new BigDecimal("0.7100"), new BigDecimal("7.00"), exact,
                UUID.randomUUID(), "\\Delta = b^2 - 4ac", SpacedQueueStatus.PENDING,
                "studyplan.spaced.badge.trap_immunity_7d"
        );

        List<SpacedReviewCandidate> queueEarly = scheduler.selectAndInterleaveDailyQueue(List.of(candidate), 3, early);
        assertTrue(queueEarly.isEmpty(), "Item must not be surfaced prior to 168 hours");

        List<SpacedReviewCandidate> queueExact = scheduler.selectAndInterleaveDailyQueue(List.of(candidate), 3, exact);
        assertEquals(1, queueExact.size(), "Item must be surfaced once 168 hours have elapsed");
    }

    @Test
    @DisplayName("INV-SR-001: Half-life updates for independent success and relapse")
    void testHalfLifeGrowthAndRelapse() {
        BigDecimal h0 = new BigDecimal("4.00");

        // Independent success after 4 days: H * (1.85 + 0.35 * 1.0) = 4 * 2.20 = 8.80
        BigDecimal hSuccess = scheduler.computeUpdatedHalfLife(h0, 4.0, true);
        assertEquals(new BigDecimal("8.80"), hSuccess);

        // Relapse decay: H * 0.55 = 4 * 0.55 = 2.20
        BigDecimal hRelapse = scheduler.computeUpdatedHalfLife(h0, 4.0, false);
        assertEquals(new BigDecimal("2.20"), hRelapse);

        // Cap at 90.00 days
        BigDecimal hLarge = new BigDecimal("80.00");
        BigDecimal hCapped = scheduler.computeUpdatedHalfLife(hLarge, 10.0, true);
        assertEquals(new BigDecimal("90.00"), hCapped);

        // Floor at 0.50 days
        BigDecimal hSmall = new BigDecimal("0.60");
        BigDecimal hFloored = scheduler.computeUpdatedHalfLife(hSmall, 1.0, false);
        assertEquals(new BigDecimal("0.50"), hFloored);
    }

    @Test
    @DisplayName("INV-SR-003 & INV-SR-004: Interleaving and daily cap on 15-item backlog")
    void testInterleavingAndDailyCapOnBacklog() {
        Instant now = Instant.parse("2026-11-13T10:00:00Z");
        List<SpacedReviewCandidate> backlog = new ArrayList<>();

        // Add 10 items of Skill A
        for (int i = 0; i < 10; i++) {
            backlog.add(new SpacedReviewCandidate(
                    UUID.randomUUID(), "MATH.ALG.QUADRATIC_DISCRIMINANT", "Quadratic Discriminant",
                    SpacedTriggerType.RETENTION_DECAY, null,
                    new BigDecimal("0.6000").add(new BigDecimal("0.0" + i)),
                    new BigDecimal("3.00"), now.minus(Duration.ofDays(i + 1)),
                    null, "p_a_" + i, SpacedQueueStatus.PENDING,
                    "studyplan.spaced.badge.retention_refresh"
            ));
        }

        // Add 5 items of Skill B
        for (int i = 0; i < 5; i++) {
            backlog.add(new SpacedReviewCandidate(
                    UUID.randomUUID(), "MATH.CALC.CHAIN_RULE_COMPOSITE", "Chain Rule",
                    SpacedTriggerType.RETENTION_DECAY, null,
                    new BigDecimal("0.6500").add(new BigDecimal("0.0" + i)),
                    new BigDecimal("4.00"), now.minus(Duration.ofDays(i + 1)),
                    null, "p_b_" + i, SpacedQueueStatus.PENDING,
                    "studyplan.spaced.badge.retention_refresh"
            ));
        }

        List<SpacedReviewCandidate> queue = scheduler.selectAndInterleaveDailyQueue(backlog, 3, now);

        // Strict daily cap: at most 3 items
        assertEquals(3, queue.size());

        // Interleaving: at least 2 distinct skills must be present
        long distinctSkills = queue.stream().map(SpacedReviewCandidate::canonicalSkillCode).distinct().count();
        assertTrue(distinctSkills >= 2, "Queue must contain at least 2 distinct skills");

        // No adjacent duplicate skills
        assertNotEquals(queue.get(0).canonicalSkillCode(), queue.get(1).canonicalSkillCode());
        assertNotEquals(queue.get(1).canonicalSkillCode(), queue.get(2).canonicalSkillCode());
    }
}
