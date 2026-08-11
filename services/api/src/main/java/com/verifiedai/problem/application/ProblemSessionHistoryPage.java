package com.verifiedai.problem.application;

import java.util.List;

public record ProblemSessionHistoryPage(
    List<ProblemSessionSummary> items,
    String nextCursor
) {
}
