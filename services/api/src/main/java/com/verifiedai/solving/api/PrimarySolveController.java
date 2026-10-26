package com.verifiedai.solving.api;

import com.verifiedai.solving.application.PrimarySolveService;
import com.verifiedai.solving.application.SolveCommand;
import com.verifiedai.solving.application.SolveView;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/problem-sessions/{sessionId}/solves")
class PrimarySolveController {
    private final PrimarySolveService service;
    PrimarySolveController(PrimarySolveService service) { this.service=service; }
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    SolveView start(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID sessionId,
        @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return service.start(command(jwt,sessionId,key,null));
    }
    @PostMapping("/{jobId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    SolveView retry(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID sessionId,@PathVariable UUID jobId,
        @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return service.start(command(jwt,sessionId,key,jobId));
    }
    @GetMapping("/{jobId}")
    SolveView get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID sessionId,@PathVariable UUID jobId) {
        return service.get(AuthenticatedUser.from(jwt).userId(),sessionId,jobId);
    }
    @PostMapping("/{jobId}/cancel")
    SolveView cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID sessionId,@PathVariable UUID jobId) {
        return service.cancel(AuthenticatedUser.from(jwt).userId(),sessionId,jobId);
    }
    private SolveCommand command(Jwt jwt,UUID session,String key,UUID retry) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,128}")) throw new ApiProblemException(HttpStatus.BAD_REQUEST,
            ApiErrorCode.IDEMPOTENCY_KEY_REQUIRED,"A bounded Idempotency-Key is required",false,"RETRY");
        return new SolveCommand(AuthenticatedUser.from(jwt).userId(),session,key,retry);
    }
}
