package com.verifiedai.problem.api;

import com.verifiedai.problem.application.ProblemParseCorrectionResult;
import java.time.Instant;
import java.util.UUID;

record CreateProblemParseCorrectionResponse(
    UUID problemSessionId,
    UUID problemParseId,
    int revision,
    String source,
    UUID parentParseId,
    boolean selected,
    String supportStatus,
    boolean reviewRequired,
    boolean canonicalizationRequired,
    Instant createdAt
) {
    static CreateProblemParseCorrectionResponse from(ProblemParseCorrectionResult result) {
        return new CreateProblemParseCorrectionResponse(
            result.problemSessionId(),
            result.problemParseId(),
            result.revision(),
            result.source(),
            result.parentParseId(),
            result.selected(),
            result.supportStatus(),
            result.reviewRequired(),
            result.canonicalizationRequired(),
            result.createdAt()
        );
    }
}
