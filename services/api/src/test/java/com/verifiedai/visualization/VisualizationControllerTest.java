package com.verifiedai.visualization;
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

@Import(VisualizationControllerTest.AuthConfig.class)
class VisualizationControllerTest extends PostgresIntegrationTestSupport {
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
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/problem-sessions/"+problem+"/visualizations"+suffix)).header("Content-Type","application/json");
        if(token!=null)b.header("Authorization","Bearer "+token);
        return HttpClient.newHttpClient().send(b.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void authenticationBolaAndDefaultOff() throws Exception {
        var other=identity.signInWithApple(new AppleSignInCommand(UUID.randomUUID().toString(),"synthetic-code","nonce"));
        assertThat(request(null,"GET","/context","").statusCode()).isEqualTo(401);
        assertThat(request(other.accessToken(),"GET","/context","").statusCode()).isEqualTo(404);
        var context=request(owner.accessToken(),"GET","/context","");
        assertThat(context.statusCode()).isEqualTo(200); assertThat(context.body()).contains("\"enabled\":false");
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var query=mapper.createObjectNode().put("schemaVersion","math-visualization-source-v1").put("type","FUNCTION").put("resolution",48);
        query.set("source",mapper.readTree(context.body()).path("source"));
        query.putObject("viewport").put("xMin",-10).put("xMax",10).put("yMin",-10).put("yMax",10);
        assertThat(request(owner.accessToken(),"POST","",query.toString()).statusCode()).isEqualTo(503);
    }
    @Test void duplicatesAuthorityAndBytesAreRejectedBeforeDispatch() throws Exception {
        assertThat(request(owner.accessToken(),"POST","","{\"type\":\"FUNCTION\",\"type\":\"EQUATION\"}").statusCode()).isEqualTo(422);
        assertThat(request(owner.accessToken(),"POST","","{\"verified\":true}").statusCode()).isEqualTo(422);
        assertThat(request(owner.accessToken(),"POST","","{\"payload\":\""+"x".repeat(17000)+"\"}").statusCode()).isEqualTo(413);
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
