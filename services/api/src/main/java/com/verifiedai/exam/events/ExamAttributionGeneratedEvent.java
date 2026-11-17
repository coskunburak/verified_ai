package com.verifiedai.exam.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExamAttributionGeneratedEvent(UUID eventId, UUID userId, UUID sessionId,
        String examCode, BigDecimal totalLostNet, List<HighRoiTopicDirective> topRoiDirectives,
        Instant occurredAt) {
    public ExamAttributionGeneratedEvent { topRoiDirectives = List.copyOf(topRoiDirectives); }
    public record HighRoiTopicDirective(int rank, String topicId, String skillId,
            String dominantCategory, String matchedMistakeCode, BigDecimal expectedNetGainPerHour,
            BigDecimal estimatedRemediationHours, String recommendedActionType, String recommendedTargetRef) { }
}
