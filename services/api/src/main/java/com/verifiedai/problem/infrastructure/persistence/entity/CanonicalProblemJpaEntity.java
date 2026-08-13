package com.verifiedai.problem.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "canonical_problems")
public class CanonicalProblemJpaEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "problem_session_id", nullable = false)
    private UUID problemSessionId;

    @Column(name = "problem_parse_id", nullable = false)
    private UUID problemParseId;

    @Column(name = "problem_parse_revision", nullable = false)
    private int problemParseRevision;

    @Column(name = "canonical_revision", nullable = false)
    private int canonicalRevision;

    @Column(name = "schema_version", nullable = false)
    private String schemaVersion;

    @Column(name = "verifier_schema_version", nullable = false)
    private String verifierSchemaVersion;

    @Column(name = "problem_type", nullable = false)
    private String problemType;

    @Column(name = "task_type", nullable = false)
    private String taskType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "canonical_problem_jsonb", nullable = false, columnDefinition = "jsonb")
    private String canonicalProblemJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "verifier_input_jsonb", nullable = false, columnDefinition = "jsonb")
    private String verifierInputJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "display_jsonb", nullable = false, columnDefinition = "jsonb")
    private String displayJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CanonicalProblemJpaEntity() {
    }

    @SuppressWarnings("ParameterNumber")
    public CanonicalProblemJpaEntity(
        UUID id,
        UUID userId,
        UUID problemSessionId,
        UUID problemParseId,
        int problemParseRevision,
        int canonicalRevision,
        String schemaVersion,
        String verifierSchemaVersion,
        String problemType,
        String taskType,
        String canonicalProblemJson,
        String verifierInputJson,
        String displayJson,
        Instant createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.problemSessionId = problemSessionId;
        this.problemParseId = problemParseId;
        this.problemParseRevision = problemParseRevision;
        this.canonicalRevision = canonicalRevision;
        this.schemaVersion = schemaVersion;
        this.verifierSchemaVersion = verifierSchemaVersion;
        this.problemType = problemType;
        this.taskType = taskType;
        this.canonicalProblemJson = canonicalProblemJson;
        this.verifierInputJson = verifierInputJson;
        this.displayJson = displayJson;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public UUID problemSessionId() {
        return problemSessionId;
    }

    public UUID problemParseId() {
        return problemParseId;
    }

    public int problemParseRevision() {
        return problemParseRevision;
    }

    public int canonicalRevision() {
        return canonicalRevision;
    }

    public String schemaVersion() {
        return schemaVersion;
    }

    public String verifierSchemaVersion() {
        return verifierSchemaVersion;
    }

    public String problemType() {
        return problemType;
    }

    public String taskType() {
        return taskType;
    }

    public String canonicalProblemJson() {
        return canonicalProblemJson;
    }

    public String verifierInputJson() {
        return verifierInputJson;
    }

    public String displayJson() {
        return displayJson;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
