package com.verifiedai.billing.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.verifiedai.attempt.application.AttemptEvaluationPreparer;
import com.verifiedai.attempt.application.AttemptEvaluationService;
import com.verifiedai.attempt.application.AttemptVerifierClient;
import com.verifiedai.attempt.domain.AttemptWorkInput;
import com.verifiedai.attempt.application.AttemptWorkNormalizer;
import com.verifiedai.attempt.application.AttemptWorkService;
import com.verifiedai.problem.application.attempt.ManualStepNormalizer;
import com.verifiedai.attempt.domain.AttemptSourceIdentity;
import com.verifiedai.attempt.infrastructure.persistence.AttemptEvaluationRepository;
import com.verifiedai.problem.application.attempt.AttemptExtractionReader;
import com.verifiedai.attempt.infrastructure.persistence.AttemptWorkRepository;
import com.verifiedai.attempt.infrastructure.persistence.StudentAttemptRepository;
import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.billing.application.LearningOsCapability;
import com.verifiedai.exam.api.ExamReadinessController;
import com.verifiedai.exam.api.ExamSessionRuntimeController;
import com.verifiedai.exam.application.ActiveExamNoHintGuard;
import com.verifiedai.exam.application.ExamReadinessProjectionService;
import com.verifiedai.exam.application.ExamReadinessSignalReader;
import com.verifiedai.exam.application.ExamRepository;
import com.verifiedai.exam.application.ExamSessionRuntimeService;
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
import com.verifiedai.exam.domain.ExamReadinessRepository;
import com.verifiedai.exam.domain.QuestionDifficultyBand;
import com.verifiedai.exam.domain.QuestionItemFormat;
import com.verifiedai.exam.domain.QuestionOption;
import com.verifiedai.exam.infrastructure.persistence.ExamSessionRepository;
import com.verifiedai.mastery.AttemptQueryPort;
import com.verifiedai.mastery.VerifiedAttemptSnapshot;
import com.verifiedai.mastery.application.CounterfactualTwinChallengeService;
import com.verifiedai.mastery.application.GeneratedTwinSpec;
import com.verifiedai.mastery.application.MathVerifierTwinPort;
import com.verifiedai.mastery.domain.CounterfactualTwinRepository;
import com.verifiedai.mastery.domain.CounterfactualTwinSubmissionRepository;
import com.verifiedai.mastery.domain.InMemoryMasteryStateLockStore;
import com.verifiedai.mastery.domain.LearnerSkillMasteryRepository;
import com.verifiedai.mastery.web.CounterfactualTwinController;
import com.verifiedai.mistake.LearnerMistakeDnaService;
import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiExceptionHandler;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.ratelimit.RateLimiter;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.studyplan.coach.DailyCoachController;
import com.verifiedai.studyplan.coach.DailyCoachMode;
import com.verifiedai.studyplan.coach.DailyCoachSessionEntity;
import com.verifiedai.studyplan.coach.DailyCoachSessionRepository;
import com.verifiedai.studyplan.coach.DailyCoachSessionService;
import com.verifiedai.studyplan.coach.DailyCoachSessionStatus;
import com.verifiedai.studyplan.coach.DailyCoachTaskEntity;
import com.verifiedai.studyplan.coach.DailyCoachTaskRepository;
import com.verifiedai.studyplan.coach.DailyCoachTaskStatus;
import com.verifiedai.studyplan.coach.DailyCoachTaskType;
import com.verifiedai.studyplan.coach.DeterministicCoachBriefingBuilder;
import com.verifiedai.studyplan.spaced.SpacedQueueStatus;
import com.verifiedai.studyplan.spaced.SpacedRepetitionScheduler;
import com.verifiedai.studyplan.spaced.SpacedReviewController;
import com.verifiedai.studyplan.spaced.SpacedReviewQueueEntity;
import com.verifiedai.studyplan.spaced.SpacedReviewQueueRepository;
import com.verifiedai.studyplan.spaced.SpacedReviewService;
import com.verifiedai.studyplan.spaced.SpacedTriggerType;
import com.verifiedai.tutoring.api.MicroLessonController;
import com.verifiedai.tutoring.application.InMemoryMicroLessonRepository;
import com.verifiedai.tutoring.application.MicroLessonApplicationService;
import com.verifiedai.tutoring.domain.MicroLessonHashVerifier;
import com.verifiedai.verification.application.VerificationSourceReader;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class LearningOsFeatureGuardMockMvcTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID ATTEMPT_ID = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID COACH_SESSION_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");
    private static final UUID COACH_TASK_ID = UUID.fromString("40000000-0000-4000-8000-000000000002");
    private static final UUID SPACED_ITEM_ID = UUID.fromString("50000000-0000-4000-8000-000000000001");
    private static final UUID TIMED_MOCK_ASSEMBLY_ID = UUID.fromString("60000000-0000-4000-8000-000000000001");

    private final AtomicReference<Set<LearningOsCapability>> grantedCapabilities = new AtomicReference<>(Set.of());
    private final AtomicBoolean syncUnavailable = new AtomicBoolean(false);

    private MockMvc mockMvc;
    private CapabilityAccessPolicy stubPolicy;

    private MathVerifierTwinPort mathVerifierTwinPort;
    private CounterfactualTwinRepository twinRepository;
    private DailyCoachSessionRepository coachSessionRepository;
    private DailyCoachTaskRepository coachTaskRepository;
    private SpacedReviewQueueRepository spacedRepository;
    private InMemoryMicroLessonRepository microLessonRepository;
    private ExamReadinessSignalReader readinessSignalReader;
    private ExamReadinessRepository readinessRepository;
    private ExamSessionRepository examSessionRepository;
    private ApplicationEventPublisher eventPublisher;

    private CounterfactualTwinChallengeService twinService;
    private DailyCoachSessionService coachService;
    private SpacedReviewService spacedService;
    private MicroLessonApplicationService microLessonService;
    private ExamReadinessProjectionService readinessService;
    private ExamSessionRuntimeService examRuntimeService;

    @BeforeEach
    void setUp() {
        syncUnavailable.set(false);
        grantedCapabilities.set(EnumSet.of(LearningOsCapability.ATTEMPT_VERIFY, LearningOsCapability.DETERMINISTIC_TWIN));

        stubPolicy = new CapabilityAccessPolicy() {
            @Override
            public boolean check(UUID userId, LearningOsCapability capability) {
                if (syncUnavailable.get()) {
                    throw new ApiProblemException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        ApiErrorCode.ENTITLEMENT_SYNC_FAILED,
                        "Entitlement verification unavailable",
                        true,
                        "RETRY"
                    );
                }
                return grantedCapabilities.get().contains(capability);
            }

            @Override
            public void requireBasicSolve(UUID userId) {
            }
        };

        AttemptQueryPort attemptQueryPort = mock(AttemptQueryPort.class);
        when(attemptQueryPort.getVerifiedAttemptForTwinGeneration(TENANT_ID, USER_ID, ATTEMPT_ID))
            .thenReturn(Optional.of(new VerifiedAttemptSnapshot(
                ATTEMPT_ID, TENANT_ID, USER_ID, "TR_YKS_TYT",
                "MATH.ALG.INEQ", "MATH.ALG.INEQ", Map.of("expr", "x>1"), true
            )));
        mathVerifierTwinPort = mock(MathVerifierTwinPort.class);
        when(mathVerifierTwinPort.requiresHybridAccess()).thenReturn(true);
        when(mathVerifierTwinPort.synthesizeIsomorphicTwin(any(), any(), any(), any(), any()))
            .thenReturn(new GeneratedTwinSpec("2x > 6", "x>3", "[]", "seed-1", "a".repeat(64)));
        twinRepository = mock(CounterfactualTwinRepository.class);
        when(twinRepository.findActiveIssuedBySourceAttempt(TENANT_ID, USER_ID, ATTEMPT_ID)).thenReturn(Optional.empty());

        twinService = new CounterfactualTwinChallengeService(
            new InMemoryMasteryStateLockStore(),
            attemptQueryPort,
            mathVerifierTwinPort,
            twinRepository,
            mock(CounterfactualTwinSubmissionRepository.class),
            mock(LearnerSkillMasteryRepository.class),
            CLOCK
        );

        coachSessionRepository = mock(DailyCoachSessionRepository.class);
        coachTaskRepository = mock(DailyCoachTaskRepository.class);
        DailyCoachSessionEntity coachSession = new DailyCoachSessionEntity(
            COACH_SESSION_ID, USER_ID, LocalDate.now(CLOCK), 15,
            DailyCoachSessionStatus.IN_PROGRESS, false, false, 0, 1,
            "Briefing", DailyCoachMode.SOCRATIC_COACH_MODE, NOW
        );
        DailyCoachTaskEntity coachTask = new DailyCoachTaskEntity(
            COACH_TASK_ID, COACH_SESSION_ID, 1, DailyCoachTaskType.TRAP_REPAIR,
            "MATH.ALG.INEQ", "INEQ_NEGATIVE_MULT_NO_FLIP", "twin-1", 5,
            "Focus", "x < 2", DailyCoachTaskStatus.PENDING
        );
        when(coachSessionRepository.findByIdAndUserId(COACH_SESSION_ID, USER_ID)).thenReturn(Optional.of(coachSession));
        when(coachTaskRepository.findAllBySessionIdOrderByTaskOrderAsc(COACH_SESSION_ID)).thenReturn(List.of(coachTask));
        when(coachSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(coachTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        coachService = new DailyCoachSessionService(
            coachSessionRepository,
            coachTaskRepository,
            new DeterministicCoachBriefingBuilder(),
            mock(JdbcClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS),
            CLOCK
        );

        spacedRepository = mock(SpacedReviewQueueRepository.class);
        SpacedReviewQueueEntity spacedItem = new SpacedReviewQueueEntity(
            SPACED_ITEM_ID, USER_ID, "MATH.ALG.INEQ", "INEQ_NEGATIVE_MULT_NO_FLIP",
            SpacedTriggerType.TRAP_IMMUNITY_7D, new BigDecimal("0.7100"), new BigDecimal("7.00"),
            NOW.minusSeconds(60), UUID.randomUUID(), "x > 1", SpacedQueueStatus.SERVED
        );
        when(spacedRepository.findByIdAndUserId(SPACED_ITEM_ID, USER_ID)).thenReturn(Optional.of(spacedItem));
        when(spacedRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        spacedService = new SpacedReviewService(
            spacedRepository,
            new SpacedRepetitionScheduler(),
            mock(JdbcClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS)
        );

        microLessonRepository = new InMemoryMicroLessonRepository();
        microLessonRepository.grantActiveDiagnosis(USER_ID, "ALG_SIGN_FLIP_DISTRIBUTE");
        microLessonService = new MicroLessonApplicationService(
            microLessonRepository,
            new MicroLessonHashVerifier(),
            new SimpleMeterRegistry(),
            CLOCK,
            true,
            1000,
            600000
        );

        readinessRepository = mock(ExamReadinessRepository.class);
        when(readinessRepository.findByUserIdAndExamCode(USER_ID, "TR_YKS_TYT_MATH")).thenReturn(Optional.empty());
        when(readinessRepository.saveUpsert(any())).thenAnswer(inv -> inv.getArgument(0));
        readinessSignalReader = mock(ExamReadinessSignalReader.class);
        when(readinessSignalReader.loadSkillMasterySignals(USER_ID)).thenReturn(Map.of());
        when(readinessSignalReader.loadActiveTrapSignals(USER_ID)).thenReturn(List.of());
        ExamRepository examRepository = mock(ExamRepository.class);
        when(examRepository.findLatestByCode("TR_YKS_TYT_MATH", false)).thenReturn(Optional.of(sampleTytDefinition()));
        when(examRepository.findTargetsByUserId(USER_ID)).thenReturn(List.of());
        when(examRepository.findCatalog(null, null, true)).thenReturn(List.of(sampleTytDefinition()));
        ExamProperties examProps = new ExamProperties();
        examProps.setReadinessEnabled(true);
        eventPublisher = mock(ApplicationEventPublisher.class);
        readinessService = new ExamReadinessProjectionService(
            readinessRepository,
            readinessSignalReader,
            examRepository,
            null,
            null,
            new ExamReadinessCalculator(),
            eventPublisher,
            examProps,
            new SimpleMeterRegistry(),
            CLOCK
        );

        examSessionRepository = mock(ExamSessionRepository.class);
        AssembledAssessmentRepository assemblyRepo = mock(AssembledAssessmentRepository.class);
        when(examSessionRepository.findActiveByUserIdAndAssemblyId(USER_ID, TIMED_MOCK_ASSEMBLY_ID))
            .thenReturn(Optional.empty());
        when(assemblyRepo.findById(TIMED_MOCK_ASSEMBLY_ID))
            .thenReturn(Optional.of(sampleTimedMockAssembly(TIMED_MOCK_ASSEMBLY_ID)));
        when(examSessionRepository.saveSession(any())).thenAnswer(inv -> inv.getArgument(0));
        examRuntimeService = new ExamSessionRuntimeService(
            examSessionRepository,
            assemblyRepo,
            examRepository,
            new ActiveExamNoHintGuard(new SimpleMeterRegistry()),
            eventPublisher,
            new SimpleMeterRegistry(),
            CLOCK,
            true
        );

        enableGuardsForAllServices("true");

        mockMvc = MockMvcBuilders.standaloneSetup(
                new CounterfactualTwinController(twinService),
                new DailyCoachController(coachService),
                new SpacedReviewController(spacedService),
                new MicroLessonController(microLessonService),
                new ExamReadinessController(readinessService),
                new ExamSessionRuntimeController(examRuntimeService)
            )
            .setControllerAdvice(new ApiExceptionHandler())
            .setCustomArgumentResolvers(principalResolver())
            .build();
    }

    @Test
    void freeTierDeniesProAndCoachEndpointsWithZeroDownstreamSideEffects() throws Exception {
        grantedCapabilities.set(EnumSet.of(LearningOsCapability.ATTEMPT_VERIFY, LearningOsCapability.DETERMINISTIC_TWIN));

        mockMvc.perform(post("/v1/attempts/" + ATTEMPT_ID + "/twins").principal(learnerAuth()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENTITLEMENT_REQUIRED"))
            .andExpect(jsonPath("$.details.recoverable").value(true))
            .andExpect(jsonPath("$.details.userAction").value("UPGRADE"));

        mockMvc.perform(post("/v1/coach/missions/" + COACH_SESSION_ID + "/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":\"" + COACH_TASK_ID + "\",\"verifiedIndependentSolve\":true,\"durationSeconds\":45}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENTITLEMENT_REQUIRED"))
            .andExpect(jsonPath("$.details.userAction").value("UPGRADE"));

        mockMvc.perform(post("/api/v1/study-plan/spaced-queue/" + SPACED_ITEM_ID + "/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passed\":true,\"independentVerification\":true,\"responseTimeMs\":9500}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENTITLEMENT_REQUIRED"));

        mockMvc.perform(get("/v1/micro-lessons/ALG_SIGN_FLIP_DISTRIBUTE").principal(learnerAuth()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENTITLEMENT_REQUIRED"));

        mockMvc.perform(post("/v1/exams/readiness/recompute")
                .param("examCode", "TR_YKS_TYT_MATH")
                .principal(learnerAuth()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENTITLEMENT_REQUIRED"));

        mockMvc.perform(post("/v1/exams/sessions")
                .principal(learnerAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assemblyId\":\"" + TIMED_MOCK_ASSEMBLY_ID + "\",\"trainingEligible\":false}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENTITLEMENT_REQUIRED"));

        verify(mathVerifierTwinPort, never()).synthesizeIsomorphicTwin(any(), any(), any(), any(), any());
        verify(twinRepository, never()).saveAndFlush(any());
        verify(coachTaskRepository, never()).save(any());
        verify(coachSessionRepository, never()).save(any());
        verify(spacedRepository, never()).save(any());
        verifyNoInteractions(readinessSignalReader);
        verify(readinessRepository, never()).saveUpsert(any());
        verify(examSessionRepository, never()).saveSession(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void proAllowsHybridTwinButStillDeniesCoachCapabilitiesWhileCoachAndSeasonAllowAll() throws Exception {
        grantedCapabilities.set(EnumSet.of(
            LearningOsCapability.ATTEMPT_VERIFY,
            LearningOsCapability.DETERMINISTIC_TWIN,
            LearningOsCapability.HYBRID_TWIN,
            LearningOsCapability.MISTAKE_PASSPORT_FULL
        ));

        mockMvc.perform(post("/v1/attempts/" + ATTEMPT_ID + "/twins").principal(learnerAuth()))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/v1/micro-lessons/ALG_SIGN_FLIP_DISTRIBUTE").principal(learnerAuth()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENTITLEMENT_REQUIRED"));

        mockMvc.perform(post("/v1/exams/readiness/recompute")
                .param("examCode", "TR_YKS_TYT_MATH")
                .principal(learnerAuth()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENTITLEMENT_REQUIRED"));

        grantedCapabilities.set(EnumSet.allOf(LearningOsCapability.class));

        mockMvc.perform(get("/v1/micro-lessons/ALG_SIGN_FLIP_DISTRIBUTE").principal(learnerAuth()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/v1/exams/readiness/recompute")
                .param("examCode", "TR_YKS_TYT_MATH")
                .principal(learnerAuth()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/v1/exams/sessions")
                .principal(learnerAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assemblyId\":\"" + TIMED_MOCK_ASSEMBLY_ID + "\",\"trainingEligible\":false}"))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/coach/missions/" + COACH_SESSION_ID + "/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":\"" + COACH_TASK_ID + "\",\"verifiedIndependentSolve\":true,\"durationSeconds\":45}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/study-plan/spaced-queue/" + SPACED_ITEM_ID + "/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passed\":true,\"independentVerification\":true,\"responseTimeMs\":9500}"))
            .andExpect(status().isOk());
    }

    @Test
    void transientBillingFailureReturns503RetryAndDisabledFlagPreservesLegacyParity() throws Exception {
        syncUnavailable.set(true);

        mockMvc.perform(post("/v1/exams/sessions")
                .principal(learnerAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assemblyId\":\"" + TIMED_MOCK_ASSEMBLY_ID + "\",\"trainingEligible\":false}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("ENTITLEMENT_SYNC_FAILED"))
            .andExpect(jsonPath("$.details.recoverable").value(true))
            .andExpect(jsonPath("$.details.userAction").value("RETRY"));

        enableGuardsForAllServices("false");
        mockMvc.perform(get("/v1/micro-lessons/ALG_SIGN_FLIP_DISTRIBUTE").principal(learnerAuth()))
            .andExpect(status().isOk());

        verifyAttemptFailClosedWhenBillingBeanAbsent();
    }

    private void verifyAttemptFailClosedWhenBillingBeanAbsent() {
        AttemptVerifierClient verifier = mock(AttemptVerifierClient.class);
        SolveSourceAccess sources = mock(SolveSourceAccess.class);
        StudentAttemptRepository attemptRepo = mock(StudentAttemptRepository.class);
        AttemptEvaluationService evalService = new AttemptEvaluationService(
            sources, attemptRepo, mock(AttemptWorkRepository.class), mock(AttemptEvaluationRepository.class),
            mock(AttemptEvaluationPreparer.class), verifier, mock(PlatformTransactionManager.class),
            CLOCK, mock(RateLimiter.class), new SimpleMeterRegistry(), false, false, "test", false
        );
        ReflectionTestUtils.setField(evalService, "learningOsFlag", "true");
        ReflectionTestUtils.setField(evalService, "access", null);
        assertThatThrownBy(() -> evalService.evaluate(USER_ID, UUID.randomUUID(), ATTEMPT_ID, "k1", null))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code()).isEqualTo(ApiErrorCode.ENTITLEMENT_SYNC_FAILED));
        verifyNoInteractions(verifier, sources);

        CanonicalMathCodec codec = new CanonicalMathCodec();
        AttemptWorkService workService = new AttemptWorkService(
            mock(LearnerMistakeDnaService.class), sources, attemptRepo, mock(AttemptWorkRepository.class),
            mock(AttemptWorkNormalizer.class), mock(ManualStepNormalizer.class), mock(AttemptExtractionReader.class),
            new VerificationSourceReader(codec), codec, new SimpleMeterRegistry(),
            mock(RateLimiter.class), CLOCK, mock(PlatformTransactionManager.class), false, false, "test", false
        );
        ReflectionTestUtils.setField(workService, "learningOsFlag", "true");
        ReflectionTestUtils.setField(workService, "access", null);
        assertThatThrownBy(() -> workService.submit(
            USER_ID, UUID.randomUUID(), "k2",
            new AttemptWorkInput(
                "attempt-structured-work-request-v1",
                new AttemptSourceIdentity(UUID.randomUUID(), 1, UUID.randomUUID(), 1),
                AttemptWorkInput.Engine.MANUAL,
                null, null, null, null,
                List.of(),
                List.of(new AttemptWorkInput.Step(
                    UUID.randomUUID(), "x = 2", AttemptWorkInput.Role.FINAL_ANSWER, List.of(), true
                ))
            )
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code()).isEqualTo(ApiErrorCode.ENTITLEMENT_SYNC_FAILED));
        verifyNoInteractions(sources, attemptRepo);
    }

    private void enableGuardsForAllServices(String flag) {
        for (Object target : List.of(twinService, coachService, spacedService, microLessonService, readinessService, examRuntimeService)) {
            ReflectionTestUtils.setField(target, "access", stubPolicy);
            ReflectionTestUtils.setField(target, "learningOsFlag", flag);
        }
    }

    private static Authentication learnerAuth() {
        return new UsernamePasswordAuthenticationToken(
            new VerifiedJwtPrincipal(TENANT_ID, USER_ID),
            "token",
            List.of(new SimpleGrantedAuthority("ROLE_LEARNER"))
        );
    }

    private static HandlerMethodArgumentResolver principalResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory
            ) {
                Class<?> type = parameter.getParameterType();
                if (VerifiedJwtPrincipal.class.isAssignableFrom(type)) {
                    return new VerifiedJwtPrincipal(TENANT_ID, USER_ID);
                }
                if (Jwt.class.isAssignableFrom(type)) {
                    return new Jwt(
                        "synthetic-token",
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(3600),
                        Map.of("alg", "none"),
                        Map.of("sub", USER_ID.toString(), "sid", UUID.randomUUID().toString())
                    );
                }
                return (AuthenticatedLearnerPrincipal) () -> USER_ID;
            }
        };
    }

    private static ExamDefinition sampleTytDefinition() {
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

    private static AssembledAssessment sampleTimedMockAssembly(UUID assemblyId) {
        List<AssembledAssessmentItem> items = new java.util.ArrayList<>();
        for (int seq = 1; seq <= 5; seq++) {
            UUID qId = UUID.fromString(String.format("93000000-0000-4000-8000-00000000010%d", seq));
            items.add(new AssembledAssessmentItem(
                UUID.randomUUID(), assemblyId, qId, "TYT-2026-00" + seq, seq, "MATH.ALG.INEQ",
                null, QuestionItemFormat.SINGLE_CHOICE_MCQ, QuestionDifficultyBand.MEDIUM,
                CognitiveTaxonomyLevel.APPLICATION, "Solve " + seq, null, null, false,
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
            assemblyId, USER_ID, null, UUID.randomUUID(),
            "BP-TYT-FULL-V1", "Full Timed Simulation", AssessmentBlueprintMode.FULL_SIMULATION,
            UUID.fromString("91000000-0000-4000-8000-000000000101"), 1, "seed-mock-v1", "f".repeat(64),
            5, 900, new BigDecimal("1.00"), 900,
            new AppliedAccommodationSnapshot(new BigDecimal("1.00"), false, false, false),
            new AssemblyDistributionSummary(Map.of(), Map.of(), Map.of(), Map.of(), 0),
            AssembledAssessmentStatus.ASSEMBLED, NOW, NOW.plusSeconds(86400), false, items
        );
    }
}
