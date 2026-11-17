package com.verifiedai.exam.api;

import com.verifiedai.exam.application.ExamAttributionReportService;
import com.verifiedai.exam.domain.ExamAttributionModels.*;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/v1/exams/sessions", "/api/v1/exams/sessions"})
public class ExamAttributionController {
    private final ExamAttributionReportService service;
    public ExamAttributionController(ExamAttributionReportService service) {this.service=service;}
    @GetMapping("/{sessionId}/attribution-report")
    public ResponseEntity<Report> report(@PathVariable UUID sessionId,
            @RequestHeader(value="Accept-Language",defaultValue="tr") String language,Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.report(resolveLearnerId(authentication),sessionId,language));
    }
    @PostMapping("/{sessionId}/apply-study-rebalance")
    public ResponseEntity<RebalanceResponse> apply(@PathVariable UUID sessionId,
            @RequestHeader(value="Idempotency-Key",required=false) String key,
            @RequestBody RebalanceRequest request,Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.apply(resolveLearnerId(authentication),sessionId,key,request));
    }
    @ExceptionHandler(ExamAttributionReportService.Failure.class)
    public ResponseEntity<ProblemDetail> failure(ExamAttributionReportService.Failure failure) {
        return problem(failure.status,failure.code);
    }
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> invalid(Exception ignored) {return problem(400,"INVALID_REBALANCE_REQUEST");}
    private ResponseEntity<ProblemDetail> problem(int status,String code) {
        var detail=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status),code);
        detail.setProperty("errorCode",code);
        detail.setProperty("code",code);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(detail);
    }
    private static UUID resolveLearnerId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiProblemException(
                    HttpStatus.UNAUTHORIZED,
                    ApiErrorCode.AUTH_TOKEN_EXPIRED,
                    "Authentication token is missing or invalid",
                    false,
                    "SIGN_IN"
            );
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedLearnerPrincipal learnerPrincipal) {
            return learnerPrincipal.learnerId();
        }
        if (principal instanceof Jwt jwt) {
            return VerifiedJwtPrincipal.from(jwt).learnerId();
        }
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        String name = authentication.getName();
        if (name != null && !name.isBlank()) {
            try {
                return UUID.fromString(name.trim());
            } catch (IllegalArgumentException ignored) {
                throw new AccessDeniedException("Authenticated principal does not carry a valid learner identifier");
            }
        }
        throw new AccessDeniedException("Authenticated principal is not permitted to access exam readiness endpoints");
    }
}
