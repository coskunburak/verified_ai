package com.verifiedai.studyplan.session;

public enum StudySessionState {
    INITIALIZED,
    ACTIVE,
    PAUSED,
    COMPLETED,
    ABANDONED;

    public boolean isTerminal() {
        return this == COMPLETED || this == ABANDONED;
    }

    public boolean isResumable() {
        return this == ACTIVE || this == PAUSED;
    }
}
