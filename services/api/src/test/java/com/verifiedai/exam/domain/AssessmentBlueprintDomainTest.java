package com.verifiedai.exam.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssessmentBlueprintDomainTest {

    private static final UUID EXAM_DEF_ID = UUID.fromString("91000000-0000-4000-8000-000000000101");
    private static final UUID BP_ID = UUID.fromString("94000000-0000-4000-8000-000000000901");

    @Test
    void validBlueprintComputesDeterministicSha256AndActivates() {
        List<BlueprintTopicRule> rules = List.of(
                new BlueprintTopicRule(UUID.randomUUID(), BP_ID, "MATH.ARITHMETIC.INTEGER_OPERATIONS", 5000, 3, 6, List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ), 1, false),
                new BlueprintTopicRule(UUID.randomUUID(), BP_ID, "MATH.ARITHMETIC.FRACTIONS", 5000, 3, 6, List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ), 2, false)
        );

        AssessmentBlueprint blueprint = new AssessmentBlueprint(
                BP_ID,
                EXAM_DEF_ID,
                1,
                null,
                "BP-TYT-TEST-001",
                "TYT Arithmetic Diagnostic",
                "8-question diagnostic blueprint",
                AssessmentBlueprintMode.DIAGNOSTIC_BASELINE,
                8,
                900,
                2500, 5000, 2500, 0,
                500,
                2500, 2500, 2500, 2500, 0,
                14,
                true,
                true,
                AssessmentBlueprintStatus.DRAFT,
                null,
                false,
                rules,
                Instant.parse("2026-02-01T10:00:00Z"),
                Instant.parse("2026-02-01T10:00:00Z")
        );

        assertNotNull(blueprint.blueprintHash());
        assertEquals(64, blueprint.blueprintHash().length());
        assertFalse(blueprint.trainingEligible());

        AssessmentBlueprint activated = blueprint.activate(Instant.parse("2026-02-01T11:00:00Z"));
        assertEquals(AssessmentBlueprintStatus.ACTIVE, activated.status());
        assertEquals(blueprint.blueprintHash(), activated.blueprintHash());
    }

    @Test
    void rejectsBlueprintWhenDifficultyOrCognitiveOrTopicWeightsDoNotSumTo10000() {
        List<BlueprintTopicRule> validRules = List.of(
                new BlueprintTopicRule(UUID.randomUUID(), BP_ID, "MATH.ARITHMETIC.INTEGER_OPERATIONS", 10000, 5, 10, List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ), 1, false)
        );

        ApiProblemException badDifficulty = assertThrows(ApiProblemException.class, () -> new AssessmentBlueprint(
                BP_ID,
                EXAM_DEF_ID,
                1,
                null,
                "BP-TYT-BAD-DIFF",
                "Bad Difficulty Sum",
                null,
                AssessmentBlueprintMode.SECTION_DRILL,
                8,
                900,
                2000, 5000, 2000, 0, // sum = 9000 != 10000
                500,
                2500, 2500, 2500, 2500, 0,
                14,
                true,
                true,
                AssessmentBlueprintStatus.DRAFT,
                null,
                false,
                validRules,
                Instant.now(),
                Instant.now()
        ));
        assertEquals(ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID, badDifficulty.code());

        List<BlueprintTopicRule> badTopicRules = List.of(
                new BlueprintTopicRule(UUID.randomUUID(), BP_ID, "MATH.ARITHMETIC.INTEGER_OPERATIONS", 4000, 2, 5, List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ), 1, false),
                new BlueprintTopicRule(UUID.randomUUID(), BP_ID, "MATH.ARITHMETIC.FRACTIONS", 4000, 2, 5, List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ), 2, false)
        );

        ApiProblemException badTopics = assertThrows(ApiProblemException.class, () -> new AssessmentBlueprint(
                BP_ID,
                EXAM_DEF_ID,
                1,
                null,
                "BP-TYT-BAD-TOPIC",
                "Bad Topic Sum",
                null,
                AssessmentBlueprintMode.SECTION_DRILL,
                8,
                900,
                2500, 5000, 2500, 0,
                500,
                2500, 2500, 2500, 2500, 0,
                14,
                true,
                true,
                AssessmentBlueprintStatus.DRAFT,
                null,
                false,
                badTopicRules,
                Instant.now(),
                Instant.now()
        ));
        assertEquals(ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID, badTopics.code());
    }

    @Test
    void rejectsBlueprintWhenTotalQuestionCountOutsideTopicMinMaxBounds() {
        List<BlueprintTopicRule> narrowBounds = List.of(
                new BlueprintTopicRule(UUID.randomUUID(), BP_ID, "MATH.ARITHMETIC.INTEGER_OPERATIONS", 5000, 1, 2, List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ), 1, false),
                new BlueprintTopicRule(UUID.randomUUID(), BP_ID, "MATH.ARITHMETIC.FRACTIONS", 5000, 1, 2, List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ), 2, false)
        );

        ApiProblemException ex = assertThrows(ApiProblemException.class, () -> new AssessmentBlueprint(
                BP_ID,
                EXAM_DEF_ID,
                1,
                null,
                "BP-TYT-BOUNDS",
                "Bounds Mismatch",
                null,
                AssessmentBlueprintMode.DIAGNOSTIC_BASELINE,
                8, // maxSum is 4
                900,
                2500, 5000, 2500, 0,
                500,
                2500, 2500, 2500, 2500, 0,
                14,
                true,
                true,
                AssessmentBlueprintStatus.DRAFT,
                null,
                false,
                narrowBounds,
                Instant.now(),
                Instant.now()
        ));
        assertEquals(ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID, ex.code());
    }
}
