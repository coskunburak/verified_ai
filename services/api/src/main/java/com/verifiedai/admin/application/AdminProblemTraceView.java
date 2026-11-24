package com.verifiedai.admin.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminProblemTraceView(
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
    List<RedactedAssetView> assets,
    List<RedactedParseView> parseRevisions,
    List<AiUsageProvenanceView> aiUsageRecords,
    List<VerificationRunSummaryView> verificationRuns,
    List<MistakeDisputeSummaryView> mistakeDisputes,
    UUID auditId,
    Instant generatedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public AdminProblemTraceView {
        assets = assets == null ? List.of() : List.copyOf(assets);
        parseRevisions = parseRevisions == null ? List.of() : List.copyOf(parseRevisions);
        aiUsageRecords = aiUsageRecords == null ? List.of() : List.copyOf(aiUsageRecords);
        verificationRuns = verificationRuns == null ? List.of() : List.copyOf(verificationRuns);
        mistakeDisputes = mistakeDisputes == null ? List.of() : List.copyOf(mistakeDisputes);
    }

    public record RedactedAssetView(
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

    public record RedactedParseView(
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

    public record AiUsageProvenanceView(
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

    public record VerificationRunSummaryView(
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

    public record MistakeDisputeSummaryView(
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
