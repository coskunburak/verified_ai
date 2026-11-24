package com.verifiedai.growth.api;

import com.verifiedai.growth.api.dto.ReferralAttributeRequest;
import com.verifiedai.growth.api.dto.ReferralAttributionResultResponse;
import com.verifiedai.growth.api.dto.ReferralSummaryResponse;
import com.verifiedai.growth.application.ReferralAttributionService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for learner referral codes, anti-abuse attribution, and milestone qualification.
 */
@RestController
@RequestMapping({"/api/v1/growth/referrals", "/v1/growth/referrals"})
public class GrowthReferralController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final ReferralAttributionService referralAttributionService;

    public GrowthReferralController(ReferralAttributionService referralAttributionService) {
        this.referralAttributionService = Objects.requireNonNull(
            referralAttributionService,
            "referralAttributionService must not be null"
        );
    }

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReferralSummaryResponse> getMyReferralSummary(
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        var view = referralAttributionService.getOrCreateSummary(userId);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(ReferralSummaryResponse.from(view));
    }

    @PostMapping(
        value = "/attribute",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ReferralAttributionResultResponse> attributeReferral(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody ReferralAttributeRequest request
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        if (request == null) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "Referral attribute request body is required",
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
        if (Boolean.TRUE.equals(request.trainingEligible())) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "Referral attribution requests are never training eligible (ADR-006)",
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
        var view = referralAttributionService.attributeReferral(
            userId,
            request.referralCode(),
            request.attributionSource(),
            request.deviceFingerprintHash(),
            idempotencyKey
        );
        HttpStatus status = view.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(ReferralAttributionResultResponse.from(view));
    }

    @PostMapping(value = "/qualify", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReferralAttributionResultResponse> qualifyReferral(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        var view = referralAttributionService.qualifyAndGrantReward(userId, idempotencyKey);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(ReferralAttributionResultResponse.from(view));
    }
}
