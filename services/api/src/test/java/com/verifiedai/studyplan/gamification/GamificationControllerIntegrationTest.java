package com.verifiedai.studyplan.gamification;

import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiExceptionHandler;
import com.verifiedai.studyplan.gamification.GamificationModels.DailyStreakLedgerRecord;
import com.verifiedai.studyplan.gamification.GamificationModels.LearnerAchievementRecord;
import com.verifiedai.studyplan.gamification.GamificationModels.LearnerStreakRecord;
import com.verifiedai.studyplan.privacy.GamificationPrivacyContributor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Docker-free MockMvc controller integration suite matching Sprint 8.7's controller test harness.
 * Full PostgreSQL + Flyway V051 persistence is verified in {@link GamificationPostgresIntegrationTest}.
 */
class GamificationControllerIntegrationTest {

    private static final UUID USER_A = UUID.fromString("00000000-0000-4000-8000-000000000880");
    private static final UUID USER_B = UUID.fromString("00000000-0000-4000-8000-00000000088f");

    private InMemoryRepository repository;
    private MutableClock clock;
    private GamificationPolicyService service;
    private GamificationPrivacyContributor privacyContributor;
    private MockMvc mockMvc;
    private MockMvc disabledMockMvc;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        clock = new MutableClock(Instant.parse("2026-11-10T17:15:00Z"));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        service = new GamificationPolicyService(repository, meterRegistry, clock, true);
        privacyContributor = new GamificationPrivacyContributor(repository);

        mockMvc = MockMvcBuilders.standaloneSetup(new GamificationController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        GamificationPolicyService disabledService = new GamificationPolicyService(repository, meterRegistry, clock, false);
        disabledMockMvc = MockMvcBuilders.standaloneSetup(new GamificationController(disabledService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void postIndependentAndAssistedActivityEndToEnd() throws Exception {
        String assistedPayload = """
                {
                  "idempotencyKey": "mvc-idem-assisted-01",
                  "evidenceType": "HINT_ASSISTED",
                  "evidenceReference": "attempt:hint:mvc01",
                  "learnerTimezone": "Europe/Istanbul"
                }
                """;
        mockMvc.perform(post("/v1/gamification/activity")
                        .principal(authFor(USER_A))
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
                  "idempotencyKey": "mvc-idem-indep-02",
                  "evidenceType": "INDEPENDENT",
                  "evidenceReference": "twin_attempt:00000000-0000-4000-8000-000000000882",
                  "learnerTimezone": "Europe/Istanbul",
                  "misconceptionRepaired": true
                }
                """;
        mockMvc.perform(post("/v1/gamification/activity")
                        .principal(authFor(USER_A))
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
                  "idempotencyKey": "mvc-idem-reveal-03",
                  "evidenceType": "SOLUTION_REVEALED_ASSISTED",
                  "evidenceReference": "attempt:rev:mvc03",
                  "learnerTimezone": "Europe/Istanbul"
                }
                """;
        mockMvc.perform(post("/api/v1/gamification/activity")
                        .principal(authFor(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(revealedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreakDays").value(1))
                .andExpect(jsonPath("$.streakStatus").value("ACTIVE_VERIFIED_TODAY"));
    }

    @Test
    void getSummaryEvaluatesTimezoneAndAppliesAutomaticFreezeAndReturnsSixAchievements() throws Exception {
        ZoneId istanbul = ZoneId.of("Europe/Istanbul");
        LocalDate todayIstanbul = LocalDate.ofInstant(clock.instant(), istanbul);
        LocalDate twoDaysAgo = todayIstanbul.minusDays(2);

        repository.upsertStreak(new LearnerStreakRecord(
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
                clock.instant().minusSeconds(172_800),
                clock.instant().minusSeconds(172_800)
        ));

        mockMvc.perform(get("/v1/gamification/summary")
                        .param("timezone", "Europe/Istanbul")
                        .principal(authFor(USER_A)))
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
                        .principal(authFor(USER_A)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_FORBIDDEN"));
    }

    @Test
    void invalidTimezoneAndFutureTimestampReturnCanonicalProblemDetails() throws Exception {
        mockMvc.perform(get("/v1/gamification/summary")
                        .param("timezone", "Invalid/Zone")
                        .principal(authFor(USER_A)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GAMIFICATION_TIMEZONE_INVALID"));

        String futureTimestamp = clock.instant().plusSeconds(3600).toString();
        String futurePayload = """
                {
                  "idempotencyKey": "mvc-future-window-01",
                  "evidenceType": "INDEPENDENT",
                  "evidenceReference": "twin_attempt:future",
                  "learnerTimezone": "UTC",
                  "occurredAtUtc": "%s"
                }
                """.formatted(futureTimestamp);

        mockMvc.perform(post("/v1/gamification/activity")
                        .principal(authFor(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(futurePayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GAMIFICATION_ACTIVITY_WINDOW_INVALID"));
    }

    @Test
    void unsupportedEvidenceTypeReturnsUnprocessableEntityProblemDetails() throws Exception {
        String unsupportedPayload = """
                {
                  "idempotencyKey": "mvc-unsupported-ev-01",
                  "evidenceType": "UNSUPPORTED",
                  "evidenceReference": "twin_attempt:unsupported",
                  "learnerTimezone": "UTC"
                }
                """;
        mockMvc.perform(post("/v1/gamification/activity")
                        .principal(authFor(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unsupportedPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GAMIFICATION_EVIDENCE_UNSUPPORTED"));
    }

    @Test
    void featureFlagKillSwitchReturnsServiceUnavailable503() throws Exception {
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
                  "idempotencyKey": "mvc-privacy-export-a",
                  "evidenceType": "INDEPENDENT",
                  "evidenceReference": "twin_attempt:privacy-a",
                  "learnerTimezone": "UTC"
                }
                """;
        mockMvc.perform(post("/v1/gamification/activity")
                        .principal(authFor(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/gamification/activity")
                        .principal(authFor(USER_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("mvc-privacy-export-a", "mvc-privacy-export-b")))
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

    private static Authentication authFor(UUID userId) {
        VerifiedJwtPrincipal principal = new VerifiedJwtPrincipal(userId, userId);
        return new UsernamePasswordAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_LEARNER"))
        );
    }

    private static final class MutableClock extends Clock {
        private final Instant current;

        MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    private static final class InMemoryRepository extends JdbcGamificationRepository {
        private final Map<UUID, LearnerStreakRecord> streaks = new HashMap<>();
        private final Map<String, DailyStreakLedgerRecord> ledgers = new HashMap<>();
        private final Map<String, LearnerAchievementRecord> achievements = new HashMap<>();

        InMemoryRepository() {
            super(mock(NamedParameterJdbcTemplate.class));
        }

        @Override
        public Optional<LearnerStreakRecord> findStreakByUserIdForUpdate(UUID userId) {
            return Optional.ofNullable(streaks.get(userId));
        }

        @Override
        public Optional<LearnerStreakRecord> findStreakByUserId(UUID userId) {
            return Optional.ofNullable(streaks.get(userId));
        }

        @Override
        public void upsertStreak(LearnerStreakRecord record) {
            streaks.put(record.userId(), record);
        }

        @Override
        public Optional<DailyStreakLedgerRecord> findDailyLedgerByUserAndDate(UUID userId, LocalDate date) {
            return Optional.ofNullable(ledgers.get(userId + ":" + date));
        }

        @Override
        public List<DailyStreakLedgerRecord> findRecentDailyLedger(UUID userId, int limit) {
            return ledgers.values().stream()
                    .filter(entry -> entry.userId().equals(userId))
                    .sorted(Comparator.comparing(DailyStreakLedgerRecord::activityLocalDate).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public void upsertDailyLedger(DailyStreakLedgerRecord entry) {
            ledgers.put(entry.userId() + ":" + entry.activityLocalDate(), entry);
        }

        @Override
        public List<LearnerAchievementRecord> findAchievementsByUserId(UUID userId) {
            List<LearnerAchievementRecord> list = new ArrayList<>();
            for (LearnerAchievementRecord rec : achievements.values()) {
                if (rec.userId().equals(userId)) {
                    list.add(rec);
                }
            }
            return list;
        }

        @Override
        public boolean insertAchievementIfAbsent(LearnerAchievementRecord achievement) {
            String key = achievement.userId() + ":" + achievement.badgeCode().name();
            return achievements.putIfAbsent(key, achievement) == null;
        }

        @Override
        public long countStreakByUserId(UUID userId) {
            return streaks.containsKey(userId) ? 1L : 0L;
        }

        @Override
        public long countLedgerByUserId(UUID userId) {
            return ledgers.values().stream().filter(e -> e.userId().equals(userId)).count();
        }

        @Override
        public long countAchievementsByUserId(UUID userId) {
            return findAchievementsByUserId(userId).size();
        }

        @Override
        public void deleteAllByUserId(UUID userId) {
            streaks.remove(userId);
            ledgers.entrySet().removeIf(e -> e.getValue().userId().equals(userId));
            achievements.entrySet().removeIf(e -> e.getValue().userId().equals(userId));
        }
    }
}
