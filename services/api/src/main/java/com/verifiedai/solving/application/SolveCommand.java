package com.verifiedai.solving.application;

import java.util.UUID;

/** No client model, prompt, expression, tier or trusted route fields. */
public record SolveCommand(UUID userId, UUID problemSessionId, String idempotencyKey, UUID retryOf) {
    public SolveCommand {
        if (userId==null || problemSessionId==null || idempotencyKey==null
            || !idempotencyKey.matches("[A-Za-z0-9_-]{8,128}"))
            throw new IllegalArgumentException("Invalid solve command");
    }
}
