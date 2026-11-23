package com.verifiedai.featureflags.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Deterministic SHA-256 user-stable bucketing engine (INV-FF-003).
 * Maps (key, salt, userId) onto basis points [0, 9999] with zero variant flicker.
 */
public final class DeterministicBucketingPolicy {

    public static final int BASIS_POINTS_TOTAL = 10_000;

    private DeterministicBucketingPolicy() {
    }

    public static int computeBucket(String key, String salt, UUID userId) {
        if (key == null || key.isBlank()) {
            throw validationFailed("Bucketing key must not be blank");
        }
        if (salt == null || salt.trim().length() < 2) {
            throw validationFailed("Bucketing salt must contain at least 2 non-blank characters");
        }
        Objects.requireNonNull(userId, "userId must not be null");

        String canonical = key.trim() + ":" + salt.trim() + ":" + userId.toString().toLowerCase(Locale.ROOT);
        byte[] digest = sha256(canonical);
        long unsigned64 = ByteBuffer.wrap(digest, 0, 8).getLong();
        return (int) Long.remainderUnsigned(unsigned64, BASIS_POINTS_TOTAL);
    }

    public static boolean isInRollout(String key, String salt, UUID userId, int rolloutBasisPoints) {
        validateRolloutBasisPoints(rolloutBasisPoints);
        if (rolloutBasisPoints == 0) {
            return false;
        }
        if (rolloutBasisPoints == BASIS_POINTS_TOTAL) {
            return true;
        }
        return computeBucket(key, salt, userId) < rolloutBasisPoints;
    }

    public static void validateRolloutBasisPoints(int rolloutBasisPoints) {
        if (rolloutBasisPoints < 0 || rolloutBasisPoints > BASIS_POINTS_TOTAL) {
            throw validationFailed("rolloutBasisPoints must be between 0 and 10000");
        }
    }

    public static void validateVariants(List<ExperimentVariantSpec> variants, String controlVariant) {
        if (variants == null || variants.size() < 2 || variants.size() > 5) {
            throw experimentValidationFailed("Experiments must define between 2 and 5 variants");
        }
        if (controlVariant == null || controlVariant.isBlank()) {
            throw experimentValidationFailed("controlVariant must not be blank");
        }
        Set<String> seenKeys = new HashSet<>();
        int sum = 0;
        boolean foundControl = false;
        String normalizedControl = controlVariant.trim();
        for (ExperimentVariantSpec spec : variants) {
            if (spec == null) {
                throw experimentValidationFailed("Experiment variant spec must not be null");
            }
            if (!seenKeys.add(spec.key())) {
                throw experimentValidationFailed("Duplicate experiment variant key: " + spec.key());
            }
            if (normalizedControl.equals(spec.key())) {
                foundControl = true;
            }
            sum += spec.weightBasisPoints();
        }
        if (!foundControl) {
            throw experimentValidationFailed("controlVariant '" + normalizedControl + "' must exist in variants");
        }
        if (sum != BASIS_POINTS_TOTAL) {
            throw experimentValidationFailed(
                "Experiment variant weights must sum to exactly 10000 basis points, got " + sum
            );
        }
    }

    public static String selectVariant(int bucket, List<ExperimentVariantSpec> variants) {
        if (bucket < 0 || bucket >= BASIS_POINTS_TOTAL) {
            throw experimentValidationFailed("Bucket value must be in [0, 9999], got " + bucket);
        }
        if (variants == null || variants.size() < 2 || variants.size() > 5) {
            throw experimentValidationFailed("Experiments must define between 2 and 5 variants");
        }
        int total = 0;
        for (ExperimentVariantSpec spec : variants) {
            total += spec.weightBasisPoints();
        }
        if (total != BASIS_POINTS_TOTAL) {
            throw experimentValidationFailed(
                "Experiment variant weights must sum to exactly 10000 basis points, got " + total
            );
        }

        int cumulative = 0;
        for (ExperimentVariantSpec spec : variants) {
            cumulative += spec.weightBasisPoints();
            if (bucket < cumulative) {
                return spec.key();
            }
        }
        return variants.getLast().key();
    }

    public static String selectVariant(
        String experimentKey,
        String salt,
        UUID userId,
        List<ExperimentVariantSpec> variants
    ) {
        int bucket = computeBucket(experimentKey, salt, userId);
        return selectVariant(bucket, variants);
    }

    private static byte[] sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
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

    private static ApiProblemException experimentValidationFailed(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.EXPERIMENT_VALIDATION_FAILED,
            message,
            false,
            "FIX_EXPERIMENT_VARIANTS"
        );
    }
}
