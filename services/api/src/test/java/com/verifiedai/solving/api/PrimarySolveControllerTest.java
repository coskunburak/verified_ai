package com.verifiedai.solving.api;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.identity.application.*;
import com.verifiedai.identity.domain.model.AppleIdentityVerifier;
import com.verifiedai.identity.domain.model.VerifiedAppleIdentity;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.solving.SolverSourceFixture;
import com.verifiedai.integration.solving.SolverFixtureConfiguration;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles({"test","solver-fixture"})
@Import({SolverFixtureConfiguration.class,PrimarySolveControllerTest.AuthConfig.class})
class PrimarySolveControllerTest extends PostgresIntegrationTestSupport {
    @Value("${local.server.port}") int port;
    @Autowired IdentityApplicationService identity;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired JdbcTemplate jdbc;
    HttpResponse<String> post(String token,UUID session,String key) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/problem-sessions/"+session+"/solves"));
        if(token!=null)b.header("Authorization","Bearer "+token);
        if(key!=null)b.header("Idempotency-Key",key);
        return HttpClient.newHttpClient().send(b.POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
    }
    AuthSessionResult login() {return identity.signInWithApple(new AppleSignInCommand(UUID.randomUUID().toString(),"unused-code","nonce"));}
    @Test void anonymousCannotStartSolve() throws Exception { assertThat(post(null,UUID.randomUUID(),"anonymous-request").statusCode()).isEqualTo(401); }
    @Test void ownerStartIsAsyncUnverifiedAndForeignOwnerCannotAccess() throws Exception {
        var owner=login();var other=login();var f=new SolverSourceFixture(jdbc);
        UUID session=f.insertSupportedParse(owner.userId(),SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
        canonical.canonicalize(owner.userId(),session);
        var response=post(owner.accessToken(),session,"controller-request");
        assertThat(response.statusCode()).isEqualTo(202);assertThat(response.body()).contains("QUEUED","has not been performed");
        assertThat(post(other.accessToken(),session,"foreign-request").statusCode()).isEqualTo(404);
        String id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body()).path("jobId").asText();
        var read=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/problem-sessions/"+session+"/solves/"+id))
            .header("Authorization","Bearer "+other.accessToken()).GET().build();
        assertThat(HttpClient.newHttpClient().send(read,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
        assertThat(post(owner.accessToken(),session,null).body()).contains("IDEMPOTENCY_KEY_REQUIRED");
    }
    @TestConfiguration static class AuthConfig {
        @Bean @Primary AppleIdentityVerifier verifier() { return (token,nonce)->new VerifiedAppleIdentity(token); }
    }
}
