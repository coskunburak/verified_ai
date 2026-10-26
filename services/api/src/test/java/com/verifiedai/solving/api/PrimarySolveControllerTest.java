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
    HttpResponse<String> current(String token, UUID session) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/problem-sessions/"+session+"/solves/current"));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void currentLookupIsAuthenticatedOwnerScopedAndNeverStartsWork() throws Exception {
        var owner=login(); var other=login(); var f=new SolverSourceFixture(jdbc);
        UUID session=f.insertSupportedParse(owner.userId(),SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
        canonical.canonicalize(owner.userId(),session);
        assertThat(current(null,session).statusCode()).isEqualTo(401);
        assertThat(current(other.accessToken(),session).statusCode()).isEqualTo(404);
        var empty=current(owner.accessToken(),session);
        assertThat(empty.statusCode()).isEqualTo(200);
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(empty.body()).get("job").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from solve_jobs where problem_session_id=?",Integer.class,session)).isZero();
        var started=post(owner.accessToken(),session,"lookup-"+session);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        String job=mapper.readTree(started.body()).path("jobId").asText();
        assertThat(mapper.readTree(current(owner.accessToken(),session).body()).path("job").path("jobId").asText()).isEqualTo(job);
        f.insertSupportedParse(owner.userId(),session,SolverSourceFixture.equationParseJson("x + 2 = 4"),2);
        canonical.canonicalize(owner.userId(),session);
        assertThat(mapper.readTree(current(owner.accessToken(),session).body()).path("job").path("state").asText()).isEqualTo("STALE");
        assertThat(jdbc.queryForObject("select count(*) from solve_jobs where problem_session_id=?",Integer.class,session)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select state from solve_jobs where id=cast(? as uuid)",String.class,job)).isEqualTo("QUEUED");
    }
    @TestConfiguration static class AuthConfig {
        @Bean @Primary AppleIdentityVerifier verifier() { return (token,nonce)->new VerifiedAppleIdentity(token); }
    }
}
