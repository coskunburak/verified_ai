package com.verifiedai.admin.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Pure state machine guarding wrong-answer report transitions and resolution completeness.
 */
public final class WrongAnswerReportStateMachine {

    private static final Map<WrongAnswerReportStatus, Set<WrongAnswerReportStatus>> LEGAL_TRANSITIONS;

    static {
        EnumMap<WrongAnswerReportStatus, Set<WrongAnswerReportStatus>> map =
            new EnumMap<>(WrongAnswerReportStatus.class);
        EnumSet<WrongAnswerReportStatus> terminal = EnumSet.of(
            WrongAnswerReportStatus.RESOLVED_CONFIRMED_DEFECT,
            WrongAnswerReportStatus.RESOLVED_NOT_A_DEFECT,
            WrongAnswerReportStatus.RESOLVED_DUPLICATE,
            WrongAnswerReportStatus.DISMISSED
        );
        EnumSet<WrongAnswerReportStatus> fromOpen = EnumSet.of(WrongAnswerReportStatus.IN_REVIEW);
        fromOpen.addAll(terminal);
        map.put(WrongAnswerReportStatus.OPEN, EnumSet.copyOf(fromOpen));
        map.put(WrongAnswerReportStatus.IN_REVIEW, EnumSet.copyOf(terminal));
        for (WrongAnswerReportStatus status : terminal) {
            map.put(status, EnumSet.noneOf(WrongAnswerReportStatus.class));
        }
        LEGAL_TRANSITIONS = Map.copyOf(map);
    }

    private WrongAnswerReportStateMachine() {
    }

    public static void validateTransition(
        WrongAnswerReportStatus currentStatus,
        WrongAnswerReportStatus targetStatus,
        WrongAnswerResolutionCode resolutionCode,
        String resolutionNote
    ) {
        Objects.requireNonNull(currentStatus, "currentStatus must not be null");
        if (targetStatus == null) {
            throw validationFailed("targetStatus is required");
        }
        Set<WrongAnswerReportStatus> allowed = LEGAL_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.ADMIN_INVALID_TRANSITION,
                "Illegal wrong-answer report transition from " + currentStatus + " to " + targetStatus,
                false,
                "CHECK_REPORT_STATUS"
            );
        }
        if (targetStatus.isTerminal()) {
            if (resolutionCode == null) {
                throw validationFailed("resolutionCode is required when resolving or dismissing a report");
            }
            AdminRedactionPolicy.validateResolutionNote(resolutionNote);
        } else {
            if (resolutionCode != null) {
                throw validationFailed("resolutionCode must be null when transitioning to " + targetStatus);
            }
            if (resolutionNote != null && !resolutionNote.isBlank()) {
                AdminRedactionPolicy.validateResolutionNote(resolutionNote);
            }
        }
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
