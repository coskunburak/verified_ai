package com.verifiedai.notification.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Canonical payload validator, PII minimizer, and SHA-256 fingerprint calculator (INV-NOTIF-003, INV-NOTIF-005).
 */
public final class NotificationPayloadHasher {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9:._-]{8,128}$");
    private static final Pattern PARAM_KEY_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{0,47}$");
    private static final Pattern DEEP_LINK_PATTERN = Pattern.compile("^verified-ai://[a-z0-9/_-]{1,100}$");
    private static final Set<String> SUPPORTED_LOCALES = Set.of("en", "tr");
    private static final Set<String> ALLOWED_SOURCE_MODULES =
        Set.of("notification", "billing", "studyplan", "exam", "identity");
    private static final Set<String> FORBIDDEN_PII_KEYS = Set.of(
        "email",
        "emailaddress",
        "phone",
        "phonenumber",
        "mobile",
        "devicetoken",
        "pushtoken",
        "token",
        "rawproblemtext",
        "problemtext",
        "studentname",
        "fullname",
        "firstname",
        "lastname",
        "username",
        "jwt",
        "accesstoken",
        "refreshtoken",
        "password",
        "secret",
        "address",
        "ssn",
        "tckn",
        "nationalid",
        "ipaddress",
        "authorization",
        "cookie"
    );
    private static final int MAX_TEMPLATE_PARAMS_COUNT = 8;
    private static final int MAX_TEMPLATE_PARAM_VALUE_LENGTH = 80;
    private static final int MAX_TEMPLATE_PARAMS_JSON_BYTES = 2048;

    private NotificationPayloadHasher() {
    }

    public static void validateScheduleFields(
        String idempotencyKey,
        String locale,
        String templateKey,
        Map<String, String> templateParams,
        String deepLinkPath,
        String sourceModule,
        String correlationId
    ) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw validationFailed("Notification idempotencyKey must match ^[A-Za-z0-9:._-]{8,128}$");
        }
        if (locale == null || !SUPPORTED_LOCALES.contains(locale)) {
            throw validationFailed("Notification locale must be en or tr");
        }
        if (templateKey == null || templateKey.isBlank() || templateKey.trim().length() > 128) {
            throw validationFailed("Notification templateKey must be non-blank and at most 128 characters");
        }
        if (deepLinkPath == null || !DEEP_LINK_PATTERN.matcher(deepLinkPath).matches()) {
            throw validationFailed("Notification deepLinkPath must match ^verified-ai://[a-z0-9/_-]{1,100}$");
        }
        if (sourceModule == null || !ALLOWED_SOURCE_MODULES.contains(sourceModule)) {
            throw validationFailed("Unsupported notification sourceModule");
        }
        if (correlationId == null || correlationId.isBlank() || correlationId.length() > 64) {
            throw validationFailed("Notification correlationId must be non-blank and at most 64 characters");
        }
        canonicalizeParams(templateParams);
    }

    public static String canonicalizeParams(Map<String, String> templateParams) {
        if (templateParams == null || templateParams.isEmpty()) {
            return "{}";
        }
        if (templateParams.size() > MAX_TEMPLATE_PARAMS_COUNT) {
            throw validationFailed("Notification templateParams must contain at most 8 keys");
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : templateParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || !PARAM_KEY_PATTERN.matcher(key).matches()) {
                throw validationFailed("Notification template parameter key format is invalid");
            }
            String normalizedKey = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (FORBIDDEN_PII_KEYS.contains(normalizedKey)) {
                throw validationFailed("Forbidden PII or credential key in notification templateParams: " + key);
            }
            if (value == null || value.length() > MAX_TEMPLATE_PARAM_VALUE_LENGTH) {
                throw validationFailed("Notification template parameter value must be non-null and at most 80 characters");
            }
            sorted.put(key, value);
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(sorted);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_TEMPLATE_PARAMS_JSON_BYTES) {
                throw validationFailed("Notification templateParams JSON exceeds 2048 bytes");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw validationFailed("Failed to serialize notification templateParams");
        }
    }

    public static String computePayloadHash(
        NotificationCategory category,
        NotificationIntentType intentType,
        NotificationChannel channel,
        String locale,
        String templateKey,
        String canonicalParamsJson,
        String deepLinkPath,
        Instant scheduledFor,
        Instant expiresAt
    ) {
        String canonical = String.join(
            "|",
            category.name(),
            intentType.name(),
            channel.name(),
            locale,
            templateKey,
            canonicalParamsJson,
            deepLinkPath,
            Long.toString(scheduledFor.getEpochSecond()),
            Long.toString(expiresAt.getEpochSecond())
        );
        return sha256Hex(canonical);
    }

    public static String sha256Hex(String value) {
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
            ApiErrorCode.NOTIFICATION_VALIDATION_FAILED,
            message,
            true,
            "RETRY"
        );
    }
}
