package com.verifiedai.growth.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Generates non-ambiguous Crockford-safe referral codes and high-entropy non-enumerable
 * base64url share tokens (INV-GRW-001 & INV-GRW-002).
 */
public final class ReferralCodeGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 8;
    private static final int SHARE_TOKEN_BYTES = 24;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z2-9]{6,16}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private ReferralCodeGenerator() {
    }

    public static String generateReferralCode() {
        char[] buf = new char[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            buf[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(buf);
    }

    public static String generateShareToken() {
        byte[] bytes = new byte[SHARE_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String normalizeAndValidateCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "referralCode is required",
                false,
                "FIX_REFERRAL_CODE"
            );
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "referralCode format is invalid",
                false,
                "FIX_REFERRAL_CODE"
            );
        }
        return normalized;
    }

    public static boolean isValidFormat(String raw) {
        return raw != null && CODE_PATTERN.matcher(raw).matches();
    }
}
