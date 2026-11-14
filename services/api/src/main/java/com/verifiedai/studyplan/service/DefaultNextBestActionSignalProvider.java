package com.verifiedai.studyplan.service;

import com.verifiedai.curriculum.api.CurriculumDagSnapshot;
import com.verifiedai.curriculum.api.CurriculumGraphQueryPort;
import com.verifiedai.studyplan.engine.SkillCandidateSignal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class DefaultNextBestActionSignalProvider implements NextBestActionSignalProvider {

    private final JdbcClient jdbcClient;
    private final Optional<CurriculumGraphQueryPort> curriculumGraphPort;

    public DefaultNextBestActionSignalProvider(
            JdbcClient jdbcClient,
            Optional<CurriculumGraphQueryPort> curriculumGraphPort
    ) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
        this.curriculumGraphPort = Objects.requireNonNull(curriculumGraphPort, "curriculumGraphPort cannot be null");
    }

    private record ActiveTrapRow(String skillNodeId, String mistakeCode, int occurrenceCount) {}
    private record SkillMasteryRow(String skillId, double score, double halfLifeDays, Timestamp lastAssessedAt) {}

    @Override
    public List<SkillCandidateSignal> fetchSignalsForLearner(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");

        // 1. Fetch active traps from mistake_dna_profiles table
        List<ActiveTrapRow> traps = Collections.emptyList();
        try {
            traps = jdbcClient.sql("""
                    SELECT skill_node_id, mistake_code, occurrence_count
                    FROM mistake_dna_profiles
                    WHERE learner_id = :userId
                      AND lifecycle_status IN ('RECURRING_PATTERN', 'IN_REMEDIATION')
                    ORDER BY occurrence_count DESC
                    """)
                    .param("userId", userId)
                    .query((rs, rowNum) -> new ActiveTrapRow(
                            rs.getString("skill_node_id"),
                            rs.getString("mistake_code"),
                            rs.getInt("occurrence_count")
                    ))
                    .list();
        } catch (Exception ignored) {
            // Degraded path fallback
        }

        Map<String, ActiveTrapRow> dominantTrapsBySkill = new HashMap<>();
        for (ActiveTrapRow trap : traps) {
            dominantTrapsBySkill.putIfAbsent(trap.skillNodeId(), trap);
        }

        // 2. Fetch recorded skill masteries from learner_skill_mastery table
        List<SkillMasteryRow> recorded = Collections.emptyList();
        try {
            recorded = jdbcClient.sql("""
                    SELECT skill_id, score, half_life_days, last_assessed_at
                    FROM learner_skill_mastery
                    WHERE user_id = :userId
                    """)
                    .param("userId", userId)
                    .query((rs, rowNum) -> new SkillMasteryRow(
                            rs.getString("skill_id"),
                            rs.getDouble("score"),
                            rs.getDouble("half_life_days"),
                            rs.getTimestamp("last_assessed_at")
                    ))
                    .list();
        } catch (Exception ignored) {
            // Degraded path fallback
        }

        Map<String, SkillMasteryRow> masteryMap = new HashMap<>();
        for (SkillMasteryRow m : recorded) {
            masteryMap.put(m.skillId(), m);
        }

        // 3. Predecessor resolution via CurriculumDag if available
        CurriculumDagSnapshot dag = curriculumGraphPort
                .flatMap(port -> port.findDagByCurriculumCode("AYT_MAT_2026"))
                .orElse(null);

        List<SkillCandidateSignal> signals = new ArrayList<>();
        Set<String> processedSkills = new HashSet<>();
        Instant now = Instant.now();

        // Build signals for recorded masteries
        for (SkillMasteryRow entity : recorded) {
            String skillCode = entity.skillId();
            processedSkills.add(skillCode);

            ActiveTrapRow trap = dominantTrapsBySkill.get(skillCode);
            BigDecimal mistakePropensity = BigDecimal.ZERO;
            String mistakeCode = null;
            int trapCount = 0;

            if (trap != null) {
                mistakeCode = trap.mistakeCode();
                trapCount = trap.occurrenceCount();
                mistakePropensity = BigDecimal.valueOf(Math.min(100.0, trap.occurrenceCount() * 25.0));
            }

            // Estimate retention using half-life decay
            Instant lastAssessed = entity.lastAssessedAt() != null ? entity.lastAssessedAt().toInstant() : null;
            double elapsedDays = lastAssessed != null
                    ? Math.max(0.0, Duration.between(lastAssessed, now).toMinutes() / 1440.0)
                    : 0.0;
            double halfLife = Math.max(1.0, entity.halfLifeDays());
            double currentRetentionDouble = 0.05 + (entity.score() - 0.05) * Math.pow(2.0, -elapsedDays / halfLife);
            BigDecimal currentRetention = BigDecimal.valueOf(Math.min(0.99, Math.max(0.05, currentRetentionDouble)))
                    .setScale(4, RoundingMode.HALF_UP);

            BigDecimal independentMastery = BigDecimal.valueOf(entity.score()).setScale(4, RoundingMode.HALF_UP);

            // Prerequisite bottleneck resolution
            String weakestPrereqCode = null;
            BigDecimal weakestPrereqMastery = null;

            if (dag != null) {
                List<String> predecessors = dag.predecessorsOf(skillCode);
                for (String p : predecessors) {
                    SkillMasteryRow pEntity = masteryMap.get(p);
                    BigDecimal pScore = pEntity != null
                            ? BigDecimal.valueOf(pEntity.score()).setScale(4, RoundingMode.HALF_UP)
                            : new BigDecimal("0.2500");

                    if (weakestPrereqMastery == null || pScore.compareTo(weakestPrereqMastery) < 0) {
                        weakestPrereqCode = p;
                        weakestPrereqMastery = pScore;
                    }
                }
            }

            signals.add(SkillCandidateSignal.builder(skillCode)
                    .dominantMistake(mistakeCode, mistakePropensity, trapCount)
                    .retention(currentRetention, BigDecimal.valueOf(halfLife).setScale(2, RoundingMode.HALF_UP))
                    .independentMastery(independentMastery)
                    .exam("AYT_MAT_2026", new BigDecimal("0.85"))
                    .weakestPrerequisite(weakestPrereqCode, weakestPrereqMastery)
                    .build());
        }

        // Add any active traps not yet recorded in mastery table
        for (Map.Entry<String, ActiveTrapRow> entry : dominantTrapsBySkill.entrySet()) {
            String skillCode = entry.getKey();
            if (processedSkills.add(skillCode)) {
                ActiveTrapRow trap = entry.getValue();
                signals.add(SkillCandidateSignal.builder(skillCode)
                        .dominantMistake(
                                trap.mistakeCode(),
                                BigDecimal.valueOf(Math.min(100.0, trap.occurrenceCount() * 25.0)),
                                trap.occurrenceCount()
                        )
                        .retention(new BigDecimal("0.5000"), new BigDecimal("14.00"))
                        .independentMastery(new BigDecimal("0.3500"))
                        .exam("AYT_MAT_2026", new BigDecimal("0.90"))
                        .build());
            }
        }

        // If signals are empty (cold start learner), load calibrated Sprint 8.10 priors if present
        // or seed high-ROI foundational curriculum topics
        if (signals.isEmpty()) {
            List<SkillCandidateSignal> calibratedColdStart = fetchCalibratedColdStartSignals(userId);
            if (!calibratedColdStart.isEmpty()) {
                signals.addAll(calibratedColdStart);
            } else {
                signals.addAll(createColdStartBaselineSignals());
            }
        }

        return signals;
    }

    private List<SkillCandidateSignal> fetchCalibratedColdStartSignals(UUID userId) {
        try {
            String outcomesJson = jdbcClient.sql("""
                    SELECT item_outcomes_json::text
                    FROM cold_start_diagnostics
                    WHERE learner_id = :userId
                    ORDER BY created_at DESC
                    LIMIT 1
                    """)
                    .param("userId", userId)
                    .query(String.class)
                    .optional()
                    .orElse(null);
            if (outcomesJson == null || outcomesJson.isBlank()) {
                return List.of();
            }
            JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
            List<Map<String, Object>> outcomes = mapper.readValue(outcomesJson, new TypeReference<>() {});
            List<SkillCandidateSignal> list = new ArrayList<>();
            for (Map<String, Object> row : outcomes) {
                Object skillObj = row.get("skillCode");
                if (!(skillObj instanceof String skillCode) || skillCode.isBlank()) {
                    continue;
                }
                double prior = row.get("assignedPrior") instanceof Number n ? n.doubleValue() : 0.25;
                String trap = row.get("detectedTrapArchetype") instanceof String t && !t.isBlank() ? t : null;
                list.add(SkillCandidateSignal.builder(skillCode)
                        .dominantMistake(trap, trap != null ? new BigDecimal("50.0000") : BigDecimal.ZERO, trap != null ? 1 : 0)
                        .retention(BigDecimal.valueOf(prior).setScale(4, RoundingMode.HALF_UP), new BigDecimal("7.00"))
                        .independentMastery(BigDecimal.valueOf(prior).setScale(4, RoundingMode.HALF_UP))
                        .exam("AYT_MAT_2026", new BigDecimal("0.8500"))
                        .build());
            }
            return list;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<SkillCandidateSignal> createColdStartBaselineSignals() {
        return List.of(
                SkillCandidateSignal.builder("MATH.ALG.QUADRATICS.FACTORING")
                        .dominantMistake("SIGN_DISTRIBUTION_TRAP", new BigDecimal("45.0000"), 2)
                        .retention(new BigDecimal("0.6000"), new BigDecimal("14.00"))
                        .independentMastery(new BigDecimal("0.4000"))
                        .exam("AYT_MAT_2026", new BigDecimal("0.8000"))
                        .build(),
                SkillCandidateSignal.builder("MATH.CALC.LIMITS.BASIC")
                        .dominantMistake(null, BigDecimal.ZERO, 0)
                        .retention(new BigDecimal("0.7000"), new BigDecimal("21.00"))
                        .independentMastery(new BigDecimal("0.4500"))
                        .exam("AYT_MAT_2026", new BigDecimal("0.8500"))
                        .build(),
                SkillCandidateSignal.builder("MATH.TRIG.IDENTITIES.PYTHAGOREAN")
                        .dominantMistake(null, BigDecimal.ZERO, 0)
                        .retention(new BigDecimal("0.4500"), new BigDecimal("10.00"))
                        .independentMastery(new BigDecimal("0.5000"))
                        .exam("AYT_MAT_2026", new BigDecimal("0.7500"))
                        .build(),
                SkillCandidateSignal.builder("MATH.STAT.PROBABILITY.BAYES")
                        .dominantMistake(null, BigDecimal.ZERO, 0)
                        .retention(new BigDecimal("0.8000"), new BigDecimal("30.00"))
                        .independentMastery(new BigDecimal("0.3000"))
                        .exam("AYT_MAT_2026", new BigDecimal("0.9000"))
                        .build()
        );
    }
}
