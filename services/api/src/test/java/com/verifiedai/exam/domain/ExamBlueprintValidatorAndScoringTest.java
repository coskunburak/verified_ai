package com.verifiedai.exam.domain;

import com.verifiedai.curriculum.application.CurriculumTaxonomyCatalog;
import com.verifiedai.curriculum.application.CurriculumTaxonomySnapshot;
import com.verifiedai.exam.domain.ExamModels.CognitiveLevelMix;
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
import com.verifiedai.exam.domain.ExamModels.SectionResponseCount;
import com.verifiedai.exam.domain.ExamModels.SectionWeightEntry;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ExamBlueprintValidatorAndScoringTest {

    private ExamBlueprintValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ExamBlueprintValidator(stubCatalog());
    }

    public static CurriculumTaxonomyCatalog stubCatalog() {
        Map<String, String> skillToTopic = Map.of(
                "MATH.ARITHMETIC.INTEGER_OPERATIONS", "MATH.ARITHMETIC",
                "MATH.ARITHMETIC.FRACTIONS", "MATH.ARITHMETIC",
                "MATH.ALGEBRA.FACTORING", "MATH.ALGEBRA",
                "MATH.EQUATIONS.QUADRATIC_SOLVING", "MATH.EQUATIONS",
                "MATH.FUNCTIONS.COMPOSITION", "MATH.FUNCTIONS",
                "MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE", "MATH.CALCULUS.DIFFERENTIATION",
                "MATH.CALCULUS.INTEGRATION.BASIC_SUBSTITUTION", "MATH.CALCULUS.INTEGRATION"
        );
        return new CurriculumTaxonomyCatalog() {
            @Override
            public boolean isActiveSubject(String subjectId) {
                return "MATH".equals(subjectId);
            }

            @Override
            public boolean isActiveTopic(String topicId) {
                return skillToTopic.containsValue(topicId);
            }

            @Override
            public boolean isActiveSkill(String skillId) {
                return skillToTopic.containsKey(skillId);
            }

            @Override
            public String topicForSkill(String skillId) {
                return skillToTopic.get(skillId);
            }

            @Override
            public String subjectForTopic(String topicId) {
                return isActiveTopic(topicId) ? "MATH" : null;
            }

            @Override
            public Set<String> activeSkillIdsForTopic(String topicId) {
                return skillToTopic.entrySet().stream()
                        .filter(e -> e.getValue().equals(topicId))
                        .map(Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toSet());
            }

            @Override
            public CurriculumTaxonomySnapshot snapshot() {
                return null;
            }
        };
    }

    @Test
    void validatesWeightConservationAndDeterministicHash() {
        ExamDefinition valid = sampleTytDefinition(
                new BigDecimal("20.0000"),
                new BigDecimal("20.0000"),
                "MATH.ARITHMETIC.FRACTIONS",
                "MATH.ARITHMETIC",
                ExamStatus.ACTIVE,
                null
        );
        validator.validateBlueprint(valid);
        validator.validateTargetBindingEligibility(valid, valid.blueprintHash(), new BigDecimal("32.50"));

        // Recomputing hash twice yields identical 64-hex string
        assertEquals(64, valid.blueprintHash().length());
        assertEquals(valid.blueprintHash(), ExamBlueprintHashCalculator.computeHash(valid));
    }

    @Test
    void rejectsWeightConservationDriftBeyondTolerance() {
        ExamDefinition drifted = sampleTytDefinition(
                new BigDecimal("20.0000"),
                new BigDecimal("19.9990"),
                "MATH.ARITHMETIC.FRACTIONS",
                "MATH.ARITHMETIC",
                ExamStatus.ACTIVE,
                null
        );

        ApiProblemException ex = assertThrows(ApiProblemException.class, () -> validator.validateBlueprint(drifted));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.status());
        assertEquals(ApiErrorCode.EXAM_WEIGHT_CONSERVATION_VIOLATION, ex.code());
    }

    @Test
    void rejectsUnknownOrRetiredCurriculumSkillAndDomainMismatch() {
        ExamDefinition unknownSkill = sampleTytDefinition(
                new BigDecimal("20.0000"),
                new BigDecimal("20.0000"),
                "MATH.DEPRECATED.OLD_SKILL",
                "MATH.ARITHMETIC",
                ExamStatus.ACTIVE,
                null
        );
        ApiProblemException exUnknown = assertThrows(ApiProblemException.class, () -> validator.validateBlueprint(unknownSkill));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exUnknown.status());
        assertEquals(ApiErrorCode.EXAM_CURRICULUM_SKILL_UNKNOWN, exUnknown.code());

        ExamDefinition domainMismatch = sampleTytDefinition(
                new BigDecimal("20.0000"),
                new BigDecimal("20.0000"),
                "MATH.ARITHMETIC.FRACTIONS",
                "MATH.ALGEBRA",
                ExamStatus.ACTIVE,
                null
        );
        ApiProblemException exMismatch = assertThrows(ApiProblemException.class, () -> validator.validateBlueprint(domainMismatch));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exMismatch.status());
        assertEquals(ApiErrorCode.EXAM_CURRICULUM_SKILL_UNKNOWN, exMismatch.code());
    }

    @Test
    void rejectsTamperedHashDeprecatedVersionAndOutOfRangeTargetScore() {
        ExamDefinition valid = sampleTytDefinition(
                new BigDecimal("20.0000"),
                new BigDecimal("20.0000"),
                "MATH.ARITHMETIC.FRACTIONS",
                "MATH.ARITHMETIC",
                ExamStatus.ACTIVE,
                null
        );

        // Wrong expected hash -> 409 EXAM_VERSION_CONFLICT
        ApiProblemException hashConflict = assertThrows(
                ApiProblemException.class,
                () -> validator.validateTargetBindingEligibility(valid, "0".repeat(64), new BigDecimal("30.00"))
        );
        assertEquals(HttpStatus.CONFLICT, hashConflict.status());
        assertEquals(ApiErrorCode.EXAM_VERSION_CONFLICT, hashConflict.code());

        // Non-ACTIVE status -> 409 EXAM_VERSION_CONFLICT
        ExamDefinition deprecated = sampleTytDefinition(
                new BigDecimal("20.0000"),
                new BigDecimal("20.0000"),
                "MATH.ARITHMETIC.FRACTIONS",
                "MATH.ARITHMETIC",
                ExamStatus.DEPRECATED,
                null
        );
        ApiProblemException deprecatedConflict = assertThrows(
                ApiProblemException.class,
                () -> validator.validateTargetBindingEligibility(deprecated, deprecated.blueprintHash(), new BigDecimal("30.00"))
        );
        assertEquals(HttpStatus.CONFLICT, deprecatedConflict.status());
        assertEquals(ApiErrorCode.EXAM_VERSION_CONFLICT, deprecatedConflict.code());

        // Out-of-range score -> 422 EXAM_TARGET_SCORE_OUT_OF_RANGE
        ApiProblemException outOfRange = assertThrows(
                ApiProblemException.class,
                () -> validator.validateTargetBindingEligibility(valid, valid.blueprintHash(), new BigDecimal("41.00"))
        );
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, outOfRange.status());
        assertEquals(ApiErrorCode.EXAM_TARGET_SCORE_OUT_OF_RANGE, outOfRange.code());

        // Stored tampered hash on definition -> 409
        ExamDefinition tampered = sampleTytDefinition(
                new BigDecimal("20.0000"),
                new BigDecimal("20.0000"),
                "MATH.ARITHMETIC.FRACTIONS",
                "MATH.ARITHMETIC",
                ExamStatus.ACTIVE,
                "f".repeat(64)
        );
        assertNotEquals(ExamBlueprintHashCalculator.computeHash(tampered), tampered.blueprintHash());
        ApiProblemException storedHashConflict = assertThrows(
                ApiProblemException.class,
                () -> validator.validateBlueprint(tampered)
        );
        assertEquals(HttpStatus.CONFLICT, storedHashConflict.status());
        assertEquals(ApiErrorCode.EXAM_VERSION_CONFLICT, storedHashConflict.code());
    }

    @Test
    void evaluatesDeterministicScoringPoliciesAtZeroLlmCost() {
        // 1. YKS TYT/AYT: 32 correct, 8 wrong, 0 blank -> 32 - 0.25 * 8 = 30.00
        ExamDefinition tytDef = sampleTytDefinition(
                new BigDecimal("20.0000"),
                new BigDecimal("20.0000"),
                "MATH.ARITHMETIC.FRACTIONS",
                "MATH.ARITHMETIC",
                ExamStatus.ACTIVE,
                null
        );
        ScoringEvaluationResult yksResult = ScoringFormulaPolicy.evaluateAggregate(tytDef, 32, 8, 0);
        assertEquals(new BigDecimal("30.00"), yksResult.rawNetScore());
        assertEquals(new BigDecimal("30.00"), yksResult.scaledScore());
        assertEquals(0, yksResult.llmTokensUsed());

        // 2. Digital SAT RAW_CORRECT_ONLY: 22/44 correct on [200, 800] -> 500.00
        ExamDefinition satDef = new ExamDefinition(
                UUID.randomUUID(),
                "INTL_SAT_MATH",
                "2026.1",
                Jurisdiction.INTL,
                ExamFamily.SAT,
                "SAT Math",
                "SAT Matematik",
                ExamStatus.ACTIVE,
                44,
                70,
                ScoringFormulaType.RAW_CORRECT_ONLY,
                BigDecimal.ZERO,
                new BigDecimal("200.00"),
                new BigDecimal("800.00"),
                NavigationPolicy.SECTION_LOCKED,
                "a".repeat(64),
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null,
                List.of(
                        new ExamSectionSpec("SAT_MOD_1", "Mod 1", "Modül 1", 22, 35, 1),
                        new ExamSectionSpec("SAT_MOD_2", "Mod 2", "Modül 2", 22, 35, 2)
                ),
                new ScoringParameters(200.0, 800.0, 2, null, null, List.of()),
                List.of(),
                false,
                Instant.now(),
                Instant.now()
        );
        ScoringEvaluationResult satResult = ScoringFormulaPolicy.evaluateAggregate(satDef, 22, 12, 10);
        assertEquals(new BigDecimal("22.00"), satResult.rawNetScore());
        assertEquals(new BigDecimal("500.00"), satResult.scaledScore());
        assertEquals(0, satResult.llmTokensUsed());

        // 3. WEIGHTED_SECTION_RUBRIC (IB AA HL 50/50 on [1, 7]): Paper 1 15/15 (1.0), Paper 2 7.5? Let's test 15/15 and 0/15 -> ratio 0.5 -> 1 + 0.5 * 6 = 4.00
        ExamDefinition ibDef = new ExamDefinition(
                UUID.randomUUID(),
                "INTL_IB_MATH_AA_HL",
                "2026.1",
                Jurisdiction.INTL,
                ExamFamily.IB,
                "IB Math AA HL",
                "IB Matematik AA HL",
                ExamStatus.ACTIVE,
                30,
                120,
                ScoringFormulaType.WEIGHTED_SECTION_RUBRIC,
                BigDecimal.ZERO,
                new BigDecimal("1.00"),
                new BigDecimal("7.00"),
                NavigationPolicy.SECTION_LOCKED,
                "b".repeat(64),
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null,
                List.of(
                        new ExamSectionSpec("IB_PAPER_1", "Paper 1", "Bölüm 1", 15, 60, 1),
                        new ExamSectionSpec("IB_PAPER_2", "Paper 2", "Bölüm 2", 15, 60, 2)
                ),
                new ScoringParameters(null, null, 2, 1, 7, List.of(
                        new SectionWeightEntry("IB_PAPER_1", 0.5),
                        new SectionWeightEntry("IB_PAPER_2", 0.5)
                )),
                List.of(),
                false,
                Instant.now(),
                Instant.now()
        );
        ScoringEvaluationResult ibResult = ScoringFormulaPolicy.evaluateBySection(
                ibDef,
                List.of(
                        new SectionResponseCount("IB_PAPER_1", 15, 0, 0),
                        new SectionResponseCount("IB_PAPER_2", 0, 10, 5)
                )
        );
        assertEquals(new BigDecimal("4.00"), ibResult.scaledScore());
        assertEquals(0, ibResult.llmTokensUsed());
    }

    private static ExamDefinition sampleTytDefinition(
            BigDecimal weight1,
            BigDecimal weight2,
            String secondSkillId,
            String secondDomainCode,
            ExamStatus status,
            String overrideHash
    ) {
        UUID defId = UUID.randomUUID();
        List<ExamTopicWeight> weights = List.of(
                new ExamTopicWeight(
                        UUID.randomUUID(),
                        defId,
                        "TYT_MATH",
                        "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                        "MATH.ARITHMETIC",
                        weight1,
                        15,
                        25,
                        new CognitiveLevelMix(0.3, 0.5, 0.2),
                        1
                ),
                new ExamTopicWeight(
                        UUID.randomUUID(),
                        defId,
                        "TYT_MATH",
                        secondSkillId,
                        secondDomainCode,
                        weight2,
                        15,
                        25,
                        new CognitiveLevelMix(0.25, 0.55, 0.2),
                        2
                )
        );
        ExamDefinition draft = new ExamDefinition(
                defId,
                "TR_YKS_TYT_MATH",
                "2026.1",
                Jurisdiction.TR,
                ExamFamily.YKS,
                "YKS TYT Math",
                "YKS TYT Matematik",
                status,
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
                List.of(new ExamSectionSpec("TYT_MATH", "TYT Math", "TYT Matematik", 40, 65, 1)),
                new ScoringParameters(null, null, 1, null, null, List.of()),
                weights,
                false,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
        String computed = overrideHash != null ? overrideHash : ExamBlueprintHashCalculator.computeHash(draft);
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
                computed,
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
