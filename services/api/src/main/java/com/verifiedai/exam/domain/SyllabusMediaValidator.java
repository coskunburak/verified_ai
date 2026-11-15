package com.verifiedai.exam.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Validates raw uploaded syllabus bytes using strict magic-byte inspection,
 * active-content rejection, 10MB cap enforcement, and SHA-256 hashing.
 */
public final class SyllabusMediaValidator {

    public static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "mif1", "msf1");
    private static final Set<String> SUPPORTED_MIMES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/heic", "application/pdf"
    );

    private SyllabusMediaValidator() {
    }

    public record ValidatedMedia(
            String detectedMimeType,
            String sha256Hex,
            int byteSize
    ) {
    }

    public static ValidatedMedia validateAndHash(byte[] rawBytes, String declaredMimeType) {
        if (rawBytes == null || rawBytes.length == 0) {
            throw new ApiProblemException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    ApiErrorCode.SYLLABUS_MEDIA_UNSUPPORTED,
                    "Uploaded syllabus media must not be empty",
                    true,
                    "Select a valid JPEG, PNG, WEBP, HEIC image, or PDF document under 10MB."
            );
        }
        if (rawBytes.length > MAX_FILE_SIZE_BYTES) {
            throw new ApiProblemException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    ApiErrorCode.SYLLABUS_MEDIA_UNSUPPORTED,
                    "Uploaded syllabus media exceeds 10MB limit",
                    true,
                    "Compress or resize the syllabus image/PDF to 10MB or less before uploading."
            );
        }

        if (looksLikeActiveTextOrMarkup(rawBytes)) {
            throw unsupportedMediaException("Uploaded file contains disallowed text or markup signature");
        }

        String detected = detectMagicMimeType(rawBytes);
        if (detected == null || !SUPPORTED_MIMES.contains(detected)) {
            throw unsupportedMediaException("Unsupported syllabus file signature; expected JPEG, PNG, WEBP, HEIC, or PDF");
        }

        if (declaredMimeType != null && !declaredMimeType.isBlank()) {
            String normalizedDeclared = normalizeMime(declaredMimeType);
            if (!"application/octet-stream".equals(normalizedDeclared) && !normalizedDeclared.equals(detected)) {
                throw unsupportedMediaException("Declared content type does not match actual file signature");
            }
        }

        return new ValidatedMedia(detected, sha256Hex(rawBytes), rawBytes.length);
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String normalizeMime(String rawMime) {
        String lower = rawMime.trim().toLowerCase(Locale.ROOT);
        int semi = lower.indexOf(';');
        if (semi >= 0) {
            lower = lower.substring(0, semi).trim();
        }
        if ("image/jpg".equals(lower)) {
            return "image/jpeg";
        }
        return lower;
    }

    private static String detectMagicMimeType(byte[] b) {
        // JPEG: FF D8 FF
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // PNG: 89 50 4E 47
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) {
            return "image/png";
        }
        // WEBP: RIFF....WEBP
        if (b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        // PDF: %PDF-
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return "application/pdf";
        }
        // HEIC / ISO BMFF: ....ftyp<brand>
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            String brand = new String(b, 8, 4, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
            if (HEIC_BRANDS.contains(brand)) {
                return "image/heic";
            }
        }
        return null;
    }

    private static boolean looksLikeActiveTextOrMarkup(byte[] b) {
        int probeLen = Math.min(b.length, 64);
        String prefix = new String(b, 0, probeLen, StandardCharsets.UTF_8)
                .trim()
                .toLowerCase(Locale.ROOT);
        return prefix.startsWith("<html")
                || prefix.startsWith("<!doctype")
                || prefix.startsWith("<script")
                || prefix.startsWith("<?xml")
                || prefix.startsWith("<svg");
    }

    private static ApiProblemException unsupportedMediaException(String title) {
        return new ApiProblemException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ApiErrorCode.SYLLABUS_MEDIA_UNSUPPORTED,
                title,
                true,
                "Upload an authentic JPEG, PNG, WEBP, HEIC image, or PDF document under 10MB."
        );
    }
}
