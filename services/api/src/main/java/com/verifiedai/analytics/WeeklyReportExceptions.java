package com.verifiedai.analytics;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

/**
 * Factory methods for Sprint 8.9 weekly report RFC 9457 domain exceptions.
 */
public final class WeeklyReportExceptions {

    private WeeklyReportExceptions() {
    }

    public static ApiProblemException featureDisabled() {
        return new ApiProblemException(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiErrorCode.WEEKLY_REPORT_FEATURE_DISABLED,
            "Weekly learning reports are temporarily disabled.",
            true,
            "RETRY_LATER"
        );
    }

    public static ApiProblemException invalidWeekIso(String rawWeekIso) {
        return new ApiProblemException(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.WEEKLY_REPORT_INVALID_WEEK_ISO,
            "Invalid ISO week format: expected YYYY-Www.",
            false,
            "FIX_WEEK_ISO"
        );
    }

    public static ApiProblemException futureWeekNotAllowed(String weekIso) {
        return new ApiProblemException(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.WEEKLY_REPORT_FUTURE_WEEK_NOT_ALLOWED,
            "Weekly learning reports cannot be generated for future ISO weeks.",
            false,
            "SELECT_CURRENT_OR_PAST_WEEK"
        );
    }

    public static ApiProblemException reportNotFound(String weekIso) {
        return new ApiProblemException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.WEEKLY_REPORT_NOT_FOUND,
            "No weekly learning digest was found for the requested ISO week.",
            true,
            "GENERATE_WEEKLY_REPORT_FIRST"
        );
    }

    public static ApiProblemException sharePacketScopeViolation(String detail) {
        return new ApiProblemException(
            HttpStatus.FORBIDDEN,
            ApiErrorCode.WEEKLY_SHARE_PACKET_SCOPE_VIOLATION,
            detail != null && !detail.isBlank()
                ? detail
                : "Share packet request violates G-PRIV-03 privacy or learner ownership boundaries.",
            false,
            "USE_ALLOWLISTED_SHARE_SCOPE"
        );
    }
}
