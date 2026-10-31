package com.verifiedai.verification.api;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.identity.application.*;
import com.verifiedai.identity.domain.model.*;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.solving.SolverSourceFixture;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@Import(VerificationDecisionControllerTest.AuthConfig.class)
class VerificationDecisionControllerTest extends PostgresIntegrationTestSupport {
    @Value("${local.server.port}") int port;
    @Autowired IdentityApplicationService identity;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired JdbcTemplate jdbc;
    AuthSessionResult login() { return identity.signInWithApple(new AppleSignInCommand(UUID.randomUUID().toString(), "unused-code", "nonce")); }
    HttpResponse<String> request(String token, UUID session, boolean post, String key) throws Exception {
        var path = post ? "/solutions/" + UUID.randomUUID() + "/verification/" + UUID.randomUUID()
            : "/solves/" + UUID.randomUUID() + "/verification";
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/problem-sessions/" + session + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (key != null) builder.header("Idempotency-Key", key);
        return HttpClient.newHttpClient().send((post ? builder.POST(HttpRequest.BodyPublishers.noBody()) : builder.GET()).build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void authenticatedReadReturnsExplicitNullAndCannotCreateJobsUsageOrDecisions() throws Exception {
        var owner = login(); var other = login(); var fixture = new SolverSourceFixture(jdbc);
        var session = fixture.insertSupportedParse(owner.userId(), SolverSourceFixture.equationParseJson("x + 1 = 2"), 1);
        canonical.canonicalize(owner.userId(), session);
        assertThat(request(null, session, false, null).statusCode()).isEqualTo(401);
        assertThat(request(other.accessToken(), session, false, null).statusCode()).isEqualTo(404);
        var response = request(owner.accessToken(), session, false, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body()).get("decision").isNull()).isTrue();
        for (var table : java.util.List.of("solve_jobs", "verification_decisions", "ai_usage_records"))
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id=?", Integer.class, owner.userId())).isZero();
    }
    @Test void mutationRequiresAuthenticationStrictKeyAndOwnedSolution() throws Exception {
        var owner = login(); var fixture = new SolverSourceFixture(jdbc);
        var session = fixture.insertSupportedParse(owner.userId(), SolverSourceFixture.equationParseJson("x + 1 = 2"), 1);
        canonical.canonicalize(owner.userId(), session);
        assertThat(request(null, session, true, UUID.randomUUID().toString()).statusCode()).isEqualTo(401);
        assertThat(request(owner.accessToken(), session, true, "invalid").body()).contains("IDEMPOTENCY_KEY_REQUIRED");
        assertThat(request(owner.accessToken(), session, true, UUID.randomUUID().toString()).body()).contains("RESOURCE_FORBIDDEN");
    }
    @TestConfiguration static class AuthConfig {
        @Bean @Primary AppleIdentityVerifier verifier() { return (token, nonce) -> new VerifiedAppleIdentity(token); }
    }
}
