package com.verifiedai.tutoring.api;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import com.verifiedai.tutoring.application.TutorSessionService;
import com.verifiedai.tutoring.domain.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/problem-sessions/{problemId}/tutor-sessions")
public class TutorSessionController {
    private final TutorSessionService service;
    public TutorSessionController(TutorSessionService service) { this.service=service; }
    @PostMapping
    public TutorSession create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody(required=false) Map<String,Object> body) {
        empty(body); return service.create(AuthenticatedUser.from(jwt).userId(),problemId,key);
    }
    @GetMapping
    public List<TutorSession> list(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,
        @RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="30") int limit) {
        return service.list(AuthenticatedUser.from(jwt).userId(),problemId,offset,limit);
    }
    @GetMapping("/{id}")
    public TutorSession read(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id) {
        return service.read(AuthenticatedUser.from(jwt).userId(),problemId,id);
    }
    @GetMapping("/{id}/messages")
    public List<TutorMessage> messages(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id,
        @RequestParam(defaultValue="0") long after,@RequestParam(defaultValue="50") int limit) {
        return service.messages(AuthenticatedUser.from(jwt).userId(),problemId,id,after,limit);
    }
    @PostMapping("/{id}/messages")
    public TutorMessage append(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body) {
        if(body.size()!=1 || !(body.get("content") instanceof String))
            throw TutorPolicy.failure(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
        return service.append(AuthenticatedUser.from(jwt).userId(),problemId,id,key,(String)body.get("content"));
    }
    @PostMapping("/{id}/close")
    public TutorSession close(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id,
        @RequestBody(required=false) Map<String,Object> body) {
        empty(body); return service.close(AuthenticatedUser.from(jwt).userId(),problemId,id);
    }
    private static void empty(Map<String,Object> body) {
        if(body!=null && !body.isEmpty())
            throw TutorPolicy.failure(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
