package com.verifiedai.studyplan.session;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiExceptionHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StudySessionControllerIntegrationTest {

    private static final UUID USER_A = UUID.fromString("00000000-0000-4000-8000-000000000870");
    private static final UUID USER_B = UUID.fromString("00000000-0000-4000-8000-000000000879");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private AdjustableClock clock;
    private InMemoryStudySessionRepository repository;
    private SimpleMeterRegistry meterRegistry;
    private MockMvc mockMvc;
    private MockMvc disabledMockMvc;

    @BeforeEach
    void setUp() {
        clock = new AdjustableClock(Instant.parse("2026-11-13T07:00:00Z"));
        repository = new InMemoryStudySessionRepository();
        meterRegistry = new SimpleMeterRegistry();

        StudySessionOrchestrationService enabledService = new StudySessionOrchestrationService(
            repository,
            new StudySessionStateMachine(),
            StudySessionOrchestrationProperties.defaults(),
            meterRegistry,
            clock
        );
        StudySessionOrchestrationService disabledService = new StudySessionOrchestrationService(
            repository,
            new StudySessionStateMachine(),
            StudySessionOrchestrationProperties.defaults().withEnabled(false),
            meterRegistry,
            clock
        );

        mockMvc = MockMvcBuilders.standaloneSetup(new StudySessionController(enabledService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
        disabledMockMvc = MockMvcBuilders.standaloneSetup(new StudySessionController(disabledService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void fullHttpLifecycleCheckpointIdempotencyPauseResumeAndComplete() throws Exception {
        authenticateAs(USER_A);

        String startBody = """
            {
              "coach_session_id": null,
              "source_context": "DAILY_COACH",
              "topic_code": "math.calculus.derivative_chain_rule",
              "locale": "tr-TR",
              "total_steps": 3,
              "client_instance_id": "ios-device-00000875",
              "step_blueprint": [
                {"step_index": 0, "step_type": "CONCEPT_PRIMER", "step_title": "Step 0", "estimated_seconds": 60, "status": "IN_PROGRESS", "target_skill_code": "CHAIN_RULE", "problem_id": null},
                {"step_index": 1, "step_type": "SOLVE_ITEM", "step_title": "Step 1", "estimated_seconds": 120, "status": "PENDING", "target_skill_code": "CHAIN_RULE", "problem_id": "prob-0871"},
                {"step_index": 2, "step_type": "REFLECTION", "step_title": "Step 2", "estimated_seconds": 60, "status": "PENDING", "target_skill_code": "CHAIN_RULE", "problem_id": null}
              ],
              "resume_snapshot": {
                "last_step_index": 0,
                "draft_step_notes": "",
                "offline_queued_count": 0,
                "active_sub_step": null,
                "ui_scroll_anchor": "step-0",
                "mistake_code": null
              }
            }
            """;

        MvcResult startResult = mockMvc.perform(post("/v1/study-sessions/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.schema_version", is("1.0.0")))
            .andExpect(jsonPath("$.session.state", is("ACTIVE")))
            .andExpect(jsonPath("$.training_eligible", is(false)))
            .andReturn();

        JsonNode startJson = objectMapper.readTree(startResult.getResponse().getContentAsString());
        String sessionId = startJson.get("session").get("session_id").asText();

        mockMvc.perform(get("/api/v1/study-sessions/active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.session_id", is(sessionId)))
            .andExpect(jsonPath("$.state", is("ACTIVE")));

        clock.advanceSeconds(120);
        String ckptKey = "sess_00000871_step_01_seq_0002";
        String checkpointBody = """
            {
              "sequence_number": 2,
              "step_index": 1,
              "step_type": "SOLVE_ITEM",
              "step_status": "COMPLETED",
              "reported_active_duration_seconds": 120,
              "offline_queued": true,
              "client_recorded_at": "2026-11-13T07:01:58Z",
              "idempotency_key": "%s",
              "interruption_reason": "NETWORK_LOST",
              "client_instance_id": "ios-device-00000875",
              "resume_snapshot": {
                "last_step_index": 1,
                "draft_step_notes": "cos(3x^2) * 6x",
                "offline_queued_count": 1,
                "active_sub_step": "inner",
                "ui_scroll_anchor": "step-1",
                "mistake_code": null
              }
            }
            """.formatted(ckptKey);

        mockMvc.perform(put("/v1/study-sessions/{sessionId}/checkpoint", sessionId)
                .header("Idempotency-Key", ckptKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkpointBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotent_replay", is(false)))
            .andExpect(jsonPath("$.stale_checkpoint_ignored", is(false)))
            .andExpect(jsonPath("$.accepted_sequence_number", is(2)))
            .andExpect(jsonPath("$.accepted_active_duration_seconds", is(120)))
            .andExpect(jsonPath("$.training_eligible", is(false)));

        // Duplicate PUT idempotent replay
        mockMvc.perform(put("/v1/study-sessions/{sessionId}/checkpoint", sessionId)
                .header("Idempotency-Key", ckptKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkpointBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotent_replay", is(true)))
            .andExpect(jsonPath("$.accepted_sequence_number", is(2)));

        // Pause -> Resume
        mockMvc.perform(post("/v1/study-sessions/{sessionId}/pause", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"interruption_reason\":\"APP_BACKGROUNDED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state", is("PAUSED")));

        clock.advanceSeconds(45);
        mockMvc.perform(post("/v1/study-sessions/{sessionId}/resume", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"client_instance_id\":\"ios-device-00000875\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state", is("ACTIVE")))
            .andExpect(jsonPath("$.paused_duration_seconds", is(45)));

        // Complete + Idempotent replay + Illegal conflict on distinct key
        clock.advanceSeconds(60);
        String completeKey = "sess_00000871_complete_v1_0003";
        String completeBody = """
            {
              "final_step_index": 2,
              "reported_active_duration_seconds": 180,
              "idempotency_key": "%s",
              "completion_notes": "Done cleanly"
            }
            """.formatted(completeKey);

        mockMvc.perform(post("/v1/study-sessions/{sessionId}/complete", sessionId)
                .header("Idempotency-Key", completeKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotent_replay", is(false)))
            .andExpect(jsonPath("$.session.state", is("COMPLETED")))
            .andExpect(jsonPath("$.supportive_summary_key", is("study_session.complete.summary")))
            .andExpect(jsonPath("$.training_eligible", is(false)));

        mockMvc.perform(post("/v1/study-sessions/{sessionId}/complete", sessionId)
                .header("Idempotency-Key", completeKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotent_replay", is(true)))
            .andExpect(jsonPath("$.session.state", is("COMPLETED")));

        // Paused transition on COMPLETED session => 409 ILLEGAL_SESSION_STATE_TRANSITION
        mockMvc.perform(post("/v1/study-sessions/{sessionId}/pause", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"interruption_reason\":\"USER_PAUSED\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", is("ILLEGAL_SESSION_STATE_TRANSITION")));
    }

    @Test
    void enforcesBola404Unauthorized401Validation400AndFeatureFlag503() throws Exception {
        // Unauthenticated => 401
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/v1/study-sessions/active"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code", is("AUTH_CREDENTIALS_INVALID")));

        // Start session as USER_A
        authenticateAs(USER_A);
        MvcResult res = mockMvc.perform(post("/v1/study-sessions/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "source_context": "FREE_STUDY",
                      "topic_code": "math.algebra",
                      "locale": "en-US",
                      "total_steps": 1,
                      "client_instance_id": "ios-device-00000875",
                      "step_blueprint": [
                        {"step_index": 0, "step_type": "SOLVE_ITEM", "step_title": "Q1", "estimated_seconds": 60, "status": "IN_PROGRESS"}
                      ]
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();
        String sessionId = objectMapper.readTree(res.getResponse().getContentAsString())
            .get("session").get("session_id").asText();

        // USER_B cross-user access => 404 STUDY_SESSION_NOT_FOUND (indistinguishable from nonexistent)
        authenticateAs(USER_B);
        mockMvc.perform(get("/v1/study-sessions/{sessionId}", sessionId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("STUDY_SESSION_NOT_FOUND")));
        mockMvc.perform(get("/v1/study-sessions/{sessionId}", UUID.fromString("00000000-0000-4000-8000-000000000878")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("STUDY_SESSION_NOT_FOUND")));

        // Missing Idempotency-Key => 400
        authenticateAs(USER_A);
        mockMvc.perform(put("/v1/study-sessions/{sessionId}/checkpoint", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sequence_number": 1,
                      "step_index": 0,
                      "step_type": "SOLVE_ITEM",
                      "step_status": "IN_PROGRESS",
                      "reported_active_duration_seconds": 10,
                      "offline_queued": false,
                      "client_recorded_at": "2026-11-13T07:00:10Z",
                      "idempotency_key": "sess_00000871_step_00_seq_0001",
                      "resume_snapshot": {"last_step_index": 0, "draft_step_notes": "", "offline_queued_count": 0}
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("REQUEST_VALIDATION_FAILED")));

        // Out-of-bounds reported duration > 14400 => 400 validation
        mockMvc.perform(put("/v1/study-sessions/{sessionId}/checkpoint", sessionId)
                .header("Idempotency-Key", "sess_00000871_step_00_seq_0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sequence_number": 1,
                      "step_index": 0,
                      "step_type": "SOLVE_ITEM",
                      "step_status": "IN_PROGRESS",
                      "reported_active_duration_seconds": 14401,
                      "offline_queued": false,
                      "client_recorded_at": "2026-11-13T07:00:10Z",
                      "idempotency_key": "sess_00000871_step_00_seq_0001",
                      "resume_snapshot": {"last_step_index": 0, "draft_step_notes": "", "offline_queued_count": 0}
                    }
                    """))
            .andExpect(status().isBadRequest());

        // Kill switch disabled => 503 STUDY_SESSION_ORCHESTRATION_DISABLED
        disabledMockMvc.perform(get("/v1/study-sessions/active"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code", is("STUDY_SESSION_ORCHESTRATION_DISABLED")));
    }

    private static void authenticateAs(UUID userId) {
        TestingAuthenticationToken token = new TestingAuthenticationToken(
            new VerifiedJwtPrincipal(userId, userId),
            null
        );
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private static final class AdjustableClock extends Clock {
        private Instant current;

        AdjustableClock(Instant initial) {
            this.current = initial;
        }

        void advanceSeconds(long seconds) {
            this.current = this.current.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
