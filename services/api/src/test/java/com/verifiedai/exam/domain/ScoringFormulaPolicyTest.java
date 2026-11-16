package com.verifiedai.exam.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.ExamFamily;
import com.verifiedai.exam.domain.ExamModels.ExamSectionSpec;
import com.verifiedai.exam.domain.ExamModels.ExamStatus;
import com.verifiedai.exam.domain.ExamModels.ExamTopicWeight;
import com.verifiedai.exam.domain.ExamModels.Jurisdiction;
import com.verifiedai.exam.domain.ExamModels.NavigationPolicy;
import com.verifiedai.exam.domain.ExamModels.ScoringEvaluationResult;
import com.verifiedai.exam.domain.ExamModels.ScoringFormulaType;
import com.verifiedai.exam.domain.ExamModels.ScoringParameters;
import com.verifiedai.exam.domain.ExamModels.CognitiveLevelMix;
import com.verifiedai.exam.domain.ExamModels.SectionWeightEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScoringFormulaPolicyTest {

    @Test
    void evaluatesOsymFourWrongOneRightIncludingNegativeNetScorePreservation() {
        ExamDefinition tytDef = buildExamDefinition(
                "TR_YKS_TYT_MATH",
                ScoringFormulaType.CORRECT_MINUS_QUARTER_WRONG,
                new BigDecimal("0.2500"),
                new BigDecimal("-10.00"),
                new BigDecimal("40.00"),
                List.of(new ExamSectionSpec("TYT_MATH", "TYT Math", "TYT Matematik", 40, 65, 1)),
                new ScoringParameters(null, null, 1, null, null, List.of())
        );

        // 0 correct, 4 wrong, 36 blank -> rawNetScore = -1.00 (never clamped to 0)
        ScoringEvaluationResult negativeNet = ScoringFormulaPolicy.evaluateAggregateForQuestionCount(
                tytDef, 40, 0, 4, 36
        );
        assertEquals(new BigDecimal("-1.00"), negativeNet.rawNetScore());
        assertEquals(0, negativeNet.llmTokensUsed());

        // Min/max possible net bounds and normalized percentage clamping at 0.00 for negative net
        assertEquals(new BigDecimal("-10.00"), ScoringFormulaPolicy.minPossibleNetScore(
                ScoringFormulaType.CORRECT_MINUS_QUARTER_WRONG, new BigDecimal("0.2500"), 40));
        assertEquals(new BigDecimal("40.00"), ScoringFormulaPolicy.maxPossibleNetScore(
                ScoringFormulaType.CORRECT_MINUS_QUARTER_WRONG, 40));
        assertEquals(new BigDecimal("0.00"), ScoringFormulaPolicy.computeNormalizedPercentageScore(
                negativeNet.rawNetScore(), new BigDecimal("40.00")));
        assertEquals(new BigDecimal("0.00"), ScoringFormulaPolicy.computeAccuracyPercentage(0, 4));
    }

    @Test
    void evaluatesLgsThreeWrongOneRightPenaltyRatioAndMixedNet() {
        ExamDefinition lgsDef = buildExamDefinition(
                "TR_LGS_MATH",
                ScoringFormulaType.CORRECT_MINUS_QUARTER_WRONG,
                new BigDecimal("0.3333"),
                new BigDecimal("-6.67"),
                new BigDecimal("20.00"),
                List.of(new ExamSectionSpec("LGS_MATH", "LGS Math", "LGS Matematik", 20, 40, 1)),
                new ScoringParameters(null, null, 1, null, null, List.of())
        );

        // 15 correct, 3 wrong, 2 blank -> 15 - (3 * 0.3333) = 14.00
        ScoringEvaluationResult result = ScoringFormulaPolicy.evaluateAggregateForQuestionCount(
                lgsDef, 20, 15, 3, 2
        );
        assertEquals(new BigDecimal("14.00"), result.rawNetScore());
        assertEquals(new BigDecimal("70.00"), ScoringFormulaPolicy.computeNormalizedPercentageScore(
                result.rawNetScore(), new BigDecimal("20.00")));
        assertEquals(new BigDecimal("83.33"), ScoringFormulaPolicy.computeAccuracyPercentage(15, 3));
    }

    @Test
    void evaluatesRawCorrectOnlyZeroPenaltyAndWeightedSectionRubric() {
        ExamDefinition satDef = buildExamDefinition(
                "INTL_SAT_MATH",
                ScoringFormulaType.RAW_CORRECT_ONLY,
                BigDecimal.ZERO,
                new BigDecimal("200.00"),
                new BigDecimal("800.00"),
                List.of(new ExamSectionSpec("SAT_MATH", "SAT Math", "SAT Mat", 44, 70, 1)),
                new ScoringParameters(200.0, 800.0, 2, null, null, List.of())
        );

        assertEquals(new BigDecimal("0.0000"), ScoringFormulaPolicy.effectiveWrongPenaltyRatio(satDef));
        assertEquals(new BigDecimal("0.00"), ScoringFormulaPolicy.minPossibleNetScore(
                ScoringFormulaType.RAW_CORRECT_ONLY, BigDecimal.ZERO, 8));
        ScoringEvaluationResult satSmallSession = ScoringFormulaPolicy.evaluateAggregateForQuestionCount(
                satDef, 8, 6, 2, 0
        );
        assertEquals(new BigDecimal("6.00"), satSmallSession.rawNetScore());
        assertEquals(new BigDecimal("75.00"), ScoringFormulaPolicy.computeNormalizedPercentageScore(
                satSmallSession.rawNetScore(), new BigDecimal("8.00")));

        ExamDefinition apDef = buildExamDefinition(
                "INTL_AP_CALC_AB",
                ScoringFormulaType.WEIGHTED_SECTION_RUBRIC,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("5.00"),
                List.of(new ExamSectionSpec("MCQ_PART_A", "MCQ Part A", "Bolum A", 10, 30, 1)),
                new ScoringParameters(null, null, 1, 1, 5, List.of(new SectionWeightEntry("MCQ_PART_A", 1.0)))
        );
        assertEquals(new BigDecimal("0.0000"), ScoringFormulaPolicy.effectiveWrongPenaltyRatio(apDef));
        ScoringEvaluationResult weighted = ScoringFormulaPolicy.evaluateAggregateForQuestionCount(
                apDef, 10, 8, 1, 1
        );
        assertEquals(new BigDecimal("8.00"), weighted.rawNetScore());
    }

    private static ExamDefinition buildExamDefinition(
            String examCode,
            ScoringFormulaType formulaType,
            BigDecimal wrongPenaltyFactor,
            BigDecimal minScore,
            BigDecimal maxScore,
            List<ExamSectionSpec> sections,
            ScoringParameters scoringParameters
    ) {
        UUID defId = UUID.randomUUID();
        List<ExamTopicWeight> weights = List.of(
                new ExamTopicWeight(
                        UUID.randomUUID(),
                        defId,
                        sections.getFirst().sectionCode(),
                        "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                        "MATH.ARITHMETIC",
                        new BigDecimal("10.0000"),
                        1,
                        20,
                        new CognitiveLevelMix(0.30, 0.50, 0.20),
                        1
                )
        );
        ExamDefinition draft = new ExamDefinition(
                defId,
                examCode,
                "2026.1",
                examCode.startsWith("TR_") ? Jurisdiction.TR : Jurisdiction.INTL,
                examCode.contains("LGS") ? ExamFamily.LGS : ExamFamily.YKS,
                examCode,
                examCode,
                ExamStatus.ACTIVE,
                sections.getFirst().questionCount(),
                60,
                formulaType,
                wrongPenaltyFactor,
                minScore,
                maxScore,
                NavigationPolicy.FREE_JUMP,
                "0".repeat(64),
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null,
                sections,
                scoringParameters,
                weights,
                false,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
        String hash = ExamBlueprintHashCalculator.computeHash(draft);
        return new ExamDefinition(
                draft.id(),
                draft.examCode(),
                draft.examVersion(),
                draft.jurisdiction(),
                draft.examFamily(),
                draft.titleEn(),
                draft.titleTr(),
                draft.status(),
                draft.totalQuestions(),
                draft.durationMinutes(),
                draft.scoringFormulaType(),
                draft.wrongPenaltyFactor(),
                draft.minScore(),
                draft.maxScore(),
                draft.navigationPolicy(),
                hash,
                draft.effectiveFrom(),
                draft.deprecatedAt(),
                draft.supersededByVersion(),
                draft.sections(),
                draft.scoringParameters(),
                draft.topicWeights(),
                false,
                draft.createdAt(),
                draft.updatedAt()
        );
    }
}
