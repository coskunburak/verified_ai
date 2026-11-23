package com.verifiedai.analytics.infrastructure;

import com.verifiedai.analytics.domain.ProductMetricsCalculator.RawMetricsInput;
import com.verifiedai.analytics.domain.ProductMetricsCalculator.RawTierEconomics;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read-only SQL metrics aggregation repository over analytics_events, entitlement_quota_consumptions,
 * entitlement_quota_ledgers, and ai_usage_records (INV-ANL-005, INV-ANL-007).
 */
@Repository
public class ProductMetricsJdbcRepository {

    private static final List<String> ALL_TIERS = List.of("FREE", "PRO", "COACH_PRO", "YKS_SEASON_PASS", "UNKNOWN");
    private static final Map<String, Double> MONTHLY_PRICE_USD_PROXY = Map.of(
        "PRO", 9.99,
        "COACH_PRO", 19.99,
        "YKS_SEASON_PASS", 14.99
    );

    private final JdbcClient jdbcClient;

    public ProductMetricsJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
    }

    public RawMetricsInput loadMetricsInput(LocalDate fromDate, LocalDate toDate, String tierFilter) {
        Instant fromTs = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toTs = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant mrrrBaselineStart = fromTs.minus(14, ChronoUnit.DAYS);
        Instant mrrrFollowupEnd = fromTs.plus(14, ChronoUnit.DAYS).isBefore(toTs)
            ? fromTs.plus(14, ChronoUnit.DAYS)
            : toTs;

        FunnelRow funnel = queryFunnel(fromTs, toTs, tierFilter);
        long secondSessionUsers = querySecondSessionUsers(fromTs, toTs, tierFilter);
        NorthStarRow northStar = queryNorthStar(fromTs, toTs, tierFilter);
        VitrrRow vitrr = queryVitrr(fromTs, toTs, tierFilter);
        DrprRow drpr = queryDrpr(fromTs, toTs, tierFilter);
        MrrrRow mrrr = queryMrrr(mrrrBaselineStart, fromTs, mrrrFollowupEnd, tierFilter);
        TgiRow tgi = queryTgi(fromTs, toTs, tierFilter);
        RetentionRow retention = queryRetention(fromTs, toTs, tierFilter);
        QualityRow quality = queryQuality(fromTs, toTs, tierFilter);
        LatencyRow latency = queryLatencyPercentiles(fromTs, toTs, tierFilter);
        ConversionCountsRow conversionCounts = queryConversionCounts(fromTs, toTs, tierFilter);
        List<RawTierEconomics> tierEconomics = queryTierEconomics(fromTs, toTs, tierFilter);

        return new RawMetricsInput(
            funnel.onboardingStartedUsers(),
            funnel.onboardingCompletedUsers(),
            funnel.firstProblemCapturedUsers(),
            funnel.firstSolutionCompletedUsers(),
            funnel.verificationDetailsViewedUsers(),
            secondSessionUsers,
            funnel.subscribedUsers(),
            northStar.weeklyVerifiedLearningSessions(),
            northStar.activeLearners(),
            northStar.skillsImprovedCount(),
            vitrr.passed(),
            vitrr.total(),
            drpr.drpr48hPassed(),
            drpr.drpr48hTotal(),
            drpr.drpr7dPassed(),
            drpr.drpr7dTotal(),
            mrrr.baselineCount(),
            mrrr.followupCount(),
            tgi.variantAPassed(),
            tgi.variantATotal(),
            tgi.variantBCPassed(),
            tgi.variantBCTotal(),
            retention.d1Retained(),
            retention.d1Cohort(),
            retention.d7Retained(),
            retention.d7Cohort(),
            retention.d30Retained(),
            retention.d30Cohort(),
            retention.repeatMistakeReviewLearners(),
            retention.studyPlanItemsCompleted(),
            quality.parseCorrectedCount(),
            quality.parseCompletedCount(),
            quality.solveFailedCount(),
            quality.solveRequestedCount(),
            latency.p50Ms(),
            latency.p95Ms(),
            latency.sampleCount(),
            conversionCounts.purchaseCompletedUsers(),
            conversionCounts.paywallViewedUsers(),
            tierEconomics
        );
    }

    private FunnelRow queryFunnel(Instant fromTs, Instant toTs, String tier) {
        return jdbcClient.sql(
                """
                SELECT
                    COUNT(DISTINCT CASE WHEN event_name = 'onboarding_started' THEN user_id END)::bigint AS onboarding_started,
                    COUNT(DISTINCT CASE WHEN event_name = 'onboarding_completed' THEN user_id END)::bigint AS onboarding_completed,
                    COUNT(DISTINCT CASE WHEN event_name = 'problem_capture_accepted' THEN user_id END)::bigint AS first_problem_captured,
                    COUNT(DISTINCT CASE WHEN event_name IN ('first_solution_completed', 'solve_completed') THEN user_id END)::bigint AS first_solution_completed,
                    COUNT(DISTINCT CASE WHEN event_name IN ('verification_details_viewed', 'first_verified_solution_viewed') THEN user_id END)::bigint AS verification_viewed,
                    COUNT(DISTINCT CASE WHEN event_name IN ('purchase_completed', 'paywall.trial_started') THEN user_id END)::bigint AS subscribed
                FROM analytics_events
                WHERE occurred_at >= :fromTs AND occurred_at < :toTs
                  AND (:tier = 'ALL' OR entitlement_tier = :tier)
                """
            )
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
            .param("tier", tier, Types.VARCHAR)
            .query((rs, rowNum) -> new FunnelRow(
                rs.getLong("onboarding_started"),
                rs.getLong("onboarding_completed"),
                rs.getLong("first_problem_captured"),
                rs.getLong("first_solution_completed"),
                rs.getLong("verification_viewed"),
                rs.getLong("subscribed")
            ))
            .single();
    }

    private long querySecondSessionUsers(Instant fromTs, Instant toTs, String tier) {
        Long count = jdbcClient.sql(
                """
                SELECT COUNT(*)::bigint FROM (
                    SELECT user_id
                    FROM analytics_events
                    WHERE occurred_at >= :fromTs AND occurred_at < :toTs
                      AND (:tier = 'ALL' OR entitlement_tier = :tier)
                    GROUP BY user_id
                    HAVING COUNT(DISTINCT COALESCE(session_ref_hash, TO_CHAR(occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD'))) >= 2
                ) s
                """
            )
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
            .param("tier", tier, Types.VARCHAR)
            .query(Long.class)
            .single();
        return count == null ? 0L : count;
    }

    private NorthStarRow queryNorthStar(Instant fromTs, Instant toTs, String tier) {
        return jdbcClient.sql(
                """
                SELECT
                    COUNT(DISTINCT CASE
                        WHEN event_name IN (
                            'attempt_submitted', 'twin_drill.completed', 'retrieval_check.completed',
                            'practice_session_completed', 'first_solution_completed', 'solve_completed',
                            'study_plan_item_completed', 'daily_coach.item_completed'
                        )
                        THEN user_id::text || ':' || COALESCE(session_ref_hash, TO_CHAR(occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD'))
                    END)::bigint AS weekly_verified_sessions,
                    COUNT(DISTINCT user_id)::bigint AS active_learners,
                    COUNT(DISTINCT CASE
                        WHEN event_name = 'mastery_band_changed'
                             AND COALESCE(properties_json->>'fromBand', '') IS DISTINCT FROM COALESCE(properties_json->>'toBand', '')
                        THEN user_id::text || ':' || COALESCE(properties_json->>'canonicalSkillCode', id::text)
                    END)::bigint AS skills_improved
                FROM analytics_events
                WHERE occurred_at >= :fromTs AND occurred_at < :toTs
                  AND (:tier = 'ALL' OR entitlement_tier = :tier)
                """
            )
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
            .param("tier", tier, Types.VARCHAR)
            .query((rs, rowNum) -> new NorthStarRow(
                rs.getLong("weekly_verified_sessions"),
                rs.getLong("active_learners"),
                rs.getLong("skills_improved")
            ))
            .single();
    }

    private VitrrRow queryVitrr(Instant fromTs, Instant toTs, String tier) {
        return jdbcClient.sql(
                """
                SELECT
                    COUNT(*) FILTER (WHERE EXISTS (
                        SELECT 1
                        FROM analytics_events t
                        WHERE t.user_id = m.user_id
                          AND t.event_name = 'twin_drill.completed'
                          AND t.occurred_at >= m.occurred_at
                          AND t.occurred_at <= m.occurred_at + INTERVAL '24 hours'
                          AND COALESCE(t.properties_json->>'variant', 'A') = 'A'
                          AND COALESCE(t.properties_json->>'evidenceClass', '') = 'INDEPENDENT'
                          AND LOWER(COALESCE(t.properties_json->>'passed', 'false')) = 'true'
                    ))::bigint AS vitrr_passed,
                    COUNT(*)::bigint AS vitrr_total
                FROM analytics_events m
                WHERE m.event_name = 'mistake_detected'
                  AND m.occurred_at >= :fromTs AND m.occurred_at < :toTs
                  AND (:tier = 'ALL' OR m.entitlement_tier = :tier)
                """
            )
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
            .param("tier", tier, Types.VARCHAR)
            .query((rs, rowNum) -> new VitrrRow(rs.getLong("vitrr_passed"), rs.getLong("vitrr_total")))
            .single();
    }

    private DrprRow queryDrpr(Instant fromTs, Instant toTs, String tier) {
        return jdbcClient.sql(
                """
                SELECT
                    COUNT(*) FILTER (
                        WHERE event_name = 'retrieval_check.completed'
                          AND properties_json->>'stage' = '48H'
                          AND LOWER(COALESCE(properties_json->>'passedIndependent', 'false')) = 'true'
                    )::bigint AS drpr_48h_passed,
                    COUNT(*) FILTER (
                        WHERE event_name = 'retrieval_check.completed'
                          AND properties_json->>'stage' = '48H'
                    )::bigint AS drpr_48h_total,
                    COUNT(*) FILTER (
                        WHERE event_name = 'retrieval_check.completed'
                          AND properties_json->>'stage' = '7D'
                          AND LOWER(COALESCE(properties_json->>'passedIndependent', 'false')) = 'true'
                    )::bigint AS drpr_7d_passed,
                    COUNT(*) FILTER (
                        WHERE event_name = 'retrieval_check.completed'
                          AND properties_json->>'stage' = '7D'
                    )::bigint AS drpr_7d_total
                FROM analytics_events
                WHERE occurred_at >= :fromTs AND occurred_at < :toTs
                  AND (:tier = 'ALL' OR entitlement_tier = :tier)
                """
            )
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
            .param("tier", tier, Types.VARCHAR)
            .query((rs, rowNum) -> new DrprRow(
                rs.getLong("drpr_48h_passed"),
                rs.getLong("drpr_48h_total"),
                rs.getLong("drpr_7d_passed"),
                rs.getLong("drpr_7d_total")
            ))
            .single();
    }

    private MrrrRow queryMrrr(Instant baselineStart, Instant fromTs, Instant followupEnd, String tier) {
        return jdbcClient.sql(
                """
                SELECT
                    COUNT(*) FILTER (WHERE occurred_at >= :baselineStart AND occurred_at < :fromTs)::bigint AS baseline_count,
                    COUNT(*) FILTER (WHERE occurred_at >= :fromTs AND occurred_at < :followupEnd)::bigint AS followup_count
                FROM analytics_events
                WHERE event_name = 'mistake_detected'
                  AND occurred_at >= :baselineStart AND occurred_at < :followupEnd
                  AND (:tier = 'ALL' OR entitlement_tier = :tier)
                """
            )
            .param("baselineStart", Timestamp.from(baselineStart), Types.TIMESTAMP)
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("followupEnd", Timestamp.from(followupEnd), Types.TIMESTAMP)
            .param("tier", tier, Types.VARCHAR)
            .query((rs, rowNum) -> new MrrrRow(rs.getLong("baseline_count"), rs.getLong("followup_count")))
            .single();
    }

    private TgiRow queryTgi(Instant fromTs, Instant toTs, String tier) {
        return jdbcClient.sql(
                """
                SELECT
                    COUNT(*) FILTER (
                        WHERE properties_json->>'variant' = 'A'
                          AND LOWER(COALESCE(properties_json->>'passed', 'false')) = 'true'
                    )::bigint AS var_a_passed,
                    COUNT(*) FILTER (
                        WHERE properties_json->>'variant' = 'A'
                    )::bigint AS var_a_total,
                    COUNT(*) FILTER (
                        WHERE properties_json->>'variant' IN ('B', 'C')
                          AND LOWER(COALESCE(properties_json->>'passed', 'false')) = 'true'
                    )::bigint AS var_bc_passed,
                    COUNT(*) FILTER (
                        WHERE properties_json->>'variant' IN ('B', 'C')
                    )::bigint AS var_bc_total
                FROM analytics_events
                WHERE event_name = 'twin_drill.completed'
                  AND COALESCE(properties_json->>'evidenceClass', 'INDEPENDENT') = 'INDEPENDENT'
                  AND occurred_at >= :fromTs AND occurred_at < :toTs
                  AND (:tier = 'ALL' OR entitlement_tier = :tier)
                """
            )
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
            .param("tier", tier, Types.VARCHAR)
            .query((rs, rowNum) -> new TgiRow(
                rs.getLong("var_a_passed"),
                rs.getLong("var_a_total"),
                rs.getLong("var_bc_passed"),
                rs.getLong("var_bc_total")
            ))
            .single();
    }

    private RetentionRow queryRetention(Instant fromTs, Instant toTs, String tier) {
        RetentionCohortRow cohorts = jdbcClient.sql(
                """
                WITH first_seen AS (
                    SELECT user_id, MIN(occurred_at) AS first_at
                    FROM analytics_events
                    WHERE (:tier = 'ALL' OR entitlement_tier = :tier)
                    GROUP BY user_id
                )
                SELECT
                    COUNT(*) FILTER (WHERE first_at >= :fromTs AND first_at < :toTs AND first_at + INTERVAL '1 day' < :toTs)::bigint AS d1_cohort,
                    COUNT(*) FILTER (WHERE first_at >= :fromTs AND first_at < :toTs AND first_at + INTERVAL '1 day' < :toTs
                        AND EXISTS (
                            SELECT 1 FROM analytics_events a
                            WHERE a.user_id = f.user_id
                              AND a.occurred_at >= f.first_at + INTERVAL '1 day'
                              AND a.occurred_at < f.first_at + INTERVAL '2 days'
                        ))::bigint AS d1_retained,
                    COUNT(*) FILTER (WHERE first_at >= :fromTs AND first_at < :toTs AND first_at + INTERVAL '7 days' < :toTs)::bigint AS d7_cohort,
                    COUNT(*) FILTER (WHERE first_at >= :fromTs AND first_at < :toTs AND first_at + INTERVAL '7 days' < :toTs
                        AND EXISTS (
                            SELECT 1 FROM analytics_events a
                            WHERE a.user_id = f.user_id
                              AND a.occurred_at >= f.first_at + INTERVAL '7 days'
                              AND a.occurred_at < f.first_at + INTERVAL '8 days'
                        ))::bigint AS d7_retained,
                    COUNT(*) FILTER (WHERE first_at >= :fromTs AND first_at < :toTs AND first_at + INTERVAL '30 days' < :toTs)::bigint AS d30_cohort,
                    COUNT(*) FILTER (WHERE first_at >= :fromTs AND first_at < :toTs AND first_at + INTERVAL '30 days' < :toTs
                        AND EXISTS (
                            SELECT 1 FROM analytics_events a
                            WHERE a.user_id = f.user_id
                              AND a.occurred_at >= f.first_at + INTERVAL '30 days'
                              AND a.occurred_at < f.first_at + INTERVAL '31 days'
                        ))::bigint AS d30_retained
                FROM first_seen f
                """
            )
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
            .param("tier", tier, Types.VARCHAR)
            .query((rs, rowNum) -> new RetentionCohortRow(
                rs.getLong("d1_retained"),
                rs.getLong("d1_cohort"),
                rs.getLong("d7_retained"),
                rs.getLong("d7_cohort"),
                rs.getLong("d30_retained"),
                rs.getLong("d30_cohort")
            ))
            .single();

        Long repeatReviewLearners = jdbcClient.sql(
                """
                SELECT COUNT(*)::bigint FROM (
                    SELECT user_id
                    FROM analytics_events
                    WHERE event_name = 'mistake_reviewed'
                      AND occurred_at >= :fromTs AND occurred_at < :toTs
                      AND (:tier = 'ALL' OR entitlement_tier = :tier)
                    GROUP BY user_id
                    HAVING COUNT(*) >= 2
                ) r
                """
            )
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
            .param("tier", tier, Types.VARCHAR)
            .query(Long.class)
            .single();

        Long studyPlanCompleted = jdbcClient.sql(
                """
                SELECT COUNT(*)::bigint
                FROM analytics_events
                WHERE event_name IN ('study_plan_item_completed', 'daily_coach.item_completed')
                  AND occurred_at >= :fromTs AND occurred_at < :toTs
                  AND (:tier = 'ALL' OR entitlement_tier = :tier)
                """
            )
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
            .param("tier", tier, Types.VARCHAR)
            .query(Long.class)
            .single();

        return new RetentionRow(
            cohorts.d1Retained(),
            cohorts.d1Cohort(),
            cohorts.d7Retained(),
            cohorts.d7Cohort(),
            cohorts.d30Retained(),
            cohorts.d30Cohort(),
            repeatReviewLearners == null ? 0L : repeatReviewLearners,
            studyPlanCompleted == null ? 0L : studyPlanCompleted
        );
    }

    private QualityRow queryQuality(Instant fromTs, Instant toTs, String tier) {
        return jdbcClient.sql(
                """
                SELECT
                    COUNT(*) FILTER (WHERE event_name = 'parse_corrected')::bigint AS parse_corrected,
                    COUNT(*) FILTER (WHERE event_name IN ('problem_parse_completed', 'problem_parse_review_required'))::bigint AS parse_completed,
                    COUNT(*) FILTER (WHERE event_name = 'solve_failed')::bigint AS solve_failed,
                    COUNT(*) FILTER (WHERE event_name = 'solve_requested')::bigint AS solve_requested
                FROM analytics_events
                WHERE occurred_at >= :fromTs AND occurred_at < :toTs
                  AND (:tier = 'ALL' OR entitlement_tier = :tier)
                """
            )
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
            .param("tier", tier, Types.VARCHAR)
            .query((rs, rowNum) -> new QualityRow(
                rs.getLong("parse_corrected"),
                rs.getLong("parse_completed"),
                rs.getLong("solve_failed"),
                rs.getLong("solve_requested")
            ))
            .single();
    }

    private LatencyRow queryLatencyPercentiles(Instant fromTs, Instant toTs, String tier) {
        return jdbcClient.sql(
                """
                SELECT
                    ROUND(CAST(percentile_cont(0.50) WITHIN GROUP (ORDER BY latency_ms) AS numeric))::integer AS p50_ms,
                    ROUND(CAST(percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) AS numeric))::integer AS p95_ms,
                    COUNT(*)::bigint AS sample_count
                FROM analytics_events
                WHERE event_name IN ('solve_completed', 'first_solution_completed')
                  AND latency_ms IS NOT NULL
                  AND occurred_at >= :fromTs AND occurred_at < :toTs
                  AND (:tier = 'ALL' OR entitlement_tier = :tier)
                """
            )
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
            .param("tier", tier, Types.VARCHAR)
            .query((rs, rowNum) -> new LatencyRow(
                rs.getObject("p50_ms", Integer.class),
                rs.getObject("p95_ms", Integer.class),
                rs.getLong("sample_count")
            ))
            .single();
    }

    private ConversionCountsRow queryConversionCounts(Instant fromTs, Instant toTs, String tier) {
        return jdbcClient.sql(
                """
                SELECT
                    COUNT(DISTINCT CASE WHEN event_name = 'purchase_completed' THEN user_id END)::bigint AS purchased_users,
                    COUNT(DISTINCT CASE WHEN event_name IN ('paywall_viewed', 'paywall.mistake_moment_shown', 'paywall.twin_success_shown') THEN user_id END)::bigint AS paywall_users
                FROM analytics_events
                WHERE occurred_at >= :fromTs AND occurred_at < :toTs
                  AND (:tier = 'ALL' OR entitlement_tier = :tier)
                """
            )
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
            .param("tier", tier, Types.VARCHAR)
            .query((rs, rowNum) -> new ConversionCountsRow(
                rs.getLong("purchased_users"),
                rs.getLong("paywall_users")
            ))
            .single();
    }

    private List<RawTierEconomics> queryTierEconomics(Instant fromTs, Instant toTs, String tierFilter) {
        List<String> tiers = "ALL".equals(tierFilter) ? ALL_TIERS : List.of(tierFilter);
        List<RawTierEconomics> result = new ArrayList<>(tiers.size());
        for (String tier : tiers) {
            TierEventStats stats = jdbcClient.sql(
                    """
                    SELECT
                        COUNT(DISTINCT user_id)::bigint AS active_learners,
                        COUNT(*) FILTER (WHERE event_name IN ('solve_completed', 'first_solution_completed'))::bigint AS completed_solves,
                        COUNT(DISTINCT CASE
                            WHEN event_name IN (
                                'attempt_submitted', 'twin_drill.completed', 'retrieval_check.completed',
                                'practice_session_completed', 'first_solution_completed', 'solve_completed',
                                'study_plan_item_completed', 'daily_coach.item_completed'
                            )
                            THEN user_id::text || ':' || COALESCE(session_ref_hash, TO_CHAR(occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD'))
                        END)::bigint AS verified_sessions,
                        COALESCE(SUM(ai_cost_micros), 0)::bigint AS event_cost_micros
                    FROM analytics_events
                    WHERE occurred_at >= :fromTs AND occurred_at < :toTs
                      AND entitlement_tier = :tier
                    """
                )
                .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
                .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
                .param("tier", tier, Types.VARCHAR)
                .query((rs, rowNum) -> new TierEventStats(
                    rs.getLong("active_learners"),
                    rs.getLong("completed_solves"),
                    rs.getLong("verified_sessions"),
                    rs.getLong("event_cost_micros")
                ))
                .single();

            Long quotaCostMicros = jdbcClient.sql(
                    """
                    SELECT COALESCE(SUM(c.cost_micros), 0)::bigint
                    FROM entitlement_quota_consumptions c
                    JOIN entitlement_quota_ledgers l ON c.ledger_id = l.id
                    WHERE l.tier_code = :tier
                      AND c.status IN ('RESERVED', 'COMMITTED')
                      AND c.created_at >= :fromTs AND c.created_at < :toTs
                    """
                )
                .param("tier", tier, Types.VARCHAR)
                .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
                .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
                .query(Long.class)
                .single();

            Long usageCostMicros = jdbcClient.sql(
                    """
                    SELECT COALESCE(SUM(r.estimated_cost_micros), 0)::bigint
                    FROM ai_usage_records r
                    LEFT JOIN LATERAL (
                        SELECT e.tier
                        FROM entitlements e
                        WHERE e.user_id = r.user_id
                        ORDER BY e.updated_at DESC
                        LIMIT 1
                    ) e ON TRUE
                    WHERE COALESCE(e.tier, 'FREE') = :tier
                      AND r.created_at >= :fromTs AND r.created_at < :toTs
                    """
                )
                .param("tier", tier, Types.VARCHAR)
                .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
                .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
                .query(Long.class)
                .single();

            long effectiveCostMicros = Math.max(
                stats.eventCostMicros(),
                Math.max(
                    quotaCostMicros == null ? 0L : quotaCostMicros,
                    usageCostMicros == null ? 0L : usageCostMicros
                )
            );
            result.add(new RawTierEconomics(
                tier,
                stats.activeLearners(),
                stats.completedSolves(),
                stats.verifiedSessions(),
                effectiveCostMicros,
                MONTHLY_PRICE_USD_PROXY.get(tier)
            ));
        }
        return result;
    }

    private record FunnelRow(
        long onboardingStartedUsers,
        long onboardingCompletedUsers,
        long firstProblemCapturedUsers,
        long firstSolutionCompletedUsers,
        long verificationDetailsViewedUsers,
        long subscribedUsers
    ) {
    }

    private record NorthStarRow(
        long weeklyVerifiedLearningSessions,
        long activeLearners,
        long skillsImprovedCount
    ) {
    }

    private record VitrrRow(long passed, long total) {
    }

    private record DrprRow(
        long drpr48hPassed,
        long drpr48hTotal,
        long drpr7dPassed,
        long drpr7dTotal
    ) {
    }

    private record MrrrRow(long baselineCount, long followupCount) {
    }

    private record TgiRow(
        long variantAPassed,
        long variantATotal,
        long variantBCPassed,
        long variantBCTotal
    ) {
    }

    private record RetentionCohortRow(
        long d1Retained,
        long d1Cohort,
        long d7Retained,
        long d7Cohort,
        long d30Retained,
        long d30Cohort
    ) {
    }

    private record RetentionRow(
        long d1Retained,
        long d1Cohort,
        long d7Retained,
        long d7Cohort,
        long d30Retained,
        long d30Cohort,
        long repeatMistakeReviewLearners,
        long studyPlanItemsCompleted
    ) {
    }

    private record QualityRow(
        long parseCorrectedCount,
        long parseCompletedCount,
        long solveFailedCount,
        long solveRequestedCount
    ) {
    }

    private record LatencyRow(Integer p50Ms, Integer p95Ms, long sampleCount) {
    }

    private record ConversionCountsRow(long purchaseCompletedUsers, long paywallViewedUsers) {
    }

    private record TierEventStats(
        long activeLearners,
        long completedSolves,
        long verifiedSessions,
        long eventCostMicros
    ) {
    }
}
