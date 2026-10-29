package com.verifiedai.problem.api.parse;

import com.verifiedai.calculus.CalculusIntegrationData;
import com.verifiedai.problem.application.parse.*;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalculusRevisionControllerTest {
    @Test void rawDuplicateKeysNeverReachCorrectionService() {
        var corrections=mock(ProblemParseCorrectionApplicationService.class);
        var controller=new ProblemParseController(mock(ProblemParseApplicationService.class),corrections);
        assertThatThrownBy(()->controller.createCalculusRevision(null,UUID.randomUUID(),"key", "{\"problem\":{},\"problem\":{}}"))
            .isInstanceOf(ApiProblemException.class);
        verifyNoInteractions(corrections);
    }
    @Test void authenticatedIdentityAndTypedRawSourceReachTheVersionedAdmissionService() {
        var corrections=mock(ProblemParseCorrectionApplicationService.class);
        var controller=new ProblemParseController(mock(ProblemParseApplicationService.class),corrections);
        UUID user=UUID.randomUUID(),session=UUID.randomUUID(),parse=UUID.randomUUID();
        var jwt=Jwt.withTokenValue("synthetic").header("alg","none").subject(user.toString()).claim("sid",UUID.randomUUID().toString()).build();
        var body=CalculusIntegrationData.JSON.createObjectNode().put("baseParseId",parse.toString()).put("baseRevision",1).put("correctionReason","OTHER");
        body.set("problem",CalculusIntegrationData.cases().getFirst().path("parse"));
        when(corrections.createCalculusCorrection(any())).thenReturn(new ProblemParseCorrectionResult(session,UUID.randomUUID(),2,"USER",parse,true,"SUPPORTED",false,true,Instant.EPOCH));
        controller.createCalculusRevision(jwt,session,"owner-calculus-key",body.toString());
        verify(corrections).createCalculusCorrection(argThat(c->c.userId().equals(user)&&c.problemSessionId().equals(session)
            &&c.baseParseId().equals(parse)&&c.correctedProblemJson().contains("problem-parse-v2")));
        verify(corrections,never()).createCorrection(any());
    }
}
