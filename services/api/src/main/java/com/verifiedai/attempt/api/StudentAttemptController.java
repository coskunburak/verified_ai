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
    public List<StudentAttempt> list(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId) { return service.list(AuthenticatedUser.from(jwt).userId(),problemId); }
    @GetMapping("/{id}")
    public StudentAttempt read(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id) { return service.read(AuthenticatedUser.from(jwt).userId(),problemId,id); }
    @PostMapping
    public StudentAttempt submit(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body) {
        var command=Command.parse(body);
        return service.submit(AuthenticatedUser.from(jwt).userId(),problemId,key,command.source(),command.steps(),command.realDomainConfirmed(),command.captureReview());
    }
    public record Command(AttemptSourceIdentity source,List<String> steps,boolean realDomainConfirmed,AttemptCaptureReview captureReview) {
        public static Command parse(Map<String,Object> body) {
            try {
                if(!(body.keySet().containsAll(Set.of("source","steps")) && Set.of("source","steps","realDomainConfirmed","captureReview").containsAll(body.keySet()))
                    || (body.containsKey("realDomainConfirmed") && !(body.get("realDomainConfirmed") instanceof Boolean)) || !(body.get("source") instanceof Map<?,?> source)
                    || !source.keySet().equals(Set.of("canonicalId","canonicalRevision","parseId","parseRevision"))
                    || !(source.get("canonicalId") instanceof String canonical) || !(source.get("parseId") instanceof String parse)
                    || !(source.get("canonicalRevision") instanceof Integer revision) || !(source.get("parseRevision") instanceof Integer parseRevision)
                    || !(body.get("steps") instanceof List<?> steps) || steps.stream().anyMatch(s->!(s instanceof String))) throw new IllegalArgumentException();
                AttemptCaptureReview review=null;
                if(body.containsKey("captureReview")) {
                    if(!(body.get("captureReview") instanceof Map<?,?> capture) || !(capture.keySet().containsAll(Set.of("schemaVersion","engine","rawLines","confirmed")) && Set.of("schemaVersion","engine","rawLines","confirmed","assetSessionId","recognitionJobId").containsAll(capture.keySet()))) throw new IllegalArgumentException();
                    review=new com.fasterxml.jackson.databind.ObjectMapper().disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
                        .convertValue(capture,AttemptCaptureReview.class);
                }
                return new Command(new AttemptSourceIdentity(UUID.fromString(canonical),revision,UUID.fromString(parse),parseRevision),steps.stream().map(String.class::cast).toList(),Boolean.TRUE.equals(body.get("realDomainConfirmed")),review);
            } catch(IllegalArgumentException | NullPointerException ex) { throw AttemptAnalysisPolicy.error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY); }
        }
    }
}
