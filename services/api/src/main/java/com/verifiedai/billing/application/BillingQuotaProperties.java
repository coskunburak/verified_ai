package com.verifiedai.billing.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Server-owned quota limits and monthly variable AI cost circuit breakers per Learning OS tier. */
@ConfigurationProperties(prefix = "verifiedai.billing.quotas")
public record BillingQuotaProperties(
    Free free,
    Monthly pro,
    Monthly coachPro,
    Monthly yksSeasonPass
) {
    public BillingQuotaProperties {
        free = free == null ? new Free(3, 1, 80_000L) : free;
        pro = pro == null ? new Monthly(150, 150, 2_500_000L) : pro;
        coachPro = coachPro == null ? new Monthly(400, 400, 4_200_000L) : coachPro;
        yksSeasonPass = yksSeasonPass == null ? new Monthly(400, 400, 4_200_000L) : yksSeasonPass;
    }

    public static BillingQuotaProperties defaults() {
        return new BillingQuotaProperties(null, null, null, null);
    }

    public record Free(
        int dailyAiVerificationsLimit,
        int dailyHandwritingDiagnosticsLimit,
        long monthlyAiCostCapMicros
    ) {
        public Free {
            if (dailyAiVerificationsLimit < 0
                || dailyHandwritingDiagnosticsLimit < 0
                || monthlyAiCostCapMicros < 0) {
                throw new IllegalArgumentException("Free quota limits and cost cap must be non-negative");
            }
        }
    }

    public record Monthly(
        int monthlyAiVerificationsLimit,
        int monthlyHybridTwinsLimit,
        long monthlyAiCostCapMicros
    ) {
        public Monthly {
            if (monthlyAiVerificationsLimit < 0
                || monthlyHybridTwinsLimit < 0
                || monthlyAiCostCapMicros < 0) {
                throw new IllegalArgumentException("Monthly quota limits and cost cap must be non-negative");
            }
        }
    }
}
