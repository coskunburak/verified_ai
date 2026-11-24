package com.verifiedai.growth.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Deterministic sanitizer & payload fingerprint generator for Sprint 10.9 (INV-GRW-001 & INV-GRW-003).
 * Rejects PII, URLs, JWTs, LaTeX/raw student work, forbidden keys, and stigmatizing lexicon fail-closed.
 */
public final class GrowthContentSanitizer {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        .build();

    private static final Pattern IDEMPOTENCY_PATTERN = Pattern.compile("^[A-Za-z0-9:._-]{8,128}$");
    private static final Pattern SHARE_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{16,48}$");
    private static final Pattern SHA256_HEX_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern DISPLAY_ALIAS_PATTERN = Pattern.compile("^[A-Za-z0-9ÇĞİÖŞÜçğıöşü ._'-]{2,24}$");
    private static final Pattern SUMMARY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,40}$");
    private static final Pattern APP_VERSION_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,32}$");

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?:\\+?\\d[\\d\\s().-]{7,}\\d)");
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)(https?://|www\\.|verified-ai://)");
    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}");
    private static final Pattern LATEX_PATTERN = Pattern.compile("(\\\\[a-zA-Z]+|\\$\\$|\\\\[\\(\\)\\[\\]])");

    private static final Set<String> BANNED_STIGMATIZING_TERMS = Set.of(
        "overdue", "behind", "fail", "failure", "failed", "punish", "penalty", "shame", "guilt",
        "slacker", "lazy", "weak", "weakness", "stupid", "dumb", "incompetent", "hopeless",
        "quitter", "broken streak", "lost streak", "hurry", "last chance",
        "kaybettin", "bozuldu", "ceza", "utanç", "tembel", "başarısız", "yetersiz"
    );

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
        "userid", "user_id", "email", "phone", "objectkey", "object_key",
        "rawproblemtext", "raw_problem_text", "ocrtext", "ocr_text",
        "solutionsteps", "solution_steps", "sessionid", "session_id",
        "idempotencykey", "idempotency_key", "devicefingerprinthash", "device_fingerprint_hash",
        "token", "jwt", "password"
    );

    private static final Map<ShareableArtifactType, Set<String>> ALLOWED_SUMMARY_KEYS = new EnumMap<>(ShareableArtifactType.class);

    static {
        ALLOWED_SUMMARY_KEYS.put(
            ShareableArtifactType.WEEKLY_PROGRESS_SUMMARY,
            Set.of("weekIso", "verifiedSessionsCount", "activeDaysCount", "topSkillDomain", "milestoneBadge")
        );
        ALLOWED_SUMMARY_KEYS.put(
            ShareableArtifactType.MASTERY_MILESTONE,
            Set.of("skillCode", "skillDomain", "milestoneBadge", "conqueredTrapCount")
        );
        ALLOWED_SUMMARY_KEYS.put(
            ShareableArtifactType.COUNTERFACTUAL_CHALLENGE_CARD,
            Set.of("mistakeCode", "skillCode", "challengeSummaryKey", "locale", "difficultyBand")
        );
        ALLOWED_SUMMARY_KEYS.put(
            ShareableArtifactType.EXAM_READINESS_MILESTONE,
            Set.of("examCode", "confidenceTier", "readinessBand", "milestoneBadge")
        );
    }

    private GrowthContentSanitizer() {
    }

    public static String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_PATTERN.matcher(idempotencyKey.trim()).matches()) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "Idempotency-Key header is required and must match ^[A-Za-z0-9:._-]{8,128}$",
                false,
                "FIX_IDEMPOTENCY_KEY"
            );
        }
        return idempotencyKey.trim();
    }

    public static String validateShareToken(String shareToken) {
        if (shareToken == null || !SHARE_TOKEN_PATTERN.matcher(shareToken).matches()) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "shareToken format is invalid",
                false,
                "CHECK_SHARE_LINK"
            );
        }
        return shareToken;
    }

    public static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "en";
        }
        String normalized = locale.trim().toLowerCase(Locale.ROOT);
        if (!"en".equals(normalized) && !"tr".equals(normalized)) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "locale must be 'en' or 'tr'",
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
        return normalized;
    }

    public static String normalizeDeviceFingerprintHash(String rawHash) {
        if (rawHash == null || rawHash.isBlank()) {
            return null;
        }
        String normalized = rawHash.trim().toLowerCase(Locale.ROOT);
        if (!SHA256_HEX_PATTERN.matcher(normalized).matches()) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "deviceFingerprintHash must be a 64-character lowercase SHA-256 hex digest",
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
        return normalized;
    }

    public static String validateAppVersion(String appVersion) {
        if (appVersion == null || !APP_VERSION_PATTERN.matcher(appVersion.trim()).matches()) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "appVersion must be 1..32 valid version characters",
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
        return appVersion.trim();
    }

    public static String sanitizeDisplayAlias(String rawAlias) {
        if (rawAlias == null || rawAlias.isBlank()) {
            return null;
        }
        String trimmed = rawAlias.trim();
        if (!DISPLAY_ALIAS_PATTERN.matcher(trimmed).matches()) {
            throw rejectPiiOrRaw("displayAlias violates safe format bounds");
        }
        assertSafeText("displayAlias", trimmed);
        return trimmed;
    }

    public static Map<String, Object> sanitizeSummaryPayload(
        ShareableArtifactType artifactType,
        Map<String, Object> rawSummary
    ) {
        if (rawSummary == null || rawSummary.isEmpty()) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "summaryPayload must be a non-empty object",
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
        Set<String> allowedKeys = ALLOWED_SUMMARY_KEYS.getOrDefault(artifactType, Set.of());
        Map<String, Object> sanitized = new LinkedHashMap<>();

        for (String key : new TreeMap<>(rawSummary).keySet()) {
            if (key == null || !SUMMARY_KEY_PATTERN.matcher(key).matches()) {
                throw rejectPiiOrRaw("Invalid summaryPayload key format");
            }
            if (FORBIDDEN_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                throw rejectPiiOrRaw("Forbidden PII or raw-content key in summaryPayload: " + key);
            }
            if (!allowedKeys.contains(key)) {
                throw rejectPiiOrRaw("Disallowed summaryPayload key for " + artifactType + ": " + key);
            }
            Object value = rawSummary.get(key);
            if (value == null) {
                throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.GROWTH_VALIDATION_FAILED,
                    "summaryPayload field '" + key + "' cannot be null",
                    false,
                    "FIX_GROWTH_REQUEST"
                );
            }
            if (value instanceof String strValue) {
                String trimmed = strValue.trim();
                if (trimmed.isEmpty() || trimmed.length() > 120) {
                    throw rejectPiiOrRaw("summaryPayload string field '" + key + "' out of bounds");
                }
                assertSafeText(key, trimmed);
                sanitized.put(key, trimmed);
            } else if (value instanceof Integer || value instanceof Long) {
                long num = ((Number) value).longValue();
                if (num < 0 || num > 100_000) {
                    throw new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.GROWTH_VALIDATION_FAILED,
                        "summaryPayload numeric field '" + key + "' out of bounds",
                        false,
                        "FIX_GROWTH_REQUEST"
                    );
                }
                sanitized.put(key, num);
            } else if (value instanceof Boolean boolVal) {
                sanitized.put(key, boolVal);
            } else {
                throw rejectPiiOrRaw("Unsupported nested or raw value type for key '" + key + "'");
            }
        }

        if (sanitized.isEmpty()) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "summaryPayload must retain at least one allowed field",
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
        String serialized = toCanonicalJson(sanitized);
        if (serialized.getBytes(StandardCharsets.UTF_8).length > 2048) {
            throw rejectPiiOrRaw("summaryPayload exceeds 2048-byte limit");
        }
        return Collections.unmodifiableMap(sanitized);
    }

    private static void assertSafeText(String fieldName, String value) {
        if (EMAIL_PATTERN.matcher(value).find()
            || PHONE_PATTERN.matcher(value).find()
            || URL_PATTERN.matcher(value).find()
            || JWT_PATTERN.matcher(value).find()
            || LATEX_PATTERN.matcher(value).find()) {
            throw rejectPiiOrRaw("PII, URL, JWT, or raw LaTeX detected in " + fieldName);
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String term : BANNED_STIGMATIZING_TERMS) {
            if (containsWord(lower, term)) {
                throw rejectPiiOrRaw("Disallowed term detected in " + fieldName);
            }
        }
    }

    private static boolean containsWord(String text, String term) {
        if (term.contains(" ")) {
            return text.contains(term);
        }
        int idx = text.indexOf(term);
        while (idx >= 0) {
            boolean startOk = idx == 0 || !Character.isLetterOrDigit(text.charAt(idx - 1));
            int endIdx = idx + term.length();
            boolean endOk = endIdx >= text.length() || !Character.isLetterOrDigit(text.charAt(endIdx));
            if (startOk && endOk) {
                return true;
            }
            idx = text.indexOf(term, idx + 1);
        }
        return false;
    }

    private static ApiProblemException rejectPiiOrRaw(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.GROWTH_PII_OR_RAW_CONTENT_REJECTED,
            message,
            false,
            "REMOVE_PII_OR_RAW_CONTENT"
        );
    }

    public static String toCanonicalJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize canonical growth JSON", ex);
        }
    }

    public static Map<String, Object> parseSummaryJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = JSON_MAPPER.readValue(json, LinkedHashMap.class);
            return Collections.unmodifiableMap(parsed);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse persisted growth summary_payload_json", ex);
        }
    }

    public static String sha256Hex(Object canonicalPayload) {
        return sha256HexString(toCanonicalJson(canonicalPayload));
    }

    public static String sha256HexString(String canonicalText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonicalText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
