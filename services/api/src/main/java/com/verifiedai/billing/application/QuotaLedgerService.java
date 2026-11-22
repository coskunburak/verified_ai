package com.verifiedai.billing.application;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL-backed atomic quota and AI cost circuit-breaker ledger (Sprint 10.3). */
@Service
public class QuotaLedgerService {
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate defaultTx;
    private final TransactionTemplate requiresNewTx;
    private final FairUsePolicy fairUsePolicy;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final boolean enabled;

    @Autowired
    public QuotaLedgerService(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager,
        FairUsePolicy fairUsePolicy,
        MeterRegistry meterRegistry,
        Clock clock,
        @Value("${verifiedai.billing.quota-ledger-enabled:false}") String enabledFlag
    ) {
        this(
            jdbcTemplate,
            transactionManager,
            fairUsePolicy,
            meterRegistry,
            clock,
            "true".equalsIgnoreCase(enabledFlag)
        );
    }

    public QuotaLedgerService(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager,
        FairUsePolicy fairUsePolicy,
        MeterRegistry meterRegistry,
        Clock clock,
        boolean enabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.fairUsePolicy = Objects.requireNonNull(fairUsePolicy, "fairUsePolicy must not be null");
        this.meterRegistry = meterRegistry;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.enabled = enabled;
        if (transactionManager != null) {
            this.defaultTx = new TransactionTemplate(transactionManager);
            TransactionTemplate nested = new TransactionTemplate(transactionManager);
            nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            this.requiresNewTx = nested;
        } else {
            this.defaultTx = null;
            this.requiresNewTx = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public QuotaSummary getOrInitializeSummary(UUID userId, EffectiveEntitlementAccess access) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(access, "access must not be null");
        if (!enabled) {
            return fairUsePolicy.defaultSummary(access);
        }
        requirePersistence();
        FairUsePolicy.TierQuotaPolicy policy = fairUsePolicy.resolve(access);
        return defaultTx.execute(status -> {
            Instant now = clock.instant();
            insertLedgerIfAbsent(userId, policy, now);
            LedgerRow row = selectLedger(userId, policy.periodKey(), false);
            long monthlyCostMicros = monthlyCostMicros(userId, policy, row.accumulatedAiCostMicros());
            row = syncLedgerTierIfNeeded(row, policy, monthlyCostMicros, now);
            boolean breakerTripped = row.costCircuitBreakerTripped() || monthlyCostMicros >= policy.aiCostCapMicros();
            return toSummary(policy, row, breakerTripped);
        });
    }

    public QuotaConsumptionDecision reserveAndConsume(
        UUID userId,
        EffectiveEntitlementAccess access,
        QuotaOperationType op,
        String idempotencyKey,
        long estimatedCostMicros
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(access, "access must not be null");
        Objects.requireNonNull(op, "op must not be null");
        if (estimatedCostMicros < 0) {
            throw new IllegalArgumentException("estimatedCostMicros must be non-negative");
        }
        if (!fairUsePolicy.requiresPaidQuota(op)) {
            return QuotaConsumptionDecision.deterministicExempt(getOrInitializeSummary(userId, access));
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                "Valid idempotency key is required for quota consumption",
                false,
                "RETRY"
            );
        }
        if (!enabled) {
            return QuotaConsumptionDecision.allowedDisabled(fairUsePolicy.defaultSummary(access));
        }
        requirePersistence();
        FairUsePolicy.TierQuotaPolicy policy = fairUsePolicy.resolve(access);

        ReserveOutcome outcome = requiresNewTx.execute(status -> {
            Instant now = clock.instant();
            lockUserQuota(userId);

            List<String> existingStatuses = jdbcTemplate.query(
                "SELECT status FROM entitlement_quota_consumptions WHERE user_id = ? AND idempotency_key = ?",
                (rs, rowNum) -> rs.getString("status"),
                userId,
                idempotencyKey
            );
            if (!existingStatuses.isEmpty()) {
                String existingStatus = existingStatuses.getFirst();
                if ("RELEASED".equals(existingStatus)) {
                    throw new ApiProblemException(
                        HttpStatus.CONFLICT,
                        ApiErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Released quota idempotency key cannot be reused",
                        false,
                        "RETRY"
                    );
                }
                insertLedgerIfAbsent(userId, policy, now);
                LedgerRow row = selectLedger(userId, policy.periodKey(), false);
                long monthlyCost = monthlyCostMicros(userId, policy, row.accumulatedAiCostMicros());
                boolean breakerTripped = row.costCircuitBreakerTripped() || monthlyCost >= policy.aiCostCapMicros();
                return new ReserveOutcome(
                    QuotaConsumptionDecision.allowed(true, toSummary(policy, row, breakerTripped)),
                    false,
                    false
                );
            }

            insertLedgerIfAbsent(userId, policy, now);
            LedgerRow row = selectLedger(userId, policy.periodKey(), true);
            long monthlyCostMicros = monthlyCostMicros(userId, policy, row.accumulatedAiCostMicros());
            row = syncLedgerTierIfNeeded(row, policy, monthlyCostMicros, now);

            boolean alreadyOrExceedsCap = row.costCircuitBreakerTripped()
                || monthlyCostMicros + estimatedCostMicros > policy.aiCostCapMicros();
            if (alreadyOrExceedsCap) {
                boolean newlyTripped = !row.costCircuitBreakerTripped();
                if (newlyTripped) {
                    jdbcTemplate.update(
                        """
                        UPDATE entitlement_quota_ledgers
                        SET cost_circuit_breaker_tripped = TRUE, updated_at = ?, version = version + 1
                        WHERE id = ?
                        """,
                        Timestamp.from(now),
                        row.id()
                    );
                }
                QuotaSummary summary = toSummary(policy, row, true);
                return new ReserveOutcome(
                    QuotaConsumptionDecision.degradedDeterministicFallback(
                        true,
                        "AI_COST_CIRCUIT_BREAKER_TRIPPED",
                        summary
                    ),
                    newlyTripped,
                    false
                );
            }

            boolean quotaExceeded = switch (op) {
                case AI_VERIFICATION -> row.aiVerificationsUsed() + 1 > policy.aiVerificationsLimit();
                case HYBRID_TWIN, HANDWRITING_DIAGNOSTIC -> row.hybridTwinsUsed() + 1 > policy.hybridTwinsLimit();
                case DETERMINISTIC_TWIN, DETERMINISTIC_SYMBOLIC_VERIFY -> false;
            };
            if (quotaExceeded) {
                boolean breaker = row.costCircuitBreakerTripped() || monthlyCostMicros >= policy.aiCostCapMicros();
                QuotaSummary summary = toSummary(policy, row, breaker);
                return new ReserveOutcome(
                    QuotaConsumptionDecision.degradedDeterministicFallback(
                        breaker,
                        "QUOTA_EXHAUSTED",
                        summary
                    ),
                    false,
                    false
                );
            }

            int nextAiUsed = row.aiVerificationsUsed() + (op == QuotaOperationType.AI_VERIFICATION ? 1 : 0);
            int nextHybridUsed = row.hybridTwinsUsed()
                + (op == QuotaOperationType.HYBRID_TWIN || op == QuotaOperationType.HANDWRITING_DIAGNOSTIC ? 1 : 0);
            long nextRowCost = row.accumulatedAiCostMicros() + estimatedCostMicros;
            long nextMonthlyCost = monthlyCostMicros + estimatedCostMicros;
            boolean trippedNow = nextMonthlyCost >= policy.aiCostCapMicros();

            jdbcTemplate.update(
                """
                UPDATE entitlement_quota_ledgers
                SET ai_verifications_used = ?,
                    hybrid_twins_used = ?,
                    accumulated_ai_cost_micros = ?,
                    cost_circuit_breaker_tripped = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                """,
                nextAiUsed,
                nextHybridUsed,
                nextRowCost,
                trippedNow,
                Timestamp.from(now),
                row.id()
            );

            jdbcTemplate.update(
                """
                INSERT INTO entitlement_quota_consumptions
                    (ledger_id, user_id, idempotency_key, quota_type, units_consumed, cost_micros, status, training_eligible, created_at, updated_at)
                VALUES (?, ?, ?, ?, 1, ?, 'RESERVED', FALSE, ?, ?)
                """,
                row.id(),
                userId,
                idempotencyKey,
                op.name(),
                estimatedCostMicros,
                Timestamp.from(now),
                Timestamp.from(now)
            );

            QuotaSummary updated = QuotaSummary.of(
                policy.periodKey(),
                nextAiUsed,
                policy.aiVerificationsLimit(),
                nextHybridUsed,
                policy.hybridTwinsLimit(),
                trippedNow,
                row.resetsAt()
            );
            return new ReserveOutcome(
                QuotaConsumptionDecision.allowed(false, updated),
                trippedNow,
                true
            );
        });

        emitMetrics(policy, op, outcome);
        return outcome.decision();
    }

    public void reconcileActualCost(UUID userId, String idempotencyKey, long actualCostMicros) {
        if (!enabled || userId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        if (actualCostMicros < 0) {
            throw new IllegalArgumentException("actualCostMicros must be non-negative");
        }
        requirePersistence();
        requiresNewTx.executeWithoutResult(status -> {
            Instant now = clock.instant();
            lockUserQuota(userId);
            ConsumptionRow consumption = selectConsumptionForUpdate(userId, idempotencyKey);
            if (consumption == null || !"RESERVED".equals(consumption.status())) {
                return;
            }
            LedgerRow ledger = selectLedgerByIdForUpdate(consumption.ledgerId());
            if (ledger == null) {
                return;
            }
            long delta = actualCostMicros - consumption.costMicros();
            long updatedAccumulated = Math.max(0L, ledger.accumulatedAiCostMicros() + delta);

            jdbcTemplate.update(
                """
                UPDATE entitlement_quota_ledgers
                SET accumulated_ai_cost_micros = ?, updated_at = ?, version = version + 1
                WHERE id = ?
                """,
                updatedAccumulated,
                Timestamp.from(now),
                ledger.id()
            );

            long monthlyCost = monthlyCostForPeriodKey(userId, ledger.periodKey(), updatedAccumulated);
            boolean breakerTripped = ledger.costCircuitBreakerTripped() || monthlyCost >= ledger.aiCostCapMicros();
            if (breakerTripped != ledger.costCircuitBreakerTripped()) {
                jdbcTemplate.update(
                    """
                    UPDATE entitlement_quota_ledgers
                    SET cost_circuit_breaker_tripped = ?, updated_at = ?, version = version + 1
                    WHERE id = ?
                    """,
                    breakerTripped,
                    Timestamp.from(now),
                    ledger.id()
                );
            }

            jdbcTemplate.update(
                """
                UPDATE entitlement_quota_consumptions
                SET cost_micros = ?, status = 'COMMITTED', updated_at = ?
                WHERE id = ?
                """,
                actualCostMicros,
                Timestamp.from(now),
                consumption.id()
            );
        });
    }

    public void releaseReservation(UUID userId, String idempotencyKey) {
        if (!enabled || userId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        requirePersistence();
        requiresNewTx.executeWithoutResult(status -> {
            Instant now = clock.instant();
            lockUserQuota(userId);
            ConsumptionRow consumption = selectConsumptionForUpdate(userId, idempotencyKey);
            if (consumption == null || !"RESERVED".equals(consumption.status())) {
                return;
            }
            LedgerRow ledger = selectLedgerByIdForUpdate(consumption.ledgerId());
            if (ledger == null) {
                return;
            }
            int aiDelta = "AI_VERIFICATION".equals(consumption.quotaType()) ? consumption.unitsConsumed() : 0;
            int hybridDelta = ("HYBRID_TWIN".equals(consumption.quotaType())
                || "HANDWRITING_DIAGNOSTIC".equals(consumption.quotaType())) ? consumption.unitsConsumed() : 0;
            int nextAiUsed = Math.max(0, ledger.aiVerificationsUsed() - aiDelta);
            int nextHybridUsed = Math.max(0, ledger.hybridTwinsUsed() - hybridDelta);
            long nextAccumulated = Math.max(0L, ledger.accumulatedAiCostMicros() - consumption.costMicros());

            jdbcTemplate.update(
                """
                UPDATE entitlement_quota_ledgers
                SET ai_verifications_used = ?,
                    hybrid_twins_used = ?,
                    accumulated_ai_cost_micros = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                """,
                nextAiUsed,
                nextHybridUsed,
                nextAccumulated,
                Timestamp.from(now),
                ledger.id()
            );

            long monthlyCost = monthlyCostForPeriodKey(userId, ledger.periodKey(), nextAccumulated);
            boolean stillTripped = monthlyCost >= ledger.aiCostCapMicros();
            if (stillTripped != ledger.costCircuitBreakerTripped()) {
                jdbcTemplate.update(
                    """
                    UPDATE entitlement_quota_ledgers
                    SET cost_circuit_breaker_tripped = ?, updated_at = ?, version = version + 1
                    WHERE id = ?
                    """,
                    stillTripped,
                    Timestamp.from(now),
                    ledger.id()
                );
            }

            jdbcTemplate.update(
                """
                UPDATE entitlement_quota_consumptions
                SET status = 'RELEASED', updated_at = ?
                WHERE id = ?
                """,
                Timestamp.from(now),
                consumption.id()
            );
        });
    }

    private void emitMetrics(FairUsePolicy.TierQuotaPolicy policy, QuotaOperationType op, ReserveOutcome outcome) {
        if (meterRegistry == null || outcome == null) {
            return;
        }
        QuotaConsumptionDecision decision = outcome.decision();
        if (outcome.consumed()) {
            meterRegistry.counter(
                "quota_consumed",
                "tier", policy.tierCode(),
                "operation", op.name(),
                "period_type", policy.periodType()
            ).increment();
        }
        if (outcome.breakerTripped()) {
            meterRegistry.counter(
                "user_ai_cost_circuit_breaker_tripped",
                "tier", policy.tierCode(),
                "period_key", policy.periodKey()
            ).increment();
        }
        if (!decision.allowed()) {
            meterRegistry.counter(
                "quota_exhausted_paywall_triggered",
                "tier", policy.tierCode(),
                "operation", op.name(),
                "reason", decision.reason()
            ).increment();
        }
    }

    private void requirePersistence() {
        if (jdbcTemplate == null || defaultTx == null || requiresNewTx == null) {
            throw new IllegalStateException("QuotaLedgerService requires JdbcTemplate and PlatformTransactionManager when enabled");
        }
    }

    private void lockUserQuota(UUID userId) {
        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(hashtextextended(?, 10303))",
            ps -> ps.setString(1, userId.toString()),
            rs -> {
            }
        );
    }

    private void insertLedgerIfAbsent(UUID userId, FairUsePolicy.TierQuotaPolicy policy, Instant now) {
        jdbcTemplate.update(
            """
            INSERT INTO entitlement_quota_ledgers (
                user_id, period_key, tier_code, ai_verifications_limit, hybrid_twins_limit,
                ai_cost_cap_micros, resets_at, training_eligible, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, FALSE, ?, ?)
            ON CONFLICT (user_id, period_key) DO NOTHING
            """,
            userId,
            policy.periodKey(),
            policy.tierCode(),
            policy.aiVerificationsLimit(),
            policy.hybridTwinsLimit(),
            policy.aiCostCapMicros(),
            Timestamp.from(policy.resetsAt()),
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    private LedgerRow selectLedger(UUID userId, String periodKey, boolean forUpdate) {
        String sql = """
            SELECT id, period_key, tier_code, ai_verifications_used, ai_verifications_limit,
                   hybrid_twins_used, hybrid_twins_limit, accumulated_ai_cost_micros,
                   ai_cost_cap_micros, cost_circuit_breaker_tripped, resets_at
            FROM entitlement_quota_ledgers
            WHERE user_id = ? AND period_key = ?
            """ + (forUpdate ? " FOR UPDATE" : "");
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> mapLedgerRow(rs), userId, periodKey);
    }

    private LedgerRow selectLedgerByIdForUpdate(UUID ledgerId) {
        List<LedgerRow> rows = jdbcTemplate.query(
            """
            SELECT id, period_key, tier_code, ai_verifications_used, ai_verifications_limit,
                   hybrid_twins_used, hybrid_twins_limit, accumulated_ai_cost_micros,
                   ai_cost_cap_micros, cost_circuit_breaker_tripped, resets_at
            FROM entitlement_quota_ledgers
            WHERE id = ?
            FOR UPDATE
            """,
            (rs, rowNum) -> mapLedgerRow(rs),
            ledgerId
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private ConsumptionRow selectConsumptionForUpdate(UUID userId, String idempotencyKey) {
        List<ConsumptionRow> rows = jdbcTemplate.query(
            """
            SELECT id, ledger_id, quota_type, units_consumed, cost_micros, status
            FROM entitlement_quota_consumptions
            WHERE user_id = ? AND idempotency_key = ?
            FOR UPDATE
            """,
            (rs, rowNum) -> new ConsumptionRow(
                rs.getObject("id", UUID.class),
                rs.getObject("ledger_id", UUID.class),
                rs.getString("quota_type"),
                rs.getInt("units_consumed"),
                rs.getLong("cost_micros"),
                rs.getString("status")
            ),
            userId,
            idempotencyKey
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private long monthlyCostMicros(UUID userId, FairUsePolicy.TierQuotaPolicy policy, long rowAccumulatedMicros) {
        if (!"DAILY".equals(policy.periodType())) {
            return rowAccumulatedMicros;
        }
        Long sum = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(accumulated_ai_cost_micros), 0)
            FROM entitlement_quota_ledgers
            WHERE user_id = ? AND period_key LIKE ?
            """,
            Long.class,
            userId,
            policy.monthlyPeriodKey() + "%"
        );
        return sum == null ? 0L : sum;
    }

    private long monthlyCostForPeriodKey(UUID userId, String periodKey, long rowAccumulatedMicros) {
        if (periodKey == null || periodKey.length() <= 7) {
            return rowAccumulatedMicros;
        }
        String monthPrefix = periodKey.substring(0, 7) + "%";
        Long sum = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(accumulated_ai_cost_micros), 0)
            FROM entitlement_quota_ledgers
            WHERE user_id = ? AND period_key LIKE ?
            """,
            Long.class,
            userId,
            monthPrefix
        );
        return sum == null ? 0L : sum;
    }

    private LedgerRow syncLedgerTierIfNeeded(
        LedgerRow row,
        FairUsePolicy.TierQuotaPolicy policy,
        long monthlyCostMicros,
        Instant now
    ) {
        boolean tierChanged = !row.tierCode().equals(policy.tierCode());
        boolean limitsChanged = row.aiVerificationsLimit() != policy.aiVerificationsLimit()
            || row.hybridTwinsLimit() != policy.hybridTwinsLimit()
            || row.aiCostCapMicros() != policy.aiCostCapMicros();
        if (!tierChanged && !limitsChanged) {
            return row;
        }
        boolean updatedBreaker = tierChanged
            ? monthlyCostMicros >= policy.aiCostCapMicros()
            : (row.costCircuitBreakerTripped() || monthlyCostMicros >= policy.aiCostCapMicros());
        jdbcTemplate.update(
            """
            UPDATE entitlement_quota_ledgers
            SET tier_code = ?,
                ai_verifications_limit = ?,
                hybrid_twins_limit = ?,
                ai_cost_cap_micros = ?,
                cost_circuit_breaker_tripped = ?,
                resets_at = ?,
                updated_at = ?,
                version = version + 1
            WHERE id = ?
            """,
            policy.tierCode(),
            policy.aiVerificationsLimit(),
            policy.hybridTwinsLimit(),
            policy.aiCostCapMicros(),
            updatedBreaker,
            Timestamp.from(policy.resetsAt()),
            Timestamp.from(now),
            row.id()
        );
        return new LedgerRow(
            row.id(),
            row.periodKey(),
            policy.tierCode(),
            row.aiVerificationsUsed(),
            policy.aiVerificationsLimit(),
            row.hybridTwinsUsed(),
            policy.hybridTwinsLimit(),
            row.accumulatedAiCostMicros(),
            policy.aiCostCapMicros(),
            updatedBreaker,
            policy.resetsAt()
        );
    }

    private static QuotaSummary toSummary(
        FairUsePolicy.TierQuotaPolicy policy,
        LedgerRow row,
        boolean breakerTripped
    ) {
        return QuotaSummary.of(
            policy.periodKey(),
            row.aiVerificationsUsed(),
            policy.aiVerificationsLimit(),
            row.hybridTwinsUsed(),
            policy.hybridTwinsLimit(),
            breakerTripped,
            row.resetsAt()
        );
    }

    private static LedgerRow mapLedgerRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new LedgerRow(
            rs.getObject("id", UUID.class),
            rs.getString("period_key"),
            rs.getString("tier_code"),
            rs.getInt("ai_verifications_used"),
            rs.getInt("ai_verifications_limit"),
            rs.getInt("hybrid_twins_used"),
            rs.getInt("hybrid_twins_limit"),
            rs.getLong("accumulated_ai_cost_micros"),
            rs.getLong("ai_cost_cap_micros"),
            rs.getBoolean("cost_circuit_breaker_tripped"),
            rs.getTimestamp("resets_at").toInstant()
        );
    }

    private record LedgerRow(
        UUID id,
        String periodKey,
        String tierCode,
        int aiVerificationsUsed,
        int aiVerificationsLimit,
        int hybridTwinsUsed,
        int hybridTwinsLimit,
        long accumulatedAiCostMicros,
        long aiCostCapMicros,
        boolean costCircuitBreakerTripped,
        Instant resetsAt
    ) {}

    private record ConsumptionRow(
        UUID id,
        UUID ledgerId,
        String quotaType,
        int unitsConsumed,
        long costMicros,
        String status
    ) {}

    private record ReserveOutcome(
        QuotaConsumptionDecision decision,
        boolean breakerTripped,
        boolean consumed
    ) {}
}
