package com.verifiedai.ai.application;

import java.util.Locale;

public record AiRouteTarget(
    String provider,
    String model
) {

    public AiRouteTarget {
        provider = normalizeProvider(provider);

        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException(
                "AI route model is required"
            );
        }

        model = model.trim();
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException(
                "AI route provider is required"
            );
        }

        return provider
            .trim()
            .toUpperCase(Locale.ROOT);
    }
}
