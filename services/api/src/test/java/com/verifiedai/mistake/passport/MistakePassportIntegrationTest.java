package com.verifiedai.mistake.passport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.mistake.passport.controller.MistakePassportQueryController;
import com.verifiedai.mistake.passport.dto.MistakePassportResponseDto.*;
import com.verifiedai.mistake.passport.repository.MistakePassportProjectionRepository;
import com.verifiedai.mistake.passport.service.MistakePassportReadModelService;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MistakePassportIntegrationTest {

    private MockMvc mockMvc;
    private MistakePassportProjectionRepository projectionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final UUID learnerAId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID learnerBId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private UUID currentPrincipalLearnerId = learnerAId;

    @BeforeEach
    void setUp() {
        projectionRepository = mock(MistakePassportProjectionRepository.class);
        MistakePassportReadModelService readModelService = new MistakePassportReadModelService(projectionRepository);
        MistakePassportQueryController controller = new MistakePassportQueryController(readModelService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return AuthenticatedLearnerPrincipal.class.isAssignableFrom(parameter.getParameterType());
                }

                @Override
                public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory
                ) {
                    return (AuthenticatedLearnerPrincipal) () -> currentPrincipalLearnerId;
                }
            })
            .build();
    }

    @Test
    @DisplayName("BOLA Enforcement: Learner A cannot access or leak Learner B mistake passport data")
    void enforcesBolaIsolationBetweenLearners() throws Exception {
        ExamTrapItemDto learnerATrap = createTrap(
            UUID.randomUUID(), "ALG_SIGN_DISTRIBUTION", "CONCEPTUAL_STRUCTURAL",
            TrapLifecycleStatus.RECURRING_PATTERN, new BigDecimal("0.7500")
        );
        ExamTrapItemDto learnerBTrap = createTrap(
            UUID.randomUUID(), "CALC_CHAIN_RULE_OMITTED", "CONCEPTUAL_STRUCTURAL",
            TrapLifecycleStatus.IN_REMEDIATION, new BigDecimal("0.9000")
        );

        when(projectionRepository.fetchPassportItemsByLearnerId(learnerAId))
            .thenReturn(List.of(learnerATrap));
        when(projectionRepository.fetchPassportItemsByLearnerId(learnerBId))
            .thenReturn(List.of(learnerBTrap));

        // Request as Learner A
        currentPrincipalLearnerId = learnerAId;
        mockMvc.perform(get("/v1/mistake-passport").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
            .andExpect(jsonPath("$.learnerId").value(learnerAId.toString()))
            .andExpect(jsonPath("$.conceptualTraps[0].mistakeCode").value("ALG_SIGN_DISTRIBUTION"))
            .andExpect(jsonPath("$.conceptualTraps", org.hamcrest.Matchers.hasSize(1)));

        // Request as Learner B
        currentPrincipalLearnerId = learnerBId;
        mockMvc.perform(get("/v1/mistake-passport").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.learnerId").value(learnerBId.toString()))
            .andExpect(jsonPath("$.conceptualTraps[0].mistakeCode").value("CALC_CHAIN_RULE_OMITTED"))
            .andExpect(jsonPath("$.conceptualTraps", org.hamcrest.Matchers.hasSize(1)));

        verify(projectionRepository, times(1)).fetchPassportItemsByLearnerId(learnerAId);
        verify(projectionRepository, times(1)).fetchPassportItemsByLearnerId(learnerBId);
    }

    @Test
    @DisplayName("Arithmetic Slips (ARITH_BASIC_COMPUTATION) isolated to secondary tab and excluded from summary KPIs")
    void arithmeticSlipsPartitionedCorrectly() throws Exception {
        ExamTrapItemDto conceptual = createTrap(
            UUID.randomUUID(), "INEQ_NEGATIVE_MULT_NO_FLIP", "CONCEPTUAL_STRUCTURAL",
            TrapLifecycleStatus.STABLE_RESOLVED, new BigDecimal("0.8500")
        );
        ExamTrapItemDto arithmeticSlip = createTrap(
            UUID.randomUUID(), "ARITH_BASIC_COMPUTATION", "ARITHMETIC_SLIP",
            TrapLifecycleStatus.STABLE_RESOLVED, new BigDecimal("0.9500")
        );

        when(projectionRepository.fetchPassportItemsByLearnerId(learnerAId))
            .thenReturn(List.of(conceptual, arithmeticSlip));

        currentPrincipalLearnerId = learnerAId;
        mockMvc.perform(get("/v1/mistake-passport"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summaryMetrics.conqueredExamTrapsCount").value(1)) // only conceptual counted
            .andExpect(jsonPath("$.summaryMetrics.independentTransferRate").value(0.8500))
            .andExpect(jsonPath("$.conceptualTraps", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.conceptualTraps[0].mistakeCode").value("INEQ_NEGATIVE_MULT_NO_FLIP"))
            .andExpect(jsonPath("$.arithmeticSlips", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.arithmeticSlips[0].mistakeCode").value("ARITH_BASIC_COMPUTATION"));
    }

    @Test
    @DisplayName("Lifecycle status query parameter accurately filters trap list")
    void lifecycleStatusFiltering() throws Exception {
        ExamTrapItemDto recurring = createTrap(
            UUID.randomUUID(), "ALG_SIGN_DISTRIBUTION", "CONCEPTUAL_STRUCTURAL",
            TrapLifecycleStatus.RECURRING_PATTERN, new BigDecimal("0.7000")
        );
        ExamTrapItemDto resolved = createTrap(
            UUID.randomUUID(), "INEQ_NEGATIVE_MULT_NO_FLIP", "CONCEPTUAL_STRUCTURAL",
            TrapLifecycleStatus.STABLE_RESOLVED, new BigDecimal("0.9000")
        );

        when(projectionRepository.fetchPassportItemsByLearnerId(learnerAId))
            .thenReturn(List.of(recurring, resolved));

        currentPrincipalLearnerId = learnerAId;
        mockMvc.perform(get("/v1/mistake-passport").param("status", "STABLE_RESOLVED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conceptualTraps", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.conceptualTraps[0].mistakeCode").value("INEQ_NEGATIVE_MULT_NO_FLIP"))
            .andExpect(jsonPath("$.conceptualTraps[0].lifecycleStatus").value("STABLE_RESOLVED"));
    }

    @Test
    @DisplayName("Strict DOC-86 5-metric separation serialized independently")
    void verifiesFiveMetricSeparation() throws Exception {
        ExamTrapItemDto trap = new ExamTrapItemDto(
            UUID.randomUUID(),
            "ALG_SIGN_DISTRIBUTION",
            "CONCEPTUAL_STRUCTURAL",
            "MATH.ALGEBRA.SIGNS",
            "trap.alg_sign_distribution.title",
            "trap.alg_sign_distribution.coaching_framing",
            TrapLifecycleStatus.RECURRING_PATTERN,
            2,
            1,
            Instant.now(),
            null,
            null,
            new SkillFiveMetricsSnapshot(
                new BigDecimal("0.2500"), // O_{u,a}
                new BigDecimal("0.8500"), // I_{u,s}
                new BigDecimal("0.7200"), // M_{u,s}
                new BigDecimal("0.6400"), // R_{u,s}(t)
                new BigDecimal("0.1800")  // S_{u,s}
            ),
            null,
            new CounterexampleProofSnapshot(
                "H-01",
                "-2(x-3) > 8",
                "x - 3 < -4",
                "x = 0",
                "-2(0-3) = 6",
                "0 - 3 = -3 > -4",
                "6 > 8 (False)",
                "Türkçe seslendirme",
                "English speech"
            )
        );

        when(projectionRepository.fetchPassportItemsByLearnerId(learnerAId))
            .thenReturn(List.of(trap));

        currentPrincipalLearnerId = learnerAId;
        MvcResult result = mockMvc.perform(get("/v1/mistake-passport"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode metrics = json.get("conceptualTraps").get(0).get("skillMetrics");

        assertThat(metrics.get("observedOutcomeScore").asDouble()).isEqualTo(0.2500);
        assertThat(metrics.get("independentCompetenceScore").asDouble()).isEqualTo(0.8500);
        assertThat(metrics.get("posteriorMasteryProbability").asDouble()).isEqualTo(0.7200);
        assertThat(metrics.get("retentionProbability").asDouble()).isEqualTo(0.6400);
        assertThat(metrics.get("scaffoldDependencyIndex").asDouble()).isEqualTo(0.1800);

        JsonNode counterexample = json.get("conceptualTraps").get(0).get("latestCounterexample");
        assertThat(counterexample.get("cardId").asText()).isEqualTo("H-01");
        assertThat(counterexample.get("witnessSubstitutionLatex").asText()).isEqualTo("x = 0");
    }

    private ExamTrapItemDto createTrap(
            UUID id,
            String code,
            String family,
            TrapLifecycleStatus status,
            BigDecimal transferScore
    ) {
        return new ExamTrapItemDto(
            id,
            code,
            family,
            "SKILL.TEST",
            "trap." + code.toLowerCase() + ".title",
            "trap." + code.toLowerCase() + ".coaching_framing",
            status,
            1,
            0,
            Instant.now(),
            null,
            null,
            new SkillFiveMetricsSnapshot(
                BigDecimal.ZERO, transferScore, transferScore, transferScore, BigDecimal.ZERO
            ),
            null,
            null
        );
    }
}
