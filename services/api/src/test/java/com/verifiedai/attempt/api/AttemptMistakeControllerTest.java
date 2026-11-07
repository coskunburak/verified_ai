package com.verifiedai.attempt.api;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties={"verifiedai.attempt.enabled=true","verifiedai.attempt.structured-work-enabled=true","verifiedai.attempt.classification-enabled=false"})
class AttemptMistakeControllerTest extends AttemptWorkControllerTest {
    @Test void classificationStrictInputBolaAndReadOnlyDisabledCapability() throws Exception {
        var saved=mapper.readTree(request(owner.accessToken(),"POST","/structured-work",body(),"classification-work").body());
        String route="/"+saved.path("attemptId").asText()+"/classification";
        String valid="{\"schemaVersion\":\"attempt-mistake-request-v1\",\"evaluationId\":\""+UUID.randomUUID()+"\",\"source\":"+source+",\"workRevision\":1}";
        var read=request(owner.accessToken(),"GET",route,"","classification-read");
        assertThat(read.statusCode()).isEqualTo(200);
        com.verifiedai.sharedkernel.contracts.VersionedJsonContract.validate("attempt-mistake-view-v1",read.body());
        assertThat(mapper.readTree(read.body()).path("creationEnabled").asBoolean()).isFalse();
        assertThat(mapper.readTree(read.body()).path("status").asText()).isEqualTo("NOT_CLASSIFIED");
        assertThat(request(owner.accessToken(),"POST",route,valid,"classification-missing").body()).contains("ATTEMPT_CLASSIFICATION_NOT_READY");
        assertThat(request(null,"GET",route,"","classification-read").statusCode()).isEqualTo(401);
        var other=identity.signInWithApple(new com.verifiedai.identity.application.AppleSignInCommand(UUID.randomUUID().toString(),"synthetic-code","nonce"));
        assertThat(request(other.accessToken(),"GET",route,"","classification-read").statusCode()).isEqualTo(404);
        assertThat(request(other.accessToken(),"POST",route,valid,"classification-foreign").statusCode()).isEqualTo(404);
        for(String field:List.of("ownerId","mistakeCode","authority","confidence","trainingEligible")) {
            var bad=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(valid); bad.put(field,"forged");
            assertThat(request(owner.accessToken(),"POST",route,bad.toString(),"classification-invalid").statusCode()).isEqualTo(422);
        }
        for(String bad:List.of("", "{}",valid.replace("\"workRevision\":1","\"workRevision\":\"1\""),valid.replace("\"workRevision\":1","\"workRevision\":1,\"workRevision\":1")))
            assertThat(request(owner.accessToken(),"POST",route,bad,"classification-invalid").statusCode()).isEqualTo(422);
        assertThat(request(owner.accessToken(),"POST",route," ".repeat(16385),"classification-oversized").statusCode()).isEqualTo(413);
    }
}
