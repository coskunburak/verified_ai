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
    /** Strict typed request; no scalar coercion, unknown field or client authority. */
    public record TurnRequest(String content, SocraticPolicy.Intent intent) {
        @com.fasterxml.jackson.annotation.JsonCreator(mode=com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
        public static TurnRequest parse(Map<String,Object> body) {
            // Transport-neutral values: Spring Boot 4 uses Jackson 3, while the bounded
            // internal JSON/schema validator deliberately uses Jackson 2.
            if(body==null || !(body.get("content") instanceof String)) invalidTurn();
            if(!Set.of("content","intent").containsAll(body.keySet())) invalidTurn();
            SocraticPolicy.Intent intent=SocraticPolicy.Intent.UNKNOWN;
            if(body.containsKey("intent")) {
                if(!(body.get("intent") instanceof String)) invalidTurn();
                try { intent=SocraticPolicy.Intent.valueOf((String)body.get("intent")); }
                catch(IllegalArgumentException ex) { invalidTurn(); }
            }
            return new TurnRequest((String)body.get("content"),intent);
        }
        private static void invalidTurn() {
            throw TutorPolicy.failure(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
    @PostMapping("/{id}/turns")
    public TutorTurn turn(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody TurnRequest body) {
        return service.submitTurn(AuthenticatedUser.from(jwt).userId(),problemId,id,key,body.content(),body.intent());
    }
    @GetMapping("/{id}/turns")
    public List<TutorTurn> turns(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id,
        @RequestParam(defaultValue="0") long after,@RequestParam(defaultValue="50") int limit) {
        return service.turns(AuthenticatedUser.from(jwt).userId(),problemId,id,after,limit);
    }
    // Validate these additive commands after transport decoding so semantic rejections keep 422.
    // Throwing from a Jackson creator wraps the domain error as HttpMessageNotReadableException (400).
    public record HintRequest(HintPolicy.Level level) {
        public static HintRequest parse(Map<String,Object> body) {
            if(body==null || body.size()!=1 || !(body.get("level") instanceof String)) invalid();
            try { return new HintRequest(HintPolicy.Level.valueOf((String)body.get("level"))); }
            catch(IllegalArgumentException ex) { invalid(); return null; }
        }
    }
    public record RevealRequest(boolean confirmed) {
        public static RevealRequest parse(Map<String,Object> body) {
            if(body==null || body.size()!=1 || !Boolean.TRUE.equals(body.get("confirmed"))) invalid();
            return new RevealRequest(true);
        }
    }
    private static void invalid() {
        throw TutorPolicy.failure(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
    }
    @GetMapping("/{id}/hints")
    public TutorHint.History hints(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id) {
        return service.hints(AuthenticatedUser.from(jwt).userId(),problemId,id);
    }
    @PostMapping("/{id}/hints")
    public TutorHint hint(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body) {
        var request=HintRequest.parse(body);
        return service.requestHint(AuthenticatedUser.from(jwt).userId(),problemId,id,key,request.level());
    }
    @PostMapping("/{id}/reveal")
    public void reveal(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@PathVariable UUID id,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body) {
        var request=RevealRequest.parse(body);
        service.reveal(AuthenticatedUser.from(jwt).userId(),problemId,id,key,request.confirmed());
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
