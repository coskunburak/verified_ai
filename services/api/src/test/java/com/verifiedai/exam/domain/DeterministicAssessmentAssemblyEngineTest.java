package com.verifiedai.exam.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicAssessmentAssemblyEngineTest {

    private static final UUID EXAM_DEF_ID = UUID.fromString("91000000-0000-4000-8000-000000000101");
    private static final UUID BP_ID = UUID.fromString("94000000-0000-4000-8000-000000000101");
    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID PROFILE_ID = UUID.fromString("66666666-7777-8888-9999-000000000000");

    private final DeterministicAssessmentAssemblyEngine engine = new DeterministicAssessmentAssemblyEngine();

    @Test
    void identicalSeedAndPoolProduceByteIdenticalReproducibilityHashAndOrdering() {
        AssessmentBlueprint blueprint = buildSampleBlueprint(500);
        List<QuestionBankItem> pool = buildSampleQuestionPool();
        Instant fixedTime = Instant.parse("2026-03-01T10:00:00Z");

        AssembledAssessment first = engine.assemble(new DeterministicAssessmentAssemblyEngine.AssemblyInput(
                null,
                USER_ID,
                PROFILE_ID,
                blueprint,
                pool,
                Set.of(),
                "seed-tyt-2026-alpha",
                new AppliedAccommodationSnapshot(new BigDecimal("1.25"), true, true, false),
                fixedTime
        ));

        AssembledAssessment second = engine.assemble(new DeterministicAssessmentAssemblyEngine.AssemblyInput(
                null,
                USER_ID,
                PROFILE_ID,
                blueprint,
                pool,
                Set.of(),
                "seed-tyt-2026-alpha",
                new AppliedAccommodationSnapshot(new BigDecimal("1.25"), true, true, false),
                fixedTime
        ));

        assertEquals(first.reproducibilityHash(), second.reproducibilityHash());
        assertEquals(8, first.items().size());
        assertEquals(900, first.baseDurationSeconds());
        assertEquals(1125, first.effectiveDurationSeconds()); // ceil(900 * 1.25) = 1125
        assertFalse(first.trainingEligible());
        assertTrue(first.distributionSummary().maxDifficultyDeviationBps() <= 500);

        for (int i = 0; i < first.items().size(); i++) {
            assertEquals(i + 1, first.items().get(i).sequenceNumber());
            assertEquals(first.items().get(i).questionItemId(), second.items().get(i).questionItemId());
            assertEquals(first.items().get(i).optionOrder(), second.items().get(i).optionOrder());
        }

        AssembledAssessment differentSeed = engine.assemble(new DeterministicAssessmentAssemblyEngine.AssemblyInput(
                null,
                USER_ID,
                PROFILE_ID,
                blueprint,
                pool,
                Set.of(),
                "seed-tyt-2026-beta",
                new AppliedAccommodationSnapshot(new BigDecimal("1.25"), true, true, false),
                fixedTime
        ));
        assertNotEquals(first.reproducibilityHash(), differentSeed.reproducibilityHash());
    }

    @Test
    void prioritizesFreshQuestionsOverRecentlySeenAndFallsBackWhenFreshInsufficient() {
        AssessmentBlueprint blueprint = buildSampleBlueprint(2000);
        List<QuestionBankItem> pool = buildSampleQuestionPool();

        // Mark 2 of the 4 items in MATH.ARITHMETIC.INTEGER_OPERATIONS as recently seen
        List<QuestionBankItem> intTopicItems = pool.stream()
                .filter(i -> i.topicCode().equals("MATH.ARITHMETIC.INTEGER_OPERATIONS"))
                .toList();
        UUID seen1 = intTopicItems.get(0).id();
        UUID seen2 = intTopicItems.get(1).id();

        AssembledAssessment assembled = engine.assemble(new DeterministicAssessmentAssemblyEngine.AssemblyInput(
                null,
                USER_ID,
                PROFILE_ID,
                blueprint,
                pool,
                Set.of(seen1, seen2),
                "seed-cooldown-priority",
                AppliedAccommodationSnapshot.standardDefaults(),
                Instant.parse("2026-03-01T10:00:00Z")
        ));

        List<UUID> selectedIds = assembled.items().stream().map(AssembledAssessmentItem::questionItemId).toList();
        assertFalse(selectedIds.contains(seen1), "Fresh items must be selected before recently seen items");
        assertFalse(selectedIds.contains(seen2), "Fresh items must be selected before recently seen items");

        // Now mark 3 of 4 as seen so only 1 is fresh, requiring 1 fallback item
        UUID seen3 = intTopicItems.get(2).id();
        AssembledAssessment fallbackAssembled = engine.assemble(new DeterministicAssessmentAssemblyEngine.AssemblyInput(
                null,
                USER_ID,
                PROFILE_ID,
                blueprint,
                pool,
                Set.of(seen1, seen2, seen3),
                "seed-cooldown-fallback",
                AppliedAccommodationSnapshot.standardDefaults(),
                Instant.parse("2026-03-01T10:00:00Z")
        ));
        assertEquals(8, fallbackAssembled.items().size());
        UUID freshOnly = intTopicItems.get(3).id();
        assertTrue(fallbackAssembled.items().stream().map(AssembledAssessmentItem::questionItemId).anyMatch(freshOnly::equals));
    }

    @Test
    void throwsQuestionPoolInsufficientWhenEligibleItemsBelowTopicQuota() {
        AssessmentBlueprint blueprint = buildSampleBlueprint(500);
        // Remove all items for MATH.ALGEBRA.FACTORING
        List<QuestionBankItem> insufficientPool = buildSampleQuestionPool().stream()
                .filter(i -> !i.topicCode().equals("MATH.ALGEBRA.FACTORING"))
                .toList();

        ApiProblemException ex = assertThrows(ApiProblemException.class, () -> engine.assemble(
                new DeterministicAssessmentAssemblyEngine.AssemblyInput(
                        null,
                        USER_ID,
                        PROFILE_ID,
                        blueprint,
                        insufficientPool,
                        Set.of(),
                        "seed-insufficient",
                        AppliedAccommodationSnapshot.standardDefaults(),
                        Instant.now()
                )
        ));
        assertEquals(ApiErrorCode.EXAM_QUESTION_POOL_INSUFFICIENT, ex.code());
    }

    @Test
    void throwsDifficultyToleranceExceededWhenPoolCannotSatisfyDifficultyTarget() {
        AssessmentBlueprint strictBlueprint = buildSampleBlueprint(100);
        // Convert all items in pool to EASY only while blueprint expects 2500 EASY / 5000 MEDIUM / 2500 HARD
        List<QuestionBankItem> allEasyPool = buildSampleQuestionPool().stream()
                .map(item -> new QuestionBankItem(
                        item.id(),
                        item.examDefinitionId(),
                        item.examVersionNumber(),
                        item.examSectionId(),
                        item.questionCode(),
                        item.topicCode(),
                        item.subtopicCode(),
                        item.itemFormat(),
                        QuestionDifficultyBand.EASY,
                        new BigDecimal("-1.00"),
                        item.discriminationAValue(),
                        item.guessingCValue(),
                        item.cognitiveLevel(),
                        item.stemMarkdown(),
                        item.stemLatex(),
                        item.altTextDescription(),
                        item.hasVisualDiagram(),
                        item.numericCorrectAnswer(),
                        item.numericTolerance(),
                        item.structuredRubric(),
                        item.solutionExplanationMarkdown(),
                        item.estimatedTimeSeconds(),
                        item.maxPoints(),
                        item.negativeMarkingWeight(),
                        item.status(),
                        null,
                        item.exposureCount(),
                        false,
                        item.options(),
                        item.provenance(),
                        item.createdAt(),
                        item.updatedAt()
                ))
                .toList();

        ApiProblemException ex = assertThrows(ApiProblemException.class, () -> engine.assemble(
                new DeterministicAssessmentAssemblyEngine.AssemblyInput(
                        null,
                        USER_ID,
                        PROFILE_ID,
                        strictBlueprint,
                        allEasyPool,
                        Set.of(),
                        "seed-diff-mismatch",
                        AppliedAccommodationSnapshot.standardDefaults(),
                        Instant.now()
                )
        ));
        assertEquals(ApiErrorCode.EXAM_ASSEMBLY_DIFFICULTY_TOLERANCE_EXCEEDED, ex.code());
    }

    private static AssessmentBlueprint buildSampleBlueprint(int toleranceBps) {
        List<BlueprintTopicRule> rules = List.of(
                new BlueprintTopicRule(UUID.randomUUID(), BP_ID, "MATH.ARITHMETIC.INTEGER_OPERATIONS", 2500, 1, 3, List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ), 1, false),
                new BlueprintTopicRule(UUID.randomUUID(), BP_ID, "MATH.ARITHMETIC.FRACTIONS", 2500, 1, 3, List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ), 2, false),
                new BlueprintTopicRule(UUID.randomUUID(), BP_ID, "MATH.ARITHMETIC.PERCENTAGES", 2500, 1, 3, List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ), 3, false),
                new BlueprintTopicRule(UUID.randomUUID(), BP_ID, "MATH.ALGEBRA.FACTORING", 2500, 1, 3, List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ), 4, false)
        );
        return new AssessmentBlueprint(
                BP_ID,
                EXAM_DEF_ID,
                1,
                null,
                "BP-TYT-MATH-DIAG-2026-V1",
                "TYT Mathematics Diagnostic Baseline (8 Items)",
                "Deterministic 8-question diagnostic baseline",
                AssessmentBlueprintMode.DIAGNOSTIC_BASELINE,
                8,
                900,
                2500, 5000, 2500, 0,
                toleranceBps,
                2500, 2500, 2500, 2500, 0,
                14,
                true,
                true,
                AssessmentBlueprintStatus.ACTIVE,
                null,
                false,
                rules,
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z")
        );
    }

    private static List<QuestionBankItem> buildSampleQuestionPool() {
        String[] topics = {
                "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                "MATH.ARITHMETIC.FRACTIONS",
                "MATH.ARITHMETIC.PERCENTAGES",
                "MATH.ALGEBRA.FACTORING"
        };
        QuestionDifficultyBand[] bands = {
                QuestionDifficultyBand.EASY,
                QuestionDifficultyBand.MEDIUM,
                QuestionDifficultyBand.MEDIUM,
                QuestionDifficultyBand.HARD
        };
        CognitiveTaxonomyLevel[] cogLevels = {
                CognitiveTaxonomyLevel.RECALL,
                CognitiveTaxonomyLevel.COMPREHENSION,
                CognitiveTaxonomyLevel.APPLICATION,
                CognitiveTaxonomyLevel.ANALYSIS
        };

        List<QuestionBankItem> items = new ArrayList<>(16);
        int seq = 1;
        for (String topic : topics) {
            for (int j = 0; j < 4; j++) {
                UUID itemId = UUID.fromString(String.format("93000000-0000-4000-8000-0000000001%02d", seq));
                List<QuestionOption> options = List.of(
                        new QuestionOption(UUID.randomUUID(), itemId, "A", 1, "Choice A for " + seq, null, true, null, false),
                        new QuestionOption(UUID.randomUUID(), itemId, "B", 2, "Choice B for " + seq, null, false, null, false),
                        new QuestionOption(UUID.randomUUID(), itemId, "C", 3, "Choice C for " + seq, null, false, null, false),
                        new QuestionOption(UUID.randomUUID(), itemId, "D", 4, "Choice D for " + seq, null, false, null, false),
                        new QuestionOption(UUID.randomUUID(), itemId, "E", 5, "Choice E for " + seq, null, false, null, false)
                );
                QuestionProvenanceRecord prov = new QuestionProvenanceRecord(
                        UUID.randomUUID(),
                        itemId,
                        QuestionProvenanceSourceType.ORIGINAL_AUTHORED,
                        QuestionLicenseClassification.PROPRIETARY_INTERNAL,
                        "VerifiedAI Assessment Guild",
                        "REF-" + seq,
                        "sme.author.1",
                        "sme.reviewer.2",
                        ProvenanceVerificationStatus.VERIFIED,
                        null,
                        Instant.parse("2026-01-15T10:00:00Z"),
                        false,
                        Instant.parse("2026-01-15T10:00:00Z")
                );
                items.add(new QuestionBankItem(
                        itemId,
                        EXAM_DEF_ID,
                        1,
                        null,
                        String.format("TYT-MATH-POOL-%03d", seq),
                        topic,
                        "SUB_" + j,
                        QuestionItemFormat.SINGLE_CHOICE_MCQ,
                        bands[j],
                        BigDecimal.valueOf(j - 1.0),
                        new BigDecimal("1.10"),
                        new BigDecimal("0.20"),
                        cogLevels[j],
                        "Deterministic stem markdown content for question item number " + seq,
                        null,
                        null,
                        false,
                        null,
                        null,
                        Map.of(),
                        "Deterministic step-by-step solution explanation for item " + seq,
                        75,
                        new BigDecimal("1.00"),
                        new BigDecimal("0.2500"),
                        QuestionItemStatus.PUBLISHED,
                        null,
                        0L,
                        false,
                        options,
                        prov,
                        Instant.parse("2026-01-15T10:00:00Z"),
                        Instant.parse("2026-01-15T10:00:00Z")
                ));
                seq++;
            }
        }
        return items;
    }
}
