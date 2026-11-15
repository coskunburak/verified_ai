package com.verifiedai.exam.domain;

public enum ExamItemResponseState {
    UNVISITED,
    VIEWED_OMITTED,
    ANSWERED,
    FLAGGED_ANSWERED,
    FLAGGED_OMITTED;

    public static ExamItemResponseState compute(boolean visited, boolean hasAnswer, boolean flagged) {
        if (hasAnswer && flagged) {
            return FLAGGED_ANSWERED;
        }
        if (hasAnswer) {
            return ANSWERED;
        }
        if (flagged) {
            return FLAGGED_OMITTED;
        }
        if (visited) {
            return VIEWED_OMITTED;
        }
        return UNVISITED;
    }

    public boolean isAnswered() {
        return this == ANSWERED || this == FLAGGED_ANSWERED;
    }

    public boolean isFlagged() {
        return this == FLAGGED_ANSWERED || this == FLAGGED_OMITTED;
    }

    public boolean isOmitted() {
        return this == UNVISITED || this == VIEWED_OMITTED || this == FLAGGED_OMITTED;
    }
}
