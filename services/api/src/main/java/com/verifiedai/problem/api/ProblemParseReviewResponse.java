package com.verifiedai.problem.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.problem.application.ProblemParseReviewResult;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

record ProblemParseReviewResponse(
    UUID problemSessionId,
    CurrentParse currentParse,
    long revisionCount,
    boolean canCorrect
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    static ProblemParseReviewResponse from(ProblemParseReviewResult result) {
        return new ProblemParseReviewResponse(
            result.problemSessionId(),
            CurrentParse.from(result.currentParse()),
            result.revisionCount(),
            result.canCorrect()
        );
    }

    record CurrentParse(
        UUID problemParseId,
        int revision,
        String source,
        String supportStatus,
        boolean reviewRequired,
        Map<String, Object> normalizedProblem,
        Instant createdAt
    ) {
        static CurrentParse from(ProblemParseReviewResult.CurrentParse parse) {
            return new CurrentParse(
                parse.problemParseId(),
                parse.revision(),
                parse.source(),
                parse.supportStatus(),
                parse.reviewRequired(),
                parse(parse.normalizedProblemJson()),
                parse.createdAt()
            );
        }
    }

    private static Map<String, Object> parse(String normalizedProblemJson) {
        if (normalizedProblemJson == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(normalizedProblemJson, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored normalized problem parse is not valid JSON", exception);
        }
    }
}
