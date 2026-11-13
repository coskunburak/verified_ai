package com.verifiedai.tutoring.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.verifiedai.sharedkernel.error.ApiExceptionHandler;
import com.verifiedai.tutoring.application.InMemoryMicroLessonRepository;
import com.verifiedai.tutoring.application.MicroLessonApplicationService;
import com.verifiedai.tutoring.domain.MicroLessonHashVerifier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MicroLessonControllerTest {

    private static final UUID USER_A = UUID.fromString("00000000-0000-4000-8620-000000000001");
    private static final UUID USER_B = UUID.fromString("00000000-0000-4000-8620-000000000002");
    private static final UUID ATTEMPT_B = UUID.fromString("00000000-0000-4000-8620-000000000202");

    private InMemoryMicroLessonRepository repository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository = new InMemoryMicroLessonRepository();
        MicroLessonApplicationService service = new MicroLessonApplicationService(
                repository,
                new MicroLessonHashVerifier(),
                new SimpleMeterRegistry(),
                Clock.fixed(Instant.parse("2026-11-13T09:45:00Z"), ZoneOffset.UTC),
                true,
                1000,
                600000
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new MicroLessonController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        authenticateAs(USER_A);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMicroLessonSupportsV1AndApiV1RoutesAndAcceptLanguage() throws Exception {
        repository.grantActiveDiagnosis(USER_A, "ALG_SIGN_FLIP_DISTRIBUTE");

        mockMvc.perform(get("/v1/micro-lessons/ALG_SIGN_FLIP_DISTRIBUTE")
                        .param("triggerSource", "COACH_REPAIR_DRAWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mistakeCode").value("ALG_SIGN_FLIP_DISTRIBUTE"))
                .andExpect(jsonPath("$.locale").value("tr-TR"))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.verificationHash").isString())
                .andExpect(jsonPath("$.coreRule.canonicalLatexFormula").value("-a(b - c) = -ab + ac"))
                .andExpect(jsonPath("$.interactiveQuickCheck.correctOptionId").value("B"));

        mockMvc.perform(get("/api/v1/micro-lessons/ALG_SIGN_FLIP_DISTRIBUTE")
                        .header("Accept-Language", "en-US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("en-US"));
    }

    @Test
    void postCompleteMicroLessonReturnsServerGradedReceiptAndIdempotentReplay() throws Exception {
        String body = """
                {
                  "locale": "tr-TR",
                  "triggerSource": "SOCRATIC_RUNG_2",
                  "selectedOptionId": "B",
                  "dwellTimeMs": 45000,
                  "idempotencyKey": "ctrl-complete-8601"
                }
                """;

        mockMvc.perform(post("/v1/micro-lessons/ALG_SIGN_FLIP_DISTRIBUTE/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quickCheckPassed").value(true))
                .andExpect(jsonPath("$.idempotentReplay").value(false))
                .andExpect(jsonPath("$.trainingEligible").value(false));

        mockMvc.perform(post("/v1/micro-lessons/ALG_SIGN_FLIP_DISTRIBUTE/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true))
                .andExpect(jsonPath("$.trainingEligible").value(false));
    }

    @Test
    void returns401WhenUnauthenticated404OnCrossUserAttemptAnd422WithoutTrigger() throws Exception {
        repository.registerAttempt(USER_B, ATTEMPT_B, "ALG_SIGN_FLIP_DISTRIBUTE");

        mockMvc.perform(get("/v1/micro-lessons/ALG_SIGN_FLIP_DISTRIBUTE")
                        .param("sourceAttemptId", ATTEMPT_B.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MISTAKE_DIAGNOSIS_NOT_FOUND"));

        mockMvc.perform(get("/v1/micro-lessons/ALG_FRESHMAN_DREAM_POWER"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MICRO_LESSON_TRIGGER_NOT_ELIGIBLE"));

        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/v1/micro-lessons/ALG_SIGN_FLIP_DISTRIBUTE"))
                .andExpect(status().isUnauthorized());
    }

    private static void authenticateAs(UUID userId) {
        var auth = new UsernamePasswordAuthenticationToken(userId.toString(), "N/A", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
