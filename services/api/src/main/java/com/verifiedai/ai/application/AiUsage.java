package com.verifiedai.ai.application;

public record AiUsage(
    Integer inputTokens,
    Integer outputTokens,
    Integer imageUnits,
    int requestUnits,
    long estimatedCostMicros,
    String currency,
    String pricingVersion
) {
    public static AiUsage zeroCost(String pricingVersion) {
        return new AiUsage(null, null, 1, 1, 0, "USD", pricingVersion);
    }
}
