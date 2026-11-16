package com.verifiedai.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.verifiedai.exam.application.ActiveExamNoHintGuard.HintGuardResult;
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
import com.verifiedai.exam.domain.ExamScratchpadAsset;
import com.verifiedai.exam.domain.ExamSession;
import com.verifiedai.exam.domain.ExamSessionItem;
import com.verifiedai.exam.domain.ExamSessionStatus;
import com.verifiedai.exam.domain.QuestionDifficultyBand;
import com.verifiedai.exam.domain.QuestionItemFormat;
import com.verifiedai.exam.domain.QuestionOption;
import com.verifiedai.exam.events.ExamItemAnsweredEvent;
import com.verifiedai.exam.events.ExamScratchpadAttachedEvent;
import com.verifiedai.exam.events.ExamSessionStartedEvent;
import com.verifiedai.exam.events.ExamSessionSubmittedEvent;
import com.verifiedai.exam.infrastructure.persistence.ExamSessionRepository;
import com.verifiedai.exam.infrastructure.persistence.ExamSessionRepository.IdempotencyRecord;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

class ExamSessionRuntimeServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");
    private static final UUID USER_ID = UUID.fromString("91000000-0000-4000-8000-000000000a01");
    private static final UUID OTHER_USER_ID = UUID.fromString("91000000-0000-4000-8000-000000000a99");
    private static final UUID ASSEMBLY_ID = UUID.fromString("94100000-0000-4000-8000-000000000001");
    private static final UUID BLUEPRINT_ID = UUID.fromString("94000000-0000-4000-8000-000000000101");
    private static final UUID EXAM_DEF_ID = UUID.fromString("91000000-0000-4000-8000-000000000101");

    private ExamSessionRepository sessionRepository;
    private AssembledAssessmentRepository assemblyRepository;
    private ExamRepository examRepository;
    private ApplicationEventPublisher eventPublisher;
    private SimpleMeterRegistry meterRegistry;
    private MutableClock clock;
    private ExamSessionRuntimeService service;

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(T0);

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }

        void advanceSeconds(long seconds) {
            current.updateAndGet(i -> i.plusSeconds(seconds));
        }
    }

    @BeforeEach
    void setUp() {
        sessionRepository = mock(ExamSessionRepository.class);
        assemblyRepository = mock(AssembledAssessmentRepository.class);
        examRepository = mock(ExamRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        meterRegistry = new SimpleMeterRegistry();
        clock = new MutableClock();

        service = new ExamSessionRuntimeService(
                sessionRepository,
                assemblyRepository,
                examRepository,
                new ActiveExamNoHintGuard(meterRegistry),
                eventPublisher,
                meterRegistry,
                clock,
                true
        );

        when(examRepository.findCatalog(null, null, true)).thenReturn(List.of());
        when(sessionRepository.saveSession(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.updateSessionHeader(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.updateSessionAndItem(any(), any(), anyInt())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.saveScratchpadAsset(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void startOrResumeStartsSessionWithServerClockAndResumesIdempotently() {
        AssembledAssessment assembly = sampleAssembly();
        when(assemblyRepository.findById(ASSEMBLY_ID)).thenReturn(Optional.of(assembly));
        when(sessionRepository.findActiveByUserIdAndAssemblyId(USER_ID, ASSEMBLY_ID)).thenReturn(Optional.empty());

        StartOrResumeResult started = service.startOrResumeSession(
                USER_ID,
                new StartSessionCommand(USER_ID, ASSEMBLY_ID, 120, T0, false)
        );
        assertThat(started.created()).isTrue();
        assertThat(started.session().effectiveDurationSeconds()).isEqualTo(1125);
        assertThat(started.session().noHintPolicyEnforced()).isTrue();
        assertThat(started.session().trainingEligible()).isFalse();
        assertThat(started.clockSync().remainingSeconds()).isEqualTo(1125);
        verify(eventPublisher).publishEvent(any(ExamSessionStartedEvent.class));

        // Resume active session after 45s
        clock.advanceSeconds(45);
        when(sessionRepository.findActiveByUserIdAndAssemblyId(USER_ID, ASSEMBLY_ID))
                .thenReturn(Optional.of(started.session()));

        StartOrResumeResult resumed = service.startOrResumeSession(
                USER_ID,
                new StartSessionCommand(USER_ID, ASSEMBLY_ID, 120, clock.instant(), false)
        );
        assertThat(resumed.created()).isFalse();
        assertThat(resumed.clockSync().remainingSeconds()).isEqualTo(1080);
    }

    @Test
    void featureFlagDisabledAndBolaViolationsFailClosed() {
        service.setSessionRuntimeEnabled(false);
        assertThatThrownBy(() -> service.startOrResumeSession(
                USER_ID,
                new StartSessionCommand(USER_ID, ASSEMBLY_ID, 120, T0, false)
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException problem = (ApiProblemException) ex;
                    assertThat(problem.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(problem.code()).isEqualTo(ApiErrorCode.EXAM_SESSION_RUNTIME_FEATURE_DISABLED);
                });

        service.setSessionRuntimeEnabled(true);
        assertThatThrownBy(() -> service.startOrResumeSession(
                OTHER_USER_ID,
                new StartSessionCommand(USER_ID, ASSEMBLY_ID, 120, T0, false)
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).status()).isEqualTo(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.startOrResumeSession(
                USER_ID,
                new StartSessionCommand(USER_ID, ASSEMBLY_ID, 120, T0, true)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trainingEligible must be false");
    }

    @Test
    void autosaveReplaysIdempotencyKeyRejectsMismatchAndPublishesDomainEvent() {
        AssembledAssessment assembly = sampleAssembly();
        when(assemblyRepository.findById(ASSEMBLY_ID)).thenReturn(Optional.of(assembly));
        when(sessionRepository.findActiveByUserIdAndAssemblyId(USER_ID, ASSEMBLY_ID)).thenReturn(Optional.empty());
        ExamSession session = service.startOrResumeSession(
                USER_ID,
                new StartSessionCommand(USER_ID, ASSEMBLY_ID, 120, T0, false)
        ).session();

        when(sessionRepository.findById(session.id())).thenReturn(Optional.of(session));
        when(sessionRepository.findIdempotencyRecord(session.id(), "ITEM_AUTOSAVE", "auto-key-1"))
                .thenReturn(Optional.empty());

        AutosaveItemCommand cmd = new AutosaveItemCommand(
                "SELECT_OPTION", "B", null, true, null, 15, 0, 1L, false, T0.plusSeconds(15), false
        );
        AutosaveResult saved = service.autosaveItem(USER_ID, session.id(), 1, "auto-key-1", cmd);
        assertThat(saved.idempotentReplay()).isFalse();
        assertThat(saved.item().selectedOptionKey()).isEqualTo("B");
        assertThat(saved.item().autosaveVersion()).isEqualTo(1);
        verify(eventPublisher).publishEvent(any(ExamItemAnsweredEvent.class));
        verify(sessionRepository).saveIdempotencyRecord(any(IdempotencyRecord.class));

        // Capture saved record to verify mismatch check vs exact replay
        var captor = org.mockito.ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(sessionRepository).saveIdempotencyRecord(captor.capture());
        IdempotencyRecord recorded = captor.getValue();

        when(sessionRepository.findIdempotencyRecord(session.id(), "ITEM_AUTOSAVE", "auto-key-1"))
                .thenReturn(Optional.of(recorded));

        AutosaveResult replayed = service.autosaveItem(USER_ID, session.id(), 1, "auto-key-1", cmd);
        assertThat(replayed.idempotentReplay()).isTrue();
        assertThat(replayed.cachedResponse()).isNotEmpty();

        AutosaveItemCommand conflictingCmd = new AutosaveItemCommand(
                "SELECT_OPTION", "C", null, false, null, 15, 0, 1L, false, T0.plusSeconds(15), false
        );
        assertThatThrownBy(() -> service.autosaveItem(USER_ID, session.id(), 1, "auto-key-1", conflictingCmd))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                        .isEqualTo(ApiErrorCode.EXAM_SESSION_IDEMPOTENCY_CONFLICT));
    }

    @Test
    void scratchpadUploadAndSubmitBuildF28ManifestAndNoHintGuardEnforcesZeroHints() {
        AssembledAssessment assembly = sampleAssembly();
        when(assemblyRepository.findById(ASSEMBLY_ID)).thenReturn(Optional.of(assembly));
        when(sessionRepository.findActiveByUserIdAndAssemblyId(USER_ID, ASSEMBLY_ID)).thenReturn(Optional.empty());
        ExamSession session = service.startOrResumeSession(
                USER_ID,
                new StartSessionCommand(USER_ID, ASSEMBLY_ID, 120, T0, false)
        ).session();

        when(sessionRepository.findById(session.id())).thenReturn(Optional.of(session));
        when(sessionRepository.findIdempotencyRecord(any(), anyString(), anyString())).thenReturn(Optional.empty());

        // Active session -> No-Hint Guard blocks hints with 0 LLM tokens
        HintGuardResult blocked = service.checkNoHintGuard(USER_ID, session.id(), "SOCRATIC_HINT_DRAWER");
        assertThat(blocked.hintAllowed()).isFalse();
        assertThat(blocked.policyCode()).isEqualTo(ApiErrorCode.EXAM_NO_HINT_MODE_ACTIVE.name());
        assertThat(blocked.llmTokensUsed()).isZero();

        String key = "users/" + USER_ID + "/exam-sessions/" + session.id() + "/scratchpad/p1.jpg";
        ScratchpadAttachResult attached = service.attachScratchpad(
                USER_ID,
                session.id(),
                1,
                "scratch-key-1",
                new AttachScratchpadCommand(
                        1,
                        key,
                        "image/jpeg",
                        80_000L,
                        "c".repeat(64),
                        1080,
                        1440,
                        "PAPER_DENEME_BOOKLET",
                        "Karekök TYT",
                        T0.plusSeconds(40),
                        false
                )
        );
        assertThat(attached.created()).isTrue();
        assertThat(attached.asset().captureSource()).isEqualTo("PAPER_DENEME_BOOKLET");
        assertThat(attached.asset().f28VerificationStatus()).isEqualTo("QUEUED_FOR_POST_SUBMIT_BATCH");
        verify(eventPublisher).publishEvent(any(ExamScratchpadAttachedEvent.class));

        when(sessionRepository.findById(session.id())).thenReturn(Optional.of(attached.session()));
        SubmitSessionResult submitted = service.submitSession(
                USER_ID,
                session.id(),
                "submit-key-9",
                new SubmitSessionCommand("USER_INITIATED", T0.plusSeconds(60), false)
        );
        assertThat(submitted.session().status()).isEqualTo(ExamSessionStatus.SUBMITTED);
        assertThat(submitted.f28Manifest().batchStatus()).isEqualTo("QUEUED_FOR_F28_BATCH");
        assertThat(submitted.f28Manifest().totalAssets()).isEqualTo(1);
        assertThat(submitted.f28Manifest().llmTokensUsed()).isZero();
        verify(eventPublisher).publishEvent(any(ExamSessionSubmittedEvent.class));

        // After submission -> No-Hint Guard allows review
        when(sessionRepository.findById(session.id())).thenReturn(Optional.of(submitted.session()));
        HintGuardResult allowed = service.checkNoHintGuard(USER_ID, session.id(), "POST_EXAM_REVIEW");
        assertThat(allowed.hintAllowed()).isTrue();
    }

    @Test
    void offlineReplayWithinGraceBufferSucceedsWhileExpiredGraceAutoExpiresSession() {
        AssembledAssessment assembly = sampleAssembly();
        when(assemblyRepository.findById(ASSEMBLY_ID)).thenReturn(Optional.of(assembly));
        when(sessionRepository.findActiveByUserIdAndAssemblyId(USER_ID, ASSEMBLY_ID)).thenReturn(Optional.empty());
        ExamSession session = service.startOrResumeSession(
                USER_ID,
                new StartSessionCommand(USER_ID, ASSEMBLY_ID, 120, T0, false)
        ).session();

        // Advance clock into grace window (+1125s deadline + 30s inside 120s grace)
        clock.advanceSeconds(1155);
        when(sessionRepository.findById(session.id())).thenReturn(Optional.of(session));
        when(sessionRepository.findIdempotencyRecord(any(), anyString(), anyString())).thenReturn(Optional.empty());

        AutosaveResult graceSave = service.autosaveItem(
                USER_ID,
                session.id(),
                1,
                "grace-save-1",
                new AutosaveItemCommand(
                        "SELECT_OPTION", "A", null, false, null, 10, 0, 1L, true, T0.plusSeconds(1100), false
                )
        );
        assertThat(graceSave.session().status()).isEqualTo(ExamSessionStatus.OFFLINE_GRACE_BUFFER);
        assertThat(graceSave.clockSync().offlineGraceActive()).isTrue();

        // Advance past grace deadline (+120s total grace exhausted)
        clock.advanceSeconds(100);
        when(sessionRepository.findById(session.id())).thenReturn(Optional.of(graceSave.session()));
        assertThatThrownBy(() -> service.autosaveItem(
                USER_ID,
                session.id(),
                1,
                "grace-save-2",
                new AutosaveItemCommand(
                        "SELECT_OPTION", "C", null, false, null, 10, 1, 2L, true, clock.instant(), false
                )
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                        .isEqualTo(ApiErrorCode.EXAM_SESSION_EXPIRED));
    }

    @Test
    void triggersDeterministicAutoScoringOnSubmitAndAutoExpire() {
        ExamScoringEngineService scoringMock = mock(ExamScoringEngineService.class);
        when(scoringMock.isScoringEngineEnabled()).thenReturn(true);
        service.setExamScoringEngineService(scoringMock);

        AssembledAssessment assembly = sampleAssembly();
        when(assemblyRepository.findById(ASSEMBLY_ID)).thenReturn(Optional.of(assembly));
        when(sessionRepository.findActiveByUserIdAndAssemblyId(USER_ID, ASSEMBLY_ID)).thenReturn(Optional.empty());
        ExamSession session = service.startOrResumeSession(
                USER_ID,
                new StartSessionCommand(USER_ID, ASSEMBLY_ID, 120, T0, false)
        ).session();

        when(sessionRepository.findById(session.id())).thenReturn(Optional.of(session));
        when(sessionRepository.findIdempotencyRecord(any(), anyString(), anyString())).thenReturn(Optional.empty());

        service.submitSession(
                USER_ID,
                session.id(),
                "submit-autoscore-1",
                new SubmitSessionCommand("USER_INITIATED", T0.plusSeconds(45), false)
        );
        verify(scoringMock, org.mockito.Mockito.times(1)).computeOrGetScoreSummary(USER_ID, session.id());

        // Second session auto-expired on past-grace autosave also triggers deterministic scoring
        UUID expiredSessionId = UUID.randomUUID();
        ExamSession secondActive = new ExamSession(
                expiredSessionId, USER_ID, ASSEMBLY_ID, BLUEPRINT_ID, session.examCode(), 1,
                ExamSessionStatus.OFFLINE_GRACE_BUFFER, session.navigationPolicy(), true,
                session.accommodationsSnapshot(), session.baseDurationSeconds(), session.accommodationsSnapshot().timeMultiplier(),
                session.effectiveDurationSeconds(), session.offlineGraceBufferSeconds(),
                T0, T0.plusSeconds(1125), T0.plusSeconds(1245), T0, T0.plusSeconds(1150),
                null, null, null, 1, 100, 0, 0, 5, 0L, null, false, T0, T0, session.items()
        );
        clock.advanceSeconds(1300);
        when(sessionRepository.findById(expiredSessionId)).thenReturn(Optional.of(secondActive));
        assertThatThrownBy(() -> service.autosaveItem(
                USER_ID,
                expiredSessionId,
                1,
                "auto-expire-hook-1",
                new AutosaveItemCommand(
                        "SELECT_OPTION", "B", null, false, null, 10, 0, 1L, true, clock.instant(), false
                )
        )).isInstanceOf(ApiProblemException.class);
        verify(scoringMock, org.mockito.Mockito.times(1)).computeOrGetScoreSummary(USER_ID, expiredSessionId);
    }

    private static AssembledAssessment sampleAssembly() {
        UUID item1QuestionId = UUID.fromString("93000000-0000-4000-8000-000000000101");
        List<AssembledAssessmentItem> items = new java.util.ArrayList<>();
        for (int seq = 1; seq <= 5; seq++) {
            UUID qId = seq == 1
                    ? item1QuestionId
                    : UUID.fromString(String.format("93000000-0000-4000-8000-00000000010%d", seq));
            items.add(new AssembledAssessmentItem(
                    UUID.randomUUID(),
                    ASSEMBLY_ID,
                    qId,
                    "TYT-MATH-2026-INT-00" + seq,
                    seq,
                    "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                    null,
                    QuestionItemFormat.SINGLE_CHOICE_MCQ,
                    QuestionDifficultyBand.EASY,
                    CognitiveTaxonomyLevel.RECALL,
                    "Sample item " + seq + ": Evaluate (-18) + 27 - (-9)",
                    null,
                    null,
                    false,
                    List.of("A", "B", "C", "D"),
                    List.of(
                            new QuestionOption(UUID.randomUUID(), qId, "A", 1, "0", null, false, null, false),
                            new QuestionOption(UUID.randomUUID(), qId, "B", 2, "18", null, true, null, false),
                            new QuestionOption(UUID.randomUUID(), qId, "C", 3, "-18", null, false, null, false),
                            new QuestionOption(UUID.randomUUID(), qId, "D", 4, "36", null, false, null, false)
                    ),
                    new BigDecimal("1.00"),
                    60,
                    false
            ));
        }
        return new AssembledAssessment(
                ASSEMBLY_ID,
                USER_ID,
                null,
                BLUEPRINT_ID,
                "BP-TYT-MATH-DIAG-2026-V1",
                "TYT Mathematics Diagnostic Baseline",
                AssessmentBlueprintMode.DIAGNOSTIC_BASELINE,
                EXAM_DEF_ID,
                1,
                "seed-runtime-test",
                "f".repeat(64),
                5,
                900,
                new BigDecimal("1.25"),
                1125,
                new AppliedAccommodationSnapshot(new BigDecimal("1.25"), false, false, false),
                new AssemblyDistributionSummary(Map.of(), Map.of(), Map.of(), Map.of(), 0),
                AssembledAssessmentStatus.ASSEMBLED,
                T0,
                T0.plusSeconds(86400),
                false,
                items
        );
    }
}
