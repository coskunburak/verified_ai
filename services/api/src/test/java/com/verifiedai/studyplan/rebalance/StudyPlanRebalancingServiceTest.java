package com.verifiedai.studyplan.rebalance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.studyplan.coach.*;
import com.verifiedai.studyplan.rebalance.dto.StudyPlanRebalanceRequest;
import com.verifiedai.studyplan.rebalance.dto.StudyPlanRebalanceResponse;
import com.verifiedai.studyplan.rebalance.dto.StudyPlanRecoveryStatusResponse;
import com.verifiedai.studyplan.spaced.SpacedQueueStatus;
import com.verifiedai.studyplan.spaced.SpacedReviewQueueEntity;
import com.verifiedai.studyplan.spaced.SpacedReviewQueueRepository;
import com.verifiedai.studyplan.spaced.SpacedTriggerType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StudyPlanRebalancingServiceTest {

    private RebalanceEventRepository rebalanceEventRepository;
    private DailyCoachSessionRepository coachSessionRepository;
    private DailyCoachTaskRepository coachTaskRepository;
    private SpacedReviewQueueRepository spacedReviewRepository;
    private MissedDayRecoveryPolicy policy;
    private ApplicationEventPublisher eventPublisher;
    private SimpleMeterRegistry meterRegistry;
    private Clock fixedClock;
    private StudyPlanRebalancingService service;
    private RebalancePrivacyPurgeContributor purgeContributor;

    private final UUID userA = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private final UUID userB = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private final LocalDate today = LocalDate.of(2026, 11, 13);

    @BeforeEach
    void setUp() {
        rebalanceEventRepository = mock(RebalanceEventRepository.class);
        coachSessionRepository = mock(DailyCoachSessionRepository.class);
        coachTaskRepository = mock(DailyCoachTaskRepository.class);
        spacedReviewRepository = mock(SpacedReviewQueueRepository.class);
        policy = new MissedDayRecoveryPolicy();
        eventPublisher = mock(ApplicationEventPublisher.class);
        meterRegistry = new SimpleMeterRegistry();
        fixedClock = Clock.fixed(Instant.parse("2026-11-13T09:00:00Z"), ZoneOffset.UTC);

        service = new StudyPlanRebalancingService(
                rebalanceEventRepository,
                coachSessionRepository,
                coachTaskRepository,
                spacedReviewRepository,
                policy,
                eventPublisher,
                meterRegistry,
                new ObjectMapper().findAndRegisterModules(),
                fixedClock,
                true
        );
        purgeContributor = new RebalancePrivacyPurgeContributor(rebalanceEventRepository);

        when(rebalanceEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(coachSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(coachTaskRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(spacedReviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("INV-REBAL-004: Idempotent replay returns existing rebalance event without double-mutating")
    void testIdempotentReplayReturnsExistingEvent() {
        UUID existingEventId = UUID.fromString("00000000-0000-0000-0000-000000000851");
        RebalanceEventEntity stored = new RebalanceEventEntity(
                existingEventId,
                userA,
                null,
                today,
                RebalanceTriggerType.MISSED_DAYS,
                3,
                30,
                15,
                20,
                2,
                1,
                3,
                false,
                PruneReasonCode.MISSED_DAY_WINDOW_RESET,
                "[\"MATH.ALG.PRIOR\"]",
                "idem-85-replay",
                Instant.parse("2026-11-13T08:45:00Z")
        );

        when(rebalanceEventRepository.findByUserIdAndIdempotencyKey(userA, "idem-85-replay"))
                .thenReturn(Optional.of(stored));

        StudyPlanRebalanceRequest req = new StudyPlanRebalanceRequest(
                RebalanceTriggerType.MISSED_DAYS, 3, 30, false, "idem-85-replay", today
        );
        StudyPlanRebalanceResponse response = service.executeRebalance(userA, req);

        assertTrue(response.idempotentReplay());
        assertEquals(existingEventId, response.rebalanceEventId());
        assertEquals(20, response.rebalancedDailyBudgetMinutes());
        assertEquals(List.of("MATH.ALG.PRIOR"), response.prunedConceptIds());
        verify(rebalanceEventRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("INV-REBAL-001: Prunes obsolete prior-day coach sessions and horizon-shifts overflow spaced reviews")
    void testPrunesPriorCoachSessionsAndShiftsSpacedOverflow() {
        UUID priorSessionId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        DailyCoachSessionEntity priorSession = new DailyCoachSessionEntity(
                priorSessionId, userA, today.minusDays(3), 15,
                DailyCoachSessionStatus.IN_PROGRESS, false, false, 0, 2,
                "Prior briefing", DailyCoachMode.SOCRATIC_COACH_MODE, Instant.parse("2026-11-10T08:00:00Z")
        );
        DailyCoachTaskEntity priorTask1 = new DailyCoachTaskEntity(
                UUID.fromString("00000000-0000-0000-0000-000000000711"),
                priorSessionId, 1, DailyCoachTaskType.TRAP_REPAIR,
                "MATH.ALG.RATIONAL_EQ", "ALG_DOMAIN_EXCLUSION_MISSED", "twin_prior_1",
                5, "Rationale", "x = 2", DailyCoachTaskStatus.PENDING
        );

        List<SpacedReviewQueueEntity> dueItems = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            dueItems.add(new SpacedReviewQueueEntity(
                    UUID.fromString("00000000-0000-0000-0000-00000000080" + i),
                    userA,
                    "MATH.SKILL." + i,
                    "TRAP_" + i,
                    SpacedTriggerType.TRAP_IMMUNITY_7D,
                    new BigDecimal("0.4000"),
                    new BigDecimal("3.00"),
                    Instant.parse("2026-11-11T09:00:00Z"),
                    null,
                    "x^2 = 4",
                    SpacedQueueStatus.PENDING
            ));
        }

        when(rebalanceEventRepository.findByUserIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(coachSessionRepository.findAllByUserIdOrderByLocalDateDesc(userA)).thenReturn(List.of(priorSession));
        when(coachSessionRepository.findByUserIdAndLocalDateBeforeAndStatusIn(eq(userA), eq(today), any()))
                .thenReturn(List.of(priorSession));
        when(coachTaskRepository.findAllBySessionIdOrderByTaskOrderAsc(priorSessionId)).thenReturn(List.of(priorTask1));
        when(coachSessionRepository.findByUserIdAndLocalDate(userA, today)).thenReturn(Optional.empty());
        when(spacedReviewRepository.findActiveDueByUserId(eq(userA), any())).thenReturn(dueItems);

        StudyPlanRebalanceRequest req = new StudyPlanRebalanceRequest(
                RebalanceTriggerType.MISSED_DAYS, null, 30, false, null, today
        );
        StudyPlanRebalanceResponse response = service.executeRebalance(userA, req);

        assertFalse(response.idempotentReplay());
        assertEquals(DailyCoachSessionStatus.EXPIRED, priorSession.getStatus());
        assertEquals(DailyCoachTaskStatus.SKIPPED, priorTask1.getStatus());
        assertEquals(1, response.prunedTasksCount());
        assertEquals(2, response.rescheduledReviewsCount());
        assertEquals(2, response.activeTodayTasksCount());
        assertTrue(response.activeTodayTasksCount() <= 3);
        assertTrue(response.rebalancedDailyBudgetMinutes() <= 25);

        verify(coachTaskRepository).skipAllPendingTasksBySessionId(priorSessionId);
        verify(spacedReviewRepository, times(2)).save(any());
        verify(eventPublisher).publishEvent(any(RebalanceEvent.class));
        assertEquals(1.0, meterRegistry.counter("verifiedai.studyplan.plan_rebalanced").count());
        assertTrue(meterRegistry.counter("verifiedai.studyplan.overload_pruning_executed").count() >= 1.0);
    }

    @Test
    @DisplayName("INV-REBAL-005: BOLA isolation throws StudyPlanRecoveryNotFoundException and privacy purge < 500 ms")
    void testBolaIsolationAndPrivacyPurgeLatency() {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000991");
        when(rebalanceEventRepository.findByIdAndUserId(eventId, userB)).thenReturn(Optional.empty());

        assertThrows(StudyPlanRecoveryNotFoundException.class, () -> service.getRebalanceEvent(userB, eventId));

        when(rebalanceEventRepository.deleteAllByUserId(userA)).thenReturn(3);
        long startNs = System.nanoTime();
        purgeContributor.deleteUserData(userA, Instant.now());
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        verify(rebalanceEventRepository).deleteAllByUserId(userA);
        assertTrue(elapsedMs < 500, "Privacy purge exceeded 500 ms budget");
        assertEquals("study_plan_rebalance_events", purgeContributor.category());
    }

    @Test
    @DisplayName("Feature flag disabled: recovery-status returns safe baseline and rebalance returns 409 Conflict")
    void testFeatureFlagDisabledBehavior() {
        StudyPlanRebalancingService disabledService = new StudyPlanRebalancingService(
                rebalanceEventRepository,
                coachSessionRepository,
                coachTaskRepository,
                spacedReviewRepository,
                policy,
                eventPublisher,
                meterRegistry,
                new ObjectMapper().findAndRegisterModules(),
                fixedClock,
                false
        );

        StudyPlanRecoveryStatusResponse status = disabledService.getRecoveryStatus(userA, today);
        assertFalse(status.recoveryNeeded());
        assertEquals(0, status.missedDaysCount());
        assertEquals("studyplan.rebalance.welcome_steady", status.welcomeMessageKey());

        StudyPlanRebalanceRequest req = new StudyPlanRebalanceRequest(
                RebalanceTriggerType.MISSED_DAYS, 3, 30, false, null, today
        );
        ApiProblemException ex = assertThrows(ApiProblemException.class, () -> disabledService.executeRebalance(userA, req));
        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertEquals(ApiErrorCode.STUDY_PLAN_REBALANCING_DISABLED, ex.code());
    }
}
