package com.verifiedai.studyplan.practice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.studyplan.practice.dto.*;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PracticeSessionControllerTest {

    private MockMvc mockMvc;
    private PracticeSessionService sessionService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final UUID authenticatedUserId = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private final UUID sessionId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private final UUID itemId = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");

    @BeforeEach
    void setUp() {
        sessionService = mock(PracticeSessionService.class);
        PracticeSessionController controller = new PracticeSessionController(sessionService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.verifiedai.sharedkernel.error.ApiExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return true;
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory
                    ) {
                        return (AuthenticatedLearnerPrincipal) () -> authenticatedUserId;
                    }
                })
                .build();
    }

    private PracticeSessionResponse buildMockSessionResponse() {
        ZpdWindowDto zpd = new ZpdWindowDto(new BigDecimal("0.4000"), new BigDecimal("0.6000"), new BigDecimal("0.5000"));
        VerificationLockStateDto lockState = new VerificationLockStateDto(true, 60, 60, Instant.now());
        PracticeSessionItemDto itemDto = new PracticeSessionItemDto(
                itemId,
                sessionId,
                authenticatedUserId,
                0,
                PracticeSlotRole.TRAP_CLOSURE,
                PracticeQuestionSource.DETERMINISTIC_TWIN_TIER1,
                "TWIN-01",
                "MATH.ALG.RATIONAL_EQ",
                "ALG_DOMAIN_EXCLUSION_MISSED",
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                zpd,
                "\\frac{x}{x-2} = 3",
                "Çöz",
                "Solve",
                "3",
                "{2, 3}",
                "hash",
                true,
                lockState,
                0,
                0,
                PracticeItemStatus.PENDING,
                null,
                false,
                null,
                null,
                Instant.now()
        );

        return new PracticeSessionResponse(
                sessionId,
                authenticatedUserId,
                null,
                PracticeSessionMode.DAILY_1_2_1,
                PracticeSessionStatus.IN_PROGRESS,
                "MATH.ALG.RATIONAL_EQ",
                "ALG_DOMAIN_EXCLUSION_MISSED",
                new BigDecimal("0.5500"),
                4,
                0,
                0,
                false,
                Instant.now(),
                null,
                null,
                Instant.now(),
                List.of(itemDto)
        );
    }

    @Test
    @DisplayName("POST /v1/study-plan/practice/sessions returns HTTP 201 Created with PracticeSessionResponse")
    void testStartPracticeSessionCreated() throws Exception {
        PracticeSessionResponse response = buildMockSessionResponse();
        when(sessionService.startPracticeSession(eq(authenticatedUserId), any())).thenReturn(response);

        StartPracticeSessionRequest req = new StartPracticeSessionRequest(
                null, "MATH.ALG.RATIONAL_EQ", PracticeSessionMode.DAILY_1_2_1, "en-US"
        );

        mockMvc.perform(post("/v1/study-plan/practice/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.sessionMode").value("DAILY_1_2_1"))
                .andExpect(jsonPath("$.items[0].slotRole").value("TRAP_CLOSURE"))
                .andExpect(jsonPath("$.items[0].isTwinVerification").value(true));
    }

    @Test
    @DisplayName("GET /v1/study-plan/practice/sessions/{id} returns HTTP 200 OK")
    void testGetPracticeSessionSuccess() throws Exception {
        PracticeSessionResponse response = buildMockSessionResponse();
        when(sessionService.getPracticeSession(sessionId, authenticatedUserId)).thenReturn(response);

        mockMvc.perform(get("/v1/study-plan/practice/sessions/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("GET /v1/study-plan/practice/sessions/{id} returns HTTP 404 on BOLA mismatch")
    void testGetPracticeSessionNotFoundBola() throws Exception {
        when(sessionService.getPracticeSession(sessionId, authenticatedUserId))
                .thenThrow(new PracticeSessionNotFoundException(sessionId));

        mockMvc.perform(get("/v1/study-plan/practice/sessions/{sessionId}", sessionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /v1/study-plan/practice/sessions/{id}/items/{itemId}/submit returns HTTP 200 OK")
    void testSubmitPracticeItemSuccess() throws Exception {
        PracticeSessionResponse sessionRes = buildMockSessionResponse();
        PracticeItemSubmissionResponse submissionResponse = new PracticeItemSubmissionResponse(
                PracticeItemStatus.SUBMITTED_CORRECT,
                true,
                false,
                null,
                null,
                null,
                false,
                0,
                true,
                sessionRes
        );

        when(sessionService.submitPracticeItemAnswer(eq(sessionId), eq(itemId), eq(authenticatedUserId), any()))
                .thenReturn(submissionResponse);

        SubmitPracticeItemRequest req = new SubmitPracticeItemRequest("3", 25, false);

        mockMvc.perform(post("/v1/study-plan/practice/sessions/{sessionId}/items/{itemId}/submit", sessionId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isCorrect").value(true))
                .andExpect(jsonPath("$.trapClosureAchieved").value(true));
    }

    @Test
    @DisplayName("POST /v1/study-plan/practice/sessions/{id}/items/{itemId}/submit returns HTTP 422 on rapid guess guard")
    void testSubmitPracticeItemRapidGuessGuard() throws Exception {
        when(sessionService.submitPracticeItemAnswer(eq(sessionId), eq(itemId), eq(authenticatedUserId), any()))
                .thenThrow(new RapidGuessGuardException("RAPID_GUESS_GUARD_TRIGGERED: Take a moment to reason algebraically before guessing."));

        SubmitPracticeItemRequest req = new SubmitPracticeItemRequest("wrong", 3, false);

        mockMvc.perform(post("/v1/study-plan/practice/sessions/{sessionId}/items/{itemId}/submit", sessionId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /v1/study-plan/practice/sessions/{id}/abandon returns HTTP 200 OK")
    void testAbandonPracticeSessionSuccess() throws Exception {
        PracticeSessionResponse sessionRes = buildMockSessionResponse();
        when(sessionService.abandonPracticeSession(eq(sessionId), eq(authenticatedUserId), any()))
                .thenReturn(sessionRes);

        mockMvc.perform(post("/v1/study-plan/practice/sessions/{sessionId}/abandon", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
