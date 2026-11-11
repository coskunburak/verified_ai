package com.verifiedai.mastery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.curriculum.api.CurriculumDagSnapshot;
import com.verifiedai.curriculum.api.CurriculumGraphQueryPort;
import com.verifiedai.mastery.api.dto.TwinSubmissionRequest;
import com.verifiedai.mastery.application.CounterfactualTwinChallengeService;
import com.verifiedai.mastery.application.KnowledgeGraphProjectionService;
import com.verifiedai.mastery.application.MathVerifierTwinPort;
import com.verifiedai.mastery.domain.*;
import com.verifiedai.mastery.web.CounterfactualTwinController;
import com.verifiedai.mastery.web.MasteryGraphController;
import com.verifiedai.security.VerifiedJwtPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class Sprint78KnowledgeGraphAndTwinIT {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private CurriculumGraphQueryPort curriculumGraphPort;
    private LearnerSkillMasteryRepository masteryRepository;
    private AttemptQueryPort attemptQueryPort;
    private MathVerifierTwinPort mathVerifierTwinPort;
    private CounterfactualTwinRepository twinRepository;
    private CounterfactualTwinSubmissionRepository submissionRepository;
    private MasteryStateLockStore lockStore;

    private final Instant baseTime = Instant.parse("2026-11-11T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID foreignUserId = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private final VerifiedJwtPrincipal principal = new VerifiedJwtPrincipal(tenantId, userId);

    @BeforeEach
    void setUp() {
        curriculumGraphPort = mock(CurriculumGraphQueryPort.class);
        masteryRepository = mock(LearnerSkillMasteryRepository.class);
        attemptQueryPort = mock(AttemptQueryPort.class);
        mathVerifierTwinPort = mock(MathVerifierTwinPort.class);
        twinRepository = mock(CounterfactualTwinRepository.class);
        submissionRepository = mock(CounterfactualTwinSubmissionRepository.class);
        lockStore = new InMemoryMasteryStateLockStore();

        KnowledgeGraphProjectionService projectionService = new KnowledgeGraphProjectionService(
                curriculumGraphPort, masteryRepository, fixedClock
        );
        CounterfactualTwinChallengeService twinService = new CounterfactualTwinChallengeService(
                lockStore, attemptQueryPort, mathVerifierTwinPort,
                twinRepository, submissionRepository, masteryRepository, fixedClock
        );

        MasteryGraphController graphController = new MasteryGraphController(projectionService);
        CounterfactualTwinController twinController = new CounterfactualTwinController(twinService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(graphController, twinController)
                .setControllerAdvice(new com.verifiedai.sharedkernel.error.ApiExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                        return parameter.getParameterType().equals(VerifiedJwtPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(
                            org.springframework.core.MethodParameter parameter,
                            org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                            org.springframework.web.context.request.NativeWebRequest webRequest,
                            org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                        return principal;
                    }
                })
                .build();
    }

    @Test
    void getSkillGraphProjectsGraphWithoutMutationsAndFlagsBottlenecks() throws Exception {
        String curriculum = "MATH_GRADE_09";
        CurriculumDagSnapshot dag = new CurriculumDagSnapshot(
                curriculum,
                List.of("PRE_01", "POST_02"),
                Map.of("PRE_01", "Prereq Isolation", "POST_02", "Linear Equations"),
                Map.of("PRE_01", List.of(), "POST_02", List.of("PRE_01")),
                List.of(new CurriculumDagSnapshot.Edge("PRE_01", "POST_02", new BigDecimal("1.0000")))
        );

        LearnerSkillMasteryEntity pre = LearnerSkillMasteryEntity.rehydrateForTest(
                tenantId, userId, curriculum, "PRE_01",
                new BigDecimal("0.4000"), "EMERGING", 2, 0, 2
        );
        LearnerSkillMasteryEntity post = LearnerSkillMasteryEntity.rehydrateForTest(
                tenantId, userId, curriculum, "POST_02",
                new BigDecimal("0.3000"), "EMERGING", 3, 2, 3
        );

        when(curriculumGraphPort.findDagByCurriculumCode(curriculum)).thenReturn(Optional.of(dag));
        when(masteryRepository.findAllByTenantIdAndUserIdAndCurriculumCode(tenantId, userId, curriculum))
                .thenReturn(List.of(pre, post));

        mockMvc.perform(get("/v1/mastery/graph")
                        .param("curriculum", curriculum)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("skill-graph-v1"))
                .andExpect(jsonPath("$.curriculumCode").value("MATH_GRADE_09"))
                .andExpect(jsonPath("$.nodes", hasSize(2)))
                .andExpect(jsonPath("$.edges[0].prerequisiteBottleneck").value(true))
                .andExpect(jsonPath("$.nodes[?(@.skillCode == 'POST_02')].blockedByPrerequisiteBottleneck").value(hasItem(true)))
                .andExpect(jsonPath("$.nodes[?(@.skillCode == 'POST_02')].masteryState").value(hasItem("AT_RISK_REMEDIATION_REQUIRED")));

        // Read purity: verify zero mutations
        verify(masteryRepository, never()).save(any());
        verify(masteryRepository, never()).delete(any());
    }

    @Test
    void prematureSolutionRevealWithoutForfeitReturns423Locked() throws Exception {
        UUID twinId = UUID.randomUUID();
        Instant createdAt = baseTime;
        Instant lockUntil = createdAt.plusSeconds(60);

        CounterfactualTwinDrillEntity twin = new CounterfactualTwinDrillEntity(
                twinId, tenantId, userId, UUID.randomUUID(),
                "MATH_09", "ALG_LIN_EQ_02", "ALG_LIN_ISO_01",
                "4x + 3 = 19", "4",
                "[{\"stepIndex\":1,\"explanation\":\"Subtract 3\",\"latexExpression\":\"4x = 16\"}]",
                "seed_abc", "a".repeat(64),
                "ISSUED", createdAt, lockUntil, false
        );

        when(twinRepository.findByTenantIdAndUserIdAndTwinId(tenantId, userId, twinId))
                .thenReturn(Optional.of(twin));

        TwinSubmissionRequest request = new TwinSubmissionRequest("REVEAL_SOLUTION", null, false);

        mockMvc.perform(post("/v1/twins/" + twinId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isLocked());
    }

    @Test
    void earlyForfeitRevealRecordsZeroCreditAndKeepsAtRiskStatus() throws Exception {
        UUID twinId = UUID.randomUUID();
        Instant createdAt = baseTime;
        Instant lockUntil = createdAt.plusSeconds(60);

        CounterfactualTwinDrillEntity twin = new CounterfactualTwinDrillEntity(
                twinId, tenantId, userId, UUID.randomUUID(),
                "MATH_09", "ALG_LIN_EQ_02", "ALG_LIN_ISO_01",
                "4x + 3 = 19", "4",
                "[{\"stepIndex\":1,\"explanation\":\"Subtract 3\",\"latexExpression\":\"4x = 16\"}]",
                "seed_abc", "a".repeat(64),
                "ISSUED", createdAt, lockUntil, false
        );

        LearnerSkillMasteryEntity mastery = LearnerSkillMasteryEntity.rehydrateForTest(
                tenantId, userId, "MATH_09", "ALG_LIN_ISO_01",
                new BigDecimal("0.4000"), "AT_RISK_REMEDIATION_REQUIRED", 3, 2, 3
        );

        when(twinRepository.findByTenantIdAndUserIdAndTwinId(tenantId, userId, twinId))
                .thenReturn(Optional.of(twin));
        when(masteryRepository.findByTenantIdAndUserIdAndSkillCode(tenantId, userId, "ALG_LIN_ISO_01"))
                .thenReturn(Optional.of(mastery));

        TwinSubmissionRequest request = new TwinSubmissionRequest("REVEAL_SOLUTION", null, true);

        mockMvc.perform(post("/v1/twins/" + twinId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLUTION_REVEALED_ASSISTED"))
                .andExpect(jsonPath("$.evaluation.evidenceClassification").value("SOLUTION_REVEALED_ASSISTED"))
                .andExpect(jsonPath("$.evaluation.remediationCredit").value(0.0))
                .andExpect(jsonPath("$.evaluation.remediationCleared").value(false))
                .andExpect(jsonPath("$.solutionSteps", hasSize(1)));
    }

    @Test
    void unassistedSuccessClearsRemediationWhenScoreReachesExitFloor() throws Exception {
        UUID twinId = UUID.randomUUID();
        Instant createdAt = baseTime;
        Instant lockUntil = createdAt.plusSeconds(60);

        CounterfactualTwinDrillEntity twin = new CounterfactualTwinDrillEntity(
                twinId, tenantId, userId, UUID.randomUUID(),
                "MATH_09", "ALG_LIN_EQ_02", "ALG_LIN_ISO_01",
                "4x + 3 = 19", "4",
                "[{\"stepIndex\":1,\"explanation\":\"Subtract 3\",\"latexExpression\":\"4x = 16\"}]",
                "seed_abc", "a".repeat(64),
                "ISSUED", createdAt, lockUntil, false
        );

        LearnerSkillMasteryEntity mastery = LearnerSkillMasteryEntity.rehydrateForTest(
                tenantId, userId, "MATH_09", "ALG_LIN_ISO_01",
                new BigDecimal("0.4500"), "AT_RISK_REMEDIATION_REQUIRED", 3, 2, 3
        );

        when(twinRepository.findByTenantIdAndUserIdAndTwinId(tenantId, userId, twinId))
                .thenReturn(Optional.of(twin));
        when(masteryRepository.findByTenantIdAndUserIdAndSkillCode(tenantId, userId, "ALG_LIN_ISO_01"))
                .thenReturn(Optional.of(mastery));
        when(mathVerifierTwinPort.verifySymbolicEquivalence("4", "4")).thenReturn(true);

        TwinSubmissionRequest request = new TwinSubmissionRequest("SUBMIT_ANSWER", "4", false);

        mockMvc.perform(post("/v1/twins/" + twinId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLVED_UNASSISTED"))
                .andExpect(jsonPath("$.evaluation.evidenceClassification").value("VERIFIED_UNASSISTED_SUCCESS"))
                .andExpect(jsonPath("$.evaluation.remediationCredit").value(0.15))
                .andExpect(jsonPath("$.evaluation.updatedMasteryScore").value(0.60))
                .andExpect(jsonPath("$.evaluation.remediationCleared").value(true));
    }

    @Test
    void crossUserOrForeignTwinAccessReturns404NotFoundToPreventEnumeration() throws Exception {
        UUID foreignTwinId = UUID.randomUUID();
        when(twinRepository.findByTenantIdAndUserIdAndTwinId(tenantId, userId, foreignTwinId))
                .thenReturn(Optional.empty());

        TwinSubmissionRequest request = new TwinSubmissionRequest("SUBMIT_ANSWER", "x = 4", false);

        mockMvc.perform(post("/v1/twins/" + foreignTwinId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
