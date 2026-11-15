package com.verifiedai.exam.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestionBankItemDomainTest {

    private static final UUID EXAM_DEF_ID = UUID.fromString("91000000-0000-4000-8000-000000000101");
    private static final UUID ITEM_ID = UUID.fromString("93000000-0000-4000-8000-000000000901");

    @Test
    void validSingleChoiceMcqWithVerifiedProvenanceCanBePublished() {
        QuestionProvenanceRecord provenance = validProvenance(ProvenanceVerificationStatus.VERIFIED);
        QuestionBankItem item = new QuestionBankItem(
                ITEM_ID,
                EXAM_DEF_ID,
                1,
                null,
                "TYT-MATH-TEST-001",
                "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                "SIGNED_INTEGERS",
                QuestionItemFormat.SINGLE_CHOICE_MCQ,
                QuestionDifficultyBand.MEDIUM,
                new BigDecimal("0.10"),
                new BigDecimal("1.10"),
                new BigDecimal("0.20"),
                CognitiveTaxonomyLevel.APPLICATION,
                "Evaluate the expression (-12) + 30 - (-6) and select the answer.",
                "(-12) + 30 - (-6)",
                null,
                false,
                null,
                null,
                Map.of(),
                "Adding 30 to -12 gives 18, and subtracting -6 adds 6 to yield 24.",
                75,
                new BigDecimal("1.00"),
                new BigDecimal("0.2500"),
                QuestionItemStatus.DRAFT,
                null,
                0L,
                false,
                fiveOptionsWithSingleCorrect("C"),
                provenance,
                Instant.parse("2026-02-01T10:00:00Z"),
                Instant.parse("2026-02-01T10:00:00Z")
        );

        QuestionBankItem published = item.publish(Instant.parse("2026-02-01T12:00:00Z"));
        assertEquals(QuestionItemStatus.PUBLISHED, published.status());
        assertFalse(published.trainingEligible());
        assertNotNull(published.contentSha256());
        assertEquals(64, published.contentSha256().length());
    }

    @Test
    void rejectsSingleChoiceMcqWithZeroOrMultipleCorrectOptions() {
        List<QuestionOption> twoCorrect = List.of(
                new QuestionOption(UUID.randomUUID(), ITEM_ID, "A", 1, "Opt A", null, true, null, false),
                new QuestionOption(UUID.randomUUID(), ITEM_ID, "B", 2, "Opt B", null, true, null, false),
                new QuestionOption(UUID.randomUUID(), ITEM_ID, "C", 3, "Opt C", null, false, null, false),
                new QuestionOption(UUID.randomUUID(), ITEM_ID, "D", 4, "Opt D", null, false, null, false)
        );

        ApiProblemException ex = assertThrows(ApiProblemException.class, () -> new QuestionBankItem(
                ITEM_ID,
                EXAM_DEF_ID,
                1,
                null,
                "TYT-MATH-TEST-002",
                "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                null,
                QuestionItemFormat.SINGLE_CHOICE_MCQ,
                QuestionDifficultyBand.EASY,
                new BigDecimal("-1.00"),
                new BigDecimal("1.00"),
                new BigDecimal("0.20"),
                CognitiveTaxonomyLevel.RECALL,
                "Evaluate 5 + 7 and select the single correct option.",
                null,
                null,
                false,
                null,
                null,
                Map.of(),
                "Adding 5 and 7 produces 12.",
                45,
                new BigDecimal("1.00"),
                new BigDecimal("0.2500"),
                QuestionItemStatus.DRAFT,
                null,
                0L,
                false,
                twoCorrect,
                validProvenance(ProvenanceVerificationStatus.VERIFIED),
                Instant.now(),
                Instant.now()
        ));
        assertEquals(ApiErrorCode.EXAM_QUESTION_INVALID_OPTIONS, ex.code());
    }

    @Test
    void rejectsVisualDiagramWithoutAccessibleAltText() {
        ApiProblemException ex = assertThrows(ApiProblemException.class, () -> new QuestionBankItem(
                ITEM_ID,
                EXAM_DEF_ID,
                1,
                null,
                "TYT-MATH-TEST-003",
                "MATH.ARITHMETIC.PERCENTAGES",
                null,
                QuestionItemFormat.SINGLE_CHOICE_MCQ,
                QuestionDifficultyBand.HARD,
                new BigDecimal("1.00"),
                new BigDecimal("1.20"),
                new BigDecimal("0.20"),
                CognitiveTaxonomyLevel.ANALYSIS,
                "Based on the bar chart, determine the percentage increase.",
                null,
                "short",
                true,
                null,
                null,
                Map.of(),
                "The chart shows an increase from 40 to 50, which is 25 percent.",
                90,
                new BigDecimal("1.00"),
                new BigDecimal("0.2500"),
                QuestionItemStatus.DRAFT,
                null,
                0L,
                false,
                fiveOptionsWithSingleCorrect("B"),
                validProvenance(ProvenanceVerificationStatus.VERIFIED),
                Instant.now(),
                Instant.now()
        ));
        assertEquals(ApiErrorCode.EXAM_QUESTION_INVALID_OPTIONS, ex.code());
    }

    @Test
    void rejectsProvenanceWithSameAuthorAndReviewerOrUnverifiedPublication() {
        ApiProblemException sameAuthorReviewer = assertThrows(ApiProblemException.class, () -> new QuestionProvenanceRecord(
                UUID.randomUUID(),
                ITEM_ID,
                QuestionProvenanceSourceType.ORIGINAL_AUTHORED,
                QuestionLicenseClassification.PROPRIETARY_INTERNAL,
                "VerifiedAI Assessment Guild",
                "REF-001",
                "sme.same.person",
                "sme.same.person",
                ProvenanceVerificationStatus.VERIFIED,
                null,
                Instant.now(),
                false,
                Instant.now()
        ));
        assertEquals(ApiErrorCode.EXAM_QUESTION_PROVENANCE_UNVERIFIED, sameAuthorReviewer.code());

        QuestionBankItem pendingItem = new QuestionBankItem(
                ITEM_ID,
                EXAM_DEF_ID,
                1,
                null,
                "TYT-MATH-TEST-004",
                "MATH.ALGEBRA.FACTORING",
                null,
                QuestionItemFormat.NUMERIC_GRID_IN,
                QuestionDifficultyBand.EXPERT,
                new BigDecimal("1.80"),
                new BigDecimal("1.40"),
                new BigDecimal("0.00"),
                CognitiveTaxonomyLevel.EVALUATION,
                "Compute the positive root of x^2 - 144 = 0.",
                "x^2 - 144 = 0",
                null,
                false,
                new BigDecimal("12.000000"),
                new BigDecimal("0.001000"),
                Map.of(),
                "Factoring (x-12)(x+12)=0 yields positive root x = 12.",
                100,
                new BigDecimal("1.00"),
                new BigDecimal("0.0000"),
                QuestionItemStatus.DRAFT,
                null,
                0L,
                false,
                List.of(),
                validProvenance(ProvenanceVerificationStatus.PENDING),
                Instant.now(),
                Instant.now()
        );

        ApiProblemException pubEx = assertThrows(ApiProblemException.class, () -> pendingItem.publish(Instant.now()));
        assertEquals(ApiErrorCode.EXAM_QUESTION_PROVENANCE_UNVERIFIED, pubEx.code());
        assertTrue(pubEx.getMessage().contains("SME-verified"));
    }

    private static QuestionProvenanceRecord validProvenance(ProvenanceVerificationStatus status) {
        return new QuestionProvenanceRecord(
                UUID.randomUUID(),
                ITEM_ID,
                QuestionProvenanceSourceType.ORIGINAL_AUTHORED,
                QuestionLicenseClassification.PROPRIETARY_INTERNAL,
                "VerifiedAI Assessment Guild",
                "TYT-2026-SPEC-001",
                "sme.author.ayse",
                "sme.reviewer.mehmet",
                status,
                null,
                Instant.parse("2026-02-01T09:00:00Z"),
                false,
                Instant.parse("2026-02-01T09:00:00Z")
        );
    }

    private static List<QuestionOption> fiveOptionsWithSingleCorrect(String correctKey) {
        return List.of(
                new QuestionOption(UUID.randomUUID(), ITEM_ID, "A", 1, "Option A", null, "A".equals(correctKey), null, false),
                new QuestionOption(UUID.randomUUID(), ITEM_ID, "B", 2, "Option B", null, "B".equals(correctKey), null, false),
                new QuestionOption(UUID.randomUUID(), ITEM_ID, "C", 3, "Option C", null, "C".equals(correctKey), null, false),
                new QuestionOption(UUID.randomUUID(), ITEM_ID, "D", 4, "Option D", null, "D".equals(correctKey), null, false),
                new QuestionOption(UUID.randomUUID(), ITEM_ID, "E", 5, "Option E", null, "E".equals(correctKey), null, false)
        );
    }
}
