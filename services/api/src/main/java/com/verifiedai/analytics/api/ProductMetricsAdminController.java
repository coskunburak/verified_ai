package com.verifiedai.analytics.api;

import com.verifiedai.analytics.api.dto.ProductMetricsSummaryResponse;
import com.verifiedai.analytics.application.ProductMetricsQueryService;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative read-only product metrics, Learning OS KPIs, and tier unit-economics controller.
 */
@RestController
@RequestMapping({"/api/v1/admin/analytics", "/v1/admin/analytics"})
public class ProductMetricsAdminController {

    private final ProductMetricsQueryService queryService;

    public ProductMetricsAdminController(ProductMetricsQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    @GetMapping(path = "/product-metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductMetricsSummaryResponse> getProductMetrics(
        @RequestParam(name = "from", required = false) String from,
        @RequestParam(name = "to", required = false) String to,
        @RequestParam(name = "tier", defaultValue = "ALL") String tier
    ) {
        LocalDate fromDate = parseDate(from, "from");
        LocalDate toDate = parseDate(to, "to");
        var summary = queryService.getProductMetrics(fromDate, toDate, tier);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(ProductMetricsSummaryResponse.from(summary));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetailsResponse> handleAccessDenied(AccessDeniedException exception) {
        ProblemDetailsResponse response = new ProblemDetailsResponse(
            "https://errors.verified-ai-learning.example/analytics-admin-forbidden",
            "Administrative role is required for analytics product metrics",
            HttpStatus.FORBIDDEN.value(),
            ApiErrorCode.ANALYTICS_ADMIN_FORBIDDEN,
            CorrelationIds.current(),
            Map.of("recoverable", false, "userAction", "USE_ADMIN_ACCOUNT")
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(response);
    }

    private static LocalDate parseDate(String raw, String paramName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException exception) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.ANALYTICS_METRICS_WINDOW_INVALID,
                "Query parameter '" + paramName + "' must use ISO YYYY-MM-DD format",
                false,
                "FIX_DATE_PARAM"
            );
        }
    }
}
