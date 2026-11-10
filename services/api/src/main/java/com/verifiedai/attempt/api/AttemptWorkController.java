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
    private final com.verifiedai.attempt.application.AttemptStepOcrCorrectionService corrections;
    public AttemptWorkController(AttemptWorkService service,com.verifiedai.attempt.application.AttemptStepOcrCorrectionService corrections) { this.service=service; this.corrections=corrections; }
    @PostMapping("/structured-work")
    public AttemptStructuredWork submit(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody AttemptWorkInput command) {
        return service.submit(AuthenticatedUser.from(jwt).userId(),problemId,key,command);
    }
    @PostMapping("/{id}/steps/{stepIndex}/correct-ocr")
    public com.verifiedai.attempt.application.AttemptStepOcrCorrectionService.Result correct(@AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID problemId,@PathVariable UUID id,@PathVariable int stepIndex,
        @RequestHeader(value="Idempotency-Key",required=false) String key,
        @RequestBody com.verifiedai.attempt.application.AttemptStepOcrCorrectionService.Input input) {
        return corrections.correct(AuthenticatedUser.from(jwt).userId(),problemId,id,stepIndex,key,input);
    }
    @GetMapping("/{id}/structured-work")
    public AttemptStructuredWork read(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id) {
        return service.read(AuthenticatedUser.from(jwt).userId(),problemId,id);
    }
}
