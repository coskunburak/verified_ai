package com.verifiedai.studyplan.rebalance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MissedDayRecoveryPolicyTest {

    private final MissedDayRecoveryPolicy policy = new MissedDayRecoveryPolicy();
    private final LocalDate today = LocalDate.of(2026, 11, 13);

    @Test
    @DisplayName("INV-REBAL-002: k=0 missed days preserves baseline budget and steady welcome key")
    void testZeroMissedDaysSteady() {
        var result = policy.evaluate(new MissedDayRecoveryPolicy.RecoveryInput(
                today, today.minusDays(1), null, 15, 30, false, List.of()
        ));

        assertEquals(0, result.missedDaysCount());
        assertFalse(result.recoveryNeeded());
        assertEquals(15, result.previousDailyBudgetMinutes());
        assertEquals(15, result.rebalancedDailyBudgetMinutes());
        assertFalse(result.miniModeActivated());
        assertEquals(3, result.activeTodayTasksCount());
        assertEquals(PruneReasonCode.NONE, result.pruneReasonCode());
        assertEquals("studyplan.rebalance.welcome_steady", result.welcomeMessageKey());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 7, 30})
    @DisplayName("INV-REBAL-001 & INV-REBAL-002: k in {1,3,7,30} never exceeds 25 min ceiling or 3 active tasks")
    void testMissedDayHorizonsBounded(int missedDays) {
        var result = policy.evaluate(new MissedDayRecoveryPolicy.RecoveryInput(
                today, null, missedDays, 15, 30, false, List.of()
        ));

        assertEquals(missedDays, result.missedDaysCount());
        assertTrue(result.recoveryNeeded());
        assertTrue(result.rebalancedDailyBudgetMinutes() >= 5 && result.rebalancedDailyBudgetMinutes() <= 25);
        assertTrue(MissedDayRecoveryPolicy.ALLOWED_BUDGET_STEPS.contains(result.rebalancedDailyBudgetMinutes()));
        assertTrue(result.activeTodayTasksCount() >= 1 && result.activeTodayTasksCount() <= 3);
        assertEquals("studyplan.rebalance.welcome_recovered", result.welcomeMessageKey());
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 10, 5, 1})
    @DisplayName("INV-REBAL-002: T_exam <= k boundary prevents division by zero and clamps within 25 min")
    void testExamHorizonSafetyBoundary(int daysUntilExam) {
        var result = policy.evaluate(new MissedDayRecoveryPolicy.RecoveryInput(
                today, null, 7, 20, daysUntilExam, false, List.of()
        ));

        assertEquals(daysUntilExam, result.daysUntilExam());
        int expectedBudget = daysUntilExam == 1 ? 20 : 25;
        assertEquals(expectedBudget, result.rebalancedDailyBudgetMinutes());
        assertTrue(result.rebalancedDailyBudgetMinutes() <= 25);
        assertTrue(result.activeTodayTasksCount() <= 3);
    }

    @Test
    @DisplayName("INV-REBAL-001 & INV-REBAL-002: Prior-day pruning, low-ROI (<0.20) pruning, and <=3 task cap")
    void testLowRoiPruningAndActiveTaskCap() {
        List<MissedDayRecoveryPolicy.CandidateTask> candidates = List.of(
                new MissedDayRecoveryPolicy.CandidateTask(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        "MATH.ALG.PRIOR_TASK", 0.45, 0.40, today.minusDays(2), true
                ),
                new MissedDayRecoveryPolicy.CandidateTask(
                        UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        "MATH.ALG.RATIONAL_EQ", 0.60, 0.50, today, true
                ),
                new MissedDayRecoveryPolicy.CandidateTask(
                        UUID.fromString("00000000-0000-0000-0000-000000000003"),
                        "MATH.GEO.LOW_ROI_CIRCLE", 0.12, 0.30, today, false
                ),
                new MissedDayRecoveryPolicy.CandidateTask(
                        UUID.fromString("00000000-0000-0000-0000-000000000004"),
                        "MATH.ALG.QUADRATIC_URGENT", 0.42, 0.25, today, false
                ),
                new MissedDayRecoveryPolicy.CandidateTask(
                        UUID.fromString("00000000-0000-0000-0000-000000000005"),
                        "MATH.CALC.CHAIN_RULE", 0.38, 0.35, today, false
                ),
                new MissedDayRecoveryPolicy.CandidateTask(
                        UUID.fromString("00000000-0000-0000-0000-000000000006"),
                        "MATH.PROB.HIGH_RETENTION", 0.30, 0.88, today, false
                )
        );

        var result = policy.evaluate(new MissedDayRecoveryPolicy.RecoveryInput(
                today, null, 15, 20, 20, false, candidates
        ));

        assertEquals(25, result.rebalancedDailyBudgetMinutes());
        assertEquals(3, result.activeTodayTasksCount());
        assertEquals(PruneReasonCode.BUDGET_CEILING_LOW_ROI_PRUNED, result.pruneReasonCode());
        assertEquals(2, result.prunedTasksCount());
        assertEquals(1, result.rescheduledReviewsCount());
        assertTrue(result.prunedConceptIds().contains("MATH.ALG.PRIOR_TASK"));
        assertTrue(result.prunedConceptIds().contains("MATH.GEO.LOW_ROI_CIRCLE"));
        assertTrue(result.prunedConceptIds().contains("MATH.PROB.HIGH_RETENTION"));
        assertEquals(3, result.activeTaskIds().size());
        assertTrue(result.activeTaskIds().contains(UUID.fromString("00000000-0000-0000-0000-000000000002")));
        assertTrue(result.activeTaskIds().contains(UUID.fromString("00000000-0000-0000-0000-000000000004")));
        assertTrue(result.activeTaskIds().contains(UUID.fromString("00000000-0000-0000-0000-000000000005")));
    }

    @Test
    @DisplayName("INV-REBAL-003: 5-Minute Mini Mode downshifts to budget=5 and 1 active mission")
    void testMiniModeDownshift() {
        List<MissedDayRecoveryPolicy.CandidateTask> candidates = List.of(
                new MissedDayRecoveryPolicy.CandidateTask(
                        UUID.fromString("00000000-0000-0000-0000-000000000011"),
                        "MATH.ALG.RATIONAL_EQ", 0.55, today, true
                ),
                new MissedDayRecoveryPolicy.CandidateTask(
                        UUID.fromString("00000000-0000-0000-0000-000000000012"),
                        "MATH.ALG.QUADRATIC", 0.40, today, false
                ),
                new MissedDayRecoveryPolicy.CandidateTask(
                        UUID.fromString("00000000-0000-0000-0000-000000000013"),
                        "MATH.CALC.CHAIN_RULE", 0.35, today, false
                )
        );

        var result = policy.evaluate(new MissedDayRecoveryPolicy.RecoveryInput(
                today, null, 7, 15, 30, true, candidates
        ));

        assertTrue(result.miniModeActivated());
        assertEquals(5, result.rebalancedDailyBudgetMinutes());
        assertEquals(1, result.activeTodayTasksCount());
        assertEquals(PruneReasonCode.MINI_MODE_SINGLE_FOCUS, result.pruneReasonCode());
        assertEquals("studyplan.rebalance.welcome_mini_mode", result.welcomeMessageKey());
        assertEquals(List.of(UUID.fromString("00000000-0000-0000-0000-000000000011")), result.activeTaskIds());
    }
}
