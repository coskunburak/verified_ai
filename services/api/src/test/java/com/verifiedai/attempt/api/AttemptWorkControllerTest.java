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
@TestPropertySource(properties={"verifiedai.attempt.enabled=true","verifiedai.attempt.structured-work-enabled=true"})
class AttemptWorkControllerTest extends PostgresIntegrationTestSupport {
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
    String body() throws Exception {
        try(var stream=getClass().getResourceAsStream("/schemas/fixtures/attempt-structured-work-request-v1.json")) {
            var json=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(stream); json.set("source",source); return json.toString();
        }
    }
    @Test void structuredHttpContractAndLegacyHistoryRemainSeparate() throws Exception {
        String body=body();
        var response=request(owner.accessToken(),"POST","/structured-work",body,"http-work-key");
        assertThat(response.statusCode()).isEqualTo(200);
        var saved=mapper.readTree(response.body()); String id=saved.path("attemptId").asText();
        com.verifiedai.sharedkernel.contracts.VersionedJsonContract.validate("attempt-structured-work-v1",response.body());
        assertThat(saved.path("ownerId").asText()).isEqualTo(owner.userId().toString());
        assertThat(saved.path("trainingEligible").asBoolean()).isFalse(); assertThat(saved.has("analysis")).isFalse();
        assertThat(mapper.readTree(request(owner.accessToken(),"POST","/structured-work",body,"http-work-key").body())).isEqualTo(saved);
        assertThat(mapper.readTree(request(owner.accessToken(),"GET","/"+id+"/structured-work","","read-work").body())).isEqualTo(saved);
        var legacy=mapper.readTree(request(owner.accessToken(),"GET","/"+id,"","read-work").body());
        assertThat(legacy.path("schemaVersion").asText()).isEqualTo("student-attempt-v1");
        assertThat(legacy.path("analysis").isNull()).isTrue(); assertThat(legacy.path("steps").size()).isEqualTo(2);
        assertThat(request(owner.accessToken(),"GET","/"+UUID.randomUUID()+"/structured-work","","read-work").statusCode()).isEqualTo(404);
    }
    @Test void authenticationBolaStaleAuthorityAndPayloadLimits() throws Exception {
        String body=body();
        var other=identity.signInWithApple(new AppleSignInCommand(UUID.randomUUID().toString(),"synthetic-code","nonce"));
        assertThat(request(null,"POST","/structured-work",body,"http-work-key").statusCode()).isEqualTo(401);
        assertThat(request(other.accessToken(),"POST","/structured-work",body,"http-work-key").statusCode()).isEqualTo(404);
        for(String malformed:List.of(body.replace("\"engine\":\"MANUAL\"","\"engine\":\"MANUAL\",\"engine\":\"MANUAL\""),
            body.replace("\"engine\":\"MANUAL\"","\"engine\":\"MANUAL\",\"provider\":\"forged\""),
            body.replace("\"confirmed\":true","\"confirmed\":\"true\""))) {
            var r=request(owner.accessToken(),"POST","/structured-work",malformed,"http-work-key");
            assertThat(r.statusCode()).isEqualTo(422); assertThat(r.body()).contains("REQUEST_VALIDATION_FAILED","traceId");
        }
        assertThat(request(owner.accessToken(),"POST","/structured-work"," ".repeat(16385),"http-work-key").statusCode()).isEqualTo(413);
        var authority=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(body); authority.put("provider","forged");
        assertThat(request(owner.accessToken(),"POST","/structured%2Dwork",authority.toString(),"encoded-work-key").statusCode()).isIn(400,422);
        var changed=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(body);
        ((com.fasterxml.jackson.databind.node.ObjectNode)changed.path("source")).put("canonicalRevision",999);
        assertThat(request(owner.accessToken(),"POST","/structured-work",changed.toString(),"http-work-key").statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from student_attempts where user_id=?",Integer.class,owner.userId())).isZero();
    }
}
