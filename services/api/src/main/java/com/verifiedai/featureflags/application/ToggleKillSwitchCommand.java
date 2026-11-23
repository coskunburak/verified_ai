package com.verifiedai.featureflags.application;

public record ToggleKillSwitchCommand(
    boolean active,
    String reason,
    long expectedVersion,
    String idempotencyKey
) {
}
