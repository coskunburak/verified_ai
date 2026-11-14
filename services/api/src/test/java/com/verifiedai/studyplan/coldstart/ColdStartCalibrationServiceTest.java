package com.verifiedai.studyplan.coldstart;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.studyplan.coldstart.ColdStartModels.BaselineTier;
import com.verifiedai.studyplan.coldstart.ColdStartModels.CalibrationMode;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ColdStartDiagnosticRequest;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ColdStartDiagnosticResponse;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ConfidenceTier;
import com.verifiedai.studyplan.coldstart.ColdStartModels.DiagnosticItemSubmissionRequest;
import com.verifiedai.studyplan.coldstart.ColdStartModels.DiagnosticStatus;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ItemVerdict;
import com.verifiedai.studyplan.coldstart.ColdStartModels.RationaleCode;
import com.verifiedai.studyplan.coldstart.ColdStartModels.StoredColdStartRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColdStartCalibrationServiceTest {

    private static final UUID LEARNER_ID = UUID.fromString("81000000-0000-4000-8000-000000000101");
    private static final Pattern BANNED_STIGMA_WORDS = Pattern.compile(
            "\\b(lazy|stupid|dumb|hopeless|failure|fail|failed|punish|penalty|weak|poor|bad|tembel|aptal|başarısız|yetersiz|ceza)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private InMemoryColdStartRepository repository;
    private SimpleMeterRegistry meterRegistry;
    private MutableClock clock;
    private ColdStartCalibrationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryColdStartRepository();
        meterRegistry = new SimpleMeterRegistry();
        clock = new MutableClock(Instant.parse("2026-11-13T12:00:00Z"));
        service = new ColdStartCalibrationService(repository, meterRegistry, clock, true);
    }

    @Test
    void disabledFeatureFlagReturnsColdStartFeatureDisabledProblem() {
        ColdStartCalibrationService disabled = new ColdStartCalibrationService(repository, meterRegistry, clock, false);
        ColdStartDiagnosticRequest request = buildRequest(
                "idem-disabled-01",
                "en-US",
                "GENERAL",
                15,
                null,
                false,
                0,
                List.of(item1("A", 30), item2("A", 30), item3("A", 30))
        );

        ApiProblemException submitEx = assertThrows(ApiProblemException.class, () -> disabled.submitDiagnostic(LEARNER_ID, request));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, submitEx.status());
        assertEquals(ApiErrorCode.COLD_START_FEATURE_DISABLED, submitEx.code());

        ApiProblemException getEx = assertThrows(ApiProblemException.class, () -> disabled.getLatestCalibration(LEARNER_ID, null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, getEx.status());
        assertEquals(ApiErrorCode.COLD_START_FEATURE_DISABLED, getEx.code());
    }

    @Test
    void fastAndSlowCorrectAssignExpectedPriorsAndFoundationSolidWithPreliminaryConfidence() {
        ColdStartDiagnosticRequest request = buildRequest(
                "idem-solid-0001",
                "en-US",
                "TYT",
                20,
                "PRACTICE_FIRST",
                false,
                0,
                List.of(
                        item1("23/20", 42),
                        item2("[2, 3]", 60),
                        item3("21", 95)
                )
        );

        ColdStartDiagnosticResponse response = service.submitDiagnostic(LEARNER_ID, request);

        assertEquals(ColdStartModels.SCHEMA_VERSION, response.schemaVersion());
        assertEquals(DiagnosticStatus.COMPLETED, response.status());
        assertEquals(BaselineTier.FOUNDATION_SOLID, response.baselineTier());
        assertEquals(3, response.correctCount());
        assertEquals(ConfidenceTier.PRELIMINARY, response.confidenceTier());
        assertEquals(CalibrationMode.EXPLORATION, response.calibrationMode());
        assertEquals(0, response.llmTokensUsed());
        assertFalse(response.trainingEligible());
        assertFalse(response.idempotentReplay());
        assertTrue(response.detectedTrapArchetypes().isEmpty());

        assertEquals(0.65, response.initializedPriors().get(0).priorProbability());
        assertEquals(0.65, response.initializedPriors().get(1).priorProbability());
        assertEquals(0.50, response.initializedPriors().get(2).priorProbability());
        assertEquals(ItemVerdict.CORRECT_FAST, response.itemOutcomes().get(0).verdict());
        assertEquals(ItemVerdict.CORRECT_SLOW, response.itemOutcomes().get(2).verdict());
        assertEquals(1.0, meterRegistry.counter(
                "studyplan_cold_start_diagnostic_completed_total",
                "baseline_tier", "FOUNDATION_SOLID",
                "confidence_tier", "PRELIMINARY",
                "calibration_mode", "EXPLORATION"
        ).count());
        assertNoStigmatizingLanguage(response);
    }

    @Test
    void knownDistractorTrapsAssignLowerPriorAndSeedTrapArchetypes() {
        ColdStartDiagnosticRequest request = buildRequest(
                "idem-traps-0001",
                "tr-TR",
                "AYT",
                25,
                "SOCRATIC_COACH",
                false,
                0,
                List.of(
                        item1("7/20", 38),
                        item2("C", 75),
                        item3("81", 40)
                )
        );

        ColdStartDiagnosticResponse response = service.submitDiagnostic(LEARNER_ID, request);

        assertEquals(BaselineTier.FOUNDATION_REPAIR, response.baselineTier());
        assertEquals(0, response.correctCount());
        assertEquals(
                List.of("SIGN_INVERSION_TRAP", "ROOT_BOUNDARY_TRAP", "COMPOSITION_ORDER_TRAP"),
                response.detectedTrapArchetypes()
        );
        response.initializedPriors().forEach(prior -> assertEquals(0.15, prior.priorProbability()));
        assertNoStigmatizingLanguage(response);
    }

    @Test
    void timedOutAndSkippedItemsAssignZeroTwentyPrior() {
        ColdStartDiagnosticRequest request = buildRequest(
                "idem-timeout-001",
                "en-US",
                "GENERAL",
                15,
                null,
                false,
                0,
                List.of(
                        new DiagnosticItemSubmissionRequest(ColdStartCalibrationService.ITEM_NUMBER_SENSE, null, "", 15, false, true),
                        new DiagnosticItemSubmissionRequest(ColdStartCalibrationService.ITEM_QUADRATIC_INEQUALITY, "A", "[2,3]", 185, true, false),
                        item3("D", 45)
                )
        );

        ColdStartDiagnosticResponse response = service.submitDiagnostic(LEARNER_ID, request);

        assertEquals(BaselineTier.FOUNDATION_REPAIR, response.baselineTier());
        assertEquals(ItemVerdict.SKIPPED, response.itemOutcomes().get(0).verdict());
        assertEquals(ItemVerdict.TIMED_OUT, response.itemOutcomes().get(1).verdict());
        assertEquals(ItemVerdict.INCORRECT_OTHER, response.itemOutcomes().get(2).verdict());
        response.initializedPriors().forEach(prior -> assertEquals(0.20, prior.priorProbability()));
    }

    @Test
    void goalConflictResolutionAndExplicitPreferenceOverrideWorkDeterministically() {
        ColdStartDiagnosticRequest request = buildRequest(
                "idem-conflict-001",
                "en-US",
                "AYT",
                10,
                "SOCRATIC_COACH",
                true,
                0,
                List.of(item1("A", 30), item2("B", 45), item3("A", 70))
        );

        ColdStartDiagnosticResponse response = service.submitDiagnostic(LEARNER_ID, request);

        assertEquals(BaselineTier.CALIBRATED_MIXED, response.baselineTier());
        assertTrue(response.goalConflictResolved());
        assertEquals(10, response.recommendedDailyBudgetMinutes());
        assertTrue(response.explicitPreferenceOverride());
        assertEquals("SOCRATIC_COACH", response.preferredModality());
        assertTrue(response.rationales().stream().anyMatch(r -> r.code() == RationaleCode.GOAL_CAPACITY_CONFLICT_RESOLVED));
        assertTrue(response.rationales().stream().anyMatch(r -> r.code() == RationaleCode.EXPLICIT_PREFERENCE_OVERRIDE));
        assertNoStigmatizingLanguage(response);
    }

    @Test
    void inactivityDecayAt14And21DaysAppliesRetentionFloorAndRevalidationFlag() {
        ColdStartDiagnosticRequest decay14 = buildRequest(
                "idem-decay-14d",
                "tr-TR",
                "TYT",
                15,
                "MIXED",
                false,
                14,
                List.of(item1("A", 30), item2("A", 40), item3("B", 35))
        );
        ColdStartDiagnosticResponse res14 = service.submitDiagnostic(LEARNER_ID, decay14);
        assertTrue(res14.inactiveDecayApplied());
        assertEquals(DiagnosticStatus.COMPLETED, res14.status());
        assertEquals(0.45, res14.initializedPriors().get(0).priorProbability());
        assertEquals(0.25, res14.initializedPriors().get(2).priorProbability());
        assertTrue(res14.rationales().stream().anyMatch(r -> r.code() == RationaleCode.INACTIVE_DECAY_APPLIED));
        assertNull(res14.revalidationPromptKey());

        ColdStartDiagnosticRequest decay21 = buildRequest(
                "idem-decay-21d",
                "en-US",
                "TYT",
                15,
                "MIXED",
                false,
                21,
                List.of(item1("A", 30), item2("A", 40), item3("A", 50))
        );
        ColdStartDiagnosticResponse res21 = service.submitDiagnostic(LEARNER_ID, decay21);
        assertTrue(res21.inactiveDecayApplied());
        assertEquals(DiagnosticStatus.REVALIDATION_REQUIRED, res21.status());
        assertEquals(CalibrationMode.REVALIDATION_REQUIRED, res21.calibrationMode());
        assertNotNull(res21.revalidationPromptKey());
        res21.initializedPriors().forEach(p -> {
            assertTrue(p.priorProbability() >= 0.25, "Retention floor >= 0.25 must hold");
            assertTrue(p.decayApplied());
        });
        assertNoStigmatizingLanguage(res21);
    }

    @Test
    void evidenceCountAtLeast3TransitionsToCalibratedActiveModeAndIdempotentReplay() {
        repository.seedSkillEvidence(LEARNER_ID, ColdStartCalibrationService.SKILL_NUMBER_SENSE, 3);
        repository.seedSkillEvidence(LEARNER_ID, ColdStartCalibrationService.SKILL_QUADRATIC_INEQUALITY, 4);
        repository.seedSkillEvidence(LEARNER_ID, ColdStartCalibrationService.SKILL_FUNCTION_COMPOSITION, 5);

        ColdStartDiagnosticRequest request = buildRequest(
                "idem-calibrated-01",
                "en-US",
                "GENERAL",
                20,
                "PRACTICE_FIRST",
                false,
                0,
                List.of(item1("A", 20), item2("A", 30), item3("A", 40))
        );
        ColdStartDiagnosticResponse first = service.submitDiagnostic(LEARNER_ID, request);
        assertEquals(ConfidenceTier.CALIBRATED, first.confidenceTier());
        assertEquals(CalibrationMode.ACTIVE_CALIBRATED, first.calibrationMode());
        assertFalse(first.idempotentReplay());

        ColdStartDiagnosticResponse replay = service.submitDiagnostic(LEARNER_ID, request);
        assertTrue(replay.idempotentReplay());
        assertEquals(first.diagnosticId(), replay.diagnosticId());
        assertEquals(first.initializedPriors(), replay.initializedPriors());
    }

    private static void assertNoStigmatizingLanguage(ColdStartDiagnosticResponse response) {
        response.rationales().forEach(r -> {
            assertFalse(BANNED_STIGMA_WORDS.matcher(r.localizedMessage()).find(),
                    "Found stigmatizing lexicon in rationale: " + r.localizedMessage());
        });
    }

    private static DiagnosticItemSubmissionRequest item1(String answer, int seconds) {
        return new DiagnosticItemSubmissionRequest(ColdStartCalibrationService.ITEM_NUMBER_SENSE, null, answer, seconds, false, false);
    }

    private static DiagnosticItemSubmissionRequest item2(String answer, int seconds) {
        return new DiagnosticItemSubmissionRequest(ColdStartCalibrationService.ITEM_QUADRATIC_INEQUALITY, null, answer, seconds, false, false);
    }

    private static DiagnosticItemSubmissionRequest item3(String answer, int seconds) {
        return new DiagnosticItemSubmissionRequest(ColdStartCalibrationService.ITEM_FUNCTION_COMPOSITION, null, answer, seconds, false, false);
    }

    private static ColdStartDiagnosticRequest buildRequest(
            String key,
            String locale,
            String target,
            Integer budget,
            String modality,
            boolean overridePref,
            Integer inactiveDays,
            List<DiagnosticItemSubmissionRequest> responses
    ) {
        return new ColdStartDiagnosticRequest(
                ColdStartModels.SCHEMA_VERSION,
                key,
                locale,
                target,
                budget,
                modality,
                overridePref,
                inactiveDays,
                responses
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant initial) {
            this.instant = initial;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    static final class InMemoryColdStartRepository implements ColdStartDiagnosticRepository {
        private final Map<String, StoredColdStartRecord> byIdempotency = new LinkedHashMap<>();
        private final Map<UUID, Map<String, Integer>> evidenceByLearner = new HashMap<>();

        void seedSkillEvidence(UUID learnerId, String skillCode, int count) {
            evidenceByLearner.computeIfAbsent(learnerId, k -> new HashMap<>()).put(skillCode, count);
        }

        @Override
        public Optional<StoredColdStartRecord> findByLearnerIdAndIdempotencyKey(UUID learnerId, String idempotencyKey) {
            return Optional.ofNullable(byIdempotency.get(learnerId + "::" + idempotencyKey));
        }

        @Override
        public Optional<StoredColdStartRecord> findLatestByLearnerId(UUID learnerId) {
            return byIdempotency.values().stream()
                    .filter(r -> r.learnerId().equals(learnerId))
                    .reduce((first, second) -> second);
        }

        @Override
        public Map<String, Integer> findSkillEvidenceCounts(UUID learnerId, Collection<String> skillCodes) {
            Map<String, Integer> seeded = evidenceByLearner.getOrDefault(learnerId, Map.of());
            Map<String, Integer> result = new LinkedHashMap<>();
            for (String code : skillCodes) {
                if (seeded.containsKey(code)) {
                    result.put(code, seeded.get(code));
                }
            }
            return result;
        }

        @Override
        public Optional<Instant> findLastVerifiedActivityAt(UUID learnerId) {
            return Optional.empty();
        }

        @Override
        public void insertDiagnostic(StoredColdStartRecord record) {
            byIdempotency.put(record.learnerId() + "::" + record.idempotencyKey(), record);
        }
    }
}
