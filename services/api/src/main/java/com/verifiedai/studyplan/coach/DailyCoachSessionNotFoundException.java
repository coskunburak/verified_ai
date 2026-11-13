package com.verifiedai.studyplan.coach;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class DailyCoachSessionNotFoundException extends ApiProblemException {

    public DailyCoachSessionNotFoundException(UUID sessionId) {
        super(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.RESOURCE_NOT_FOUND,
                "Daily coach session not found: " + sessionId,
                false,
                "VERIFY_COACH_SESSION_ID"
        );
    }

    public DailyCoachSessionNotFoundException(UUID sessionId, UUID taskId) {
        super(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.RESOURCE_NOT_FOUND,
                "Daily coach task " + taskId + " not found in session " + sessionId,
                false,
                "VERIFY_COACH_TASK_ID"
        );
    }
}
