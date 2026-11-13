package com.verifiedai.studyplan.gamification;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.studyplan.privacy.GamificationPrivacyContributor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full Testcontainers PostgreSQL 16 + Flyway V051 + Spring Security MockMvc integration test suite
 * executed in host Docker runs (`make test-api`).
 */
class GamificationPostgresIntegrationTest extends PostgresIntegrationTestSupport {

    private static final UUID USER_A = UUID.fromString("00000000-0000-4000-8000-000000000880");
    private static final UUID USER_B = UUID.fromString("00000000-0000-4000-8000-00000000088f");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcGamificationRepository repository;

    @Autowired
    private GamificationPrivacyContributor privacyContributor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        jdbcTemplate.update("DELETE FROM learner_daily_streak_ledger WHERE user_id IN (?, ?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM learner_achievements WHERE user_id IN (?, ?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM learner_streaks WHERE user_id IN (?, ?)", USER_A, USER_B);

        ensureUserExists(USER_A);
        ensureUserExists(USER_B);
    }

    @Test
    void postIndependentAndAssistedActivityEndToEndWithPostgreSqlPersistence() throws Exception {
        String assistedPayload = """
                {
                  "idempotencyKey": "pg-idem-assisted-01",
                  "evidenceType": "HINT_ASSISTED",
                  "evidenceReference": "attempt:hint:pg01",
                  "learnerTimezone": "Europe/Istanbul"
                }
                """;
        mockMvc.perform(post("/v1/gamification/activity")
                        .with(authentication(authFor(USER_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assistedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_A.toString()))
                .andExpect(jsonPath("$.currentStreakDays").value(0))
                .andExpect(jsonPath("$.streakStatus").value("CONTINUITY_PRESERVED_TODAY"))
                .andExpect(jsonPath("$.continuityPreservedToday").value(true))
                .andExpect(jsonPath("$.verifiedToday").value(false))
                .andExpect(jsonPath("$.trainingEligible").value(false))
                .andExpect(jsonPath("$.achievements", hasSize(6)));

        String independentPayload = """
                {
                  "idempotencyKey": "pg-idem-indep-02",
                  "evidenceType": "INDEPENDENT",
                  "evidenceReference": "twin_attempt:00000000-0000-4000-8000-000000000882",
                  "learnerTimezone": "Europe/Istanbul",
                  "misconceptionRepaired": true
                }
                """;
        mockMvc.perform(post("/v1/gamification/activity")
                        .with(authentication(authFor(USER_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(independentPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreakDays").value(1))
                .andExpect(jsonPath("$.longestStreakDays").value(1))
                .andExpect(jsonPath("$.streakStatus").value("ACTIVE_VERIFIED_TODAY"))
                .andExpect(jsonPath("$.verifiedToday").value(true))
                .andExpect(jsonPath("$.streakIncrementedOnEvaluation").value(true))
                .andExpect(jsonPath("$.trainingEligible").value(false))
                .andExpect(jsonPath("$.achievements", hasSize(6)));

        assertEquals(1L, repository.countStreakByUserId(USER_A));
        assertEquals(1L, repository.countLedgerByUserId(USER_A));
        assertEquals(2L, repository.countAchievementsByUserId(USER_A));

        String revealedPayload = """
                {
                  "idempotencyKey": "pg-idem-reveal-03",
                  "evidenceType": "SOLUTION_REVEALED_ASSISTED",
                  "evidenceReference": "attempt:rev:pg03",
                  "learnerTimezone": "Europe/Istanbul"
                }
                """;
        mockMvc.perform(post("/api/v1/gamification/activity")
                        .with(authentication(authFor(USER_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(revealedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreakDays").value(1))
                .andExpect(jsonPath("$.streakStatus").value("ACTIVE_VERIFIED_TODAY"));
    }

    @Test
    void getSummaryEvaluatesTimezoneAndAppliesAutomaticFreezeAndReturnsSixAchievements() throws Exception {
        ZoneId istanbul = ZoneId.of("Europe/Istanbul");
        LocalDate todayIstanbul = LocalDate.now(istanbul);
        LocalDate twoDaysAgo = todayIstanbul.minusDays(2);
        Instant now = Instant.now();

        repository.upsertStreak(new GamificationModels.LearnerStreakRecord(
                USER_A,
                4,
                6,
                2,
                0,
                YearMonth.from(twoDaysAgo).toString(),
                "Europe/Istanbul",
                twoDaysAgo,
                twoDaysAgo,
                GamificationEvidenceType.INDEPENDENT,
                StreakStatus.ACTIVE_VERIFIED_TODAY,
                4,
                0,
                false,
                now.minusSeconds(172_800),
                now.minusSeconds(172_800)
        ));

        mockMvc.perform(get("/v1/gamification/summary")
                        .param("timezone", "Europe/Istanbul")
                        .with(authentication(authFor(USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learnerTimezone").value("Europe/Istanbul"))
                .andExpect(jsonPath("$.serverLocalDate").value(todayIstanbul.toString()))
                .andExpect(jsonPath("$.currentStreakDays").value(4))
                .andExpect(jsonPath("$.streakFreezesRemaining").value(1))
                .andExpect(jsonPath("$.freezesUsedThisMonth").value(1))
                .andExpect(jsonPath("$.freezeAppliedOnEvaluation").value(true))
                .andExpect(jsonPath("$.streakStatus").value("FREEZE_PROTECTED"))
                .andExpect(jsonPath("$.achievements", hasSize(6)))
                .andExpect(jsonPath("$.trainingEligible").value(false));
    }

    @Test
    void unauthenticatedAndCrossLearnerBolaRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/v1/gamification/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));

        mockMvc.perform(get("/v1/gamification/summary")
                        .param("userId", USER_B.toString())
                        .with(authentication(authFor(USER_A))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_FORBIDDEN"));
    }

    @Test
    void invalidTimezoneAndFutureTimestampReturnCanonicalProblemDetails() throws Exception {
        mockMvc.perform(get("/v1/gamification/summary")
                        .param("timezone", "Invalid/Zone")
                        .with(authentication(authFor(USER_A))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GAMIFICATION_TIMEZONE_INVALID"));

        String futureTimestamp = Instant.now().plusSeconds(3600).toString();
        String futurePayload = """
                {
                  "idempotencyKey": "pg-future-window-01",
                  "evidenceType": "INDEPENDENT",
                  "evidenceReference": "twin_attempt:future",
                  "learnerTimezone": "UTC",
                  "occurredAtUtc": "%s"
                }
                """.formatted(futureTimestamp);

        mockMvc.perform(post("/v1/gamification/activity")
                        .with(authentication(authFor(USER_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(futurePayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GAMIFICATION_ACTIVITY_WINDOW_INVALID"));
    }

    @Test
    void unsupportedEvidenceTypeReturnsUnprocessableEntityProblemDetails() throws Exception {
        String unsupportedPayload = """
                {
                  "idempotencyKey": "pg-unsupported-ev-01",
                  "evidenceType": "UNSUPPORTED",
                  "evidenceReference": "twin_attempt:unsupported",
                  "learnerTimezone": "UTC"
                }
                """;
        mockMvc.perform(post("/v1/gamification/activity")
                        .with(authentication(authFor(USER_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unsupportedPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GAMIFICATION_EVIDENCE_UNSUPPORTED"));
    }

    @Test
    void featureFlagKillSwitchReturnsServiceUnavailable503() throws Exception {
        GamificationPolicyService disabledService = new GamificationPolicyService(
                repository,
                new SimpleMeterRegistry(),
                Clock.systemUTC(),
                false
        );
        MockMvc disabledMockMvc = MockMvcBuilders.standaloneSetup(new GamificationController(disabledService))
                .build();

        disabledMockMvc.perform(get("/v1/gamification/summary")
                        .principal(authFor(USER_A)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GAMIFICATION_FEATURE_DISABLED"))
                .andExpect(jsonPath("$.details.retryable").value(true));
    }

    @Test
    void gamificationPrivacyContributorExportsAndPurgesAllRowsUnder500Ms() throws Exception {
        String payload = """
                {
                  "idempotencyKey": "pg-privacy-export-a",
                  "evidenceType": "INDEPENDENT",
                  "evidenceReference": "twin_attempt:privacy-a",
                  "learnerTimezone": "UTC"
                }
                """;
        mockMvc.perform(post("/v1/gamification/activity")
                        .with(authentication(authFor(USER_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/gamification/activity")
                        .with(authentication(authFor(USER_B)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("pg-privacy-export-a", "pg-privacy-export-b")))
                .andExpect(status().isOk());

        Map<String, Object> exported = privacyContributor.exportUserData(USER_A);
        assertEquals("studyplan.gamification", exported.get("contributor"));
        assertEquals(false, exported.get("trainingEligible"));
        assertFalse(((List<?>) exported.get("unlockedBadgeCodes")).isEmpty());

        long startedNs = System.nanoTime();
        privacyContributor.deleteUserData(USER_A, Instant.now());
        long elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L;

        assertTrue(elapsedMs < 500L, "Expected privacy purge < 500ms, took " + elapsedMs + "ms");
        assertEquals(0L, repository.countStreakByUserId(USER_A));
        assertEquals(0L, repository.countLedgerByUserId(USER_A));
        assertEquals(0L, repository.countAchievementsByUserId(USER_A));

        assertEquals(1L, repository.countStreakByUserId(USER_B));
        assertEquals(1L, repository.countLedgerByUserId(USER_B));
        assertEquals(1L, repository.countAchievementsByUserId(USER_B));
    }

    private void ensureUserExists(UUID userId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                INSERT INTO users (id, status, created_at, updated_at)
                VALUES (?, 'ACTIVE', ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                userId,
                now,
                now
        );
    }

    private static Authentication authFor(UUID userId) {
        VerifiedJwtPrincipal principal = new VerifiedJwtPrincipal(userId, userId);
        return new UsernamePasswordAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_LEARNER"))
        );
    }
}
