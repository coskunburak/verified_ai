package com.verifiedai.studyplan.coldstart;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.studyplan.privacy.StudyPlanPrivacyLifecycleContributor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full Testcontainers PostgreSQL 16 + Flyway V053 + Spring Security integration suite for Sprint 8.10.
 */
class ColdStartAndQualityGatePostgresIntegrationTest extends PostgresIntegrationTestSupport {

    private static final UUID USER_A = UUID.fromString("81000000-0000-4000-8000-000000000a01");
    private static final UUID USER_B = UUID.fromString("81000000-0000-4000-8000-000000000b02");
    private static final UUID ADMIN_ID = UUID.fromString("81000000-0000-4000-8000-000000000ad1");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StudyPlanPrivacyLifecycleContributor privacyContributor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        jdbcTemplate.update("DELETE FROM cold_start_diagnostics WHERE learner_id IN (?, ?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM planner_quality_gate_snapshots WHERE evaluated_by_actor_id = ?", ADMIN_ID);
        ensureUserExists(USER_A);
        ensureUserExists(USER_B);
    }

    @Test
    void coldStartEndToEndPersistenceIdempotencyBolaAndGdprPurge() throws Exception {
        String requestPayload = """
                {
                  "schemaVersion": "cold-start-v1",
                  "idempotencyKey": "pg-cold-start-001",
                  "locale": "tr-TR",
                  "declaredExamTarget": "AYT",
                  "declaredDailyBudgetMinutes": 10,
                  "preferredModality": "SOCRATIC_COACH",
                  "explicitPreferenceOverride": true,
                  "daysSinceLastVerifiedAttempt": 0,
                  "responses": [
                    {"itemId": "DIAG_ITEM_1_NUMBER_SENSE", "selectedOptionId": "A", "answer": "23/20", "elapsedSeconds": 28, "timedOut": false, "skipped": false},
                    {"itemId": "DIAG_ITEM_2_QUADRATIC_INEQUALITY", "selectedOptionId": "B", "answer": "x <= 2 or x >= 3", "elapsedSeconds": 40, "timedOut": false, "skipped": false},
                    {"itemId": "DIAG_ITEM_3_FUNCTION_COMPOSITION", "selectedOptionId": "A", "answer": "21", "elapsedSeconds": 95, "timedOut": false, "skipped": false}
                  ]
                }
                """;

        // Unauthenticated fails 401
        mockMvc.perform(get("/v1/study-plan/cold-start"))
                .andExpect(status().isUnauthorized());

        // Submit diagnostic -> persisted in V053 table
        mockMvc.perform(post("/v1/study-plan/cold-start")
                        .with(authentication(authFor(USER_A, "ROLE_LEARNER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("cold-start-v1"))
                .andExpect(jsonPath("$.baselineTier").value("CALIBRATED_MIXED"))
                .andExpect(jsonPath("$.goalConflictResolved").value(true))
                .andExpect(jsonPath("$.explicitPreferenceOverride").value(true))
                .andExpect(jsonPath("$.llmTokensUsed").value(0))
                .andExpect(jsonPath("$.trainingEligible").value(false))
                .andExpect(jsonPath("$.idempotentReplay").value(false))
                .andExpect(jsonPath("$.initializedPriors", hasSize(3)));

        // Idempotent replay returns same diagnostic
        mockMvc.perform(post("/api/v1/study-plan/cold-start")
                        .with(authentication(authFor(USER_A, "ROLE_LEARNER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        // Cross-learner BOLA returns 403
        mockMvc.perform(get("/v1/study-plan/cold-start")
                        .param("userId", USER_B.toString())
                        .with(authentication(authFor(USER_A, "ROLE_LEARNER"))))
                .andExpect(status().isForbidden());

        // GDPR export contains coldStartDiagnostics with trainingEligible=false
        Map<String, Object> exported = privacyContributor.exportUserData(USER_A);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> coldStartRows = (List<Map<String, Object>>) exported.get("coldStartDiagnostics");
        assertNotNull(coldStartRows);
        assertEquals(1, coldStartRows.size());
        assertEquals(false, coldStartRows.getFirst().get("trainingEligible"));
        assertEquals(0, coldStartRows.getFirst().get("llmTokensUsed"));

        // GDPR delete removes all rows cleanly
        privacyContributor.deleteUserData(USER_A, Instant.now());
        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cold_start_diagnostics WHERE learner_id = ?",
                Integer.class,
                USER_A
        );
        assertEquals(0, remaining);
    }

    @Test
    void adminQualityGateSecurityAndSnapshotPersistence() throws Exception {
        // Anonymous -> 401
        mockMvc.perform(get("/v1/admin/study-plan/quality-gate"))
                .andExpect(status().isUnauthorized());

        // Non-admin learner -> 403 Forbidden
        mockMvc.perform(get("/v1/admin/study-plan/quality-gate")
                        .with(authentication(authFor(USER_A, "ROLE_LEARNER"))))
                .andExpect(status().isForbidden());

        // Admin role -> 200 OK with persisted snapshot
        mockMvc.perform(get("/api/v1/admin/study-plan/quality-gate")
                        .param("persistSnapshot", "true")
                        .with(authentication(authFor(ADMIN_ID, "ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("study-plan-quality-v1"))
                .andExpect(jsonPath("$.overallStatus").value("PASS"))
                .andExpect(jsonPath("$.llmTokensUsed").value(0))
                .andExpect(jsonPath("$.trainingEligible").value(false))
                .andExpect(jsonPath("$.telemetryMetrics.piiViolationCount").value(0));

        Integer saved = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM planner_quality_gate_snapshots WHERE evaluated_by_actor_id = ? AND training_eligible = FALSE AND llm_tokens_used = 0",
                Integer.class,
                ADMIN_ID
        );
        assertEquals(1, saved);
    }

    private void ensureUserExists(UUID userId) {
        jdbcTemplate.update(
                """
                INSERT INTO users (id, status, created_at, updated_at)
                VALUES (?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """,
                userId
        );
    }

    private static Authentication authFor(UUID userId, String role) {
        return new UsernamePasswordAuthenticationToken(
                new VerifiedJwtPrincipal(UUID.randomUUID(), userId),
                "token",
                List.of(new SimpleGrantedAuthority(role))
        );
    }
}
