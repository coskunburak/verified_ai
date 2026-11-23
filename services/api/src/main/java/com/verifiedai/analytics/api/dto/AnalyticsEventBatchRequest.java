package com.verifiedai.analytics.api.dto;

import java.util.List;

public record AnalyticsEventBatchRequest(
    List<AnalyticsEventIngestRequest> events,
    Boolean trainingEligible
) {
}
