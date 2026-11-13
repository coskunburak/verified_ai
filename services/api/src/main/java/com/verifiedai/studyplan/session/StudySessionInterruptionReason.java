package com.verifiedai.studyplan.session;

public enum StudySessionInterruptionReason {
    USER_PAUSED,
    APP_BACKGROUNDED,
    NETWORK_LOST,
    PHONE_CALL,
    OS_TERMINATED,
    EXPIRED_INACTIVITY,
    SUPERSEDED_BY_NEW_SESSION
}
