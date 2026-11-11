package com.verifiedai.mastery.application;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

public class UnprocessableDomainRuleException extends ApiProblemException {

    public UnprocessableDomainRuleException(String ruleCode, String message) {
        super(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.ATTEMPT_NOT_ELIGIBLE_FOR_TWIN,
                message != null ? message : "Domain rule rejected operation: " + ruleCode,
                false,
                "RESOLVE_PREREQUISITE"
        );
    }
}
