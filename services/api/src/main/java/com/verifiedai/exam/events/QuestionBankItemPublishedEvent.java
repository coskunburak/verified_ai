package com.verifiedai.exam.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record QuestionBankItemPublishedEvent(
        UUID questionItemId,
        UUID examDefinitionId,
        int examVersionNumber,
        String questionCode,
        String topicCode,
        String difficultyBand,
        String cognitiveLevel,
        String provenanceHash,
        Instant publishedAt
) {
    public QuestionBankItemPublishedEvent {
        Objects.requireNonNull(questionItemId, "questionItemId must not be null");
        Objects.requireNonNull(examDefinitionId, "examDefinitionId must not be null");
        Objects.requireNonNull(questionCode, "questionCode must not be null");
        Objects.requireNonNull(topicCode, "topicCode must not be null");
        Objects.requireNonNull(difficultyBand, "difficultyBand must not be null");
        Objects.requireNonNull(cognitiveLevel, "cognitiveLevel must not be null");
        Objects.requireNonNull(provenanceHash, "provenanceHash must not be null");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
    }
}
