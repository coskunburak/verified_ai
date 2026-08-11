package com.verifiedai.problem.infrastructure.persistence;

import com.verifiedai.ai.application.AiProvenance;
import com.verifiedai.ai.application.AiUsage;
import com.verifiedai.problem.domain.model.ProblemParseSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "problem_parses")
public class ProblemParseJpaEntity {
    @Id
    private UUID id;

    @Column(name = "parse_job_id")
    private UUID parseJobId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "problem_session_id", nullable = false)
    private UUID problemSessionId;

    @Column(name = "recognition_evidence_id", nullable = false)
    private UUID recognitionEvidenceId;

    @Column(name = "recognition_evidence_revision", nullable = false)
    private int recognitionEvidenceRevision;

    @Column(nullable = false)
    private int revision;

    @Column(nullable = false)
    private String source;

    @Column(name = "support_status", nullable = false)
    private String supportStatus;

    @Column(name = "unsupported_reason")
    private String unsupportedReason;

    @Column(name = "review_required", nullable = false)
    private boolean reviewRequired;

    @Column(name = "schema_version", nullable = false)
    private String schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_output_jsonb", columnDefinition = "jsonb")
    private String rawOutputJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_problem_jsonb", nullable = false, columnDefinition = "jsonb")
    private String normalizedProblemJson;

    @Column
    private String provider;

    @Column
    private String model;

    @Column(name = "route_policy_version")
    private String routePolicyVersion;

    @Column(name = "prompt_id")
    private String promptId;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "provider_request_id")
    private String providerRequestId;

    @Column(name = "provider_response_id")
    private String providerResponseId;

    @Column(name = "fallback_used")
    private Boolean fallbackUsed;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "image_units")
    private Integer imageUnits;

    @Column(name = "request_units")
    private Integer requestUnits;

    @Column(name = "provider_latency_ms")
    private Long providerLatencyMs;

    @Column(name = "total_latency_ms")
    private Long totalLatencyMs;

    @Column(name = "estimated_cost_micros")
    private Long estimatedCostMicros;

    @Column
    private String currency;

    @Column(name = "pricing_version")
    private String pricingVersion;

    @Column(name = "raw_output_retention_until")
    private Instant rawOutputRetentionUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "parent_parse_id")
    private UUID parentParseId;

    @Column(name = "correction_idempotency_key", length = 128)
    private String correctionIdempotencyKey;

    @Column(name = "correction_request_hash", length = 64)
    private String correctionRequestHash;

    @Column(name = "correction_reason", length = 32)
    private String correctionReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "corrected_fields_jsonb", columnDefinition = "jsonb")
    private String correctedFieldsJson;

    @Column(name = "correction_schema_version", length = 64)
    private String correctionSchemaVersion;

    protected ProblemParseJpaEntity() {
    }

    @SuppressWarnings("ParameterNumber")
    public static ProblemParseJpaEntity fromAi(
        UUID id,
        UUID parseJobId,
        UUID userId,
        UUID problemSessionId,
        UUID recognitionEvidenceId,
        int recognitionEvidenceRevision,
        int revision,
        String supportStatus,
        String unsupportedReason,
        boolean reviewRequired,
        String schemaVersion,
        String rawOutputJson,
        String normalizedProblemJson,
        AiProvenance provenance,
        AiUsage usage,
        long providerLatencyMs,
        long totalLatencyMs,
        Instant rawOutputRetentionUntil,
        Instant createdAt
    ) {
        ProblemParseJpaEntity entity = new ProblemParseJpaEntity();
        entity.id = id;
        entity.parseJobId = parseJobId;
        entity.userId = userId;
        entity.problemSessionId = problemSessionId;
        entity.recognitionEvidenceId = recognitionEvidenceId;
        entity.recognitionEvidenceRevision = recognitionEvidenceRevision;
        entity.revision = revision;
        entity.source = ProblemParseSource.AI.name();
        entity.supportStatus = supportStatus;
        entity.unsupportedReason = unsupportedReason;
        entity.reviewRequired = reviewRequired;
        entity.schemaVersion = schemaVersion;
        entity.rawOutputJson = rawOutputJson;
        entity.normalizedProblemJson = normalizedProblemJson;
        entity.provider = provenance.provider();
        entity.model = provenance.model();
        entity.routePolicyVersion = provenance.routePolicyVersion();
        entity.promptId = provenance.promptId();
        entity.promptVersion = provenance.promptVersion();
        entity.providerRequestId = provenance.providerRequestId();
        entity.providerResponseId = provenance.providerResponseId();
        entity.fallbackUsed = provenance.fallbackUsed();
        entity.inputTokens = usage.inputTokens();
        entity.outputTokens = usage.outputTokens();
        entity.imageUnits = usage.imageUnits();
        entity.requestUnits = usage.requestUnits();
        entity.providerLatencyMs = providerLatencyMs;
        entity.totalLatencyMs = totalLatencyMs;
        entity.estimatedCostMicros = usage.estimatedCostMicros();
        entity.currency = usage.currency();
        entity.pricingVersion = usage.pricingVersion();
        entity.rawOutputRetentionUntil = rawOutputRetentionUntil;
        entity.createdAt = createdAt;
        entity.parentParseId = null;
        entity.correctionIdempotencyKey = null;
        entity.correctionRequestHash = null;
        entity.correctionReason = null;
        entity.correctedFieldsJson = null;
        entity.correctionSchemaVersion = null;
        return entity;
    }

    @SuppressWarnings("ParameterNumber")
    public static ProblemParseJpaEntity fromUserCorrection(
        UUID id,
        UUID parentParseId,
        UUID userId,
        UUID problemSessionId,
        UUID recognitionEvidenceId,
        int recognitionEvidenceRevision,
        int revision,
        String supportStatus,
        String unsupportedReason,
        boolean reviewRequired,
        String schemaVersion,
        String normalizedProblemJson,
        String correctionIdempotencyKey,
        String correctionRequestHash,
        String correctionReason,
        String correctedFieldsJson,
        String correctionSchemaVersion,
        Instant createdAt
    ) {
        ProblemParseJpaEntity entity = new ProblemParseJpaEntity();
        entity.id = id;
        entity.userId = userId;
        entity.problemSessionId = problemSessionId;
        entity.recognitionEvidenceId = recognitionEvidenceId;
        entity.recognitionEvidenceRevision = recognitionEvidenceRevision;
        entity.revision = revision;
        entity.source = ProblemParseSource.USER.name();
        entity.supportStatus = supportStatus;
        entity.unsupportedReason = unsupportedReason;
        entity.reviewRequired = reviewRequired;
        entity.schemaVersion = schemaVersion;
        entity.normalizedProblemJson = normalizedProblemJson;
        entity.createdAt = createdAt;
        entity.parentParseId = parentParseId;
        entity.correctionIdempotencyKey = correctionIdempotencyKey;
        entity.correctionRequestHash = correctionRequestHash;
        entity.correctionReason = correctionReason;
        entity.correctedFieldsJson = correctedFieldsJson;
        entity.correctionSchemaVersion = correctionSchemaVersion;
        entity.parseJobId = null;
        entity.rawOutputJson = null;
        entity.provider = null;
        entity.model = null;
        entity.routePolicyVersion = null;
        entity.promptId = null;
        entity.promptVersion = null;
        entity.providerRequestId = null;
        entity.providerResponseId = null;
        entity.fallbackUsed = null;
        entity.inputTokens = null;
        entity.outputTokens = null;
        entity.imageUnits = null;
        entity.requestUnits = null;
        entity.providerLatencyMs = null;
        entity.totalLatencyMs = null;
        entity.estimatedCostMicros = null;
        entity.currency = null;
        entity.pricingVersion = null;
        entity.rawOutputRetentionUntil = null;
        return entity;
    }

    public UUID id() {
        return id;
    }

    public UUID parseJobId() {
        return parseJobId;
    }

    public UUID userId() {
        return userId;
    }

    public UUID problemSessionId() {
        return problemSessionId;
    }

    public UUID recognitionEvidenceId() {
        return recognitionEvidenceId;
    }

    public int recognitionEvidenceRevision() {
        return recognitionEvidenceRevision;
    }

    public int revision() {
        return revision;
    }

    public String source() {
        return source;
    }

    public String supportStatus() {
        return supportStatus;
    }

    public String unsupportedReason() {
        return unsupportedReason;
    }

    public boolean reviewRequired() {
        return reviewRequired;
    }

    public String schemaVersion() {
        return schemaVersion;
    }

    public String rawOutputJson() {
        return rawOutputJson;
    }

    public String normalizedProblemJson() {
        return normalizedProblemJson;
    }

    public String provider() {
        return provider;
    }

    public String model() {
        return model;
    }

    public String routePolicyVersion() {
        return routePolicyVersion;
    }

    public String promptId() {
        return promptId;
    }

    public String promptVersion() {
        return promptVersion;
    }

    public Integer inputTokens() {
        return inputTokens;
    }

    public Integer outputTokens() {
        return outputTokens;
    }

    public Integer imageUnits() {
        return imageUnits;
    }

    public Integer requestUnits() {
        return requestUnits;
    }

    public Long providerLatencyMs() {
        return providerLatencyMs;
    }

    public Long totalLatencyMs() {
        return totalLatencyMs;
    }

    public Long estimatedCostMicros() {
        return estimatedCostMicros;
    }

    public String currency() {
        return currency;
    }

    public String pricingVersion() {
        return pricingVersion;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public UUID parentParseId() {
        return parentParseId;
    }

    public String correctionIdempotencyKey() {
        return correctionIdempotencyKey;
    }

    public String correctionRequestHash() {
        return correctionRequestHash;
    }

    public String correctionReason() {
        return correctionReason;
    }

    public String correctedFieldsJson() {
        return correctedFieldsJson;
    }

    public String correctionSchemaVersion() {
        return correctionSchemaVersion;
    }
}
