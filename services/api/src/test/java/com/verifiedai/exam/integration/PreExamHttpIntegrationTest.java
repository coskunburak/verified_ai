package com.verifiedai.exam.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.verifiedai.exam.config.ExamProperties;
import com.verifiedai.exam.infrastructure.persistence.PreExamStudySessionRepository;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.security.VerifiedJwtPrincipal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

class PreExamHttpIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired WebApplicationContext context;
    @Autowired ExamProperties flags;
    @Autowired PreExamStudySessionRepository sessions;
    @Autowired JsonMapper mapper;
    private MockMvc mvc;
    private final UUID owner=UUID.randomUUID(), stranger=UUID.randomUUID();
    private boolean previousFlag;
    private UsernamePasswordAuthenticationToken auth(UUID id) {
        return new UsernamePasswordAuthenticationToken(new VerifiedJwtPrincipal(UUID.randomUUID(),id),
                "synthetic",List.of(new SimpleGrantedAuthority("ROLE_LEARNER")));
    }
    @BeforeEach void setup() {
        previousFlag=flags.isPreExamModeEnabled(); flags.setPreExamModeEnabled(true);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void cleanup() {
        flags.setPreExamModeEnabled(previousFlag); sessions.deleteByUserId(owner); sessions.deleteByUserId(stranger);
    }
    @Test void bothPrefixesRevalidateAndExportIsOwnerScopedAndIdempotent() throws Exception {
        String route="/v1/exams/pre-exam-review/TR_YKS_TYT_MATH";
        var first=mvc.perform(get(route).with(authentication(auth(owner))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.hoursRemaining").isEmpty())
                .andExpect(jsonPath("$.trainingEligible").value(false)).andReturn().getResponse();
        String etag=first.getHeader("ETag"); assertNotNull(etag);
        String session=mapper.readTree(first.getContentAsString()).path("sessionId").asText();
        mvc.perform(get("/api"+route).header("If-None-Match","W/"+etag).with(authentication(auth(owner))))
                .andExpect(status().isNotModified()).andExpect(content().string(""));
        mvc.perform(get(route).header("If-None-Match",etag).with(authentication(auth(stranger))))
                .andExpect(status().isOk());
        String export="/v1/exams/pre-exam-review/sessions/"+session+"/export-events";
        mvc.perform(post(export).header("Idempotency-Key","synthetic").with(authentication(auth(stranger))))
                .andExpect(status().isNotFound());
        for (int i=0;i<2;i++) mvc.perform(post(export).header("Idempotency-Key","synthetic").with(authentication(auth(owner))))
                .andExpect(status().isNoContent());
        assertEquals(1,sessions.exportEventsByUserId(owner).size());
        mvc.perform(get("/v1/exams/cheat-sheet/TR_YKS_TYT_MATH").header("If-None-Match",etag).with(authentication(auth(owner))))
                .andExpect(status().isOk());
    }
    @Test void featureGuardPrecedesConditionalResponse() throws Exception {
        flags.setPreExamModeEnabled(false);
        for (String prefix:List.of("/v1","/api/v1")) {
            mvc.perform(get(prefix+"/exams/pre-exam-review/TR_YKS_TYT_MATH").header("If-None-Match","*")
                    .with(authentication(auth(owner)))).andExpect(status().isForbidden());
        }
        assertTrue(sessions.exportByUserId(owner).isEmpty());
    }
}
