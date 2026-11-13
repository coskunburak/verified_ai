package com.verifiedai.studyplan.gamification;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

/**
 * Domain exceptions for Sprint 8.8 mapped to canonical RFC 9457 {@link ApiErrorCode} responses.
 */
public final class GamificationExceptions {

    private GamificationExceptions() {
    }

    public static final class GamificationFeatureDisabledException extends ApiProblemException {
        public GamificationFeatureDisabledException(String message) {
            super(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.GAMIFICATION_FEATURE_DISABLED,
                    message,
                    true,
                    "RETRY_LATER"
            );
        }
    }

    public static final class GamificationTimezoneInvalidException extends ApiProblemException {
        public GamificationTimezoneInvalidException(String message) {
            super(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.GAMIFICATION_TIMEZONE_INVALID,
                    message,
                    false,
                    "PROVIDE_VALID_IANA_TIMEZONE"
            );
        }
    }

    public static final class GamificationEvidenceUnsupportedException extends ApiProblemException {
        public GamificationEvidenceUnsupportedException(String message) {
            super(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.GAMIFICATION_EVIDENCE_UNSUPPORTED,
                    message,
                    false,
                    "SUBMIT_SUPPORTED_EVIDENCE"
            );
        }
    }

    public static final class GamificationActivityWindowInvalidException extends ApiProblemException {
        public GamificationActivityWindowInvalidException(String message) {
            super(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.GAMIFICATION_ACTIVITY_WINDOW_INVALID,
                    message,
                    false,
                    "USE_CURRENT_ACTIVITY_TIMESTAMP"
            );
        }
    }
}
