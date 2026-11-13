package com.verifiedai.studyplan.rebalance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiExceptionHandler;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.studyplan.rebalance.dto.StudyPlanRebalanceRequest;
import com.verifiedai.studyplan.rebalance.dto.StudyPlanRebalanceResponse;
import com.verifiedai.studyplan.rebalance.dto.StudyPlanRecoveryStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RebalanceControllerTest {

    private MockMvc mockMvc;
    private MockMvc unauthenticatedMockMvc;
    private StudyPlanRebalancingService service;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private final LocalDate today = LocalDate.of(2026, 11, 13);

    @BeforeEach
    void setUp() {
        service = mock(StudyPlanRebalancingService.class);
        RebalanceController controller = new RebalanceController(service);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().equals(Object.class);
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory
                    ) {
                        return (AuthenticatedLearnerPrincipal) () -> userId;
                    }
                })
                .build();

        unauthenticatedMockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /v1/study-plan/recovery-status returns 200 OK with snake_case contract")
    void testGetRecoveryStatus200() throws Exception {
        StudyPlanRecoveryStatusResponse statusResponse = new StudyPlanRecoveryStatusResponse(
                "1.0.0",
                userId,
                today,
                true,
                3,
                30,
                15,
                20,
                false,
                3,
                2,
                1,
                PruneReasonCode.MISSED_DAY_WINDOW_RESET,
                "studyplan.rebalance.welcome_recovered",
                null
        );

        when(service.getRecoveryStatus(eq(userId), eq(today))).thenReturn(statusResponse);

        mockMvc.perform(get("/v1/study-plan/recovery-status")
                        .param("as_of_date", "2026-11-13")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema_version").value("1.0.0"))
                .andExpect(jsonPath("$.user_id").value(userId.toString()))
                .andExpect(jsonPath("$.recovery_needed").value(true))
                .andExpect(jsonPath("$.missed_days_count").value(3))
                .andExpect(jsonPath("$.recommended_daily_budget_minutes").value(20))
                .andExpect(jsonPath("$.active_today_tasks_count").value(3));
    }

    @Test
    @DisplayName("POST /v1/study-plan/rebalance returns 200 OK with rebalanced plan receipt")
    void testRebalanceStudyPlan200() throws Exception {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000851");
        StudyPlanRebalanceResponse response = new StudyPlanRebalanceResponse(
                "1.0.0",
                eventId,
                userId,
                today,
                RebalanceTriggerType.MINI_MODE_DOWNSHIFT,
                7,
                30,
                15,
                5,
                3,
                2,
                1,
                true,
                PruneReasonCode.MINI_MODE_SINGLE_FOCUS,
                List.of("MATH.GEO.CIRCLE_SECANT_INTRO"),
                "studyplan.rebalance.welcome_mini_mode",
                false,
                Instant.parse("2026-11-13T09:30:00Z")
        );

        when(service.executeRebalance(eq(userId), any())).thenReturn(response);

        String requestJson = """
                {
                  "trigger_type": "MINI_MODE_DOWNSHIFT",
                  "missed_days_override": 7,
                  "days_until_exam_override": 30,
                  "prefer_mini_mode": true,
                  "idempotency_key": "idem-001",
                  "as_of_date": "2026-11-13"
                }
                """;

        mockMvc.perform(post("/v1/study-plan/rebalance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rebalance_event_id").value(eventId.toString()))
                .andExpect(jsonPath("$.rebalanced_daily_budget_minutes").value(5))
                .andExpect(jsonPath("$.mini_mode_activated").value(true))
                .andExpect(jsonPath("$.active_today_tasks_count").value(1));
    }

    @Test
    @DisplayName("POST /v1/study-plan/rebalance returns 404 Not Found on cross-user recovery miss")
    void testRebalanceNotFound404() throws Exception {
        UUID missingPlanId = UUID.fromString("00000000-0000-0000-0000-000000000999");
        when(service.executeRebalance(eq(userId), any()))
                .thenThrow(new StudyPlanRecoveryNotFoundException(missingPlanId));

        mockMvc.perform(post("/v1/study-plan/rebalance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trigger_type\": \"MISSED_DAYS\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("STUDY_PLAN_RECOVERY_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /v1/study-plan/rebalance returns 422 Unprocessable Entity on domain rule violation")
    void testRebalanceUnprocessable422() throws Exception {
        when(service.executeRebalance(eq(userId), any()))
                .thenThrow(new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.STUDY_PLAN_REBALANCE_INVALID_REQUEST,
                        "Requested horizon is outside supported bounds",
                        true,
                        "ADJUST_EXAM_HORIZON"
                ));

        mockMvc.perform(post("/v1/study-plan/rebalance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trigger_type\": \"EXAM_DATE_SHIFT\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("STUDY_PLAN_REBALANCE_INVALID_REQUEST"));
    }

    @Test
    @DisplayName("POST /v1/study-plan/rebalance rejects invalid payload with 400 Validation Error")
    void testRebalanceValidationError400() throws Exception {
        mockMvc.perform(post("/v1/study-plan/rebalance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"missed_days_override\": -5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Unauthenticated requests return 401 Unauthorized")
    void testUnauthenticatedReturns401() throws Exception {
        unauthenticatedMockMvc.perform(get("/v1/study-plan/recovery-status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        unauthenticatedMockMvc.perform(post("/v1/study-plan/rebalance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trigger_type\": \"MISSED_DAYS\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Feature flag disabled returns 409 Conflict")
    void testFeatureFlagDisabledReturns409() throws Exception {
        when(service.executeRebalance(eq(userId), any()))
                .thenThrow(new ApiProblemException(
                        HttpStatus.CONFLICT,
                        ApiErrorCode.STUDY_PLAN_REBALANCING_DISABLED,
                        "Study plan rebalancing is currently disabled",
                        true,
                        "TRY_LATER"
                ));

        mockMvc.perform(post("/v1/study-plan/rebalance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trigger_type\": \"MISSED_DAYS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("STUDY_PLAN_REBALANCING_DISABLED"));
    }
}
