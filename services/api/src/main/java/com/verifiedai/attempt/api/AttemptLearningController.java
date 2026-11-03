package com.verifiedai.attempt.api;
import com.verifiedai.attempt.application.AttemptLearningService;
import com.verifiedai.attempt.domain.*;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/problem-sessions/{problemId}/attempts")
public class AttemptLearningController {
    private final AttemptLearningService service;
    public AttemptLearningController(AttemptLearningService service) { this.service=service; }
    @PostMapping("/{id}/counterfactual-twin")
    public AttemptLearningService.PublicDrill create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body) {
        empty(body); return service.create(AuthenticatedUser.from(jwt).userId(),problemId,id,key);
    }
    @GetMapping("/{id}/counterfactual-twin")
    public AttemptLearningService.PublicDrill read(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id) {
        return service.drill(AuthenticatedUser.from(jwt).userId(),problemId,id);
    }
    @PostMapping("/{id}/counterfactual-twin/answer")
    public AttemptLearningService.PublicDrill answer(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body) {
        if(!body.keySet().equals(Set.of("selectedOption")) || !(body.get("selectedOption") instanceof Integer selected)) throw invalid();
        return service.answer(AuthenticatedUser.from(jwt).userId(),problemId,id,key,selected);
    }
    @PostMapping("/{id}/alternative-methods")
    public AttemptLearningEvidence methods(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body) {
        empty(body); return service.methods(AuthenticatedUser.from(jwt).userId(),problemId,id,key);
    }
    @GetMapping("/drills")
    public List<AttemptLearningService.PublicDrill> history(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId) {
        return service.history(AuthenticatedUser.from(jwt).userId(),problemId);
    }
    @GetMapping("/mistake-passport")
    public Map<String,Object> passport(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId) {
        return service.passport(AuthenticatedUser.from(jwt).userId(),problemId);
    }
    private static void empty(Map<String,Object> body) { if(!body.isEmpty()) throw invalid(); }
    private static com.verifiedai.sharedkernel.error.ApiProblemException invalid() { return AttemptAnalysisPolicy.error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY); }
}
