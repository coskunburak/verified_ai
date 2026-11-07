package com.verifiedai.attempt.api;

import com.verifiedai.attempt.application.AttemptEvaluationService;
import com.verifiedai.attempt.domain.AttemptEvaluationInput;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/problem-sessions/{problemId}/attempts/{id}/evaluation")
public class AttemptEvaluationController {
    private final AttemptEvaluationService service;
    public AttemptEvaluationController(AttemptEvaluationService service) { this.service=service; }
    @PostMapping
    public AttemptEvaluationService.View evaluate(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody AttemptEvaluationInput input) {
        return service.evaluate(AuthenticatedUser.from(jwt).userId(),problemId,id,key,input);
    }
    @GetMapping
    public AttemptEvaluationService.View read(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id) {
        return service.read(AuthenticatedUser.from(jwt).userId(),problemId,id);
    }
}
