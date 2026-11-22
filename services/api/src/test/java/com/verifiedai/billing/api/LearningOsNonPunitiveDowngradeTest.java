package com.verifiedai.billing.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.billing.application.EntitlementApplicationService;
import com.verifiedai.billing.application.LearningOsCapability;
import com.verifiedai.exam.application.ActiveExamNoHintGuard;
import com.verifiedai.exam.application.ExamReadinessProjectionService;
import com.verifiedai.exam.application.ExamReadinessSignalReader;
import com.verifiedai.exam.application.ExamRepository;
import com.verifiedai.exam.application.ExamSessionRuntimeService;
import com.verifiedai.exam.application.ExamSessionRuntimeService.StartOrResumeResult;
import com.verifiedai.exam.application.ExamSessionRuntimeService.StartSessionCommand;
import com.verifiedai.exam.config.ExamProperties;
import com.verifiedai.exam.domain.AppliedAccommodationSnapshot;
import com.verifiedai.exam.domain.AssembledAssessment;
import com.verifiedai.exam.domain.AssembledAssessmentItem;
import com.verifiedai.exam.domain.AssembledAssessmentRepository;
import com.verifiedai.exam.domain.AssembledAssessmentStatus;
import com.verifiedai.exam.domain.AssemblyDistributionSummary;
import com.verifiedai.exam.domain.AssessmentBlueprintMode;
import com.verifiedai.exam.domain.CognitiveTaxonomyLevel;
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
import com.verifiedai.exam.domain.ExamReadinessCalculator;
import com.verifiedai.exam.domain.ExamReadinessProjection;
import com.verifiedai.exam.domain.ExamReadinessRepository;
import com.verifiedai.exam.domain.ExamReadinessTopicAllocation;
import com.verifiedai.exam.domain.ExamSession;
import com.verifiedai.exam.domain.QuestionDifficultyBand;
import com.verifiedai.exam.domain.QuestionItemFormat;
import com.verifiedai.exam.domain.QuestionOption;
import com.verifiedai.exam.infrastructure.persistence.ExamSessionRepository;
import com.verifiedai.mistake.LearnerMistakeDnaService;
import com.verifiedai.mistake.MistakeClassificationDecision;
import com.verifiedai.mistake.MistakeDnaEvidenceSource;
import com.verifiedai.mistake.MistakeTaxonomy;
import com.verifiedai.mistake.api.MistakeDnaController;
import com.verifiedai.mistake.infrastructure.MistakeDnaRepository;
import com.verifiedai.mistake.passport.dto.MistakePassportResponseDto;
import com.verifiedai.mistake.passport.dto.MistakePassportResponseDto.CounterexampleProofSnapshot;
import com.verifiedai.mistake.passport.dto.MistakePassportResponseDto.ExamTrapItemDto;
import com.verifiedai.mistake.passport.dto.MistakePassportResponseDto.SkillFiveMetricsSnapshot;
import com.verifiedai.mistake.passport.dto.MistakePassportResponseDto.TrapLifecycleStatus;
import com.verifiedai.mistake.passport.repository.MistakePassportProjectionRepository;
import com.verifiedai.mistake.passport.service.MistakePassportReadModelService;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.ratelimit.RateLimitDecision;
import com.verifiedai.sharedkernel.ratelimit.RateLimiter;
import com.verifiedai.studyplan.coach.DailyCoachMode;
import com.verifiedai.studyplan.coach.DailyCoachSessionEntity;
import com.verifiedai.studyplan.coach.DailyCoachSessionNotFoundException;
import com.verifiedai.studyplan.coach.DailyCoachSessionRepository;
import com.verifiedai.studyplan.coach.DailyCoachSessionService;
import com.verifiedai.studyplan.coach.DailyCoachSessionStatus;
import com.verifiedai.studyplan.coach.DailyCoachTaskEntity;
import com.verifiedai.studyplan.coach.DailyCoachTaskRepository;
import com.verifiedai.studyplan.coach.DailyCoachTaskStatus;
import com.verifiedai.studyplan.coach.DailyCoachTaskType;
import com.verifiedai.studyplan.coach.DeterministicCoachBriefingBuilder;
import com.verifiedai.studyplan.coach.dto.DailyCoachSummaryResponse;
import com.verifiedai.studyplan.coach.dto.DailyCoachTaskCompleteRequest;
import com.verifiedai.studyplan.spaced.SpacedQueueItemNotFoundException;
import com.verifiedai.studyplan.spaced.SpacedQueueStatus;
import com.verifiedai.studyplan.spaced.SpacedRepetitionScheduler;
import com.verifiedai.studyplan.spaced.SpacedReviewQueueEntity;
import com.verifiedai.studyplan.spaced.SpacedReviewQueueRepository;
import com.verifiedai.studyplan.spaced.SpacedReviewService;
import com.verifiedai.studyplan.spaced.SpacedTriggerType;
import com.verifiedai.studyplan.spaced.dto.SpacedItemCompleteRequest;
import com.verifiedai.studyplan.spaced.dto.SpacedItemDismissRequest;
import com.verifiedai.studyplan.spaced.dto.SpacedQueueResponse;
import com.verifiedai.tutoring.application.InMemoryMicroLessonRepository;
import com.verifiedai.tutoring.application.MicroLessonApplicationService;
import com.verifiedai.tutoring.domain.MicroLessonCard;
import com.verifiedai.tutoring.domain.MicroLessonCompletionRecord;
import com.verifiedai.tutoring.domain.MicroLessonCompletionRequest;
import com.verifiedai.tutoring.domain.MicroLessonHashVerifier;
import com.verifiedai.tutoring.domain.MicroLessonTriggerSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

class LearningOsNonPunitiveDowngradeTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID OWNER = UUID.fromString("70000000-0000-4000-8000-000000000001");
    private static final UUID OTHER = UUID.fromString("70000000-0000-4000-8000-000000000002");

    @Test
    void mistakePassportAndDnaRetainReadOnlySummaryOnDowngradeAndOutageWhilePropagatingInactiveAccount() {
        var repo = mock(MistakePassportProjectionRepository.class);
        var proof = new CounterexampleProofSnapshot(
            "H-01", "(a+b)^2=a^2+b^2", "(a+b)^2=a^2+2ab+b^2", "a=2,b=3",
            "25", "13", "25!=13", "tr", "en"
        );
        when(repo.fetchPassportItemsByLearnerId(OWNER)).thenReturn(List.of(
            trapItem("INEQ_NEGATIVE_MULT_NO_FLIP", TrapLifecycleStatus.RECURRING_PATTERN, 5, proof),
            trapItem("ALG_SIGN_FLIP_DISTRIBUTE", TrapLifecycleStatus.IN_REMEDIATION, 3, null),
            trapItem("CALC_CHAIN_RULE_OMITTED", TrapLifecycleStatus.STABLE_RESOLVED, 1, null)
        ));

        MistakePassportReadModelService passportService = new MistakePassportReadModelService(repo);
        ReflectionTestUtils.setField(passportService, "learningOsFlag", "true");
        ReflectionTestUtils.setField(passportService, "access", policyReturning(false));

        MistakePassportResponseDto freeSummary = passportService.getLearnerMistakePassport(OWNER, null);
        assertThat(freeSummary.summaryMetrics().conqueredExamTrapsCount()).isEqualTo(1);
        assertThat(freeSummary.summaryMetrics().activeExamTrapsCount()).isEqualTo(2);
        assertThat(freeSummary.conceptualTraps()).isEmpty();
        assertThat(freeSummary.arithmeticSlips()).isEmpty();
        assertThat(freeSummary.summaryTrapTitleKeys()).containsExactly(
            "trap.ineq_negative_mult_no_flip.title",
            "trap.alg_sign_flip_distribute.title"
        );
        assertThat(freeSummary.earnedProofs()).containsExactly(proof);

        ReflectionTestUtils.setField(passportService, "access", policyThrowing(ApiErrorCode.ENTITLEMENT_SYNC_FAILED, HttpStatus.SERVICE_UNAVAILABLE));
        MistakePassportResponseDto outageSummary = passportService.getLearnerMistakePassport(OWNER, null);
        assertThat(outageSummary.summaryTrapTitleKeys()).hasSize(2);
        assertThat(outageSummary.earnedProofs()).containsExactly(proof);

        ReflectionTestUtils.setField(passportService, "access", policyThrowing(ApiErrorCode.ACCOUNT_NOT_ACTIVE, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> passportService.getLearnerMistakePassport(OWNER, null))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code()).isEqualTo(ApiErrorCode.ACCOUNT_NOT_ACTIVE));

        MistakeDnaRepository dnaRepo = mock(MistakeDnaRepository.class);
        when(dnaRepo.available()).thenReturn(true);
        when(dnaRepo.excluded(OWNER)).thenReturn(Set.of());
        RateLimiter limiter = (p, k) -> RateLimitDecision.allow();
        MistakeDnaEvidenceSource dnaSource = owner -> List.of(
            evidence("ALG_SIGN_FLIP_DISTRIBUTE", NOW.minusSeconds(10)),
            evidence("ALG_FRACTION_ADD_DENOM", NOW.minusSeconds(20)),
            evidence("INEQ_NEGATIVE_MULT_NO_FLIP", NOW.minusSeconds(30))
        );
        LearnerMistakeDnaService dnaService = new LearnerMistakeDnaService(
            dnaRepo, dnaSource, CLOCK, new SimpleMeterRegistry(), limiter, true, true, "test", false
        );
        ReflectionTestUtils.setField(dnaService, "learningOsFlag", "true");
        ReflectionTestUtils.setField(dnaService, "access", policyThrowing(ApiErrorCode.ENTITLEMENT_SYNC_FAILED, HttpStatus.SERVICE_UNAVAILABLE));

        EntitlementApplicationService entitlementSvc = mock(EntitlementApplicationService.class);
        when(entitlementSvc.currentAiTier(OWNER))
            .thenThrow(new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.ENTITLEMENT_SYNC_FAILED, "Billing outage", true, "RETRY"
            ))
            .thenThrow(new ApiProblemException(
                HttpStatus.FORBIDDEN, ApiErrorCode.ACCOUNT_NOT_ACTIVE, "Inactive", false, "SIGN_IN"
            ));
        MistakeDnaController dnaController = new MistakeDnaController(dnaService, entitlementSvc);
        LearnerMistakeDnaService.View dnaView = dnaController.read(jwtFor(OWNER));
        assertThat(dnaView.limited()).isTrue();
        assertThat(dnaView.entries()).isEmpty();
        assertThat(dnaView.profiles()).hasSize(2);

        ReflectionTestUtils.setField(dnaService, "access", policyThrowing(ApiErrorCode.ACCOUNT_NOT_ACTIVE, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> dnaController.read(jwtFor(OWNER)))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code()).isEqualTo(ApiErrorCode.ACCOUNT_NOT_ACTIVE));
    }

    @Test
    void examReadinessAndExamRuntimeRetainPersistedReadAndActiveResumeAfterDowngrade() {
        AtomicBoolean paid = new AtomicBoolean(true);
        CapabilityAccessPolicy togglePolicy = new CapabilityAccessPolicy() {
            @Override
            public boolean check(UUID userId, LearningOsCapability capability) {
                return paid.get() || capability == LearningOsCapability.ATTEMPT_VERIFY || capability == LearningOsCapability.DETERMINISTIC_TWIN;
            }

            @Override
            public void requireBasicSolve(UUID userId) {
            }
        };

        Map<String, ExamReadinessProjection> store = new LinkedHashMap<>();
        ExamReadinessRepository readinessRepo = new ExamReadinessRepository() {
            @Override
            public ExamReadinessProjection saveUpsert(ExamReadinessProjection p) {
                store.put(p.userId() + ":" + p.examCode(), p);
                return p;
            }

            @Override
            public Optional<ExamReadinessProjection> findByUserIdAndExamCode(UUID u, String c) {
                return Optional.ofNullable(store.get(u + ":" + c));
            }

            @Override
            public List<ExamReadinessProjection> findAllByUserId(UUID u) {
                return List.copyOf(store.values());
            }

            @Override
            public List<ExamReadinessTopicAllocation> findTopicAllocationsByUserId(UUID u) {
                return List.of();
            }

            @Override
            public int deleteByUserId(UUID u) {
                return 0;
            }
        };
        ExamReadinessSignalReader signalReader = mock(ExamReadinessSignalReader.class);
        when(signalReader.loadSkillMasterySignals(OWNER)).thenReturn(Map.of());
        when(signalReader.loadActiveTrapSignals(OWNER)).thenReturn(List.of());
        ExamRepository examRepo = mock(ExamRepository.class);
        when(examRepo.findLatestByCode("TR_YKS_TYT_MATH", false)).thenReturn(Optional.of(tytDefinition()));
        when(examRepo.findTargetsByUserId(OWNER)).thenReturn(List.of());
        when(examRepo.findCatalog(null, null, true)).thenReturn(List.of(tytDefinition()));
        ExamProperties examProps = new ExamProperties();
        examProps.setReadinessEnabled(true);

        ExamReadinessProjectionService readinessSvc = new ExamReadinessProjectionService(
            readinessRepo, signalReader, examRepo, null, null,
            new ExamReadinessCalculator(), mock(ApplicationEventPublisher.class),
            examProps, new SimpleMeterRegistry(), CLOCK
        );
        ReflectionTestUtils.setField(readinessSvc, "learningOsFlag", "true");
        ReflectionTestUtils.setField(readinessSvc, "access", togglePolicy);

        ExamReadinessProjection computed = readinessSvc.getOrComputeReadiness(OWNER, null, "TR_YKS_TYT_MATH", true);
        assertThat(computed.examCode()).isEqualTo("TR_YKS_TYT_MATH");

        paid.set(false);
        reset(signalReader);
        ExamReadinessProjection persisted = readinessSvc.getOrComputeReadiness(OWNER, null, "TR_YKS_TYT_MATH", false);
        assertThat(persisted.projectionId()).isEqualTo(computed.projectionId());
        verifyNoInteractions(signalReader);

        assertThatThrownBy(() -> readinessSvc.getOrComputeReadiness(OWNER, null, "TR_YKS_TYT_MATH", true))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code()).isEqualTo(ApiErrorCode.ENTITLEMENT_REQUIRED));

        assertThatThrownBy(() -> readinessSvc.getOrComputeReadiness(OTHER, OWNER, "TR_YKS_TYT_MATH", true))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code()).isEqualTo(ApiErrorCode.EXAM_ACCESS_DENIED));

        // Exam runtime: resume active timed mock after downgrade + start DIAGNOSTIC_BASELINE as FREE + BOLA before gate
        paid.set(true);
        ExamSessionRepository sessionRepo = mock(ExamSessionRepository.class);
        AssembledAssessmentRepository assemblyRepo = mock(AssembledAssessmentRepository.class);
        UUID activeMockAssemblyId = UUID.randomUUID();
        UUID newMockAssemblyId = UUID.randomUUID();
        UUID diagAssemblyId = UUID.randomUUID();
        UUID foreignAssemblyId = UUID.randomUUID();

        when(assemblyRepo.findById(activeMockAssemblyId)).thenReturn(Optional.of(assembly(activeMockAssemblyId, OWNER, AssessmentBlueprintMode.FULL_SIMULATION)));
        when(assemblyRepo.findById(newMockAssemblyId)).thenReturn(Optional.of(assembly(newMockAssemblyId, OWNER, AssessmentBlueprintMode.FULL_SIMULATION)));
        when(assemblyRepo.findById(diagAssemblyId)).thenReturn(Optional.of(assembly(diagAssemblyId, OWNER, AssessmentBlueprintMode.DIAGNOSTIC_BASELINE)));
        when(assemblyRepo.findById(foreignAssemblyId)).thenReturn(Optional.of(assembly(foreignAssemblyId, OTHER, AssessmentBlueprintMode.FULL_SIMULATION)));
        when(sessionRepo.saveSession(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepo.updateSessionHeader(any())).thenAnswer(inv -> inv.getArgument(0));

        ExamSessionRuntimeService runtimeSvc = new ExamSessionRuntimeService(
            sessionRepo, assemblyRepo, examRepo, new ActiveExamNoHintGuard(new SimpleMeterRegistry()),
            mock(ApplicationEventPublisher.class), new SimpleMeterRegistry(), CLOCK, true
        );
        ReflectionTestUtils.setField(runtimeSvc, "learningOsFlag", "true");
        ReflectionTestUtils.setField(runtimeSvc, "access", togglePolicy);

        when(sessionRepo.findActiveByUserIdAndAssemblyId(OWNER, activeMockAssemblyId)).thenReturn(Optional.empty());
        ExamSession startedMock = runtimeSvc.startOrResumeSession(
            OWNER, new StartSessionCommand(OWNER, activeMockAssemblyId, 120, NOW, false)
        ).session();

        paid.set(false);
        when(sessionRepo.findActiveByUserIdAndAssemblyId(OWNER, activeMockAssemblyId)).thenReturn(Optional.of(startedMock));
        StartOrResumeResult resumedMock = runtimeSvc.startOrResumeSession(
            OWNER, new StartSessionCommand(OWNER, activeMockAssemblyId, 120, NOW.plusSeconds(20), false)
        );
        assertThat(resumedMock.created()).isFalse();
        assertThat(resumedMock.session().id()).isEqualTo(startedMock.id());

        StartOrResumeResult diagStarted = runtimeSvc.startOrResumeSession(
            OWNER, new StartSessionCommand(OWNER, diagAssemblyId, 120, NOW, false)
        );
        assertThat(diagStarted.created()).isTrue();

        when(sessionRepo.findActiveByUserIdAndAssemblyId(OWNER, newMockAssemblyId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> runtimeSvc.startOrResumeSession(
            OWNER, new StartSessionCommand(OWNER, newMockAssemblyId, 120, NOW, false)
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code()).isEqualTo(ApiErrorCode.ENTITLEMENT_REQUIRED));

        when(sessionRepo.findActiveByUserIdAndAssemblyId(OWNER, foreignAssemblyId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> runtimeSvc.startOrResumeSession(
            OWNER, new StartSessionCommand(OWNER, foreignAssemblyId, 120, NOW, false)
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code()).isEqualTo(ApiErrorCode.EXAM_CANDIDATE_ACCESS_DENIED));
    }

    @Test
    void microLessonCoachAndSpacedReviewPreserveEarnedHistoryAndEnforceBolaBeforeGate() {
        InMemoryMicroLessonRepository microRepo = new InMemoryMicroLessonRepository();
        microRepo.grantActiveDiagnosis(OWNER, "ALG_SIGN_FLIP_DISTRIBUTE");
        microRepo.grantActiveDiagnosis(OWNER, "ALG_FRACTION_ADD_DENOM");
        microRepo.saveCompletion(new MicroLessonCompletionRecord(
            UUID.randomUUID(), OWNER, UUID.randomUUID(), "ALG_SIGN_FLIP_DISTRIBUTE", "tr-TR",
            null, MicroLessonTriggerSource.COACH_REPAIR_DRAWER, "B", true, 2500, "earned-key-1", false, NOW
        ));
        MicroLessonApplicationService microSvc = new MicroLessonApplicationService(
            microRepo, new MicroLessonHashVerifier(), new SimpleMeterRegistry(), CLOCK, true, 1000, 600000
        );
        ReflectionTestUtils.setField(microSvc, "learningOsFlag", "true");
        ReflectionTestUtils.setField(microSvc, "access", policyReturning(false));

        MicroLessonCard earnedCard = microSvc.getTriggeredMicroLesson(OWNER, "ALG_SIGN_FLIP_DISTRIBUTE", "tr-TR", null, null);
        assertThat(earnedCard.mistakeCode()).isEqualTo("ALG_SIGN_FLIP_DISTRIBUTE");

        assertThatThrownBy(() -> microSvc.getTriggeredMicroLesson(OWNER, "ALG_FRACTION_ADD_DENOM", "tr-TR", null, null))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code()).isEqualTo(ApiErrorCode.ENTITLEMENT_REQUIRED));

        assertThatThrownBy(() -> microSvc.completeMicroLesson(
            OWNER, "ALG_FRACTION_ADD_DENOM",
            new MicroLessonCompletionRequest("tr-TR", "COACH_REPAIR_DRAWER", null, "B", 2000, "new-key-99")
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code()).isEqualTo(ApiErrorCode.ENTITLEMENT_REQUIRED));

        // DailyCoachSessionService: read-only existing summary works, mutations blocked, BOLA 404 before gate
        DailyCoachSessionRepository coachSessionRepo = mock(DailyCoachSessionRepository.class);
        DailyCoachTaskRepository coachTaskRepo = mock(DailyCoachTaskRepository.class);
        UUID sessionId = UUID.randomUUID();
        UUID missingSessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        DailyCoachSessionEntity existingCoach = new DailyCoachSessionEntity(
            sessionId, OWNER, LocalDate.now(CLOCK), 15, DailyCoachSessionStatus.IN_PROGRESS,
            false, false, 0, 1, "Existing summary", DailyCoachMode.SOCRATIC_COACH_MODE, NOW
        );
        DailyCoachTaskEntity task = new DailyCoachTaskEntity(
            taskId, sessionId, 1, DailyCoachTaskType.TRAP_REPAIR, "MATH.ALG",
            "ALG_SIGN_FLIP_DISTRIBUTE", "twin-1", 5, "Focus", "2(x-1)", DailyCoachTaskStatus.PENDING
        );
        when(coachSessionRepo.findByUserIdAndLocalDate(OWNER, LocalDate.now(CLOCK))).thenReturn(Optional.of(existingCoach));
        when(coachSessionRepo.findByIdAndUserId(sessionId, OWNER)).thenReturn(Optional.of(existingCoach));
        when(coachSessionRepo.findByIdAndUserId(missingSessionId, OWNER)).thenReturn(Optional.empty());
        when(coachTaskRepo.findAllBySessionIdOrderByTaskOrderAsc(sessionId)).thenReturn(List.of(task));

        DailyCoachSessionService coachSvc = new DailyCoachSessionService(
            coachSessionRepo, coachTaskRepo, new DeterministicCoachBriefingBuilder(),
            mock(JdbcClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS), CLOCK
        );
        ReflectionTestUtils.setField(coachSvc, "learningOsFlag", "true");
        ReflectionTestUtils.setField(coachSvc, "access", policyReturning(false));

        DailyCoachSummaryResponse summary = coachSvc.getOrCreateTodaySummary(OWNER, LocalDate.now(CLOCK));
        assertThat(summary.sessionId()).isEqualTo(sessionId);

        assertThatThrownBy(() -> coachSvc.downshiftMission(OWNER, missingSessionId))
            .isInstanceOf(DailyCoachSessionNotFoundException.class);
        assertThatThrownBy(() -> coachSvc.downshiftMission(OWNER, sessionId))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code()).isEqualTo(ApiErrorCode.ENTITLEMENT_REQUIRED));
        assertThatThrownBy(() -> coachSvc.completeTask(OWNER, sessionId, new DailyCoachTaskCompleteRequest(taskId, true, 30)))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code()).isEqualTo(ApiErrorCode.ENTITLEMENT_REQUIRED));

        // SpacedReviewService: SERVED items readable with zero writes, empty queue denied, BOLA 404 before gate
        SpacedReviewQueueRepository spacedRepo = mock(SpacedReviewQueueRepository.class);
        UUID servedId = UUID.randomUUID();
        UUID missingItemId = UUID.randomUUID();
        SpacedReviewQueueEntity servedEntity = new SpacedReviewQueueEntity(
            servedId, OWNER, "MATH.ALG.INEQ", "INEQ_NEGATIVE_MULT_NO_FLIP",
            SpacedTriggerType.TRAP_IMMUNITY_7D, new BigDecimal("0.7000"), new BigDecimal("7.00"),
            NOW.minusSeconds(60), UUID.randomUUID(), "x > 1", SpacedQueueStatus.SERVED
        );
        when(spacedRepo.findByUserIdAndStatusIn(OWNER, List.of(SpacedQueueStatus.SERVED))).thenReturn(List.of(servedEntity));
        when(spacedRepo.findByIdAndUserId(servedId, OWNER)).thenReturn(Optional.of(servedEntity));
        when(spacedRepo.findByIdAndUserId(missingItemId, OWNER)).thenReturn(Optional.empty());

        SpacedReviewService spacedSvc = new SpacedReviewService(
            spacedRepo, new SpacedRepetitionScheduler(), mock(JdbcClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS)
        );
        ReflectionTestUtils.setField(spacedSvc, "learningOsFlag", "true");
        ReflectionTestUtils.setField(spacedSvc, "access", policyReturning(false));

        SpacedQueueResponse readOnlyQueue = spacedSvc.getDailySpacedQueue(OWNER, NOW);
        assertThat(readOnlyQueue.items()).hasSize(1);
        verify(spacedRepo, never()).save(any());

        when(spacedRepo.findByUserIdAndStatusIn(OWNER, List.of(SpacedQueueStatus.SERVED))).thenReturn(List.of());
        assertThatThrownBy(() -> spacedSvc.getDailySpacedQueue(OWNER, NOW))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code()).isEqualTo(ApiErrorCode.ENTITLEMENT_REQUIRED));

        assertThatThrownBy(() -> spacedSvc.completeQueueItem(OWNER, missingItemId, new SpacedItemCompleteRequest(true, true, 9000L, null), NOW))
            .isInstanceOf(SpacedQueueItemNotFoundException.class);
        assertThatThrownBy(() -> spacedSvc.dismissQueueItem(OWNER, missingItemId, new SpacedItemDismissRequest("SKIP"), NOW))
            .isInstanceOf(SpacedQueueItemNotFoundException.class);
        assertThatThrownBy(() -> spacedSvc.dismissQueueItem(OWNER, servedId, new SpacedItemDismissRequest("SKIP"), NOW))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code()).isEqualTo(ApiErrorCode.ENTITLEMENT_REQUIRED));
    }

    private static CapabilityAccessPolicy policyReturning(boolean allowed) {
        return new CapabilityAccessPolicy() {
            @Override
            public boolean check(UUID userId, LearningOsCapability capability) {
                return allowed;
            }

            @Override
            public void requireBasicSolve(UUID userId) {
            }
        };
    }

    private static CapabilityAccessPolicy policyThrowing(ApiErrorCode code, HttpStatus status) {
        return new CapabilityAccessPolicy() {
            @Override
            public boolean check(UUID userId, LearningOsCapability capability) {
                throw new ApiProblemException(status, code, "Billing failure", true, "RETRY");
            }

            @Override
            public void requireBasicSolve(UUID userId) {
            }
        };
    }

    private static Jwt jwtFor(UUID userId) {
        return new Jwt(
            "synthetic-jwt",
            NOW.minusSeconds(60),
            NOW.plusSeconds(3600),
            Map.of("alg", "none"),
            Map.of("sub", userId.toString(), "sid", UUID.randomUUID().toString())
        );
    }

    private static ExamTrapItemDto trapItem(String code, TrapLifecycleStatus status, int count, CounterexampleProofSnapshot proof) {
        return new ExamTrapItemDto(
            UUID.randomUUID(), code, "CONCEPTUAL_STRUCTURAL", "MATH.ALG",
            "trap." + code.toLowerCase(java.util.Locale.ROOT) + ".title",
            "trap." + code.toLowerCase(java.util.Locale.ROOT) + ".framing",
            status, count, 0, NOW, null, null,
            new SkillFiveMetricsSnapshot(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO),
            null, proof
        );
    }

    private static MistakeDnaEvidenceSource.Evidence evidence(String code, Instant at) {
        MistakeTaxonomy.Category cat = MistakeTaxonomy.CATEGORIES.get(code);
        String skill = MistakeTaxonomy.SKILLS.get(code);
        var decision = new MistakeClassificationDecision(
            MistakeClassificationDecision.Status.SIGNATURE_SUPPORTED,
            code,
            cat,
            MistakeClassificationDecision.Authority.DETERMINISTIC_EVIDENCE,
            MistakeClassificationDecision.Strength.STRUCTURAL_AND_DISPROOF,
            MistakeClassificationDecision.Review.AUTO_DETECTED,
            skill,
            "attempt.mistake." + code
        );
        return new MistakeDnaEvidenceSource.Evidence(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            1, at, decision, UUID.randomUUID(), skill, true, false
        );
    }

    private static ExamDefinition tytDefinition() {
        UUID defId = UUID.fromString("91000000-0000-4000-8000-000000000101");
        return new ExamDefinition(
            defId, "TR_YKS_TYT_MATH", "2026.1", Jurisdiction.TR, ExamFamily.YKS,
            "YKS TYT Math", "YKS TYT Matematik", ExamStatus.ACTIVE, 40, 65,
            ScoringFormulaType.CORRECT_MINUS_QUARTER_WRONG, new BigDecimal("0.2500"),
            new BigDecimal("-10.00"), new BigDecimal("40.00"), NavigationPolicy.FREE_JUMP,
            "b".repeat(64), Instant.parse("2026-01-01T00:00:00Z"), null, null,
            List.of(new ExamSectionSpec("TYT_MATH", "TYT Math", "TYT Matematik", 40, 65, 1)),
            new ScoringParameters(null, null, 1, null, null, List.of()),
            List.of(new ExamTopicWeight(
                UUID.randomUUID(), defId, "TYT_MATH", "MATH.ALG.INEQ",
                "MATH.ALG", new BigDecimal("20.0000"), 15, 25,
                new CognitiveLevelMix(0.3, 0.5, 0.2), 1
            )),
            false, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private static AssembledAssessment assembly(UUID assemblyId, UUID owner, AssessmentBlueprintMode mode) {
        List<AssembledAssessmentItem> items = new ArrayList<>();
        for (int seq = 1; seq <= 5; seq++) {
            UUID qId = UUID.fromString(String.format("93000000-0000-4000-8000-00000000020%d", seq));
            items.add(new AssembledAssessmentItem(
                UUID.randomUUID(), assemblyId, qId, "TYT-NP-00" + seq, seq, "MATH.ALG.INEQ",
                null, QuestionItemFormat.SINGLE_CHOICE_MCQ, QuestionDifficultyBand.EASY,
                CognitiveTaxonomyLevel.RECALL, "Item " + seq, null, null, false,
                List.of("A", "B", "C", "D"),
                List.of(
                    new QuestionOption(UUID.randomUUID(), qId, "A", 1, "1", null, false, null, false),
                    new QuestionOption(UUID.randomUUID(), qId, "B", 2, "2", null, true, null, false),
                    new QuestionOption(UUID.randomUUID(), qId, "C", 3, "3", null, false, null, false),
                    new QuestionOption(UUID.randomUUID(), qId, "D", 4, "4", null, false, null, false)
                ),
                new BigDecimal("1.00"), 60, false
            ));
        }
        return new AssembledAssessment(
            assemblyId, owner, null, UUID.randomUUID(), "BP-TYT-V1", mode.name(),
            mode, UUID.fromString("91000000-0000-4000-8000-000000000101"), 1,
            "seed-np-v1", "c".repeat(64), 5, 900, new BigDecimal("1.00"), 900,
            new AppliedAccommodationSnapshot(new BigDecimal("1.00"), false, false, false),
            new AssemblyDistributionSummary(Map.of(), Map.of(), Map.of(), Map.of(), 0),
            AssembledAssessmentStatus.ASSEMBLED, NOW, NOW.plusSeconds(86400), false, items
        );
    }
}
