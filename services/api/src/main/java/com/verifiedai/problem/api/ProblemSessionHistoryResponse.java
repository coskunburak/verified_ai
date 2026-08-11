package com.verifiedai.problem.api;

import com.verifiedai.problem.application.ProblemSessionHistoryPage;
import java.util.List;

public record ProblemSessionHistoryResponse(
    List<ProblemSessionSummaryResponse> items,
    String nextCursor
) {
    static ProblemSessionHistoryResponse from(ProblemSessionHistoryPage page) {
        return new ProblemSessionHistoryResponse(
            page.items().stream().map(ProblemSessionSummaryResponse::from).toList(),
            page.nextCursor()
        );
    }
}
