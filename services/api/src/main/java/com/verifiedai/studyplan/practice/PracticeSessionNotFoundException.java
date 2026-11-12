package com.verifiedai.studyplan.practice;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class PracticeSessionNotFoundException extends ApiProblemException {

    public PracticeSessionNotFoundException(UUID sessionId) {
        super(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.RESOURCE_NOT_FOUND,
                "Practice session not found: " + sessionId,
                false,
                "CHECK_SESSION_ID"
        );
    }
}
