package com.verifiedai.featureflags.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Pure domain policy for flag evaluation, kill-switch supremacy (INV-FF-002),
 * type/bounds enforcement, client-safe filtering (INV-FF-001), and canonical audit hashing.
 */
public final class FeatureFlagEvaluationPolicy {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9:._-]{8,128}$");
    private static final int MAX_VALUE_JSON_BYTES = 512;
    private static final int MIN_REASON_LEN = 5;
    private static final int MAX_REASON_LEN = 240;

    private static final Map<String, FeatureFlagDefinitionSnapshot> CANONICAL_DEFAULTS = buildCanonicalDefaults();
    private static final Map<String, String> CANONICAL_EXPERIMENT_CONTROLS = Map.of(
        "EXP-LOS-01", "control",
        "EXP-LOS-02", "control_60s",
        "EXP-LOS-08", "control_quota_wall",
        "EXP-LOS-09", "control_two_plan"
    );

    private FeatureFlagEvaluationPolicy() {
    }

    private static Map<String, FeatureFlagDefinitionSnapshot> buildCanonicalDefaults() {
        Instant epoch = Instant.parse("2026-11-23T00:00:00Z");
        Instant removal = Instant.parse("2027-06-01T00:00:00Z");
        return Map.ofEntries(
            Map.entry("secondary_solver_enabled", new FeatureFlagDefinitionSnapshot(
                "secondary_solver_enabled", FlagCategory.KILL_SWITCH, FlagValueType.BOOLEAN,
                false, false, false, 0, "v1", "false", "false",
                "platform-ai", "Emergency kill switch and progressive gate for secondary AI solver escalation",
                removal, 0L, false, epoch, epoch
            )),
            Map.entry("advanced_verification_enabled", new FeatureFlagDefinitionSnapshot(
                "advanced_verification_enabled", FlagCategory.RELEASE_FLAG, FlagValueType.BOOLEAN,
                false, false, false, 0, "v1", "false", "false",
                "platform-verifier", "Progressive rollout flag for advanced deterministic verification methods",
                removal, 0L, false, epoch, epoch
            )),
            Map.entry("tutor_v2_enabled", new FeatureFlagDefinitionSnapshot(
                "tutor_v2_enabled", FlagCategory.RELEASE_FLAG, FlagValueType.BOOLEAN,
                false, false, false, 0, "v1", "false", "false",
                "platform-learning", "Progressive rollout flag for Socratic Tutor v2 flow",
                removal, 0L, false, epoch, epoch
            )),
            Map.entry("adaptive_plan_v2_enabled", new FeatureFlagDefinitionSnapshot(
                "adaptive_plan_v2_enabled", FlagCategory.RELEASE_FLAG, FlagValueType.BOOLEAN,
                false, false, false, 0, "v1", "false", "false",
                "platform-learning", "Progressive rollout flag for adaptive study plan v2 engine",
                removal, 0L, false, epoch, epoch
            )),
            Map.entry("pdf_upload_enabled", new FeatureFlagDefinitionSnapshot(
                "pdf_upload_enabled", FlagCategory.KILL_SWITCH, FlagValueType.BOOLEAN,
                true, true, false, 10_000, "v1", "true", "true",
                "platform-security", "Operational kill switch to disable PDF uploads immediately during security incidents",
                null, 0L, false, epoch, epoch
            )),
            Map.entry("verification_method_false_positive_kill_switch", new FeatureFlagDefinitionSnapshot(
                "verification_method_false_positive_kill_switch", FlagCategory.KILL_SWITCH, FlagValueType.BOOLEAN,
                false, false, false, 0, "v1", "false", "false",
                "platform-verifier", "Emergency kill switch to disable verification methods linked to false positives",
                null, 0L, false, epoch, epoch
            )),
            Map.entry("strong_model_pct", new FeatureFlagDefinitionSnapshot(
                "strong_model_pct", FlagCategory.REMOTE_CONFIG, FlagValueType.INTEGER,
                false, true, false, 10_000, "v1", "0", "0",
                "platform-ai", "Server-authoritative strong-model routing percentage ceiling (0-100)",
                null, 0L, false, epoch, epoch
            )),
            Map.entry("proprietary_canary_pct", new FeatureFlagDefinitionSnapshot(
                "proprietary_canary_pct", FlagCategory.REMOTE_CONFIG, FlagValueType.INTEGER,
                false, false, false, 0, "v1", "0", "0",
                "platform-ai", "Proprietary model shadow/canary percentage (locked at 0 until Phase 13 gates)",
                null, 0L, false, epoch, epoch
            )),
            Map.entry("daily_solve_quota_override", new FeatureFlagDefinitionSnapshot(
                "daily_solve_quota_override", FlagCategory.REMOTE_CONFIG, FlagValueType.INTEGER,
                false, false, false, 0, "v1", "3", "3",
                "platform-billing", "Server-authoritative remote configuration for free daily solve quota baseline",
                null, 0L, false, epoch, epoch
            )),
            Map.entry("client_platform_health_screen", new FeatureFlagDefinitionSnapshot(
                "client_platform_health_screen", FlagCategory.RELEASE_FLAG, FlagValueType.BOOLEAN,
                true, false, false, 0, "v1", "false", "false",
                "platform-ios", "Client-safe presentation flag matching FeatureFlag.platformHealthScreen",
                removal, 0L, false, epoch, epoch
            )),
            Map.entry("client_mistake_moment_paywall_ui", new FeatureFlagDefinitionSnapshot(
                "client_mistake_moment_paywall_ui", FlagCategory.EXPERIMENT_FLAG, FlagValueType.BOOLEAN,
                true, false, false, 0, "v1", "false", "false",
                "platform-growth", "Client-safe presentation flag for contextual paywall card framing",
                removal, 0L, false, epoch, epoch
            ))
        );
    }

    public static Optional<FeatureFlagDefinitionSnapshot> canonicalDefault(String flagKey) {
        if (flagKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CANONICAL_DEFAULTS.get(flagKey));
    }

    public static String canonicalExperimentControl(String experimentKey) {
        if (experimentKey == null) {
            return "control";
        }
        return CANONICAL_EXPERIMENT_CONTROLS.getOrDefault(experimentKey, "control");
    }

    public static EvaluatedFlag evaluateFlag(FeatureFlagDefinitionSnapshot def, UUID userId, Instant now) {
        Objects.requireNonNull(def, "flag definition must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(now, "now must not be null");

        // INV-FF-002: Kill switch supremacy overrides enabled state, rollout percentage, and current value.
        if (def.killSwitchActive()) {
            String safeValue = def.valueType() == FlagValueType.BOOLEAN ? "false" : def.defaultValueJson();
            return new EvaluatedFlag(
                def.flagKey(),
                def.category().name(),
                def.valueType().name(),
                false,
                safeValue,
                true,
                "KILL_SWITCH_ACTIVE",
                now
            );
        }

        if (!def.enabled()) {
            return new EvaluatedFlag(
                def.flagKey(),
                def.category().name(),
                def.valueType().name(),
                false,
                def.defaultValueJson(),
                false,
                "FLAG_DISABLED",
                now
            );
        }

        boolean inRollout = DeterministicBucketingPolicy.isInRollout(
            def.flagKey(),
            def.bucketingSalt(),
            userId,
            def.rolloutBasisPoints()
        );
        if (!inRollout) {
            return new EvaluatedFlag(
                def.flagKey(),
                def.category().name(),
                def.valueType().name(),
                false,
                def.defaultValueJson(),
                false,
                "ROLLOUT_EXCLUDED",
                now
            );
        }

        if (def.valueType() == FlagValueType.BOOLEAN) {
            boolean effective = Boolean.parseBoolean(def.currentValueJson().trim());
            return new EvaluatedFlag(
                def.flagKey(),
                def.category().name(),
                def.valueType().name(),
                effective,
                effective ? "true" : "false",
                false,
                effective ? "BUCKET_MATCH" : "VALUE_FALSE",
                now
            );
        }

        return new EvaluatedFlag(
            def.flagKey(),
            def.category().name(),
            def.valueType().name(),
            true,
            def.currentValueJson(),
            false,
            "BUCKET_MATCH",
            now
        );
    }

    /**
     * INV-FF-001: Strictly excludes any flag with clientSafe=false from client-visible remote config bundles.
     */
    public static List<FeatureFlagDefinitionSnapshot> filterClientSafeFlags(
        List<FeatureFlagDefinitionSnapshot> allFlags,
        int maxClientFlags
    ) {
        if (allFlags == null || allFlags.isEmpty() || maxClientFlags <= 0) {
            return List.of();
        }
        return allFlags.stream()
            .filter(FeatureFlagDefinitionSnapshot::clientSafe)
            .limit(maxClientFlags)
            .toList();
    }

    public static String validateAndNormalizeValue(String flagKey, FlagValueType valueType, String rawJson) {
        Objects.requireNonNull(valueType, "valueType must not be null");
        if (rawJson == null || rawJson.isBlank()) {
            throw validationFailed("currentValueJson must not be blank");
        }
        String trimmed = rawJson.trim();
        if (trimmed.getBytes(StandardCharsets.UTF_8).length > MAX_VALUE_JSON_BYTES) {
            throw validationFailed("currentValueJson exceeds 512 byte bound");
        }

        JsonNode node;
        try {
            node = JSON_MAPPER.readTree(trimmed);
        } catch (Exception exception) {
            throw validationFailed("currentValueJson must be valid JSON matching type " + valueType);
        }
        if (node == null || node.isNull()) {
            throw validationFailed("currentValueJson must not be JSON null");
        }

        return switch (valueType) {
            case BOOLEAN -> {
                if (!node.isBoolean()) {
                    throw validationFailed("Flag '" + flagKey + "' expects BOOLEAN JSON value");
                }
                yield node.asBoolean() ? "true" : "false";
            }
            case INTEGER -> {
                if (!node.isIntegralNumber()) {
                    throw validationFailed("Flag '" + flagKey + "' expects INTEGER JSON value");
                }
                long value = node.asLong();
                validateIntegerBounds(flagKey, value);
                yield Long.toString(value);
            }
            case DECIMAL -> {
                if (!node.isNumber()) {
                    throw validationFailed("Flag '" + flagKey + "' expects DECIMAL JSON value");
                }
                BigDecimal decimal = node.decimalValue();
                yield decimal.toPlainString();
            }
            case STRING -> {
                if (!node.isTextual() || node.asText().length() > 128) {
                    throw validationFailed("Flag '" + flagKey + "' expects JSON string (max 128 chars)");
                }
                try {
                    yield JSON_MAPPER.writeValueAsString(node.asText());
                } catch (Exception exception) {
                    throw validationFailed("Unable to canonicalize JSON string value");
                }
            }
        };
    }

    private static void validateIntegerBounds(String flagKey, long value) {
        if ("strong_model_pct".equals(flagKey) || "proprietary_canary_pct".equals(flagKey)) {
            if (value < 0 || value > 100) {
                throw validationFailed("Flag '" + flagKey + "' integer value must be in [0, 100]");
            }
        } else if ("daily_solve_quota_override".equals(flagKey)) {
            if (value < 1 || value > 50) {
                throw validationFailed("Flag 'daily_solve_quota_override' integer value must be in [1, 50]");
            }
        }
    }

    public static String validateReason(String reason) {
        if (reason == null) {
            throw validationFailed("reason is required (5..240 characters)");
        }
        String trimmed = reason.trim();
        if (trimmed.length() < MIN_REASON_LEN || trimmed.length() > MAX_REASON_LEN) {
            throw validationFailed("reason must be between 5 and 240 characters");
        }
        return trimmed;
    }

    public static String validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                "Idempotency-Key header is required",
                false,
                "SUPPLY_IDEMPOTENCY_KEY"
            );
        }
        String trimmed = idempotencyKey.trim();
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(trimmed).matches()) {
            throw validationFailed("Idempotency-Key must match ^[A-Za-z0-9:._-]{8,128}$");
        }
        return trimmed;
    }

    public static String sha256Hex(String value) {
        Objects.requireNonNull(value, "value must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static ApiProblemException validationFailed(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.FEATURE_FLAG_VALIDATION_FAILED,
            message,
            false,
            "FIX_FEATURE_FLAG_INPUT"
        );
    }

    public record EvaluatedFlag(
        String flagKey,
        String category,
        String valueType,
        boolean enabled,
        String valueJson,
        boolean killSwitchActive,
        String reason,
        Instant evaluatedAt
    ) {
    }
}
