package com.verifiedai.admin.domain;

public enum WrongAnswerReportStatus {
    OPEN,
    IN_REVIEW,
    RESOLVED_CONFIRMED_DEFECT,
    RESOLVED_NOT_A_DEFECT,
    RESOLVED_DUPLICATE,
    DISMISSED;

    public boolean isTerminal() {
        return this == RESOLVED_CONFIRMED_DEFECT
            || this == RESOLVED_NOT_A_DEFECT
            || this == RESOLVED_DUPLICATE
            || this == DISMISSED;
    }
}
