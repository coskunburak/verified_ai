package com.verifiedai.identity.api;

import com.verifiedai.identity.application.CurrentSessionResult;
import java.util.UUID;

public record CurrentSessionResponse(UUID userId, UUID sessionId) {
    static CurrentSessionResponse from(CurrentSessionResult result) {
        return new CurrentSessionResponse(result.userId(), result.sessionId());
    }
}
