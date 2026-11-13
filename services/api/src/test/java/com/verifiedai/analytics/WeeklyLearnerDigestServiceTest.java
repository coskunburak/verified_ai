package com.verifiedai.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.verifiedai.analytics.WeeklyLearnerDigestRepository.AttemptEventRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.LearnerContextProjection;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.LedgerDayRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.MasteryTransitionRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.MistakeDnaProfileRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.MistakeWindowCountRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.SkillMasteryRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.SpacedReviewRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.StudySessionRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.TwinCompletionRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.WeeklyEvidenceProjection;
import com.verifiedai.analytics.WeeklyReportDtos.ExplainableRecommendationItemDto;
import com.verifiedai.analytics.WeeklyReportDtos.WeeklyDigestDto;
import com.verifiedai.analytics.WeeklyReportDtos.WeeklySharePacketDto;
import com.verifiedai.analytics.WeeklyReportDtos.WeeklySharePacketRequest;
import com.verifiedai.curriculum.api.CurriculumDagSnapshot;
import com.verifiedai.curriculum.api.CurriculumGraphQueryPort;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class WeeklyLearnerDigestServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000890");
    private static final Instant FIXED_NOW = Instant.parse("2026-11-13T10:30:00Z");

    private FakeDigestRepository repository;
    private SimpleMeterRegistry meterRegistry;
    private WeeklyLearnerDigestService service;

    @BeforeEach
    void setUp() {
        repository = new FakeDigestRepository();
        meterRegistry = new SimpleMeterRegistry();
        CurriculumGraphQueryPort graphPort = code -> Optional.of(new CurriculumDagSnapshot(
            code,
            List.of("MATH.ARITHMETIC.FRACTIONS", "MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY", "MATH.EQUATIONS.LINEAR_ONE_VAR"),
            Map.of(
                "MATH.ARITHMETIC.FRACTIONS", "Fraction Operations & Equivalence",
                "MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY", "Distributive Property & Expanding",
                "MATH.EQUATIONS.LINEAR_ONE_VAR", "Single-Variable Linear Equations"
            ),
            Map.of(
                "MATH.ARITHMETIC.FRACTIONS", List.of(),
                "MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY", List.of("MATH.ARITHMETIC.FRACTIONS"),
                "MATH.EQUATIONS.LINEAR_ONE_VAR", List.of("MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY")
            ),
            List.of(
                new CurriculumDagSnapshot.Edge("MATH.ARITHMETIC.FRACTIONS", "MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY", BigDecimal.ONE),
                new CurriculumDagSnapshot.Edge("MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY", "MATH.EQUATIONS.LINEAR_ONE_VAR", BigDecimal.ONE)
            )
        ));
        service = new WeeklyLearnerDigestService(
            repository,
            graphPort,
            meterRegistry,
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
            true
        );
    }

    @Test
    void computesDeterministicDigestAndPriorityOrderingAndPersists() {
        repository.setEvidence(populatedEvidence());

        WeeklyDigestDto first = service.getWeeklyDigest(USER_ID, "2026-W46", "en", false);
        assertThat(first.schemaVersion()).isEqualTo("weekly-digest-v1");
        assertThat(first.status()).isEqualTo(WeeklyDigestStatus.GENERATED);
        assertThat(first.trainingEligible()).isFalse();
        assertThat(first.weekStartDate()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(first.weekEndDate()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(first.executiveSummary().solvedAttemptsCount()).isEqualTo(2);
        assertThat(first.executiveSummary().independentTwinsSolvedCount()).isEqualTo(1);
        assertThat(first.executiveSummary().studyMinutes()).isEqualTo(35);
        assertThat(first.executiveSummary().activeDaysCount()).isEqualTo(2);
        assertThat(first.masteryMomentum().dailyActivityBuckets()).hasSize(7);
        assertThat(first.recurringMistakes()).isNotEmpty();
        assertThat(first.recommendations()).isNotEmpty();

        List<ExplainableRecommendationItemDto> recs = first.recommendations();
        for (int i = 0; i < recs.size(); i++) {
            assertThat(recs.get(i).evidenceRefs()).isNotEmpty();
            assertThat(recs.get(i).priorityScore()).isBetween(0.0, 1.0);
            if (i > 0) {
                assertThat(recs.get(i - 1).priorityScore()).isGreaterThanOrEqualTo(recs.get(i).priorityScore());
            }
        }
        assertThat(recs.getFirst().targetSkillId()).isEqualTo("MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY");
        assertThat(repository.readEvidenceCalls.get()).isEqualTo(1);
        assertThat(meterRegistry.counter("weekly_report_viewed", "status", "GENERATED", "locale", "en", "cacheHit", "false").count())
            .isEqualTo(1.0);
    }

    @Test
    void servesCachedDigestOnRepeatCallAndRecomputesWhenForceRefreshTrue() {
        repository.setEvidence(populatedEvidence());

        WeeklyDigestDto first = service.getWeeklyDigest(USER_ID, "2026-W46", "en", false);
        WeeklyDigestDto second = service.getWeeklyDigest(USER_ID, "2026-W46", "en", false);
        WeeklyDigestDto refreshed = service.getWeeklyDigest(USER_ID, "2026-W46", "en", true);

        assertThat(second.digestId()).isEqualTo(first.digestId());
        assertThat(refreshed.digestId()).isEqualTo(first.digestId());
        assertThat(repository.readEvidenceCalls.get()).isEqualTo(2);
        assertThat(meterRegistry.counter("weekly_report_viewed", "status", "GENERATED", "locale", "en", "cacheHit", "true").count())
            .isEqualTo(1.0);
    }

    @Test
    void handlesColdStartEmptyWeekWithGentleStarterRecommendations() {
        repository.setEvidence(WeeklyEvidenceProjection.empty());

        WeeklyDigestDto digest = service.getWeeklyDigest(USER_ID, "2026-W45", "tr", false);

        assertThat(digest.status()).isEqualTo(WeeklyDigestStatus.EMPTY_WEEK);
        assertThat(digest.trainingEligible()).isFalse();
        assertThat(digest.locale()).isEqualTo("tr");
        assertThat(digest.executiveSummary().solvedAttemptsCount()).isZero();
        assertThat(digest.recommendations()).isNotEmpty();
        assertThat(digest.recommendations().getFirst().reasonCode()).isEqualTo(RecommendationReasonCode.COLD_START_ONBOARDING);
        assertThat(digest.recommendations().getFirst().evidenceRefs()).isNotEmpty();
        assertThat(digest.executiveSummary().headline()).doesNotContain("başarısız", "yetersiz");
    }

    @Test
    void rejectsInvalidAndFutureWeekIsoWith400() {
        assertThatThrownBy(() -> service.getWeeklyDigest(USER_ID, "2026-13", "en", false))
            .isInstanceOf(ApiProblemException.class)
            .extracting(e -> ((ApiProblemException) e).code())
            .isEqualTo(ApiErrorCode.WEEKLY_REPORT_INVALID_WEEK_ISO);

        assertThatThrownBy(() -> service.getWeeklyDigest(USER_ID, "2025-W53", "en", false))
            .isInstanceOf(ApiProblemException.class)
            .extracting(e -> ((ApiProblemException) e).code())
            .isEqualTo(ApiErrorCode.WEEKLY_REPORT_INVALID_WEEK_ISO);

        assertThatThrownBy(() -> service.getWeeklyDigest(USER_ID, "2026-W50", "en", false))
            .isInstanceOf(ApiProblemException.class)
            .extracting(e -> ((ApiProblemException) e).code())
            .isEqualTo(ApiErrorCode.WEEKLY_REPORT_FUTURE_WEEK_NOT_ALLOWED);
    }

    @Test
    void returns503RecoverableWhenKillSwitchDisabled() {
        WeeklyLearnerDigestService disabled = new WeeklyLearnerDigestService(
            repository,
            null,
            meterRegistry,
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
            false
        );

        assertThatThrownBy(() -> disabled.getWeeklyDigest(USER_ID, "2026-W46", "en", false))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                ApiProblemException ape = (ApiProblemException) ex;
                assertThat(ape.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(ape.code()).isEqualTo(ApiErrorCode.WEEKLY_REPORT_FEATURE_DISABLED);
                assertThat(ape.details().get("recoverable")).isEqualTo(true);
            });
    }

    @Test
    void createsPrivacySafeSharePacketRedactingEmailsAndEnforcingGPriv03() {
        repository.setEvidence(populatedEvidence());
        service.getWeeklyDigest(USER_ID, "2026-W46", "en", false);

        WeeklySharePacketRequest request = new WeeklySharePacketRequest("PARENT", "student@example.com 5551234", "en");
        WeeklySharePacketDto packet = service.createSharePacket(USER_ID, "2026-W46", request);

        assertThat(packet.schemaVersion()).isEqualTo("weekly-share-packet-v1");
        assertThat(packet.recipientRole()).isEqualTo(ShareRecipientRole.PARENT);
        assertThat(packet.displayAlias()).isEqualTo("Learner");
        assertThat(packet.trainingEligible()).isFalse();
        assertThat(packet.privacyAttestation().policyId()).isEqualTo("G-PRIV-03");
        assertThat(packet.privacyAttestation().piiExcluded()).isTrue();
        assertThat(packet.privacyAttestation().rawTutorTranscriptsIncluded()).isFalse();
        assertThat(packet.privacyAttestation().rawStudentPhotosIncluded()).isFalse();
        assertThat(packet.privacyAttestation().exactSessionTimestampsIncluded()).isFalse();
        assertThat(packet.privacyAttestation().trainingEligible()).isFalse();
        assertThat(meterRegistry.counter("weekly_tutor_packet_exported", "recipientRole", "PARENT", "locale", "en").count())
            .isEqualTo(1.0);
    }

    @Test
    void computePriorityScoreClampsWithinUnitInterval() {
        double score = WeeklyLearnerDigestService.computePriorityScore(0.50, 0.80, 0.40, 0.3333);
        assertThat(score).isEqualTo(0.53);
        assertThat(WeeklyLearnerDigestService.computePriorityScore(-2.0, 4.0, 2.0, 5.0)).isEqualTo(1.0);
        assertThat(WeeklyLearnerDigestService.computePriorityScore(1.5, -1.0, -1.0, -1.0)).isEqualTo(0.0);
    }

    @Test
    void analyticsPrivacyLifecycleContributorExportsAndDeletesWeeklyDigests() {
        repository.setEvidence(populatedEvidence());
        service.getWeeklyDigest(USER_ID, "2026-W46", "en", false);

        LearnerMasteryCalibrationRepository calibrationRepo = mock(LearnerMasteryCalibrationRepository.class);
        AnalyticsPrivacyLifecycleContributor contributor = new AnalyticsPrivacyLifecycleContributor(calibrationRepo, repository);

        Map<String, Object> exported = contributor.exportUserData(USER_ID);
        assertThat(exported).containsKeys("category", "recordCount", "records", "weeklyLearnerDigestCount", "weeklyLearnerDigests");
        assertThat(exported.get("weeklyLearnerDigestCount")).isEqualTo(1);

        contributor.deleteUserData(USER_ID, FIXED_NOW);
        assertThat(repository.findAllByUserId(USER_ID)).isEmpty();
    }

    @Test
    void repositoryJsonRoundTripHandlesJavaTimeAndTrainingEligibleFlag() {
        repository.setEvidence(populatedEvidence());
        WeeklyDigestDto digest = service.getWeeklyDigest(USER_ID, "2026-W46", "en", false);

        String payloadJson = repository.writeJson(digest);
        String recsJson = repository.writeJson(digest.recommendations());

        assertThat(payloadJson).contains("\"training_eligible\":false");
        assertThat(payloadJson).contains("\"weekStartDate\":\"2026-11-09\"");

        WeeklyDigestDto decoded = repository.readJson(payloadJson, WeeklyDigestDto.class);
        assertThat(decoded.weekStartDate()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(decoded.trainingEligible()).isFalse();
        assertThat(decoded.generatedAt()).isEqualTo(digest.generatedAt());
        assertThat(recsJson).contains("targetSkillId");
    }

    private WeeklyEvidenceProjection populatedEvidence() {
        return new WeeklyEvidenceProjection(
            List.of(
                new LedgerDayRow(LocalDate.of(2026, 11, 10), "VERIFIED_INDEPENDENT", 1, 0, "twin:1"),
                new LedgerDayRow(LocalDate.of(2026, 11, 12), "VERIFIED_INDEPENDENT", 1, 0, "twin:2")
            ),
            List.of(
                new AttemptEventRow(UUID.fromString("00000000-0000-4000-8000-000000000894"), Instant.parse("2026-11-10T14:00:00Z")),
                new AttemptEventRow(UUID.fromString("00000000-0000-4000-8000-000000000895"), Instant.parse("2026-11-12T15:00:00Z"))
            ),
            List.of(
                new TwinCompletionRow(
                    UUID.fromString("00000000-0000-4000-8000-000000000896"),
                    "MATH.EQUATIONS.LINEAR_ONE_VAR",
                    "SOLVED_UNASSISTED",
                    Instant.parse("2026-11-12T15:20:00Z")
                )
            ),
            List.of(
                new StudySessionRow(UUID.fromString("00000000-0000-4000-8000-000000000897"), "MATH", "COMPLETED", 1200, Instant.parse("2026-11-10T14:15:00Z")),
                new StudySessionRow(UUID.fromString("00000000-0000-4000-8000-000000000898"), "MATH", "COMPLETED", 900, Instant.parse("2026-11-12T15:30:00Z"))
            ),
            3,
            List.of(
                new SkillMasteryRow("MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY", 0.42, 7.0, 2, 1, 0.42),
                new SkillMasteryRow("MATH.EQUATIONS.LINEAR_ONE_VAR", 0.80, 14.0, 4, 0, 0.80)
            ),
            List.of(
                new MasteryTransitionRow("MATH.EQUATIONS.LINEAR_ONE_VAR", 0.68, 0.80, "INDEPENDENT", Instant.parse("2026-11-12T15:20:00Z"))
            ),
            List.of(
                new MistakeDnaProfileRow("SIGN_DISTRIBUTION_FLIP", "MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY", 0.65, 3, "ACTIVE")
            ),
            List.of(
                new MistakeWindowCountRow("SIGN_DISTRIBUTION_FLIP", "MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY", 2, 4)
            ),
            Set.of("SIGN_DISTRIBUTION_FLIP"),
            List.of(
                new SpacedReviewRow("MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY", 0.52, "PENDING", Instant.parse("2026-11-13T08:00:00Z"))
            ),
            Map.of("MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY", 2)
        );
    }

    private static final class FakeDigestRepository extends WeeklyLearnerDigestRepository {

        private final Map<String, WeeklyLearnerDigest> store = new ConcurrentHashMap<>();
        private WeeklyEvidenceProjection evidence = WeeklyEvidenceProjection.empty();
        private final AtomicInteger readEvidenceCalls = new AtomicInteger();

        void setEvidence(WeeklyEvidenceProjection evidence) {
            this.evidence = evidence;
        }

        @Override
        public Optional<WeeklyLearnerDigest> findByUserAndWeek(UUID userId, String weekIso) {
            return Optional.ofNullable(store.get(userId + "|" + weekIso));
        }

        @Override
        public List<WeeklyLearnerDigest> findAllByUserId(UUID userId) {
            List<WeeklyLearnerDigest> list = new ArrayList<>();
            for (WeeklyLearnerDigest d : store.values()) {
                if (d.userId().equals(userId)) {
                    list.add(d);
                }
            }
            return list;
        }

        @Override
        public void upsertDigest(WeeklyLearnerDigest digest) {
            store.put(digest.userId() + "|" + digest.weekIso(), digest);
        }

        @Override
        public int deleteByUserId(UUID userId) {
            int before = store.size();
            store.entrySet().removeIf(e -> e.getValue().userId().equals(userId));
            return before - store.size();
        }

        @Override
        public LearnerContextProjection readLearnerContext(UUID userId) {
            return new LearnerContextProjection("UTC", "en", 4);
        }

        @Override
        public WeeklyEvidenceProjection readWeeklyEvidence(
            UUID userId,
            LocalDate weekStartDate,
            LocalDate weekEndDate,
            Instant weekStartTs,
            Instant weekEndTs,
            Instant prevWeekStartTs
        ) {
            readEvidenceCalls.incrementAndGet();
            return evidence;
        }
    }
}
