package com.verifiedai.admin.api;

import com.verifiedai.admin.api.dto.WrongAnswerReportCreateRequest;
import com.verifiedai.admin.api.dto.WrongAnswerReportResponse;
import com.verifiedai.admin.application.WrongAnswerReportService;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Learner-facing wrong-answer report endpoint enforcing BOLA ownership (INV-ADM-005),
 * active account check, Idempotency-Key replay/conflict, and server-side evidence snapshots.
 */
@RestController
@RequestMapping({"/api/v1/support/wrong-answer-reports", "/v1/support/wrong-answer-reports"})
public class LearnerWrongAnswerReportController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final WrongAnswerReportService reportService;

    public LearnerWrongAnswerReportController(WrongAnswerReportService reportService) {
        this.reportService = Objects.requireNonNull(reportService, "reportService must not be null");
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WrongAnswerReportResponse> submitReport(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody WrongAnswerReportCreateRequest request
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        if (request == null) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.ADMIN_VALIDATION_FAILED,
                "Wrong-answer report request body is required",
                false,
                "FIX_REPORT_REQUEST"
            );
        }
        if (Boolean.TRUE.equals(request.trainingEligible())) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.ADMIN_VALIDATION_FAILED,
                "Wrong-answer report requests are never training eligible (ADR-006)",
                false,
                "FIX_REPORT_REQUEST"
            );
        }
        var view = reportService.submitLearnerReport(
            userId,
            request.problemSessionId(),
            request.reportReason(),
            request.userNote(),
            idempotencyKey
        );
        HttpStatus status = view.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(WrongAnswerReportResponse.from(view));
    }
}
