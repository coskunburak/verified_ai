package com.verifiedai.ai.infrastructure.persistence;

import com.verifiedai.ai.application.*;
import com.verifiedai.ai.infrastructure.configuration.AiBudgetProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Row locks serialize commitments across instances. Failures roll back every account. */
@Component
public class PostgresAiSpendGuard implements AiSpendGuard {
    private final JdbcTemplate jdbc;
    private final AiBudgetProperties limits;
    private final AiEntitlementResolver entitlements;
    private final Clock clock;

    public PostgresAiSpendGuard(JdbcTemplate jdbc, AiBudgetProperties limits,
        AiEntitlementResolver entitlements, Clock clock) {
        this.jdbc = jdbc;
        this.limits = limits;
        this.entitlements = entitlements;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(AiExecutionCommand command, AiRoutePlan plan, AiRouteTarget target,
        int attempt, long ceiling, long requestLimit) {
        if (ceiling < 0 || requestLimit < 0 || attempt < 1 || attempt > 8 || !plan.allTargets().contains(target)) throw blocked();
        // Fixture calls are provably free and already have operation idempotency in the usage ledger.
        if (ceiling == 0 && ("LOCAL_FIXTURE".equals(target.provider()) || "UNAVAILABLE".equals(target.provider()))) return;
        var context = command.executionContext();
        if (context.userId() == null || context.problemSessionId() == null || ceiling == 0) throw blocked();
        String tier = entitlements.tier(context.userId());
        requestLimit = Math.min(requestLimit, plan.maxCostMicros());
        if (command.maxCostMicros() != null) requestLimit = Math.min(requestLimit, command.maxCostMicros());
        if (command.routeContext().maxCostMicros() != null) requestLimit = Math.min(requestLimit, command.routeContext().maxCostMicros());
        long effectiveRequest = Math.min(requestLimit, Math.min(limits.requestMicros(),
            limits.tierRequestMicros().getOrDefault(tier, 0L)));
        if (ceiling > limits.attemptMicros() || ceiling > effectiveRequest) throw blocked();
        var now = clock.instant();
        var date = now.atZone(ZoneOffset.UTC).toLocalDate();
        String month = date.withDayOfMonth(1).toString();
        Map<String, Long> accounts = new TreeMap<>();
        accounts.put("GLOBAL_MONTH:" + month, limits.globalMonthlyMicros());
        accounts.put("PROVIDER_MONTH:" + target.provider() + ":" + month, limits.providerMonthlyMicros());
        accounts.put("OPERATION:" + context.operationId(), effectiveRequest);
        accounts.put("SESSION:" + context.problemSessionId(), limits.sessionMicros());
        accounts.put("USER_DAY:" + context.userId() + ":" + date, limits.userDailyMicros());
        accounts.put("USER_MONTH:" + context.userId() + ":" + month, limits.userMonthlyMicros());
        // The FK and unique key bind budget attempts to the existing operation ledger.
        jdbc.update("""
            INSERT INTO ai_spend_attempts(operation_id, attempt_number, provider, model, pricing_version,
                budget_policy_version, reserved_micros, state, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'RESERVED', ?)
            """, context.operationId(), attempt, target.provider(), target.model(), plan.pricingVersion(),
            limits.policyVersion(), ceiling, Timestamp.from(now));
        for (var account : accounts.entrySet()) {
            boolean personal = !account.getKey().startsWith("GLOBAL_") && !account.getKey().startsWith("PROVIDER_");
            jdbc.update("INSERT INTO ai_spend_accounts(account_key, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                account.getKey(), personal ? context.userId() : null);
            long used = jdbc.queryForObject("SELECT committed_micros FROM ai_spend_accounts WHERE account_key = ? FOR UPDATE",
                Long.class, account.getKey());
            if (used > account.getValue() || ceiling > account.getValue() - used) throw blocked();
            jdbc.update("UPDATE ai_spend_accounts SET committed_micros = committed_micros + ? WHERE account_key = ?",
                ceiling, account.getKey());
            jdbc.update("INSERT INTO ai_spend_allocations VALUES (?, ?, ?)", context.operationId(), attempt, account.getKey());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void report(AiExecutionCommand command, int attempt, AiUsage usage, AiProviderFailureClass failure) {
        // Provider usage is an estimate, not an invoice. Never auto-release committed ceilings.
        // Missing reports/crashes retain RESERVED or UNKNOWN commitments for operator reconciliation.
        var prices = jdbc.queryForList("SELECT pricing_version FROM ai_spend_attempts WHERE operation_id = ? AND attempt_number = ?",
            String.class, command.executionContext().operationId(), attempt);
        if (prices.isEmpty()) return; // Free fixture has no financial commitment row.
        boolean compatible = usage != null && "USD".equals(usage.currency()) && prices.getFirst().equals(usage.pricingVersion());
        jdbc.update("""
            UPDATE ai_spend_attempts SET reported_micros = ?, input_tokens = ?, output_tokens = ?, image_units = ?,
                request_units = ?, state = ?, failure_class = ?, reported_at = ?
            WHERE operation_id = ? AND attempt_number = ? AND state = 'RESERVED'
            """, compatible ? usage.estimatedCostMicros() : null,
            compatible ? usage.inputTokens() : null, compatible ? usage.outputTokens() : null,
            compatible ? usage.imageUnits() : null, compatible ? usage.requestUnits() : null,
            compatible ? "REPORTED_ESTIMATE" : "UNKNOWN", failure == null ? null : failure.name(),
            Timestamp.from(clock.instant()), command.executionContext().operationId(), attempt);
    }

    private static AiProviderException blocked() {
        return new AiProviderException(AiProviderFailureClass.BUDGET_EXCEEDED, false, "AI spending allowance unavailable");
    }
}
