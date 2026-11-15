package com.verifiedai.exam.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.exam.domain.ExamSession.F28VerificationBatchManifest;
import com.verifiedai.exam.domain.ExamSession.SectionProgressSummary;
import com.verifiedai.exam.domain.ExamSession.ServerClockSync;
import com.verifiedai.exam.domain.ExamSessionItem.SessionItemOptionSnapshot;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ExamSessionTest {

    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");
    private static final UUID USER_ID = UUID.fromString("91000000-0000-4000-8000-000000000a01");
    private static final UUID SESSION_ID = UUID.fromString("95000000-0000-4000-8000-000000000001");
    private static final UUID ITEM_ID = UUID.fromString("96000000-0000-4000-8000-000000000001");

    @Test
    void examClockPolicyEnforcesMultipliersBoundsAndSkewAnomalies() {
        assertThat(ExamClockPolicy.normalizeMultiplier(null)).isEqualByComparingTo("1.00");
        assertThat(ExamClockPolicy.normalizeMultiplier(new BigDecimal("1.25"))).isEqualByComparingTo("1.25");
        assertThat(ExamClockPolicy.normalizeMultiplier(new BigDecimal("2.00"))).isEqualByComparingTo("2.00");

        assertThatThrownBy(() -> ExamClockPolicy.normalizeMultiplier(new BigDecimal("1.33")))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException problem = (ApiProblemException) ex;
                    assertThat(problem.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(problem.code()).isEqualTo(ApiErrorCode.CANDIDATE_ACCOMMODATION_INVALID);
                });

        assertThat(ExamClockPolicy.computeEffectiveDurationSeconds(900, new BigDecimal("1.25"))).isEqualTo(1125);
        assertThat(ExamClockPolicy.computeEffectiveDurationSeconds(21600, new BigDecimal("2.00"))).isEqualTo(32400);
        assertThat(ExamClockPolicy.validateOfflineGraceBufferSeconds(120)).isEqualTo(120);
        assertThatThrownBy(() -> ExamClockPolicy.validateOfflineGraceBufferSeconds(180))
                .isInstanceOf(IllegalArgumentException.class);

        Instant deadline = ExamClockPolicy.computeServerDeadlineAt(T0, 900);
        Instant graceDeadline = ExamClockPolicy.computeGraceDeadlineAt(deadline, 120);
        assertThat(deadline).isEqualTo(T0.plusSeconds(900));
        assertThat(graceDeadline).isEqualTo(T0.plusSeconds(1020));
        assertThat(ExamClockPolicy.computeRemainingSeconds(deadline.plusSeconds(5), deadline)).isZero();

        long skewMs = ExamClockPolicy.computeClientClockSkewMs(T0, T0.plusSeconds(10));
        assertThat(skewMs).isEqualTo(10_000L);
        assertThat(ExamClockPolicy.isClockSkewAnomaly(skewMs)).isTrue();
        assertThat(ExamClockPolicy.isWithinOfflineGraceWindow(deadline.plusSeconds(30), deadline, graceDeadline, true)).isTrue();
        assertThat(ExamClockPolicy.isWithinOfflineGraceWindow(graceDeadline.plusSeconds(1), deadline, graceDeadline, true)).isFalse();
    }

    @Test
    void sessionItemAutosaveEnforcesOptimisticConcurrencyOptionEliminationAndTrainingLock() {
        ExamSessionItem item = sampleItem(1, "TYT-MATH-001", "SEC_1", 1, 1);

        ExamSessionItem eliminated = item.applyAutosave(
                "SET_ELIMINATED_OPTIONS",
                null,
                null,
                null,
                List.of("B", "D"),
                10,
                1,
                1L,
                T0.plusSeconds(10)
        );
        assertThat(eliminated.eliminatedOptionKeys()).containsExactly("B", "D");
        assertThat(eliminated.autosaveVersion()).isEqualTo(2);

        ExamSessionItem answered = eliminated.applyAutosave(
                "SELECT_OPTION",
                "B",
                null,
                true,
                null,
                12,
                2,
                2L,
                T0.plusSeconds(22)
        );
        assertThat(answered.selectedOptionKey()).isEqualTo("B");
        assertThat(answered.eliminatedOptionKeys()).containsExactly("D");
        assertThat(answered.responseState()).isEqualTo(ExamItemResponseState.FLAGGED_ANSWERED);
        assertThat(answered.autosaveVersion()).isEqualTo(3);

        // Stale version conflict -> 409
        assertThatThrownBy(() -> answered.applyAutosave(
                "SELECT_OPTION",
                "C",
                null,
                null,
                null,
                5,
                2,
                3L,
                T0.plusSeconds(25)
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                        .isEqualTo(ApiErrorCode.EXAM_ITEM_STALE_VERSION));

        // Out-of-order clientAutosaveSeq -> 409
        assertThatThrownBy(() -> answered.applyAutosave(
                "SELECT_OPTION",
                "C",
                null,
                null,
                null,
                5,
                3,
                1L,
                T0.plusSeconds(25)
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                        .isEqualTo(ApiErrorCode.EXAM_ITEM_STALE_VERSION));

        assertThatThrownBy(() -> new ExamSessionItem(
                ITEM_ID, SESSION_ID, 1, UUID.randomUUID(), "Q1", "SEC_1", 1, "MATH",
                "SINGLE_CHOICE_MCQ", QuestionDifficultyBand.EASY, "2+2", List.of(), null,
                BigDecimal.ONE, BigDecimal.ZERO, ExamItemResponseState.UNVISITED,
                null, null, false, List.of(), 0, 1, 0L, null, false, true, T0, T0, List.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scratchpadAssetRejectsPathTraversalDisallowedPrefixBadMimeAndInvalidSha() {
        String validPrefix = "users/" + USER_ID + "/exam-sessions/" + SESSION_ID + "/scratchpad/";
        String validSha = "a".repeat(64);

        ExamScratchpadAsset valid = new ExamScratchpadAsset(
                UUID.randomUUID(), SESSION_ID, ITEM_ID, USER_ID, 1, 1,
                validPrefix + "page-1.jpg", "image/jpeg", 45_000L, validSha,
                1080, 1440, "PAPER_DENEME_BOOKLET", "3D Yayinlari",
                "QUEUED_FOR_POST_SUBMIT_BATCH", false, T0, T0
        );
        assertThat(valid.f28VerificationStatus()).isEqualTo("QUEUED_FOR_POST_SUBMIT_BATCH");

        assertThatThrownBy(() -> new ExamScratchpadAsset(
                UUID.randomUUID(), SESSION_ID, ITEM_ID, USER_ID, 1, 1,
                validPrefix + "../escape.jpg", "image/jpeg", 45_000L, validSha,
                1080, 1440, "PAPER_DENEME_BOOKLET", null,
                null, false, T0, T0
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).status()).isEqualTo(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> new ExamScratchpadAsset(
                UUID.randomUUID(), SESSION_ID, ITEM_ID, USER_ID, 1, 1,
                "users/" + UUID.randomUUID() + "/other.jpg", "image/jpeg", 45_000L, validSha,
                null, null, "PAPER_DENEME_BOOKLET", null,
                null, false, T0, T0
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).status()).isEqualTo(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> new ExamScratchpadAsset(
                UUID.randomUUID(), SESSION_ID, ITEM_ID, USER_ID, 1, 5,
                validPrefix + "page-5.jpg", "image/jpeg", 45_000L, validSha,
                null, null, "PAPER_DENEME_BOOKLET", null,
                null, false, T0, T0
        )).isInstanceOf(ApiProblemException.class);

        assertThatThrownBy(() -> new ExamScratchpadAsset(
                UUID.randomUUID(), SESSION_ID, ITEM_ID, USER_ID, 1, 1,
                validPrefix + "page-1.pdf", "application/pdf", 45_000L, validSha,
                null, null, "PAPER_DENEME_BOOKLET", null,
                null, false, T0, T0
        )).isInstanceOf(ApiProblemException.class);
    }

    @Test
    void examSessionSubmitBuildsDeterministicF28ManifestAndLocksReSubmission() {
        String validPrefix = "users/" + USER_ID + "/exam-sessions/" + SESSION_ID + "/scratchpad/";
        ExamScratchpadAsset asset = new ExamScratchpadAsset(
                UUID.randomUUID(), SESSION_ID, ITEM_ID, USER_ID, 1, 1,
                validPrefix + "deneme-p1.jpg", "image/jpeg", 52_000L, "b".repeat(64),
                1080, 1440, "PAPER_DENEME_BOOKLET", "Apotemi Deneme",
                "QUEUED_FOR_POST_SUBMIT_BATCH", false, T0.plusSeconds(30), T0.plusSeconds(30)
        );
        ExamSessionItem item1 = sampleItem(1, "Q-001", "SEC_1", 1, 1).withScratchpadAssetAdded(asset, T0.plusSeconds(30));
        ExamSessionItem item2 = sampleItem(2, "Q-002", "SEC_2", 2, 1);

        ExamSession session = new ExamSession(
                SESSION_ID,
                USER_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "INTL_SAT_MATH",
                1,
                ExamSessionStatus.IN_PROGRESS,
                "SECTION_LOCKED",
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
                null,
                null,
                null,
                2,
                0,
                0,
                2,
                1,
                0L,
                null,
                false,
                T0,
                T0,
                List.of(item1, item2)
        );

        ServerClockSync sync = session.computeClockSync(T0.plusSeconds(100));
        assertThat(sync.remainingSeconds()).isEqualTo(800);
        assertThat(sync.deadlineExpired()).isFalse();

        List<SectionProgressSummary> progress = session.computeSectionProgress();
        assertThat(progress).hasSize(2);

        ExamSession submitted = session.submit(
                ExamSessionStatus.SUBMITTED,
                "USER_INITIATED",
                "submit-key-1",
                T0.plusSeconds(200),
                0L
        );
        F28VerificationBatchManifest manifest = submitted.f28VerificationBatchManifest();
        assertThat(manifest).isNotNull();
        assertThat(manifest.batchStatus()).isEqualTo("QUEUED_FOR_F28_BATCH");
        assertThat(manifest.totalAssets()).isEqualTo(1);
        assertThat(manifest.llmTokensUsed()).isZero();
        assertThat(manifest.trainingEligible()).isFalse();
        assertThat(manifest.assets().getFirst().captureSource()).isEqualTo("PAPER_DENEME_BOOKLET");
        assertThat(manifest.assets().getFirst().bookletPublisher()).isEqualTo("Apotemi Deneme");

        assertThatThrownBy(() -> submitted.submit(
                ExamSessionStatus.SUBMITTED,
                "USER_INITIATED",
                "submit-key-2",
                T0.plusSeconds(210),
                0L
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                        .isEqualTo(ApiErrorCode.EXAM_SESSION_ALREADY_TERMINATED));
    }

    private static ExamSessionItem sampleItem(int pos, String code, String sectionCode, int sectionOrder, int version) {
        return new ExamSessionItem(
                pos == 1 ? ITEM_ID : UUID.randomUUID(),
                SESSION_ID,
                pos,
                UUID.randomUUID(),
                code,
                sectionCode,
                sectionOrder,
                "MATH.ALGEBRA",
                "SINGLE_CHOICE_MCQ",
                QuestionDifficultyBand.MEDIUM,
                "Solve $x^2 - 9 = 0$.",
                List.of(
                        new SessionItemOptionSnapshot("A", 1, "x = 1", null, false),
                        new SessionItemOptionSnapshot("B", 2, "x = 3 or -3", null, false),
                        new SessionItemOptionSnapshot("C", 3, "x = 9", null, false),
                        new SessionItemOptionSnapshot("D", 4, "x = -9", null, false)
                ),
                null,
                new BigDecimal("1.00"),
                new BigDecimal("0.2500"),
                ExamItemResponseState.UNVISITED,
                null,
                null,
                false,
                List.of(),
                0,
                version,
                0L,
                null,
                true,
                false,
                T0,
                T0,
                List.of()
        );
    }
}
