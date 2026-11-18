package com.verifiedai.exam.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Canonical longitudinal exam progress analytics report (INV-EXAM-005, schema exam-analytics-progress-v1).
 */
public record ExamProgressReport(
        String schemaVersion,
        UUID snapshotId,
        UUID userId,
        String examCode,
        int mockCount,
        EvidenceStatus evidenceStatus,
        BigDecimal deltaNet,
        List<ScoreTrajectoryPointDto> scoreTrajectory,
        List<PacingAnalysisDto> pacingByTopic,
        TrapRemediationDto trapRemediation,
        int conqueredTrapsCount,
        LocalDate asOfDate,
        String algorithmVersion,
        boolean trainingEligible,
        int llmTokensUsed,
        BigDecimal inferenceCostUsd,
        Instant generatedAt,
        Instant expiresAt
) {
    public static final String SCHEMA_VERSION = "exam-analytics-progress-v1";
    public static final String ALGORITHM_VERSION = "INV_EXAM_005_V1";
    public static final Set<String> SUPPORTED_EXAM_CODES = Set.of("TR_YKS_TYT_MATH", "TR_YKS_AYT_MATH");

    public enum EvidenceStatus {
        EMPTY,
        INSUFFICIENT_FOR_TREND,
        PARTIAL_TREND,
        SUFFICIENT_EVIDENCE
    }

    public enum TrapSourceKind {
        SESSION_MATCHED_TRAP_INCIDENCE,
        HISTORICAL_ACTIVE_TRAP_SNAPSHOTS
    }

    public enum TrapDataStatus {
        COMPUTED,
        INSUFFICIENT_DATA,
        ZERO_VARIANCE,
        NO_HISTORICAL_ACTIVE_TRAP_DATA
    }

    public enum ConqueredTrapsEvidenceStatus {
        VERIFIED_TRANSITIONS,
        UNAVAILABLE
    }

    public record ScoreTrajectoryPointDto(
            UUID sessionId,
            Instant submittedAt,
            BigDecimal netScore,
            BigDecimal movingAverage3,
            int scoreVersion
    ) {
        public ScoreTrajectoryPointDto {
            Objects.requireNonNull(sessionId, "sessionId cannot be null");
            Objects.requireNonNull(submittedAt, "submittedAt cannot be null");
            Objects.requireNonNull(netScore, "netScore cannot be null");
            Objects.requireNonNull(movingAverage3, "movingAverage3 cannot be null");
            if (scoreVersion < 1) {
                throw new IllegalArgumentException("scoreVersion must be >= 1");
            }
        }
    }

    public record TrapObservationPointDto(
            UUID sessionId,
            Instant submittedAt,
            BigDecimal netScore,
            int matchedTrapIncidenceCount
    ) {
        public TrapObservationPointDto {
            Objects.requireNonNull(sessionId, "sessionId cannot be null");
            Objects.requireNonNull(submittedAt, "submittedAt cannot be null");
            Objects.requireNonNull(netScore, "netScore cannot be null");
            if (matchedTrapIncidenceCount < 0) {
                throw new IllegalArgumentException("matchedTrapIncidenceCount cannot be negative");
            }
        }
    }

    public record TrapRemediationDto(
            TrapSourceKind sourceKind,
            TrapDataStatus dataStatus,
            List<TrapObservationPointDto> observations,
            Integer activeTrapCountNow,
            BigDecimal correlationCoefficient,
            int correlationSampleSize,
            ConqueredTrapsEvidenceStatus conqueredTrapsEvidenceStatus,
            boolean associationIsCausal
    ) {
        public TrapRemediationDto {
            Objects.requireNonNull(sourceKind, "sourceKind cannot be null");
            Objects.requireNonNull(dataStatus, "dataStatus cannot be null");
            observations = List.copyOf(Objects.requireNonNull(observations, "observations cannot be null"));
            Objects.requireNonNull(conqueredTrapsEvidenceStatus, "conqueredTrapsEvidenceStatus cannot be null");
            if (associationIsCausal) {
                throw new IllegalArgumentException("associationIsCausal must always be false");
            }
            if (correlationSampleSize < 0 || correlationSampleSize > 20 || observations.size() > 20) {
                throw new IllegalArgumentException("Invalid trap correlation sample bounds");
            }
            if (activeTrapCountNow != null && activeTrapCountNow < 0) {
                throw new IllegalArgumentException("activeTrapCountNow cannot be negative");
            }
            if (dataStatus == TrapDataStatus.COMPUTED) {
                if (correlationSampleSize < 3 || correlationCoefficient == null) {
                    throw new IllegalArgumentException("COMPUTED trap correlation requires sampleSize >= 3 and non-null r");
                }
                if (correlationCoefficient.compareTo(BigDecimal.valueOf(-1)) < 0
                        || correlationCoefficient.compareTo(BigDecimal.ONE) > 0) {
                    throw new IllegalArgumentException("correlationCoefficient must be within [-1, 1]");
                }
            } else if (correlationCoefficient != null) {
                throw new IllegalArgumentException("Non-computed trap correlation must have null correlationCoefficient");
            }
        }
    }

    public ExamProgressReport {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported schemaVersion: " + schemaVersion);
        }
        Objects.requireNonNull(snapshotId, "snapshotId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        if (examCode == null || !SUPPORTED_EXAM_CODES.contains(examCode)) {
            throw new IllegalArgumentException("Unsupported examCode: " + examCode);
        }
        if (mockCount < 0 || mockCount > 20) {
            throw new IllegalArgumentException("mockCount must be between 0 and 20");
        }
        Objects.requireNonNull(evidenceStatus, "evidenceStatus cannot be null");
        scoreTrajectory = List.copyOf(Objects.requireNonNull(scoreTrajectory, "scoreTrajectory cannot be null"));
        pacingByTopic = List.copyOf(Objects.requireNonNull(pacingByTopic, "pacingByTopic cannot be null"));
        Objects.requireNonNull(trapRemediation, "trapRemediation cannot be null");
        Objects.requireNonNull(asOfDate, "asOfDate cannot be null");
        if (!ALGORITHM_VERSION.equals(algorithmVersion)) {
            throw new IllegalArgumentException("Unsupported algorithmVersion: " + algorithmVersion);
        }
        Objects.requireNonNull(generatedAt, "generatedAt cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        if (!expiresAt.isAfter(generatedAt)) {
            throw new IllegalArgumentException("expiresAt must be after generatedAt");
        }
        if (trainingEligible || llmTokensUsed != 0 || inferenceCostUsd == null || inferenceCostUsd.signum() != 0) {
            throw new IllegalArgumentException("Privacy or zero-cost invariant violated");
        }
        if (conqueredTrapsCount < 0) {
            throw new IllegalArgumentException("conqueredTrapsCount cannot be negative");
        }
        if (trapRemediation.conqueredTrapsEvidenceStatus() == ConqueredTrapsEvidenceStatus.UNAVAILABLE
                && conqueredTrapsCount != 0) {
            throw new IllegalArgumentException("conqueredTrapsCount must be 0 when conqueredTrapsEvidenceStatus is UNAVAILABLE");
        }
        if (scoreTrajectory.size() != mockCount) {
            throw new IllegalArgumentException("scoreTrajectory size must match mockCount");
        }
        if (mockCount == 0 && (evidenceStatus != EvidenceStatus.EMPTY || deltaNet != null || !pacingByTopic.isEmpty())) {
            throw new IllegalArgumentException("Invalid 0-mock report state");
        }
        if (mockCount == 1 && (evidenceStatus != EvidenceStatus.INSUFFICIENT_FOR_TREND || deltaNet != null)) {
            throw new IllegalArgumentException("Invalid 1-mock report state");
        }
        if (mockCount == 2 && (evidenceStatus != EvidenceStatus.PARTIAL_TREND || deltaNet == null)) {
            throw new IllegalArgumentException("Invalid 2-mock report state");
        }
        if (mockCount >= 3 && (evidenceStatus != EvidenceStatus.SUFFICIENT_EVIDENCE || deltaNet == null)) {
            throw new IllegalArgumentException("Invalid >=3-mock report state");
        }
    }
}
