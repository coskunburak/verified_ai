package com.verifiedai.studyplan.spaced;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class SpacedRepetitionScheduler {

    public static final BigDecimal M_FLOOR = new BigDecimal("0.0500");
    public static final BigDecimal R_THRESHOLD = new BigDecimal("0.7200");
    public static final BigDecimal MIN_HALF_LIFE_DAYS = new BigDecimal("0.50");
    public static final BigDecimal MAX_HALF_LIFE_DAYS = new BigDecimal("90.00");
    public static final long TRAP_IMMUNITY_HOURS = 168L;
    public static final Duration TRAP_IMMUNITY_DURATION = Duration.ofHours(TRAP_IMMUNITY_HOURS);
    public static final int DAILY_QUEUE_CAP = 3;

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final double SECONDS_PER_DAY = 86400.0;
    private static final double LN_2 = Math.log(2.0);

    /**
     * INV-SR-001: Computes retention decay R_{u,s}(t) = 0.05 + (M_0 - 0.05) * 2^(-delta_t / H).
     * Clamped to [0.0500, 1.0000] and rounded to 4 decimal places with HALF_EVEN.
     */
    public BigDecimal computeCurrentRetention(
            BigDecimal initialMastery,
            BigDecimal halfLifeDays,
            Instant lastEvaluatedAt,
            Instant now
    ) {
        Objects.requireNonNull(initialMastery, "initialMastery cannot be null");
        Objects.requireNonNull(halfLifeDays, "halfLifeDays cannot be null");
        Objects.requireNonNull(lastEvaluatedAt, "lastEvaluatedAt cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        double m0 = initialMastery.doubleValue();
        double h = Math.max(MIN_HALF_LIFE_DAYS.doubleValue(), Math.min(MAX_HALF_LIFE_DAYS.doubleValue(), halfLifeDays.doubleValue()));

        double deltaSeconds = Math.max(0.0, (double) Duration.between(lastEvaluatedAt, now).toSeconds());
        double deltaDays = deltaSeconds / SECONDS_PER_DAY;

        double decayFactor = Math.pow(2.0, -deltaDays / h);
        double retention = M_FLOOR.doubleValue() + (m0 - M_FLOOR.doubleValue()) * decayFactor;

        double clamped = Math.max(0.0500, Math.min(1.0000, retention));
        return BigDecimal.valueOf(clamped).setScale(4, RoundingMode.HALF_EVEN);
    }

    /**
     * INV-SR-001: Computes exact timestamp inversion t_due = t_0 + H * log2((M_0 - 0.05) / (0.72 - 0.05)).
     * If initialMastery <= 0.72, delta_t_due = 0 (immediately due at lastEvaluatedAt).
     */
    public Instant computeDueTimestamp(
            BigDecimal initialMastery,
            BigDecimal halfLifeDays,
            Instant lastEvaluatedAt
    ) {
        Objects.requireNonNull(initialMastery, "initialMastery cannot be null");
        Objects.requireNonNull(halfLifeDays, "halfLifeDays cannot be null");
        Objects.requireNonNull(lastEvaluatedAt, "lastEvaluatedAt cannot be null");

        double m0 = initialMastery.doubleValue();
        if (m0 <= R_THRESHOLD.doubleValue()) {
            return lastEvaluatedAt;
        }

        double h = Math.max(MIN_HALF_LIFE_DAYS.doubleValue(), Math.min(MAX_HALF_LIFE_DAYS.doubleValue(), halfLifeDays.doubleValue()));
        double numerator = m0 - M_FLOOR.doubleValue();
        double denominator = R_THRESHOLD.doubleValue() - M_FLOOR.doubleValue();

        if (numerator <= denominator) {
            return lastEvaluatedAt;
        }

        double ratio = numerator / denominator;
        double deltaDays = h * (Math.log(ratio) / LN_2);
        long deltaSeconds = Math.round(deltaDays * SECONDS_PER_DAY);

        return lastEvaluatedAt.plusSeconds(deltaSeconds);
    }

    /**
     * INV-SR-002: Computes 7-Day Trap Immunity due timestamp t_due = remediatedAt + 168 hours.
     */
    public Instant computeTrapImmunityDueTimestamp(Instant remediatedAt) {
        Objects.requireNonNull(remediatedAt, "remediatedAt cannot be null");
        return remediatedAt.plus(TRAP_IMMUNITY_DURATION);
    }

    /**
     * INV-SR-001 / INV-SR-002: Computes updated half-life.
     * On successful independent review:
     *   H_new = min(90.00, H * (1.85 + 0.35 * min(1.0, delta_t / H)))
     * On unsuccessful review / trap relapse:
     *   H_new = max(0.50, H * 0.55)
     */
    public BigDecimal computeUpdatedHalfLife(
            BigDecimal currentHalfLifeDays,
            double elapsedDays,
            boolean passedIndependently
    ) {
        Objects.requireNonNull(currentHalfLifeDays, "currentHalfLifeDays cannot be null");

        double h = Math.max(MIN_HALF_LIFE_DAYS.doubleValue(), Math.min(MAX_HALF_LIFE_DAYS.doubleValue(), currentHalfLifeDays.doubleValue()));
        double updatedH;

        if (passedIndependently) {
            double deltaT = Math.max(0.0, elapsedDays);
            double ratio = Math.min(1.0, deltaT / h);
            double multiplier = 1.85 + 0.35 * ratio;
            updatedH = Math.min(MAX_HALF_LIFE_DAYS.doubleValue(), h * multiplier);
        } else {
            updatedH = Math.max(MIN_HALF_LIFE_DAYS.doubleValue(), h * 0.55);
        }

        return BigDecimal.valueOf(updatedH).setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * INV-SR-003 & INV-SR-004: Selects and interleaves daily queue from candidate pool.
     */
    public List<SpacedReviewCandidate> selectAndInterleaveDailyQueue(
            List<SpacedReviewCandidate> duePool,
            int maxItems,
            Instant now
    ) {
        Objects.requireNonNull(duePool, "duePool cannot be null");
        if (duePool.isEmpty() || maxItems <= 0) {
            return Collections.emptyList();
        }

        // 1. Filter: dueAt <= now
        List<SpacedReviewCandidate> eligible = duePool.stream()
                .filter(c -> c.dueAt() != null && !c.dueAt().isAfter(now))
                .toList();

        if (eligible.isEmpty()) {
            return Collections.emptyList();
        }

        return selectAndInterleaveRankedCandidates(eligible, maxItems);
    }

    public List<SpacedReviewCandidate> selectAndInterleaveDailyQueue(
            List<SpacedReviewCandidate> duePool,
            int maxItems
    ) {
        return selectAndInterleaveDailyQueue(duePool, maxItems, Instant.MAX);
    }

    private List<SpacedReviewCandidate> selectAndInterleaveRankedCandidates(
            List<SpacedReviewCandidate> eligible,
            int maxItems
    ) {
        int cap = Math.min(DAILY_QUEUE_CAP, Math.max(1, maxItems));

        // 2. Prioritize: TRAP_IMMUNITY_7D first, then RETENTION_DECAY
        List<SpacedReviewCandidate> immunityPool = eligible.stream()
                .filter(c -> c.triggerType() == SpacedTriggerType.TRAP_IMMUNITY_7D)
                .sorted(Comparator.comparing(SpacedReviewCandidate::dueAt)
                        .thenComparing(SpacedReviewCandidate::canonicalSkillCode))
                .toList();

        List<SpacedReviewCandidate> decayPool = eligible.stream()
                .filter(c -> c.triggerType() == SpacedTriggerType.RETENTION_DECAY)
                .sorted(Comparator.comparing(SpacedReviewCandidate::estimatedRetention)
                        .thenComparing(SpacedReviewCandidate::dueAt)
                        .thenComparing(SpacedReviewCandidate::canonicalSkillCode))
                .toList();

        List<SpacedReviewCandidate> prioritizedPool = new ArrayList<>(immunityPool.size() + decayPool.size());
        prioritizedPool.addAll(immunityPool);
        prioritizedPool.addAll(decayPool);

        if (prioritizedPool.size() <= 1 || cap == 1) {
            return prioritizedPool.stream().limit(cap).toList();
        }

        Set<String> distinctSkillsInPool = prioritizedPool.stream()
                .map(SpacedReviewCandidate::canonicalSkillCode)
                .collect(Collectors.toSet());

        // 3. Selection with interleaving guard (INV-SR-003, INV-SR-004)
        List<SpacedReviewCandidate> selected = new ArrayList<>();
        Set<UUID> chosenIds = new HashSet<>();

        // Pick 1st item
        SpacedReviewCandidate first = prioritizedPool.get(0);
        selected.add(first);
        chosenIds.add(first.itemId());

        // Pick 2nd item: prefer distinct skill from first if pool has >= 2 skills
        SpacedReviewCandidate second = null;
        if (distinctSkillsInPool.size() >= 2) {
            for (SpacedReviewCandidate candidate : prioritizedPool) {
                if (!chosenIds.contains(candidate.itemId()) && !candidate.canonicalSkillCode().equals(first.canonicalSkillCode())) {
                    second = candidate;
                    break;
                }
            }
        }
        if (second == null) {
            for (SpacedReviewCandidate candidate : prioritizedPool) {
                if (!chosenIds.contains(candidate.itemId())) {
                    second = candidate;
                    break;
                }
            }
        }

        if (second != null) {
            selected.add(second);
            chosenIds.add(second.itemId());
        }

        // Pick 3rd item if cap >= 3
        if (cap >= 3 && selected.size() == 2) {
            SpacedReviewCandidate third = null;
            SpacedReviewCandidate currentSecond = selected.get(1);

            // Prefer distinct from second
            for (SpacedReviewCandidate candidate : prioritizedPool) {
                if (!chosenIds.contains(candidate.itemId()) && !candidate.canonicalSkillCode().equals(currentSecond.canonicalSkillCode())) {
                    third = candidate;
                    break;
                }
            }
            if (third == null) {
                for (SpacedReviewCandidate candidate : prioritizedPool) {
                    if (!chosenIds.contains(candidate.itemId())) {
                        third = candidate;
                        break;
                    }
                }
            }
            if (third != null) {
                selected.add(third);
                chosenIds.add(third.itemId());
            }
        }

        // 4. Rearrange selected subset so no two adjacent share the same skill when possible
        return orderAdjacentDistinct(selected);
    }

    private List<SpacedReviewCandidate> orderAdjacentDistinct(List<SpacedReviewCandidate> items) {
        if (items.size() <= 2) {
            return items;
        }

        // 3 items case
        SpacedReviewCandidate a = items.get(0);
        SpacedReviewCandidate b = items.get(1);
        SpacedReviewCandidate c = items.get(2);

        // If a and b share skill, but c differs: [a, c, b]
        if (a.canonicalSkillCode().equals(b.canonicalSkillCode()) && !a.canonicalSkillCode().equals(c.canonicalSkillCode())) {
            return List.of(a, c, b);
        }

        // If b and c share skill, but a differs: [b, a, c]
        if (b.canonicalSkillCode().equals(c.canonicalSkillCode()) && !a.canonicalSkillCode().equals(b.canonicalSkillCode())) {
            return List.of(b, a, c);
        }

        return items;
    }
}
