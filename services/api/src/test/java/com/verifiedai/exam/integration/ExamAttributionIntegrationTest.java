package com.verifiedai.exam.integration;

import tools.jackson.databind.json.JsonMapper;
import com.verifiedai.exam.application.*;
import com.verifiedai.exam.config.ExamProperties;
import com.verifiedai.exam.domain.*;
import com.verifiedai.exam.infrastructure.privacy.ExamPrivacyLifecycleContributor;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.security.VerifiedJwtPrincipal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL/Flyway V061 and authenticated REST; all learners are synthetic fixtures. */
class ExamAttributionIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired WebApplicationContext context;
    @Autowired ExamAssessmentAssemblyService assembly;
    @Autowired ExamSessionRuntimeService runtime;
    @Autowired ExamScoringEngineService scoring;
    @Autowired QuestionBankRepository questions;
    @Autowired ExamProperties flags;
    @Autowired JdbcTemplate jdbc;
    @Autowired ExamPrivacyLifecycleContributor privacy;
    @Autowired JsonMapper mapper;
    MockMvc mvc;
    UUID owner,other;
    private static final String BODY="{\"maxTopicsToApply\":3,\"targetHorizonDays\":7,\"preferredDailyMinutes\":45}";
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        owner=UUID.randomUUID();other=UUID.randomUUID();
        for(var user:List.of(owner,other)) jdbc.update("INSERT INTO users(id,status,created_at,updated_at) VALUES(?,'ACTIVE',NOW(),NOW())",user);
        flags.setAttributionEnabled(true);flags.setNetGainSimulatorEnabled(true);
        assembly.setQuestionBankEnabled(true);assembly.setBlueprintManagementEnabled(true);assembly.setAssessmentAssemblyEnabled(true);
        runtime.setSessionRuntimeEnabled(true);scoring.setScoringEngineEnabled(true);
    }
    private UsernamePasswordAuthenticationToken auth(UUID user) {
        return new UsernamePasswordAuthenticationToken(new VerifiedJwtPrincipal(UUID.randomUUID(),user),"synthetic-token",List.of(new SimpleGrantedAuthority("ROLE_LEARNER")));
    }
    private ExamSession session(boolean scored) {
        var assessment=assembly.assembleAssessment(new ExamAssessmentAssemblyService.AssembleCommand(
                UUID.fromString("91000000-0000-4000-8000-000000000201"),owner,owner,null,
                UUID.fromString("94000000-0000-0000-0000-000000000201"),"s97-"+UUID.randomUUID(),null,null,null,null)).assessment();
        var session=runtime.startOrResumeSession(owner,new ExamSessionRuntimeService.StartSessionCommand(owner,assessment.id(),120,Instant.now(),false)).session();
        if(scored) {
            var q=questions.findById(session.items().getFirst().questionItemId()).orElseThrow();
            String wrong=q.options().stream().filter(o->!o.isCorrect()).findFirst().orElseThrow().optionKey();
            runtime.autosaveItem(owner,session.id(),1,"synthetic-answer",new ExamSessionRuntimeService.AutosaveItemCommand("SELECT_OPTION",wrong,null,false,List.of(),20,0,1L,false,Instant.now(),false));
            runtime.submitSession(owner,session.id(),"synthetic-submit",new ExamSessionRuntimeService.SubmitSessionCommand("USER_INITIATED",Instant.now(),false));
            scoring.computeOrGetScoreSummary(owner,session.id());
        }
        return session;
    }
    @Test void reportRebalanceReplayAndPrivacyPurge() throws Exception {
        var s=session(true);String path="/v1/exams/sessions/"+s.id();
        var first=mvc.perform(get(path+"/attribution-report").with(authentication(auth(owner))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.trainingEligible").value(false)).andExpect(jsonPath("$.categorySummary.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api"+path+"/attribution-report").with(authentication(auth(owner))))
                .andExpect(status().isOk()).andExpect(content().json(first));
        var applied=mvc.perform(post(path+"/apply-study-rebalance").with(authentication(auth(owner))).header("Idempotency-Key","synthetic-rebalance").contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.appliedToStudyPlan").value(true)).andExpect(jsonPath("$.idempotentReplay").value(false))
                .andReturn().getResponse().getContentAsString();
        String batch=mapper.readTree(applied).get("batchId").asText();
        mvc.perform(post("/api"+path+"/apply-study-rebalance").with(authentication(auth(owner))).header("Idempotency-Key","synthetic-rebalance").contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idempotentReplay").value(true)).andExpect(jsonPath("$.batchId").value(batch));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM exam_study_reallocation_batches WHERE source_session_id=?",Integer.class,s.id()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM exam_session_item_attributions WHERE user_id=? AND (llm_tokens_used<>0 OR training_eligible)",Integer.class,owner));
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM daily_coach_sessions WHERE user_id=?",Integer.class,owner)>0);
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM daily_coach_sessions WHERE user_id=? AND budget_minutes>25",Integer.class,owner));
        assertFalse(((List<?>)privacy.exportUserData(owner).get("itemAttributions")).isEmpty());
        privacy.deleteUserData(owner,Instant.now());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM exam_session_item_attributions WHERE user_id=?",Integer.class,owner));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM exam_study_reallocation_batches WHERE user_id=?",Integer.class,owner));
    }
    @Test void bolaValidationFlagsAndUnscoredSession() throws Exception {
        var s=session(true);String path="/v1/exams/sessions/"+s.id();
        mvc.perform(get(path+"/attribution-report")).andExpect(status().isUnauthorized());
        mvc.perform(get(path+"/attribution-report").with(authentication(auth(other)))).andExpect(status().isNotFound());
        mvc.perform(post(path+"/apply-study-rebalance").with(authentication(auth(other))).header("Idempotency-Key","test-key").contentType("application/json").content(BODY)).andExpect(status().isNotFound());
        mvc.perform(post(path+"/apply-study-rebalance").with(authentication(auth(owner))).contentType("application/json").content(BODY)).andExpect(status().isBadRequest());
        mvc.perform(post(path+"/apply-study-rebalance").with(authentication(auth(owner))).header("Idempotency-Key"," ").contentType("application/json").content(BODY)).andExpect(status().isBadRequest());
        mvc.perform(post(path+"/apply-study-rebalance").with(authentication(auth(owner))).header("Idempotency-Key","test-key").contentType("application/json").content(BODY.replace(":7",":8"))).andExpect(status().isBadRequest());
        flags.setAttributionEnabled(false);
        try {mvc.perform(get(path+"/attribution-report").with(authentication(auth(owner)))).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FEATURE_DISABLED"));}
        finally {flags.setAttributionEnabled(true);}
        flags.setNetGainSimulatorEnabled(false);
        try {mvc.perform(post(path+"/apply-study-rebalance").with(authentication(auth(owner))).header("Idempotency-Key","test-key").contentType("application/json").content(BODY)).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FEATURE_DISABLED"));}
        finally {flags.setNetGainSimulatorEnabled(true);}
        var active=session(false);
        mvc.perform(get("/v1/exams/sessions/"+active.id()+"/attribution-report").with(authentication(auth(owner)))).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("SESSION_NOT_SCORED"));
    }
    @Test void concurrentReplayProducesOneBatchAndChangedPayloadConflicts() throws Exception {
        var s=session(true);String path="/v1/exams/sessions/"+s.id()+"/apply-study-rebalance";
        var request=new java.util.concurrent.Callable<String>() {public String call() throws Exception {
            return mvc.perform(post(path).with(authentication(auth(owner))).header("Idempotency-Key","concurrent-key").contentType("application/json").content(BODY)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        }};
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results=executor.invokeAll(List.of(request,request));
            var one=mapper.readTree(results.get(0).get());var two=mapper.readTree(results.get(1).get());
            assertEquals(one.get("batchId"),two.get("batchId"));
            assertNotEquals(one.get("idempotentReplay"),two.get("idempotentReplay"));
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM exam_study_reallocation_batches WHERE source_session_id=?",Integer.class,s.id()));
        mvc.perform(post(path).with(authentication(auth(owner))).header("Idempotency-Key","concurrent-key").contentType("application/json").content(BODY.replace(":45",":30"))).andExpect(status().isConflict());
    }
}
