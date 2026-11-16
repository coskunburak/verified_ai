package com.verifiedai.exam.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.verifiedai.exam.application.ExamReadinessProjectionService;
import com.verifiedai.exam.application.ExamReadinessSignalReader;
import com.verifiedai.exam.application.ExamRepository;
import com.verifiedai.exam.config.ExamProperties;
import com.verifiedai.exam.domain.ExamModels.CognitiveLevelMix;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.ExamFamily;
import com.verifiedai.exam.domain.ExamModels.ExamSectionSpec;
import com.verifiedai.exam.domain.ExamModels.ExamStatus;
import com.verifiedai.exam.domain.ExamModels.ExamTopicWeight;
import com.verifiedai.exam.domain.ExamModels.Jurisdiction;
import com.verifiedai.exam.domain.ExamModels.NavigationPolicy;
import com.verifiedai.exam.domain.ExamModels.ScoringFormulaType;
import com.verifiedai.exam.domain.ExamModels.ScoringParameters;
import com.verifiedai.exam.domain.ExamReadinessCalculator;
import com.verifiedai.exam.domain.ExamReadinessCalculator.ActiveTrapSignal;
import com.verifiedai.exam.domain.ExamReadinessCalculator.SkillMasterySignal;
import com.verifiedai.exam.domain.ExamReadinessProjection;
import com.verifiedai.exam.domain.ExamReadinessRepository;
import com.verifiedai.exam.domain.ExamReadinessTopicAllocation;
import com.verifiedai.exam.events.ExamReadinessComputedEvent;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiExceptionHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Category C WebMvc tests for {@link ExamReadinessController} & {@link ExamReadinessProjectionService}
 * (endpoints, alias resolution, BOLA 403, disabled feature flag 403, 404, 401, ADR-006/INV-EXAM-004).
 */
class ExamReadinessControllerWebMvcTest {

    private static final UUID USER_A = UUID.fromString("96000000-0000-4000-8000-000000000a11");
    private static final UUID USER_B = UUID.fromString("96000000-0000-4000-8000-000000000b22");
    private static final Instant FIXED_NOW = Instant.parse("2026-11-16T12:00:00Z");

    private MockMvc mockMvc;
    private ExamProperties examProperties;
    private ApplicationEventPublisher eventPublisher;
    private InMemoryReadinessRepository readinessRepository;

    @BeforeEach
    void setUp() {
        readinessRepository = new InMemoryReadinessRepository();
        ExamReadinessSignalReader signalReader = mock(ExamReadinessSignalReader.class);
        when(signalReader.loadSkillMasterySignals(USER_A)).thenReturn(Map.of(
                "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                new SkillMasterySignal("MATH.ARITHMETIC.INTEGER_OPERATIONS", 0.72, 18.0, 0.58, 12, FIXED_NOW.minusSeconds(86400)),
                "MATH.ALGEBRA.FACTORING",
                new SkillMasterySignal("MATH.ALGEBRA.FACTORING", 0.58, 14.0, 0.48, 8, FIXED_NOW.minusSeconds(172800))
        ));
        when(signalReader.loadActiveTrapSignals(USER_A)).thenReturn(List.of(
                new ActiveTrapSignal("SIGN_FLIP", "ARITHMETIC_SLIP", "MATH.ALGEBRA.FACTORING", 0.42, 4)
        ));

        ExamRepository examRepository = mock(ExamRepository.class);
        when(examRepository.findLatestByCode("YKS_TYT", false)).thenReturn(Optional.empty());
        when(examRepository.findLatestByCode("TR_YKS_TYT_MATH", false)).thenReturn(Optional.of(tytDefinition()));
        when(examRepository.findTargetsByUserId(USER_A)).thenReturn(List.of());

        examProperties = new ExamProperties();
        examProperties.setReadinessEnabled(true);
        eventPublisher = mock(ApplicationEventPublisher.class);

        ExamReadinessProjectionService service = new ExamReadinessProjectionService(
                readinessRepository,
                signalReader,
                examRepository,
                null,
                null,
                new ExamReadinessCalculator(),
                eventPublisher,
                examProperties,
                new SimpleMeterRegistry(),
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );

        mockMvc = MockMvcBuilders.standaloneSetup(new ExamReadinessController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAndRecomputeReturnCredibleIntervalNoStoreAndPublishEvent() throws Exception {
        mockMvc.perform(get("/v1/exams/readiness")
                        .param("examCode", "YKS_TYT")
                        .principal(learnerAuth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.examCode").value("TR_YKS_TYT_MATH"))
                .andExpect(jsonPath("$.credibleIntervalLevel").value(0.90))
                .andExpect(jsonPath("$.estimatedNetLow").value(lessThanOrEqualTo(40.0)))
                .andExpect(jsonPath("$.estimatedNetHigh").value(greaterThanOrEqualTo(-10.0)))
                .andExpect(jsonPath("$.trainingEligible").value(false))
                .andExpect(jsonPath("$.llmTokensUsed").value(0))
                .andExpect(jsonPath("$.inferenceCostUsd").value(0.0));

        // Second GET via /api/v1 twin uses persisted cache (only 1 event published so far)
        mockMvc.perform(get("/api/v1/exams/readiness")
                        .param("examCode", "TR_YKS_TYT_MATH")
                        .principal(learnerAuth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examCode").value("TR_YKS_TYT_MATH"));

        verify(eventPublisher, times(1)).publishEvent(any(ExamReadinessComputedEvent.class));

        // POST /recompute forces recomputation & publishes second event
        mockMvc.perform(post("/v1/exams/readiness/recompute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"examCode":"YKS_TYT","trainingEligible":false}
                                """)
                        .principal(learnerAuth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examCode").value("TR_YKS_TYT_MATH"))
                .andExpect(jsonPath("$.trainingEligible").value(false))
                .andExpect(jsonPath("$.llmTokensUsed").value(0));

        verify(eventPublisher, times(2)).publishEvent(any(ExamReadinessComputedEvent.class));
    }

    @Test
    void rejectsBolaDisabledFeatureFlagTrainingEligibleTrueUnknownExamAndUnauthenticated() throws Exception {
        // Cross-learner BOLA -> 403 EXAM_ACCESS_DENIED
        mockMvc.perform(get("/v1/exams/readiness")
                        .param("examCode", "YKS_TYT")
                        .param("userId", USER_B.toString())
                        .principal(learnerAuth(USER_A)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EXAM_ACCESS_DENIED"));

        // trainingEligible=true in POST body -> 422
        mockMvc.perform(post("/v1/exams/readiness/recompute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"examCode":"YKS_TYT","trainingEligible":true}
                                """)
                        .principal(learnerAuth(USER_A)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"));

        // Unknown exam -> 404 EXAM_DEFINITION_NOT_FOUND
        mockMvc.perform(get("/v1/exams/readiness")
                        .param("examCode", "UNKNOWN_EXAM")
                        .principal(learnerAuth(USER_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXAM_DEFINITION_NOT_FOUND"));

        // Unauthenticated -> 401 AUTH_TOKEN_EXPIRED
        mockMvc.perform(get("/v1/exams/readiness")
                        .param("examCode", "YKS_TYT"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));

        // Disabled feature flag -> 403 EXAM_READINESS_DISABLED
        examProperties.setReadinessEnabled(false);
        mockMvc.perform(get("/v1/exams/readiness")
                        .param("examCode", "YKS_TYT")
                        .principal(learnerAuth(USER_A)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EXAM_READINESS_DISABLED"));
    }

    private static Authentication learnerAuth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
                new VerifiedJwtPrincipal(UUID.randomUUID(), userId),
                "token",
                List.of(new SimpleGrantedAuthority("ROLE_LEARNER"))
        );
    }

    private static ExamDefinition tytDefinition() {
        UUID defId = UUID.fromString("91000000-0000-4000-8000-000000000101");
        List<ExamTopicWeight> weights = List.of(
                new ExamTopicWeight(UUID.randomUUID(), defId, "TYT_MATH", "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                        "MATH.ARITHMETIC", new BigDecimal("20.0000"), 15, 25,
                        new CognitiveLevelMix(0.3, 0.5, 0.2), 1),
                new ExamTopicWeight(UUID.randomUUID(), defId, "TYT_MATH", "MATH.ALGEBRA.FACTORING",
                        "MATH.ALGEBRA", new BigDecimal("20.0000"), 15, 25,
                        new CognitiveLevelMix(0.25, 0.55, 0.2), 2)
        );
        return new ExamDefinition(
                defId,
                "TR_YKS_TYT_MATH",
                "2026.1",
                Jurisdiction.TR,
                ExamFamily.YKS,
                "YKS TYT Math",
                "YKS TYT Matematik",
                ExamStatus.ACTIVE,
                40,
                65,
                ScoringFormulaType.CORRECT_MINUS_QUARTER_WRONG,
                new BigDecimal("0.2500"),
                new BigDecimal("-10.00"),
                new BigDecimal("40.00"),
                NavigationPolicy.FREE_JUMP,
                "b".repeat(64),
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null,
                List.of(new ExamSectionSpec("TYT_MATH", "TYT Math", "TYT Matematik", 40, 65, 1)),
                new ScoringParameters(null, null, 1, null, null, List.of()),
                weights,
                false,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private static final class InMemoryReadinessRepository implements ExamReadinessRepository {
        private final Map<String, ExamReadinessProjection> store = new LinkedHashMap<>();

        @Override
        public ExamReadinessProjection saveUpsert(ExamReadinessProjection projection) {
            store.put(projection.userId() + ":" + projection.examCode(), projection);
            return projection;
        }

        @Override
        public Optional<ExamReadinessProjection> findByUserIdAndExamCode(UUID userId, String examCode) {
            return Optional.ofNullable(store.get(userId + ":" + examCode));
        }

        @Override
        public List<ExamReadinessProjection> findAllByUserId(UUID userId) {
            return store.values().stream()
                    .filter(p -> p.userId().equals(userId))
                    .toList();
        }

        @Override
        public List<ExamReadinessTopicAllocation> findTopicAllocationsByUserId(UUID userId) {
            return findAllByUserId(userId).stream()
                    .flatMap(p -> p.topRoiAllocations().stream())
                    .toList();
        }

        @Override
        public int deleteByUserId(UUID userId) {
            int before = store.size();
            store.entrySet().removeIf(e -> e.getValue().userId().equals(userId));
            return before - store.size();
        }
    }
}
