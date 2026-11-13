package com.verifiedai.studyplan.session;

public class IllegalSessionStateTransitionException extends RuntimeException {

    private final StudySessionState fromState;
    private final StudySessionState toState;

    public IllegalSessionStateTransitionException(StudySessionState fromState, StudySessionState toState) {
        super("Illegal study session state transition from " + fromState + " to " + toState);
        this.fromState = fromState;
        this.toState = toState;
    }

    public IllegalSessionStateTransitionException(StudySessionState fromState, StudySessionState toState, String reason) {
        super("Illegal study session state transition from " + fromState + " to " + toState + ": " + reason);
        this.fromState = fromState;
        this.toState = toState;
    }

    public StudySessionState fromState() {
        return fromState;
    }

    public StudySessionState toState() {
        return toState;
    }
}
