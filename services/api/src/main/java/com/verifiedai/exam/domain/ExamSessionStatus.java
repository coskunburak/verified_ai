package com.verifiedai.exam.domain;

public enum ExamSessionStatus {
    IN_PROGRESS,
    OFFLINE_GRACE_BUFFER,
    SUBMITTED,
    EXPIRED_AUTO_SUBMITTED,
    ABANDONED;

    public boolean isTerminal() {
        return this == SUBMITTED || this == EXPIRED_AUTO_SUBMITTED || this == ABANDONED;
    }

    public boolean isActive() {
        return this == IN_PROGRESS || this == OFFLINE_GRACE_BUFFER;
    }
}
