package com.verifiedai.growth.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

public enum ReferralAttributionSource {
    DEEP_LINK,
    SHARE_CARD,
    MANUAL_CODE,
    WEEKLY_PDF_QR;

    public static ReferralAttributionSource parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "attributionSource is required",
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
        try {
            return ReferralAttributionSource.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "Unsupported attributionSource: " + raw,
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
    }
}
