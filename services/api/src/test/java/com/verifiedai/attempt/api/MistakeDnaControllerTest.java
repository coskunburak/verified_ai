package com.verifiedai.attempt.api;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties={"verifiedai.mistake.dna-enabled=true","verifiedai.attempt.enabled=true","verifiedai.attempt.structured-work-enabled=true"})
class MistakeDnaControllerTest extends AttemptWorkControllerTest {
    HttpResponse<String> dnaRequest(String token,String method,String route,String body,String key) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/mistakes"+route)).header("Content-Type","application/json");
        if(token!=null) b.header("Authorization","Bearer "+token); if(key!=null) b.header("Idempotency-Key",key);
        return HttpClient.newHttpClient().send(b.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void authenticationStrictPayloadAndFreeTierAreEnforced() throws Exception {
        assertThat(dnaRequest(null,"GET","/dna","",null).statusCode()).isEqualTo(401);
        var read=dnaRequest(owner.accessToken(),"GET","/dna","",null);
        assertThat(read.statusCode()).isEqualTo(200);
        com.verifiedai.sharedkernel.contracts.VersionedJsonContract.validate("mistake-dna-view-v1",read.body());
        assertThat(mapper.readTree(read.body()).path("limited").asBoolean()).isTrue();
        assertThat(mapper.readTree(read.body()).path("entries").size()).isZero();
        String id=UUID.randomUUID().toString();
        String body="{\"schemaVersion\":\"mistake-dispute-request-v1\",\"evaluationId\":\""+UUID.randomUUID()+"\",\"workRevision\":1}";
        assertThat(dnaRequest(owner.accessToken(),"POST","/"+id+"/dispute",body,"missing-diagnosis").statusCode()).isEqualTo(404);
        assertThat(dnaRequest(owner.accessToken(),"POST","/"+id+"/dispute",body,null).statusCode()).isEqualTo(422);
        for(String bad:List.of("{}",body.replace("\"workRevision\":1","\"workRevision\":\"1\""),body.replace("\"workRevision\":1","\"workRevision\":1,\"authority\":\"USER\""),body.replace("\"workRevision\":1","\"workRevision\":1,\"workRevision\":1")))
            assertThat(dnaRequest(owner.accessToken(),"POST","/"+id+"/dispute",bad,"bad-diagnosis-key").statusCode()).isEqualTo(422);
        assertThat(dnaRequest(owner.accessToken(),"POST","/"+id+"/dispute"," ".repeat(16385),"oversized-dispute").statusCode()).isEqualTo(413);
    }
    @Test void ocrOwnershipAndRequestValidationUseExistingAttemptRoute() throws Exception {
        var command=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(body());
        // A manual attempt may never be relabeled as OCR evidence by this operation.
        var saved=mapper.readTree(request(owner.accessToken(),"POST","/structured-work",command.toString(),"ocr-http-work").body());
        var route="/"+saved.path("attemptId").asText()+"/steps/1/correct-ocr";
        String body="{\"schemaVersion\":\"attempt-ocr-correction-request-v1\",\"source\":"+source+",\"workRevision\":1,\"correctedText\":\"x=4\"}";
        assertThat(request(owner.accessToken(),"POST",route,body,"ocr-http-key").statusCode()).isEqualTo(422);
        var other=identity.signInWithApple(new com.verifiedai.identity.application.AppleSignInCommand(UUID.randomUUID().toString(),"synthetic-code","nonce"));
        assertThat(request(other.accessToken(),"POST",route,body,"ocr-http-key").statusCode()).isEqualTo(404);
        assertThat(request(owner.accessToken(),"POST",route,body.replace("\"workRevision\":1","\"workRevision\":1,\"confidence\":0.99"),"ocr-http-key").statusCode()).isEqualTo(422);
    }
}
