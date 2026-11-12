package com.verifiedai.studyplan.spaced;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.studyplan.spaced.dto.*;
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

class SpacedReviewControllerTest {

    private MockMvc mockMvc;
    private SpacedReviewService service;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final UUID authenticatedUserId = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private final UUID itemId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @BeforeEach
    void setUp() {
        service = mock(SpacedReviewService.class);
        SpacedReviewController controller = new SpacedReviewController(service);

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
    @DisplayName("GET /v1/study-plan/spaced-queue returns 200 OK with capped interleaved queue")
    void testGetDailySpacedQueue() throws Exception {
        SpacedQueueItemDto item = new SpacedQueueItemDto(
                itemId, "MATH.ALG.QUADRATIC_DISCRIMINANT", "Quadratic Discriminant Analysis",
                SpacedTriggerType.TRAP_IMMUNITY_7D, "TRAP_SIGN_DISTRIB_NEG",
                new BigDecimal("0.7100"), new BigDecimal("7.00"), Instant.now(),
                UUID.randomUUID(), "\\Delta = b^2 - 4ac", SpacedQueueStatus.SERVED,
                "studyplan.spaced.badge.trap_immunity_7d"
        );

        SpacedQueueResponse response = new SpacedQueueResponse(
                "1.0.0", authenticatedUserId, Instant.now(), 3, 1, 1, true, List.of(item)
        );

        when(service.getDailySpacedQueue(eq(authenticatedUserId), any())).thenReturn(response);

        mockMvc.perform(get("/v1/study-plan/spaced-queue")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("1.0.0"))
                .andExpect(jsonPath("$.userId").value(authenticatedUserId.toString()))
                .andExpect(jsonPath("$.dailyCap").value(3))
                .andExpect(jsonPath("$.items[0].canonicalSkillCode").value("MATH.ALG.QUADRATIC_DISCRIMINANT"))
                .andExpect(jsonPath("$.items[0].triggerType").value("TRAP_IMMUNITY_7D"));
    }

    @Test
    @DisplayName("POST /v1/study-plan/spaced-queue/{itemId}/complete returns 200 OK on successful submission")
    void testCompleteSpacedItemSuccess() throws Exception {
        SpacedItemCompleteRequest request = new SpacedItemCompleteRequest(true, true, 8500L, null);
        SpacedItemCompletionResponse completionResponse = new SpacedItemCompletionResponse(
                itemId, new BigDecimal("7.00"), new BigDecimal("15.40"),
                new BigDecimal("0.8500"), Instant.now().plusSeconds(604800),
                MistakeResolutionStatus.STABLE_RESOLVED
        );

        when(service.completeQueueItem(eq(authenticatedUserId), eq(itemId), any(), any()))
                .thenReturn(completionResponse);

        mockMvc.perform(post("/v1/study-plan/spaced-queue/{itemId}/complete", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(itemId.toString()))
                .andExpect(jsonPath("$.mistakeResolutionStatus").value("STABLE_RESOLVED"))
                .andExpect(jsonPath("$.updatedHalfLifeDays").value(15.40));
    }

    @Test
    @DisplayName("BOLA: POST /complete on unknown or cross-tenant itemId returns 404 Not Found RFC 9457")
    void testBOLACrossTenantAccessReturns404() throws Exception {
        UUID crossTenantItemId = UUID.randomUUID();
        when(service.completeQueueItem(eq(authenticatedUserId), eq(crossTenantItemId), any(), any()))
                .thenThrow(new SpacedQueueItemNotFoundException(crossTenantItemId));

        SpacedItemCompleteRequest request = new SpacedItemCompleteRequest(true, true, 8500L, null);

        mockMvc.perform(post("/v1/study-plan/spaced-queue/{itemId}/complete", crossTenantItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /complete with rapid-guess violation (<3000ms) returns 422 Unprocessable Entity")
    void testRapidGuessGuardReturns422() throws Exception {
        when(service.completeQueueItem(eq(authenticatedUserId), eq(itemId), any(), any()))
                .thenThrow(new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.REQUEST_VALIDATION_FAILED,
                        "Rapid guess guard triggered.",
                        true,
                        "REASON_BEFORE_SUBMITTING"
                ));

        SpacedItemCompleteRequest fastRequest = new SpacedItemCompleteRequest(true, true, 1200L, null);

        mockMvc.perform(post("/v1/study-plan/spaced-queue/{itemId}/complete", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fastRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"));
    }
}
