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
@RequestMapping("/api/v1/problem-sessions/{problemId}")
public class AttemptLearningController {
    private static final com.fasterxml.jackson.databind.ObjectMapper WIRE=new com.fasterxml.jackson.databind.ObjectMapper();
    private final AttemptLearningService service;
    public AttemptLearningController(AttemptLearningService service) { this.service=service; }
    @PostMapping("/attempts/{id}/counterfactual-twin")
    public AttemptLearningService.PublicDrill create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body) {
        empty(body); return service.create(AuthenticatedUser.from(jwt).userId(),problemId,id,key);
    }
    @GetMapping("/attempts/{id}/counterfactual-twin")
    public AttemptLearningService.PublicDrill read(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id) {
        return service.drill(AuthenticatedUser.from(jwt).userId(),problemId,id);
    }
    @PostMapping("/attempts/{id}/counterfactual-twin/answer")
    public AttemptLearningService.PublicDrill answer(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body) {
        if(!body.keySet().equals(Set.of("selectedOption")) || !(body.get("selectedOption") instanceof Integer selected)) throw invalid();
        return service.answer(AuthenticatedUser.from(jwt).userId(),problemId,id,key,selected);
    }
    @PostMapping("/attempts/{id}/alternative-methods")
    public Object methods(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body) {
        if(body.equals(Map.of("schemaVersion","method-comparison-v2")))
            return wire(service.comparison(AuthenticatedUser.from(jwt).userId(),problemId,id,null,key));
        empty(body); return service.methods(AuthenticatedUser.from(jwt).userId(),problemId,id,key);
    }
    @GetMapping("/attempts/drills")
    public List<AttemptLearningService.PublicDrill> history(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId) {
        return service.history(AuthenticatedUser.from(jwt).userId(),problemId);
    }
    @GetMapping("/attempts/mistake-passport")
    public Map<String,Object> passport(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId) {
        return service.passport(AuthenticatedUser.from(jwt).userId(),problemId);
    }
    @PostMapping("/alternative-methods")
    public Map<String,Object> sourceMethods(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body) {
        AttemptSourceIdentity identity;
        try {
            var document=WIRE.valueToTree(body);
            com.verifiedai.sharedkernel.contracts.VersionedJsonContract.validate("method-comparison-source-v2",document.toString());
            var s=document.path("source");
            identity=new AttemptSourceIdentity(UUID.fromString(s.path("canonicalId").asText()),s.path("canonicalRevision").asInt(),
                UUID.fromString(s.path("parseId").asText()),s.path("parseRevision").asInt());
        } catch(IllegalArgumentException ex) { throw invalid(); }
        return wire(service.comparison(AuthenticatedUser.from(jwt).userId(),problemId,null,identity,key));
    }
    // Plain transport values keep the existing Jackson-2 canonical AST boundary
    // independent from the application's HTTP mapper version.
    private static Map<String,Object> wire(AttemptLearningService.PublicComparison result) {
        return WIRE.convertValue(result,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {});
    }
    private static void empty(Map<String,Object> body) { if(!body.isEmpty()) throw invalid(); }
    private static com.verifiedai.sharedkernel.error.ApiProblemException invalid() { return AttemptAnalysisPolicy.error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY); }
}
