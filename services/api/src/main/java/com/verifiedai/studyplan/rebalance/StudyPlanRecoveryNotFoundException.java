package com.verifiedai.studyplan.rebalance;

import java.util.UUID;

public class StudyPlanRecoveryNotFoundException extends RuntimeException {

    public StudyPlanRecoveryNotFoundException(UUID resourceId) {
        super("Study plan recovery resource not found: " + resourceId);
    }

    public StudyPlanRecoveryNotFoundException(String message) {
        super(message);
    }
}
