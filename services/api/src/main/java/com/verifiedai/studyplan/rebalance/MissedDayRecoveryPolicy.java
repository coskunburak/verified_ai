package com.verifiedai.studyplan.rebalance;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pure deterministic domain engine for non-punitive missed-day recovery,
 * cognitive-load bounded daily budget rebalancing, and low-ROI backlog pruning.
 * Zero LLM or network dependencies ($0.00 cost, INV-REBAL-001..006).
 */
@Component
public class MissedDayRecoveryPolicy {

    public static final int MAX_DAILY_BUDGET_MINUTES = 25;
    public static final int MINI_MODE_BUDGET_MINUTES = 5;
    public static final int DEFAULT_BASELINE_BUDGET_MINUTES = 15;
    public static final int MAX_ACTIVE_TODAY_TASKS = 3;
    public static final int MAX_TODAY_TRAP_TASKS = 1;
    public static final int MAX_TODAY_SPACED_TASKS = 2;
    public static final double LOW_ROI_DELTA_NET_PER_HOUR_THRESHOLD = 0.20;
    public static final List<Integer> ALLOWED_BUDGET_STEPS = List.of(5, 10, 15, 20, 25);

    public record CandidateTask(
            UUID taskId,
            String conceptId,
            double deltaNetPerHour,
            double estimatedRetention,
            LocalDate sessionDate,
            boolean isTrapRepair
    ) {
        public CandidateTask(
                UUID taskId,
                String conceptId,
                double deltaNetPerHour,
                LocalDate sessionDate,
                boolean isTrapRepair
        ) {
            this(taskId, conceptId, deltaNetPerHour, 0.50, sessionDate, isTrapRepair);
        }
    }

    public record RecoveryInput(
            LocalDate asOfDate,
            LocalDate lastSessionDate,
            Integer missedDaysOverride,
            int baselineDailyBudgetMinutes,
            int daysUntilExam,
            boolean preferMiniMode,
            List<CandidateTask> candidateTasks
    ) {
    }

    public record RecoveryComputation(
            int missedDaysCount,
            int daysUntilExam,
            int previousDailyBudgetMinutes,
            int rebalancedDailyBudgetMinutes,
            boolean recoveryNeeded,
            boolean miniModeActivated,
            boolean miniModeRecommended,
            int activeTodayTasksCount,
            int prunedTasksCount,
            int rescheduledReviewsCount,
            PruneReasonCode pruneReasonCode,
            List<String> prunedConceptIds,
            List<UUID> activeTaskIds,
            String welcomeMessageKey
    ) {
    }

    public RecoveryComputation evaluate(RecoveryInput input) {
        Objects.requireNonNull(input, "input cannot be null");
        LocalDate asOfDate = input.asOfDate() != null ? input.asOfDate() : LocalDate.now();
        int previousBudget = normalizeBaselineBudget(input.baselineDailyBudgetMinutes());
        int tExam = Math.max(1, Math.min(730, input.daysUntilExam()));
        int missedDaysCount = resolveMissedDays(asOfDate, input.lastSessionDate(), input.missedDaysOverride());
        List<CandidateTask> candidates = input.candidateTasks() != null ? input.candidateTasks() : List.of();

        double rawBudget = computeRawBudget(previousBudget, tExam, missedDaysCount);
        boolean demandExceedsCeiling = rawBudget > MAX_DAILY_BUDGET_MINUTES || (missedDaysCount > 0 && tExam <= missedDaysCount);

        List<CandidateTask> priorDayTasks = new ArrayList<>();
        List<CandidateTask> todayCandidates = new ArrayList<>();
        for (CandidateTask candidate : candidates) {
            if (candidate.sessionDate() != null && candidate.sessionDate().isBefore(asOfDate)) {
                priorDayTasks.add(candidate);
            } else {
                todayCandidates.add(candidate);
            }
        }

        LinkedHashSet<String> prunedConceptIds = new LinkedHashSet<>();
        for (CandidateTask prior : priorDayTasks) {
            if (prior.conceptId() != null && !prior.conceptId().isBlank()) {
                prunedConceptIds.add(prior.conceptId());
            }
        }
        int prunedCount = priorDayTasks.size();
        int rescheduledCount = 0;
        boolean lowRoiPruned = false;
        boolean capEnforced = false;

        List<CandidateTask> selectedToday = new ArrayList<>();
        if (input.preferMiniMode()) {
            // INV-REBAL-003: Single highest-ROI micro-mission (<= 300 seconds)
            Optional<CandidateTask> topTask = todayCandidates.stream()
                    .max(Comparator
                            .comparing(CandidateTask::isTrapRepair)
                            .thenComparingDouble(CandidateTask::deltaNetPerHour)
                            .thenComparingDouble(t -> 1.0 - clampRetention(t.estimatedRetention())));
            if (topTask.isPresent()) {
                selectedToday.add(topTask.get());
                for (CandidateTask candidate : todayCandidates) {
                    if (!Objects.equals(candidate.taskId(), topTask.get().taskId())) {
                        rescheduledCount++;
                        if (candidate.conceptId() != null && !candidate.conceptId().isBlank()) {
                            prunedConceptIds.add(candidate.conceptId());
                        }
                    }
                }
            }
            int activeCount = 1;
            String welcomeKey = "studyplan.rebalance.welcome_mini_mode";
            return new RecoveryComputation(
                    missedDaysCount,
                    tExam,
                    previousBudget,
                    MINI_MODE_BUDGET_MINUTES,
                    missedDaysCount >= 1 || prunedCount > 0 || rescheduledCount > 0,
                    true,
                    true,
                    activeCount,
                    prunedCount,
                    rescheduledCount,
                    PruneReasonCode.MINI_MODE_SINGLE_FOCUS,
                    List.copyOf(prunedConceptIds).stream().limit(50).toList(),
                    selectedToday.stream().map(CandidateTask::taskId).filter(Objects::nonNull).toList(),
                    welcomeKey
            );
        }

        // Standard cognitive-load bounded budget snapping (INV-REBAL-002)
        int rawCeil = (int) Math.ceil(rawBudget);
        boolean allowFive = (missedDaysCount == 0 && previousBudget == MINI_MODE_BUDGET_MINUTES);
        int rebalancedBudget = snapToAllowedBudget(rawCeil, allowFive);

        boolean shouldPruneForCapacity = demandExceedsCeiling || todayCandidates.size() > MAX_ACTIVE_TODAY_TASKS;
        if (!todayCandidates.isEmpty()) {
            List<CandidateTask> pool = new ArrayList<>(todayCandidates);

            if (demandExceedsCeiling && pool.size() > 1) {
                // Drop low-ROI tasks (< 0.20 deltaNet/hr) first while preserving at least 1 active mission
                List<CandidateTask> highRoi = new ArrayList<>();
                List<CandidateTask> lowRoi = new ArrayList<>();
                for (CandidateTask task : pool) {
                    if (task.deltaNetPerHour() < LOW_ROI_DELTA_NET_PER_HOUR_THRESHOLD) {
                        lowRoi.add(task);
                    } else {
                        highRoi.add(task);
                    }
                }
                if (!highRoi.isEmpty() && !lowRoi.isEmpty()) {
                    for (CandidateTask dropped : lowRoi) {
                        prunedCount++;
                        lowRoiPruned = true;
                        if (dropped.conceptId() != null && !dropped.conceptId().isBlank()) {
                            prunedConceptIds.add(dropped.conceptId());
                        }
                    }
                    pool = highRoi;
                } else if (highRoi.isEmpty() && lowRoi.size() > 1) {
                    // Keep the highest ROI among low-ROI tasks so at least 1 active task remains
                    lowRoi.sort(Comparator
                            .comparingDouble(CandidateTask::deltaNetPerHour).reversed()
                            .thenComparingDouble(t -> clampRetention(t.estimatedRetention())));
                    pool = new ArrayList<>(List.of(lowRoi.get(0)));
                    for (int i = 1; i < lowRoi.size(); i++) {
                        CandidateTask dropped = lowRoi.get(i);
                        prunedCount++;
                        lowRoiPruned = true;
                        if (dropped.conceptId() != null && !dropped.conceptId().isBlank()) {
                            prunedConceptIds.add(dropped.conceptId());
                        }
                    }
                }
            }

            // Enforce INV-REBAL-001: <= 1 active trap repair + <= 2 spaced reviews (<= 3 total)
            List<CandidateTask> trapCandidates = pool.stream()
                    .filter(CandidateTask::isTrapRepair)
                    .sorted(Comparator
                            .comparingDouble(CandidateTask::deltaNetPerHour).reversed()
                            .thenComparingDouble(t -> clampRetention(t.estimatedRetention())))
                    .toList();

            List<CandidateTask> spacedCandidates = pool.stream()
                    .filter(t -> !t.isTrapRepair())
                    .sorted(Comparator
                            .comparingDouble((CandidateTask t) -> t.deltaNetPerHour() < LOW_ROI_DELTA_NET_PER_HOUR_THRESHOLD ? 0.0 : 1.0)
                            .reversed()
                            .thenComparingDouble(t -> clampRetention(t.estimatedRetention()))
                            .thenComparing(Comparator.comparingDouble(CandidateTask::deltaNetPerHour).reversed()))
                    .toList();

            if (!trapCandidates.isEmpty()) {
                selectedToday.add(trapCandidates.get(0));
                for (int i = MAX_TODAY_TRAP_TASKS; i < trapCandidates.size(); i++) {
                    CandidateTask overflow = trapCandidates.get(i);
                    rescheduledCount++;
                    capEnforced = true;
                    if (overflow.conceptId() != null && !overflow.conceptId().isBlank()) {
                        prunedConceptIds.add(overflow.conceptId());
                    }
                }
            }

            int remainingSlots = MAX_ACTIVE_TODAY_TASKS - selectedToday.size();
            int spacedCap = Math.min(MAX_TODAY_SPACED_TASKS, remainingSlots);
            for (int i = 0; i < spacedCandidates.size(); i++) {
                CandidateTask candidate = spacedCandidates.get(i);
                if (i < spacedCap) {
                    selectedToday.add(candidate);
                } else {
                    if (shouldPruneForCapacity && candidate.deltaNetPerHour() < LOW_ROI_DELTA_NET_PER_HOUR_THRESHOLD) {
                        prunedCount++;
                        lowRoiPruned = true;
                    } else {
                        rescheduledCount++;
                        capEnforced = true;
                    }
                    if (candidate.conceptId() != null && !candidate.conceptId().isBlank()) {
                        prunedConceptIds.add(candidate.conceptId());
                    }
                }
            }
        }

        int activeTodayCount = selectedToday.isEmpty()
                ? MAX_ACTIVE_TODAY_TASKS
                : Math.max(1, Math.min(MAX_ACTIVE_TODAY_TASKS, selectedToday.size()));

        boolean recoveryNeeded = missedDaysCount >= 1 || !priorDayTasks.isEmpty() || rescheduledCount > 0;
        boolean miniModeRecommended = missedDaysCount >= 7;

        PruneReasonCode pruneReasonCode;
        if (lowRoiPruned) {
            pruneReasonCode = PruneReasonCode.BUDGET_CEILING_LOW_ROI_PRUNED;
        } else if (capEnforced) {
            pruneReasonCode = PruneReasonCode.DAILY_MISSION_CAP_ENFORCED;
        } else if (!priorDayTasks.isEmpty() || missedDaysCount >= 1) {
            pruneReasonCode = PruneReasonCode.MISSED_DAY_WINDOW_RESET;
        } else {
            pruneReasonCode = PruneReasonCode.NONE;
        }

        String welcomeMessageKey = recoveryNeeded
                ? "studyplan.rebalance.welcome_recovered"
                : "studyplan.rebalance.welcome_steady";

        return new RecoveryComputation(
                missedDaysCount,
                tExam,
                previousBudget,
                rebalancedBudget,
                recoveryNeeded,
                false,
                miniModeRecommended,
                activeTodayCount,
                prunedCount,
                rescheduledCount,
                pruneReasonCode,
                List.copyOf(prunedConceptIds).stream().limit(50).toList(),
                selectedToday.stream().map(CandidateTask::taskId).filter(Objects::nonNull).toList(),
                welcomeMessageKey
        );
    }

    public int snapToAllowedBudget(int rawMinutes, boolean allowFive) {
        int capped = Math.min(MAX_DAILY_BUDGET_MINUTES, Math.max(allowFive ? MINI_MODE_BUDGET_MINUTES : 10, rawMinutes));
        for (int step : ALLOWED_BUDGET_STEPS) {
            if (!allowFive && step == MINI_MODE_BUDGET_MINUTES) {
                continue;
            }
            if (step >= capped) {
                return step;
            }
        }
        return MAX_DAILY_BUDGET_MINUTES;
    }

    public double computeRawBudget(int baselineDailyBudgetMinutes, int daysUntilExam, int missedDaysCount) {
        if (missedDaysCount <= 0) {
            return baselineDailyBudgetMinutes;
        }
        int denominator = Math.max(1, daysUntilExam - missedDaysCount);
        return baselineDailyBudgetMinutes * ((double) daysUntilExam / (double) denominator);
    }

    private int resolveMissedDays(LocalDate asOfDate, LocalDate lastSessionDate, Integer missedDaysOverride) {
        if (missedDaysOverride != null) {
            return Math.max(0, Math.min(365, missedDaysOverride));
        }
        if (lastSessionDate == null || !lastSessionDate.isBefore(asOfDate)) {
            return 0;
        }
        long daysBetween = ChronoUnit.DAYS.between(lastSessionDate, asOfDate);
        if (daysBetween <= 1) {
            return 0;
        }
        return (int) Math.min(365, daysBetween - 1);
    }

    private int normalizeBaselineBudget(int baseline) {
        if (baseline <= 0) {
            return DEFAULT_BASELINE_BUDGET_MINUTES;
        }
        if (baseline <= MINI_MODE_BUDGET_MINUTES) {
            return MINI_MODE_BUDGET_MINUTES;
        }
        return snapToAllowedBudget(baseline, true);
    }

    private static double clampRetention(double retention) {
        if (Double.isNaN(retention)) {
            return 0.50;
        }
        return Math.max(0.0, Math.min(1.0, retention));
    }
}
