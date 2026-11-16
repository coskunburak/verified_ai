package com.verifiedai.exam.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.verifiedai.exam.domain.ExamModels.CognitiveLevelMix;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.ExamFamily;
import com.verifiedai.exam.domain.ExamModels.ExamSectionSpec;
import com.verifiedai.exam.domain.ExamModels.ExamStatus;
import com.verifiedai.exam.domain.ExamModels.ExamTopicWeight;
import com.verifiedai.exam.domain.ExamModels.Jurisdiction;
import com.verifiedai.exam.domain.ExamModels.NavigationPolicy;
import com.verifiedai.exam.domain.ExamModels.ScoringFormulaType;
import com.verifiedai.exam.domain.ExamModels.ScoringParameters;
import com.verifiedai.exam.domain.ExamReadinessCalculator.ActiveTrapSignal;
import com.verifiedai.exam.domain.ExamReadinessCalculator.CalculationInput;
import com.verifiedai.exam.domain.ExamReadinessCalculator.MockScoreSignal;
import com.verifiedai.exam.domain.ExamReadinessCalculator.SkillMasterySignal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Category A domain tests for Sprint 9.6 {@link ExamReadinessCalculator}
 * (90% credible intervals, INV-EXAM-001 PRELIMINARY guard, active-trap monotonicity, INV-EXAM-004 $0 LLM cost).
 */
class ExamReadinessCalculatorTest {

    private static final Instant AS_OF = Instant.parse("2026-11-16T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("96000000-0000-4000-8000-000000000001");
    private static final String SKILL_ARITH = "MATH.ARITHMETIC.INTEGER_OPERATIONS";
    private static final String SKILL_ALGEBRA = "MATH.ALGEBRA.FACTORING";
    private static final String SKILL_FUNC = "MATH.FUNCTIONS.COMPOSITION";
    private static final String SKILL_GEO = "MATH.GEOMETRY.TRIANGLES";

    private final ExamReadinessCalculator calculator = new ExamReadinessCalculator();

    @Test
    void coldStartTriggersPreliminaryGuardWideIntervalAndZeroLlmCost() {
        ExamDefinition definition = tytMathDefinition();
        UUID projectionId = UUID.fromString("96000000-0000-4000-8000-000000000101");

        ExamReadinessProjection projection = calculator.calculate(new CalculationInput(
                projectionId,
                USER_ID,
                definition,
                null,
                new BigDecimal("28.00"),
                Map.of(),
                List.of(),
                List.of(),
                AS_OF
        ));

        assertTrue(projection.epistemicConfidence().compareTo(new BigDecimal("0.2500")) < 0);
        assertEquals(ExamReadinessConfidenceTier.PRELIMINARY, projection.confidenceTier());
        assertTrue(projection.calibrationRequired());
        assertTrue(projection.recommendedDiagnosticQuestionCount() >= 10);
        assertEquals(new BigDecimal("0.90"), projection.credibleIntervalLevel());
        assertTrue(projection.estimatedNetLow().compareTo(projection.estimatedNetMid()) <= 0);
        assertTrue(projection.estimatedNetMid().compareTo(projection.estimatedNetHigh()) <= 0);
        // INV-EXAM-001: half-width >= 0.15 * 40 = 6.00 (full width >= 12.00 unless bounded)
        assertTrue(projection.credibleIntervalWidth().compareTo(new BigDecimal("12.00")) >= 0);

        assertFalse(projection.trainingEligible());
        assertEquals(0, projection.llmTokensUsed());
        assertEquals(0, projection.inferenceCostUsd().compareTo(BigDecimal.ZERO));
        assertEquals(ExamReadinessCalculator.METHODOLOGY_VERSION, projection.methodologyVersion());

        assertFalse(projection.topRoiAllocations().isEmpty());
        for (int i = 0; i < projection.topRoiAllocations().size(); i++) {
            ExamReadinessTopicAllocation alloc = projection.topRoiAllocations().get(i);
            assertEquals(i + 1, alloc.priorityRank());
            assertFalse(alloc.trainingEligible());
            if (i > 0) {
                assertTrue(projection.topRoiAllocations().get(i - 1).expectedNetGainPerHour()
                        .compareTo(alloc.expectedNetGainPerHour()) >= 0);
            }
        }
    }

    @Test
    void credibleIntervalNarrowsMonotonicallyAsEvidenceAndConfidenceIncrease() {
        ExamDefinition definition = tytMathDefinition();

        ExamReadinessProjection lowConf = calculator.calculate(new CalculationInput(
                UUID.randomUUID(),
                USER_ID,
                definition,
                null,
                new BigDecimal("30.00"),
                skillSignals(0.55, 0.10, 2, 1),
                List.of(),
                List.of(),
                AS_OF
        ));

        ExamReadinessProjection medConf = calculator.calculate(new CalculationInput(
                UUID.randomUUID(),
                USER_ID,
                definition,
                null,
                new BigDecimal("30.00"),
                skillSignals(0.55, 0.45, 10, 1),
                List.of(),
                List.of(mockSignal(22.0, 22, 4, 14, 3800, 3900)),
                AS_OF
        ));

        ExamReadinessProjection highConf = calculator.calculate(new CalculationInput(
                UUID.randomUUID(),
                USER_ID,
                definition,
                null,
                new BigDecimal("30.00"),
                skillSignals(0.55, 0.78, 30, 0),
                List.of(),
                List.of(
                        mockSignal(23.0, 24, 4, 12, 3700, 3900),
                        mockSignal(24.0, 25, 4, 11, 3650, 3900),
                        mockSignal(25.0, 26, 4, 10, 3600, 3900)
                ),
                AS_OF
        ));

        assertEquals(ExamReadinessConfidenceTier.PRELIMINARY, lowConf.confidenceTier());
        assertFalse(medConf.calibrationRequired());
        assertEquals(ExamReadinessConfidenceTier.HIGH_CONFIDENCE, highConf.confidenceTier());
        assertEquals(0, highConf.recommendedDiagnosticQuestionCount());

        assertTrue(lowConf.epistemicConfidence().compareTo(medConf.epistemicConfidence()) < 0);
        assertTrue(medConf.epistemicConfidence().compareTo(highConf.epistemicConfidence()) < 0);

        assertTrue(lowConf.credibleIntervalWidth().compareTo(medConf.credibleIntervalWidth()) > 0);
        assertTrue(medConf.credibleIntervalWidth().compareTo(highConf.credibleIntervalWidth()) > 0);
    }

    @Test
    void activeTrapPenaltyMonotonicallyReducesEstimatedMidAndIncreasesNetLoss() {
        ExamDefinition definition = tytMathDefinition();
        Map<String, SkillMasterySignal> signals = skillSignals(0.76, 0.65, 18, 2);
        List<MockScoreSignal> mocks = List.of(mockSignal(27.0, 28, 4, 8, 3600, 3900));

        ExamReadinessProjection heavyTraps = calculator.calculate(new CalculationInput(
                UUID.randomUUID(),
                USER_ID,
                definition,
                null,
                new BigDecimal("32.00"),
                signals,
                List.of(
                        new ActiveTrapSignal("SIGN_FLIP", "ARITHMETIC_SLIP", SKILL_ALGEBRA, 0.80, 6),
                        new ActiveTrapSignal("DOMAIN_RESTRICTION", "CONCEPTUAL_MISCONCEPTION", SKILL_FUNC, 0.70, 5)
                ),
                mocks,
                AS_OF
        ));

        ExamReadinessProjection lightTraps = calculator.calculate(new CalculationInput(
                UUID.randomUUID(),
                USER_ID,
                definition,
                null,
                new BigDecimal("32.00"),
                signals,
                List.of(
                        new ActiveTrapSignal("SIGN_FLIP", "ARITHMETIC_SLIP", SKILL_ALGEBRA, 0.25, 2),
                        new ActiveTrapSignal("DOMAIN_RESTRICTION", "CONCEPTUAL_MISCONCEPTION", SKILL_FUNC, 0.20, 2)
                ),
                mocks,
                AS_OF
        ));

        ExamReadinessProjection noTraps = calculator.calculate(new CalculationInput(
                UUID.randomUUID(),
                USER_ID,
                definition,
                null,
                new BigDecimal("32.00"),
                signals,
                List.of(),
                mocks,
                AS_OF
        ));

        assertTrue(heavyTraps.netLostToTraps().compareTo(lightTraps.netLostToTraps()) > 0);
        assertTrue(lightTraps.netLostToTraps().compareTo(noTraps.netLostToTraps()) > 0);
        assertEquals(0, noTraps.netLostToTraps().compareTo(BigDecimal.ZERO));

        assertTrue(heavyTraps.estimatedNetMid().compareTo(lightTraps.estimatedNetMid()) < 0);
        assertTrue(lightTraps.estimatedNetMid().compareTo(noTraps.estimatedNetMid()) < 0);
        assertFalse(heavyTraps.activeTrapBreakdown().isEmpty());
        assertEquals("ARITHMETIC_SLIP", heavyTraps.activeTrapBreakdown().get(0).trapFamilyCode());
    }

    @Test
    void intervalRespectsExamMinMaxBoundsAndDeterminism() {
        ExamDefinition definition = tytMathDefinition();
        UUID projId = UUID.fromString("96000000-0000-4000-8000-000000000222");

        CalculationInput extremeLowInput = new CalculationInput(
                projId,
                USER_ID,
                definition,
                null,
                new BigDecimal("35.00"),
                skillSignals(0.05, 0.10, 1, 90),
                List.of(new ActiveTrapSignal("SIGN_FLIP", "CARELESS", SKILL_ARITH, 1.0, 20)),
                List.of(mockSignal(-5.0, 2, 28, 10, 4800, 3900)),
                AS_OF
        );

        ExamReadinessProjection p1 = calculator.calculate(extremeLowInput);
        ExamReadinessProjection p2 = calculator.calculate(extremeLowInput);
        assertEquals(p1, p2);
        assertTrue(p1.estimatedNetLow().compareTo(new BigDecimal("-10.00")) >= 0);
        assertTrue(p1.estimatedNetHigh().compareTo(new BigDecimal("40.00")) <= 0);
        assertTrue(p1.estimatedNetLow().compareTo(p1.estimatedNetMid()) <= 0);
        assertTrue(p1.estimatedNetMid().compareTo(p1.estimatedNetHigh()) <= 0);

        ExamReadinessProjection extremeHigh = calculator.calculate(new CalculationInput(
                UUID.randomUUID(),
                USER_ID,
                definition,
                null,
                new BigDecimal("39.00"),
                skillSignals(0.99, 0.95, 50, 0),
                List.of(),
                List.of(mockSignal(39.0, 39, 0, 1, 3200, 3900)),
                AS_OF
        ));
        assertTrue(extremeHigh.estimatedNetHigh().compareTo(new BigDecimal("40.00")) <= 0);
        assertTrue(extremeHigh.estimatedNetLow().compareTo(extremeHigh.estimatedNetMid()) <= 0);
        assertTrue(extremeHigh.estimatedNetMid().compareTo(extremeHigh.estimatedNetHigh()) <= 0);
    }

    private static Map<String, SkillMasterySignal> skillSignals(
            double mastery,
            double confidence,
            int evidenceCount,
            int daysAgo
    ) {
        Instant assessedAt = AS_OF.minus(daysAgo, ChronoUnit.DAYS);
        return Map.of(
                SKILL_ARITH, new SkillMasterySignal(SKILL_ARITH, mastery, 14.0, confidence, evidenceCount, assessedAt),
                SKILL_ALGEBRA, new SkillMasterySignal(SKILL_ALGEBRA, mastery, 14.0, confidence, evidenceCount, assessedAt),
                SKILL_FUNC, new SkillMasterySignal(SKILL_FUNC, mastery, 14.0, confidence, evidenceCount, assessedAt),
                SKILL_GEO, new SkillMasterySignal(SKILL_GEO, mastery, 14.0, confidence, evidenceCount, assessedAt)
        );
    }

    private static MockScoreSignal mockSignal(
            double net,
            int correct,
            int wrong,
            int blank,
            int spentSeconds,
            int expectedSeconds
    ) {
        return new MockScoreSignal(
                UUID.randomUUID(),
                BigDecimal.valueOf(net),
                40,
                correct,
                wrong,
                blank,
                spentSeconds,
                expectedSeconds,
                AS_OF.minus(2, ChronoUnit.DAYS)
        );
    }

    private static ExamDefinition tytMathDefinition() {
        UUID defId = UUID.fromString("91000000-0000-4000-8000-000000000101");
        List<ExamTopicWeight> weights = List.of(
                new ExamTopicWeight(UUID.randomUUID(), defId, "TYT_MATH", SKILL_ARITH, "MATH.ARITHMETIC",
                        new BigDecimal("10.0000"), 15, 25, new CognitiveLevelMix(0.3, 0.5, 0.2), 1),
                new ExamTopicWeight(UUID.randomUUID(), defId, "TYT_MATH", SKILL_ALGEBRA, "MATH.ALGEBRA",
                        new BigDecimal("12.0000"), 15, 25, new CognitiveLevelMix(0.25, 0.55, 0.2), 2),
                new ExamTopicWeight(UUID.randomUUID(), defId, "TYT_MATH", SKILL_FUNC, "MATH.FUNCTIONS",
                        new BigDecimal("8.0000"), 15, 25, new CognitiveLevelMix(0.25, 0.50, 0.25), 3),
                new ExamTopicWeight(UUID.randomUUID(), defId, "TYT_MATH", SKILL_GEO, "MATH.GEOMETRY",
                        new BigDecimal("10.0000"), 15, 25, new CognitiveLevelMix(0.25, 0.50, 0.25), 4)
        );
        return new ExamDefinition(
                defId,
                "TR_YKS_TYT_MATH",
                "2026.1",
                Jurisdiction.TR,
                ExamFamily.YKS,
                "YKS TYT Math",
                "YKS TYT Matematik",
                ExamStatus.ACTIVE,
                40,
                65,
                ScoringFormulaType.CORRECT_MINUS_QUARTER_WRONG,
                new BigDecimal("0.2500"),
                new BigDecimal("-10.00"),
                new BigDecimal("40.00"),
                NavigationPolicy.FREE_JUMP,
                "a".repeat(64),
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null,
                List.of(new ExamSectionSpec("TYT_MATH", "TYT Math", "TYT Matematik", 40, 65, 1)),
                new ScoringParameters(null, null, 1, null, null, List.of()),
                weights,
                false,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
