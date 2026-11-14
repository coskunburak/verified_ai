package com.verifiedai.exam.api;

import com.verifiedai.exam.application.ExamCatalogService;
import com.verifiedai.exam.application.ExamRepository;
import com.verifiedai.exam.application.StudentExamTargetService;
import com.verifiedai.exam.domain.ExamBlueprintHashCalculator;
import com.verifiedai.exam.domain.ExamBlueprintValidator;
import com.verifiedai.exam.domain.ExamBlueprintValidatorAndScoringTest;
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
import com.verifiedai.exam.domain.ExamModels.StudentExamTargetRecord;
import com.verifiedai.exam.domain.ExamModels.TargetStatus;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiExceptionHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamPlatformControllerTest {

    private static final UUID USER_A = UUID.fromString("91000000-0000-4000-8000-000000000a01");
    private static final UUID USER_B = UUID.fromString("91000000-0000-4000-8000-000000000b02");

    private MockMvc enabledMvc;
    private MockMvc disabledMvc;
    private SimpleMeterRegistry meterRegistry;
    private String tytActiveHash;

    @BeforeEach
    void setUp() {
        InMemoryExamRepository repository = new InMemoryExamRepository();
        tytActiveHash = repository.seedFixtures();

        ExamBlueprintValidator validator = new ExamBlueprintValidator(ExamBlueprintValidatorAndScoringTest.stubCatalog());
        Clock clock = Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC);
        meterRegistry = new SimpleMeterRegistry();

        ExamCatalogService enabledCatalog = new ExamCatalogService(repository, validator, meterRegistry, true);
        StudentExamTargetService enabledTargets = new StudentExamTargetService(repository, validator, meterRegistry, clock, true);
        enabledMvc = MockMvcBuilders.standaloneSetup(new ExamPlatformController(enabledCatalog, enabledTargets))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        SimpleMeterRegistry disabledRegistry = new SimpleMeterRegistry();
        ExamCatalogService disabledCatalog = new ExamCatalogService(repository, validator, disabledRegistry, false);
        StudentExamTargetService disabledTargets = new StudentExamTargetService(repository, validator, disabledRegistry, clock, false);
        disabledMvc = MockMvcBuilders.standaloneSetup(new ExamPlatformController(disabledCatalog, disabledTargets))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void disabledFeatureFlagsReturn503WithExamFeatureDisabled() throws Exception {
        disabledMvc.perform(get("/v1/exams/definitions").principal(learnerAuth(USER_A)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXAM_FEATURE_DISABLED"));

        String requestBody = """
                {
                  "schemaVersion": "student-exam-target-request-v1",
                  "examCode": "TR_YKS_TYT_MATH",
                  "targetScore": 35.0,
                  "targetDate": "2027-06-15",
                  "idempotencyKey": "disabled-test-001"
                }
                """;
        disabledMvc.perform(post("/v1/exams/targets")
                        .principal(learnerAuth(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXAM_FEATURE_DISABLED"));
    }

    @Test
    void catalogEndpointsSupportFiltersVersioningAndErrorCodes() throws Exception {
        // Unauthenticated -> 401
        enabledMvc.perform(get("/v1/exams/definitions"))
                .andExpect(status().isUnauthorized());

        // Active only default (2 active definitions seeded)
        enabledMvc.perform(get("/v1/exams/definitions").principal(learnerAuth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(jsonPath("$.schemaVersion").value("exam-definition-catalog-v1"))
                .andExpect(jsonPath("$.includeDeprecated").value(false))
                .andExpect(jsonPath("$.trainingEligible").value(false))
                .andExpect(jsonPath("$.llmTokensUsed").value(0))
                .andExpect(jsonPath("$.definitions", hasSize(2)));

        // includeDeprecated=true returns 3 entries
        enabledMvc.perform(get("/v1/exams/definitions")
                        .param("includeDeprecated", "true")
                        .param("jurisdiction", "TR")
                        .principal(learnerAuth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitions", hasSize(2)));

        // Active detail by code
        enabledMvc.perform(get("/v1/exams/definitions/TR_YKS_TYT_MATH").principal(learnerAuth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("exam-definition-detail-v1"))
                .andExpect(jsonPath("$.examVersion").value("2026.1"))
                .andExpect(jsonPath("$.blueprintHash").value(tytActiveHash))
                .andExpect(jsonPath("$.llmTokensUsed").value(0))
                .andExpect(jsonPath("$.trainingEligible").value(false));

        // Deprecated version detail
        enabledMvc.perform(get("/v1/exams/definitions/TR_YKS_TYT_MATH/versions/2025.2").principal(learnerAuth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEPRECATED"))
                .andExpect(jsonPath("$.supersededByVersion").value("2026.1"));

        // Missing definition / version -> 404
        enabledMvc.perform(get("/v1/exams/definitions/NON_EXISTENT_EXAM").principal(learnerAuth(USER_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXAM_DEFINITION_NOT_FOUND"));

        enabledMvc.perform(get("/v1/exams/definitions/TR_YKS_TYT_MATH/versions/1999.1").principal(learnerAuth(USER_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXAM_VERSION_NOT_FOUND"));
    }

    @Test
    void targetBindingIdempotencyVersioningBolaAndMetrics() throws Exception {
        String validPayload = """
                {
                  "schemaVersion": "student-exam-target-request-v1",
                  "examCode": "TR_YKS_TYT_MATH",
                  "examVersion": "2026.1",
                  "expectedBlueprintHash": "%s",
                  "targetScore": 35.00,
                  "targetDate": "2027-06-15",
                  "priorityRank": 1,
                  "isPrimary": true,
                  "idempotencyKey": "mvc-target-bind-001"
                }
                """.formatted(tytActiveHash);

        // First POST -> 201 Created
        String createdJson = enabledMvc.perform(post("/v1/exams/targets")
                        .principal(learnerAuth(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemaVersion").value("student-exam-target-v1"))
                .andExpect(jsonPath("$.blueprintHash").value(tytActiveHash))
                .andExpect(jsonPath("$.idempotentReplay").value(false))
                .andExpect(jsonPath("$.readinessGapPreview.llmTokensUsed").value(0))
                .andExpect(jsonPath("$.pinnedBlueprint.examVersion").value("2026.1"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String targetId = com.jayway.jsonpath.JsonPath.read(createdJson, "$.targetId");

        // Replay POST with same key & payload -> 200 OK
        enabledMvc.perform(post("/api/v1/exams/targets")
                        .principal(learnerAuth(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetId").value(targetId))
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        // Reuse idempotency key with mutated score -> 409 EXAM_VERSION_CONFLICT
        String conflictPayload = validPayload.replace("35.00", "38.00");
        enabledMvc.perform(post("/v1/exams/targets")
                        .principal(learnerAuth(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_VERSION_CONFLICT"));

        // Bind to deprecated 2025.2 version -> 409 EXAM_VERSION_CONFLICT
        String deprecatedBindPayload = """
                {
                  "schemaVersion": "student-exam-target-request-v1",
                  "examCode": "TR_YKS_TYT_MATH",
                  "examVersion": "2025.2",
                  "targetScore": 32.00,
                  "targetDate": "2027-06-15",
                  "idempotencyKey": "mvc-target-bind-dep"
                }
                """;
        enabledMvc.perform(post("/v1/exams/targets")
                        .principal(learnerAuth(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deprecatedBindPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_VERSION_CONFLICT"));

        // Score out of bounds -> 422 EXAM_TARGET_SCORE_OUT_OF_RANGE
        String outOfBoundsPayload = """
                {
                  "schemaVersion": "student-exam-target-request-v1",
                  "examCode": "TR_YKS_TYT_MATH",
                  "targetScore": 45.00,
                  "targetDate": "2027-06-15",
                  "idempotencyKey": "mvc-target-bind-oob"
                }
                """;
        enabledMvc.perform(post("/v1/exams/targets")
                        .principal(learnerAuth(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(outOfBoundsPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_TARGET_SCORE_OUT_OF_RANGE"));

        // Cross-learner BOLA on GET /targets/{targetId} -> 403 EXAM_ACCESS_DENIED
        enabledMvc.perform(get("/v1/exams/targets/" + targetId).principal(learnerAuth(USER_B)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EXAM_ACCESS_DENIED"));

        // Non-existent targetId -> 404 EXAM_TARGET_NOT_FOUND
        enabledMvc.perform(get("/v1/exams/targets/" + UUID.randomUUID()).principal(learnerAuth(USER_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXAM_TARGET_NOT_FOUND"));

        assertEquals(2.0, meterRegistry.get("verifiedai.exam.target.bindings").counter().count());
        assertEquals(3.0, meterRegistry.get("verifiedai.exam.validation.failures").counter().count());
    }

    private static Authentication learnerAuth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
                new VerifiedJwtPrincipal(UUID.randomUUID(), userId),
                "token",
                List.of(new SimpleGrantedAuthority("ROLE_LEARNER"))
        );
    }

    static final class InMemoryExamRepository implements ExamRepository {

        private final List<ExamDefinition> definitions = new ArrayList<>();
        private final List<StudentExamTargetRecord> targets = new ArrayList<>();

        String seedFixtures() {
            definitions.clear();
            targets.clear();

            ExamDefinition tytActive = buildDefinition(
                    "TR_YKS_TYT_MATH",
                    "2026.1",
                    Jurisdiction.TR,
                    ExamFamily.YKS,
                    ExamStatus.ACTIVE,
                    40,
                    new BigDecimal("-10.00"),
                    new BigDecimal("40.00"),
                    ScoringFormulaType.CORRECT_MINUS_QUARTER_WRONG,
                    new BigDecimal("0.2500"),
                    null,
                    "TYT_MATH",
                    "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                    "MATH.ARITHMETIC"
            );
            ExamDefinition tytDeprecated = buildDefinition(
                    "TR_YKS_TYT_MATH",
                    "2025.2",
                    Jurisdiction.TR,
                    ExamFamily.YKS,
                    ExamStatus.DEPRECATED,
                    40,
                    new BigDecimal("-10.00"),
                    new BigDecimal("40.00"),
                    ScoringFormulaType.CORRECT_MINUS_QUARTER_WRONG,
                    new BigDecimal("0.2500"),
                    "2026.1",
                    "TYT_MATH",
                    "MATH.ARITHMETIC.FRACTIONS",
                    "MATH.ARITHMETIC"
            );
            ExamDefinition satActive = buildDefinition(
                    "INTL_SAT_MATH",
                    "2026.1",
                    Jurisdiction.INTL,
                    ExamFamily.SAT,
                    ExamStatus.ACTIVE,
                    44,
                    new BigDecimal("200.00"),
                    new BigDecimal("800.00"),
                    ScoringFormulaType.RAW_CORRECT_ONLY,
                    BigDecimal.ZERO.setScale(4),
                    null,
                    "SAT_MOD_1",
                    "MATH.ALGEBRA.FACTORING",
                    "MATH.ALGEBRA"
            );

            definitions.add(tytActive);
            definitions.add(tytDeprecated);
            definitions.add(satActive);
            return tytActive.blueprintHash();
        }

        private static ExamDefinition buildDefinition(
                String code,
                String version,
                Jurisdiction jurisdiction,
                ExamFamily family,
                ExamStatus status,
                int totalQuestions,
                BigDecimal minScore,
                BigDecimal maxScore,
                ScoringFormulaType formulaType,
                BigDecimal penalty,
                String supersededBy,
                String sectionCode,
                String skillId,
                String domainCode
        ) {
            UUID defId = UUID.randomUUID();
            List<ExamTopicWeight> weights = List.of(new ExamTopicWeight(
                    UUID.randomUUID(),
                    defId,
                    sectionCode,
                    skillId,
                    domainCode,
                    BigDecimal.valueOf(totalQuestions).setScale(4),
                    Math.max(0, totalQuestions - 2),
                    totalQuestions,
                    new CognitiveLevelMix(0.25, 0.50, 0.25),
                    1
            ));
            ExamDefinition base = new ExamDefinition(
                    defId,
                    code,
                    version,
                    jurisdiction,
                    family,
                    code + " EN",
                    code + " TR",
                    status,
                    totalQuestions,
                    70,
                    formulaType,
                    penalty,
                    minScore,
                    maxScore,
                    NavigationPolicy.FREE_JUMP,
                    "0".repeat(64),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    status == ExamStatus.DEPRECATED ? Instant.parse("2026-01-01T00:00:00Z") : null,
                    supersededBy,
                    List.of(new ExamSectionSpec(sectionCode, "Main", "Ana Bölüm", totalQuestions, 70, 1)),
                    new ScoringParameters(null, null, 1, null, null, List.of()),
                    weights,
                    false,
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-01-01T00:00:00Z")
            );
            String hash = ExamBlueprintHashCalculator.computeHash(base);
            return new ExamDefinition(
                    base.id(), base.examCode(), base.examVersion(), base.jurisdiction(), base.examFamily(),
                    base.titleEn(), base.titleTr(), base.status(), base.totalQuestions(), base.durationMinutes(),
                    base.scoringFormulaType(), base.wrongPenaltyFactor(), base.minScore(), base.maxScore(),
                    base.navigationPolicy(), hash, base.effectiveFrom(), base.deprecatedAt(), base.supersededByVersion(),
                    base.sections(), base.scoringParameters(), base.topicWeights(), false, base.createdAt(), base.updatedAt()
            );
        }

        @Override
        public List<ExamDefinition> findCatalog(Jurisdiction jurisdiction, ExamFamily examFamily, boolean includeDeprecated) {
            return definitions.stream()
                    .filter(def -> includeDeprecated
                            ? (def.status() == ExamStatus.ACTIVE || def.status() == ExamStatus.DEPRECATED)
                            : def.status() == ExamStatus.ACTIVE)
                    .filter(def -> jurisdiction == null || def.jurisdiction() == jurisdiction)
                    .filter(def -> examFamily == null || def.examFamily() == examFamily)
                    .sorted(Comparator.comparing(ExamDefinition::examCode))
                    .toList();
        }

        @Override
        public Optional<ExamDefinition> findLatestByCode(String examCode, boolean includeDeprecated) {
            return definitions.stream()
                    .filter(def -> def.examCode().equals(examCode))
                    .filter(def -> includeDeprecated
                            ? (def.status() == ExamStatus.ACTIVE || def.status() == ExamStatus.DEPRECATED)
                            : def.status() == ExamStatus.ACTIVE)
                    .findFirst();
        }

        @Override
        public Optional<ExamDefinition> findByCodeAndVersion(String examCode, String examVersion) {
            return definitions.stream()
                    .filter(def -> def.examCode().equals(examCode) && def.examVersion().equals(examVersion))
                    .findFirst();
        }

        @Override
        public Optional<StudentExamTargetRecord> findTargetByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
            return targets.stream()
                    .filter(t -> t.userId().equals(userId) && t.idempotencyKey().equals(idempotencyKey))
                    .findFirst();
        }

        @Override
        public Optional<StudentExamTargetRecord> findTargetById(UUID targetId) {
            return targets.stream()
                    .filter(t -> t.targetId().equals(targetId))
                    .findFirst();
        }

        @Override
        public List<StudentExamTargetRecord> findTargetsByUserId(UUID userId) {
            return targets.stream()
                    .filter(t -> t.userId().equals(userId))
                    .toList();
        }

        @Override
        public int supersedeActivePrimaryTargets(UUID userId, Instant supersededAt) {
            int updated = 0;
            for (int i = 0; i < targets.size(); i++) {
                StudentExamTargetRecord t = targets.get(i);
                if (t.userId().equals(userId) && t.targetStatus() == TargetStatus.ACTIVE && t.isPrimary()) {
                    targets.set(i, new StudentExamTargetRecord(
                            t.targetId(), t.userId(), t.examDefinitionId(), t.examCode(), t.examVersion(),
                            t.blueprintHash(), t.targetScore(), t.targetDate(), t.priorityRank(),
                            TargetStatus.SUPERSEDED, t.isPrimary(), t.idempotencyKey(), t.snapshotPayloadJson(),
                            supersededAt, t.llmTokensUsed(), false, t.createdAt(), supersededAt
                    ));
                    updated++;
                }
            }
            return updated;
        }

        @Override
        public StudentExamTargetRecord insertTarget(StudentExamTargetRecord record) {
            Objects.requireNonNull(record);
            targets.add(record);
            return record;
        }

        @Override
        public int deleteTargetsByUserId(UUID userId) {
            int before = targets.size();
            targets.removeIf(t -> t.userId().equals(userId));
            return before - targets.size();
        }
    }
}
