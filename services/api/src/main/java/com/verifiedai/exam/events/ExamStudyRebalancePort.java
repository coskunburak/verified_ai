package com.verifiedai.exam.events;

import java.util.Map;
import java.util.UUID;

/** Public synchronous boundary. The caller owns the transaction and idempotency lock. */
public interface ExamStudyRebalancePort {
    Result apply(ExamStudyRebalanceRequestedEvent request);
    /** planId anchors the seven-day schedule at its first daily coach session. */
    record Result(UUID planId, int tasksCount, Map<String,Integer> scheduledMinutes) {
        public Result { scheduledMinutes=Map.copyOf(scheduledMinutes); }
    }
}
