package com.verifiedai.problem.infrastructure.persistence;

import com.verifiedai.problem.domain.model.ProblemAssetPreprocessingSignalType;
import com.verifiedai.problem.domain.model.ProblemAssetQualitySeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "problem_asset_quality_evidence")
public class ProblemAssetQualityEvidenceJpaEntity {
    @Id
    private UUID id;

    @Column(name = "derivative_id", nullable = false)
    private UUID derivativeId;

    @Column(name = "source_asset_id", nullable = false)
    private UUID sourceAssetId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "signal_type", nullable = false)
    private String signalType;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private BigDecimal score;

    @Column(nullable = false)
    private BigDecimal threshold;

    @Column(name = "policy_version", nullable = false)
    private String policyVersion;

    @Column(name = "message_code", nullable = false)
    private String messageCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProblemAssetQualityEvidenceJpaEntity() {
    }

    public ProblemAssetQualityEvidenceJpaEntity(
        UUID id,
        UUID derivativeId,
        UUID sourceAssetId,
        UUID userId,
        ProblemAssetPreprocessingSignalType signalType,
        ProblemAssetQualitySeverity severity,
        BigDecimal score,
        BigDecimal threshold,
        String policyVersion,
        String messageCode,
        Instant createdAt
    ) {
        this.id = id;
        this.derivativeId = derivativeId;
        this.sourceAssetId = sourceAssetId;
        this.userId = userId;
        this.signalType = signalType.name();
        this.severity = severity.name();
        this.score = score;
        this.threshold = threshold;
        this.policyVersion = policyVersion;
        this.messageCode = messageCode;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID derivativeId() {
        return derivativeId;
    }

    public UUID sourceAssetId() {
        return sourceAssetId;
    }

    public UUID userId() {
        return userId;
    }

    public String signalType() {
        return signalType;
    }

    public String severity() {
        return severity;
    }

    public BigDecimal score() {
        return score;
    }

    public BigDecimal threshold() {
        return threshold;
    }

    public String policyVersion() {
        return policyVersion;
    }

    public String messageCode() {
        return messageCode;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
