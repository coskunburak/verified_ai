package com.verifiedai.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "learner_mastery_calibration_records",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_calibration_attempt_skill", columnNames = {"attempt_id", "canonical_skill_code"})
    }
)
public class LearnerMasteryCalibrationRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "canonical_skill_code", nullable = false, length = 100)
    private String canonicalSkillCode;

    @Column(name = "predicted_retention_prob", nullable = false, precision = 5, scale = 4)
    private BigDecimal predictedRetentionProb;

    @Column(name = "observed_independent_outcome", nullable = false)
    private short observedIndependentOutcome;

    @Column(name = "brier_residual", nullable = false, precision = 6, scale = 5)
    private BigDecimal brierResidual;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LearnerMasteryCalibrationRecordEntity() {
    }

    public LearnerMasteryCalibrationRecordEntity(
        UUID userId,
        UUID attemptId,
        String canonicalSkillCode,
        BigDecimal predictedRetentionProb,
        short observedIndependentOutcome,
        BigDecimal brierResidual,
        Instant createdAt
    ) {
        this(UUID.randomUUID(), userId, attemptId, canonicalSkillCode, predictedRetentionProb, observedIndependentOutcome, brierResidual, createdAt);
    }

    public LearnerMasteryCalibrationRecordEntity(
        UUID id,
        UUID userId,
        UUID attemptId,
        String canonicalSkillCode,
        BigDecimal predictedRetentionProb,
        short observedIndependentOutcome,
        BigDecimal brierResidual,
        Instant createdAt
    ) {
        this.id = id;
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.attemptId = Objects.requireNonNull(attemptId, "attemptId cannot be null");
        this.canonicalSkillCode = Objects.requireNonNull(canonicalSkillCode, "canonicalSkillCode cannot be null");
        this.predictedRetentionProb = Objects.requireNonNull(predictedRetentionProb, "predictedRetentionProb cannot be null");
        this.observedIndependentOutcome = observedIndependentOutcome;
        this.brierResidual = Objects.requireNonNull(brierResidual, "brierResidual cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public String getCanonicalSkillCode() {
        return canonicalSkillCode;
    }

    public BigDecimal getPredictedRetentionProb() {
        return predictedRetentionProb;
    }

    public short getObservedIndependentOutcome() {
        return observedIndependentOutcome;
    }

    public BigDecimal getBrierResidual() {
        return brierResidual;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LearnerMasteryCalibrationRecordEntity that)) return false;
        return Objects.equals(attemptId, that.attemptId) && Objects.equals(canonicalSkillCode, that.canonicalSkillCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attemptId, canonicalSkillCode);
    }
}
