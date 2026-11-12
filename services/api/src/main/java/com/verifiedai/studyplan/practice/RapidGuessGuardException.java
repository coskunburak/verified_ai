package com.verifiedai.studyplan.practice;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

public class RapidGuessGuardException extends ApiProblemException {

    public RapidGuessGuardException(String message) {
        super(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                message,
                true,
                "ALLOW_REASONING_TIME"
        );
    }
}
