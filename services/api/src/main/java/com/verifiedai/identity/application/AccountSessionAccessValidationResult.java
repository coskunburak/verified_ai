package com.verifiedai.identity.application;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public record AccountSessionAccessValidationResult(
    boolean allowed,
    HttpStatus status,
    ApiErrorCode code,
    String title,
    String userAction
) {
    public static AccountSessionAccessValidationResult allow() {
        return new AccountSessionAccessValidationResult(true, null, null, null, null);
    }

    public static AccountSessionAccessValidationResult rejected(
        HttpStatus status,
        ApiErrorCode code,
        String title,
        String userAction
    ) {
        return new AccountSessionAccessValidationResult(false, status, code, title, userAction);
    }
}
