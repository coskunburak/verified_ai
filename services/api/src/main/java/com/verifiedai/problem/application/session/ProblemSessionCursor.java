package com.verifiedai.problem.application.session;

import java.time.Instant;
import java.util.UUID;

record ProblemSessionCursor(
    Instant updatedAt,
    UUID sessionId
) {
}
