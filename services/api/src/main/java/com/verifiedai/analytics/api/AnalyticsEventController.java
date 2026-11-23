package com.verifiedai.analytics.api;

import com.verifiedai.analytics.api.dto.AnalyticsEventBatchRequest;
import com.verifiedai.analytics.api.dto.AnalyticsEventCatalogResponse;
import com.verifiedai.analytics.api.dto.AnalyticsEventIngestRequest;
import com.verifiedai.analytics.api.dto.AnalyticsEventIngestResponse;
import com.verifiedai.analytics.application.AnalyticsEventIngestionService;
import com.verifiedai.analytics.application.AnalyticsEventIngestionService.ClientEventInput;
import com.verifiedai.analytics.application.AnalyticsEventIngestionService.SingleIngestResult;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated learner analytics catalog and client event ingestion controller.
 * Always derives identity from JWT, enforces 64 KiB payload caps, and emits Cache-Control: no-store, private.
 */
@RestController
@RequestMapping({"/api/v1/analytics", "/v1/analytics"})
public class AnalyticsEventController {

    private static final long MAX_HTTP_BODY_BYTES = 65_536L;

    private final AnalyticsEventIngestionService ingestionService;

    public AnalyticsEventController(AnalyticsEventIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @GetMapping(path = "/catalog", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AnalyticsEventCatalogResponse> getCatalog(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        var catalog = ingestionService.getCatalogForApi(userId);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(AnalyticsEventCatalogResponse.from(catalog));
    }

    @PostMapping(
        path = "/events",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<AnalyticsEventIngestResponse> ingestEvent(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody AnalyticsEventIngestRequest requestBody,
        HttpServletRequest httpRequest
    ) {
        enforceBodySizeBound(httpRequest);
        UUID userId = AuthenticatedUser.from(jwt).userId();
        SingleIngestResult result = ingestionService.ingestClientEvent(
            userId,
            toInput(requestBody),
            CorrelationIds.current()
        );
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status)
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(AnalyticsEventIngestResponse.from(result.view()));
    }

    @PostMapping(
        path = "/events/batch",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<AnalyticsEventIngestResponse> ingestBatch(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody AnalyticsEventBatchRequest requestBody,
        HttpServletRequest httpRequest
    ) {
        enforceBodySizeBound(httpRequest);
        UUID userId = AuthenticatedUser.from(jwt).userId();
        List<ClientEventInput> inputs = requestBody.events() == null
            ? List.of()
            : requestBody.events().stream().map(AnalyticsEventController::toInput).toList();
        var view = ingestionService.ingestClientBatch(
            userId,
            inputs,
            requestBody.trainingEligible(),
            CorrelationIds.current()
        );
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(AnalyticsEventIngestResponse.from(view));
    }

    private static void enforceBodySizeBound(HttpServletRequest request) {
        if (request != null && request.getContentLengthLong() > MAX_HTTP_BODY_BYTES) {
            throw new ApiProblemException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                ApiErrorCode.REQUEST_TOO_LARGE,
                "Analytics request payload exceeds 65536 byte limit",
                false,
                "REDUCE_BATCH_SIZE"
            );
        }
    }

    private static ClientEventInput toInput(AnalyticsEventIngestRequest req) {
        if (req == null) {
            return null;
        }
        return new ClientEventInput(
            req.clientEventId(),
            req.eventName(),
            req.emittedBy(),
            req.sourceModule(),
            req.entitlementTier(),
            req.locale(),
            req.appVersion(),
            req.sessionRefHash(),
            req.latencyMs(),
            req.properties(),
            req.occurredAt(),
            req.trainingEligible()
        );
    }
}
