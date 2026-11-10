package com.verifiedai.attempt.api;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties="verifiedai.mastery.evidence-stratified-enabled=true")
class MasteryControllerTest extends AttemptWorkControllerTest {
    HttpResponse<String> getMastery(String token,String query) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/mastery/skills"+query));
        if(token!=null) request.header("Authorization","Bearer "+token);
        return HttpClient.newHttpClient().send(request.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void authenticatedOwnerOnlyNoClientOwnerOverrideAndStableEmptyContract() throws Exception {
        assertThat(getMastery(null,"").statusCode()).isEqualTo(401);
        var result=getMastery(owner.accessToken(),"?ownerId=00000000-0000-0000-0000-000000000000");
        assertThat(result.statusCode()).isEqualTo(200);
        com.verifiedai.sharedkernel.contracts.VersionedJsonContract.validate("mastery-skills-v1",result.body());
        assertThat(mapper.readTree(result.body()).path("enabled").asBoolean()).isTrue();
        assertThat(mapper.readTree(result.body()).path("skills").size()).isZero();
        assertThat(mapper.readTree(result.body()).has("userId")).isFalse();
    }
}
