package com.verifiedai.attempt.api;
import com.verifiedai.attempt.application.StudentAttemptOrchestrator;
import com.verifiedai.attempt.domain.*;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/problem-sessions/{problemId}/attempts")
public class StudentAttemptController {
    private final StudentAttemptOrchestrator service;
    public StudentAttemptController(StudentAttemptOrchestrator service) { this.service=service; }
    @GetMapping("/context")
    public StudentAttemptOrchestrator.Context context(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId) { return service.context(AuthenticatedUser.from(jwt).userId(),problemId); }
    @GetMapping
    public List<StudentAttempt> list(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,
        @RequestParam(defaultValue="1") int representationVersion) {
        representation(representationVersion);
        return service.list(AuthenticatedUser.from(jwt).userId(),problemId).stream()
            .map(a->representationVersion==1?a.legacyRepresentation():a).toList();
    }
    @GetMapping("/{id}")
    public StudentAttempt read(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id,
        @RequestParam(defaultValue="1") int representationVersion) {
        representation(representationVersion);
        var attempt=service.read(AuthenticatedUser.from(jwt).userId(),problemId,id);
        return representationVersion==1?attempt.legacyRepresentation():attempt;
    }
    private void representation(int version) {
        if(version!=1 && version!=2) throw AttemptAnalysisPolicy.error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
    }
    @PostMapping
    public StudentAttempt submit(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody AttemptSubmissionRequest command) {
        UUID owner=AuthenticatedUser.from(jwt).userId();
        if(command.finalAnswer()!=null) return service.submitAnswer(owner,problemId,key,command.source(),command.steps(),command.finalAnswer());
        return service.submit(owner,problemId,key,command.source(),command.steps(),Boolean.TRUE.equals(command.realDomainConfirmed()),command.captureReview());
    }
}
