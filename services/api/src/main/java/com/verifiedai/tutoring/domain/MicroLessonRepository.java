package com.verifiedai.tutoring.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MicroLessonRepository {

    Optional<MicroLessonCard> findVerifiedCard(String mistakeCode, String locale);

    boolean hasActiveMistakeDiagnosis(UUID userId, String mistakeCode);

    AttemptDiagnosisOwnership checkAttemptDiagnosisOwnership(UUID userId, UUID sourceAttemptId, String mistakeCode);

    boolean hasEligibleContextualTrigger(
            UUID userId,
            String mistakeCode,
            MicroLessonTriggerSource triggerSource,
            UUID sourceAttemptId
    );

    Optional<MicroLessonCompletionRecord> findCompletionByIdempotencyKey(UUID userId, String idempotencyKey);

    void saveCompletion(MicroLessonCompletionRecord record);

    List<MicroLessonCompletionRecord> findCompletionsByUserId(UUID userId);

    int deleteCompletionsByUserId(UUID userId);
}
