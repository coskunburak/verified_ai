package com.verifiedai.attempt.domain;

import java.util.UUID;

public record AttemptMistakeInput(String schemaVersion, UUID evaluationId, AttemptSourceIdentity source, int workRevision) {
    public AttemptMistakeInput {
        if(!"attempt-mistake-request-v1".equals(schemaVersion) || evaluationId == null || source == null || workRevision < 1 || workRevision > 30)
            throw new IllegalArgumentException("Invalid classification request");
    }
}
