package com.verifiedai.mastery;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import static com.verifiedai.mastery.EvidenceStratifiedMasteryPolicy.*;

final class HalfLifeRetentionPolicy {
    private HalfLifeRetentionPolicy() { }

    static double elapsedDays(Instant from, Instant at) {
        Objects.requireNonNull(at);
        if (from == null || !at.isAfter(from)) return 0;
        Duration elapsed = Duration.between(from, at);
        return (elapsed.getSeconds() + elapsed.getNano() / 1_000_000_000.0) / 86400.0;
    }

    static double retention(State state, Instant at) {
        double half = validHalfLife(state.halfLife());
        if (!(state.score() >= FLOOR && state.score() <= CEILING)) throw new IllegalArgumentException("Invalid mastery");
        return FLOOR + (state.score() - FLOOR) * StrictMath.pow(2, -elapsedDays(state.observedAt(), at) / half);
    }

    static double next(State state, MasteryEvidenceSource.Evidence event) {
        double half = validHalfLife(state.halfLife());
        // V038 treats a revealed solution as failed retrieval for BKT only.
        if (!event.correct() || event.type() == LearningEvidenceType.SOLUTION_REVEALED_ASSISTED) return StrictMath.max(1, .55 * half);
        if (event.type() != LearningEvidenceType.INDEPENDENT) return half;
        double spacing = elapsedDays(state.observedAt(), event.observedAt());
        double factor = spacing < .5 * half ? 1.20 : 1.85 + .35 * StrictMath.min(1, spacing / half);
        return StrictMath.min(90, half * factor);
    }

    private static double validHalfLife(double half) {
        if (!(half >= 1 && half <= 90)) throw new IllegalArgumentException("Invalid half-life");
        return half;
    }
}
