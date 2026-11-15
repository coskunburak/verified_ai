package com.verifiedai.exam.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.exam.application.ExamSessionRuntimeService;
import com.verifiedai.exam.application.ExamSessionRuntimeService.AttachScratchpadCommand;
import com.verifiedai.exam.application.ExamSessionRuntimeService.AutosaveItemCommand;
import com.verifiedai.exam.application.ExamSessionRuntimeService.AutosaveResult;
import com.verifiedai.exam.application.ExamSessionRuntimeService.ScratchpadAttachResult;
import com.verifiedai.exam.application.ExamSessionRuntimeService.StartOrResumeResult;
import com.verifiedai.exam.application.ExamSessionRuntimeService.StartSessionCommand;
import com.verifiedai.exam.application.ExamSessionRuntimeService.SubmitSessionCommand;
import com.verifiedai.exam.application.ExamSessionRuntimeService.SubmitSessionResult;
import com.verifiedai.exam.domain.AppliedAccommodationSnapshot;
import com.verifiedai.exam.domain.AssembledAssessment;
import com.verifiedai.exam.domain.AssembledAssessmentItem;
import com.verifiedai.exam.domain.AssembledAssessmentRepository;
import com.verifiedai.exam.domain.AssembledAssessmentStatus;
import com.verifiedai.exam.domain.AssemblyDistributionSummary;
import com.verifiedai.exam.domain.AssessmentBlueprintMode;
import com.verifiedai.exam.domain.CognitiveTaxonomyLevel;
import com.verifiedai.exam.domain.ExamItemResponseState;
import com.verifiedai.exam.domain.ExamSession;
import com.verifiedai.exam.domain.ExamSessionStatus;
import com.verifiedai.exam.domain.QuestionDifficultyBand;
import com.verifiedai.exam.domain.QuestionItemFormat;
import com.verifiedai.exam.domain.QuestionOption;
import com.verifiedai.exam.infrastructure.persistence.ExamSessionRepository;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class ExamSessionMigrationAndRepositoryTest extends PostgresIntegrationTestSupport {

    private static final UUID EXAM_DEF_ID = UUID.fromString("91000000-0000-4000-8000-000000000101");
    private static final UUID BLUEPRINT_ID = UUID.fromString("94000000-0000-4000-8000-000000000101");
    private static final UUID SEEDED_Q1_ID = UUID.fromString("93000000-0000-4000-8000-000000000101");

    @Autowired
    private AssembledAssessmentRepository assembledAssessmentRepository;

    @Autowired
    private ExamSessionRepository examSessionRepository;

    @Autowired
    private ExamSessionRuntimeService runtimeService;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void endToEndTimedSessionLifecycleScratchpadF28AndPrivacyDeleteAgainstPostgres() {
        UUID userId = UUID.randomUUID();
        UUID assemblyId = UUID.randomUUID();
        Instant now = Instant.now();

        AssembledAssessment assembled = new AssembledAssessment(
                assemblyId,
                userId,
                null,
                BLUEPRINT_ID,
                "BP-TYT-MATH-DIAG-2026-V1",
                "TYT Mathematics Diagnostic Baseline",
                AssessmentBlueprintMode.DIAGNOSTIC_BASELINE,
                EXAM_DEF_ID,
                1,
                "s94-pg-seed-" + userId.toString().substring(0, 8),
                "e".repeat(64),
                5,
                900,
                new BigDecimal("1.00"),
                900,
                new AppliedAccommodationSnapshot(new BigDecimal("1.00"), false, false, false),
                new AssemblyDistributionSummary(Map.of("MATH.ARITHMETIC.INTEGER_OPERATIONS", 5), Map.of("EASY", 5), Map.of("RECALL", 5), Map.of("TYT_MATH", 5), 0),
                AssembledAssessmentStatus.ASSEMBLED,
                now,
                now.plusSeconds(86400),
                false,
                java.util.stream.IntStream.rangeClosed(1, 5)
                        .mapToObj(seq -> {
                            UUID qId = seq == 1
                                    ? SEEDED_Q1_ID
                                    : UUID.fromString(String.format("93000000-0000-4000-8000-0000000001%02d", seq));
                            return new AssembledAssessmentItem(
                                    UUID.randomUUID(),
                                    assemblyId,
                                    qId,
                                    "TYT-MATH-2026-INT-00" + seq,
                                    seq,
                                    "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                                    null,
                                    QuestionItemFormat.SINGLE_CHOICE_MCQ,
                                    QuestionDifficultyBand.EASY,
                                    CognitiveTaxonomyLevel.RECALL,
                                    "Evaluate item #" + seq,
                                    null,
                                    null,
                                    false,
                                    List.of("A", "B", "C", "D", "E"),
                                    List.of(
                                            new QuestionOption(UUID.randomUUID(), qId, "A", 1, "0", null, false, null, false),
                                            new QuestionOption(UUID.randomUUID(), qId, "B", 2, "18", null, true, null, false),
                                            new QuestionOption(UUID.randomUUID(), qId, "C", 3, "-18", null, false, null, false),
                                            new QuestionOption(UUID.randomUUID(), qId, "D", 4, "36", null, false, null, false),
                                            new QuestionOption(UUID.randomUUID(), qId, "E", 5, "9", null, false, null, false)
                                    ),
                                    new BigDecimal("1.00"),
                                    60,
                                    false
                            );
                        })
                        .toList()
        );
        assembledAssessmentRepository.save(assembled);

        StartOrResumeResult started = runtimeService.startOrResumeSession(
                userId,
                new StartSessionCommand(userId, assemblyId, 120, now, false)
        );
        assertThat(started.created()).isTrue();
        UUID sessionId = started.session().id();

        StartOrResumeResult resumed = runtimeService.startOrResumeSession(
                userId,
                new StartSessionCommand(userId, assemblyId, 120, now.plusSeconds(5), false)
        );
        assertThat(resumed.created()).isFalse();
        assertThat(resumed.session().id()).isEqualTo(sessionId);

        AutosaveResult autosaved = runtimeService.autosaveItem(
                userId,
                sessionId,
                1,
                "pg-auto-1",
                new AutosaveItemCommand("SELECT_OPTION", "B", null, true, null, 20, 0, 1L, false, now.plusSeconds(20), false)
        );
        assertThat(autosaved.item().responseState()).isEqualTo(ExamItemResponseState.FLAGGED_ANSWERED);
        assertThat(autosaved.item().autosaveVersion()).isEqualTo(1);

        // Stale version fails against PostgreSQL optimistic check
        assertThatThrownBy(() -> runtimeService.autosaveItem(
                userId,
                sessionId,
                1,
                "pg-auto-stale",
                new AutosaveItemCommand("SELECT_OPTION", "C", null, false, null, 5, 0, 2L, false, now.plusSeconds(25), false)
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                        .isEqualTo(ApiErrorCode.EXAM_ITEM_STALE_VERSION));

        // Attach paper deneme scratchpad photo
        String storageKey = "users/" + userId + "/exam-sessions/" + sessionId + "/scratchpad/deneme-page1.jpg";
        ScratchpadAttachResult attached = runtimeService.attachScratchpad(
                userId,
                sessionId,
                1,
                "pg-scratch-1",
                new AttachScratchpadCommand(
                        1,
                        storageKey,
                        "image/jpeg",
                        120_000L,
                        "d".repeat(64),
                        1080,
                        1440,
                        "PAPER_DENEME_BOOKLET",
                        "Bilgi Sarmal TYT",
                        now.plusSeconds(35),
                        false
                )
        );
        assertThat(attached.created()).isTrue();
        assertThat(attached.session().scratchpadAttachmentCount()).isEqualTo(1);

        // Submit session idempotently
        SubmitSessionResult submitted = runtimeService.submitSession(
                userId,
                sessionId,
                "pg-submit-1",
                new SubmitSessionCommand("USER_INITIATED", now.plusSeconds(45), false)
        );
        assertThat(submitted.session().status()).isEqualTo(ExamSessionStatus.SUBMITTED);
        assertThat(submitted.f28Manifest().totalAssets()).isEqualTo(1);
        assertThat(submitted.f28Manifest().assets().getFirst().bookletPublisher()).isEqualTo("Bilgi Sarmal TYT");

        SubmitSessionResult replayed = runtimeService.submitSession(
                userId,
                sessionId,
                "pg-submit-1",
                new SubmitSessionCommand("USER_INITIATED", now.plusSeconds(45), false)
        );
        assertThat(replayed.idempotentReplay()).isTrue();

        ExamSession reloaded = examSessionRepository.findById(sessionId).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ExamSessionStatus.SUBMITTED);
        assertThat(reloaded.trainingEligible()).isFalse();
        assertThat(reloaded.items()).hasSize(5);
        assertThat(reloaded.items().getFirst().scratchpadAssets()).hasSize(1);

        // Verify training_eligible = FALSE at PostgreSQL row level
        Boolean dbTrainingFlag = jdbcClient.sql("SELECT training_eligible FROM exam_sessions WHERE id = :id")
                .param("id", sessionId)
                .query(Boolean.class)
                .single();
        assertThat(dbTrainingFlag).isFalse();

        // Verify strict child-first privacy deletion (1 scratchpad + 3 idempotency + 5 items + 1 session)
        int deleted = examSessionRepository.deleteByUserId(userId);
        assertThat(deleted).isEqualTo(10);
        assertThat(examSessionRepository.findById(sessionId)).isEmpty();
        assembledAssessmentRepository.deleteByUserId(userId);
    }
}
