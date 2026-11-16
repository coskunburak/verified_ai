package com.verifiedai.exam.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.verifiedai.exam.domain.AppliedAccommodationSnapshot;
import com.verifiedai.exam.domain.AssembledAssessment;
import com.verifiedai.exam.domain.AssessmentBlueprint;
import com.verifiedai.exam.domain.AssessmentBlueprintMode;
import com.verifiedai.exam.domain.AssessmentBlueprintStatus;
import com.verifiedai.exam.domain.BlueprintTopicRule;
import com.verifiedai.exam.domain.CognitiveTaxonomyLevel;
import com.verifiedai.exam.domain.DeterministicAssessmentAssemblyEngine;
import com.verifiedai.exam.domain.ProvenanceVerificationStatus;
import com.verifiedai.exam.domain.QuestionBankItem;
import com.verifiedai.exam.domain.QuestionDifficultyBand;
import com.verifiedai.exam.domain.QuestionItemFormat;
import com.verifiedai.exam.domain.QuestionItemStatus;
import com.verifiedai.exam.domain.QuestionLicenseClassification;
import com.verifiedai.exam.domain.QuestionOption;
import com.verifiedai.exam.domain.QuestionProvenanceRecord;
import com.verifiedai.exam.domain.QuestionProvenanceSourceType;
import com.verifiedai.mistake.MistakeTaxonomy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Verifies V060 SQL invariants without Docker and executes the real Java
 * DeterministicAssessmentAssemblyEngine across all 5 V2 blueprints parsed from V060.
 */
final class V060MigrationSqlConsistencyTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-16T10:00:00Z");

    @Test
    void v060SqlEnforcesAllInvariantsAndAssemblesAllFiveBlueprintsDeterministically() throws Exception {
        Path migration = resolveMigrationPath("V060__yks_tyt_ayt_curriculum_expansion_and_question_bank.sql");
        String sql = Files.readString(migration, StandardCharsets.UTF_8);

        assertV060PrimaryKeysDisjointFromPriorSeeds(sql);

        assertTrue(sql.contains("ALTER TABLE exam_question_options"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS distractor_mistake_code VARCHAR(64) NULL"));
        assertTrue(sql.contains("SET status = 'DEPRECATED'"));
        assertTrue(sql.contains("superseded_by_version = '2026.2'"));

        // Parse options
        String optionsBlock = extractInsertBlock(sql, "exam_question_options");
        Map<UUID, List<QuestionOption>> optionsByItem = new HashMap<>();
        int distractorCount = 0;
        for (List<String> row : parseTuples(optionsBlock)) {
            UUID optId = UUID.fromString(row.get(0));
            UUID qId = UUID.fromString(row.get(1));
            String optKey = row.get(2);
            int dispOrder = Integer.parseInt(row.get(3));
            String md = row.get(4);
            String latex = row.get(5);
            boolean isCorrect = Boolean.parseBoolean(row.get(6));
            String rationale = row.get(7);
            String mistakeCode = row.get(8);
            boolean trainingEligible = Boolean.parseBoolean(row.get(9));

            assertFalse(trainingEligible, "ADR-006 training_eligible must be FALSE on option " + optId);
            if (!isCorrect) {
                assertNotNull(mistakeCode, "Wrong option must carry distractor_mistake_code");
                assertTrue(MistakeTaxonomy.CATEGORIES.containsKey(mistakeCode),
                        "Invalid MistakeTaxonomy code: " + mistakeCode);
                assertNotNull(rationale);
                assertTrue(rationale.trim().length() >= 10);
                distractorCount++;
            }
            optionsByItem.computeIfAbsent(qId, k -> new ArrayList<>()).add(
                    new QuestionOption(optId, qId, optKey, dispOrder, md, latex, isCorrect, rationale, false)
            );
        }
        assertTrue(distractorCount >= 480, "Expected at least 480 distractor options (120 * 4)");

        // Parse provenance
        String provBlock = extractInsertBlock(sql, "exam_question_provenance_records");
        Map<UUID, QuestionProvenanceRecord> provByItem = new HashMap<>();
        for (List<String> row : parseTuples(provBlock)) {
            UUID pId = UUID.fromString(row.get(0));
            UUID qId = UUID.fromString(row.get(1));
            String authorId = row.get(6);
            String reviewerId = row.get(7);
            String provHash = row.get(9);
            boolean trainingEligible = Boolean.parseBoolean(row.get(11));

            assertFalse(trainingEligible);
            assertFalse(authorId.equals(reviewerId), "Dual signature requires author_id <> reviewer_id");
            provByItem.put(qId, new QuestionProvenanceRecord(
                    pId, qId,
                    QuestionProvenanceSourceType.valueOf(row.get(2)),
                    QuestionLicenseClassification.valueOf(row.get(3)),
                    row.get(4), row.get(5), authorId, reviewerId,
                    ProvenanceVerificationStatus.valueOf(row.get(8)),
                    provHash, FIXED_INSTANT, false, FIXED_INSTANT
            ));
        }

        // Parse question bank items
        String itemsBlock = extractInsertBlock(sql, "exam_question_bank_items");
        List<QuestionBankItem> allItems = new ArrayList<>();
        Set<String> contentHashes = new HashSet<>();
        int tytCount = 0;
        int aytCount = 0;
        int visualDiagCount = 0;
        for (List<String> row : parseTuples(itemsBlock)) {
            UUID id = UUID.fromString(row.get(0));
            UUID defId = UUID.fromString(row.get(1));
            int verNum = Integer.parseInt(row.get(2));
            UUID secId = UUID.fromString(row.get(3));
            String qCode = row.get(4);
            String topicCode = row.get(5);
            String subtopicCode = row.get(6);
            QuestionItemFormat fmt = QuestionItemFormat.valueOf(row.get(7));
            QuestionDifficultyBand diff = QuestionDifficultyBand.valueOf(row.get(8));
            BigDecimal bVal = new BigDecimal(row.get(9));
            BigDecimal aVal = new BigDecimal(row.get(10));
            BigDecimal cVal = new BigDecimal(row.get(11));
            CognitiveTaxonomyLevel cog = CognitiveTaxonomyLevel.valueOf(row.get(12));
            String stemMd = row.get(13);
            String stemLatex = row.get(14);
            String altText = row.get(15);
            boolean hasVisual = Boolean.parseBoolean(row.get(16));
            String solMd = row.get(20);
            int estSec = Integer.parseInt(row.get(21));
            String sha = row.get(25);
            boolean trainingEligible = Boolean.parseBoolean(row.get(27));

            assertFalse(trainingEligible);
            assertTrue(contentHashes.add(sha), "Duplicate content_sha256: " + sha);
            if (hasVisual) {
                assertNotNull(altText);
                assertTrue(altText.trim().length() >= 10);
                visualDiagCount++;
            }
            if (qCode.startsWith("TYT-")) {
                tytCount++;
            } else if (qCode.startsWith("AYT-")) {
                aytCount++;
            }

            List<QuestionOption> opts = optionsByItem.get(id);
            assertNotNull(opts);
            assertEquals(5, opts.size(), "Question must have 5 options A..E: " + qCode);

            QuestionBankItem item = new QuestionBankItem(
                    id, defId, verNum, secId, qCode, topicCode, subtopicCode, fmt,
                    diff, bVal, aVal, cVal, cog, stemMd, stemLatex, altText, hasVisual,
                    null, null, Map.of(), solMd, estSec, new BigDecimal("1.00"), new BigDecimal("0.2500"),
                    QuestionItemStatus.valueOf(row.get(24)), sha, 0L, false,
                    opts, provByItem.get(id), FIXED_INSTANT, FIXED_INSTANT
            );
            assertEquals(sha, QuestionBankItem.computeContentSha256(
                    qCode, topicCode, fmt, stemMd, solMd, opts
            ), "content_sha256 parity check failed on " + qCode);
            allItems.add(item);
        }

        assertTrue(tytCount >= 60, "Expected >= 60 TYT items, got " + tytCount);
        assertTrue(aytCount >= 60, "Expected >= 60 AYT items, got " + aytCount);
        assertTrue(allItems.size() >= 120, "Expected >= 120 total items");
        assertTrue(visualDiagCount >= 12, "Expected >= 12 visual diagram items");

        // Parse blueprint topic rules
        String rulesBlock = extractInsertBlock(sql, "exam_blueprint_topic_rules");
        Map<UUID, List<BlueprintTopicRule>> rulesByBp = new HashMap<>();
        for (List<String> row : parseTuples(rulesBlock)) {
            UUID rId = UUID.fromString(row.get(0));
            UUID bpId = UUID.fromString(row.get(1));
            rulesByBp.computeIfAbsent(bpId, k -> new ArrayList<>()).add(
                    new BlueprintTopicRule(
                            rId, bpId, row.get(2),
                            Integer.parseInt(row.get(3)),
                            Integer.parseInt(row.get(4)),
                            Integer.parseInt(row.get(5)),
                            List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ),
                            Integer.parseInt(row.get(7)),
                            Boolean.parseBoolean(row.get(8))
                    )
            );
        }

        // Parse 5 V2 blueprints and assemble with real DeterministicAssessmentAssemblyEngine
        String bpBlock = extractInsertBlock(sql, "exam_assessment_blueprints");
        List<AssessmentBlueprint> blueprints = new ArrayList<>();
        for (List<String> row : parseTuples(bpBlock)) {
            UUID bpId = UUID.fromString(row.get(0));
            AssessmentBlueprint bp = new AssessmentBlueprint(
                    bpId,
                    UUID.fromString(row.get(1)),
                    Integer.parseInt(row.get(2)),
                    UUID.fromString(row.get(3)),
                    row.get(4),
                    row.get(5),
                    row.get(6),
                    AssessmentBlueprintMode.valueOf(row.get(7)),
                    Integer.parseInt(row.get(8)),
                    Integer.parseInt(row.get(9)),
                    Integer.parseInt(row.get(10)),
                    Integer.parseInt(row.get(11)),
                    Integer.parseInt(row.get(12)),
                    Integer.parseInt(row.get(13)),
                    Integer.parseInt(row.get(14)),
                    Integer.parseInt(row.get(15)),
                    Integer.parseInt(row.get(16)),
                    Integer.parseInt(row.get(17)),
                    Integer.parseInt(row.get(18)),
                    Integer.parseInt(row.get(19)),
                    Integer.parseInt(row.get(20)),
                    Boolean.parseBoolean(row.get(21)),
                    Boolean.parseBoolean(row.get(22)),
                    AssessmentBlueprintStatus.valueOf(row.get(23)),
                    row.get(24),
                    Boolean.parseBoolean(row.get(25)),
                    rulesByBp.get(bpId),
                    FIXED_INSTANT,
                    FIXED_INSTANT
            );
            blueprints.add(bp);
        }

        assertEquals(5, blueprints.size());
        DeterministicAssessmentAssemblyEngine engine = new DeterministicAssessmentAssemblyEngine();
        for (int trial = 0; trial < 100; trial++) {
            UUID userFresh = UUID.randomUUID();
            for (AssessmentBlueprint bp : blueprints) {
                try {
                    AssembledAssessment run1 = engine.assemble(new DeterministicAssessmentAssemblyEngine.AssemblyInput(
                            null, userFresh, null, bp, allItems, Set.of(), "v060-java-unit-seed",
                            AppliedAccommodationSnapshot.standardDefaults(), FIXED_INSTANT
                    ));
                    assertEquals(bp.totalQuestionCount(), run1.items().size());
                } catch (RuntimeException ex) {
                    throw new AssertionError("Fresh pool failed on bp=" + bp.blueprintCode() + " trial=" + trial + ": " + ex.getMessage(), ex);
                }
            }
        }
        for (int trial = 0; trial < 50; trial++) {
            UUID userTrial = UUID.randomUUID();
            Set<UUID> seen = new HashSet<>();
            for (AssessmentBlueprint bp : blueprints) {
                try {
                    AssembledAssessment run1 = engine.assemble(new DeterministicAssessmentAssemblyEngine.AssemblyInput(
                            null, userTrial, null, bp, allItems, seen, "v060-java-unit-seed",
                            AppliedAccommodationSnapshot.standardDefaults(), FIXED_INSTANT
                    ));
                    AssembledAssessment run2 = engine.assemble(new DeterministicAssessmentAssemblyEngine.AssemblyInput(
                            null, userTrial, null, bp, allItems, seen, "v060-java-unit-seed",
                            AppliedAccommodationSnapshot.standardDefaults(), FIXED_INSTANT
                    ));
                    assertEquals(bp.totalQuestionCount(), run1.items().size(), "Failed on " + bp.blueprintCode() + " trial=" + trial);
                    assertEquals(run1.reproducibilityHash(), run2.reproducibilityHash());
                    assertFalse(run1.trainingEligible());
                    run1.items().forEach(it -> seen.add(it.questionItemId()));
                } catch (RuntimeException ex) {
                    throw new AssertionError("Cooldown failed on bp=" + bp.blueprintCode() + " trial=" + trial + " seenCount=" + seen.size() + ": " + ex.getMessage(), ex);
                }
            }
        }
    }

    private static Path resolveMigrationPath(String filename) {
        Path direct = Path.of("src/main/resources/db/migration/platform/" + filename);
        return Files.exists(direct) ? direct : Path.of("services/api/src/main/resources/db/migration/platform/" + filename);
    }

    private static void assertV060PrimaryKeysDisjointFromPriorSeeds(String v060Sql) throws Exception {
        String priorSql = Files.readString(resolveMigrationPath("V054__exam_definitions_curriculum_mapping_and_versioning.sql"), StandardCharsets.UTF_8)
                + "\n"
                + Files.readString(resolveMigrationPath("V056__question_bank_blueprints_and_assessment_assembly.sql"), StandardCharsets.UTF_8);
        Set<String> seenV060Ids = new HashSet<>();
        for (String table : List.of(
                "exam_definitions",
                "exam_sections",
                "exam_question_bank_items",
                "exam_question_options",
                "exam_question_provenance_records",
                "exam_assessment_blueprints",
                "exam_blueprint_topic_rules"
        )) {
            String block = extractInsertBlock(v060Sql, table);
            for (List<String> row : parseTuples(block)) {
                String id = row.get(0);
                UUID.fromString(id);
                assertTrue(seenV060Ids.add(table + ":" + id), "Duplicate PK in V060 for " + table + ": " + id);
                assertFalse(priorSql.contains(id), "V060 PK collides with V054/V056 seed UUID in " + table + ": " + id);
            }
        }
    }

    private static String extractInsertBlock(String sql, String tableName) {
        Pattern p = Pattern.compile(
                "INSERT INTO " + Pattern.quote(tableName) + "\\s*\\([^)]+\\)\\s*VALUES\\s*(.*?)\\s*ON CONFLICT",
                Pattern.DOTALL
        );
        Matcher m = p.matcher(sql);
        assertTrue(m.find(), "Missing INSERT block for " + tableName);
        return m.group(1);
    }

    private static List<List<String>> parseTuples(String block) {
        List<List<String>> rows = new ArrayList<>();
        int i = 0;
        int n = block.length();
        while (i < n) {
            if (block.charAt(i) == '(') {
                i++;
                List<String> fields = new ArrayList<>();
                while (i < n) {
                    while (i < n && " \t\r\n,".indexOf(block.charAt(i)) >= 0) {
                        i++;
                    }
                    if (i < n && block.charAt(i) == ')') {
                        i++;
                        break;
                    }
                    if (block.charAt(i) == '\'') {
                        i++;
                        StringBuilder sb = new StringBuilder();
                        while (i < n) {
                            char c = block.charAt(i);
                            if (c == '\'' && i + 1 < n && block.charAt(i + 1) == '\'') {
                                sb.append('\'');
                                i += 2;
                            } else if (c == '\'') {
                                i++;
                                break;
                            } else {
                                sb.append(c);
                                i++;
                            }
                        }
                        while (i < n && block.charAt(i) != ',' && block.charAt(i) != ')' && block.charAt(i) != '\n') {
                            i++;
                        }
                        fields.add(sb.toString());
                    } else {
                        int start = i;
                        while (i < n && block.charAt(i) != ',' && block.charAt(i) != ')') {
                            i++;
                        }
                        String tok = block.substring(start, i).trim();
                        fields.add("NULL".equals(tok) ? null : tok);
                    }
                }
                rows.add(fields);
            } else {
                i++;
            }
        }
        return rows;
    }
}
