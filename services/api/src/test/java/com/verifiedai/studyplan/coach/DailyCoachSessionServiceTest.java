package com.verifiedai.studyplan.coach;

import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.studyplan.coach.dto.DailyCoachPreferencesRequest;
import com.verifiedai.studyplan.coach.dto.DailyCoachSummaryResponse;
import com.verifiedai.studyplan.coach.dto.DailyCoachTaskCompleteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DailyCoachSessionServiceTest {

    private DailyCoachSessionRepository sessionRepository;
    private DailyCoachTaskRepository taskRepository;
    private DeterministicCoachBriefingBuilder briefingBuilder;
    private JdbcClient jdbcClient;
    private Clock fixedClock;
    private DailyCoachSessionService service;
    private DailyCoachPrivacyPurgeContributor purgeContributor;

    private final UUID userA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID userB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final LocalDate today = LocalDate.of(2026, 11, 13);

    @BeforeEach
    void setUp() {
        sessionRepository = mock(DailyCoachSessionRepository.class);
        taskRepository = mock(DailyCoachTaskRepository.class);
        briefingBuilder = new DeterministicCoachBriefingBuilder();
        jdbcClient = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
        fixedClock = Clock.fixed(Instant.parse("2026-11-13T08:00:00Z"), ZoneOffset.UTC);

        service = new DailyCoachSessionService(
                sessionRepository, taskRepository, briefingBuilder, jdbcClient, fixedClock
        );
        purgeContributor = new DailyCoachPrivacyPurgeContributor(sessionRepository, taskRepository);

        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @SuppressWarnings("unchecked")
    private void stubUpstreamSignals(boolean hasTraps, boolean hasSpaced, boolean hasRecs) {
        when(jdbcClient.sql(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
            JdbcClient.MappedQuerySpec<Object> mapped = mock(JdbcClient.MappedQuerySpec.class);
            when(spec.param(anyString(), any())).thenReturn(spec);
            when(spec.query(any(RowMapper.class))).thenAnswer(qInv -> {
                RowMapper<Object> mapper = qInv.getArgument(0);
                List<Object> rows = new ArrayList<>();
                if (sql.contains("learner_mistake_dna_profiles") && hasTraps) {
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("skill_id")).thenReturn("MATH.ALG.RATIONAL_EQ");
                    when(rs.getString("mistake_code")).thenReturn("ALG_DOMAIN_EXCLUSION_MISSED");
                    when(rs.getString("recurrence_state")).thenReturn("RECURRING_PATTERN");
                    rows.add(mapper.mapRow(rs, 0));
                } else if (sql.contains("spaced_review_queue") && hasSpaced) {
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("canonical_skill_code")).thenReturn("MATH.ALG.QUADRATIC_DISCRIMINANT");
                    when(rs.getString("target_mistake_code")).thenReturn("TRAP_SIGN_DISTRIB_NEG");
                    when(rs.getString("trigger_type")).thenReturn("TRAP_IMMUNITY_7D");
                    when(rs.getString("prompt_preview_latex")).thenReturn("2x^2 - 5x - 3 = 0");
                    rows.add(mapper.mapRow(rs, 0));
                } else if (sql.contains("learner_recommendations") && hasRecs) {
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("target_skill_code")).thenReturn("MATH.CALC.CHAIN_RULE_COMPOSITE");
                    when(rs.getString("target_mistake_code")).thenReturn("CALC_CHAIN_RULE_OMITTED");
                    when(rs.getString("rationale_code")).thenReturn("HIGH_EXAM_NET_ROI");
                    rows.add(mapper.mapRow(rs, 0));
                }
                when(mapped.list()).thenReturn(rows);
                return mapped;
            });
            return spec;
        });
    }

    @Test
    @DisplayName("INV-COACH-001: Deterministic 3-block mission assembly from upstream signals")
    void testThreeBlockDeterministicAssembly() {
        stubUpstreamSignals(true, true, true);
        when(sessionRepository.findByUserIdAndLocalDate(userA, today)).thenReturn(Optional.empty());
        when(sessionRepository.findFirstByUserIdOrderByLocalDateDesc(userA)).thenReturn(Optional.empty());

        DailyCoachSummaryResponse summary = service.getOrCreateTodaySummary(userA, today);

        assertFalse(summary.isColdStart());
        assertFalse(summary.isDownshifted());
        assertEquals(15, summary.budgetMinutes());
        assertEquals(3, summary.totalTasksCount());
        assertEquals(0, summary.completedTasksCount());
        assertEquals(DailyCoachSessionStatus.IN_PROGRESS, summary.status());
        assertEquals(DailyCoachMode.SOCRATIC_COACH_MODE, summary.preferredMode());
        assertNull(summary.coldStartDemo());

        assertEquals(3, summary.tasks().size());
        assertEquals(DailyCoachTaskType.TRAP_REPAIR, summary.tasks().get(0).taskType());
        assertEquals("MATH.ALG.RATIONAL_EQ", summary.tasks().get(0).targetSkillCode());
        assertEquals(DailyCoachTaskType.SPACED_RETENTION, summary.tasks().get(1).taskType());
        assertEquals("MATH.ALG.QUADRATIC_DISCRIMINANT", summary.tasks().get(1).targetSkillCode());
        assertEquals(DailyCoachTaskType.EXAM_SYNTHESIS, summary.tasks().get(2).taskType());
        assertEquals("MATH.CALC.CHAIN_RULE_COMPOSITE", summary.tasks().get(2).targetSkillCode());
        assertFalse(summary.activeTrapRadar().isEmpty());
    }

    @Test
    @DisplayName("INV-COACH-006: Cold-start fallback synthesizes 3-task readiness mission and demo card")
    void testColdStartFallback() {
        stubUpstreamSignals(false, false, false);
        when(sessionRepository.findByUserIdAndLocalDate(userA, today)).thenReturn(Optional.empty());
        when(sessionRepository.findFirstByUserIdOrderByLocalDateDesc(userA)).thenReturn(Optional.empty());

        DailyCoachSummaryResponse summary = service.getOrCreateTodaySummary(userA, today);

        assertTrue(summary.isColdStart());
        assertEquals(3, summary.tasks().size());
        assertNotNull(summary.coldStartDemo());
        assertEquals("-2(x - 3) > 8", summary.coldStartDemo().samplePromptLatex());
        assertTrue(summary.tasks().stream().allMatch(t -> t.assignedTwinId().startsWith("twin_84_")));
    }

    @Test
    @DisplayName("INV-COACH-004: Repeated daily summary calls are idempotent and do not duplicate rows")
    void testDailyIdempotency() {
        UUID existingSessionId = UUID.randomUUID();
        DailyCoachSessionEntity existingSession = new DailyCoachSessionEntity(
                existingSessionId, userA, today, 15, DailyCoachSessionStatus.IN_PROGRESS,
                false, false, 1, 3,
                "Bugün 15 dakikada odaklı pratikle ivmeni koruyoruz.",
                DailyCoachMode.SOLVER_MODE, fixedClock.instant()
        );
        List<DailyCoachTaskEntity> existingTasks = List.of(
                new DailyCoachTaskEntity(UUID.randomUUID(), existingSessionId, 1, DailyCoachTaskType.TRAP_REPAIR,
                        "MATH.ALG.RATIONAL_EQ", "ALG_DOMAIN_EXCLUSION_MISSED", "twin_84_111111111111",
                        5, "growth_opportunity: Payda kısıtı", "\\frac{x^2 - 4}{x - 2}", DailyCoachTaskStatus.COMPLETED),
                new DailyCoachTaskEntity(UUID.randomUUID(), existingSessionId, 2, DailyCoachTaskType.SPACED_RETENTION,
                        "MATH.ALG.QUADRATIC_DISCRIMINANT", "TRAP_SIGN_DISTRIB_NEG", "twin_84_222222222222",
                        5, "retention_refresh: Diskriminant", "2x^2 - 5x - 3 = 0", DailyCoachTaskStatus.PENDING),
                new DailyCoachTaskEntity(UUID.randomUUID(), existingSessionId, 3, DailyCoachTaskType.EXAM_SYNTHESIS,
                        "MATH.CALC.CHAIN_RULE_COMPOSITE", "CALC_CHAIN_RULE_OMITTED", "twin_84_333333333333",
                        5, "growth_opportunity: İç türev", "(3x^2+1)^4", DailyCoachTaskStatus.PENDING)
        );

        stubUpstreamSignals(true, true, true);
        when(sessionRepository.findByUserIdAndLocalDate(userA, today)).thenReturn(Optional.of(existingSession));
        when(taskRepository.findAllBySessionIdOrderByTaskOrderAsc(existingSessionId)).thenReturn(existingTasks);

        DailyCoachSummaryResponse first = service.getOrCreateTodaySummary(userA, today);
        DailyCoachSummaryResponse second = service.getOrCreateTodaySummary(userA, today);

        assertEquals(existingSessionId, first.sessionId());
        assertEquals(first.sessionId(), second.sessionId());
        assertEquals(first.tasks().size(), second.tasks().size());
        verify(sessionRepository, never()).save(any());
        verify(taskRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("INV-COACH-002: Busy-day 5-min downshift skips remaining tasks and completes on priority twin")
    void testBusyDayDownshiftStateMachine() {
        UUID sessionId = UUID.randomUUID();
        UUID task1Id = UUID.randomUUID();
        UUID task2Id = UUID.randomUUID();
        UUID task3Id = UUID.randomUUID();

        DailyCoachSessionEntity session = new DailyCoachSessionEntity(
                sessionId, userA, today, 15, DailyCoachSessionStatus.IN_PROGRESS,
                false, false, 0, 3, "Initial briefing", DailyCoachMode.SOCRATIC_COACH_MODE, fixedClock.instant()
        );
        List<DailyCoachTaskEntity> tasks = List.of(
                new DailyCoachTaskEntity(task1Id, sessionId, 1, DailyCoachTaskType.TRAP_REPAIR,
                        "MATH.ALG.RATIONAL_EQ", "ALG_DOMAIN_EXCLUSION_MISSED", "twin_84_111111111111",
                        5, "growth_opportunity", "\\frac{x^2-4}{x-2}", DailyCoachTaskStatus.PENDING),
                new DailyCoachTaskEntity(task2Id, sessionId, 2, DailyCoachTaskType.SPACED_RETENTION,
                        "MATH.ALG.QUADRATIC_DISCRIMINANT", "TRAP_SIGN_DISTRIB_NEG", "twin_84_222222222222",
                        5, "retention_refresh", "2x^2-5x-3=0", DailyCoachTaskStatus.PENDING),
                new DailyCoachTaskEntity(task3Id, sessionId, 3, DailyCoachTaskType.EXAM_SYNTHESIS,
                        "MATH.CALC.CHAIN_RULE_COMPOSITE", "CALC_CHAIN_RULE_OMITTED", "twin_84_333333333333",
                        5, "growth_opportunity", "(3x^2+1)^4", DailyCoachTaskStatus.PENDING)
        );

        stubUpstreamSignals(true, false, false);
        when(sessionRepository.findByIdAndUserId(sessionId, userA)).thenReturn(Optional.of(session));
        when(taskRepository.findAllBySessionIdOrderByTaskOrderAsc(sessionId)).thenReturn(tasks);

        DailyCoachSummaryResponse downshifted = service.downshiftMission(userA, sessionId);

        assertTrue(downshifted.isDownshifted());
        assertEquals(5, downshifted.budgetMinutes());
        assertEquals(1, downshifted.totalTasksCount());
        assertEquals(DailyCoachSessionStatus.IN_PROGRESS, downshifted.status());
        assertEquals(DailyCoachTaskStatus.PENDING, downshifted.tasks().get(0).status());
        assertEquals(DailyCoachTaskStatus.SKIPPED, downshifted.tasks().get(1).status());
        assertEquals(DailyCoachTaskStatus.SKIPPED, downshifted.tasks().get(2).status());

        // Complete priority Task 1 -> session becomes COMPLETED
        DailyCoachSummaryResponse completed = service.completeTask(
                userA, sessionId, new DailyCoachTaskCompleteRequest(task1Id, true, 90)
        );
        assertEquals(DailyCoachSessionStatus.COMPLETED, completed.status());
        assertEquals(1, completed.completedTasksCount());
        assertEquals(DailyCoachTaskStatus.COMPLETED, completed.tasks().get(0).status());
    }

    @Test
    @DisplayName("INV-COACH-002: Downshifting when Task 1 is already COMPLETED marks session COMPLETED immediately")
    void testDownshiftWhenTaskOneAlreadyCompleted() {
        UUID sessionId = UUID.randomUUID();
        DailyCoachSessionEntity session = new DailyCoachSessionEntity(
                sessionId, userA, today, 15, DailyCoachSessionStatus.IN_PROGRESS,
                false, false, 1, 3, "Initial briefing", DailyCoachMode.SOCRATIC_COACH_MODE, fixedClock.instant()
        );
        List<DailyCoachTaskEntity> tasks = List.of(
                new DailyCoachTaskEntity(UUID.randomUUID(), sessionId, 1, DailyCoachTaskType.TRAP_REPAIR,
                        "MATH.ALG.RATIONAL_EQ", "ALG_DOMAIN_EXCLUSION_MISSED", "twin_84_111111111111",
                        5, "growth_opportunity", "\\frac{x^2-4}{x-2}", DailyCoachTaskStatus.COMPLETED),
                new DailyCoachTaskEntity(UUID.randomUUID(), sessionId, 2, DailyCoachTaskType.SPACED_RETENTION,
                        "MATH.ALG.QUADRATIC_DISCRIMINANT", "TRAP_SIGN_DISTRIB_NEG", "twin_84_222222222222",
                        5, "retention_refresh", "2x^2-5x-3=0", DailyCoachTaskStatus.PENDING)
        );

        stubUpstreamSignals(false, false, false);
        when(sessionRepository.findByIdAndUserId(sessionId, userA)).thenReturn(Optional.of(session));
        when(taskRepository.findAllBySessionIdOrderByTaskOrderAsc(sessionId)).thenReturn(tasks);

        DailyCoachSummaryResponse result = service.downshiftMission(userA, sessionId);

        assertEquals(DailyCoachSessionStatus.COMPLETED, result.status());
        assertEquals(1, result.completedTasksCount());
        assertEquals(1, result.totalTasksCount());
        assertEquals(DailyCoachTaskStatus.SKIPPED, result.tasks().get(1).status());
    }

    @Test
    @DisplayName("INV-COACH-003: Dual-mode preference switcher updates SOLVER_MODE and validates budgetMinutes")
    void testDualModePreferenceSwitch() {
        UUID sessionId = UUID.randomUUID();
        DailyCoachSessionEntity session = new DailyCoachSessionEntity(
                sessionId, userA, today, 15, DailyCoachSessionStatus.IN_PROGRESS,
                false, false, 0, 3, "Initial", DailyCoachMode.SOCRATIC_COACH_MODE, fixedClock.instant()
        );
        stubUpstreamSignals(false, false, false);
        when(sessionRepository.findByUserIdAndLocalDate(userA, today)).thenReturn(Optional.of(session));
        when(taskRepository.findAllBySessionIdOrderByTaskOrderAsc(sessionId)).thenReturn(List.of());

        DailyCoachSummaryResponse updated = service.updatePreferences(
                userA, new DailyCoachPreferencesRequest(DailyCoachMode.SOLVER_MODE, 10)
        );

        assertEquals(DailyCoachMode.SOLVER_MODE, updated.preferredMode());
        assertEquals(10, updated.budgetMinutes());

        assertThrows(ApiProblemException.class, () ->
                service.updatePreferences(userA, new DailyCoachPreferencesRequest(DailyCoachMode.SOLVER_MODE, 12))
        );
    }

    @Test
    @DisplayName("INV-COACH-005 & ADR-011: BOLA 404 isolation, < 500ms privacy purge, and < 5ms briefing fallback")
    void testBolaPrivacyPurgeAndBriefingFallback() {
        UUID foreignSessionId = UUID.randomUUID();
        when(sessionRepository.findByIdAndUserId(foreignSessionId, userB)).thenReturn(Optional.empty());

        assertThrows(DailyCoachSessionNotFoundException.class, () ->
                service.downshiftMission(userB, foreignSessionId)
        );
        assertThrows(DailyCoachSessionNotFoundException.class, () ->
                service.completeTask(userB, foreignSessionId, new DailyCoachTaskCompleteRequest(UUID.randomUUID(), true, 45))
        );

        long purgeStart = System.nanoTime();
        purgeContributor.deleteUserData(userA, fixedClock.instant());
        long purgeMs = (System.nanoTime() - purgeStart) / 1_000_000L;
        verify(sessionRepository, times(1)).deleteAllByUserId(userA);
        assertTrue(purgeMs < 500, "Privacy purge must execute in < 500ms");

        // ADR-011: Throwing explainer falls back immediately in < 5ms
        DeterministicCoachBriefingBuilder resilientBuilder = new DeterministicCoachBriefingBuilder(
                Optional.of((mode, budget, cold, downshifted, locale) -> {
                    throw new IllegalStateException("Offline");
                })
        );
        long briefStart = System.nanoTime();
        String fallback = resilientBuilder.buildBriefing(
                DailyCoachSessionStatus.IN_PROGRESS, DailyCoachMode.SOLVER_MODE, 15, false, false, List.of(), "tr"
        );
        long briefMs = (System.nanoTime() - briefStart) / 1_000_000L;
        assertFalse(fallback.isBlank());
        assertTrue(briefMs < 5, "Deterministic briefing fallback must run in < 5ms");
    }
}
