package com.verifiedai.studyplan.domain;

public enum RecommendationType {
    PREREQUISITE_REPAIR(1),
    MISTAKE_REPAIR(2),
    SPACED_RETENTION(3),
    HIGH_ROI_TOPIC(4);

    private final int tieBreakOrder;

    RecommendationType(int tieBreakOrder) {
        this.tieBreakOrder = tieBreakOrder;
    }

    public int tieBreakOrder() {
        return tieBreakOrder;
    }
}
