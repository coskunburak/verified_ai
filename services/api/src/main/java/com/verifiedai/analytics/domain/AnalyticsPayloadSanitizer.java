package com.verifiedai.analytics.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Privacy sanitizer, structural validator, and deterministic SHA-256 fingerprint calculator
 * enforcing INV-ANL-001 through INV-ANL-004 and ADR-006.
 */
public final class AnalyticsPayloadSanitizer {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private static final Pattern CLIENT_EVENT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9:._-]{8,128}$");
    private static final Pattern APP_VERSION_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,32}$");
    private static final Pattern SHA256_HEX_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern PROPERTY_KEY_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{0,47}$");

    private static final Pattern EMAIL_VALUE_PATTERN =
        Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern JWT_VALUE_PATTERN =
        Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");
    private static final Pattern URL_VALUE_PATTERN =
        Pattern.compile("https?://", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAW_LATEX_VALUE_PATTERN =
        Pattern.compile("\\\\(?:frac|int|sqrt|sum|lim|prod|begin|end)\\b|\\$\\$");
    private static final Pattern BASE64_BLOB_PATTERN =
        Pattern.compile("^[A-Za-z0-9+/=_-]{129,}$");

    private static final Set<String> ALLOWED_SOURCE_MODULES = Set.of(
        "ios_client", "analytics", "identity", "profile", "problem", "solving",
        "verification", "tutoring", "attempt", "mistake", "mastery",
        "studyplan", "exam", "billing", "notification"
    );

    private static final Set<String> ALLOWED_TIERS = Set.of(
        "FREE", "PRO", "COACH_PRO", "YKS_SEASON_PASS", "UNKNOWN"
    );

    private static final Set<String> ALLOWED_LOCALES = Set.of("en", "tr");

    private static final Set<String> FORBIDDEN_EXACT_NORMALIZED_KEYS = Set.of(
        "email", "studentemail", "emailaddress",
        "phone", "phonenumber", "mobile",
        "studentname", "fullname", "firstname", "lastname", "username",
        "devicetoken", "pushtoken", "token", "accesstoken", "refreshtoken",
        "jwt", "authorization", "cookie", "password", "secret", "jws", "signedpayload",
        "rawproblemtext", "problemtext", "ocrtext", "recognizedtext",
        "latex", "rawlatex", "solutionsteps", "prompt", "systemprompt", "rawprovideroutput",
        "imagebase64", "imagebytes", "pdfbytes",
        "objectkey", "storagekey", "presignedurl", "signedurl", "filename", "localpath",
        "boundingbox", "checksum",
        "exactmasteryfloat", "masteryscore"
    );

    private static final List<String> FORBIDDEN_KEY_TOKENS = List.of(
        "email", "phone", "password", "secret", "jwt", "token", "authorization",
        "signedpayload", "rawproblem", "problemtext", "ocrtext", "recognizedtext",
        "rawlatex", "latex", "solutionsteps", "systemprompt", "rawprovider",
        "imagebase64", "imagebytes", "pdfbytes", "objectkey", "storagekey",
        "presignedurl", "signedurl", "localpath", "boundingbox", "checksum",
        "exactmastery", "masteryscore"
    );

    private static final int MAX_PROPERTY_VALUE_LENGTH = 80;
    private static final long MAX_LATENCY_MS = 600_000L;

    private final int maxPropertyCount;
    private final int maxPropertiesBytes;

    public AnalyticsPayloadSanitizer(int maxPropertyCount, int maxPropertiesBytes) {
        this.maxPropertyCount = maxPropertyCount;
        this.maxPropertiesBytes = maxPropertiesBytes;
    }

    public SanitizedAnalyticsEvent sanitize(
        RawEventInput input,
        AnalyticsEventSource expectedSource,
        String resolvedTierFallback
    ) {
        if (input == null) {
            throw validationFailed("Analytics event payload is required");
        }
        AnalyticsEventSpec spec = AnalyticsEventCatalog.requireEvent(input.eventName());
        AnalyticsEventCatalog.requireSourceAllowed(spec, expectedSource);

        if (input.emittedBy() != null && !input.emittedBy().isBlank()) {
            String normalizedEmittedBy = input.emittedBy().trim().toUpperCase(Locale.ROOT);
            if (!expectedSource.name().equals(normalizedEmittedBy)) {
                throw validationFailed(
                    "Analytics event emittedBy '" + input.emittedBy() + "' violates " + expectedSource.name() + " authority"
                );
            }
        }
        if (Boolean.TRUE.equals(input.trainingEligible())) {
            throw validationFailed("Analytics events are never training eligible (ADR-006)");
        }

        String clientEventId = requireClientEventId(input.clientEventId());
        String sourceModule = validateSourceModule(input.sourceModule(), expectedSource);
        String entitlementTier = validateEntitlementTier(input.entitlementTier(), resolvedTierFallback);
        String locale = validateLocale(input.locale());
        String appVersion = validateAppVersion(input.appVersion());
        String sessionRefHash = validateSessionRefHash(input.sessionRefHash());
        Integer latencyMs = validateLatencyMs(input.latencyMs());
        long aiCostMicros = expectedSource == AnalyticsEventSource.CLIENT
            ? 0L
            : validateAiCostMicros(input.aiCostMicros());

        if (input.occurredAt() == null) {
            throw validationFailed("Analytics event occurredAt is required");
        }
        Instant occurredAt = input.occurredAt();

        String canonicalPropertiesJson = sanitizeProperties(spec, input.properties());
        String payloadHash = computePayloadHash(
            spec.eventName(),
            spec.domain().name(),
            expectedSource.name(),
            entitlementTier,
            occurredAt,
            canonicalPropertiesJson
        );

        return new SanitizedAnalyticsEvent(
            clientEventId,
            payloadHash,
            spec.eventName(),
            spec.domain(),
            AnalyticsEventCatalog.CATALOG_VERSION,
            expectedSource,
            sourceModule,
            entitlementTier,
            locale,
            appVersion,
            sessionRefHash,
            latencyMs,
            aiCostMicros,
            canonicalPropertiesJson,
            occurredAt
        );
    }

    public String sanitizeProperties(AnalyticsEventSpec spec, Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }
        if (properties.size() > maxPropertyCount) {
            throw validationFailed("Analytics event properties exceed maximum count of " + maxPropertyCount);
        }

        TreeMap<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || key.isBlank()) {
                throw validationFailed("Analytics property key must not be blank");
            }
            String trimmedKey = key.trim();
            checkForbiddenKey(trimmedKey);

            if (!PROPERTY_KEY_PATTERN.matcher(trimmedKey).matches()) {
                throw validationFailed("Analytics property key format is invalid: " + trimmedKey);
            }
            if (!spec.allowedPropertyKeys().contains(trimmedKey)) {
                throw validationFailed(
                    "Property key '" + trimmedKey + "' is not allowlisted for event '" + spec.eventName() + "'"
                );
            }
            if (value == null || value instanceof Map<?, ?> || value instanceof Collection<?>) {
                throw validationFailed("Analytics property '" + trimmedKey + "' must be a non-null scalar");
            }

            Object normalizedValue = normalizeAndCheckValue(trimmedKey, value);
            sorted.put(trimmedKey, normalizedValue);
        }

        try {
            String json = JSON_MAPPER.writeValueAsString(sorted);
            if (json.getBytes(StandardCharsets.UTF_8).length > maxPropertiesBytes) {
                throw validationFailed("Serialized analytics properties exceed " + maxPropertiesBytes + " bytes");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw validationFailed("Analytics properties could not be serialized");
        }
    }

    private static void checkForbiddenKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (FORBIDDEN_EXACT_NORMALIZED_KEYS.contains(normalized)) {
            throw piiRejected("Forbidden PII or raw-content analytics property key rejected");
        }
        for (String token : FORBIDDEN_KEY_TOKENS) {
            if (normalized.contains(token)) {
                throw piiRejected("Forbidden PII or raw-content analytics property key rejected");
            }
        }
    }

    private static Object normalizeAndCheckValue(String key, Object value) {
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            return value;
        }
        if (value instanceof Double d) {
            if (!Double.isFinite(d)) {
                throw validationFailed("Analytics numeric property must be finite: " + key);
            }
            return d;
        }
        if (value instanceof Float f) {
            if (!Float.isFinite(f)) {
                throw validationFailed("Analytics numeric property must be finite: " + key);
            }
            return f.doubleValue();
        }
        if (value instanceof Number) {
            return value;
        }
        if (!(value instanceof String str)) {
            throw validationFailed("Analytics property '" + key + "' must be a string, number, or boolean");
        }
        if (BASE64_BLOB_PATTERN.matcher(str).matches() || str.length() > MAX_PROPERTY_VALUE_LENGTH) {
            throw piiRejected("Analytics property value exceeds 80-character low-cardinality bound");
        }
        if (EMAIL_VALUE_PATTERN.matcher(str).find()
            || JWT_VALUE_PATTERN.matcher(str).find()
            || URL_VALUE_PATTERN.matcher(str).find()
            || RAW_LATEX_VALUE_PATTERN.matcher(str).find()) {
            throw piiRejected("Forbidden PII, URL, JWT, or raw LaTeX detected in analytics property value");
        }
        return str;
    }

    public static String computePayloadHash(
        String eventName,
        String domain,
        String emittedBy,
        String entitlementTier,
        Instant occurredAt,
        String canonicalPropertiesJson
    ) {
        String canonical = String.join(
            "|",
            eventName,
            domain,
            emittedBy,
            entitlementTier,
            Long.toString(occurredAt.getEpochSecond()),
            canonicalPropertiesJson
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

    private static String requireClientEventId(String clientEventId) {
        if (clientEventId == null || !CLIENT_EVENT_ID_PATTERN.matcher(clientEventId.trim()).matches()) {
            throw validationFailed("clientEventId must match ^[A-Za-z0-9:._-]{8,128}$");
        }
        return clientEventId.trim();
    }

    private static String validateSourceModule(String sourceModule, AnalyticsEventSource expectedSource) {
        if (sourceModule == null || sourceModule.isBlank()) {
            return expectedSource == AnalyticsEventSource.CLIENT ? "ios_client" : "analytics";
        }
        String trimmed = sourceModule.trim();
        if (expectedSource == AnalyticsEventSource.CLIENT && !"ios_client".equals(trimmed)) {
            throw validationFailed("Client analytics events must use sourceModule 'ios_client'");
        }
        if (!ALLOWED_SOURCE_MODULES.contains(trimmed)) {
            throw validationFailed("Unsupported analytics sourceModule: " + trimmed);
        }
        return trimmed;
    }

    private static String validateEntitlementTier(String entitlementTier, String resolvedFallback) {
        String candidate = (entitlementTier == null || entitlementTier.isBlank())
            ? resolvedFallback
            : entitlementTier.trim().toUpperCase(Locale.ROOT);
        if (candidate == null || candidate.isBlank()) {
            return "FREE";
        }
        if (!ALLOWED_TIERS.contains(candidate)) {
            throw validationFailed("Unsupported analytics entitlementTier: " + candidate);
        }
        return candidate;
    }

    private static String validateLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "en";
        }
        String normalized = locale.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_LOCALES.contains(normalized)) {
            throw validationFailed("Analytics locale must be 'en' or 'tr'");
        }
        return normalized;
    }

    private static String validateAppVersion(String appVersion) {
        if (appVersion == null || appVersion.isBlank()) {
            return null;
        }
        String trimmed = appVersion.trim();
        if (!APP_VERSION_PATTERN.matcher(trimmed).matches()) {
            throw validationFailed("appVersion must match ^[A-Za-z0-9._-]{1,32}$");
        }
        return trimmed;
    }

    private static String validateSessionRefHash(String sessionRefHash) {
        if (sessionRefHash == null || sessionRefHash.isBlank()) {
            return null;
        }
        String normalized = sessionRefHash.trim().toLowerCase(Locale.ROOT);
        if (!SHA256_HEX_PATTERN.matcher(normalized).matches()) {
            throw validationFailed("sessionRefHash must be a 64-character lowercase hex SHA-256 digest");
        }
        return normalized;
    }

    private static Integer validateLatencyMs(Integer latencyMs) {
        if (latencyMs == null) {
            return null;
        }
        if (latencyMs < 0 || latencyMs > MAX_LATENCY_MS) {
            throw validationFailed("latencyMs must be between 0 and 600000");
        }
        return latencyMs;
    }

    private static long validateAiCostMicros(long aiCostMicros) {
        if (aiCostMicros < 0L) {
            throw validationFailed("aiCostMicros must be non-negative");
        }
        return aiCostMicros;
    }

    private static ApiProblemException validationFailed(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.ANALYTICS_EVENT_VALIDATION_FAILED,
            message,
            false,
            "FIX_EVENT_PAYLOAD"
        );
    }

    private static ApiProblemException piiRejected(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.ANALYTICS_PII_OR_RAW_CONTENT_REJECTED,
            message,
            false,
            "STRIP_PII_AND_RAW_CONTENT"
        );
    }

    public record RawEventInput(
        String clientEventId,
        String eventName,
        String emittedBy,
        String sourceModule,
        String entitlementTier,
        String locale,
        String appVersion,
        String sessionRefHash,
        Integer latencyMs,
        long aiCostMicros,
        Map<String, Object> properties,
        Instant occurredAt,
        Boolean trainingEligible
    ) {
    }

    public record SanitizedAnalyticsEvent(
        String clientEventId,
        String payloadHash,
        String eventName,
        AnalyticsEventDomain eventDomain,
        String catalogVersion,
        AnalyticsEventSource emittedBy,
        String sourceModule,
        String entitlementTier,
        String locale,
        String appVersion,
        String sessionRefHash,
        Integer latencyMs,
        long aiCostMicros,
        String canonicalPropertiesJson,
        Instant occurredAt
    ) {
        public SanitizedAnalyticsEvent {
            Objects.requireNonNull(clientEventId, "clientEventId must not be null");
            Objects.requireNonNull(payloadHash, "payloadHash must not be null");
            Objects.requireNonNull(eventName, "eventName must not be null");
            Objects.requireNonNull(eventDomain, "eventDomain must not be null");
            Objects.requireNonNull(emittedBy, "emittedBy must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }
}
