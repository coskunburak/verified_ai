package com.verifiedai.attempt.api;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.identity.application.*;
import com.verifiedai.identity.domain.model.*;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.solving.SolverSourceFixture;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@Import(StudentAttemptControllerTest.AuthConfig.class)
class StudentAttemptControllerTest extends PostgresIntegrationTestSupport {
    @Value("${local.server.port}") int port;
    @Autowired IdentityApplicationService identity;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired JdbcTemplate jdbc;
    AuthSessionResult owner; UUID problem;
    @BeforeEach void setup() {
        owner=identity.signInWithApple(new AppleSignInCommand(UUID.randomUUID().toString(),"synthetic-code","nonce"));
        problem=new SolverSourceFixture(jdbc).insertSupportedParse(owner.userId(),SolverSourceFixture.equationParseJson("2*x+3=11"),1);
        canonical.canonicalize(owner.userId(),problem);
    }
    HttpResponse<String> request(String token,String method,String suffix,String body) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/problem-sessions/"+problem+"/attempts"+suffix))
            .header("Content-Type","application/json").header("Idempotency-Key","synthetic-attempt");
        if(token!=null)b.header("Authorization","Bearer "+token);
        return HttpClient.newHttpClient().send(b.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void authenticationAndBolaAreEnforced() throws Exception {
        var other=identity.signInWithApple(new AppleSignInCommand(UUID.randomUUID().toString(),"synthetic-code","nonce"));
        assertThat(request(null,"GET","/context","").statusCode()).isEqualTo(401);
        assertThat(request(other.accessToken(),"GET","/context","").statusCode()).isEqualTo(404);
        assertThat(request(owner.accessToken(),"GET","/context","").body()).contains("UNAVAILABLE","\"enabled\":false");
    }
    @Test void strictAuthorityFieldsDuplicatesAndByteLimitsReject() throws Exception {
        assertThat(request(owner.accessToken(),"POST","","{\"steps\":[],\"userId\":\"forged\"}").statusCode()).isEqualTo(422);
        assertThat(request(owner.accessToken(),"POST","","{\"steps\":[],\"steps\":[]}").statusCode()).isEqualTo(422);
        assertThat(request(owner.accessToken(),"POST","","{\"steps\":[\""+"x".repeat(17000)+"\"]}").statusCode()).isEqualTo(413);
    }
    @Test void disabledSubmissionReturnsProblemDetails() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var context=request(owner.accessToken(),"GET","/context","");
        assertThat(context.statusCode()).isEqualTo(200);
        var source=json.readTree(context.body()).path("source");
        assertThat(source.isObject()).isTrue();
        var response=request(owner.accessToken(),"POST","","{\"source\":"+source+",\"steps\":[\"x=4\"]}");
        assertThat(response.statusCode()).isEqualTo(503);assertThat(response.body()).contains("ATTEMPT_UNAVAILABLE","traceId").doesNotContain("x=4");
    }
    @Test void learningRoutesEnforceOwnershipAndRejectClientAuthority() throws Exception {
        var other=identity.signInWithApple(new AppleSignInCommand(UUID.randomUUID().toString(),"synthetic-code","nonce"));
        assertThat(request(null,"GET","/mistake-passport","").statusCode()).isEqualTo(401);
        assertThat(request(other.accessToken(),"GET","/mistake-passport","").statusCode()).isEqualTo(404);
        assertThat(request(owner.accessToken(),"POST","/"+UUID.randomUUID()+"/counterfactual-twin/answer","{\"selectedOption\":1,\"correct\":true}").statusCode()).isEqualTo(422);
        assertThat(request(owner.accessToken(),"POST","/extractions","{\"assetSessionId\":\"bad\",\"provider\":\"forged\"}").statusCode()).isEqualTo(422);
    }
    @Test void sourceComparisonAuthenticationAuthorityAndDefaultOff() throws Exception {
        var other=identity.signInWithApple(new AppleSignInCommand(UUID.randomUUID().toString(),"synthetic-code","nonce"));
        var source=new com.fasterxml.jackson.databind.ObjectMapper().readTree(request(owner.accessToken(),"GET","/context","").body()).path("source");
        String body="{\"schemaVersion\":\"method-comparison-v2\",\"source\":"+source+"}";
        assertThat(sourceRequest(null,body).statusCode()).isEqualTo(401);
        assertThat(sourceRequest(other.accessToken(),body).statusCode()).isEqualTo(404);
        assertThat(sourceRequest(owner.accessToken(),body).statusCode()).isEqualTo(503);
        assertThat(sourceRequest(owner.accessToken(),"{\"schemaVersion\":\"method-comparison-v2\",\"source\":"+source+",\"verified\":true}").statusCode()).isEqualTo(422);
        assertThat(sourceRequest(owner.accessToken(),"{\"payload\":\""+"x".repeat(17000)+"\"}").statusCode()).isEqualTo(413);
    }
    HttpResponse<String> sourceRequest(String token,String body) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/problem-sessions/"+problem+"/alternative-methods"))
            .header("Content-Type","application/json").header("Idempotency-Key","synthetic-comparison");
        if(token!=null)b.header("Authorization","Bearer "+token);
        return HttpClient.newHttpClient().send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @TestConfiguration static class AuthConfig {
        // These HTTP tests exercise authorization/contracts, without an owner-managed Redis dependency.
        @Bean @Primary com.verifiedai.sharedkernel.ratelimit.RateLimiter rateLimiter() {
            return (policy,key)->com.verifiedai.sharedkernel.ratelimit.RateLimitDecision.allow();
        }
        @Bean @Primary com.verifiedai.problem.domain.port.ProblemAssetStorage storage() { return org.mockito.Mockito.mock(com.verifiedai.problem.domain.port.ProblemAssetStorage.class); }
        @Bean @Primary AppleIdentityVerifier verifier() { return (token,nonce)->new VerifiedAppleIdentity(token); }
    }
}
