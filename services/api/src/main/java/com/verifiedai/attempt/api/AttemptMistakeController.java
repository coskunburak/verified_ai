package com.verifiedai.attempt.api;

import com.verifiedai.attempt.application.AttemptMistakeService;
import com.verifiedai.attempt.domain.AttemptMistakeInput;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/problem-sessions/{problemId}/attempts/{id}/classification")
public class AttemptMistakeController {
    private final AttemptMistakeService service;
    public AttemptMistakeController(AttemptMistakeService service) { this.service=service; }
    @GetMapping
    public AttemptMistakeService.View read(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id) {
        return service.read(AuthenticatedUser.from(jwt).userId(),problemId,id);
    }
    @PostMapping
    public AttemptMistakeService.View classify(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody AttemptMistakeInput input) {
        return service.classify(AuthenticatedUser.from(jwt).userId(),problemId,id,key,input);
    }
}
