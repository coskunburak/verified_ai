package com.verifiedai.exam.domain;

import com.verifiedai.curriculum.application.CurriculumTaxonomyCatalog;
import com.verifiedai.curriculum.application.CurriculumTaxonomySnapshot;
import com.verifiedai.exam.domain.ExamModels.CognitiveLevelMix;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.ExamFamily;
import com.verifiedai.exam.domain.ExamModels.ExamStatus;
import com.verifiedai.exam.domain.ExamModels.ExamTopicWeight;
import com.verifiedai.exam.domain.ExamModels.Jurisdiction;
import com.verifiedai.exam.domain.ExamModels.NavigationPolicy;
import com.verifiedai.exam.domain.ExamModels.ScoringFormulaType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies V054 SQL seed integrity without requiring Docker: parses the real Flyway V054 script
 * and StaticCurriculumTaxonomyCatalog source to assert weight conservation, SHA-256 hash parity,
 * zero unknown skills, and training_eligible = FALSE across all 6 canonical seeded blueprints.
 */
final class V054SeedSqlConsistencyTest {

    private static final Pattern TAXONOMY_ENTRY_PATTERN = Pattern.compile(
            "Map\\.entry\\(\"([^\"]+)\",\\s*\"([^\"]+)\"\\)"
    );

    private static final Pattern DEFINITION_TUPLE_PATTERN = Pattern.compile(
            "\\(\\s*'([0-9a-fA-F-]{36})',\\s*"
                    + "'([^']+)',\\s*'([^']+)',\\s*'([^']+)',\\s*'([^']+)',\\s*"
                    + "'([^']+)',\\s*'([^']+)',\\s*'([^']+)',\\s*(\\d+),\\s*(\\d+),\\s*"
                    + "'([^']+)',\\s*([0-9.]+),\\s*(-?[0-9.]+),\\s*([0-9.]+),\\s*'([^']+)',\\s*"
                    + "'([0-9a-f]{64})'",
            Pattern.DOTALL
    );

    private static final Pattern WEIGHT_TUPLE_PATTERN = Pattern.compile(
            "\\('([0-9a-fA-F-]{36})',\\s*'([^']+)',\\s*'([^']+)',\\s*'([^']+)',\\s*([0-9.]+),\\s*(\\d+),\\s*(\\d+),\\s*'[^']+'::jsonb,\\s*(\\d+)\\)"
    );

    @Test
    void allSixSeedDefinitionsInV054AreMathematicallyConservedAndMatchRealTaxonomy() throws Exception {
        Path v054Path = resolveRepoFile("src/main/resources/db/migration/platform/V054__exam_definitions_curriculum_mapping_and_versioning.sql");
        Path taxonomyPath = resolveRepoFile("src/main/java/com/verifiedai/curriculum/application/StaticCurriculumTaxonomyCatalog.java");

        String v054Sql = Files.readString(v054Path);
        String taxonomySource = Files.readString(taxonomyPath);

        assertTrue(v054Sql.contains("training_eligible = FALSE"), "V054 must enforce training_eligible = FALSE constraint");
        assertTrue(v054Sql.contains("llm_tokens_used = 0"), "V054 must enforce llm_tokens_used = 0 constraint");

        CurriculumTaxonomyCatalog realCatalog = buildCatalogFromSource(taxonomySource);
        ExamBlueprintValidator validator = new ExamBlueprintValidator(realCatalog);

        Map<UUID, List<ExamTopicWeight>> weightsByDef = new HashMap<>();
        Matcher weightMatcher = WEIGHT_TUPLE_PATTERN.matcher(v054Sql);
        while (weightMatcher.find()) {
            UUID defId = UUID.fromString(weightMatcher.group(1));
            ExamTopicWeight weight = new ExamTopicWeight(
                    UUID.randomUUID(),
                    defId,
                    weightMatcher.group(2),
                    weightMatcher.group(3),
                    weightMatcher.group(4),
                    new BigDecimal(weightMatcher.group(5)),
                    Integer.parseInt(weightMatcher.group(6)),
                    Integer.parseInt(weightMatcher.group(7)),
                    new CognitiveLevelMix(0.2, 0.5, 0.3),
                    Integer.parseInt(weightMatcher.group(8))
            );
            weightsByDef.computeIfAbsent(defId, ignored -> new ArrayList<>()).add(weight);
        }

        Matcher defMatcher = DEFINITION_TUPLE_PATTERN.matcher(v054Sql);
        List<String> discoveredKeys = new ArrayList<>();
        while (defMatcher.find()) {
            UUID defId = UUID.fromString(defMatcher.group(1));
            String examCode = defMatcher.group(2);
            String examVersion = defMatcher.group(3);
            List<ExamTopicWeight> weights = weightsByDef.getOrDefault(defId, List.of());

            ExamDefinition definition = new ExamDefinition(
                    defId,
                    examCode,
                    examVersion,
                    Jurisdiction.valueOf(defMatcher.group(4)),
                    ExamFamily.valueOf(defMatcher.group(5)),
                    defMatcher.group(6),
                    defMatcher.group(7),
                    ExamStatus.valueOf(defMatcher.group(8)),
                    Integer.parseInt(defMatcher.group(9)),
                    Integer.parseInt(defMatcher.group(10)),
                    ScoringFormulaType.valueOf(defMatcher.group(11)),
                    new BigDecimal(defMatcher.group(12)),
                    new BigDecimal(defMatcher.group(13)),
                    new BigDecimal(defMatcher.group(14)),
                    NavigationPolicy.valueOf(defMatcher.group(15)),
                    defMatcher.group(16),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    null,
                    null,
                    List.of(),
                    null,
                    weights,
                    false,
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-01-01T00:00:00Z")
            );

            validator.validateBlueprint(definition);
            assertEquals(defMatcher.group(16), ExamBlueprintHashCalculator.computeHash(definition));
            discoveredKeys.add(examCode + "@" + examVersion);
        }

        assertEquals(
                List.of(
                        "TR_YKS_TYT_MATH@2026.1",
                        "TR_YKS_TYT_MATH@2025.2",
                        "TR_YKS_AYT_MATH@2026.1",
                        "INTL_SAT_MATH@2026.1",
                        "INTL_AP_CALC_BC@2026.1",
                        "INTL_IB_MATH_AA_HL@2026.1"
                ),
                discoveredKeys
        );
    }

    private static Path resolveRepoFile(String relativeToApiModule) {
        Path direct = Path.of(relativeToApiModule);
        if (Files.exists(direct)) {
            return direct;
        }
        return Path.of("services/api").resolve(relativeToApiModule);
    }

    private static CurriculumTaxonomyCatalog buildCatalogFromSource(String source) {
        Map<String, String> skillToTopic = new HashMap<>();
        Matcher matcher = TAXONOMY_ENTRY_PATTERN.matcher(source);
        while (matcher.find()) {
            skillToTopic.put(matcher.group(1), matcher.group(2));
        }
        Set<String> activeTopics = Set.copyOf(skillToTopic.values());
        Set<String> activeSkills = Set.copyOf(skillToTopic.keySet());
        return new CurriculumTaxonomyCatalog() {
            @Override
            public boolean isActiveSubject(String subjectId) {
                return "MATH".equals(subjectId);
            }

            @Override
            public boolean isActiveTopic(String topicId) {
                return activeTopics.contains(topicId);
            }

            @Override
            public boolean isActiveSkill(String skillId) {
                return activeSkills.contains(skillId);
            }

            @Override
            public String topicForSkill(String skillId) {
                return skillToTopic.get(skillId);
            }

            @Override
            public String subjectForTopic(String topicId) {
                return activeTopics.contains(topicId) ? "MATH" : null;
            }

            @Override
            public Set<String> activeSkillIdsForTopic(String topicId) {
                return skillToTopic.entrySet().stream()
                        .filter(e -> e.getValue().equals(topicId))
                        .map(Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
            }

            @Override
            public CurriculumTaxonomySnapshot snapshot() {
                return new CurriculumTaxonomySnapshot(
                        "curriculum-v1-seed",
                        Set.of("MATH"),
                        activeTopics,
                        activeSkills,
                        skillToTopic,
                        Map.of()
                );
            }
        };
    }
}
