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

    @Column(name = "parse_job_id", nullable = false)
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
    @Column(name = "raw_output_jsonb", nullable = false, columnDefinition = "jsonb")
    private String rawOutputJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_problem_jsonb", nullable = false, columnDefinition = "jsonb")
    private String normalizedProblemJson;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(name = "route_policy_version", nullable = false)
    private String routePolicyVersion;

    @Column(name = "prompt_id", nullable = false)
    private String promptId;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(name = "provider_request_id")
    private String providerRequestId;

    @Column(name = "provider_response_id")
    private String providerResponseId;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "image_units")
    private Integer imageUnits;

    @Column(name = "request_units", nullable = false)
    private int requestUnits;

    @Column(name = "provider_latency_ms", nullable = false)
    private long providerLatencyMs;

    @Column(name = "total_latency_ms", nullable = false)
    private long totalLatencyMs;

    @Column(name = "estimated_cost_micros", nullable = false)
    private long estimatedCostMicros;

    @Column(nullable = false)
    private String currency;

    @Column(name = "pricing_version", nullable = false)
    private String pricingVersion;

    @Column(name = "raw_output_retention_until")
    private Instant rawOutputRetentionUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProblemParseJpaEntity() {
    }

    @SuppressWarnings("ParameterNumber")
    public ProblemParseJpaEntity(
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
        this.id = id;
        this.parseJobId = parseJobId;
        this.userId = userId;
        this.problemSessionId = problemSessionId;
        this.recognitionEvidenceId = recognitionEvidenceId;
        this.recognitionEvidenceRevision = recognitionEvidenceRevision;
        this.revision = revision;
        this.source = ProblemParseSource.AI.name();
        this.supportStatus = supportStatus;
        this.unsupportedReason = unsupportedReason;
        this.reviewRequired = reviewRequired;
        this.schemaVersion = schemaVersion;
        this.rawOutputJson = rawOutputJson;
        this.normalizedProblemJson = normalizedProblemJson;
        this.provider = provenance.provider();
        this.model = provenance.model();
        this.routePolicyVersion = provenance.routePolicyVersion();
        this.promptId = provenance.promptId();
        this.promptVersion = provenance.promptVersion();
        this.providerRequestId = provenance.providerRequestId();
        this.providerResponseId = provenance.providerResponseId();
        this.fallbackUsed = provenance.fallbackUsed();
        this.inputTokens = usage.inputTokens();
        this.outputTokens = usage.outputTokens();
        this.imageUnits = usage.imageUnits();
        this.requestUnits = usage.requestUnits();
        this.providerLatencyMs = providerLatencyMs;
        this.totalLatencyMs = totalLatencyMs;
        this.estimatedCostMicros = usage.estimatedCostMicros();
        this.currency = usage.currency();
        this.pricingVersion = usage.pricingVersion();
        this.rawOutputRetentionUntil = rawOutputRetentionUntil;
        this.createdAt = createdAt;
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

    public int requestUnits() {
        return requestUnits;
    }

    public long providerLatencyMs() {
        return providerLatencyMs;
    }

    public long totalLatencyMs() {
        return totalLatencyMs;
    }

    public long estimatedCostMicros() {
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
}
