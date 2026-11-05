package com.verifiedai.attempt.domain;
import com.verifiedai.sharedkernel.error.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.HttpStatus;
public final class AttemptAnalysisPolicy {
    private AttemptAnalysisPolicy() {}
    public static void submission(String key,List<String> steps) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,128}") || steps==null || steps.isEmpty() || steps.size()>12
            || steps.stream().anyMatch(s->!validText(s)))
            throw error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
    }
    public static boolean validText(String text) {
        return text!=null && !text.isBlank() && text.length()<=256
            && text.getBytes(StandardCharsets.UTF_8).length<=1024
            && text.codePoints().noneMatch(c->c==0 || (c>=0xD800 && c<=0xDFFF));
    }
    public static void answerSubmission(String key,List<String> steps,String finalAnswer) {
        if(steps==null || steps.size()>11 || !validText(finalAnswer))
            throw error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
        var all=new ArrayList<>(steps); all.add(finalAnswer); submission(key,all);
    }
    public static boolean canTransition(AttemptStatus from,AttemptStatus to) {
        return from==AttemptStatus.SUBMITTED && (to==AttemptStatus.ANALYZED || to==AttemptStatus.STALE_SOURCE);
    }
    public static ApiProblemException error(ApiErrorCode code,HttpStatus status) {
        return new ApiProblemException(status,code,"Attempt operation unavailable",status==HttpStatus.SERVICE_UNAVAILABLE,"REVIEW");
    }
}
