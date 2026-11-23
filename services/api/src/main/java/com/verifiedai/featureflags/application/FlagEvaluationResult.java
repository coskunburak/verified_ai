package com.verifiedai.featureflags.application;

import java.time.Instant;

public record FlagEvaluationResult(
    String flagKey,
    String category,
    String valueType,
    boolean enabled,
    String valueJson,
    boolean killSwitchActive,
    String reason,
    Instant evaluatedAt
) {
}
