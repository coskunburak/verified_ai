package com.verifiedai.attempt.application;

import com.verifiedai.attempt.domain.*;
import com.verifiedai.problem.application.attempt.AttemptExtractionReader;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AttemptWorkProvenanceTest {
    @Test void gatewayEvidenceIsComparedNotTrustedFromClient() throws Exception {
        UUID asset=UUID.randomUUID(),job=UUID.randomUUID(),line=UUID.randomUUID();
        var box=new AttemptWorkInput.Box(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ONE,BigDecimal.ONE);
        var source=new AttemptSourceIdentity(UUID.randomUUID(),1,UUID.randomUUID(),1);
        var input=new AttemptWorkInput("attempt-structured-work-request-v1",source,AttemptWorkInput.Engine.GATEWAY_RECOGNITION,null,asset,job,null,
            List.of(new AttemptWorkInput.Observation(line,"x=4",new BigDecimal("0.80"),box)),
            List.of(new AttemptWorkInput.Step(UUID.randomUUID(),"x=5",AttemptWorkInput.Role.INTERMEDIATE,List.of(line),true)));
        var evidence=new AttemptExtractionReader.Extraction("attempt-extraction-v1",asset,job,"REVIEW_REQUIRED","synthetic","synthetic","v1","v1",
            List.of(new AttemptExtractionReader.Line(1,"x=4",null,null,new AttemptExtractionReader.BoundingBox(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ONE,BigDecimal.ONE),new BigDecimal("0.8"),true)),false);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        String wire=mapper.writeValueAsString(evidence);
        assertThat(mapper.readValue(wire,AttemptExtractionReader.Extraction.class)).isEqualTo(evidence);
        assertThat(mapper.readTree(wire).path("steps").get(0).path("boundingBox"))
            .isEqualTo(mapper.readTree("{\"x\":0,\"y\":0,\"width\":1,\"height\":1}"));
        assertThatCode(()->AttemptWorkService.validateRecognition(input,evidence)).doesNotThrowAnyException();
        for(var altered:List.of(new AttemptWorkInput.Observation(line,"forged",new BigDecimal("0.8"),box),
            new AttemptWorkInput.Observation(line,"x=4",BigDecimal.ONE,box),new AttemptWorkInput.Observation(line,"x=4",new BigDecimal("0.8"),null))) {
            var forged=new AttemptWorkInput(input.schemaVersion(),source,input.engine(),null,asset,job,null,List.of(altered),input.steps());
            assertThatThrownBy(()->AttemptWorkService.validateRecognition(forged,evidence)).isInstanceOf(com.verifiedai.sharedkernel.error.ApiProblemException.class);
        }
    }
}
