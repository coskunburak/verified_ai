package com.verifiedai.studyplan.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "learner_recommendations")
public class LearnerRecommendationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_type", nullable = false, length = 32)
    private RecommendationType recommendationType;

    @Column(name = "target_skill_code", nullable = false, length = 128)
    private String targetSkillCode;

    @Column(name = "target_mistake_code", length = 64)
    private String targetMistakeCode;

    @Column(name = "priority_score", nullable = false, precision = 8, scale = 4)
    private BigDecimal priorityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "rationale_code", nullable = false, length = 64)
    private RecommendationRationaleCode rationaleCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rationale_params_json", nullable = false, columnDefinition = "jsonb")
    private String rationaleParamsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RecommendationStatus status = RecommendationStatus.ACTIVE;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LearnerRecommendationEntity() {
    }

    public LearnerRecommendationEntity(
            UUID id,
            UUID userId,
            RecommendationType recommendationType,
            String targetSkillCode,
            String targetMistakeCode,
            BigDecimal priorityScore,
            RecommendationRationaleCode rationaleCode,
            String rationaleParamsJson,
            RecommendationStatus status,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.recommendationType = Objects.requireNonNull(recommendationType, "recommendationType cannot be null");
        this.targetSkillCode = Objects.requireNonNull(targetSkillCode, "targetSkillCode cannot be null");
        this.targetMistakeCode = targetMistakeCode;
        this.priorityScore = Objects.requireNonNull(priorityScore, "priorityScore cannot be null");
        this.rationaleCode = Objects.requireNonNull(rationaleCode, "rationaleCode cannot be null");
        this.rationaleParamsJson = Objects.requireNonNull(rationaleParamsJson, "rationaleParamsJson cannot be null");
        this.status = status != null ? status : RecommendationStatus.ACTIVE;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    protected void onPersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (status == null) {
            status = RecommendationStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void accept(Instant now) {
        if (this.status != RecommendationStatus.ACTIVE) {
            throw new RecommendationStateConflictException("Cannot accept recommendation with status: " + this.status);
        }
        if (this.expiresAt.isBefore(now)) {
            this.status = RecommendationStatus.EXPIRED;
            this.updatedAt = now;
            throw new RecommendationStateConflictException("Recommendation has expired at: " + this.expiresAt);
        }
        this.status = RecommendationStatus.ACCEPTED;
        this.updatedAt = now;
    }

    public void dismiss(Instant now) {
        if (this.status != RecommendationStatus.ACTIVE) {
            throw new RecommendationStateConflictException("Cannot dismiss recommendation with status: " + this.status);
        }
        if (this.expiresAt.isBefore(now)) {
            this.status = RecommendationStatus.EXPIRED;
            this.updatedAt = now;
            throw new RecommendationStateConflictException("Recommendation has expired at: " + this.expiresAt);
        }
        this.status = RecommendationStatus.DISMISSED;
        this.updatedAt = now;
    }

    public void markExpired(Instant now) {
        this.status = RecommendationStatus.EXPIRED;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public RecommendationType getRecommendationType() {
        return recommendationType;
    }

    public String getTargetSkillCode() {
        return targetSkillCode;
    }

    public String getTargetMistakeCode() {
        return targetMistakeCode;
    }

    public BigDecimal getPriorityScore() {
        return priorityScore;
    }

    public RecommendationRationaleCode getRationaleCode() {
        return rationaleCode;
    }

    public String getRationaleParamsJson() {
        return rationaleParamsJson;
    }

    public RecommendationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
