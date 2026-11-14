package com.verifiedai.studyplan.coldstart;

import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiExceptionHandler;
import com.verifiedai.studyplan.coldstart.ColdStartCalibrationServiceTest.InMemoryColdStartRepository;
import com.verifiedai.studyplan.quality.AdminQualityGateController;
import com.verifiedai.studyplan.quality.PlannerQualityGateService;
import com.verifiedai.studyplan.quality.QualityGateModels.ContractGateCheck;
import com.verifiedai.studyplan.quality.QualityGateModels.GateStatus;
import com.verifiedai.studyplan.quality.QualityGateModels.QualityTelemetryMetrics;
import com.verifiedai.studyplan.quality.QualityGateModels.RegressionSeverity;
import com.verifiedai.studyplan.quality.QualityGateModels.StudyPlanQualityGateReport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ColdStartAndQualityGateControllerTest {

    private static final UUID USER_A = UUID.fromString("81000000-0000-4000-8000-000000000810");
    private static final UUID USER_B = UUID.fromString("81000000-0000-4000-8000-000000000811");
    private static final UUID ADMIN_ID = UUID.fromString("81000000-0000-4000-8000-000000000999");

    private MockMvc coldStartMvc;
    private MockMvc disabledColdStartMvc;
    private MockMvc adminGateMvc;
    private PlannerQualityGateService qualityGateService;

    @BeforeEach
    void setUp() {
        InMemoryColdStartRepository repository = new InMemoryColdStartRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        ColdStartCalibrationService enabledService = new ColdStartCalibrationService(repository, meterRegistry, clock, true);
        ColdStartCalibrationService disabledService = new ColdStartCalibrationService(repository, meterRegistry, clock, false);
        qualityGateService = mock(PlannerQualityGateService.class);

        coldStartMvc = MockMvcBuilders.standaloneSetup(new ColdStartCalibrationController(enabledService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        disabledColdStartMvc = MockMvcBuilders.standaloneSetup(new ColdStartCalibrationController(disabledService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        adminGateMvc = MockMvcBuilders.standaloneSetup(new AdminQualityGateController(qualityGateService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void coldStartPostGetDualPathsAndBolaAndFeatureDisabled() throws Exception {
        String payload = """
                {
                  "schemaVersion": "cold-start-v1",
                  "idempotencyKey": "mvc-cold-start-001",
                  "locale": "en-US",
                  "declaredExamTarget": "AYT",
                  "declaredDailyBudgetMinutes": 10,
                  "preferredModality": "SOCRATIC_COACH",
                  "explicitPreferenceOverride": true,
                  "daysSinceLastVerifiedAttempt": 0,
                  "responses": [
                    {"itemId": "DIAG_ITEM_1_NUMBER_SENSE", "answer": "23/20", "elapsedSeconds": 35, "timedOut": false, "skipped": false},
                    {"itemId": "DIAG_ITEM_2_QUADRATIC_INEQUALITY", "answer": "B", "elapsedSeconds": 50, "timedOut": false, "skipped": false},
                    {"itemId": "DIAG_ITEM_3_FUNCTION_COMPOSITION", "answer": "21", "elapsedSeconds": 80, "timedOut": false, "skipped": false}
                  ]
                }
                """;

        // Disabled feature flag returns 503 with COLD_START_FEATURE_DISABLED
        disabledColdStartMvc.perform(post("/v1/study-plan/cold-start")
                        .principal(learnerAuth(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COLD_START_FEATURE_DISABLED"));

        // Unauthenticated fails 401
        coldStartMvc.perform(get("/v1/study-plan/cold-start"))
                .andExpect(status().isUnauthorized());

        // Valid POST /v1/study-plan/cold-start succeeds with $0.00 LLM cost
        coldStartMvc.perform(post("/v1/study-plan/cold-start")
                        .principal(learnerAuth(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(jsonPath("$.schemaVersion").value("cold-start-v1"))
                .andExpect(jsonPath("$.baselineTier").value("CALIBRATED_MIXED"))
                .andExpect(jsonPath("$.confidenceTier").value("PRELIMINARY"))
                .andExpect(jsonPath("$.goalConflictResolved").value(true))
                .andExpect(jsonPath("$.explicitPreferenceOverride").value(true))
                .andExpect(jsonPath("$.llmTokensUsed").value(0))
                .andExpect(jsonPath("$.trainingEligible").value(false))
                .andExpect(jsonPath("$.initializedPriors", hasSize(3)));

        // GET /api/v1/study-plan/cold-start dual route mirror succeeds
        coldStartMvc.perform(get("/api/v1/study-plan/cold-start")
                        .principal(learnerAuth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learnerId").value(USER_A.toString()))
                .andExpect(jsonPath("$.detectedTrapArchetypes[0]").value("INEQUALITY_POLARITY_TRAP"));

        // Cross-learner BOLA fails 403 Forbidden
        coldStartMvc.perform(get("/v1/study-plan/cold-start")
                        .param("userId", USER_B.toString())
                        .principal(learnerAuth(USER_A)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_FORBIDDEN"));
    }

    @Test
    void adminQualityGateRequiresAdminRoleAnnotationAndServesReportOnBothRoutes() throws Exception {
        Method method = AdminQualityGateController.class.getMethod("getQualityGateReport", boolean.class, Authentication.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation);
        assertEquals("hasRole('ADMIN')", annotation.value());

        StudyPlanQualityGateReport report = new StudyPlanQualityGateReport(
                "study-plan-quality-v1",
                UUID.fromString("81000000-0000-4000-8000-000000000902"),
                Instant.parse("2026-11-13T12:00:00Z"),
                GateStatus.PASS,
                0,
                0,
                0,
                false,
                List.of(
                        new ContractGateCheck("CNT-LOS-06", "Cold Start", GateStatus.PASS, RegressionSeverity.P0, 1.0, "1.00", "ok"),
                        new ContractGateCheck("CNT-LOS-07", "Coach & Session", GateStatus.PASS, RegressionSeverity.P0, 1.0, "1.00", "ok"),
                        new ContractGateCheck("CNT-LOS-08", "Rebalance & Lesson", GateStatus.PASS, RegressionSeverity.P0, 1.0, "1.00", "ok"),
                        new ContractGateCheck("CNT-LOS-09", "Streak & Digest", GateStatus.PASS, RegressionSeverity.P0, 1.0, "1.00", "ok")
                ),
                new QualityTelemetryMetrics(3, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0)
        );
        when(qualityGateService.evaluateQualityGate(eq(ADMIN_ID), anyBoolean())).thenReturn(report);

        adminGateMvc.perform(get("/v1/admin/study-plan/quality-gate")
                        .principal(adminAuth(ADMIN_ID))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(jsonPath("$.schemaVersion").value("study-plan-quality-v1"))
                .andExpect(jsonPath("$.overallStatus").value("PASS"))
                .andExpect(jsonPath("$.p0RegressionCount").value(0))
                .andExpect(jsonPath("$.llmTokensUsed").value(0))
                .andExpect(jsonPath("$.trainingEligible").value(false))
                .andExpect(jsonPath("$.contractChecks", hasSize(4)))
                .andExpect(jsonPath("$.telemetryMetrics.piiViolationCount").value(0));

        adminGateMvc.perform(get("/api/v1/admin/study-plan/quality-gate")
                        .param("persistSnapshot", "true")
                        .principal(adminAuth(ADMIN_ID))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("study-plan-quality-v1"));

        verify(qualityGateService).evaluateQualityGate(ADMIN_ID, false);
        verify(qualityGateService).evaluateQualityGate(ADMIN_ID, true);

        Authentication learner = learnerAuth(USER_A);
        assertTrue(learner.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }

    private static Authentication learnerAuth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
                new VerifiedJwtPrincipal(UUID.randomUUID(), userId),
                "token",
                List.of(new SimpleGrantedAuthority("ROLE_LEARNER"))
        );
    }

    private static Authentication adminAuth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
                new VerifiedJwtPrincipal(UUID.randomUUID(), userId),
                "token",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }
}
