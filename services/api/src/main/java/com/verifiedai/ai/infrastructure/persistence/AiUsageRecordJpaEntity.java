package com.verifiedai.ai.infrastructure.persistence;

import com.verifiedai.ai.application.AiUsageRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

@Entity
@Table(name = "ai_usage_records")
public class AiUsageRecordJpaEntity {

    @Id
    private UUID id;

    @Column(
        name = "operation_id",
        nullable = false,
        unique = true
    )
    private UUID operationId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "problem_session_id")
    private UUID problemSessionId;

    @Column(nullable = false)
    private String capability;

    @Column(
        name = "route_policy_version",
        nullable = false
    )
    private String routePolicyVersion;

    @Column(
        name = "route_id",
        nullable = false
    )
    private String routeId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(name = "prompt_id")
    private String promptId;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "schema_version")
    private String schemaVersion;

    @Column(name = "provider_request_id")
    private String providerRequestId;

    @Column(name = "provider_response_id")
    private String providerResponseId;

    @Column(nullable = false)
    private String status;

    @Column(name = "failure_class")
    private String failureClass;

    @Column(nullable = false)
    private boolean retryable;

    @Column(
        name = "attempt_count",
        nullable = false
    )
    private int attemptCount;

    @Column(
        name = "fallback_used",
        nullable = false
    )
    private boolean fallbackUsed;

    @Column(name = "fallback_chain")
    private String fallbackChain;

    @Column(name = "input_token_count")
    private Integer inputTokenCount;

    @Column(name = "output_token_count")
    private Integer outputTokenCount;

    @Column(name = "image_unit_count")
    private Integer imageUnitCount;

    @Column(
        name = "request_unit_count",
        nullable = false
    )
    private int requestUnitCount;

    @Column(
        name = "estimated_cost_micros",
        nullable = false
    )
    private long estimatedCostMicros;

    @Column(nullable = false)
    private String currency;

    @Column(
        name = "pricing_version",
        nullable = false
    )
    private String pricingVersion;

    @Column(
        name = "provider_latency_ms",
        nullable = false
    )
    private long providerLatencyMs;

    @Column(
        name = "gateway_latency_ms",
        nullable = false
    )
    private long gatewayLatencyMs;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "trace_id")
    private String traceId;

    @Column(
        name = "created_at",
        nullable = false
    )
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AiUsageRecordJpaEntity() {
    }

    private AiUsageRecordJpaEntity(
        AiUsageRecord record
    ) {
        apply(record);
    }

    public static AiUsageRecordJpaEntity from(
        AiUsageRecord record
    ) {
        return new AiUsageRecordJpaEntity(
            record
        );
    }

    public void apply(
        AiUsageRecord record
    ) {
        if (
            id != null
                && !id.equals(record.id())
        ) {
            throw new IllegalArgumentException(
                "AI usage record id cannot change"
            );
        }

        if (
            operationId != null
                && !operationId.equals(
                record.operationId()
            )
        ) {
            throw new IllegalArgumentException(
                "AI operation id cannot change"
            );
        }

        this.id = record.id();
        this.operationId =
            record.operationId();
        this.userId =
            record.userId();
        this.problemSessionId =
            record.problemSessionId();
        this.capability =
            record.capability().name();
        this.routePolicyVersion =
            record.routePolicyVersion();
        this.routeId =
            record.routeId();
        this.provider =
            record.provider();
        this.model =
            record.model();
        this.promptId =
            record.promptId();
        this.promptVersion =
            record.promptVersion();
        this.schemaVersion =
            record.schemaVersion();
        this.providerRequestId =
            record.providerRequestId();
        this.providerResponseId =
            record.providerResponseId();
        this.status =
            record.status().name();
        this.failureClass =
            record.failureClass() == null
                ? null
                : record.failureClass().name();
        this.retryable =
            record.retryable();
        this.attemptCount =
            record.attemptCount();
        this.fallbackUsed =
            record.fallbackUsed();
        this.fallbackChain =
            encodeFallbackChain(record);
        this.inputTokenCount =
            record.inputTokens();
        this.outputTokenCount =
            record.outputTokens();
        this.imageUnitCount =
            record.imageUnits();
        this.requestUnitCount =
            record.requestUnits();
        this.estimatedCostMicros =
            record.estimatedCostMicros();
        this.currency =
            record.currency();
        this.pricingVersion =
            record.pricingVersion();
        this.providerLatencyMs =
            record.providerLatencyMs();
        this.gatewayLatencyMs =
            record.gatewayLatencyMs();
        this.correlationId =
            record.correlationId();
        this.traceId =
            record.traceId();
        this.createdAt =
            record.createdAt();
        this.completedAt =
            record.completedAt();
    }

    private String encodeFallbackChain(
        AiUsageRecord record
    ) {
        if (record.fallbackChain().isEmpty()) {
            return null;
        }

        StringJoiner joiner =
            new StringJoiner(">");

        record.fallbackChain()
            .forEach(joiner::add);

        return joiner.toString();
    }

    public UUID id() {
        return id;
    }

    public UUID operationId() {
        return operationId;
    }

    public UUID userId() {
        return userId;
    }

    public UUID problemSessionId() {
        return problemSessionId;
    }

    public String status() {
        return status;
    }

    public long estimatedCostMicros() {
        return estimatedCostMicros;
    }
}
