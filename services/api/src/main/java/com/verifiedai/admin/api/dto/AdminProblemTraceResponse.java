package com.verifiedai.admin.api.dto;

import com.verifiedai.admin.application.AdminProblemTraceView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminProblemTraceResponse(
    String schemaVersion,
    UUID problemSessionId,
    UUID userId,
    String sessionStatus,
    String inputMode,
    UUID currentParseId,
    UUID problemId,
    UUID solveJobId,
    Instant sessionCreatedAt,
    Instant sessionUpdatedAt,
    Instant sessionCompletedAt,
    boolean rawContentRedacted,
    List<RedactedAssetDto> assets,
    List<RedactedParseDto> parseRevisions,
    List<AiUsageProvenanceDto> aiUsageRecords,
    List<VerificationRunSummaryDto> verificationRuns,
    List<MistakeDisputeSummaryDto> mistakeDisputes,
    UUID auditId,
    Instant generatedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public static AdminProblemTraceResponse from(AdminProblemTraceView view) {
        return new AdminProblemTraceResponse(
            view.schemaVersion(),
            view.problemSessionId(),
            view.userId(),
            view.sessionStatus(),
            view.inputMode(),
            view.currentParseId(),
            view.problemId(),
            view.solveJobId(),
            view.sessionCreatedAt(),
            view.sessionUpdatedAt(),
            view.sessionCompletedAt(),
            view.rawContentRedacted(),
            view.assets().stream()
                .map(a -> new RedactedAssetDto(
                    a.assetId(),
                    a.sourceType(),
                    a.assetKind(),
                    a.status(),
                    a.contentType(),
                    a.sizeBytes(),
                    a.imageWidth(),
                    a.imageHeight(),
                    a.pageCount(),
                    a.retentionClass(),
                    a.availableAt(),
                    a.deletedAt(),
                    a.createdAt()
                ))
                .toList(),
            view.parseRevisions().stream()
                .map(p -> new RedactedParseDto(
                    p.parseId(),
                    p.parseJobId(),
                    p.revision(),
                    p.source(),
                    p.supportStatus(),
                    p.unsupportedReason(),
                    p.reviewRequired(),
                    p.schemaVersion(),
                    p.provider(),
                    p.model(),
                    p.routePolicyVersion(),
                    p.promptId(),
                    p.promptVersion(),
                    p.fallbackUsed(),
                    p.inputTokens(),
                    p.outputTokens(),
                    p.providerLatencyMs(),
                    p.totalLatencyMs(),
                    p.estimatedCostMicros(),
                    p.createdAt()
                ))
                .toList(),
            view.aiUsageRecords().stream()
                .map(u -> new AiUsageProvenanceDto(
                    u.usageRecordId(),
                    u.operationId(),
                    u.capability(),
                    u.routePolicyVersion(),
                    u.routeId(),
                    u.provider(),
                    u.model(),
                    u.promptId(),
                    u.promptVersion(),
                    u.schemaVersion(),
                    u.status(),
                    u.failureClass(),
                    u.retryable(),
                    u.attemptCount(),
                    u.fallbackUsed(),
                    u.fallbackChain(),
                    u.escalationReason(),
                    u.cacheStatus(),
                    u.inputTokenCount(),
                    u.outputTokenCount(),
                    u.estimatedCostMicros(),
                    u.providerLatencyMs(),
                    u.gatewayLatencyMs(),
                    u.correlationId(),
                    u.traceId(),
                    u.createdAt(),
                    u.completedAt()
                ))
                .toList(),
            view.verificationRuns().stream()
                .map(v -> new VerificationRunSummaryDto(
                    v.runId(),
                    v.solutionId(),
                    v.canonicalProblemId(),
                    v.problemParseId(),
                    v.problemParseRevision(),
                    v.canonicalRevision(),
                    v.policyVersion(),
                    v.capabilityVersion(),
                    v.correlationId(),
                    v.status(),
                    v.createdAt()
                ))
                .toList(),
            view.mistakeDisputes().stream()
                .map(d -> new MistakeDisputeSummaryDto(
                    d.disputeId(),
                    d.attemptId(),
                    d.classificationId(),
                    d.replacementAttemptId(),
                    d.stepIndex(),
                    d.disputeType(),
                    d.policyVersion(),
                    d.createdAt()
                ))
                .toList(),
            view.auditId(),
            view.generatedAt(),
            view.llmTokensUsed(),
            view.trainingEligible()
        );
    }

    public record RedactedAssetDto(
        UUID assetId,
        String sourceType,
        String assetKind,
        String status,
        String contentType,
        Long sizeBytes,
        Integer imageWidth,
        Integer imageHeight,
        Integer pageCount,
        String retentionClass,
        Instant availableAt,
        Instant deletedAt,
        Instant createdAt
    ) {
    }

    public record RedactedParseDto(
        UUID parseId,
        UUID parseJobId,
        int revision,
        String source,
        String supportStatus,
        String unsupportedReason,
        boolean reviewRequired,
        String schemaVersion,
        String provider,
        String model,
        String routePolicyVersion,
        String promptId,
        String promptVersion,
        boolean fallbackUsed,
        Integer inputTokens,
        Integer outputTokens,
        Integer providerLatencyMs,
        Integer totalLatencyMs,
        Long estimatedCostMicros,
        Instant createdAt
    ) {
    }

    public record AiUsageProvenanceDto(
        UUID usageRecordId,
        String operationId,
        String capability,
        String routePolicyVersion,
        String routeId,
        String provider,
        String model,
        String promptId,
        String promptVersion,
        String schemaVersion,
        String status,
        String failureClass,
        Boolean retryable,
        Integer attemptCount,
        Boolean fallbackUsed,
        String fallbackChain,
        String escalationReason,
        String cacheStatus,
        Integer inputTokenCount,
        Integer outputTokenCount,
        Long estimatedCostMicros,
        Integer providerLatencyMs,
        Integer gatewayLatencyMs,
        String correlationId,
        String traceId,
        Instant createdAt,
        Instant completedAt
    ) {
    }

    public record VerificationRunSummaryDto(
        UUID runId,
        UUID solutionId,
        UUID canonicalProblemId,
        UUID problemParseId,
        int problemParseRevision,
        int canonicalRevision,
        String policyVersion,
        String capabilityVersion,
        String correlationId,
        String status,
        Instant createdAt
    ) {
    }

    public record MistakeDisputeSummaryDto(
        UUID disputeId,
        UUID attemptId,
        UUID classificationId,
        UUID replacementAttemptId,
        Integer stepIndex,
        String disputeType,
        String policyVersion,
        Instant createdAt
    ) {
    }
}
