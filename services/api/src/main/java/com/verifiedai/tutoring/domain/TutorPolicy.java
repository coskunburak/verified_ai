package com.verifiedai.tutoring.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

public final class TutorPolicy {
    public static final String SCHEMA="tutor-session-v1", SAFETY="learner-persistence-v1";
    public static final int MAX_CHARACTERS=2000, MAX_BYTES=8000, MAX_MESSAGES=100, MAX_SESSIONS=30, MAX_PAGE=50;
    private TutorPolicy() {}
    public enum Status { ACTIVE, CLOSED }
    public enum Role { LEARNER }
    public static void key(String key) {
        if (key==null || !key.matches("[A-Za-z0-9_-]{8,128}"))
            throw failure(ApiErrorCode.IDEMPOTENCY_KEY_REQUIRED,HttpStatus.BAD_REQUEST);
    }
    public static void content(String content) {
        if (content==null || content.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)) || content.codePointCount(0,content.length())>MAX_CHARACTERS
            || content.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES
            || content.codePoints().anyMatch(c -> (Character.isISOControl(c) && c!='\n' && c!='\t')
                || Character.getType(c)==Character.SURROGATE || Character.getType(c)==Character.FORMAT))
            throw failure(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
    }
    public static void append(Status status, boolean current, long count, int limit) {
        if (status!=Status.ACTIVE) throw failure(ApiErrorCode.TUTOR_SESSION_CLOSED,HttpStatus.CONFLICT);
        if (!current) throw failure(ApiErrorCode.TUTOR_SOURCE_STALE,HttpStatus.CONFLICT);
        if (count>=limit) throw failure(ApiErrorCode.TUTOR_CAPACITY_EXCEEDED,HttpStatus.CONFLICT);
    }
    public static void page(long after, int limit) {
        if (after<0 || limit<1 || limit>MAX_PAGE) throw failure(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.BAD_REQUEST);
    }
    public static ApiProblemException failure(ApiErrorCode code, HttpStatus status) {
        return new ApiProblemException(status,code,"Tutor operation unavailable",false,"REVIEW");
    }
}
