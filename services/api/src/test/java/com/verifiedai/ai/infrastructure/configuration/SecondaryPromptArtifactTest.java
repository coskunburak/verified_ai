package com.verifiedai.ai.infrastructure.configuration;

import com.verifiedai.ai.application.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import static org.assertj.core.api.Assertions.*;

class SecondaryPromptArtifactTest {
    @Test void immutableSecondaryPromptAndExistingCandidateSchemaResolveTogether() throws Exception {
        var resources=new DefaultResourceLoader();
        var validator=new PromptRegistryValidator(PromptRegistry.defaults(),OutputSchemaRegistry.defaults(),
            new ClasspathPromptArtifactResolver(resources),new ClasspathOutputSchemaArtifactResolver(resources));
        var route=new FixtureSecondaryRoute(true,"test",true,validator);
        var context=new AiRouteContext(AiCapability.SOLVE,"EQUATION","SOLVE_EQUATION",null,null,false,"LOW",null,null,
            java.util.Locale.ENGLISH,java.time.Duration.ofSeconds(30),0L,null,java.util.Map.of(),null,null,
            "NO_TRAINING_BY_DEFAULT",AiRouteContext.RouteRole.SECONDARY_SOLVER);
        assertThat(route.plan(context).schemaVersion()).isEqualTo("solution-candidate-v1");
        var entry=PromptRegistry.defaults().require("secondary-solver","v001");
        var bytes=resources.getResource("classpath:"+entry.promptArtifactPath()).getContentAsByteArray();
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))).isEqualTo(entry.contentSha256());
        assertThat(new String(bytes,StandardCharsets.UTF_8)).contains("canonical-problem-v1","no first solution","not proof");
        assertThat(entry.releaseStage()).isEqualTo(PromptReleaseStage.LOCAL_ONLY);
        assertThat(entry.trainingEligibility()).isEqualTo(PromptTrainingEligibility.NOT_ELIGIBLE);
        assertThatThrownBy(()->validator.validate(route.plan(context),"production")).isInstanceOf(PromptRegistryValidationException.class);
    }
}
