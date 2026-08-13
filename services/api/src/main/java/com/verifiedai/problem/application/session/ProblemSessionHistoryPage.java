package com.verifiedai.problem.application.session;

import java.util.List;

public record ProblemSessionHistoryPage(
    List<ProblemSessionSummary> items,
    String nextCursor
) {
}
