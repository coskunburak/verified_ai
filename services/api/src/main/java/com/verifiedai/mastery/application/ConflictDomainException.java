package com.verifiedai.mastery.application;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

public class ConflictDomainException extends ApiProblemException {

    public ConflictDomainException(String code, String message) {
        super(
                HttpStatus.CONFLICT,
                ApiErrorCode.TWIN_ALREADY_TERMINAL,
                message != null ? message : "Conflict with current resource state: " + code,
                false,
                "REFRESH_STATE"
        );
    }
}
