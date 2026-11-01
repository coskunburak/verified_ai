package com.verifiedai.ai.infrastructure.configuration;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operator-owned Gemini API evaluation and runtime configuration.
 * Strictly prevents credential leakage in logs or diagnostics.
 */
@ConfigurationProperties("app.ai.gemini")
public record GeminiProviderProperties(
    boolean enabled,
    URI endpoint,
    String model,
    String apiKey,
    String evaluationApproval,
    Limits limits,
    Accounting accounting
) {
    public static final String PROVIDER = "GEMINI";
    public static final String DEFAULT_MODEL = "gemini-2.5-flash";

    private static final Set<String> APPROVED_MODELS = Set.of(
        "gemini-2.5-flash",
        "gemini-2.5-pro",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-1.5-flash",
        "gemini-1.5-pro"
    );

    private static final Pattern MODEL_PATTERN = Pattern.compile("^gemini-[12]\\.[05]-(flash|pro)(-[a-z0-9]+)?$");

    public GeminiProviderProperties {
        model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        limits = limits == null ? Limits.defaultLimits() : limits;
        accounting = accounting == null ? Accounting.defaultAccounting() : accounting;

        if (enabled) {
            require(endpoint != null && endpoint.getHost() != null && endpoint.getUserInfo() == null
                && endpoint.getQuery() == null && endpoint.getFragment() == null,
                "Invalid Gemini inference endpoint");
            boolean loopback = Set.of("127.0.0.1", "[::1]", "localhost").contains(endpoint.getHost());
            require("https".equals(endpoint.getScheme()) || (loopback && "http".equals(endpoint.getScheme())),
                "Gemini inference endpoint must be HTTPS (or loopback HTTP for local evaluation)");
            require(apiKey != null && !apiKey.isBlank() && apiKey.length() >= 16,
                "Missing or invalid Gemini API key");
            require(model != null && !model.isBlank() && (APPROVED_MODELS.contains(model) || MODEL_PATTERN.matcher(model).matches()),
                "Unapproved Gemini model identifier: " + model + ". Consumer-facing names are not valid API identifiers.");
            require(evaluationApproval != null && evaluationApproval.matches("[A-Za-z0-9._/-]{4,160}"),
                "Missing owner evaluation approval reference");
            require(limits != null && accounting != null, "Incomplete Gemini provider policy");
        }
    }

    public static GeminiProviderProperties disabled() {
        return new GeminiProviderProperties(
            false,
            URI.create("https://generativelanguage.googleapis.com"),
            DEFAULT_MODEL,
            "",
            "disabled",
            Limits.defaultLimits(),
            Accounting.defaultAccounting()
        );
    }

    @Override
    public String toString() {
        return "GeminiProviderProperties[enabled=" + enabled + ", model=" + model + ", apiKey=REDACTED]";
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public record Limits(
        int contextTokens,
        int maxInputTokens,
        int maxOutputTokens,
        int maxRequestBytes,
        int maxResponseBytes,
        Duration connectTimeout,
        Duration responseTimeout,
        int maxConcurrency
    ) {
        public Limits {
            require(contextTokens > 0 && maxInputTokens > 0 && maxOutputTokens > 0, "Invalid token limits");
            require(maxRequestBytes > 0 && maxResponseBytes > 0, "Invalid byte limits");
            require(connectTimeout != null && !connectTimeout.isNegative() && !connectTimeout.isZero(), "Invalid connect timeout");
            require(responseTimeout != null && !responseTimeout.isNegative() && !responseTimeout.isZero(), "Invalid response timeout");
            require(maxConcurrency > 0, "Invalid concurrency limit");
        }

        public static Limits defaultLimits() {
            return new Limits(
                1048576,
                32768,
                8192,
                131072,
                262144,
                Duration.ofSeconds(2),
                Duration.ofSeconds(30),
                4
            );
        }
    }

    public record Accounting(
        String pricingVersion,
        String billingSemantics,
        long attemptAllocationMicros
    ) {
        public Accounting {
            require(pricingVersion != null && !pricingVersion.isBlank(), "Invalid pricing version");
            require("PREPAID_CAPACITY_FIXED_ALLOCATION".equals(billingSemantics)
                || "VARIABLE_TOKEN_METERING".equals(billingSemantics), "Unapproved billing semantics");
            require(attemptAllocationMicros >= 0, "Invalid attempt allocation");
        }

        public static Accounting defaultAccounting() {
            return new Accounting(
                "gemini-2.5-flash-2026-v1",
                "PREPAID_CAPACITY_FIXED_ALLOCATION",
                200L
            );
        }
    }
}
