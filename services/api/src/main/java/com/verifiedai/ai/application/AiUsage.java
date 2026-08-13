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

    public AiUsage {
        requireNullableNonNegative(inputTokens, "inputTokens");
        requireNullableNonNegative(outputTokens, "outputTokens");
        requireNullableNonNegative(imageUnits, "imageUnits");

        if (requestUnits < 0) {
            throw new IllegalArgumentException(
                "requestUnits must not be negative"
            );
        }

        if (estimatedCostMicros < 0) {
            throw new IllegalArgumentException(
                "estimatedCostMicros must not be negative"
            );
        }

        if (currency == null || currency.isBlank()) {
            currency = "USD";
        }

        if (pricingVersion == null || pricingVersion.isBlank()) {
            throw new IllegalArgumentException(
                "pricingVersion is required"
            );
        }
    }

    public static AiUsage zeroCost(String pricingVersion) {
        return new AiUsage(
            null,
            null,
            1,
            1,
            0,
            "USD",
            pricingVersion
        );
    }

    private static void requireNullableNonNegative(
        Integer value,
        String name
    ) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(
                name + " must not be negative"
            );
        }
    }
}
