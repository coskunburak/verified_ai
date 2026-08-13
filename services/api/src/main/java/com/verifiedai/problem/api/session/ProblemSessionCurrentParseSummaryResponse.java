package com.verifiedai.problem.api.session;

import com.verifiedai.problem.application.session.ProblemSessionCurrentParseSummary;
import java.util.UUID;

public record ProblemSessionCurrentParseSummaryResponse(
    UUID parseId,
    int revision,
    String source,
    String supportStatus,
    boolean reviewRequired
) {
    static ProblemSessionCurrentParseSummaryResponse from(ProblemSessionCurrentParseSummary currentParse) {
        if (currentParse == null) {
            return null;
        }
        return new ProblemSessionCurrentParseSummaryResponse(
            currentParse.parseId(),
            currentParse.revision(),
            currentParse.source(),
            currentParse.supportStatus(),
            currentParse.reviewRequired()
        );
    }
}
