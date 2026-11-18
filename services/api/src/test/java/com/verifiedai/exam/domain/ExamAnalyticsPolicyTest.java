package com.verifiedai.exam.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.verifiedai.exam.domain.ExamAnalyticsPolicy.ComputedAnalytics;
import com.verifiedai.exam.domain.ExamAnalyticsPolicy.ItemTimingObservation;
import com.verifiedai.exam.domain.ExamAnalyticsPolicy.ScoredSessionInput;
import com.verifiedai.exam.domain.ExamAnalyticsPolicy.TrapIncidenceObservation;
import com.verifiedai.exam.domain.ExamProgressReport.ConqueredTrapsEvidenceStatus;
import com.verifiedai.exam.domain.ExamProgressReport.EvidenceStatus;
import com.verifiedai.exam.domain.ExamProgressReport.TrapDataStatus;
import com.verifiedai.exam.domain.ExamProgressReport.TrapObservationPointDto;
import com.verifiedai.exam.domain.ExamProgressReport.TrapSourceKind;
import com.verifiedai.exam.domain.PacingAnalysisDto.PacingClassification;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExamAnalyticsPolicyTest {

    private static final UUID OWNER = UUID.fromString("99000000-0000-4000-8000-000000000001");
    private static final UUID OTHER = UUID.fromString("99000000-0000-4000-8000-000000000002");
    private final ExamAnalyticsPolicy policy = new ExamAnalyticsPolicy();

    // --- Category A: Longitudinal Score Trajectory & Moving Average ---

    @Test
    void zeroMocksReturnsEmptyStateWithNullDelta() {
        ComputedAnalytics res = policy.compute(OWNER, "TR_YKS_TYT_MATH", List.of(), List.of(), List.of(), 3);
        assertEquals(EvidenceStatus.EMPTY, res.evidenceStatus());
        assertNull(res.deltaNet());
        assertNull(res.sourceLastScoreId());
        assertTrue(res.scoreTrajectory().isEmpty());
        assertTrue(res.pacingByTopic().isEmpty());
        assertEquals(TrapDataStatus.INSUFFICIENT_DATA, res.trapRemediation().dataStatus());
        assertEquals(3, res.trapRemediation().activeTrapCountNow());
        assertEquals(0, res.conqueredTrapsCount());
    }

    @Test
    void oneAndTwoMocksHaveAccuratePartialMovingAveragesAndDeltaNullRules() {
        Instant t0 = Instant.parse("2026-10-01T10:00:00Z");
        Instant t1 = Instant.parse("2026-10-10T10:00:00Z");

        ScoredSessionInput s1 = session(OWNER, "TR_YKS_TYT_MATH", "10.00", 1, t0);
        ComputedAnalytics one = policy.compute(OWNER, "TR_YKS_TYT_MATH", List.of(s1), List.of(), List.of(), null);
        assertEquals(EvidenceStatus.INSUFFICIENT_FOR_TREND, one.evidenceStatus());
        assertNull(one.deltaNet());
        assertEquals(1, one.scoreTrajectory().size());
        assertEquals(new BigDecimal("10.00"), one.scoreTrajectory().get(0).movingAverage3());

        ScoredSessionInput s2 = session(OWNER, "TR_YKS_TYT_MATH", "13.25", 1, t1);
        ComputedAnalytics two = policy.compute(OWNER, "TR_YKS_TYT_MATH", List.of(s2, s1), List.of(), List.of(), null);
        assertEquals(EvidenceStatus.PARTIAL_TREND, two.evidenceStatus());
        assertEquals(new BigDecimal("3.25"), two.deltaNet());
        assertEquals(new BigDecimal("10.00"), two.scoreTrajectory().get(0).movingAverage3());
        assertEquals(new BigDecimal("11.63"), two.scoreTrajectory().get(1).movingAverage3());
    }

    @Test
    void negativeNetsDecimalsAndTimestampTieBreakOrderDeterministically() {
        Instant sameTime = Instant.parse("2026-10-05T12:00:00Z");
        UUID lowId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID highId = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");

        ScoredSessionInput a = new ScoredSessionInput(UUID.randomUUID(), highId, OWNER, "TR_YKS_AYT_MATH", new BigDecimal("-1.75"), 2, sameTime, sameTime);
        ScoredSessionInput b = new ScoredSessionInput(UUID.randomUUID(), lowId, OWNER, "TR_YKS_AYT_MATH", new BigDecimal("2.50"), 1, sameTime, sameTime);
        ScoredSessionInput c = session(OWNER, "TR_YKS_AYT_MATH", "5.25", 1, sameTime.plus(1, ChronoUnit.DAYS));

        ComputedAnalytics res = policy.compute(OWNER, "TR_YKS_AYT_MATH", List.of(c, a, b), List.of(), List.of(), 0);
        assertEquals(EvidenceStatus.SUFFICIENT_EVIDENCE, res.evidenceStatus());
        assertEquals(lowId, res.scoreTrajectory().get(0).sessionId());
        assertEquals(highId, res.scoreTrajectory().get(1).sessionId());
        assertEquals(new BigDecimal("2.50"), res.scoreTrajectory().get(0).movingAverage3());
        assertEquals(new BigDecimal("0.38"), res.scoreTrajectory().get(1).movingAverage3());
        assertEquals(new BigDecimal("2.00"), res.scoreTrajectory().get(2).movingAverage3());
        assertEquals(new BigDecimal("2.75"), res.deltaNet());
    }

    @Test
    void fiftyMixedTytAndAytMocksLimitStrictlyToLatestTwentyForSelectedExamAndOwner() {
        Instant base = Instant.parse("2026-08-01T09:00:00Z");
        List<ScoredSessionInput> mixed = new ArrayList<>();
        // 50 TYT mocks for OWNER, 50 AYT mocks for OWNER, and 10 TYT mocks for OTHER
        for (int i = 0; i < 50; i++) {
            mixed.add(session(OWNER, "TR_YKS_TYT_MATH", String.valueOf(10 + i * 0.5), 1, base.plus(i, ChronoUnit.DAYS)));
            mixed.add(session(OWNER, "TR_YKS_AYT_MATH", String.valueOf(5 + i * 0.25), 1, base.plus(i, ChronoUnit.DAYS)));
        }
        for (int i = 0; i < 10; i++) {
            mixed.add(session(OTHER, "TR_YKS_TYT_MATH", "39.00", 1, base.plus(100 + i, ChronoUnit.DAYS)));
        }

        ComputedAnalytics tyt = policy.compute(OWNER, "TR_YKS_TYT_MATH", mixed, List.of(), List.of(), 1);
        assertEquals(20, tyt.selectedSessionsChronological().size());
        // Latest 20 of 0..49 are indices 30..49: net = 10 + 30*0.5 = 25.00 through 10 + 49*0.5 = 34.50
        assertEquals(new BigDecimal("25.00"), tyt.scoreTrajectory().get(0).netScore());
        assertEquals(new BigDecimal("34.50"), tyt.scoreTrajectory().get(19).netScore());
        assertEquals(new BigDecimal("9.50"), tyt.deltaNet());
        assertTrue(tyt.selectedSessionsChronological().stream().allMatch(s -> OWNER.equals(s.userId()) && "TR_YKS_TYT_MATH".equals(s.examCode())));
    }

    // --- Category B: Topic-Level Speed-Pressure Pacing ---

    @Test
    void pacingComputesMeanExpectedRatioExcludesZeroOrMissingAndHonorsExactBoundaries() {
        Instant t0 = Instant.parse("2026-10-01T09:00:00Z");
        ScoredSessionInput s1 = session(OWNER, "TR_YKS_AYT_MATH", "25.00", 1, t0);
        UUID sid = s1.sessionId();

        List<ItemTimingObservation> timings = List.of(
                // Algebra: 85s vs 100s => 0.85 exact => FAST
                new ItemTimingObservation(sid, UUID.randomUUID(), "MATH.ALGEBRA", "QUADRATICS", "SK.ALG", 85, 100),
                new ItemTimingObservation(sid, UUID.randomUUID(), "MATH.ALGEBRA", "QUADRATICS", "SK.ALG", 85, 100),
                // Zero measured time excluded, must not count toward FAST!
                new ItemTimingObservation(sid, UUID.randomUUID(), "MATH.ALGEBRA", "QUADRATICS", "SK.ALG", 0, 100),
                // Integrals: 195s vs 100s => 1.95 => BOTTLENECK
                new ItemTimingObservation(sid, UUID.randomUUID(), "MATH.CALCULUS", "DEFINITE_INTEGRAL", "SK.INT", 195, 100),
                new ItemTimingObservation(sid, UUID.randomUUID(), "MATH.CALCULUS", "DEFINITE_INTEGRAL", "SK.INT", 195, 100),
                // Functions: 115s vs 100s => 1.15 exact => ON_PACE
                new ItemTimingObservation(sid, UUID.randomUUID(), "MATH.FUNCTIONS", null, null, 115, 100),
                new ItemTimingObservation(sid, UUID.randomUUID(), "MATH.FUNCTIONS", null, null, 115, 100),
                // Geometry: only 1 valid item + 1 missing expected => INSUFFICIENT_DATA
                new ItemTimingObservation(sid, UUID.randomUUID(), "MATH.GEOMETRY", null, null, 90, 90),
                new ItemTimingObservation(sid, UUID.randomUUID(), "MATH.GEOMETRY", null, null, 90, null),
                // Foreign session must be ignored
                new ItemTimingObservation(UUID.randomUUID(), UUID.randomUUID(), "MATH.FOREIGN", null, null, 10, 100)
        );

        ComputedAnalytics res = policy.compute(OWNER, "TR_YKS_AYT_MATH", List.of(s1), timings, List.of(), null);
        assertEquals(4, res.pacingByTopic().size());

        PacingAnalysisDto algebra = res.pacingByTopic().get(0);
        assertEquals("MATH.ALGEBRA", algebra.topicCode());
        assertEquals("SK.ALG", algebra.skillId());
        assertEquals(2, algebra.observedQuestionCount());
        assertEquals(1, algebra.excludedUnmeasuredCount());
        assertEquals(new BigDecimal("0.85"), algebra.ratio());
        assertEquals(PacingClassification.FAST, algebra.classification());

        PacingAnalysisDto calculus = res.pacingByTopic().get(1);
        assertEquals(PacingClassification.BOTTLENECK, calculus.classification());
        assertEquals(new BigDecimal("195.00"), calculus.meanSecondsPerQuestion());

        PacingAnalysisDto functions = res.pacingByTopic().get(2);
        assertEquals(PacingClassification.ON_PACE, functions.classification());
        assertEquals(new BigDecimal("1.15"), functions.ratio());

        PacingAnalysisDto geometry = res.pacingByTopic().get(3);
        assertEquals(PacingClassification.INSUFFICIENT_DATA, geometry.classification());
        assertNull(geometry.meanSecondsPerQuestion());
        assertNull(geometry.expectedSecondsPerQuestion());
        assertNull(geometry.ratio());
    }

    @Test
    void rawRatioDrivesClassificationBeforeDisplayRounding() {
        assertEquals(PacingClassification.FAST, ExamAnalyticsPolicy.classifyRawRatio(new BigDecimal("0.85000000")));
        assertEquals(PacingClassification.ON_PACE, ExamAnalyticsPolicy.classifyRawRatio(new BigDecimal("0.85000100")));
        assertEquals(PacingClassification.ON_PACE, ExamAnalyticsPolicy.classifyRawRatio(new BigDecimal("1.15000000")));
        assertEquals(PacingClassification.BOTTLENECK, ExamAnalyticsPolicy.classifyRawRatio(new BigDecimal("1.15000100")));
    }

    // --- Category C: Trap Remediation Correlation ---

    @Test
    void trapCorrelationGuardsInsufficientSameDayZeroVarianceAndComputesValidNegativeOrPositiveAssociation() {
        Instant d1 = Instant.parse("2026-10-01T09:00:00Z");
        Instant d2 = Instant.parse("2026-10-05T09:00:00Z");
        Instant d3 = Instant.parse("2026-10-10T09:00:00Z");

        ScoredSessionInput s1 = session(OWNER, "TR_YKS_TYT_MATH", "20.00", 1, d1);
        ScoredSessionInput s2 = session(OWNER, "TR_YKS_TYT_MATH", "25.00", 1, d2);
        ScoredSessionInput s3 = session(OWNER, "TR_YKS_TYT_MATH", "30.00", 1, d3);

        // Same-date tie across 3 sessions must not pass distinct-date correlation guard
        ScoredSessionInput sameDay1 = session(OWNER, "TR_YKS_TYT_MATH", "20.00", 1, d1);
        ScoredSessionInput sameDay2 = session(OWNER, "TR_YKS_TYT_MATH", "25.00", 1, d1.plus(1, ChronoUnit.HOURS));
        ScoredSessionInput sameDay3 = session(OWNER, "TR_YKS_TYT_MATH", "30.00", 1, d1.plus(2, ChronoUnit.HOURS));
        ComputedAnalytics sameDayRes = policy.compute(OWNER, "TR_YKS_TYT_MATH",
                List.of(sameDay1, sameDay2, sameDay3), List.of(), List.of(), 2);
        assertEquals(TrapDataStatus.INSUFFICIENT_DATA, sameDayRes.trapRemediation().dataStatus());
        assertNull(sameDayRes.trapRemediation().correlationCoefficient());

        // Zero variance when no trap incidences exist across 3 distinct days
        ComputedAnalytics zeroVar = policy.compute(OWNER, "TR_YKS_TYT_MATH",
                List.of(s1, s2, s3), List.of(), List.of(), 4);
        assertEquals(TrapDataStatus.ZERO_VARIANCE, zeroVar.trapRemediation().dataStatus());
        assertNull(zeroVar.trapRemediation().correlationCoefficient());
        assertEquals(4, zeroVar.trapRemediation().activeTrapCountNow());
        assertEquals(ConqueredTrapsEvidenceStatus.UNAVAILABLE, zeroVar.trapRemediation().conqueredTrapsEvidenceStatus());
        assertFalse(zeroVar.trapRemediation().associationIsCausal());

        // Inverse association: 4 traps -> 2 traps -> 0 traps as net grows 20 -> 25 -> 30
        List<TrapIncidenceObservation> traps = new ArrayList<>();
        for (int i = 0; i < 4; i++) traps.add(new TrapIncidenceObservation(s1.sessionId(), UUID.randomUUID(), "RECURRING_MISTAKE_TRAP", "SIGN_FLIP"));
        for (int i = 0; i < 2; i++) traps.add(new TrapIncidenceObservation(s2.sessionId(), UUID.randomUUID(), "RECURRING_MISTAKE_TRAP", "SIGN_FLIP"));
        // Conceptual gaps & blank matched code must NOT be counted as RECURRING_MISTAKE_TRAP
        traps.add(new TrapIncidenceObservation(s3.sessionId(), UUID.randomUUID(), "CONCEPTUAL_GAP", "SIGN_FLIP"));
        traps.add(new TrapIncidenceObservation(s3.sessionId(), UUID.randomUUID(), "RECURRING_MISTAKE_TRAP", " "));

        ComputedAnalytics inverse = policy.compute(OWNER, "TR_YKS_TYT_MATH",
                List.of(s1, s2, s3), List.of(), traps, 2);
        assertEquals(TrapSourceKind.SESSION_MATCHED_TRAP_INCIDENCE, inverse.trapRemediation().sourceKind());
        assertEquals(TrapDataStatus.COMPUTED, inverse.trapRemediation().dataStatus());
        assertEquals(new BigDecimal("-1.0000"), inverse.trapRemediation().correlationCoefficient());
        assertEquals(3, inverse.trapRemediation().correlationSampleSize());
        assertEquals(0, inverse.conqueredTrapsCount());

        // Positive r also allowed (never assumed negative!)
        var positive = ExamAnalyticsPolicy.computePearson(List.of(
                new TrapObservationPointDto(UUID.randomUUID(), d1, new BigDecimal("10.00"), 1),
                new TrapObservationPointDto(UUID.randomUUID(), d2, new BigDecimal("20.00"), 2),
                new TrapObservationPointDto(UUID.randomUUID(), d3, new BigDecimal("30.00"), 3)
        ));
        assertEquals(TrapDataStatus.COMPUTED, positive.status());
        assertEquals(new BigDecimal("1.0000"), positive.coefficient());
    }

    private static ScoredSessionInput session(UUID user, String exam, String net, int version, Instant submittedAt) {
        return new ScoredSessionInput(
                UUID.randomUUID(),
                UUID.randomUUID(),
                user,
                exam,
                new BigDecimal(net),
                version,
                submittedAt,
                submittedAt
        );
    }
}
