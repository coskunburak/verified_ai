package com.verifiedai.studyplan.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.studyplan.api.dto.DismissRecommendationRequest;
import com.verifiedai.studyplan.domain.*;
import com.verifiedai.studyplan.service.StudyPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecommendationControllerTest {

    private MockMvc mockMvc;
    private StudyPlanService studyPlanService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final UUID authenticatedUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        studyPlanService = mock(StudyPlanService.class);
        RecommendationController controller = new RecommendationController(studyPlanService);

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

    @Test
    @DisplayName("GET /v1/study-plan/recommendations returns HTTP 200 OK with $0.00 cost and NBA-v1.0 engine version")
    void testGetRecommendationsSuccess() throws Exception {
        LearnerRecommendationEntity entity = new LearnerRecommendationEntity(
                UUID.randomUUID(),
                authenticatedUserId,
                RecommendationType.MISTAKE_REPAIR,
                "MATH.ALG.FACTORING",
                "SIGN_TRAP",
                new BigDecimal("72.5000"),
                RecommendationRationaleCode.ACTIVE_RECURRING_TRAP,
                "{\"skillCode\":\"MATH.ALG.FACTORING\",\"mistakeCode\":\"SIGN_TRAP\",\"mistakePropensity\":72.5,\"unresolvedTrapCount\":3}",
                RecommendationStatus.ACTIVE,
                Instant.now().plusSeconds(86400),
                Instant.now()
        );

        when(studyPlanService.getOrGenerateRecommendations(eq(authenticatedUserId), anyInt(), anyBoolean()))
                .thenReturn(List.of(entity));

        mockMvc.perform(get("/v1/study-plan/recommendations")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(jsonPath("$.userId").value(authenticatedUserId.toString()))
                .andExpect(jsonPath("$.deterministicEngineVersion").value("NBA-v1.0"))
                .andExpect(jsonPath("$.llmTokenCostUsd").value(0.0))
                .andExpect(jsonPath("$.recommendations[0].targetSkillCode").value("MATH.ALG.FACTORING"))
                .andExpect(jsonPath("$.recommendations[0].targetMistakeCode").value("SIGN_TRAP"))
                .andExpect(jsonPath("$.recommendations[0].priorityScore").value(72.5000))
                .andExpect(jsonPath("$.recommendations[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /v1/study-plan/recommendations/{id}/accept returns HTTP 200 with deepLink and sessionType")
    void testAcceptRecommendationSuccess() throws Exception {
        UUID recId = UUID.randomUUID();
        LearnerRecommendationEntity entity = new LearnerRecommendationEntity(
                recId,
                authenticatedUserId,
                RecommendationType.MISTAKE_REPAIR,
                "MATH.ALG.FACTORING",
                "SIGN_TRAP",
                new BigDecimal("72.5000"),
                RecommendationRationaleCode.ACTIVE_RECURRING_TRAP,
                "{}",
                RecommendationStatus.ACCEPTED,
                Instant.now().plusSeconds(86400),
                Instant.now()
        );

        when(studyPlanService.acceptRecommendation(authenticatedUserId, recId)).thenReturn(entity);

        mockMvc.perform(post("/v1/study-plan/recommendations/{id}/accept", recId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationId").value(recId.toString()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.nextSessionType").value("TWIN_REMEDIATION"))
                .andExpect(jsonPath("$.targetSkillCode").value("MATH.ALG.FACTORING"))
                .andExpect(jsonPath("$.targetMistakeCode").value("SIGN_TRAP"))
                .andExpect(jsonPath("$.deepLinkUri").value("verifiedai://coach/session?type=TWIN_REMEDIATION&skill=MATH.ALG.FACTORING&mistake=SIGN_TRAP"));
    }

    @Test
    @DisplayName("POST /v1/study-plan/recommendations/{id}/dismiss returns HTTP 200 with status DISMISSED")
    void testDismissRecommendationSuccess() throws Exception {
        UUID recId = UUID.randomUUID();
        LearnerRecommendationEntity entity = new LearnerRecommendationEntity(
                recId,
                authenticatedUserId,
                RecommendationType.SPACED_RETENTION,
                "MATH.TRIG.IDENTITIES",
                null,
                new BigDecimal("50.0000"),
                RecommendationRationaleCode.RETENTION_HALF_LIFE_DUE,
                "{}",
                RecommendationStatus.DISMISSED,
                Instant.now().plusSeconds(86400),
                Instant.now()
        );

        when(studyPlanService.dismissRecommendation(eq(authenticatedUserId), eq(recId), any())).thenReturn(entity);

        DismissRecommendationRequest body = new DismissRecommendationRequest("ALREADY_MASTERED");

        mockMvc.perform(post("/v1/study-plan/recommendations/{id}/dismiss", recId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationId").value(recId.toString()))
                .andExpect(jsonPath("$.status").value("DISMISSED"));
    }

    @Test
    @DisplayName("POST accept returns HTTP 403 Forbidden when BOLA check fails")
    void testAcceptBolaForbidden() throws Exception {
        UUID recId = UUID.randomUUID();
        when(studyPlanService.acceptRecommendation(authenticatedUserId, recId))
                .thenThrow(new AccessDeniedException("User does not own recommendation"));

        mockMvc.perform(post("/v1/study-plan/recommendations/{id}/accept", recId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_FORBIDDEN"));
    }

    @Test
    @DisplayName("POST accept returns HTTP 404 Not Found when recommendation does not exist")
    void testAcceptNotFound() throws Exception {
        UUID nonExistent = UUID.randomUUID();
        when(studyPlanService.acceptRecommendation(authenticatedUserId, nonExistent))
                .thenThrow(new RecommendationNotFoundException(nonExistent));

        mockMvc.perform(post("/v1/study-plan/recommendations/{id}/accept", nonExistent)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST accept returns HTTP 409 Conflict when recommendation is already accepted")
    void testAcceptConflict() throws Exception {
        UUID recId = UUID.randomUUID();
        when(studyPlanService.acceptRecommendation(authenticatedUserId, recId))
                .thenThrow(new RecommendationStateConflictException("Cannot accept recommendation with status: ACCEPTED"));

        mockMvc.perform(post("/v1/study-plan/recommendations/{id}/accept", recId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }
}
