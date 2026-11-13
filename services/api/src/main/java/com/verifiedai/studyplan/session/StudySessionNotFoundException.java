package com.verifiedai.studyplan.session;

import java.util.UUID;

public class StudySessionNotFoundException extends RuntimeException {

    public StudySessionNotFoundException(UUID sessionId) {
        super("Study session was not found: " + sessionId);
    }

    public StudySessionNotFoundException(String message) {
        super(message);
    }
}
