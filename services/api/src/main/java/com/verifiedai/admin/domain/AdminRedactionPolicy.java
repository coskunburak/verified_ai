package com.verifiedai.admin.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;

/**
 * Deterministic policy enforcing input validation, PII/secret filtering,
 * default redaction of raw student content, SHA-256 hashing, and elevated RBAC checks.
 */
public final class AdminRedactionPolicy {

    public static final String RAW_CONTENT_READ_AUTHORITY = "ADMIN_RAW_CONTENT_READ";
    public static final String ENGINEERING_ROLE_AUTHORITY = "ROLE_ADMIN_ENGINEERING";
    public static final String REDACTED_PLACEHOLDER = "[REDACTED]";

    private static final Pattern IDEMPOTENCY_PATTERN = Pattern.compile("^[A-Za-z0-9:._-]{8,128}$");
    private static final Pattern TICKET_REFERENCE_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,64}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern JWT_PATTERN = Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{4,}\\b");
    private static final Pattern SECRET_PREFIX_PATTERN = Pattern.compile("\\b(?:sk|pk|rk|api|bearer)[-_ ][A-Za-z0-9._-]{10,}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern S3_URI_PATTERN = Pattern.compile("s3://[^\\s]+", Pattern.CASE_INSENSITIVE);

    private static final Set<String> FORBIDDEN_MAP_KEYS = Set.of(
        "objectkey",
        "object_key",
        "rawoutputjsonb",
        "raw_output_jsonb",
        "normalizedproblemjsonb",
        "normalized_problem_jsonb",
        "rawjws",
        "raw_jws",
        "signedtransactioninfo",
        "signed_transaction_info",
        "signedpayload",
        "signed_payload",
        "tokenhash",
        "token_hash",
        "refreshtoken",
        "refresh_token",
        "accesstoken",
        "access_token"
    );

    private AdminRedactionPolicy() {
    }

    public static String validateIdempotencyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                "Idempotency-Key header is required",
                false,
                "PROVIDE_IDEMPOTENCY_KEY"
            );
        }
        String trimmed = rawKey.trim();
        if (!IDEMPOTENCY_PATTERN.matcher(trimmed).matches()) {
            throw validationFailed("Idempotency-Key must match ^[A-Za-z0-9:._-]{8,128}$");
        }
        return trimmed;
    }

    public static String validateStandardReason(String rawReason) {
        return validateBoundedText(rawReason, "reason", 5, 240, true);
    }

    public static String validateElevatedGrantReason(String rawReason) {
        return validateBoundedText(rawReason, "reason", 10, 240, true);
    }

    public static String validateResolutionNote(String rawNote) {
        return validateBoundedText(rawNote, "resolutionNote", 5, 400, true);
    }

    public static String validateOptionalUserNote(String rawNote) {
        if (rawNote == null || rawNote.isBlank()) {
            return null;
        }
        return validateBoundedText(rawNote, "userNote", 1, 400, false);
    }

    public static String validateTicketReference(String rawTicket) {
        if (rawTicket == null || rawTicket.isBlank()) {
            throw validationFailed("ticketReference is required");
        }
        String trimmed = rawTicket.trim();
        if (!TICKET_REFERENCE_PATTERN.matcher(trimmed).matches()) {
            throw validationFailed("ticketReference must match ^[A-Za-z0-9._-]{3,64}$");
        }
        return trimmed;
    }

    public static int validateGrantTtlMinutes(Integer requestedTtl, int defaultTtlMinutes) {
        int ttl = requestedTtl == null ? defaultTtlMinutes : requestedTtl;
        if (ttl < 5 || ttl > 60) {
            throw validationFailed("ttlMinutes must be between 5 and 60");
        }
        return ttl;
    }

    public static void assertNoSensitivePatterns(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (EMAIL_PATTERN.matcher(text).find()
            || JWT_PATTERN.matcher(text).find()
            || SECRET_PREFIX_PATTERN.matcher(text).find()
            || S3_URI_PATTERN.matcher(text).find()) {
            throw validationFailed(fieldName + " must not contain raw emails, tokens, secrets, or storage URIs");
        }
    }

    public static String redactString(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = EMAIL_PATTERN.matcher(value).replaceAll(REDACTED_PLACEHOLDER);
        sanitized = JWT_PATTERN.matcher(sanitized).replaceAll(REDACTED_PLACEHOLDER);
        sanitized = SECRET_PREFIX_PATTERN.matcher(sanitized).replaceAll(REDACTED_PLACEHOLDER);
        sanitized = S3_URI_PATTERN.matcher(sanitized).replaceAll(REDACTED_PLACEHOLDER);
        return sanitized;
    }

    public static Map<String, Object> redactMap(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String normalizedKey = key.toLowerCase(java.util.Locale.ROOT);
            if (FORBIDDEN_MAP_KEYS.contains(normalizedKey)) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof String str) {
                sanitized.put(key, redactString(str));
            } else if (value instanceof Map<?, ?> nested) {
                Map<String, Object> nestedCast = new LinkedHashMap<>();
                for (Map.Entry<?, ?> nestedEntry : nested.entrySet()) {
                    if (nestedEntry.getKey() != null) {
                        nestedCast.put(String.valueOf(nestedEntry.getKey()), nestedEntry.getValue());
                    }
                }
                sanitized.put(key, redactMap(nestedCast));
            } else if (value instanceof List<?> list) {
                sanitized.put(key, list.stream()
                    .map(item -> item instanceof String s ? redactString(s) : item)
                    .toList());
            } else {
                sanitized.put(key, value);
            }
        }
        return sanitized;
    }

    public static boolean hasRawContentAccess(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return false;
        }
        for (GrantedAuthority authority : authorities) {
            String name = authority.getAuthority();
            if (RAW_CONTENT_READ_AUTHORITY.equals(name) || ENGINEERING_ROLE_AUTHORITY.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static String extractActorRole(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return "ROLE_ADMIN";
        }
        if (hasRawContentAccess(authorities)) {
            for (GrantedAuthority authority : authorities) {
                if (ENGINEERING_ROLE_AUTHORITY.equals(authority.getAuthority())) {
                    return ENGINEERING_ROLE_AUTHORITY;
                }
            }
            return "ROLE_ADMIN+RAW_CONTENT";
        }
        for (GrantedAuthority authority : authorities) {
            String name = authority.getAuthority();
            if (name != null && name.startsWith("ROLE_ADMIN")) {
                return name.length() > 48 ? name.substring(0, 48) : name;
            }
        }
        return "ROLE_ADMIN";
    }

    public static String sha256Hex(String canonicalInput) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalInput.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String validateBoundedText(
        String raw,
        String fieldName,
        int minLength,
        int maxLength,
        boolean required
    ) {
        if (raw == null || raw.isBlank()) {
            if (required) {
                throw validationFailed(fieldName + " is required");
            }
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() < minLength || trimmed.length() > maxLength) {
            throw validationFailed(fieldName + " must be between " + minLength + " and " + maxLength + " characters");
        }
        assertNoSensitivePatterns(trimmed, fieldName);
        return trimmed;
    }

    private static ApiProblemException validationFailed(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.ADMIN_VALIDATION_FAILED,
            message,
            false,
            "FIX_ADMIN_REQUEST"
        );
    }
}
