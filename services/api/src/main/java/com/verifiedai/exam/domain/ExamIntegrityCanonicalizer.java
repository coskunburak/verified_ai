package com.verifiedai.exam.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Deterministic canonicalizer for Sprint 9.10 {@code exam-submit-hmac-v1} wire payloads (INV-EXAM-006).
 * Matches the Swift {@code ExamIntegrityCanonicalSigner} byte-for-byte over UTF-8 LF-delimited fields.
 */
public final class ExamIntegrityCanonicalizer {

    public static final String PROTOCOL_V1 = "exam-submit-hmac-v1";
    public static final DateTimeFormatter CANONICAL_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private static final Pattern KEY_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    private static final Pattern NONCE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{16,128}$");
    private static final Pattern SIGNATURE_HEX_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final HexFormat HEX = HexFormat.of();

    private ExamIntegrityCanonicalizer() {
    }

    public static String formatCanonicalInstant(Instant instant) {
        Objects.requireNonNull(instant, "instant is required");
        return CANONICAL_TIMESTAMP_FORMATTER.format(instant.truncatedTo(ChronoUnit.MILLIS));
    }

    public static String normalizeSubmitPath(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        return "/v1/exams/sessions/" + sessionId.toString().toLowerCase(Locale.ROOT) + "/submit";
    }

    public static String normalizeSubmitPath(String rawPath, UUID sessionId) {
        String expected = normalizeSubmitPath(sessionId);
        if (rawPath == null || rawPath.isBlank()) {
            return expected;
        }
        String trimmed = rawPath.trim();
        if (trimmed.equalsIgnoreCase(expected)
                || trimmed.equalsIgnoreCase("/api" + expected)) {
            return expected;
        }
        throw invalidPayload("Invalid submission endpoint path for canonicalization");
    }

    public static String buildCanonicalSubmitBody(String normalizedSubmissionReason, Instant clientTimestamp) {
        validateSafeToken(normalizedSubmissionReason, "submissionReason", 1, 48);
        if (!ExamSession.ALLOWED_SUBMISSION_REASONS.contains(normalizedSubmissionReason)) {
            throw invalidPayload("Unsupported submissionReason");
        }
        String canonicalTs = formatCanonicalInstant(clientTimestamp);
        return "{\"clientTimestamp\":\"" + canonicalTs
                + "\",\"submissionReason\":\"" + normalizedSubmissionReason
                + "\",\"trainingEligible\":false}";
    }

    public static String computeCanonicalBodySha256Hex(String normalizedSubmissionReason, Instant clientTimestamp) {
        return sha256Hex(buildCanonicalSubmitBody(normalizedSubmissionReason, clientTimestamp));
    }

    public static String buildCanonicalSigningString(
            UUID userId,
            UUID sessionId,
            String normalizedSubmissionReason,
            String idempotencyKey,
            Instant clientTimestamp,
            String integrityKeyId,
            String submissionNonce
    ) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        validateIdempotencyKey(idempotencyKey);
        validateKeyId(integrityKeyId);
        validateNonce(submissionNonce);

        String canonicalTs = formatCanonicalInstant(clientTimestamp);
        String bodySha256 = computeCanonicalBodySha256Hex(normalizedSubmissionReason, clientTimestamp);
        String userLower = userId.toString().toLowerCase(Locale.ROOT);
        String sessionLower = sessionId.toString().toLowerCase(Locale.ROOT);

        return PROTOCOL_V1 + "\n"
                + "POST\n"
                + normalizeSubmitPath(sessionId) + "\n"
                + userLower + "\n"
                + sessionLower + "\n"
                + normalizedSubmissionReason + "\n"
                + idempotencyKey.trim() + "\n"
                + canonicalTs + "\n"
                + integrityKeyId.trim() + "\n"
                + submissionNonce.trim() + "\n"
                + bodySha256 + "\n";
    }

    public static void validateIdempotencyKey(String idempotencyKey) {
        validateSafeToken(idempotencyKey, "Idempotency-Key", 3, 128);
        String trimmed = idempotencyKey.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch < 0x21 || ch > 0x7E) {
                throw invalidPayload("Idempotency-Key must contain printable non-whitespace ASCII characters");
            }
        }
    }

    public static void validateKeyId(String integrityKeyId) {
        if (integrityKeyId == null || !KEY_ID_PATTERN.matcher(integrityKeyId.trim()).matches()) {
            throw invalidPayload("integrityKeyId is invalid");
        }
    }

    public static void validateNonce(String submissionNonce) {
        if (submissionNonce == null || !NONCE_PATTERN.matcher(submissionNonce.trim()).matches()) {
            throw invalidPayload("submissionNonce must be 16..128 URL-safe ASCII characters");
        }
    }

    public static void validateSignatureHex(String submissionSignature) {
        if (submissionSignature == null || !SIGNATURE_HEX_PATTERN.matcher(submissionSignature.trim()).matches()) {
            throw invalidPayload("submissionSignature must be a 64-character lowercase hex string");
        }
    }

    /**
     * Server-side digest binding authoritative saved items state at submission time (non-client-dictated).
     */
    public static String computeSavedItemsStateDigest(ExamSession session) {
        Objects.requireNonNull(session, "session is required");
        StringBuilder sb = new StringBuilder();
        sb.append(session.id().toString().toLowerCase(Locale.ROOT))
                .append('|').append(session.userId().toString().toLowerCase(Locale.ROOT))
                .append('|').append(session.totalItemCount())
                .append('\n');
        session.items().stream()
                .sorted(Comparator.comparingInt(ExamSessionItem::itemPosition))
                .forEach(item -> sb
                        .append(item.itemPosition()).append(':')
                        .append(item.responseState().name()).append(':')
                        .append(item.selectedOptionKey() == null ? "-" : item.selectedOptionKey()).append(':')
                        .append(item.numericResponseValue() == null ? "-" : item.numericResponseValue().toPlainString()).append(':')
                        .append(item.autosaveVersion()).append(':')
                        .append(item.scratchpadAssets().size()).append('\n'));
        return sha256Hex(sb.toString());
    }

    public static String sha256Hex(String input) {
        Objects.requireNonNull(input, "input is required");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static void validateSafeToken(String value, String fieldName, int minLen, int maxLen) {
        if (value == null) {
            throw invalidPayload(fieldName + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() < minLen || trimmed.length() > maxLen) {
            throw invalidPayload(fieldName + " length out of bounds");
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 0x20 || ch == 0x7F) {
                throw invalidPayload(fieldName + " contains forbidden control characters");
            }
        }
    }

    private static ApiProblemException invalidPayload(String message) {
        return new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.EXAM_INTEGRITY_PAYLOAD_INVALID,
                message,
                true,
                "Check submission integrity fields and retry with canonical formatting."
        );
    }
}
