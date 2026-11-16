package com.verifiedai.exam.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.verifiedai.exam.domain.ExamSessionItem.SessionItemOptionSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicExamScoringPolicyTest {

    private static final UUID USER_ID = UUID.fromString("95000000-0000-4000-8000-000000000001");
    private static final UUID DEF_ID = UUID.fromString("91000000-0000-4000-8000-000000000101");
    private static final UUID SESSION_ID = UUID.fromString("95000000-0000-4000-8000-000000000002");
    private static final Instant T0 = Instant.parse("2026-11-16T00:00:00Z");

    private final DeterministicExamScoringPolicy policy = new DeterministicExamScoringPolicy();

    @Test
    void gradesSingleMcqMultiSelectNumericToleranceBlankScratchpadAndBreakdownsDeterministically() {
        UUID q1Id = UUID.randomUUID();
        UUID q2Id = UUID.randomUUID();
        UUID q3Id = UUID.randomUUID();
        UUID q4Id = UUID.randomUUID();
        UUID q5Id = UUID.randomUUID();

        QuestionBankItem q1 = mcqItem(q1Id, "TYT-Q1", "MATH.ARITHMETIC.FRACTIONS",
                QuestionDifficultyBand.EASY, CognitiveTaxonomyLevel.RECALL, "B",
                "Dividing before common denominator omits the factor 2.", "Reduce both denominators first.");
        QuestionBankItem q2Wrong = mcqItem(q2Id, "TYT-Q2", "MATH.ARITHMETIC.FRACTIONS",
                QuestionDifficultyBand.MEDIUM, CognitiveTaxonomyLevel.APPLICATION, "D",
                "Inverted numerator and denominator during fraction division.", "Multiply by the reciprocal.");
        QuestionBankItem q3Multi = multiSelectItem(q3Id, "TYT-Q3", "MATH.ALGEBRA.FACTORING",
                QuestionDifficultyBand.HARD, CognitiveTaxonomyLevel.ANALYSIS, List.of("A", "C"));
        QuestionBankItem q4Numeric = numericItem(q4Id, "TYT-Q4", "MATH.ALGEBRA.FACTORING",
                QuestionDifficultyBand.EXPERT, CognitiveTaxonomyLevel.EVALUATION,
                new BigDecimal("12.500000"), new BigDecimal("0.050000"));
        QuestionBankItem q5Blank = mcqItem(q5Id, "TYT-Q5", "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                QuestionDifficultyBand.EASY, CognitiveTaxonomyLevel.RECALL, "E",
                "Sign slip on subtraction.", "Distribute the negative sign.");

        UUID scratchAssetId = UUID.randomUUID();
        ExamScratchpadAsset scratchpad = new ExamScratchpadAsset(
                scratchAssetId,
                SESSION_ID,
                UUID.randomUUID(),
                USER_ID,
                2,
                1,
                "users/" + USER_ID + "/exam-sessions/" + SESSION_ID + "/scratchpad/p1.jpg",
                "image/jpeg",
                24576L,
                "b".repeat(64),
                1080,
                1440,
                "PAPER_DENEME_BOOKLET",
                "345 Yayinlari",
                "QUEUED_FOR_POST_SUBMIT_BATCH",
                false,
                T0,
                T0
        );

        ExamSession session = new ExamSession(
                SESSION_ID,
                USER_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TR_YKS_TYT_MATH",
                1,
                ExamSessionStatus.SUBMITTED,
                "FREE_NAVIGATION",
                true,
                new AppliedAccommodationSnapshot(new BigDecimal("1.00"), false, false, false),
                900,
                new BigDecimal("1.00"),
                900,
                120,
                T0,
                T0.plusSeconds(900),
                T0.plusSeconds(1020),
                T0,
                null,
                T0.plusSeconds(300),
                "USER_INITIATED",
                "sub-key-1",
                1,
                300,
                4,
                1,
                1,
                0L,
                null,
                false,
                T0,
                T0.plusSeconds(300),
                List.of(
                        sessionItem(1, q1Id, "TYT-Q1", "MATH.ARITHMETIC.FRACTIONS", QuestionDifficultyBand.EASY,
                                "SINGLE_CHOICE_MCQ", ExamItemResponseState.ANSWERED, "B", null, false, 40, List.of()),
                        sessionItem(2, q2Id, "TYT-Q2", "MATH.ARITHMETIC.FRACTIONS", QuestionDifficultyBand.MEDIUM,
                                "SINGLE_CHOICE_MCQ", ExamItemResponseState.FLAGGED_ANSWERED, "A", null, true, 75, List.of(scratchpad)),
                        // Order-independent multi-select ("c, a" == "A,C")
                        sessionItem(3, q3Id, "TYT-Q3", "MATH.ALGEBRA.FACTORING", QuestionDifficultyBand.HARD,
                                "MULTI_SELECT_MCQ", ExamItemResponseState.ANSWERED, "c, a", null, false, 60, List.of()),
                        // Boundary numeric tolerance: |12.55 - 12.50| == 0.05 <= 0.05 -> CORRECT
                        sessionItem(4, q4Id, "TYT-Q4", "MATH.ALGEBRA.FACTORING", QuestionDifficultyBand.EXPERT,
                                "NUMERIC_GRID_IN", ExamItemResponseState.ANSWERED, null, new BigDecimal("12.55"), false, 95, List.of()),
                        // Blank item
                        sessionItem(5, q5Id, "TYT-Q5", "MATH.ARITHMETIC.INTEGER_OPERATIONS", QuestionDifficultyBand.EASY,
                                "SINGLE_CHOICE_MCQ", ExamItemResponseState.VIEWED_OMITTED, null, null, false, 15, List.of())
                )
        );

        ExamDefinition definition = tytDefinition();
        Map<UUID, QuestionBankItem> questions = Map.of(
                q1Id, q1,
                q2Id, q2Wrong,
                q3Id, q3Multi,
                q4Id, q4Numeric,
                q5Id, q5Blank
        );

        UUID scoreId = UUID.randomUUID();
        ExamSessionScoreSnapshot snapshot = policy.scoreSession(
                scoreId,
                session,
                definition,
                questions,
                1,
                T0.plusSeconds(300),
                T0.plusSeconds(301),
                null
        );

        // 3 correct (Q1, Q3, Q4), 1 wrong (Q2), 1 blank (Q5) -> net = 3 - 0.25 = 2.75
        assertEquals(DEF_ID, snapshot.examDefinitionId());
        assertEquals(5, snapshot.totalQuestions());
        assertEquals(3, snapshot.correctCount());
        assertEquals(1, snapshot.wrongCount());
        assertEquals(1, snapshot.blankCount());
        assertEquals(new BigDecimal("3.00"), snapshot.rawCorrectScore());
        assertEquals(new BigDecimal("0.25"), snapshot.wrongPenaltyDeduction());
        assertEquals(new BigDecimal("2.75"), snapshot.netScore());
        assertEquals(new BigDecimal("55.00"), snapshot.normalizedPercentageScore());
        assertEquals(new BigDecimal("75.00"), snapshot.accuracyPercentage());
        assertEquals(285, snapshot.totalTimeSpentSeconds());
        assertEquals(0, snapshot.llmTokensUsed());
        assertEquals(0, snapshot.inferenceCostUsd().compareTo(BigDecimal.ZERO));
        assertFalse(snapshot.trainingEligible());

        // Per-item evaluations & evidence traceability
        ExamSessionItemEvaluation eval2 = snapshot.itemEvaluations().get(1);
        assertEquals(ItemEvaluationOutcome.WRONG, eval2.evaluationOutcome());
        assertEquals(DifficultyBand.MODERATE, eval2.difficultyBand());
        assertEquals("A", eval2.selectedOptionKey());
        assertEquals("D", eval2.correctOptionKey());
        assertTrue(eval2.flaggedForReview());
        assertEquals("Inverted numerator and denominator during fraction division.", eval2.selectedOptionDistractorRationale());
        assertEquals(F28ScratchpadTraceStatus.ATTACHED_METADATA_ONLY, eval2.f28ScratchpadStatus());
        assertEquals(1, eval2.scratchpadAssetCount());
        assertEquals(List.of(scratchAssetId), eval2.scratchpadAssetIds());
        assertEquals(0, eval2.llmTokensUsed());
        assertFalse(eval2.trainingEligible());

        ExamSessionItemEvaluation eval3 = snapshot.itemEvaluations().get(2);
        assertEquals(ItemEvaluationOutcome.CORRECT, eval3.evaluationOutcome());
        assertEquals(QuestionFormatType.MULTI_SELECT_MCQ, eval3.formatType());
        assertEquals("A,C", eval3.correctOptionKey());
        assertNull(eval3.selectedOptionDistractorRationale());

        ExamSessionItemEvaluation eval4 = snapshot.itemEvaluations().get(3);
        assertEquals(ItemEvaluationOutcome.CORRECT, eval4.evaluationOutcome());
        assertEquals(QuestionFormatType.NUMERIC_GRID_IN, eval4.formatType());
        assertEquals(DifficultyBand.ELITE, eval4.difficultyBand());
        assertEquals(CognitiveLevel.SYNTHESIS, eval4.cognitiveLevel());

        ExamSessionItemEvaluation eval5 = snapshot.itemEvaluations().get(4);
        assertEquals(ItemEvaluationOutcome.BLANK, eval5.evaluationOutcome());
        assertEquals(new BigDecimal("0.00"), eval5.netPointsContribution());
        assertEquals(F28ScratchpadTraceStatus.NONE, eval5.f28ScratchpadStatus());

        // Topic & difficulty breakdown invariants sum to totalQuestions
        assertEquals(3, snapshot.topicBreakdowns().size());
        assertEquals(5, snapshot.topicBreakdowns().stream().mapToInt(TopicScoreBreakdown::totalQuestions).sum());
        assertEquals(5, snapshot.difficultyBreakdowns().stream().mapToInt(DifficultyScoreBreakdown::totalQuestions).sum());
    }

    private static ExamDefinition tytDefinition() {
        ExamDefinition draft = new ExamDefinition(
                DEF_ID,
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
                "0".repeat(64),
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null,
                List.of(new ExamSectionSpec("MAIN", "Main", "Ana", 40, 65, 1)),
                new ScoringParameters(null, null, 1, null, null, List.of()),
                List.of(new ExamTopicWeight(
                        UUID.randomUUID(), DEF_ID, "MAIN", "MATH.ARITHMETIC.FRACTIONS", "MATH.ARITHMETIC",
                        new BigDecimal("40.0000"), 1, 40, new CognitiveLevelMix(0.3, 0.5, 0.2), 1
                )),
                false,
                T0,
                T0
        );
        return new ExamDefinition(
                draft.id(), draft.examCode(), draft.examVersion(), draft.jurisdiction(), draft.examFamily(),
                draft.titleEn(), draft.titleTr(), draft.status(), draft.totalQuestions(), draft.durationMinutes(),
                draft.scoringFormulaType(), draft.wrongPenaltyFactor(), draft.minScore(), draft.maxScore(),
                draft.navigationPolicy(), ExamBlueprintHashCalculator.computeHash(draft),
                draft.effectiveFrom(), draft.deprecatedAt(), draft.supersededByVersion(),
                draft.sections(), draft.scoringParameters(), draft.topicWeights(), false, T0, T0
        );
    }

    private static ExamSessionItem sessionItem(
            int pos,
            UUID qId,
            String qCode,
            String topicCode,
            QuestionDifficultyBand diff,
            String format,
            ExamItemResponseState responseState,
            String selectedOptionKey,
            BigDecimal numericResponseValue,
            boolean flagged,
            int timeSpent,
            List<ExamScratchpadAsset> scratchpads
    ) {
        return new ExamSessionItem(
                UUID.randomUUID(),
                SESSION_ID,
                pos,
                qId,
                qCode,
                "MAIN",
                1,
                topicCode,
                format,
                diff,
                "Stem for " + qCode,
                List.of(
                        new SessionItemOptionSnapshot("A", 1, "Opt A", null, false),
                        new SessionItemOptionSnapshot("B", 2, "Opt B", null, false),
                        new SessionItemOptionSnapshot("C", 3, "Opt C", null, false),
                        new SessionItemOptionSnapshot("D", 4, "Opt D", null, false),
                        new SessionItemOptionSnapshot("E", 5, "Opt E", null, false)
                ),
                null,
                BigDecimal.ONE,
                new BigDecimal("0.2500"),
                responseState,
                selectedOptionKey,
                numericResponseValue,
                flagged,
                List.of(),
                timeSpent,
                1,
                1L,
                T0,
                true,
                false,
                T0,
                T0,
                scratchpads
        );
    }

    private static QuestionBankItem mcqItem(
            UUID id,
            String code,
            String topic,
            QuestionDifficultyBand diff,
            CognitiveTaxonomyLevel cog,
            String correctKey,
            String wrongRationale,
            String explanation
    ) {
        List<QuestionOption> opts = List.of(
                new QuestionOption(UUID.randomUUID(), id, "A", 1, "A opt", null, "A".equals(correctKey), "A".equals(correctKey) ? null : wrongRationale, false),
                new QuestionOption(UUID.randomUUID(), id, "B", 2, "B opt", null, "B".equals(correctKey), "B".equals(correctKey) ? null : wrongRationale, false),
                new QuestionOption(UUID.randomUUID(), id, "C", 3, "C opt", null, "C".equals(correctKey), "C".equals(correctKey) ? null : wrongRationale, false),
                new QuestionOption(UUID.randomUUID(), id, "D", 4, "D opt", null, "D".equals(correctKey), "D".equals(correctKey) ? null : wrongRationale, false),
                new QuestionOption(UUID.randomUUID(), id, "E", 5, "E opt", null, "E".equals(correctKey), "E".equals(correctKey) ? null : wrongRationale, false)
        );
        return new QuestionBankItem(
                id, DEF_ID, 1, null, code, topic, null, QuestionItemFormat.SINGLE_CHOICE_MCQ,
                diff, BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("0.20"), cog,
                "Prompt for " + code, null, null, false, null, null, Map.of(),
                explanation, 60, BigDecimal.ONE, new BigDecimal("0.2500"),
                QuestionItemStatus.PUBLISHED, null, 1L, false, opts, provenance(id), T0, T0
        );
    }

    private static QuestionBankItem multiSelectItem(
            UUID id,
            String code,
            String topic,
            QuestionDifficultyBand diff,
            CognitiveTaxonomyLevel cog,
            List<String> correctKeys
    ) {
        List<QuestionOption> opts = List.of(
                new QuestionOption(UUID.randomUUID(), id, "A", 1, "A", null, correctKeys.contains("A"), "A rationale", false),
                new QuestionOption(UUID.randomUUID(), id, "B", 2, "B", null, correctKeys.contains("B"), "B rationale", false),
                new QuestionOption(UUID.randomUUID(), id, "C", 3, "C", null, correctKeys.contains("C"), "C rationale", false),
                new QuestionOption(UUID.randomUUID(), id, "D", 4, "D", null, correctKeys.contains("D"), "D rationale", false)
        );
        return new QuestionBankItem(
                id, DEF_ID, 1, null, code, topic, null, QuestionItemFormat.MULTI_SELECT_MCQ,
                diff, BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("0.20"), cog,
                "Multi-select prompt for " + code, null, null, false, null, null, Map.of(),
                "Select both A and C factors.", 90, BigDecimal.ONE, new BigDecimal("0.2500"),
                QuestionItemStatus.PUBLISHED, null, 1L, false, opts, provenance(id), T0, T0
        );
    }

    private static QuestionBankItem numericItem(
            UUID id,
            String code,
            String topic,
            QuestionDifficultyBand diff,
            CognitiveTaxonomyLevel cog,
            BigDecimal expected,
            BigDecimal tolerance
    ) {
        return new QuestionBankItem(
                id, DEF_ID, 1, null, code, topic, null, QuestionItemFormat.NUMERIC_GRID_IN,
                diff, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, cog,
                "Numeric prompt for " + code, null, null, false, expected, tolerance, Map.of(),
                "Numeric grid-in solution = " + expected, 90, BigDecimal.ONE, new BigDecimal("0.2500"),
                QuestionItemStatus.PUBLISHED, null, 1L, false, List.of(), provenance(id), T0, T0
        );
    }

    private static QuestionProvenanceRecord provenance(UUID itemId) {
        return new QuestionProvenanceRecord(
                UUID.randomUUID(), itemId, QuestionProvenanceSourceType.ORIGINAL_AUTHORED,
                QuestionLicenseClassification.PROPRIETARY_INTERNAL, "VerifiedAI Guild",
                "REF-S95", "sme.author", "sme.reviewer", ProvenanceVerificationStatus.VERIFIED,
                null, T0, false, T0
        );
    }
}
