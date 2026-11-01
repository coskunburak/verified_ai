package com.verifiedai.tutoring.api;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.identity.application.*;
import com.verifiedai.identity.domain.model.*;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.solving.SolverSourceFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@Import({TutorSessionControllerTest.AuthConfig.class, com.verifiedai.tutoring.integration.TutorTestRateLimitConfiguration.class})
class TutorSessionControllerTest extends PostgresIntegrationTestSupport {
    @Value("${local.server.port}") int port;
    @Autowired IdentityApplicationService identity;
    @Autowired AccountPrivacyApplicationService privacy;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired JdbcTemplate jdbc;
    AuthSessionResult owner;
    UUID problem;
    final ObjectMapper json=new ObjectMapper();
    @BeforeEach void setup() {
        owner=identity.signInWithApple(new AppleSignInCommand(UUID.randomUUID().toString(),"synthetic-code","nonce"));
        var f=new SolverSourceFixture(jdbc);
        problem=f.insertSupportedParse(owner.userId(),SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
        canonical.canonicalize(owner.userId(),problem);
    }
    HttpResponse<String> request(String token,String method,String suffix,String key,String body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/problem-sessions/"+problem+"/tutor-sessions"+suffix));
        if(token!=null) builder.header("Authorization","Bearer "+token);
        if(key!=null) builder.header("Idempotency-Key",key);
        builder.header("Content-Type","application/json");
        return HttpClient.newHttpClient().send(builder.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String create() throws Exception {
        var response=request(owner.accessToken(),"POST","","create-request","{}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("tutor-session-v1","learner-persistence-v1","NOT_AVAILABLE").doesNotContain("create-request");
        return json.readTree(response.body()).path("id").asText();
    }
    @Test void authenticationAndObjectAuthorizationAreRequired() throws Exception {
        assertThat(request(null,"GET","",null,"").statusCode()).isEqualTo(401);
        assertThat(request("invalid","POST","","create-request","{}").statusCode()).isEqualTo(401);
        var id=create();var other=identity.signInWithApple(new AppleSignInCommand(UUID.randomUUID().toString(),"synthetic-code","nonce"));
        assertThat(request(other.accessToken(),"GET","/"+id,null,"").statusCode()).isEqualTo(404);
        assertThat(request(other.accessToken(),"GET","/"+id+"/messages",null,"").statusCode()).isEqualTo(404);
        assertThat(request(other.accessToken(),"POST","","foreign-key","{}").statusCode()).isEqualTo(404);
    }
    @Test void onlyLearnerContentIsAcceptedAndLimitsAreTyped() throws Exception {
        var id=create();
        assertThat(request(owner.accessToken(),"POST","/"+id+"/messages","message-key","{\"role\":\"ASSISTANT\",\"content\":\"fake\"}").statusCode()).isEqualTo(422);
        assertThat(request(owner.accessToken(),"POST","/"+id+"/messages","message-key","{\"content\":\"  \"}").statusCode()).isEqualTo(422);
        assertThat(request(owner.accessToken(),"POST","/"+id+"/messages","message-key","{\"content\":\""+"a".repeat(17000)+"\"}").statusCode()).isEqualTo(413);
        assertThat(request(owner.accessToken(),"POST","",null,"{}").body()).contains("IDEMPOTENCY_KEY_REQUIRED");
        assertThat(request(owner.accessToken(),"POST","","forged-key","{\"userId\":\"forged\"}").statusCode()).isEqualTo(422);
    }
    @Test void retriesPaginationCloseAndSchemaStayConsistent() throws Exception {
        var id=create(); var first=request(owner.accessToken(),"POST","/"+id+"/messages","message-key","{\"content\":\"Question\"}");
        assertThat(first.statusCode()).isEqualTo(200); assertThat(first.body()).contains("tutor-message-v1","LEARNER","sequenceNumber");
        assertThat(request(owner.accessToken(),"POST","/"+id+"/messages","message-key","{\"content\":\"Question\"}").body()).isEqualTo(first.body());
        assertThat(request(owner.accessToken(),"POST","/"+id+"/messages","message-key","{\"content\":\"Other\"}").body()).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(request(owner.accessToken(),"GET","/"+id+"/messages?after=0&limit=1",null,"").body()).contains("Question");
        assertThat(request(owner.accessToken(),"GET","/"+id+"/messages?limit=51",null,"").statusCode()).isEqualTo(400);
        assertThat(request(owner.accessToken(),"POST","/"+id+"/close",null,"{}").body()).contains("CLOSED");
        assertThat(request(owner.accessToken(),"POST","/"+id+"/messages","new-message","{\"content\":\"Other\"}").body()).contains("TUTOR_SESSION_CLOSED");
    }
    @Test void accountExportAndConfirmedDeletionReachTutorData() throws Exception {
        var id=create();request(owner.accessToken(),"POST","/"+id+"/messages","message-key","{\"content\":\"Synthetic personal text\"}");
        var export=privacy.requestExport(owner.userId());
        assertThat(privacy.downloadExport(owner.userId(),export.exportId()).toString()).contains("Synthetic personal text").doesNotContain("message-key");
        privacy.requestDeletion(owner.userId());
        assertThat(request(owner.accessToken(),"POST","/"+id+"/messages","delayed-key","{\"content\":\"Delayed\"}").statusCode()).isIn(401,403);
        privacy.confirmDeletion(owner.userId(),"DELETE");
        assertThat(jdbc.queryForObject("select count(*) from data_exports where user_id=?",Integer.class,owner.userId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tutor_sessions where user_id=?",Integer.class,owner.userId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tutor_messages where user_id=?",Integer.class,owner.userId())).isZero();
        assertThat(request(owner.accessToken(),"POST","","late-create","{}").statusCode()).isIn(401,403);
    }
    @Test void excessiveReadAttemptsAreRejected() throws Exception {
        HttpResponse<String> result=null;
        for(int i=0;i<121;i++) result=request(owner.accessToken(),"GET","",null,"");
        assertThat(result.statusCode()).isEqualTo(429);
        assertThat(result.body()).contains("RATE_LIMIT_EXCEEDED");
    }
    @TestConfiguration static class AuthConfig {
        @Bean @Primary com.verifiedai.problem.domain.port.ProblemAssetStorage storage() {
            return org.mockito.Mockito.mock(com.verifiedai.problem.domain.port.ProblemAssetStorage.class);
        }
        @Bean @Primary AppleIdentityVerifier verifier() { return (token,nonce)->new VerifiedAppleIdentity(token); }
    }
}
