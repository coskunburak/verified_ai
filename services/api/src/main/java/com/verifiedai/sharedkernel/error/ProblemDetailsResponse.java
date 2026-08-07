package com.verifiedai.sharedkernel.error;

import java.util.Map;

public record ProblemDetailsResponse(
    String type,
    String title,
    int status,
    ApiErrorCode code,
    String traceId,
    Map<String, Object> details
) {
}

