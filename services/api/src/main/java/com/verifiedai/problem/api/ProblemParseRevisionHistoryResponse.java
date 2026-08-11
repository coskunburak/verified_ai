package com.verifiedai.problem.api;

import com.verifiedai.problem.application.ProblemParseRevisionSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record ProblemParseRevisionHistoryResponse(
    UUID problemSessionId,
    UUID selectedParseId,
    List<ProblemParseRevisionEntry> revisions
) {
    static ProblemParseRevisionHistoryResponse from(UUID problemSessionId, List<ProblemParseRevisionSummary> summaries) {
        UUID selectedParseId = summaries.stream()
            .filter(ProblemParseRevisionSummary::selected)
            .findFirst()
            .map(ProblemParseRevisionSummary::id)
            .orElse(null);
        return new ProblemParseRevisionHistoryResponse(
            problemSessionId,
            selectedParseId,
            summaries.stream().map(ProblemParseRevisionEntry::from).toList()
        );
    }

    record ProblemParseRevisionEntry(
        UUID problemParseId,
        int revision,
        String source,
        UUID parentParseId,
        boolean selected,
        String supportStatus,
        boolean reviewRequired,
        String correctionReason,
        List<String> correctedFieldCategories,
        Instant createdAt
    ) {
        static ProblemParseRevisionEntry from(ProblemParseRevisionSummary summary) {
            return new ProblemParseRevisionEntry(
                summary.id(),
                summary.revision(),
                summary.source(),
                summary.parentParseId(),
                summary.selected(),
                summary.supportStatus(),
                summary.reviewRequired(),
                summary.correctionReason(),
                summary.correctedFieldCategories(),
                summary.createdAt()
            );
        }
    }
}
