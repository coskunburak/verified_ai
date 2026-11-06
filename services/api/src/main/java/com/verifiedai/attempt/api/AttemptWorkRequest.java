package com.verifiedai.attempt.api;

import com.fasterxml.jackson.databind.*;
import com.verifiedai.attempt.domain.*;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Shared strict validation before HTTP binding; application also validates typed domain values. */
public final class AttemptWorkRequest {
    private AttemptWorkRequest() { }
    private static final ObjectMapper MAPPER=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    public static AttemptWorkInput parse(JsonNode input) {
        try {
            VersionedJsonContract.validate("attempt-structured-work-request-v1",input.toString());
            return MAPPER.treeToValue(input,AttemptWorkInput.class);
        } catch(java.io.IOException | IllegalArgumentException ex) {
            throw AttemptAnalysisPolicy.error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}
