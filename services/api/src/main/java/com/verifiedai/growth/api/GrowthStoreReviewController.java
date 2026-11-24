package com.verifiedai.growth.api;

import com.verifiedai.growth.api.dto.StoreReviewEligibilityResponse;
import com.verifiedai.growth.api.dto.StoreReviewEventRequest;
import com.verifiedai.growth.api.dto.StoreReviewEventResponse;
import com.verifiedai.growth.application.StoreReviewPromptService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for non-spammy store review prompt eligibility and outcome recording.
 */
@RestController
@RequestMapping({"/api/v1/growth/review-prompts", "/v1/growth/review-prompts"})
public class GrowthStoreReviewController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final StoreReviewPromptService storeReviewPromptService;

    public GrowthStoreReviewController(StoreReviewPromptService storeReviewPromptService) {
        this.storeReviewPromptService = Objects.requireNonNull(
            storeReviewPromptService,
            "storeReviewPromptService must not be null"
        );
    }

    @GetMapping(value = "/eligibility", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StoreReviewEligibilityResponse> evaluateEligibility(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam("triggerMoment") String triggerMoment,
        @RequestParam(value = "activeTimerRunning", defaultValue = "false") boolean activeTimerRunning
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        var view = storeReviewPromptService.evaluateEligibility(userId, triggerMoment, activeTimerRunning);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(StoreReviewEligibilityResponse.from(view));
    }

    @PostMapping(
        value = "/events",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<StoreReviewEventResponse> recordOutcome(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody StoreReviewEventRequest request
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        if (request == null) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "Store review event request body is required",
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
        if (Boolean.TRUE.equals(request.trainingEligible())) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "Store review events are never training eligible (ADR-006)",
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
        var view = storeReviewPromptService.recordOutcome(
            userId,
            request.triggerMoment(),
            request.outcome(),
            request.appVersion(),
            request.locale(),
            idempotencyKey
        );
        HttpStatus status = view.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(StoreReviewEventResponse.from(view));
    }
}
