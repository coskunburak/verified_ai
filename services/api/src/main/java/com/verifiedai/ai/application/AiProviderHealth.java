package com.verifiedai.ai.application;

import java.time.Duration;
import java.time.Instant;

/** Server telemetry port. Request strings and feature flags never populate this port. */
public interface AiProviderHealth {
    Snapshot snapshot(AiRouteTarget target);

    record Snapshot(State state, Instant observedAt, String source) {
        public boolean usable(Instant now, Duration maxAge) {
            return state == State.CLOSED && observedAt != null && source != null && !source.isBlank()
                && !observedAt.isAfter(now) && observedAt.plus(maxAge).isAfter(now);
        }
    }

    enum State { CLOSED, OPEN, HALF_OPEN, UNKNOWN }

    static AiProviderHealth unknown() {
        return target -> new Snapshot(State.UNKNOWN, null, null);
    }
}
