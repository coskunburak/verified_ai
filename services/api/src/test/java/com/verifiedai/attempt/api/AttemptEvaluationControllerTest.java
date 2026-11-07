package com.verifiedai.attempt.api;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties={"verifiedai.attempt.enabled=true","verifiedai.attempt.structured-work-enabled=true","verifiedai.attempt.evaluation-enabled=false"})
class AttemptEvaluationControllerTest extends AttemptWorkControllerTest {
    @Test void explicitEvaluationStrictInputAuthorizationAndDisabledHistory() throws Exception {
        var saved=mapper.readTree(request(owner.accessToken(),"POST","/structured-work",body(),"evaluation-work-key").body());
        String route="/"+saved.path("attemptId").asText()+"/evaluation";
        String valid="{\"schemaVersion\":\"attempt-evaluation-request-v1\",\"source\":"+source+",\"workRevision\":1,\"realDomainConfirmed\":false}";
        var read=request(owner.accessToken(),"GET",route,"","evaluation-read");
        assertThat(read.statusCode()).isEqualTo(200);
        com.verifiedai.sharedkernel.contracts.VersionedJsonContract.validate("attempt-evaluation-view-v1",read.body());
        assertThat(mapper.readTree(read.body()).path("status").asText()).isEqualTo("NOT_EVALUATED");
        assertThat(mapper.readTree(read.body()).path("creationEnabled").asBoolean()).isFalse();
        assertThat(request(owner.accessToken(),"POST",route,valid,"evaluation-disabled").statusCode()).isEqualTo(503);
        assertThat(request(null,"GET",route,"","evaluation-read").statusCode()).isEqualTo(401);
        var other=identity.signInWithApple(new com.verifiedai.identity.application.AppleSignInCommand(UUID.randomUUID().toString(),"synthetic-code","nonce"));
        assertThat(request(other.accessToken(),"GET",route,"","evaluation-read").statusCode()).isEqualTo(404);
        for(String invalid:List.of("", "{}", valid.replace("\"workRevision\":1","\"workRevision\":\"1\""),
            valid.replace("\"workRevision\":1","\"workRevision\":1,\"ownerId\":\"forged\""),
            valid.replace("\"workRevision\":1","\"workRevision\":1,\"workRevision\":1"))) {
            var response=request(owner.accessToken(),"POST",route,invalid,"evaluation-invalid");
            assertThat(response.statusCode()).isEqualTo(422); assertThat(response.body()).contains("REQUEST_VALIDATION_FAILED");
        }
        assertThat(request(owner.accessToken(),"POST",route," ".repeat(16385),"evaluation-oversized").statusCode()).isEqualTo(413);
    }
}
