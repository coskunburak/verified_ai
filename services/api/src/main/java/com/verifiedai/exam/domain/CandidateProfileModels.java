package com.verifiedai.exam.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CandidateProfileModels {

    public static final String CANDIDATE_PROFILE_REQUEST_SCHEMA_VERSION = "candidate-profile-request-v1";
    public static final String CANDIDATE_PROFILE_SCHEMA_VERSION = "candidate-profile-v1";
    public static final String CANDIDATE_PROFILE_HISTORY_SCHEMA_VERSION = "candidate-profile-history-v1";
    public static final String SYLLABUS_INGESTION_SCHEMA_VERSION = "syllabus-ingestion-v1";
    public static final String SYLLABUS_INGEST_REQUEST_SCHEMA_VERSION = "syllabus-ingest-request-v1";
    public static final String SYLLABUS_CONFIRM_REQUEST_SCHEMA_VERSION = "syllabus-confirm-request-v1";

    private CandidateProfileModels() {
    }

    public enum FeasibilityStatus {
        OPTIMAL,
        STRETCH,
        AT_RISK,
        UNREALISTIC
    }

    public enum SyllabusUploadStatus {
        PARSED_AUTO_CONFIRMED,
        NEEDS_DISAMBIGUATION,
        USER_CONFIRMED,
        REJECTED
    }

    public record AccessibilityAccommodations(
            BigDecimal extraTimeMultiplier,
            boolean highContrastEnabled,
            boolean screenReaderOptimized,
            boolean reducedMotionEnabled
    ) {
        public static AccessibilityAccommodations standardDefaults() {
            return new AccessibilityAccommodations(new BigDecimal("1.00"), false, false, false);
        }
    }

    public record AdjustedExamSection(
            String sectionCode,
            String titleEn,
            String titleTr,
            int questionCount,
            int baseDurationMinutes,
            int adjustedDurationMinutes,
            int order
    ) {
    }

    public record PacingAssessment(
            int daysUntilExam,
            int effectiveDailyCapacityMinutes,
            int requiredDailyMinutes,
            BigDecimal totalEstimatedRemainingMinutes,
            BigDecimal pacingRatio,
            FeasibilityStatus feasibilityStatus
    ) {
    }

    public record CandidateProfileRecord(
            UUID profileId,
            UUID userId,
            UUID activeExamDefinitionId,
            String activeExamCode,
            String pinnedExamVersion,
            UUID activeTargetId,
            ExamModels.Jurisdiction jurisdiction,
            LocalDate targetDate,
            BigDecimal targetScore,
            int dailyStudyMinutes,
            int weeklyStudyMinutes,
            AccessibilityAccommodations accommodations,
            BigDecimal baselineMastery,
            int requiredDailyMinutes,
            FeasibilityStatus feasibilityStatus,
            String lastIdempotencyKey,
            String schemaVersion,
            boolean trainingEligible,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CandidateHorizonHistoryEntry(
            UUID historyId,
            UUID profileId,
            String examCode,
            String pinnedExamVersion,
            LocalDate previousTargetDate,
            LocalDate newTargetDate,
            Integer previousDailyMinutes,
            int newDailyMinutes,
            Integer previousWeeklyMinutes,
            int newWeeklyMinutes,
            BigDecimal previousTargetScore,
            BigDecimal newTargetScore,
            BigDecimal extraTimeMultiplier,
            int requiredDailyMinutes,
            FeasibilityStatus feasibilityStatus,
            String changeReason,
            Instant changedAt
    ) {
    }

    public record CandidateProfileUpsertRequest(
            String schemaVersion,
            String examCode,
            String pinnedExamVersion,
            BigDecimal targetScore,
            LocalDate targetDate,
            Integer dailyStudyMinutes,
            Integer weeklyStudyMinutes,
            AccessibilityAccommodations accommodations,
            BigDecimal baselineMastery,
            String changeReason,
            String idempotencyKey
    ) {
    }

    public record CandidateProfileResponse(
            String schemaVersion,
            UUID profileId,
            UUID userId,
            UUID activeExamDefinitionId,
            String activeExamCode,
            String pinnedExamVersion,
            String blueprintHash,
            UUID activeTargetId,
            ExamModels.Jurisdiction jurisdiction,
            LocalDate targetDate,
            BigDecimal targetScore,
            int dailyStudyMinutes,
            int weeklyStudyMinutes,
            AccessibilityAccommodations accommodations,
            BigDecimal baselineMastery,
            int daysUntilExam,
            int effectiveDailyCapacityMinutes,
            int requiredDailyMinutes,
            FeasibilityStatus feasibilityStatus,
            List<AdjustedExamSection> adjustedSections,
            boolean idempotentReplay,
            int llmTokensUsed,
            boolean trainingEligible,
            Instant createdAt,
            Instant updatedAt
    ) {
        public CandidateProfileResponse {
            adjustedSections = adjustedSections == null ? List.of() : List.copyOf(adjustedSections);
            if (trainingEligible) {
                throw new IllegalArgumentException("Candidate profiles are never training eligible");
            }
        }
    }

    public record CandidateProfileHistoryResponse(
            String schemaVersion,
            UUID userId,
            List<CandidateHorizonHistoryEntry> items,
            int llmTokensUsed,
            boolean trainingEligible
    ) {
        public CandidateProfileHistoryResponse {
            items = items == null ? List.of() : List.copyOf(items);
            if (trainingEligible) {
                throw new IllegalArgumentException("Candidate profile history is never training eligible");
            }
        }
    }

    public record DisambiguationCandidate(
            String skillId,
            String topicId,
            BigDecimal similarityScore
    ) {
    }

    public record ParsedSyllabusTopicItem(
            int itemIndex,
            String rawTopicLabel,
            Integer targetWeek,
            String targetMonth,
            BigDecimal extractionConfidence,
            String matchedSkillId,
            String matchedTopicId,
            BigDecimal similarityScore,
            boolean autoConfirmed,
            boolean userConfirmed,
            List<DisambiguationCandidate> candidates
    ) {
        public ParsedSyllabusTopicItem {
            Objects.requireNonNull(rawTopicLabel, "rawTopicLabel must not be null");
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record SyllabusExtractionCandidate(
            String rawTopicLabel,
            Integer targetWeek,
            String targetMonth,
            BigDecimal extractionConfidence
    ) {
    }

    public record SyllabusVisionResult(
            List<SyllabusExtractionCandidate> items,
            int llmTokensUsed,
            String extractorMode
    ) {
        public SyllabusVisionResult {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record SyllabusUploadRecord(
            UUID uploadId,
            UUID userId,
            UUID examDefinitionId,
            String examCode,
            String examVersion,
            String imageHash,
            String mimeType,
            int byteSize,
            List<ParsedSyllabusTopicItem> parsedTopics,
            SyllabusUploadStatus status,
            boolean deduplicatedFromCache,
            int llmTokensUsed,
            String lastIdempotencyKey,
            String schemaVersion,
            boolean trainingEligible,
            Instant expiresAt,
            Instant confirmedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public SyllabusUploadRecord {
            parsedTopics = parsedTopics == null ? List.of() : List.copyOf(parsedTopics);
        }
    }

    public record SyllabusIngestJsonRequest(
            String schemaVersion,
            String examCode,
            String examVersion,
            String mimeType,
            String base64Payload,
            String filenameHint,
            String idempotencyKey
    ) {
    }

    public record SyllabusMappingConfirmation(
            Integer itemIndex,
            String confirmedSkillId
    ) {
    }

    public record SyllabusConfirmRequest(
            String schemaVersion,
            List<SyllabusMappingConfirmation> mappings
    ) {
    }

    public record SyllabusIngestionResponse(
            String schemaVersion,
            UUID uploadId,
            UUID userId,
            UUID examDefinitionId,
            String examCode,
            String examVersion,
            String imageHash,
            String mimeType,
            int byteSize,
            SyllabusUploadStatus status,
            boolean deduplicatedFromCache,
            boolean idempotentReplay,
            int remainingMonthlyQuota,
            List<ParsedSyllabusTopicItem> parsedTopics,
            int llmTokensUsed,
            boolean trainingEligible,
            Instant expiresAt,
            Instant confirmedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public SyllabusIngestionResponse {
            parsedTopics = parsedTopics == null ? List.of() : List.copyOf(parsedTopics);
            if (trainingEligible) {
                throw new IllegalArgumentException("Syllabus uploads are never training eligible");
            }
        }
    }
}
