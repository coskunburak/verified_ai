package com.verifiedai.featureflags.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Immutable specification for an A/B experiment variant and its traffic weight in basis points.
 */
public record ExperimentVariantSpec(String key, int weightBasisPoints) {

    private static final Pattern VARIANT_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{2,48}$");

    public ExperimentVariantSpec {
        if (key == null || !VARIANT_KEY_PATTERN.matcher(key.trim()).matches()) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.EXPERIMENT_VALIDATION_FAILED,
                "Experiment variant key must match ^[A-Za-z0-9_.-]{2,48}$",
                false,
                "FIX_EXPERIMENT_VARIANTS"
            );
        }
        key = key.trim();
        if (weightBasisPoints <= 0 || weightBasisPoints > 10_000) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.EXPERIMENT_VALIDATION_FAILED,
                "Experiment variant weightBasisPoints must be between 1 and 10000",
                false,
                "FIX_EXPERIMENT_VARIANTS"
            );
        }
    }
}
