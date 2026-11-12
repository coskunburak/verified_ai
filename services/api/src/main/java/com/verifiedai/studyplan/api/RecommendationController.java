package com.verifiedai.studyplan.api;

import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import com.verifiedai.studyplan.api.dto.DismissRecommendationRequest;
import com.verifiedai.studyplan.api.dto.LearnerRecommendationDto;
import com.verifiedai.studyplan.api.dto.LearnerRecommendationsResponse;
import com.verifiedai.studyplan.api.dto.RecommendationActionResponse;
import com.verifiedai.studyplan.domain.LearnerRecommendationEntity;
import com.verifiedai.studyplan.service.StudyPlanService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(path = {"/v1/study-plan/recommendations", "/api/v1/study-plan/recommendations"})
public class RecommendationController {

    private final StudyPlanService studyPlanService;

    public RecommendationController(StudyPlanService studyPlanService) {
        this.studyPlanService = studyPlanService;
    }

    @GetMapping
    public ResponseEntity<LearnerRecommendationsResponse> getRecommendations(
            @AuthenticationPrincipal Object principal,
            @RequestParam(name = "limit", required = false, defaultValue = "5") int limit,
            @RequestParam(name = "refresh", required = false, defaultValue = "false") boolean refresh
    ) {
        UUID userId = resolveUserId(principal);
        List<LearnerRecommendationEntity> entities = studyPlanService.getOrGenerateRecommendations(userId, limit, refresh);
        List<LearnerRecommendationDto> dtos = entities.stream()
                .map(LearnerRecommendationDto::fromEntity)
                .toList();

        LearnerRecommendationsResponse response = LearnerRecommendationsResponse.of(userId, Instant.now(), dtos);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(response);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<RecommendationActionResponse> acceptRecommendation(
            @AuthenticationPrincipal Object principal,
            @PathVariable("id") UUID id
    ) {
        UUID userId = resolveUserId(principal);
        LearnerRecommendationEntity updated = studyPlanService.acceptRecommendation(userId, id);

        String sessionType = StudyPlanService.resolveNextSessionType(updated.getRecommendationType());
        String deepLink = StudyPlanService.buildDeepLinkUri(sessionType, updated.getTargetSkillCode(), updated.getTargetMistakeCode());

        RecommendationActionResponse response = new RecommendationActionResponse(
                updated.getId(),
                updated.getStatus().name(),
                sessionType,
                updated.getTargetSkillCode(),
                updated.getTargetMistakeCode(),
                deepLink
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<RecommendationActionResponse> dismissRecommendation(
            @AuthenticationPrincipal Object principal,
            @PathVariable("id") UUID id,
            @RequestBody(required = false) DismissRecommendationRequest request
    ) {
        UUID userId = resolveUserId(principal);
        String reasonCode = request != null ? request.reasonCode() : "NOT_NOW";
        LearnerRecommendationEntity updated = studyPlanService.dismissRecommendation(userId, id, reasonCode);

        RecommendationActionResponse response = new RecommendationActionResponse(
                updated.getId(),
                updated.getStatus().name(),
                null,
                updated.getTargetSkillCode(),
                updated.getTargetMistakeCode(),
                null
        );
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(com.verifiedai.sharedkernel.error.ApiProblemException.class)
    public ResponseEntity<ProblemDetailsResponse> handleApiProblem(com.verifiedai.sharedkernel.error.ApiProblemException exception) {
        ProblemDetailsResponse response = new ProblemDetailsResponse(
                "https://errors.verified-ai-learning.example/" + exception.code().name().toLowerCase().replace('_', '-'),
                exception.getMessage(),
                exception.status().value(),
                exception.code(),
                CorrelationIds.current(),
                exception.details()
        );
        return ResponseEntity.status(exception.status()).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetailsResponse> handleAccessDenied(AccessDeniedException ex) {
        ProblemDetailsResponse response = new ProblemDetailsResponse(
                "https://errors.verified-ai-learning.example/resource-forbidden",
                ex.getMessage(),
                HttpStatus.FORBIDDEN.value(),
                ApiErrorCode.RESOURCE_FORBIDDEN,
                CorrelationIds.current(),
                Map.of("recoverable", false, "userAction", "AUTHENTICATE_AS_OWNER")
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    private UUID resolveUserId(Object principal) {
        if (principal instanceof AuthenticatedLearnerPrincipal alp) {
            return alp.learnerId();
        }
        if (principal instanceof VerifiedJwtPrincipal vjp) {
            return vjp.userId();
        }
        if (principal instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            try {
                return UUID.fromString(auth.getName());
            } catch (IllegalArgumentException ignored) {
            }
        }
        throw new AccessDeniedException("Unable to resolve authenticated learner ID");
    }
}
