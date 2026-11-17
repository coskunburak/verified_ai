package com.verifiedai.exam.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Synchronous delivery inside the requesting transaction; failures roll back both modules. */
public record ExamStudyRebalanceRequestedEvent(UUID batchId, UUID userId, UUID sourceSessionId,
        String examCode, String idempotencyKey,
        List<ExamAttributionGeneratedEvent.HighRoiTopicDirective> directives, Instant requestedAt,
        int targetHorizonDays, int preferredDailyMinutes) {
    public ExamStudyRebalanceRequestedEvent { directives = List.copyOf(directives); }
    public ExamStudyRebalanceRequestedEvent(UUID batchId, UUID userId, UUID sourceSessionId,
            String examCode, String idempotencyKey,
            List<ExamAttributionGeneratedEvent.HighRoiTopicDirective> directives, Instant requestedAt) {
        this(batchId,userId,sourceSessionId,examCode,idempotencyKey,directives,requestedAt,7,45);
    }
}
