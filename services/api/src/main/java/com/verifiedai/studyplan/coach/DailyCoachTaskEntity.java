package com.verifiedai.studyplan.coach;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "daily_coach_tasks")
public class DailyCoachTaskEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "task_order", nullable = false)
    private int taskOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private DailyCoachTaskType taskType;

    @Column(name = "target_skill_code", nullable = false, length = 128)
    private String targetSkillCode;

    @Column(name = "target_mistake_code", length = 128)
    private String targetMistakeCode;

    @Column(name = "assigned_twin_id", nullable = false, length = 128)
    private String assignedTwinId;

    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes = 5;

    @Column(name = "why_this_task_rationale", nullable = false, columnDefinition = "TEXT")
    private String whyThisTaskRationale;

    @Column(name = "prompt_preview_latex", nullable = false, columnDefinition = "TEXT")
    private String promptPreviewLatex;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DailyCoachTaskStatus status = DailyCoachTaskStatus.PENDING;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected DailyCoachTaskEntity() {
    }

    public DailyCoachTaskEntity(
            UUID id,
            UUID sessionId,
            int taskOrder,
            DailyCoachTaskType taskType,
            String targetSkillCode,
            String targetMistakeCode,
            String assignedTwinId,
            int estimatedMinutes,
            String whyThisTaskRationale,
            String promptPreviewLatex,
            DailyCoachTaskStatus status
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId cannot be null");
        this.taskOrder = taskOrder;
        this.taskType = Objects.requireNonNull(taskType, "taskType cannot be null");
        this.targetSkillCode = Objects.requireNonNull(targetSkillCode, "targetSkillCode cannot be null");
        this.targetMistakeCode = targetMistakeCode;
        this.assignedTwinId = Objects.requireNonNull(assignedTwinId, "assignedTwinId cannot be null");
        this.estimatedMinutes = estimatedMinutes;
        this.whyThisTaskRationale = Objects.requireNonNull(whyThisTaskRationale, "whyThisTaskRationale cannot be null");
        this.promptPreviewLatex = Objects.requireNonNull(promptPreviewLatex, "promptPreviewLatex cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getTaskOrder() {
        return taskOrder;
    }

    public DailyCoachTaskType getTaskType() {
        return taskType;
    }

    public String getTargetSkillCode() {
        return targetSkillCode;
    }

    public String getTargetMistakeCode() {
        return targetMistakeCode;
    }

    public String getAssignedTwinId() {
        return assignedTwinId;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public String getWhyThisTaskRationale() {
        return whyThisTaskRationale;
    }

    public String getPromptPreviewLatex() {
        return promptPreviewLatex;
    }

    public DailyCoachTaskStatus getStatus() {
        return status;
    }

    public void setStatus(DailyCoachTaskStatus status) {
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
