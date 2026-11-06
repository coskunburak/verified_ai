package com.verifiedai.attempt.api;

import com.verifiedai.attempt.application.AttemptWorkService;
import com.verifiedai.attempt.domain.*;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/problem-sessions/{problemId}/attempts")
public class AttemptWorkController {
    private final AttemptWorkService service;
    public AttemptWorkController(AttemptWorkService service) { this.service=service; }
    @PostMapping("/structured-work")
    public AttemptStructuredWork submit(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody AttemptWorkInput command) {
        return service.submit(AuthenticatedUser.from(jwt).userId(),problemId,key,command);
    }
    @GetMapping("/{id}/structured-work")
    public AttemptStructuredWork read(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id) {
        return service.read(AuthenticatedUser.from(jwt).userId(),problemId,id);
    }
}
