package com.verifiedai.mastery.application;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

public class ResourceNotFoundOrForbiddenException extends ApiProblemException {

    public ResourceNotFoundOrForbiddenException(String resource) {
        super(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.RESOURCE_NOT_FOUND,
                resource != null ? resource : "Resource not found or access forbidden",
                false,
                "VERIFY_IDENTIFIER"
        );
    }
}
