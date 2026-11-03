package com.verifiedai.attempt.domain;
import com.verifiedai.sharedkernel.error.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.HttpStatus;
public final class AttemptAnalysisPolicy {
    private AttemptAnalysisPolicy() {}
    public static void submission(String key,List<String> steps) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,128}") || steps==null || steps.isEmpty() || steps.size()>12
            || steps.stream().anyMatch(s->s==null || s.isBlank() || s.length()>256 || s.getBytes(StandardCharsets.UTF_8).length>1024))
            throw error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
    }
    public static boolean canTransition(AttemptStatus from,AttemptStatus to) {
        return from==AttemptStatus.SUBMITTED && (to==AttemptStatus.ANALYZED || to==AttemptStatus.STALE_SOURCE);
    }
    public static ApiProblemException error(ApiErrorCode code,HttpStatus status) {
        return new ApiProblemException(status,code,"Attempt operation unavailable",status==HttpStatus.SERVICE_UNAVAILABLE,"REVIEW");
    }
}
