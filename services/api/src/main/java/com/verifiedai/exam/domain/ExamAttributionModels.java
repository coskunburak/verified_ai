package com.verifiedai.exam.domain;

import com.verifiedai.exam.application.ExamPostAssessmentAttributionService.Attribution;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** CNT-LOS-10/11 wire records. Student data is never training eligible. */
public final class ExamAttributionModels {
    private ExamAttributionModels() { }
    public record CategorySummary(String category, int itemCount, BigDecimal lostNet,
            BigDecimal sharePercentage, String headline) { }
    public record RoiAllocation(int rank, String topicId, String topicTitle, String skillId,
            String skillTitle, String dominantCategory, String matchedMistakeCode,
            BigDecimal sessionLostNet, BigDecimal currentMastery, BigDecimal examSuccessProbability,
            BigDecimal expectedNetGainPerHour, BigDecimal estimatedRemediationHours,
            BigDecimal estimatedNetRecoveryTotal, String recommendedActionType,
            String recommendedTargetRef, String rationale) { }
    public record ItemAttribution(UUID attributionId, UUID itemEvaluationId, int itemOrder,
            String sectionCode, String topicId, String skillId, String itemOutcome,
            String attributionCategory, String matchedMistakeCode, String evidenceSource,
            int timeSpentSeconds, int expectedSolveSeconds, BigDecimal masterySnapshot,
            BigDecimal estimatedNetImpact, String recommendedActionType,
            String recommendedTargetRef, String rationale) { }
    public record Report(UUID sessionId, UUID userId, String examCode, Instant generatedAt,
            String algorithmVersion, BigDecimal totalLostNet, List<CategorySummary> categorySummary,
            List<RoiAllocation> topRoiAllocations, List<ItemAttribution> itemAttributions,
            boolean studyPlanRebalanced, UUID lastReallocationBatchId, boolean trainingEligible) {
        public Report {
            if (trainingEligible) throw new IllegalArgumentException("ADR-006");
            categorySummary=List.copyOf(categorySummary); topRoiAllocations=List.copyOf(topRoiAllocations);
            itemAttributions=List.copyOf(itemAttributions);
        }
    }
    public record RebalanceRequest(int maxTopicsToApply, int targetHorizonDays, int preferredDailyMinutes) {
        public RebalanceRequest {
            if (maxTopicsToApply < 1 || maxTopicsToApply > 3 || targetHorizonDays != 7
                    || preferredDailyMinutes < 5 || preferredDailyMinutes > 240)
                throw new IllegalArgumentException("Expected 1–3 topics, a 7-day horizon and 5–240 daily minutes");
        }
    }
    public record RebalanceDirective(int rank, String topicId, String skillId,
            BigDecimal expectedNetGainPerHour, BigDecimal estimatedRemediationHours,
            String recommendedActionType, String recommendedTargetRef) { }
    public record RebalanceResponse(UUID batchId, UUID sourceSessionId, UUID userId, String examCode,
            boolean appliedToStudyPlan, boolean idempotentReplay, UUID rebalancedStudyPlanId,
            int rebalancedTasksCount, BigDecimal totalExpectedNetRecovery,
            List<RebalanceDirective> appliedDirectives, Instant appliedAt, boolean trainingEligible) {
        public RebalanceResponse {
            if (trainingEligible) throw new IllegalArgumentException("ADR-006");
            appliedDirectives=List.copyOf(appliedDirectives);
        }
    }
    public record StoredAttribution(UUID attributionId, UUID sessionId, UUID itemEvaluationId,
            UUID userId, int itemOrder, String sectionCode, String topicId, String subtopicId,
            Attribution attribution, Instant createdAt) { }
}
