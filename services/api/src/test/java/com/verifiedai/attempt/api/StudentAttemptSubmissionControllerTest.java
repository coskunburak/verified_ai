package com.verifiedai.attempt.api;

import com.fasterxml.jackson.databind.*;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

@Import(StudentAttemptControllerTest.AuthConfig.class)
@TestPropertySource(properties="verifiedai.attempt.enabled=true")
class StudentAttemptSubmissionControllerTest extends PostgresIntegrationTestSupport {
    @Value("${local.server.port}") int port;
    @Autowired IdentityApplicationService identity;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper mapper=new ObjectMapper();
    AuthSessionResult owner; UUID problem; JsonNode source;
    @BeforeEach void setup() throws Exception {
        owner=identity.signInWithApple(new AppleSignInCommand(UUID.randomUUID().toString(),"synthetic-code","nonce"));
        problem=new SolverSourceFixture(jdbc).insertSupportedParse(owner.userId(),SolverSourceFixture.equationParseJson("2*x+3=11"),1);
        canonical.canonicalize(owner.userId(),problem);
        source=mapper.readTree(request(owner.accessToken(),"GET","/context","","http-answer-key").body()).path("source");
    }
    HttpResponse<String> request(String token,String method,String suffix,String body,String key) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/problem-sessions/"+problem+"/attempts"+suffix))
            .header("Content-Type","application/json").header("Idempotency-Key",key);
        if(token!=null) builder.header("Authorization","Bearer "+token);
        return HttpClient.newHttpClient().send(builder.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String body(String answer) {
        var body=mapper.createObjectNode().put("schemaVersion","attempt-submission-v3").put("finalAnswer",answer);
        body.set("source",source); body.putArray("steps"); return body.toString();
    }
    @Test void answerAcknowledgementHistoryAndLegacyProjection() throws Exception {
        String input=body("  x = 4  ");
        var response=request(owner.accessToken(),"POST","",input,"http-answer-key");
        assertThat(response.statusCode()).isEqualTo(200);
        var saved=mapper.readTree(response.body()); String id=saved.path("id").asText();
        assertThat(saved.path("finalAnswer").asText()).isEqualTo("  x = 4  ");
        assertThat(saved.path("steps").size()).isZero(); assertThat(saved.path("analysis").isNull()).isTrue();
        assertThat(saved.path("schemaVersion").asText()).isEqualTo("student-attempt-v2");
        assertThat(mapper.readTree(request(owner.accessToken(),"POST","",input,"http-answer-key").body())).isEqualTo(saved);
        assertThat(mapper.readTree(request(owner.accessToken(),"GET","/"+id+"?representationVersion=2","","read-answer").body())).isEqualTo(saved);
        var legacy=mapper.readTree(request(owner.accessToken(),"GET","/"+id,"","read-answer").body());
        assertThat(legacy.path("schemaVersion").asText()).isEqualTo("student-attempt-v1");
        assertThat(legacy.has("finalAnswer")).isFalse(); assertThat(legacy.path("steps").get(0).path("content").asText()).isEqualTo("  x = 4  ");
        assertThat(mapper.readTree(request(owner.accessToken(),"GET","?representationVersion=2","","read-answer").body()).size()).isEqualTo(1);
        assertThat(request(owner.accessToken(),"POST","",body("x=5"),"http-answer-key").statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from learner_mistake_events where user_id=?",Integer.class,owner.userId())).isZero();
    }
    @Test void authStaleAndAdversarialPayloadsHaveStableErrors() throws Exception {
        var other=identity.signInWithApple(new AppleSignInCommand(UUID.randomUUID().toString(),"synthetic-code","nonce"));
        assertThat(request(null,"POST","",body("4"),"http-answer-key").statusCode()).isEqualTo(401);
        assertThat(request(other.accessToken(),"POST","",body("4"),"http-answer-key").statusCode()).isEqualTo(404);
        for(String malformed:List.of(body("4").replace("\"finalAnswer\":\"4\"","\"finalAnswer\":4"),
            body("4").replace("\"steps\":[]","\"steps\":[],\"steps\":[]"),
            body("4").replace("\"steps\":[]","\"steps\":[],\"userId\":\"forged\""),body("😀".repeat(129)))) {
            var response=request(owner.accessToken(),"POST","",malformed,"http-answer-key");
            assertThat(response.statusCode()).isEqualTo(422); assertThat(response.body()).contains("REQUEST_VALIDATION_FAILED");
        }
        assertThat(request(owner.accessToken(),"POST","",body("4"),"bad").statusCode()).isEqualTo(422);
        assertThat(request(owner.accessToken(),"GET","/not-a-uuid","","read-answer").statusCode()).isEqualTo(400);
        assertThat(request(owner.accessToken(),"GET","?representationVersion=3","","read-answer").statusCode()).isEqualTo(422);
        ((com.fasterxml.jackson.databind.node.ObjectNode)source).put("canonicalRevision",999);
        var stale=request(owner.accessToken(),"POST","",body("4"),"stale-http-answer");
        assertThat(stale.statusCode()).isEqualTo(409); assertThat(stale.body()).contains("ATTEMPT_STALE_SOURCE");
        assertThat(jdbc.queryForObject("select count(*) from student_attempts where user_id=?",Integer.class,owner.userId())).isZero();
    }
    @Test void malformedUtf8IsRejectedInsteadOfSilentlyReplacingLearnerText() throws Exception {
        byte[] prefix=("{\"schemaVersion\":\"attempt-submission-v3\",\"source\":"+source+",\"steps\":[],\"finalAnswer\":\"").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var bytes=new java.io.ByteArrayOutputStream(); bytes.write(prefix); bytes.write(0xC3); bytes.write(0x28); bytes.write("\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/problem-sessions/"+problem+"/attempts"))
            .header("Content-Type","application/json").header("Authorization","Bearer "+owner.accessToken())
            .header("Idempotency-Key","invalid-utf8-key").POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(422); assertThat(response.body()).contains("REQUEST_VALIDATION_FAILED");
        assertThat(jdbc.queryForObject("select count(*) from student_attempts where user_id=?",Integer.class,owner.userId())).isZero();
    }

}
