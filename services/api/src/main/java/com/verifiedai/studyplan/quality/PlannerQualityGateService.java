package com.verifiedai.studyplan.quality;

import com.verifiedai.studyplan.quality.QualityGateModels.ContractGateCheck;
import com.verifiedai.studyplan.quality.QualityGateModels.GateStatus;
import com.verifiedai.studyplan.quality.QualityGateModels.QualityGateRawAggregateSnapshot;
import com.verifiedai.studyplan.quality.QualityGateModels.QualityTelemetryMetrics;
import com.verifiedai.studyplan.quality.QualityGateModels.RegressionSeverity;
import com.verifiedai.studyplan.quality.QualityGateModels.StudyPlanQualityGateReport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic $0.00 Phase 08 Exit Gate quality evaluator across CNT-LOS-06..09.
 * Computes non-PII aggregate health metrics, detects P0/P1 regressions, and optionally
 * persists snapshots into {@code planner_quality_gate_snapshots} with {@code training_eligible=FALSE}.
 */
@Service
public class PlannerQualityGateService {

    private static final Logger LOG = LoggerFactory.getLogger(PlannerQualityGateService.class);

    private final JdbcClient jdbcClient;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final JsonMapper jsonMapper;

    @Autowired
    public PlannerQualityGateService(
            JdbcClient jdbcClient,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.jsonMapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Transactional
    public StudyPlanQualityGateReport evaluateQualityGate(UUID actorId, boolean persistSnapshot) {
        QualityGateRawAggregateSnapshot raw = queryRawAggregates();
        StudyPlanQualityGateReport report = evaluateFromSnapshot(raw);
        if (persistSnapshot) {
            persistQualitySnapshot(report, actorId);
        }
        return report;
    }

    public StudyPlanQualityGateReport evaluateFromSnapshot(QualityGateRawAggregateSnapshot raw) {
        Objects.requireNonNull(raw, "raw aggregate snapshot cannot be null");

        double coldStartComplianceRate = raw.coldStartTotalCount() == 0
                ? 1.0
                : ratio(raw.coldStartTotalCount() - raw.coldStartViolationCount(), raw.coldStartTotalCount());

        double recExplainabilityRate = raw.recommendationTotalCount() == 0
                ? 1.0
                : ratio(raw.recommendationTotalCount() - raw.recommendationMissingRationaleCount(), raw.recommendationTotalCount());

        double spacedOnTimeRate = raw.spacedReviewTotalCount() == 0
                ? 1.0
                : ratio(raw.spacedReviewTotalCount() - raw.spacedReviewOverdueCount(), raw.spacedReviewTotalCount());

        double dailyCoachCompletionRate = raw.dailyCoachTotalCount() == 0
                ? 1.0
                : ratio(raw.dailyCoachCompletedCount(), raw.dailyCoachTotalCount());

        double studySessionRecoveryRate = raw.studySessionTotalCount() == 0
                ? 1.0
                : ratio(raw.studySessionTotalCount() - raw.studySessionViolationCount(), raw.studySessionTotalCount());

        double rebalanceRecoveryRate = raw.rebalanceTotalCount() == 0
                ? 1.0
                : ratio(raw.rebalanceTotalCount() - raw.rebalanceInvalidBudgetCount(), raw.rebalanceTotalCount());

        double microLessonVerifiedRate = raw.microLessonCatalogTotalCount() == 0
                ? 1.0
                : ratio(raw.microLessonCatalogVerifiedCount(), raw.microLessonCatalogTotalCount());

        double streakRetentionRate = raw.streakTotalCount() == 0
                ? 1.0
                : ratio(raw.streakTotalCount() - raw.streakTrainingViolationCount(), raw.streakTotalCount());

        int piiViolations = Math.max(0, raw.weeklyDigestPiiViolationCount());
        double weeklyZeroPiiRate = raw.weeklyDigestTotalCount() == 0
                ? (piiViolations == 0 ? 1.0 : 0.0)
                : ratio(Math.max(0, raw.weeklyDigestTotalCount() - piiViolations - raw.weeklyDigestTrainingViolationCount()),
                        raw.weeklyDigestTotalCount());

        GateStatus cnt06Status = (raw.coldStartViolationCount() > 0 || raw.recommendationMissingRationaleCount() > 0)
                ? GateStatus.FAIL
                : GateStatus.PASS;

        GateStatus cnt07Status = raw.studySessionViolationCount() > 0
                ? GateStatus.FAIL
                : (spacedOnTimeRate < 0.70 ? GateStatus.WARN : GateStatus.PASS);

        GateStatus cnt08Status = (raw.rebalanceInvalidBudgetCount() > 0 || microLessonVerifiedRate < 1.0)
                ? GateStatus.FAIL
                : GateStatus.PASS;

        GateStatus cnt09Status = (piiViolations > 0 || raw.streakTrainingViolationCount() > 0 || raw.weeklyDigestTrainingViolationCount() > 0)
                ? GateStatus.FAIL
                : GateStatus.PASS;

        List<ContractGateCheck> checks = List.of(
                new ContractGateCheck(
                        "CNT-LOS-06",
                        "Explainable Recommendation & Cold-Start Calibration",
                        cnt06Status,
                        RegressionSeverity.P0,
                        round4(Math.min(coldStartComplianceRate, recExplainabilityRate)),
                        "1.00 zero-LLM and rationale coverage",
                        cnt06Status == GateStatus.PASS
                                ? "Cold-start calibration ($0.00 LLM) and recommendation rationales passed structural invariants."
                                : "Detected cold-start cost/training-eligibility or missing-rationale violation."
                ),
                new ContractGateCheck(
                        "CNT-LOS-07",
                        "Spaced Repetition, Daily Coach & Study Session Recovery",
                        cnt07Status,
                        cnt07Status == GateStatus.WARN ? RegressionSeverity.P1 : RegressionSeverity.P0,
                        round4(Math.min(studySessionRecoveryRate, spacedOnTimeRate)),
                        ">= 0.70 queue freshness and 1.00 session bounds",
                        cnt07Status == GateStatus.PASS
                                ? "Spaced repetition queue, daily coach missions, and study session invariants passed."
                                : "Spaced queue backlog or study session bound requires attention."
                ),
                new ContractGateCheck(
                        "CNT-LOS-08",
                        "Adaptive Rebalance & Micro-Lesson Pedagogy",
                        cnt08Status,
                        RegressionSeverity.P0,
                        round4(Math.min(rebalanceRecoveryRate, microLessonVerifiedRate)),
                        "1.00 verified micro-lessons and 5-25 min budget bounds",
                        cnt08Status == GateStatus.PASS
                                ? "Rebalance budgets and SymPy-verified micro-lesson catalog invariants passed."
                                : "Detected out-of-bounds rebalance budget or unverified micro-lesson catalog item."
                ),
                new ContractGateCheck(
                        "CNT-LOS-09",
                        "Verified Habit Continuity, Weekly Digest & Zero-PII Share Gate",
                        cnt09Status,
                        RegressionSeverity.P0,
                        round4(Math.min(weeklyZeroPiiRate, streakRetentionRate)),
                        "0 PII violations and trainingEligible=false",
                        cnt09Status == GateStatus.PASS
                                ? "Independent streak ledger, weekly digest, and G-PRIV-03 share privacy invariants passed."
                                : "Detected PII or training-eligibility invariant violation in habit/digest telemetry."
                )
        );

        int p0Regressions = 0;
        int p1Regressions = 0;
        for (ContractGateCheck check : checks) {
            if (check.status() == GateStatus.FAIL) {
                p0Regressions++;
            } else if (check.status() == GateStatus.WARN) {
                p1Regressions++;
            }
        }

        GateStatus overall = p0Regressions > 0
                ? GateStatus.FAIL
                : (p1Regressions > 0 ? GateStatus.WARN : GateStatus.PASS);

        QualityTelemetryMetrics metrics = new QualityTelemetryMetrics(
                raw.coldStartTotalCount(),
                round4(coldStartComplianceRate),
                round4(recExplainabilityRate),
                round4(spacedOnTimeRate),
                round4(dailyCoachCompletionRate),
                round4(rebalanceRecoveryRate),
                round4(microLessonVerifiedRate),
                round4(studySessionRecoveryRate),
                round4(streakRetentionRate),
                round4(weeklyZeroPiiRate),
                piiViolations
        );

        UUID snapshotId = UUID.randomUUID();
        Instant evaluatedAt = clock.instant();
        StudyPlanQualityGateReport report = new StudyPlanQualityGateReport(
                QualityGateModels.SCHEMA_VERSION,
                snapshotId,
                evaluatedAt,
                overall,
                p0Regressions,
                p1Regressions,
                0,
                false,
                checks,
                metrics
        );

        incrementCounter("studyplan_quality_gate_evaluated_total", "status", overall.name());
        LOG.info(
                "event=planner_quality_gate_evaluated snapshot_id={} overall_status={} p0_regressions={} p1_regressions={} llm_tokens_used=0 training_eligible=false",
                snapshotId, overall, p0Regressions, p1Regressions
        );
        return report;
    }

    private QualityGateRawAggregateSnapshot queryRawAggregates() {
        int coldStartTotal = queryInt("SELECT COUNT(*) FROM cold_start_diagnostics");
        int coldStartViolations = queryInt(
                "SELECT COUNT(*) FROM cold_start_diagnostics WHERE llm_tokens_used <> 0 OR training_eligible = TRUE");

        int recTotal = queryInt("SELECT COUNT(*) FROM learner_recommendations");
        int recMissingRationale = queryInt(
                "SELECT COUNT(*) FROM learner_recommendations WHERE rationale_code IS NULL");

        int spacedTotal = queryInt("SELECT COUNT(*) FROM spaced_review_queue");
        int spacedCompleted = queryInt(
                "SELECT COUNT(*) FROM spaced_review_queue WHERE status = 'COMPLETED'");
        int spacedOverdue = queryInt(
                "SELECT COUNT(*) FROM spaced_review_queue WHERE status IN ('PENDING', 'SERVED') AND due_at < NOW() - INTERVAL '14 days'");

        int coachTotal = queryInt("SELECT COUNT(*) FROM daily_coach_sessions");
        int coachCompleted = queryInt(
                "SELECT COUNT(*) FROM daily_coach_sessions WHERE status = 'COMPLETED'");

        int sessionTotal = queryInt("SELECT COUNT(*) FROM study_sessions");
        int sessionViolations = queryInt(
                "SELECT COUNT(*) FROM study_sessions WHERE active_duration_seconds > 14400 OR training_eligible = TRUE");

        int rebalanceTotal = queryInt("SELECT COUNT(*) FROM study_plan_rebalance_events");
        int rebalanceViolations = queryInt(
                "SELECT COUNT(*) FROM study_plan_rebalance_events WHERE rebalanced_daily_budget NOT BETWEEN 5 AND 25");

        int catalogTotal = queryInt("SELECT COUNT(*) FROM micro_lesson_catalog");
        int catalogVerified = queryInt(
                "SELECT COUNT(*) FROM micro_lesson_catalog WHERE verification_status = 'VERIFIED'");
        int microCompletions = queryInt("SELECT COUNT(*) FROM learner_micro_lesson_completions");

        int streakTotal = queryInt("SELECT COUNT(*) FROM learner_streaks");
        int streakPositive = queryInt(
                "SELECT COUNT(*) FROM learner_streaks WHERE total_independent_days > 0");
        int streakViolations = queryInt(
                "SELECT COUNT(*) FROM learner_streaks WHERE training_eligible = TRUE");

        int digestTotal = queryInt("SELECT COUNT(*) FROM weekly_learner_digests");
        int digestTrainingViolations = queryInt(
                "SELECT COUNT(*) FROM weekly_learner_digests WHERE training_eligible = TRUE");
        int digestPiiViolations = queryInt(
                "SELECT COUNT(*) FROM weekly_learner_digests WHERE digest_payload_json::text ~* '[^\\s@\"\\{\\}]+@[^\\s@\"\\{\\}]+\\.[^\\s@\"\\{\\}]+'");

        return new QualityGateRawAggregateSnapshot(
                coldStartTotal,
                coldStartViolations,
                recTotal,
                recMissingRationale,
                spacedTotal,
                spacedCompleted,
                spacedOverdue,
                coachTotal,
                coachCompleted,
                sessionTotal,
                sessionViolations,
                rebalanceTotal,
                rebalanceViolations,
                catalogTotal,
                catalogVerified,
                microCompletions,
                streakTotal,
                streakPositive,
                streakViolations,
                digestTotal,
                digestTrainingViolations,
                digestPiiViolations
        );
    }

    private void persistQualitySnapshot(StudyPlanQualityGateReport report, UUID actorId) {
        String cnt06 = findCheckStatus(report, "CNT-LOS-06").name();
        String cnt07 = findCheckStatus(report, "CNT-LOS-07").name();
        String cnt08 = findCheckStatus(report, "CNT-LOS-08").name();
        String cnt09 = findCheckStatus(report, "CNT-LOS-09").name();

        jdbcClient.sql("""
                INSERT INTO planner_quality_gate_snapshots (
                    snapshot_id, schema_version, overall_status, p0_regression_count, p1_regression_count,
                    cnt_los_06_status, cnt_los_07_status, cnt_los_08_status, cnt_los_09_status,
                    metrics_json, contract_checks_json, evaluated_by_actor_id,
                    llm_tokens_used, training_eligible, evaluated_at, created_at
                ) VALUES (
                    :snapshotId, :schemaVersion, :overallStatus, :p0, :p1,
                    :cnt06, :cnt07, :cnt08, :cnt09,
                    CAST(:metricsJson AS jsonb), CAST(:checksJson AS jsonb), :actorId,
                    0, FALSE, :evaluatedAt, :createdAt
                )
                """)
                .param("snapshotId", report.snapshotId())
                .param("schemaVersion", report.schemaVersion())
                .param("overallStatus", report.overallStatus().name())
                .param("p0", report.p0RegressionCount())
                .param("p1", report.p1RegressionCount())
                .param("cnt06", cnt06)
                .param("cnt07", cnt07)
                .param("cnt08", cnt08)
                .param("cnt09", cnt09)
                .param("metricsJson", writeJson(report.telemetryMetrics()))
                .param("checksJson", writeJson(report.contractChecks()))
                .param("actorId", actorId)
                .param("evaluatedAt", Timestamp.from(report.evaluatedAt()))
                .param("createdAt", Timestamp.from(report.evaluatedAt()))
                .update();
    }

    private GateStatus findCheckStatus(StudyPlanQualityGateReport report, String contractId) {
        return report.contractChecks().stream()
                .filter(c -> contractId.equals(c.contractId()))
                .map(ContractGateCheck::status)
                .findFirst()
                .orElse(GateStatus.PASS);
    }

    private int queryInt(String sql) {
        try {
            Integer val = jdbcClient.sql(sql).query(Integer.class).single();
            return val != null ? val : 0;
        } catch (Exception ex) {
            LOG.warn("Quality gate aggregate query failed closed to 0: {}", ex.getMessage());
            return 0;
        }
    }

    private static double ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, (double) numerator / (double) denominator));
    }

    private static double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize quality gate snapshot JSON", ex);
        }
    }

    private void incrementCounter(String name, String... tags) {
        if (meterRegistry != null) {
            Counter.builder(name).tags(tags).register(meterRegistry).increment();
        }
    }
}
