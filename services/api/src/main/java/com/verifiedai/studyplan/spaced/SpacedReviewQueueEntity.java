package com.verifiedai.studyplan.spaced;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "spaced_review_queue")
public class SpacedReviewQueueEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "canonical_skill_code", nullable = false, length = 96)
    private String canonicalSkillCode;

    @Column(name = "target_mistake_code", length = 64)
    private String targetMistakeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32)
    private SpacedTriggerType triggerType;

    @Column(name = "estimated_retention", nullable = false, precision = 5, scale = 4)
    private BigDecimal estimatedRetention = new BigDecimal("0.7200");

    @Column(name = "half_life_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal halfLifeDays = new BigDecimal("3.00");

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "assigned_twin_id")
    private UUID assignedTwinId;

    @Column(name = "prompt_preview_latex", nullable = false, columnDefinition = "TEXT")
    private String promptPreviewLatex = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SpacedQueueStatus status = SpacedQueueStatus.PENDING;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public SpacedReviewQueueEntity() {
    }

    public SpacedReviewQueueEntity(
            UUID id,
            UUID userId,
            String canonicalSkillCode,
            String targetMistakeCode,
            SpacedTriggerType triggerType,
            BigDecimal estimatedRetention,
            BigDecimal halfLifeDays,
            Instant dueAt,
            UUID assignedTwinId,
            String promptPreviewLatex,
            SpacedQueueStatus status
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.canonicalSkillCode = Objects.requireNonNull(canonicalSkillCode, "canonicalSkillCode cannot be null");
        this.targetMistakeCode = targetMistakeCode;
        this.triggerType = Objects.requireNonNull(triggerType, "triggerType cannot be null");
        this.estimatedRetention = estimatedRetention != null ? estimatedRetention : new BigDecimal("0.7200");
        this.halfLifeDays = halfLifeDays != null ? halfLifeDays : new BigDecimal("3.00");
        this.dueAt = Objects.requireNonNull(dueAt, "dueAt cannot be null");
        this.assignedTwinId = assignedTwinId;
        this.promptPreviewLatex = promptPreviewLatex != null ? promptPreviewLatex : "";
        this.status = status != null ? status : SpacedQueueStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getCanonicalSkillCode() {
        return canonicalSkillCode;
    }

    public void setCanonicalSkillCode(String canonicalSkillCode) {
        this.canonicalSkillCode = canonicalSkillCode;
    }

    public String getTargetMistakeCode() {
        return targetMistakeCode;
    }

    public void setTargetMistakeCode(String targetMistakeCode) {
        this.targetMistakeCode = targetMistakeCode;
    }

    public SpacedTriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(SpacedTriggerType triggerType) {
        this.triggerType = triggerType;
    }

    public BigDecimal getEstimatedRetention() {
        return estimatedRetention;
    }

    public void setEstimatedRetention(BigDecimal estimatedRetention) {
        this.estimatedRetention = estimatedRetention;
    }

    public BigDecimal getHalfLifeDays() {
        return halfLifeDays;
    }

    public void setHalfLifeDays(BigDecimal halfLifeDays) {
        this.halfLifeDays = halfLifeDays;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public UUID getAssignedTwinId() {
        return assignedTwinId;
    }

    public void setAssignedTwinId(UUID assignedTwinId) {
        this.assignedTwinId = assignedTwinId;
    }

    public String getPromptPreviewLatex() {
        return promptPreviewLatex;
    }

    public void setPromptPreviewLatex(String promptPreviewLatex) {
        this.promptPreviewLatex = promptPreviewLatex;
    }

    public SpacedQueueStatus getStatus() {
        return status;
    }

    public void setStatus(SpacedQueueStatus status) {
        this.status = status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPedagogicalBadgeKey() {
        return switch (triggerType) {
            case TRAP_IMMUNITY_7D -> "studyplan.spaced.badge.trap_immunity_7d";
            case RETENTION_DECAY -> "studyplan.spaced.badge.retention_refresh";
        };
    }
}
