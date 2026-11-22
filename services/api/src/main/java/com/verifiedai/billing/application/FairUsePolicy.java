package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.EntitlementSource;
import com.verifiedai.billing.domain.model.EntitlementStatus;
import com.verifiedai.billing.domain.model.EntitlementTier;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Deterministic server-authoritative quota limits, UTC period windows, and AI cost circuit breakers. */
@Component
public class FairUsePolicy {
    private final BillingQuotaProperties properties;
    private final Clock clock;

    @Autowired
    public FairUsePolicy(BillingQuotaProperties properties, Clock clock) {
        this.properties = properties == null ? BillingQuotaProperties.defaults() : properties;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public FairUsePolicy(Clock clock) {
        this(BillingQuotaProperties.defaults(), clock);
    }

    public record TierQuotaPolicy(
        String tierCode,
        String periodType,
        String periodKey,
        String monthlyPeriodKey,
        int aiVerificationsLimit,
        int hybridTwinsLimit,
        long aiCostCapMicros,
        Instant resetsAt
    ) {}

    public String resolveTierCode(EffectiveEntitlementAccess access) {
        if (access == null || !access.paidAccessActive() || "FREE".equals(access.effectiveTier())) {
            return "FREE";
        }
        if ("YKS_SEASON_PASS".equals(access.packageCode())) {
            return "YKS_SEASON_PASS";
        }
        if ("COACH_PRO".equals(access.effectiveTier())) {
            return "COACH_PRO";
        }
        if ("PRO".equals(access.effectiveTier())) {
            return "PRO";
        }
        return "FREE";
    }

    public TierQuotaPolicy resolve(EffectiveEntitlementAccess access) {
        return resolveForTierCode(resolveTierCode(access), clock.instant());
    }

    public TierQuotaPolicy resolveForTierCode(String tierCode, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        String normalized = normalizeTierCode(tierCode);
        LocalDate utcDate = now.atZone(ZoneOffset.UTC).toLocalDate();
        YearMonth utcMonth = YearMonth.from(utcDate);
        String monthlyKey = utcMonth.toString();
        Instant nextDayReset = utcDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant nextMonthReset = utcMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        return switch (normalized) {
            case "PRO" -> new TierQuotaPolicy(
                "PRO",
                "MONTHLY",
                monthlyKey,
                monthlyKey,
                properties.pro().monthlyAiVerificationsLimit(),
                properties.pro().monthlyHybridTwinsLimit(),
                properties.pro().monthlyAiCostCapMicros(),
                nextMonthReset
            );
            case "COACH_PRO" -> new TierQuotaPolicy(
                "COACH_PRO",
                "MONTHLY",
                monthlyKey,
                monthlyKey,
                properties.coachPro().monthlyAiVerificationsLimit(),
                properties.coachPro().monthlyHybridTwinsLimit(),
                properties.coachPro().monthlyAiCostCapMicros(),
                nextMonthReset
            );
            case "YKS_SEASON_PASS" -> new TierQuotaPolicy(
                "YKS_SEASON_PASS",
                "MONTHLY",
                monthlyKey,
                monthlyKey,
                properties.yksSeasonPass().monthlyAiVerificationsLimit(),
                properties.yksSeasonPass().monthlyHybridTwinsLimit(),
                properties.yksSeasonPass().monthlyAiCostCapMicros(),
                nextMonthReset
            );
            default -> new TierQuotaPolicy(
                "FREE",
                "DAILY",
                utcDate.toString(),
                monthlyKey,
                properties.free().dailyAiVerificationsLimit(),
                properties.free().dailyHandwritingDiagnosticsLimit(),
                properties.free().monthlyAiCostCapMicros(),
                nextDayReset
            );
        };
    }

    public boolean requiresPaidQuota(QuotaOperationType op) {
        Objects.requireNonNull(op, "op must not be null");
        return op != QuotaOperationType.DETERMINISTIC_TWIN
            && op != QuotaOperationType.DETERMINISTIC_SYMBOLIC_VERIFY;
    }

    public QuotaSummary defaultSummary(EffectiveEntitlementAccess access) {
        TierQuotaPolicy policy = resolve(access);
        return QuotaSummary.of(
            policy.periodKey(),
            0,
            policy.aiVerificationsLimit(),
            0,
            policy.hybridTwinsLimit(),
            false,
            policy.resetsAt()
        );
    }

    public static EffectiveEntitlementAccess syntheticAccessForTier(String rawTier, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        String normalized = rawTier == null ? "FREE" : rawTier.trim().toUpperCase(Locale.ROOT);
        Instant future = now.plusSeconds(3600);
        return switch (normalized) {
            case "YKS_SEASON_PASS" -> LearningOsTierCapabilityPolicy.project(
                EntitlementTier.PRO_PLUS, EntitlementStatus.ACTIVE, EntitlementSource.APP_STORE_SUBSCRIPTION,
                future, null, true, true, now);
            case "COACH_PRO", "PRO_PLUS" -> LearningOsTierCapabilityPolicy.project(
                EntitlementTier.PRO_PLUS, EntitlementStatus.ACTIVE, EntitlementSource.APP_STORE_SUBSCRIPTION,
                future, null, false, true, now);
            case "PRO" -> LearningOsTierCapabilityPolicy.project(
                EntitlementTier.PRO, EntitlementStatus.ACTIVE, EntitlementSource.APP_STORE_SUBSCRIPTION,
                future, null, false, true, now);
            default -> LearningOsTierCapabilityPolicy.project(
                EntitlementTier.FREE, EntitlementStatus.ACTIVE, EntitlementSource.DEFAULT_FREE,
                null, null, false, true, now);
        };
    }

    private static String normalizeTierCode(String tierCode) {
        if (tierCode == null || tierCode.isBlank()) {
            return "FREE";
        }
        return switch (tierCode.trim().toUpperCase(Locale.ROOT)) {
            case "PRO" -> "PRO";
            case "COACH_PRO", "PRO_PLUS" -> "COACH_PRO";
            case "YKS_SEASON_PASS" -> "YKS_SEASON_PASS";
            default -> "FREE";
        };
    }
}
