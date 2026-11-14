package com.verifiedai.exam.api;

import com.verifiedai.exam.application.ExamCatalogService;
import com.verifiedai.exam.application.StudentExamTargetService;
import com.verifiedai.exam.domain.ExamModels.ExamDefinitionCatalogResponse;
import com.verifiedai.exam.domain.ExamModels.ExamDefinitionDetailResponse;
import com.verifiedai.exam.domain.ExamModels.ExamFamily;
import com.verifiedai.exam.domain.ExamModels.Jurisdiction;
import com.verifiedai.exam.domain.ExamModels.StudentExamTargetListResponse;
import com.verifiedai.exam.domain.ExamModels.StudentExamTargetResponse;
import com.verifiedai.exam.domain.ExamModels.TargetBindingOutcome;
import com.verifiedai.exam.domain.ExamModels.UpsertStudentExamTargetRequest;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * REST API controller for Phase 9 / Sprint 9.1 Exam Definitions Catalog,
 * Curriculum Topic Weight Versioning, and Student Exam Target Binding.
 */
@RestController
@Validated
@RequestMapping({"/v1/exams", "/api/v1/exams"})
public class ExamPlatformController {

    private final ExamCatalogService catalogService;
    private final StudentExamTargetService targetService;

    public ExamPlatformController(
            ExamCatalogService catalogService,
            StudentExamTargetService targetService
    ) {
        this.catalogService = Objects.requireNonNull(catalogService, "catalogService cannot be null");
        this.targetService = Objects.requireNonNull(targetService, "targetService cannot be null");
    }

    @GetMapping("/definitions")
    public ResponseEntity<ExamDefinitionCatalogResponse> listDefinitions(
            @RequestParam(name = "jurisdiction", required = false) Jurisdiction jurisdiction,
            @RequestParam(name = "examFamily", required = false) ExamFamily examFamily,
            @RequestParam(name = "includeDeprecated", defaultValue = "false") boolean includeDeprecated,
            Authentication authentication
    ) {
        resolveLearnerId(authentication);
        ExamDefinitionCatalogResponse response = catalogService.listDefinitions(jurisdiction, examFamily, includeDeprecated);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(response);
    }

    @GetMapping("/definitions/{examCode}")
    public ResponseEntity<ExamDefinitionDetailResponse> getActiveDefinition(
            @PathVariable("examCode") @Pattern(regexp = "^[A-Za-z0-9_]{3,64}$") String examCode,
            Authentication authentication
    ) {
        resolveLearnerId(authentication);
        ExamDefinitionDetailResponse response = catalogService.getActiveDefinition(examCode);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(response);
    }

    @GetMapping("/definitions/{examCode}/versions/{examVersion}")
    public ResponseEntity<ExamDefinitionDetailResponse> getVersionedDefinition(
            @PathVariable("examCode") @Pattern(regexp = "^[A-Za-z0-9_]{3,64}$") String examCode,
            @PathVariable("examVersion") @Pattern(regexp = "^[A-Za-z0-9._-]{1,32}$") String examVersion,
            Authentication authentication
    ) {
        resolveLearnerId(authentication);
        ExamDefinitionDetailResponse response = catalogService.getVersionedDefinition(examCode, examVersion);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(response);
    }

    @PostMapping("/targets")
    public ResponseEntity<StudentExamTargetResponse> upsertTarget(
            @RequestParam(name = "userId", required = false) UUID requestedUserId,
            @Valid @RequestBody UpsertStudentExamTargetRequest request,
            Authentication authentication
    ) {
        UUID learnerId = resolveLearnerId(authentication);
        enforceRequestedUserOwnership(learnerId, requestedUserId);

        TargetBindingOutcome outcome = targetService.upsertTarget(learnerId, request);
        if (outcome.created()) {
            return ResponseEntity.created(URI.create("/v1/exams/targets/" + outcome.response().targetId()))
                    .cacheControl(CacheControl.noStore().mustRevalidate())
                    .body(outcome.response());
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(outcome.response());
    }

    @GetMapping("/targets")
    public ResponseEntity<StudentExamTargetListResponse> listTargets(
            @RequestParam(name = "userId", required = false) UUID requestedUserId,
            Authentication authentication
    ) {
        UUID learnerId = resolveLearnerId(authentication);
        enforceRequestedUserOwnership(learnerId, requestedUserId);

        StudentExamTargetListResponse response = targetService.listTargets(learnerId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(response);
    }

    @GetMapping("/targets/{targetId}")
    public ResponseEntity<StudentExamTargetResponse> getTarget(
            @PathVariable("targetId") UUID targetId,
            Authentication authentication
    ) {
        UUID learnerId = resolveLearnerId(authentication);
        StudentExamTargetResponse response = targetService.getTarget(learnerId, targetId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(response);
    }

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ProblemDetailsResponse> handleApiProblem(ApiProblemException ex) {
        Map<String, Object> exDetails = ex.details();
        boolean recoverable = Boolean.TRUE.equals(exDetails.get("recoverable"));
        String userAction = exDetails.getOrDefault("userAction", "REVIEW_REQUEST").toString();
        return ResponseEntity.status(ex.status()).body(buildProblemDetails(
                "https://verifiedai.com/errors/" + ex.code().name().toLowerCase(),
                ex.getMessage(),
                ex.status(),
                ex.code(),
                recoverable,
                userAction
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetailsResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildProblemDetails(
                "https://verifiedai.com/errors/" + ApiErrorCode.EXAM_ACCESS_DENIED.name().toLowerCase(),
                ex.getMessage(),
                HttpStatus.FORBIDDEN,
                ApiErrorCode.EXAM_ACCESS_DENIED,
                false,
                "USE_OWN_LEARNER_ACCOUNT"
        ));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ProblemDetailsResponse> handleValidation(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(buildProblemDetails(
                "https://verifiedai.com/errors/" + ApiErrorCode.REQUEST_VALIDATION_FAILED.name().toLowerCase(),
                "Validation failed for exam platform request",
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                false,
                "FIX_REQUEST_FIELDS"
        ));
    }

    private static void enforceRequestedUserOwnership(UUID learnerId, UUID requestedUserId) {
        if (requestedUserId != null && !requestedUserId.equals(learnerId)) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_ACCESS_DENIED,
                    "Cross-learner exam target access is forbidden",
                    false,
                    "USE_OWN_LEARNER_ACCOUNT"
            );
        }
    }

    private static ProblemDetailsResponse buildProblemDetails(
            String type,
            String title,
            HttpStatus status,
            ApiErrorCode code,
            boolean recoverable,
            String userAction
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("errorCode", code.name());
        details.put("retryable", recoverable);
        details.put("recoverable", recoverable);
        details.put("userAction", userAction);
        return new ProblemDetailsResponse(
                type,
                title,
                status.value(),
                code,
                CorrelationIds.current(),
                details
        );
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
        throw new AccessDeniedException("Authenticated principal is not permitted to access exam endpoints");
    }
}
