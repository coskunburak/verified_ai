package com.verifiedai.studyplan.rebalance;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "study_plan_rebalance_events")
public class RebalanceEventEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "study_plan_id")
    private UUID studyPlanId;

    @Column(name = "recovery_date", nullable = false)
    private LocalDate recoveryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32)
    private RebalanceTriggerType triggerType;

    @Column(name = "missed_days_count", nullable = false)
    private int missedDaysCount;

    @Column(name = "days_until_exam", nullable = false)
    private int daysUntilExam;

    @Column(name = "previous_daily_budget", nullable = false)
    private int previousDailyBudget;

    @Column(name = "rebalanced_daily_budget", nullable = false)
    private int rebalancedDailyBudget;

    @Column(name = "pruned_tasks_count", nullable = false)
    private int prunedTasksCount;

    @Column(name = "rescheduled_reviews_count", nullable = false)
    private int rescheduledReviewsCount;

    @Column(name = "active_today_tasks_count", nullable = false)
    private int activeTodayTasksCount;

    @Column(name = "mini_mode_activated", nullable = false)
    private boolean miniModeActivated;

    @Enumerated(EnumType.STRING)
    @Column(name = "prune_reason_code", nullable = false, length = 48)
    private PruneReasonCode pruneReasonCode = PruneReasonCode.NONE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pruned_concept_ids_json", nullable = false, columnDefinition = "jsonb")
    private String prunedConceptIdsJson = "[]";

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RebalanceEventEntity() {
    }

    public RebalanceEventEntity(
            UUID id,
            UUID userId,
            UUID studyPlanId,
            LocalDate recoveryDate,
            RebalanceTriggerType triggerType,
            int missedDaysCount,
            int daysUntilExam,
            int previousDailyBudget,
            int rebalancedDailyBudget,
            int prunedTasksCount,
            int rescheduledReviewsCount,
            int activeTodayTasksCount,
            boolean miniModeActivated,
            PruneReasonCode pruneReasonCode,
            String prunedConceptIdsJson,
            String idempotencyKey,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.studyPlanId = studyPlanId;
        this.recoveryDate = Objects.requireNonNull(recoveryDate, "recoveryDate cannot be null");
        this.triggerType = Objects.requireNonNull(triggerType, "triggerType cannot be null");
        this.missedDaysCount = missedDaysCount;
        this.daysUntilExam = daysUntilExam;
        this.previousDailyBudget = previousDailyBudget;
        this.rebalancedDailyBudget = rebalancedDailyBudget;
        this.prunedTasksCount = prunedTasksCount;
        this.rescheduledReviewsCount = rescheduledReviewsCount;
        this.activeTodayTasksCount = activeTodayTasksCount;
        this.miniModeActivated = miniModeActivated;
        this.pruneReasonCode = pruneReasonCode != null ? pruneReasonCode : PruneReasonCode.NONE;
        this.prunedConceptIdsJson = prunedConceptIdsJson != null ? prunedConceptIdsJson : "[]";
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getStudyPlanId() {
        return studyPlanId;
    }

    public LocalDate getRecoveryDate() {
        return recoveryDate;
    }

    public RebalanceTriggerType getTriggerType() {
        return triggerType;
    }

    public int getMissedDaysCount() {
        return missedDaysCount;
    }

    public int getDaysUntilExam() {
        return daysUntilExam;
    }

    public int getPreviousDailyBudget() {
        return previousDailyBudget;
    }

    public int getRebalancedDailyBudget() {
        return rebalancedDailyBudget;
    }

    public int getPrunedTasksCount() {
        return prunedTasksCount;
    }

    public int getRescheduledReviewsCount() {
        return rescheduledReviewsCount;
    }

    public int getActiveTodayTasksCount() {
        return activeTodayTasksCount;
    }

    public boolean isMiniModeActivated() {
        return miniModeActivated;
    }

    public PruneReasonCode getPruneReasonCode() {
        return pruneReasonCode;
    }

    public String getPrunedConceptIdsJson() {
        return prunedConceptIdsJson;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
