package com.verifiedai.attempt.api;

import com.verifiedai.problem.application.attempt.*;
import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.verifiedai.verification.application.VerificationSourceReader;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import com.verifiedai.sharedkernel.ratelimit.*;
import com.verifiedai.sharedkernel.error.*;
import com.verifiedai.attempt.domain.AttemptAnalysisPolicy;
import java.util.*;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@RestController
@RequestMapping("/api/v1/problem-sessions/{problemId}/attempts/extractions")
public class AttemptExtractionController {
    private final SolveSourceAccess sources;
    private final VerificationSourceReader reader;
    private final AttemptExtractionReader extraction;
    private final ManualStepNormalizer normalizer;
    private final RateLimiter limiter;
    private final TransactionTemplate tx;
    public AttemptExtractionController(SolveSourceAccess sources,VerificationSourceReader reader,AttemptExtractionReader extraction,
        ManualStepNormalizer normalizer,RateLimiter limiter,PlatformTransactionManager manager) {
        this.sources=sources; this.reader=reader; this.extraction=extraction; this.normalizer=normalizer; this.limiter=limiter;
        tx=new TransactionTemplate(manager); tx.setTimeout(5);
    }
    @PostMapping
    public AttemptExtractionReader.Extraction extract(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@RequestBody Map<String,Object> body) {
        UUID owner=AuthenticatedUser.from(jwt).userId(); UUID asset;
        try {
            if(!body.keySet().equals(Set.of("assetSessionId")) || !(body.get("assetSessionId") instanceof String value)) throw new IllegalArgumentException();
            asset=UUID.fromString(value);
        } catch(IllegalArgumentException ex) { throw AttemptAnalysisPolicy.error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY); }
        if(!limiter.check(new RateLimitPolicy("attempt-extraction",20,Duration.ofMinutes(1),true),owner.toString()).allowed())
            throw AttemptAnalysisPolicy.error(ApiErrorCode.RATE_LIMIT_EXCEEDED,HttpStatus.TOO_MANY_REQUESTS);
        // Reads existing gateway evidence only: no extra paid request and natural job idempotency.
        return tx.execute(t->{
            sources.lockOwner(owner,problemId);
            var source=sources.current(owner,problemId);
            if(source.canonicalJson().contains("canonical-problem-v3")) return extraction.read(owner,asset,AttemptSourceDocument.variables(AttemptSourceDocument.read(source)),normalizer);
            var parsed=reader.readForExecution(source); return extraction.read(owner,asset,parsed.variables().keySet(),normalizer);
        });
    }
}
