package com.verifiedai.admin.api.dto;

import com.verifiedai.admin.application.WrongAnswerReportView;
import java.time.Instant;
import java.util.List;

public record WrongAnswerReportQueueResponse(
    String schemaVersion,
    int reportCount,
    List<WrongAnswerReportResponse> reports,
    Instant generatedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public static WrongAnswerReportQueueResponse from(WrongAnswerReportView.WrongAnswerReportQueueView view) {
        return new WrongAnswerReportQueueResponse(
            view.schemaVersion(),
            view.reportCount(),
            view.reports().stream().map(WrongAnswerReportResponse::from).toList(),
            view.generatedAt(),
            view.llmTokensUsed(),
            view.trainingEligible()
        );
    }
}
