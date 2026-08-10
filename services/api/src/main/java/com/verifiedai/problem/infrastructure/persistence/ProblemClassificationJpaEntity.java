package com.verifiedai.problem.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "problem_classifications")
public class ProblemClassificationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "classification_job_id", nullable = false)
    private UUID classificationJobId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "problem_session_id", nullable = false)
    private UUID problemSessionId;

    @Column(name = "canonical_problem_id", nullable = false)
    private UUID canonicalProblemId;

    @Column(nullable = false)
    private int revision;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String status;

    @Column(name = "review_reason")
    private String reviewReason;

    @Column(name = "ontology_version", nullable = false)
    private String ontologyVersion;

    @Column(
        name = "classification_schema_version",
        nullable = false
    )
    private String classificationSchemaVersion;

    @Column(name = "projection_version", nullable = false)
    private String projectionVersion;

    @Column(name = "subject_id")
    private String subjectId;

    @Column(name = "topic_id")
    private String topicId;

    @Column(name = "primary_skill_id")
    private String primarySkillId;

    @Column
    private String difficulty;

    @Column(
        name = "difficulty_policy_version",
        nullable = false
    )
    private String difficultyPolicyVersion;

    @Column(name = "confidence_band", nullable = false)
    private String confidenceBand;

    @Column(
        name = "confidence_policy_version",
        nullable = false
    )
    private String confidencePolicyVersion;

    @Column(
        name = "confidence_calibration",
        nullable = false
    )
    private String confidenceCalibration;

    @Column(nullable = false)
    private String capability;

    @Column
    private String provider;

    @Column
    private String model;

    @Column(name = "prompt_id", nullable = false)
    private String promptId;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(name = "route_policy_version", nullable = false)
    private String routePolicyVersion;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Column(name = "provider_latency_ms")
    private Long providerLatencyMs;

    @Column(name = "estimated_cost_micros")
    private Long estimatedCostMicros;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
        name = "request_fingerprint",
        nullable = false,
        length = 64
    )
    private String requestFingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProblemClassificationJpaEntity() {
    }

    @SuppressWarnings("ParameterNumber")
    public ProblemClassificationJpaEntity(
        UUID id,
        UUID classificationJobId,
        UUID userId,
        UUID problemSessionId,
        UUID canonicalProblemId,
        int revision,
        String source,
        String status,
        String reviewReason,
        String ontologyVersion,
        String classificationSchemaVersion,
        String projectionVersion,
        String subjectId,
        String topicId,
        String primarySkillId,
        String difficulty,
        String difficultyPolicyVersion,
        String confidenceBand,
        String confidencePolicyVersion,
        String confidenceCalibration,
        String capability,
        String provider,
        String model,
        String promptId,
        String promptVersion,
        String routePolicyVersion,
        boolean fallbackUsed,
        Long providerLatencyMs,
        Long estimatedCostMicros,
        String requestFingerprint,
        Instant createdAt
    ) {
        this.id = id;
        this.classificationJobId = classificationJobId;
        this.userId = userId;
        this.problemSessionId = problemSessionId;
        this.canonicalProblemId = canonicalProblemId;
        this.revision = revision;
        this.source = source;
        this.status = status;
        this.reviewReason = reviewReason;
        this.ontologyVersion = ontologyVersion;
        this.classificationSchemaVersion = classificationSchemaVersion;
        this.projectionVersion = projectionVersion;
        this.subjectId = subjectId;
        this.topicId = topicId;
        this.primarySkillId = primarySkillId;
        this.difficulty = difficulty;
        this.difficultyPolicyVersion = difficultyPolicyVersion;
        this.confidenceBand = confidenceBand;
        this.confidencePolicyVersion = confidencePolicyVersion;
        this.confidenceCalibration = confidenceCalibration;
        this.capability = capability;
        this.provider = provider;
        this.model = model;
        this.promptId = promptId;
        this.promptVersion = promptVersion;
        this.routePolicyVersion = routePolicyVersion;
        this.fallbackUsed = fallbackUsed;
        this.providerLatencyMs = providerLatencyMs;
        this.estimatedCostMicros = estimatedCostMicros;
        this.requestFingerprint = requestFingerprint;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }

    public UUID classificationJobId() {
        return classificationJobId;
    }

    public UUID userId() { return userId; }

    public UUID problemSessionId() {
        return problemSessionId;
    }

    public UUID canonicalProblemId() {
        return canonicalProblemId;
    }

    public int revision() { return revision; }

    public String source() { return source; }

    public String status() { return status; }

    public String reviewReason() {
        return reviewReason;
    }

    public String ontologyVersion() {
        return ontologyVersion;
    }

    public String classificationSchemaVersion() {
        return classificationSchemaVersion;
    }

    public String projectionVersion() {
        return projectionVersion;
    }

    public String subjectId() { return subjectId; }

    public String topicId() { return topicId; }

    public String primarySkillId() {
        return primarySkillId;
    }

    public String difficulty() { return difficulty; }

    public String difficultyPolicyVersion() {
        return difficultyPolicyVersion;
    }

    public String confidenceBand() {
        return confidenceBand;
    }

    public String confidencePolicyVersion() {
        return confidencePolicyVersion;
    }

    public String confidenceCalibration() {
        return confidenceCalibration;
    }

    public String capability() { return capability; }

    public String provider() { return provider; }

    public String model() { return model; }

    public String promptId() { return promptId; }

    public String promptVersion() {
        return promptVersion;
    }

    public String routePolicyVersion() {
        return routePolicyVersion;
    }

    public boolean fallbackUsed() {
        return fallbackUsed;
    }

    public Long providerLatencyMs() {
        return providerLatencyMs;
    }

    public Long estimatedCostMicros() {
        return estimatedCostMicros;
    }

    public String requestFingerprint() {
        return requestFingerprint;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
