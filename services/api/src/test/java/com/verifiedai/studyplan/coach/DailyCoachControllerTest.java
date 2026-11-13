package com.verifiedai.studyplan.coach;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.sharedkernel.error.ApiExceptionHandler;
import com.verifiedai.studyplan.coach.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DailyCoachControllerTest {

    private MockMvc mockMvc;
    private DailyCoachSessionService service;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final UUID userId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private final UUID sessionId = UUID.fromString("84000000-1111-4444-8888-000000000001");
    private final UUID taskId = UUID.fromString("84000000-2222-4444-8888-000000000001");

    @BeforeEach
    void setUp() {
        service = mock(DailyCoachSessionService.class);
        DailyCoachController controller = new DailyCoachController(service);

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
    }

    private DailyCoachSummaryResponse sampleSummary(DailyCoachMode mode, int budget, boolean downshifted, DailyCoachSessionStatus status) {
        DailyCoachTaskDto task = new DailyCoachTaskDto(
                taskId, 1, DailyCoachTaskType.TRAP_REPAIR,
                "MATH.ALG.RATIONAL_EQ", "ALG_DOMAIN_EXCLUSION_MISSED", "twin_84_a1b2c3d4e5f6",
                5, "growth_opportunity: Payda tanım kümesi kontrolü",
                "\\frac{x^2 - 4}{x - 2} = x + 2, \\quad x \\neq 2",
                status == DailyCoachSessionStatus.COMPLETED ? DailyCoachTaskStatus.COMPLETED : DailyCoachTaskStatus.PENDING,
                null
        );
        return new DailyCoachSummaryResponse(
                sessionId, userId, LocalDate.of(2026, 11, 13),
                budget, status, downshifted, false,
                status == DailyCoachSessionStatus.COMPLETED ? 1 : 0,
                downshifted ? 1 : 3,
                "Bugün odaklı görevle serini güçlendiriyoruz.",
                mode, 5, List.of(task),
                List.of(new TrapRadarPreviewDto("MATH.ALG.RATIONAL_EQ", "ALG_DOMAIN_EXCLUSION_MISSED",
                        "Payda Tanım Kümesi Kısıtı", "growth_opportunity", "active_trap_pattern")),
                null
        );
    }

    @Test
    @DisplayName("GET /v1/coach/today-summary returns 200 OK with deterministic mission")
    void testGetTodaySummary() throws Exception {
        when(service.getOrCreateTodaySummary(eq(userId), eq(LocalDate.of(2026, 11, 13))))
                .thenReturn(sampleSummary(DailyCoachMode.SOCRATIC_COACH_MODE, 15, false, DailyCoachSessionStatus.IN_PROGRESS));

        mockMvc.perform(get("/v1/coach/today-summary")
                        .param("localDate", "2026-11-13")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.budgetMinutes").value(15))
                .andExpect(jsonPath("$.preferredMode").value("SOCRATIC_COACH_MODE"))
                .andExpect(jsonPath("$.tasks[0].taskType").value("TRAP_REPAIR"));
    }

    @Test
    @DisplayName("PATCH /v1/coach/preferences switches between SOLVER_MODE and SOCRATIC_COACH_MODE")
    void testUpdatePreferences() throws Exception {
        DailyCoachPreferencesRequest request = new DailyCoachPreferencesRequest(DailyCoachMode.SOLVER_MODE, 10);
        when(service.updatePreferences(eq(userId), eq(request)))
                .thenReturn(sampleSummary(DailyCoachMode.SOLVER_MODE, 10, false, DailyCoachSessionStatus.IN_PROGRESS));

        mockMvc.perform(patch("/v1/coach/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredMode").value("SOLVER_MODE"))
                .andExpect(jsonPath("$.budgetMinutes").value(10));
    }

    @Test
    @DisplayName("POST /v1/coach/missions/{id}/complete returns 200 OK")
    void testCompleteMissionTask() throws Exception {
        DailyCoachTaskCompleteRequest request = new DailyCoachTaskCompleteRequest(taskId, true, 95);
        when(service.completeTask(eq(userId), eq(sessionId), eq(request)))
                .thenReturn(sampleSummary(DailyCoachMode.SOCRATIC_COACH_MODE, 15, false, DailyCoachSessionStatus.COMPLETED));

        mockMvc.perform(post("/v1/coach/missions/{id}/complete", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("POST /v1/coach/missions/{id}/downshift returns 200 OK with 5-min mini streak")
    void testDownshiftMission() throws Exception {
        when(service.downshiftMission(eq(userId), eq(sessionId)))
                .thenReturn(sampleSummary(DailyCoachMode.SOCRATIC_COACH_MODE, 5, true, DailyCoachSessionStatus.IN_PROGRESS));

        mockMvc.perform(post("/v1/coach/missions/{id}/downshift", sessionId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDownshifted").value(true))
                .andExpect(jsonPath("$.budgetMinutes").value(5))
                .andExpect(jsonPath("$.totalTasksCount").value(1));
    }

    @Test
    @DisplayName("INV-COACH-005 BOLA: Cross-tenant complete or downshift returns 404 Not Found")
    void testBolaCrossTenantReturns404() throws Exception {
        UUID foreignId = UUID.randomUUID();
        when(service.downshiftMission(eq(userId), eq(foreignId)))
                .thenThrow(new DailyCoachSessionNotFoundException(foreignId));
        when(service.completeTask(eq(userId), eq(foreignId), any()))
                .thenThrow(new DailyCoachSessionNotFoundException(foreignId));

        mockMvc.perform(post("/v1/coach/missions/{id}/downshift", foreignId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        DailyCoachTaskCompleteRequest request = new DailyCoachTaskCompleteRequest(taskId, true, 60);
        mockMvc.perform(post("/v1/coach/missions/{id}/complete", foreignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
