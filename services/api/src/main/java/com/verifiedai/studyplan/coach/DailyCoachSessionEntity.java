package com.verifiedai.studyplan.coach;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "daily_coach_sessions")
public class DailyCoachSessionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "local_date", nullable = false, updatable = false)
    private LocalDate localDate;

    @Column(name = "budget_minutes", nullable = false)
    private int budgetMinutes = 15;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DailyCoachSessionStatus status = DailyCoachSessionStatus.IN_PROGRESS;

    @Column(name = "is_downshifted", nullable = false)
    private boolean isDownshifted = false;

    @Column(name = "is_cold_start", nullable = false)
    private boolean isColdStart = false;

    @Column(name = "completed_tasks_count", nullable = false)
    private int completedTasksCount = 0;

    @Column(name = "total_tasks_count", nullable = false)
    private int totalTasksCount = 3;

    @Column(name = "briefing_text", nullable = false, columnDefinition = "TEXT")
    private String briefingText;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_mode", nullable = false, length = 32)
    private DailyCoachMode preferredMode = DailyCoachMode.SOCRATIC_COACH_MODE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected DailyCoachSessionEntity() {
    }

    public DailyCoachSessionEntity(
            UUID id,
            UUID userId,
            LocalDate localDate,
            int budgetMinutes,
            DailyCoachSessionStatus status,
            boolean isDownshifted,
            boolean isColdStart,
            int completedTasksCount,
            int totalTasksCount,
            String briefingText,
            DailyCoachMode preferredMode,
            Instant now
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.localDate = Objects.requireNonNull(localDate, "localDate cannot be null");
        this.budgetMinutes = budgetMinutes;
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.isDownshifted = isDownshifted;
        this.isColdStart = isColdStart;
        this.completedTasksCount = completedTasksCount;
        this.totalTasksCount = totalTasksCount;
        this.briefingText = Objects.requireNonNull(briefingText, "briefingText cannot be null");
        this.preferredMode = Objects.requireNonNull(preferredMode, "preferredMode cannot be null");
        Instant ts = now != null ? now : Instant.now();
        this.createdAt = ts;
        this.updatedAt = ts;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }

    public int getBudgetMinutes() {
        return budgetMinutes;
    }

    public void setBudgetMinutes(int budgetMinutes) {
        this.budgetMinutes = budgetMinutes;
    }

    public DailyCoachSessionStatus getStatus() {
        return status;
    }

    public void setStatus(DailyCoachSessionStatus status) {
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    public boolean isDownshifted() {
        return isDownshifted;
    }

    public void setDownshifted(boolean downshifted) {
        isDownshifted = downshifted;
    }

    public boolean isColdStart() {
        return isColdStart;
    }

    public void setColdStart(boolean coldStart) {
        isColdStart = coldStart;
    }

    public int getCompletedTasksCount() {
        return completedTasksCount;
    }

    public void setCompletedTasksCount(int completedTasksCount) {
        this.completedTasksCount = completedTasksCount;
    }

    public int getTotalTasksCount() {
        return totalTasksCount;
    }

    public void setTotalTasksCount(int totalTasksCount) {
        this.totalTasksCount = totalTasksCount;
    }

    public String getBriefingText() {
        return briefingText;
    }

    public void setBriefingText(String briefingText) {
        this.briefingText = Objects.requireNonNull(briefingText, "briefingText cannot be null");
    }

    public DailyCoachMode getPreferredMode() {
        return preferredMode;
    }

    public void setPreferredMode(DailyCoachMode preferredMode) {
        this.preferredMode = Objects.requireNonNull(preferredMode, "preferredMode cannot be null");
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }
}
