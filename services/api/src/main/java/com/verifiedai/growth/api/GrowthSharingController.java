package com.verifiedai.growth.api;

import com.verifiedai.growth.api.dto.CreateShareArtifactRequest;
import com.verifiedai.growth.api.dto.PublicShareArtifactResponse;
import com.verifiedai.growth.api.dto.ShareableArtifactListResponse;
import com.verifiedai.growth.api.dto.ShareableArtifactResponse;
import com.verifiedai.growth.application.ShareableArtifactService;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for PII-free shareable learning artifacts and public deep-link resolution.
 */
@RestController
@RequestMapping({"/api/v1/growth/shares", "/v1/growth/shares"})
public class GrowthSharingController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final ShareableArtifactService shareableArtifactService;

    public GrowthSharingController(ShareableArtifactService shareableArtifactService) {
        this.shareableArtifactService = Objects.requireNonNull(
            shareableArtifactService,
            "shareableArtifactService must not be null"
        );
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ShareableArtifactResponse> createShare(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody CreateShareArtifactRequest request
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        if (request == null) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "Share artifact request body is required",
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
        if (Boolean.TRUE.equals(request.trainingEligible())) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "Growth share artifacts are never training eligible (ADR-006)",
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
        var view = shareableArtifactService.createShare(
            userId,
            request.artifactType(),
            request.locale(),
            request.displayAlias(),
            request.referralCode(),
            request.summaryPayload(),
            request.ttlDays(),
            idempotencyKey
        );
        HttpStatus status = view.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(ShareableArtifactResponse.from(view));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ShareableArtifactListResponse> listShares(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        List<ShareableArtifactResponse> items = shareableArtifactService.listShares(userId, limit).stream()
            .map(ShareableArtifactResponse::from)
            .toList();
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(new ShareableArtifactListResponse(items, items.size(), false, 0));
    }

    @PostMapping(value = "/{shareId}/revoke", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ShareableArtifactResponse> revokeShare(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable("shareId") UUID shareId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        var view = shareableArtifactService.revokeShare(userId, shareId, idempotencyKey);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(ShareableArtifactResponse.from(view));
    }

    @GetMapping(value = "/public/{shareToken}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PublicShareArtifactResponse> resolvePublicShare(
        @PathVariable("shareToken") String shareToken
    ) {
        var view = shareableArtifactService.resolvePublicShare(shareToken);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(PublicShareArtifactResponse.from(view));
    }
}
