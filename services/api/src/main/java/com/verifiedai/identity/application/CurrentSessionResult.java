package com.verifiedai.identity.application;

import java.util.UUID;

public record CurrentSessionResult(UUID userId, UUID sessionId) {
}
