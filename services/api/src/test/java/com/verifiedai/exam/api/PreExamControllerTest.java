package com.verifiedai.exam.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.verifiedai.exam.application.*;
import com.verifiedai.exam.infrastructure.persistence.PreExamStudySessionRepository.Snapshot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class PreExamControllerTest {
    @Test void authenticatesBeforeConditionalResponseAndNeverDecodes304Body() {
        var service=mock(PreExamReviewService.class);
        var controller=new PreExamController(service,new PersonalCheatSheetService(service));
        UUID owner=UUID.randomUUID(); String digest="a".repeat(64);
        var auth=new UsernamePasswordAuthenticationToken(owner,"synthetic",List.of());
        var now=Instant.parse("2026-11-18T09:00:00Z");
        when(service.review(owner,"TR_YKS_TYT_MATH")).thenReturn(new Snapshot(UUID.randomUUID(),owner,
                "TR_YKS_TYT_MATH",digest,digest,"b".repeat(64),"{}","{}",now,now.plusSeconds(3600)));
        var response=controller.review("TR_YKS_TYT_MATH","W/\""+digest+"\"",auth);
        assertEquals(304,response.getStatusCode().value()); assertNull(response.getBody());
        assertEquals("no-store",response.getHeaders().getCacheControl());
        assertThrows(com.verifiedai.sharedkernel.error.ApiProblemException.class,
                ()->controller.review("TR_YKS_TYT_MATH","*",null));
        verify(service,times(1)).review(owner,"TR_YKS_TYT_MATH");
        assertEquals(200,controller.sheet("TR_YKS_TYT_MATH","\""+digest+"\"",auth).getStatusCode().value());
    }
    @Test void exportForwardsAuthenticatedOwnerAndIdempotencyKey() {
        var service=mock(PreExamReviewService.class);
        var controller=new PreExamController(service,new PersonalCheatSheetService(service));
        UUID owner=UUID.randomUUID(),session=UUID.randomUUID();
        var auth=new UsernamePasswordAuthenticationToken(owner,"synthetic",List.of());
        assertEquals(204,controller.exported(session,"synthetic-export",auth).getStatusCode().value());
        verify(service).export(owner,session,"synthetic-export");
    }
}
