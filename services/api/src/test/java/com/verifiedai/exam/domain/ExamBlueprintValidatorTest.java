package com.verifiedai.exam.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Test;

/**
 * Validates V060 2026.2 TR_YKS_TYT_MATH and TR_YKS_AYT_MATH 40.0000 weight conservation
 * and SHA-256 blueprint hash parity against the real StaticCurriculumTaxonomyCatalog.
 */
final class ExamBlueprintValidatorTest {

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
    void v060Yks2026_2DefinitionsConserveFortyQuestionsAndMatchTaxonomyCatalog() throws Exception {
        Path v060Path = resolveRepoFile("src/main/resources/db/migration/platform/V060__yks_tyt_ayt_curriculum_expansion_and_question_bank.sql");
        Path taxonomyPath = resolveRepoFile("src/main/java/com/verifiedai/curriculum/application/StaticCurriculumTaxonomyCatalog.java");

        String v060Sql = Files.readString(v060Path);
        String taxonomySource = Files.readString(taxonomyPath);
        CurriculumTaxonomyCatalog catalog = buildCatalogFromSource(taxonomySource);
        ExamBlueprintValidator validator = new ExamBlueprintValidator(catalog);

        Map<UUID, List<ExamTopicWeight>> weightsByDef = new HashMap<>();
        Matcher wm = WEIGHT_TUPLE_PATTERN.matcher(v060Sql);
        while (wm.find()) {
            UUID defId = UUID.fromString(wm.group(1));
            weightsByDef.computeIfAbsent(defId, k -> new ArrayList<>()).add(
                    new ExamTopicWeight(
                            UUID.randomUUID(),
                            defId,
                            wm.group(2),
                            wm.group(3),
                            wm.group(4),
                            new BigDecimal(wm.group(5)),
                            Integer.parseInt(wm.group(6)),
                            Integer.parseInt(wm.group(7)),
                            new CognitiveLevelMix(0.15, 0.55, 0.30),
                            Integer.parseInt(wm.group(8))
                    )
            );
        }

        Matcher dm = DEFINITION_TUPLE_PATTERN.matcher(v060Sql);
        List<String> keys = new ArrayList<>();
        while (dm.find()) {
            UUID defId = UUID.fromString(dm.group(1));
            String examCode = dm.group(2);
            String examVersion = dm.group(3);
            List<ExamTopicWeight> weights = weightsByDef.getOrDefault(defId, List.of());
            assertFalse(weights.isEmpty(), "Expected weights for " + examCode + "@" + examVersion);

            BigDecimal sum = weights.stream()
                    .map(ExamTopicWeight::questionCountWeight)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, new BigDecimal("40.0000").compareTo(sum), "Weight sum must be 40.0000 for " + examCode);

            ExamDefinition definition = new ExamDefinition(
                    defId,
                    examCode,
                    examVersion,
                    Jurisdiction.valueOf(dm.group(4)),
                    ExamFamily.valueOf(dm.group(5)),
                    dm.group(6),
                    dm.group(7),
                    ExamStatus.valueOf(dm.group(8)),
                    Integer.parseInt(dm.group(9)),
                    Integer.parseInt(dm.group(10)),
                    ScoringFormulaType.valueOf(dm.group(11)),
                    new BigDecimal(dm.group(12)),
                    new BigDecimal(dm.group(13)),
                    new BigDecimal(dm.group(14)),
                    NavigationPolicy.valueOf(dm.group(15)),
                    dm.group(16),
                    Instant.parse("2026-11-16T00:00:00Z"),
                    null,
                    null,
                    List.of(),
                    null,
                    weights,
                    false,
                    Instant.parse("2026-11-16T00:00:00Z"),
                    Instant.parse("2026-11-16T00:00:00Z")
            );

            validator.validateBlueprint(definition);
            validator.validateTargetBindingEligibility(definition, definition.blueprintHash(), new BigDecimal("35.00"));
            assertEquals(dm.group(16), ExamBlueprintHashCalculator.computeHash(definition));
            keys.add(examCode + "@" + examVersion);
        }

        assertEquals(List.of("TR_YKS_TYT_MATH@2026.2", "TR_YKS_AYT_MATH@2026.2"), keys);
        assertTrue(v060Sql.contains("superseded_by_version = '2026.2'"));
    }

    private static Path resolveRepoFile(String relativeToApiModule) {
        Path direct = Path.of(relativeToApiModule);
        return Files.exists(direct) ? direct : Path.of("services/api").resolve(relativeToApiModule);
    }

    private static CurriculumTaxonomyCatalog buildCatalogFromSource(String source) {
        Map<String, String> skillToTopic = new HashMap<>();
        Matcher matcher = TAXONOMY_ENTRY_PATTERN.matcher(source);
        while (matcher.find()) {
            String k = matcher.group(1);
            String v = matcher.group(2);
            if (!"MATH".equals(v)) {
                skillToTopic.put(k, v);
            }
        }
        Set<String> activeTopics = Set.copyOf(skillToTopic.values());
        Set<String> activeSkills = Set.copyOf(skillToTopic.keySet());
        return new CurriculumTaxonomyCatalog() {
            @Override public boolean isActiveSubject(String subjectId) { return "MATH".equals(subjectId); }
            @Override public boolean isActiveTopic(String topicId) { return activeTopics.contains(topicId); }
            @Override public boolean isActiveSkill(String skillId) { return activeSkills.contains(skillId); }
            @Override public String topicForSkill(String skillId) { return skillToTopic.get(skillId); }
            @Override public String subjectForTopic(String topicId) { return activeTopics.contains(topicId) ? "MATH" : null; }
            @Override public Set<String> activeSkillIdsForTopic(String topicId) {
                return skillToTopic.entrySet().stream()
                        .filter(e -> e.getValue().equals(topicId))
                        .map(Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
            }
            @Override public CurriculumTaxonomySnapshot snapshot() {
                return new CurriculumTaxonomySnapshot("curriculum-v1-seed", Set.of("MATH"), activeTopics, activeSkills, skillToTopic, Map.of());
            }
        };
    }
}
