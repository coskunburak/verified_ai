package com.verifiedai.exam.integration;

import com.verifiedai.exam.application.ExamRepository;
import com.verifiedai.exam.domain.ExamBlueprintHashCalculator;
import com.verifiedai.exam.domain.ExamBlueprintValidator;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.infrastructure.privacy.ExamPrivacyLifecycleContributor;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.security.VerifiedJwtPrincipal;
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

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full Testcontainers PostgreSQL 16 + Flyway V054 + Spring Security integration suite for Sprint 9.1.
 */
class ExamPlatformPostgresIntegrationTest extends PostgresIntegrationTestSupport {

    private static final UUID USER_A = UUID.fromString("91000000-0000-4000-8000-000000000d01");
    private static final UUID USER_B = UUID.fromString("91000000-0000-4000-8000-000000000d02");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExamBlueprintValidator blueprintValidator;

    @Autowired
    private ExamPrivacyLifecycleContributor privacyContributor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        jdbcTemplate.update("DELETE FROM student_exam_targets WHERE user_id IN (?, ?)", USER_A, USER_B);
        ensureUserExists(USER_A);
        ensureUserExists(USER_B);
    }

    @Test
    void seededV054AndV060BlueprintsConserveWeightsVerifyHashesAndValidateAgainstCurriculumCatalog() {
        List<ExamDefinition> allDefinitions = examRepository.findCatalog(null, null, true);
        assertEquals(8, allDefinitions.size());

        for (ExamDefinition def : allDefinitions) {
            blueprintValidator.validateBlueprint(def);
            assertEquals(ExamBlueprintHashCalculator.computeHash(def), def.blueprintHash());
        }
    }

    @Test
    void endToEndCatalogTargetBindingSupersedingBolaAndGdprPurge() throws Exception {
        // 1. Unauthenticated -> 401
        mockMvc.perform(get("/v1/exams/definitions"))
                .andExpect(status().isUnauthorized());

        // 2. Active catalog returns 5 canonical 2026.1 exams
        mockMvc.perform(get("/v1/exams/definitions")
                        .with(authentication(authFor(USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitions", hasSize(5)))
                .andExpect(jsonPath("$.definitions[*].examCode", hasItems(
                        "TR_YKS_TYT_MATH",
                        "TR_YKS_AYT_MATH",
                        "INTL_SAT_MATH",
                        "INTL_AP_CALC_BC",
                        "INTL_IB_MATH_AA_HL"
                )));

        // 3. Filter INTL jurisdiction
        mockMvc.perform(get("/v1/exams/definitions")
                        .param("jurisdiction", "INTL")
                        .with(authentication(authFor(USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitions", hasSize(3)));

        // 4. Fetch active TYT detail + blueprint hash (2026.2 after V060)
        String tytJson = mockMvc.perform(get("/v1/exams/definitions/TR_YKS_TYT_MATH")
                        .with(authentication(authFor(USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examVersion").value("2026.2"))
                .andExpect(jsonPath("$.totalQuestions").value(40))
                .andExpect(jsonPath("$.topicWeights", hasSize(22)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String tytHash = com.jayway.jsonpath.JsonPath.read(tytJson, "$.blueprintHash");

        // 5. Bind primary target TR_YKS_TYT_MATH -> 201 Created
        String bindTytPayload = """
                {
                  "schemaVersion": "student-exam-target-request-v1",
                  "examCode": "TR_YKS_TYT_MATH",
                  "examVersion": "2026.2",
                  "expectedBlueprintHash": "%s",
                  "targetScore": 36.25,
                  "targetDate": "2027-06-20",
                  "priorityRank": 1,
                  "isPrimary": true,
                  "idempotencyKey": "pg-bind-tyt-001"
                }
                """.formatted(tytHash);

        String boundTytResponse = mockMvc.perform(post("/v1/exams/targets")
                        .with(authentication(authFor(USER_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindTytPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.blueprintHash").value(tytHash))
                .andExpect(jsonPath("$.targetStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.readinessGapPreview.llmTokensUsed").value(0))
                .andExpect(jsonPath("$.llmTokensUsed").value(0))
                .andExpect(jsonPath("$.trainingEligible").value(false))
                .andExpect(jsonPath("$.idempotentReplay").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String tytTargetId = com.jayway.jsonpath.JsonPath.read(boundTytResponse, "$.targetId");

        // 6. Idempotent replay -> 200 OK
        mockMvc.perform(post("/api/v1/exams/targets")
                        .with(authentication(authFor(USER_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindTytPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetId").value(tytTargetId))
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        // 7. Binding new primary target INTL_SAT_MATH supersedes prior primary TYT target
        String bindSatPayload = """
                {
                  "schemaVersion": "student-exam-target-request-v1",
                  "examCode": "INTL_SAT_MATH",
                  "targetScore": 740.00,
                  "targetDate": "2027-05-01",
                  "priorityRank": 1,
                  "isPrimary": true,
                  "idempotencyKey": "pg-bind-sat-002"
                }
                """;
        mockMvc.perform(post("/v1/exams/targets")
                        .with(authentication(authFor(USER_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindSatPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.examCode").value("INTL_SAT_MATH"))
                .andExpect(jsonPath("$.targetStatus").value("ACTIVE"));

        // Verify previous target transitioned to SUPERSEDED while preserving immutable snapshot
        mockMvc.perform(get("/v1/exams/targets/" + tytTargetId)
                        .with(authentication(authFor(USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetStatus").value("SUPERSEDED"))
                .andExpect(jsonPath("$.pinnedBlueprint.examCode").value("TR_YKS_TYT_MATH"))
                .andExpect(jsonPath("$.pinnedBlueprint.blueprintHash").value(tytHash));

        // 8. Cross-learner BOLA fails 403 EXAM_ACCESS_DENIED
        mockMvc.perform(get("/v1/exams/targets/" + tytTargetId)
                        .with(authentication(authFor(USER_B))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EXAM_ACCESS_DENIED"));

        // 9. GDPR export includes both targets with trainingEligible=false and $0.00 LLM cost
        Map<String, Object> exported = privacyContributor.exportUserData(USER_A);
        assertEquals("exam", exported.get("category"));
        assertEquals(2, exported.get("studentTargetCount"));
        assertEquals(false, exported.get("trainingEligible"));
        assertEquals(0, exported.get("llmTokensUsed"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exportedRows = (List<Map<String, Object>>) exported.get("studentTargets");
        assertNotNull(exportedRows);
        assertEquals(2, exportedRows.size());

        // 10. GDPR purge removes all rows cleanly
        privacyContributor.deleteUserData(USER_A, Instant.now());
        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_exam_targets WHERE user_id = ?",
                Integer.class,
                USER_A
        );
        assertEquals(0, remaining);
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

    private static Authentication authFor(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
                new VerifiedJwtPrincipal(UUID.randomUUID(), userId),
                "token",
                List.of(new SimpleGrantedAuthority("ROLE_LEARNER"))
        );
    }
}
