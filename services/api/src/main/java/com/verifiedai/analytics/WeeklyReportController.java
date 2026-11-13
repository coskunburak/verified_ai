package com.verifiedai.analytics;

import com.verifiedai.analytics.WeeklyReportDtos.WeeklyDigestDto;
import com.verifiedai.analytics.WeeklyReportDtos.WeeklySharePacketDto;
import com.verifiedai.analytics.WeeklyReportDtos.WeeklySharePacketRequest;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for Sprint 8.9 weekly learning digests, explainable recommendations,
 * and privacy-safe G-PRIV-03 tutor/parent share packets.
 */
@RestController
@RequestMapping({"/v1/reports/weekly", "/api/v1/reports/weekly"})
public class WeeklyReportController {

    private final WeeklyLearnerDigestService service;

    public WeeklyReportController(WeeklyLearnerDigestService service) {
        this.service = Objects.requireNonNull(service, "service cannot be null");
    }

    @GetMapping(value = "/{weekIso}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WeeklyDigestDto> getWeeklyDigest(
        @AuthenticationPrincipal Object principal,
        @PathVariable("weekIso") String weekIso,
        @RequestParam(name = "locale", required = false) String locale,
        @RequestParam(name = "forceRefresh", defaultValue = "false") boolean forceRefresh
    ) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(service.getWeeklyDigest(userId, weekIso, locale, forceRefresh));
    }

    @PostMapping(
        value = "/{weekIso}/share-packet",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<WeeklySharePacketDto> createSharePacket(
        @AuthenticationPrincipal Object principal,
        @PathVariable("weekIso") String weekIso,
        @RequestBody(required = false) WeeklySharePacketRequest request
    ) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(service.createSharePacket(userId, weekIso, request));
    }

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ProblemDetailsResponse> handleApiProblem(ApiProblemException ex) {
        return ResponseEntity.status(ex.status())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(new ProblemDetailsResponse(
                "https://errors.verified-ai-learning.example/" + ex.code().name().toLowerCase().replace('_', '-'),
                ex.getMessage(),
                ex.status().value(),
                ex.code(),
                CorrelationIds.current(),
                ex.details()
            ));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetailsResponse> handleInvalidBody(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(new ProblemDetailsResponse(
                "https://errors.verified-ai-learning.example/request-validation-failed",
                "Invalid weekly report request payload.",
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                CorrelationIds.current(),
                Map.of("recoverable", false, "userAction", "FIX_REQUEST_PAYLOAD")
            ));
    }

    private static UUID resolveUserId(Object principal) {
        UUID resolved = extractUuid(principal);
        if (resolved != null) {
            return resolved;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            UUID authResolved = extractUuid(authentication.getPrincipal());
            if (authResolved != null) {
                return authResolved;
            }
        }
        throw new ApiProblemException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.AUTH_CREDENTIALS_INVALID,
            "Authentication required",
            false,
            "SIGN_IN"
        );
    }

    private static UUID extractUuid(Object candidate) {
        if (candidate instanceof VerifiedJwtPrincipal verified) {
            return verified.userId();
        }
        if (candidate instanceof AuthenticatedLearnerPrincipal learner) {
            return learner.learnerId();
        }
        if (candidate instanceof Jwt jwt) {
            return VerifiedJwtPrincipal.from(jwt).userId();
        }
        if (candidate instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }
}
