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

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "problem_session_id", nullable = false)
    private UUID problemSessionId;

    @Column(name = "canonical_problem_id", nullable = false)
    private UUID canonicalProblemId;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "ontology_version", nullable = false)
    private String ontologyVersion;

    @Column(name = "schema_version", nullable = false)
    private String schemaVersion;

    @Column(name = "subject_id")
    private String subjectId;

    @Column(name = "topic_id")
    private String topicId;

    @Column(name = "primary_skill_id")
    private String primarySkillId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "secondary_skill_ids", columnDefinition = "jsonb")
    private String secondarySkillIds;

    @Column(name = "difficulty")
    private String difficulty;

    @Column(name = "confidence")
    private String confidence;

    @Column(name = "provider")
    private String provider;

    @Column(name = "model")
    private String model;

    @Column(name = "prompt_id")
    private String promptId;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "route_policy_version")
    private String routePolicyVersion;

    @Column(name = "classification_schema_version")
    private String classificationSchemaVersion;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Column(name = "provider_latency_ms")
    private Integer providerLatencyMs;

    @Column(name = "estimated_cost_micros")
    private Long estimatedCostMicros;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProblemClassificationJpaEntity() {
    }

    @SuppressWarnings("ParameterNumber")
    public ProblemClassificationJpaEntity(
        UUID id,
        UUID userId,
        UUID problemSessionId,
        UUID canonicalProblemId,
        int revision,
        String status,
        String ontologyVersion,
        String schemaVersion,
        String subjectId,
        String topicId,
        String primarySkillId,
        String secondarySkillIds,
        String difficulty,
        String confidence,
        String provider,
        String model,
        String promptId,
        String promptVersion,
        String routePolicyVersion,
        String classificationSchemaVersion,
        boolean fallbackUsed,
        Integer providerLatencyMs,
        Long estimatedCostMicros,
        Instant createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.problemSessionId = problemSessionId;
        this.canonicalProblemId = canonicalProblemId;
        this.revision = revision;
        this.status = status;
        this.ontologyVersion = ontologyVersion;
        this.schemaVersion = schemaVersion;
        this.subjectId = subjectId;
        this.topicId = topicId;
        this.primarySkillId = primarySkillId;
        this.secondarySkillIds = secondarySkillIds;
        this.difficulty = difficulty;
        this.confidence = confidence;
        this.provider = provider;
        this.model = model;
        this.promptId = promptId;
        this.promptVersion = promptVersion;
        this.routePolicyVersion = routePolicyVersion;
        this.classificationSchemaVersion = classificationSchemaVersion;
        this.fallbackUsed = fallbackUsed;
        this.providerLatencyMs = providerLatencyMs;
        this.estimatedCostMicros = estimatedCostMicros;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public UUID problemSessionId() { return problemSessionId; }
    public UUID canonicalProblemId() { return canonicalProblemId; }
    public int revision() { return revision; }
    public String status() { return status; }
    public String ontologyVersion() { return ontologyVersion; }
    public String schemaVersion() { return schemaVersion; }
    public String subjectId() { return subjectId; }
    public String topicId() { return topicId; }
    public String primarySkillId() { return primarySkillId; }
    public String secondarySkillIds() { return secondarySkillIds; }
    public String difficulty() { return difficulty; }
    public String confidence() { return confidence; }
    public String provider() { return provider; }
    public String model() { return model; }
    public String promptId() { return promptId; }
    public String promptVersion() { return promptVersion; }
    public String routePolicyVersion() { return routePolicyVersion; }
    public String classificationSchemaVersion() { return classificationSchemaVersion; }
    public boolean fallbackUsed() { return fallbackUsed; }
    public Integer providerLatencyMs() { return providerLatencyMs; }
    public Long estimatedCostMicros() { return estimatedCostMicros; }
    public Instant createdAt() { return createdAt; }
}
