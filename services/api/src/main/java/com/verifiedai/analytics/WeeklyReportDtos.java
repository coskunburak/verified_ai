package com.verifiedai.analytics;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contract-aligned DTOs matching {@code weekly-digest-v1.schema.json} and
 * {@code weekly-share-packet-v1.schema.json}.
 */
public final class WeeklyReportDtos {

    private WeeklyReportDtos() {
    }

    public record WeeklyDigestDto(
        String schemaVersion,
        UUID digestId,
        UUID userId,
        String weekIso,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        String locale,
        WeeklyDigestStatus status,
        @JsonProperty("training_eligible") boolean trainingEligible,
        Instant generatedAt,
        ExecutiveSummaryDto executiveSummary,
        List<WeeklyWinItemDto> wins,
        List<WeeklyRecurringMistakeItemDto> recurringMistakes,
        MasteryMomentumDto masteryMomentum,
        List<ExplainableRecommendationItemDto> recommendations
    ) {
    }

    public record ExecutiveSummaryDto(
        String headline,
        String encouragementNote,
        int solvedAttemptsCount,
        int independentTwinsSolvedCount,
        int studyMinutes,
        int activeDaysCount,
        double independentShareRate,
        int completedPlanItemsCount,
        int currentStreakDays
    ) {
    }

    public record WeeklyWinItemDto(
        String winId,
        String category,
        String title,
        String detail,
        String skillId,
        String metricDelta,
        String evidenceRef
    ) {
    }

    public record WeeklyRecurringMistakeItemDto(
        String mistakeCode,
        String label,
        String skillId,
        String skillTitle,
        int occurrenceCountThisWeek,
        int previousWeekOccurrenceCount,
        String trendDirection,
        boolean microLessonCompleted,
        String supportiveGuidance,
        List<String> evidenceRefs
    ) {
    }

    public record DailyActivityBucketDto(
        LocalDate date,
        String dayLabel,
        int studyMinutes,
        int solvedCount,
        int independentCount,
        boolean active
    ) {
    }

    public record SkillMomentumItemDto(
        String skillId,
        String skillTitle,
        double masteryScore,
        double retentionScore,
        double masteryDelta,
        int independentEvidenceCount
    ) {
    }

    public record MasteryMomentumDto(
        double overallMasteryAvg,
        double overallRetentionAvg,
        int skillsImprovedCount,
        int skillsPracticedCount,
        int dueReviewsCount,
        List<DailyActivityBucketDto> dailyActivityBuckets,
        List<SkillMomentumItemDto> topSkillDeltas
    ) {
    }

    public record RecommendationFactorBreakdownDto(
        double masteryGapWeight,
        double mistakeWeight,
        double retentionWeight,
        double prerequisiteWeight,
        double observedMasteryScore,
        int observedMistakeCount,
        double observedRetentionScore,
        int dependentBlockedSkillsCount
    ) {
    }

    public record RecommendationExplanationDto(
        String summaryText,
        RecommendationFactorBreakdownDto factorBreakdown
    ) {
    }

    public record ExplainableRecommendationItemDto(
        String recommendationId,
        String targetSkillId,
        String targetSkillTitle,
        RecommendationReasonCode reasonCode,
        double priorityScore,
        int estimatedMinutes,
        String recommendedActionType,
        String headline,
        RecommendationExplanationDto explanation,
        List<String> evidenceRefs
    ) {
    }

    /**
     * Request payload for generating a strictly allowlisted tutor/parent share packet.
     * Captures any attempted unknown or restricted scope properties so security checks can reject them cleanly.
     */
    public static final class WeeklySharePacketRequest {
        private String recipientRole;
        private String displayAlias;
        private String locale;
        private UUID targetUserId;
        private Boolean includeRawTranscripts;
        private Boolean includeRawMedia;
        private Boolean includeExactTimestamps;
        private final Map<String, Object> extraFields = new LinkedHashMap<>();

        public WeeklySharePacketRequest() {
        }

        public WeeklySharePacketRequest(String recipientRole, String displayAlias, String locale) {
            this.recipientRole = recipientRole;
            this.displayAlias = displayAlias;
            this.locale = locale;
        }

        public String getRecipientRole() {
            return recipientRole;
        }

        public void setRecipientRole(String recipientRole) {
            this.recipientRole = recipientRole;
        }

        public String getDisplayAlias() {
            return displayAlias;
        }

        public void setDisplayAlias(String displayAlias) {
            this.displayAlias = displayAlias;
        }

        public String getLocale() {
            return locale;
        }

        public void setLocale(String locale) {
            this.locale = locale;
        }

        public UUID getTargetUserId() {
            return targetUserId;
        }

        public void setTargetUserId(UUID targetUserId) {
            this.targetUserId = targetUserId;
        }

        public Boolean getIncludeRawTranscripts() {
            return includeRawTranscripts;
        }

        public void setIncludeRawTranscripts(Boolean includeRawTranscripts) {
            this.includeRawTranscripts = includeRawTranscripts;
        }

        public Boolean getIncludeRawMedia() {
            return includeRawMedia;
        }

        public void setIncludeRawMedia(Boolean includeRawMedia) {
            this.includeRawMedia = includeRawMedia;
        }

        public Boolean getIncludeExactTimestamps() {
            return includeExactTimestamps;
        }

        public void setIncludeExactTimestamps(Boolean includeExactTimestamps) {
            this.includeExactTimestamps = includeExactTimestamps;
        }

        @JsonAnySetter
        public void captureExtraField(String key, Object value) {
            if (key != null) {
                extraFields.put(key, value);
            }
        }

        public Map<String, Object> getExtraFields() {
            return extraFields;
        }
    }

    public record WeeklySharePacketDto(
        String schemaVersion,
        UUID packetId,
        UUID digestId,
        String weekIso,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        String locale,
        ShareRecipientRole recipientRole,
        String displayAlias,
        @JsonProperty("training_eligible") boolean trainingEligible,
        Instant generatedAt,
        ShareExecutiveMetricsDto executiveMetrics,
        List<ShareMasterySnapshotItemDto> masterySnapshot,
        List<ShareFocusThemeItemDto> focusThemes,
        List<ShareRecommendedActionItemDto> recommendedNextActions,
        PrivacyAttestationDto privacyAttestation
    ) {
    }

    public record ShareExecutiveMetricsDto(
        int solvedAttemptsCount,
        int independentTwinsSolvedCount,
        int studyMinutes,
        int activeDaysCount,
        double independentShareRate
    ) {
    }

    public record ShareMasterySnapshotItemDto(
        String skillId,
        String skillTitle,
        double masteryScore,
        double masteryDelta
    ) {
    }

    public record ShareFocusThemeItemDto(
        String mistakeCode,
        String label,
        String skillTitle,
        String trendDirection,
        String guidance
    ) {
    }

    public record ShareRecommendedActionItemDto(
        String recommendationId,
        String targetSkillTitle,
        RecommendationReasonCode reasonCode,
        double priorityScore,
        int estimatedMinutes,
        String headline,
        String explanationSummary,
        List<String> evidenceRefs
    ) {
    }

    public record PrivacyAttestationDto(
        String policyId,
        boolean piiExcluded,
        boolean rawTutorTranscriptsIncluded,
        boolean rawStudentPhotosIncluded,
        boolean exactSessionTimestampsIncluded,
        boolean trainingEligible,
        String shareableScopeSummary
    ) {
    }
}
