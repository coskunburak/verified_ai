package com.verifiedai.exam.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.verifiedai.exam.application.CandidateProfileRepository;
import com.verifiedai.exam.application.ExamAssessmentAssemblyService;
import com.verifiedai.exam.domain.AssembledAssessment;
import com.verifiedai.exam.domain.AssembledAssessmentRepository;
import com.verifiedai.exam.domain.AssessmentBlueprint;
import com.verifiedai.exam.domain.AssessmentBlueprintMode;
import com.verifiedai.exam.domain.AssessmentBlueprintRepository;
import com.verifiedai.exam.domain.AssessmentBlueprintStatus;
import com.verifiedai.exam.domain.BlueprintTopicRule;
import com.verifiedai.exam.domain.CognitiveTaxonomyLevel;
import com.verifiedai.exam.domain.DeterministicAssessmentAssemblyEngine;
import com.verifiedai.exam.domain.ProvenanceVerificationStatus;
import com.verifiedai.exam.domain.QuestionBankItem;
import com.verifiedai.exam.domain.QuestionBankRepository;
import com.verifiedai.exam.domain.QuestionDifficultyBand;
import com.verifiedai.exam.domain.QuestionItemFormat;
import com.verifiedai.exam.domain.QuestionItemStatus;
import com.verifiedai.exam.domain.QuestionLicenseClassification;
import com.verifiedai.exam.domain.QuestionOption;
import com.verifiedai.exam.domain.QuestionProvenanceRecord;
import com.verifiedai.exam.domain.QuestionProvenanceSourceType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ExamAssessmentAssemblyControllerTest {

    private static final UUID EXAM_DEF_ID = UUID.fromString("91000000-0000-4000-8000-000000000101");
    private static final UUID BLUEPRINT_ID = UUID.fromString("94000000-0000-4000-8000-000000000101");
    private static final UUID LEARNER_ID = UUID.fromString("99999999-1111-4222-8333-444444444444");
    private static final UUID OTHER_USER_ID = UUID.fromString("88888888-1111-4222-8333-444444444444");
    private static final Instant FIXED_NOW = Instant.parse("2026-03-01T10:00:00Z");

    private QuestionBankRepository questionBankRepository;
    private AssessmentBlueprintRepository blueprintRepository;
    private AssembledAssessmentRepository assembledAssessmentRepository;
    private CandidateProfileRepository candidateProfileRepository;
    private ApplicationEventPublisher eventPublisher;
    private SimpleMeterRegistry meterRegistry;
    private ExamAssessmentAssemblyService assemblyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        questionBankRepository = mock(QuestionBankRepository.class);
        blueprintRepository = mock(AssessmentBlueprintRepository.class);
        assembledAssessmentRepository = mock(AssembledAssessmentRepository.class);
        candidateProfileRepository = mock(CandidateProfileRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        meterRegistry = new SimpleMeterRegistry();

        assemblyService = new ExamAssessmentAssemblyService(
                questionBankRepository,
                blueprintRepository,
                assembledAssessmentRepository,
                candidateProfileRepository,
                new DeterministicAssessmentAssemblyEngine(),
                eventPublisher,
                meterRegistry,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
                true,
                true,
                true
        );

        ExamAssessmentAssemblyController controller = new ExamAssessmentAssemblyController(assemblyService);
        HandlerMethodArgumentResolver jwtResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return Jwt.class.isAssignableFrom(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory
            ) {
                String subject = webRequest.getHeader("X-Test-Jwt-Sub");
                if (subject == null || subject.isBlank()) {
                    subject = LEARNER_ID.toString();
                }
                return Jwt.withTokenValue("mock-jwt-token")
                        .header("alg", "RS256")
                        .subject(subject)
                        .build();
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(jwtResolver)
                .build();
    }

    @Test
    void createAndPublishQuestionBankItemAcrossDualRoutes() throws Exception {
        when(questionBankRepository.findByQuestionCode("TYT-MATH-NEW-001")).thenReturn(Optional.empty());
        when(questionBankRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String createPayload = """
                {
                  "examVersionNumber": 1,
                  "questionCode": "TYT-MATH-NEW-001",
                  "topicCode": "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                  "subtopicCode": "SIGNED_INTEGERS",
                  "itemFormat": "SINGLE_CHOICE_MCQ",
                  "difficultyBand": "MEDIUM",
                  "difficultyBValue": 0.15,
                  "discriminationAValue": 1.15,
                  "guessingCValue": 0.20,
                  "cognitiveLevel": "APPLICATION",
                  "stemMarkdown": "Evaluate (-15) + 28 - (-7) and select the correct option.",
                  "hasVisualDiagram": false,
                  "solutionExplanationMarkdown": "Adding 28 to -15 gives 13, and adding 7 gives 20.",
                  "estimatedTimeSeconds": 75,
                  "maxPoints": 1.00,
                  "negativeMarkingWeight": 0.2500,
                  "options": [
                    {"optionKey": "A", "displayOrder": 1, "contentMarkdown": "6", "isCorrect": false},
                    {"optionKey": "B", "displayOrder": 2, "contentMarkdown": "13", "isCorrect": false},
                    {"optionKey": "C", "displayOrder": 3, "contentMarkdown": "20", "isCorrect": true},
                    {"optionKey": "D", "displayOrder": 4, "contentMarkdown": "36", "isCorrect": false},
                    {"optionKey": "E", "displayOrder": 5, "contentMarkdown": "50", "isCorrect": false}
                  ],
                  "provenance": {
                    "sourceType": "ORIGINAL_AUTHORED",
                    "licenseClassification": "PROPRIETARY_INTERNAL",
                    "copyrightHolder": "VerifiedAI Assessment Guild",
                    "sourceReference": "TYT-2026-NEW-001",
                    "authorId": "sme.author.ayse",
                    "reviewerId": "sme.reviewer.mehmet",
                    "verificationStatus": "VERIFIED"
                  },
                  "trainingEligible": false
                }
                """;

        mockMvc.perform(post("/v1/exams/{examDefinitionId}/question-bank/items", EXAM_DEF_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.questionCode").value("TYT-MATH-NEW-001"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.trainingEligible").value(false));

        UUID itemId = UUID.fromString("93000000-0000-4000-8000-000000000101");
        QuestionBankItem draftItem = buildQuestionBankPool().getFirst();
        when(questionBankRepository.findById(itemId)).thenReturn(Optional.of(draftItem));

        mockMvc.perform(post("/api/v1/exams/{examDefinitionId}/question-bank/items/{itemId}/publish", EXAM_DEF_ID, itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.trainingEligible").value(false));
    }

    @Test
    void assembleAssessmentReturns201Then200OnReplayAndOmitsAnswerKeys() throws Exception {
        AssessmentBlueprint activeBp = buildActiveBlueprint();
        List<QuestionBankItem> pool = buildQuestionBankPool();

        when(blueprintRepository.findById(BLUEPRINT_ID)).thenReturn(Optional.of(activeBp));
        when(candidateProfileRepository.findProfileByUserId(LEARNER_ID)).thenReturn(Optional.empty());
        when(assembledAssessmentRepository.findRecentlySeenQuestionIds(eq(LEARNER_ID), eq(EXAM_DEF_ID), any()))
                .thenReturn(Set.of());
        when(questionBankRepository.findByExamDefinition(EXAM_DEF_ID, 1, QuestionItemStatus.PUBLISHED, null, null))
                .thenReturn(pool);

        final AssembledAssessment[] stored = new AssembledAssessment[1];
        when(assembledAssessmentRepository.findByUserIdAndBlueprintIdAndSeed(LEARNER_ID, BLUEPRINT_ID, "seed-mvc-2026"))
                .thenAnswer(inv -> Optional.ofNullable(stored[0]));
        when(assembledAssessmentRepository.save(any())).thenAnswer(inv -> {
            stored[0] = inv.getArgument(0);
            return stored[0];
        });

        String assemblePayload = """
                {
                  "userId": "99999999-1111-4222-8333-444444444444",
                  "blueprintId": "94000000-0000-4000-8000-000000000101",
                  "assemblySeed": "seed-mvc-2026",
                  "overrideTimeMultiplier": 1.50,
                  "highContrastMode": true,
                  "screenReaderOptimized": true,
                  "extendedBreakPrompts": true,
                  "trainingEligible": false
                }
                """;

        MvcResult freshResult = mockMvc.perform(post("/v1/exams/{examDefinitionId}/assessments/assemble", EXAM_DEF_ID)
                        .header("X-User-Id", LEARNER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assemblePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalQuestionCount").value(8))
                .andExpect(jsonPath("$.baseDurationSeconds").value(900))
                .andExpect(jsonPath("$.effectiveDurationSeconds").value(1350))
                .andExpect(jsonPath("$.trainingEligible").value(false))
                .andReturn();

        String body = freshResult.getResponse().getContentAsString();
        assertThat(body).doesNotContain("isCorrect");
        assertThat(body).doesNotContain("distractorRationale");
        assertThat(body).doesNotContain("numericCorrectAnswer");
        assertThat(body).doesNotContain("solutionExplanationMarkdown");

        // Idempotent replay on /api/v1 mirror returns 200 OK with identical reproducibilityHash
        mockMvc.perform(post("/api/v1/exams/{examDefinitionId}/assessments/assemble", EXAM_DEF_ID)
                        .header("X-User-Id", LEARNER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assemblePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reproducibilityHash").value(stored[0].reproducibilityHash()));
    }

    @Test
    void enforcesBolaIsolationAndFeatureFlagDisabledStatusCodes() throws Exception {
        UUID assessmentId = UUID.randomUUID();
        mockMvc.perform(get("/v1/exams/candidates/{userId}/assessments/{assessmentId}", OTHER_USER_ID, assessmentId)
                        .header("X-User-Id", LEARNER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EXAM_CANDIDATE_ACCESS_DENIED"));

        assemblyService.setBlueprintManagementEnabled(false);
        mockMvc.perform(get("/v1/exams/{examDefinitionId}/blueprints", EXAM_DEF_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXAM_BLUEPRINT_DISABLED"));
    }

    private static AssessmentBlueprint buildActiveBlueprint() {
        List<BlueprintTopicRule> rules = List.of(
                new BlueprintTopicRule(UUID.randomUUID(), BLUEPRINT_ID, "MATH.ARITHMETIC.INTEGER_OPERATIONS", 2500, 1, 3, List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ), 1, false),
                new BlueprintTopicRule(UUID.randomUUID(), BLUEPRINT_ID, "MATH.ARITHMETIC.FRACTIONS", 2500, 1, 3, List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ), 2, false),
                new BlueprintTopicRule(UUID.randomUUID(), BLUEPRINT_ID, "MATH.ARITHMETIC.PERCENTAGES", 2500, 1, 3, List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ), 3, false),
                new BlueprintTopicRule(UUID.randomUUID(), BLUEPRINT_ID, "MATH.ALGEBRA.FACTORING", 2500, 1, 3, List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ), 4, false)
        );
        return new AssessmentBlueprint(
                BLUEPRINT_ID,
                EXAM_DEF_ID,
                1,
                null,
                "BP-TYT-MATH-DIAG-2026-V1",
                "TYT Mathematics Diagnostic Baseline (8 Items)",
                "Deterministic 8-question diagnostic baseline",
                AssessmentBlueprintMode.DIAGNOSTIC_BASELINE,
                8,
                900,
                2500, 5000, 2500, 0,
                500,
                2500, 2500, 2500, 2500, 0,
                14,
                true,
                true,
                AssessmentBlueprintStatus.ACTIVE,
                null,
                false,
                rules,
                FIXED_NOW,
                FIXED_NOW
        );
    }

    private static List<QuestionBankItem> buildQuestionBankPool() {
        String[] topics = {
                "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                "MATH.ARITHMETIC.FRACTIONS",
                "MATH.ARITHMETIC.PERCENTAGES",
                "MATH.ALGEBRA.FACTORING"
        };
        QuestionDifficultyBand[] bands = {
                QuestionDifficultyBand.EASY,
                QuestionDifficultyBand.MEDIUM,
                QuestionDifficultyBand.MEDIUM,
                QuestionDifficultyBand.HARD
        };
        CognitiveTaxonomyLevel[] cogLevels = {
                CognitiveTaxonomyLevel.RECALL,
                CognitiveTaxonomyLevel.COMPREHENSION,
                CognitiveTaxonomyLevel.APPLICATION,
                CognitiveTaxonomyLevel.ANALYSIS
        };

        List<QuestionBankItem> items = new ArrayList<>(16);
        int seq = 1;
        for (String topic : topics) {
            for (int j = 0; j < 4; j++) {
                UUID itemId = UUID.fromString(String.format("93000000-0000-4000-8000-0000000001%02d", seq));
                List<QuestionOption> options = List.of(
                        new QuestionOption(UUID.randomUUID(), itemId, "A", 1, "Choice A for " + seq, null, true, "Correct rationale", false),
                        new QuestionOption(UUID.randomUUID(), itemId, "B", 2, "Choice B for " + seq, null, false, "Distractor B", false),
                        new QuestionOption(UUID.randomUUID(), itemId, "C", 3, "Choice C for " + seq, null, false, "Distractor C", false),
                        new QuestionOption(UUID.randomUUID(), itemId, "D", 4, "Choice D for " + seq, null, false, "Distractor D", false),
                        new QuestionOption(UUID.randomUUID(), itemId, "E", 5, "Choice E for " + seq, null, false, "Distractor E", false)
                );
                QuestionProvenanceRecord prov = new QuestionProvenanceRecord(
                        UUID.randomUUID(),
                        itemId,
                        QuestionProvenanceSourceType.ORIGINAL_AUTHORED,
                        QuestionLicenseClassification.PROPRIETARY_INTERNAL,
                        "VerifiedAI Assessment Guild",
                        "REF-" + seq,
                        "sme.author.1",
                        "sme.reviewer.2",
                        ProvenanceVerificationStatus.VERIFIED,
                        null,
                        FIXED_NOW,
                        false,
                        FIXED_NOW
                );
                items.add(new QuestionBankItem(
                        itemId,
                        EXAM_DEF_ID,
                        1,
                        null,
                        String.format("TYT-MATH-POOL-%03d", seq),
                        topic,
                        "SUB_" + j,
                        QuestionItemFormat.SINGLE_CHOICE_MCQ,
                        bands[j],
                        BigDecimal.valueOf(j - 1.0),
                        new BigDecimal("1.10"),
                        new BigDecimal("0.20"),
                        cogLevels[j],
                        "Deterministic stem markdown content for question item number " + seq,
                        null,
                        null,
                        false,
                        null,
                        null,
                        Map.of(),
                        "Deterministic step-by-step solution explanation for item " + seq,
                        75,
                        new BigDecimal("1.00"),
                        new BigDecimal("0.2500"),
                        QuestionItemStatus.PUBLISHED,
                        null,
                        0L,
                        false,
                        options,
                        prov,
                        FIXED_NOW,
                        FIXED_NOW
                ));
                seq++;
            }
        }
        return items;
    }
}
