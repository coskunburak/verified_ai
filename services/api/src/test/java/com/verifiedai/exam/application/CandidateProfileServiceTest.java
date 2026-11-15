package com.verifiedai.exam.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.verifiedai.exam.application.CandidateProfileService.UpsertResult;
import com.verifiedai.exam.domain.CandidateProfileModels.AccessibilityAccommodations;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateHorizonHistoryEntry;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileHistoryResponse;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileRecord;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileResponse;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileUpsertRequest;
import com.verifiedai.exam.domain.CandidateProfileModels.FeasibilityStatus;
import com.verifiedai.exam.domain.CandidateProfileModels.ParsedSyllabusTopicItem;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusUploadRecord;
import com.verifiedai.exam.domain.ExamModels.CognitiveLevelMix;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.ExamFamily;
import com.verifiedai.exam.domain.ExamModels.ExamSectionSpec;
import com.verifiedai.exam.domain.ExamModels.ExamStatus;
import com.verifiedai.exam.domain.ExamModels.ExamTopicWeight;
import com.verifiedai.exam.domain.ExamModels.Jurisdiction;
import com.verifiedai.exam.domain.ExamModels.NavigationPolicy;
import com.verifiedai.exam.domain.ExamModels.ReadinessGapPreview;
import com.verifiedai.exam.domain.ExamModels.ScoringFormulaType;
import com.verifiedai.exam.domain.ExamModels.ScoringParameters;
import com.verifiedai.exam.domain.ExamModels.StudentExamTargetResponse;
import com.verifiedai.exam.domain.ExamModels.TargetBindingOutcome;
import com.verifiedai.exam.domain.ExamModels.TargetStatus;
import com.verifiedai.exam.events.CandidateGoalUpdatedEvent;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

class CandidateProfileServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-15T10:00:00Z");
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 11, 15);
    private static final UUID USER_ID = UUID.fromString("92000000-0000-4000-8000-000000000101");
    private static final UUID EXAM_DEF_ID = UUID.fromString("91000000-0000-4000-8000-000000000101");
    private static final UUID TARGET_ID = UUID.fromString("91000000-0000-4000-8000-000000000901");

    private InMemoryCandidateProfileRepository repository;
    private ExamRepository examRepository;
    private StudentExamTargetService targetService;
    private List<CandidateGoalUpdatedEvent> publishedEvents;
    private SimpleMeterRegistry meterRegistry;
    private CandidateProfileService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCandidateProfileRepository();
        examRepository = mock(ExamRepository.class);
        targetService = mock(StudentExamTargetService.class);
        publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = event -> {
            if (event instanceof CandidateGoalUpdatedEvent goalEvent) {
                publishedEvents.add(goalEvent);
            }
        };
        meterRegistry = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

        ExamDefinition tytExam = buildTytMathDefinition();
        when(examRepository.findLatestByCode("TR_YKS_TYT_MATH", false)).thenReturn(Optional.of(tytExam));
        when(examRepository.findByCodeAndVersion("TR_YKS_TYT_MATH", "2026.1")).thenReturn(Optional.of(tytExam));
        when(targetService.upsertTarget(eq(USER_ID), any())).thenAnswer(inv -> {
            StudentExamTargetResponse targetResp = new StudentExamTargetResponse(
                    "student-exam-target-v1",
                    TARGET_ID,
                    USER_ID,
                    EXAM_DEF_ID,
                    "TR_YKS_TYT_MATH",
                    "2026.1",
                    tytExam.blueprintHash(),
                    new BigDecimal("35.00"),
                    FIXED_TODAY.plusDays(90),
                    1,
                    TargetStatus.ACTIVE,
                    true,
                    "sync-key",
                    new ReadinessGapPreview("readiness-gap-preview-v1", new BigDecimal("20.0"), new BigDecimal("35.0"),
                            new BigDecimal("15.0"), "PRELIMINARY", 0, false),
                    null,
                    null,
                    false,
                    0,
                    false,
                    FIXED_NOW,
                    FIXED_NOW
            );
            return new TargetBindingOutcome(targetResp, true);
        });

        service = new CandidateProfileService(
                repository,
                examRepository,
                targetService,
                publisher,
                meterRegistry,
                clock,
                true
        );
    }

    @Test
    void upsertOptimalProfileAndReplayIdempotentlyAtZeroLlmSpend() {
        CandidateProfileUpsertRequest request = new CandidateProfileUpsertRequest(
                "candidate-profile-request-v1",
                "TR_YKS_TYT_MATH",
                null,
                new BigDecimal("35.50"),
                FIXED_TODAY.plusDays(90),
                60,
                420,
                new AccessibilityAccommodations(new BigDecimal("1.25"), true, true, false),
                new BigDecimal("0.2500"),
                "INITIAL_SETUP",
                "profile-key-001"
        );

        UpsertResult created = service.upsertProfile(USER_ID, request, null);
        assertTrue(created.created());
        CandidateProfileResponse r = created.response();
        assertEquals("candidate-profile-v1", r.schemaVersion());
        assertEquals("TR_YKS_TYT_MATH", r.activeExamCode());
        assertEquals("2026.1", r.pinnedExamVersion());
        assertEquals(TARGET_ID, r.activeTargetId());
        assertEquals(FeasibilityStatus.OPTIMAL, r.feasibilityStatus());
        assertEquals(30, r.requiredDailyMinutes());
        assertEquals(60, r.effectiveDailyCapacityMinutes());
        assertEquals(82, r.adjustedSections().getFirst().adjustedDurationMinutes()); // ceil(65 * 1.25) = 82
        assertEquals(0, r.llmTokensUsed());
        assertFalse(r.trainingEligible());
        assertFalse(r.idempotentReplay());
        assertEquals(1, publishedEvents.size());
        assertEquals("TR_YKS_TYT_MATH", publishedEvents.getFirst().examCode());
        assertEquals(1.0d, meterRegistry.counter("verifiedai_exam_candidate_profile_upserted_total").count());
        assertEquals(1.0d, meterRegistry.counter("verifiedai_exam_accommodation_updated_total").count());

        // Idempotent replay
        UpsertResult replay = service.upsertProfile(USER_ID, request, "profile-key-001");
        assertFalse(replay.created());
        assertTrue(replay.response().idempotentReplay());
        assertEquals(1, publishedEvents.size());

        // Fetch profile and history
        CandidateProfileResponse fetched = service.getProfile(USER_ID);
        assertEquals(r.profileId(), fetched.profileId());
        CandidateProfileHistoryResponse history = service.getProfileHistory(USER_ID);
        assertEquals(1, history.items().size());
        assertEquals("INITIAL_SETUP", history.items().getFirst().changeReason());
    }

    @Test
    void classifiesStretchAtRiskAndUnrealisticAndUpdatesImmutableHistory() {
        // STETCH: 60 days, 45m/day (30*90/60 = 45 -> ratio 1.00)
        UpsertResult stretch = service.upsertProfile(
                USER_ID,
                new CandidateProfileUpsertRequest(
                        null, "TR_YKS_TYT_MATH", "2026.1", new BigDecimal("30"),
                        FIXED_TODAY.plusDays(60), 45, 315, null, null, null, "horizon-1"
                ),
                null
        );
        assertEquals(FeasibilityStatus.STRETCH, stretch.response().feasibilityStatus());

        // AT_RISK: 45 days, 45m/day (2700/45 = 60 -> ratio 1.3333)
        UpsertResult atRisk = service.upsertProfile(
                USER_ID,
                new CandidateProfileUpsertRequest(
                        null, "TR_YKS_TYT_MATH", "2026.1", new BigDecimal("32"),
                        FIXED_TODAY.plusDays(45), 45, 315, null, null, null, "horizon-2"
                ),
                null
        );
        assertEquals(FeasibilityStatus.AT_RISK, atRisk.response().feasibilityStatus());

        // UNREALISTIC: 10 days, 15m/day (2700/10 = 270 -> ratio 18.0)
        UpsertResult unrealistic = service.upsertProfile(
                USER_ID,
                new CandidateProfileUpsertRequest(
                        null, "TR_YKS_TYT_MATH", "2026.1", new BigDecimal("38"),
                        FIXED_TODAY.plusDays(10), 15, 105, null, null, null, "horizon-3"
                ),
                null
        );
        assertEquals(FeasibilityStatus.UNREALISTIC, unrealistic.response().feasibilityStatus());
        assertEquals(2.0d, meterRegistry.counter("verifiedai_exam_feasibility_warning_total").count());

        CandidateProfileHistoryResponse history = service.getProfileHistory(USER_ID);
        assertEquals(3, history.items().size());
        assertEquals(FIXED_TODAY.plusDays(45), history.items().getFirst().previousTargetDate());
        assertEquals(FIXED_TODAY.plusDays(10), history.items().getFirst().newTargetDate());
    }

    @Test
    void rejectsInvalidDateBudgetAccommodationAndTargetScoreWithDeterministicErrorCodes() {
        ApiProblemException pastDateEx = assertThrows(ApiProblemException.class, () ->
                service.upsertProfile(USER_ID, new CandidateProfileUpsertRequest(
                        null, "TR_YKS_TYT_MATH", null, new BigDecimal("30"),
                        FIXED_TODAY, 60, 420, null, null, null, "bad-date"
                ), null)
        );
        assertEquals(ApiErrorCode.CANDIDATE_EXAM_DATE_IN_PAST, pastDateEx.code());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, pastDateEx.status());

        ApiProblemException budgetEx = assertThrows(ApiProblemException.class, () ->
                service.upsertProfile(USER_ID, new CandidateProfileUpsertRequest(
                        null, "TR_YKS_TYT_MATH", null, new BigDecimal("30"),
                        FIXED_TODAY.plusDays(30), 120, 60, null, null, null, "bad-budget"
                ), null)
        );
        assertEquals(ApiErrorCode.CANDIDATE_STUDY_BUDGET_INVALID, budgetEx.code());

        ApiProblemException accommodationEx = assertThrows(ApiProblemException.class, () ->
                service.upsertProfile(USER_ID, new CandidateProfileUpsertRequest(
                        null, "TR_YKS_TYT_MATH", null, new BigDecimal("30"),
                        FIXED_TODAY.plusDays(30), 60, 420,
                        new AccessibilityAccommodations(new BigDecimal("1.33"), false, false, false),
                        null, null, "bad-acc"
                ), null)
        );
        assertEquals(ApiErrorCode.CANDIDATE_ACCOMMODATION_INVALID, accommodationEx.code());

        ApiProblemException scoreEx = assertThrows(ApiProblemException.class, () ->
                service.upsertProfile(USER_ID, new CandidateProfileUpsertRequest(
                        null, "TR_YKS_TYT_MATH", null, new BigDecimal("99"),
                        FIXED_TODAY.plusDays(30), 60, 420, null, null, null, "bad-score"
                ), null)
        );
        assertEquals(ApiErrorCode.EXAM_TARGET_SCORE_OUT_OF_RANGE, scoreEx.code());
    }

    @Test
    void returnsNotFoundWhenProfileMissingAndServiceUnavailableWhenDisabled() {
        ApiProblemException notFound = assertThrows(ApiProblemException.class, () -> service.getProfile(USER_ID));
        assertEquals(ApiErrorCode.CANDIDATE_PROFILE_NOT_FOUND, notFound.code());
        assertEquals(HttpStatus.NOT_FOUND, notFound.status());

        CandidateProfileService disabled = new CandidateProfileService(
                repository,
                examRepository,
                targetService,
                event -> {},
                new SimpleMeterRegistry(),
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
                false
        );
        ApiProblemException disabledEx = assertThrows(ApiProblemException.class, () -> disabled.getProfile(USER_ID));
        assertEquals(ApiErrorCode.EXAM_FEATURE_DISABLED, disabledEx.code());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, disabledEx.status());
    }

    static ExamDefinition buildTytMathDefinition() {
        ExamTopicWeight w1 = new ExamTopicWeight(
                UUID.randomUUID(), EXAM_DEF_ID, "TYT_MATH",
                "MATH.ARITHMETIC.INTEGER_OPERATIONS", "MATH.ARITHMETIC",
                new BigDecimal("40.00"), 35, 40, new CognitiveLevelMix(0.3, 0.5, 0.2), 1
        );
        return new ExamDefinition(
                EXAM_DEF_ID,
                "TR_YKS_TYT_MATH",
                "2026.1",
                Jurisdiction.TR,
                ExamFamily.YKS,
                "YKS TYT Mathematics",
                "YKS TYT Matematik",
                ExamStatus.ACTIVE,
                40,
                65,
                ScoringFormulaType.CORRECT_MINUS_QUARTER_WRONG,
                new BigDecimal("0.25"),
                new BigDecimal("-10.00"),
                new BigDecimal("40.00"),
                NavigationPolicy.FREE_JUMP,
                "c1cb8c26245fdee1b53c164a94e35a6cecbde8bae6ece948e60f577df53e1391",
                FIXED_NOW.minusSeconds(86400),
                null,
                null,
                List.of(new ExamSectionSpec("TYT_MATH", "TYT Mathematics", "TYT Matematik", 40, 65, 1)),
                new ScoringParameters(null, null, 1, null, null, List.of()),
                List.of(w1),
                false,
                FIXED_NOW.minusSeconds(86400),
                FIXED_NOW.minusSeconds(86400)
        );
    }

    static final class InMemoryCandidateProfileRepository implements CandidateProfileRepository {
        private final Map<UUID, CandidateProfileRecord> profilesByUser = new HashMap<>();
        private final List<CandidateHorizonHistoryEntry> historyEntries = new ArrayList<>();
        private final Map<UUID, SyllabusUploadRecord> uploadsById = new HashMap<>();

        @Override
        public Optional<CandidateProfileRecord> findProfileByUserId(UUID userId) {
            return Optional.ofNullable(profilesByUser.get(userId));
        }

        @Override
        public Optional<CandidateProfileRecord> findProfileByUserAndIdempotencyKey(UUID userId, String key) {
            CandidateProfileRecord p = profilesByUser.get(userId);
            if (p != null && key.equals(p.lastIdempotencyKey())) {
                return Optional.of(p);
            }
            return Optional.empty();
        }

        @Override
        public CandidateProfileRecord saveProfileAndRecordHistory(
                CandidateProfileRecord profile,
                CandidateHorizonHistoryEntry historyEntry,
                boolean insertNewProfile
        ) {
            profilesByUser.put(profile.userId(), profile);
            historyEntries.add(0, historyEntry);
            return profile;
        }

        @Override
        public List<CandidateHorizonHistoryEntry> findHorizonHistoryByUserId(UUID userId, int limit) {
            CandidateProfileRecord p = profilesByUser.get(userId);
            if (p == null) {
                return List.of();
            }
            return historyEntries.stream()
                    .filter(h -> h.profileId().equals(p.profileId()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<SyllabusUploadRecord> findSyllabusUploadById(UUID uploadId) {
            return Optional.ofNullable(uploadsById.get(uploadId));
        }

        @Override
        public Optional<SyllabusUploadRecord> findSyllabusUploadByUserAndHash(UUID userId, String imageHash) {
            return uploadsById.values().stream()
                    .filter(u -> u.userId().equals(userId) && u.imageHash().equals(imageHash))
                    .findFirst();
        }

        @Override
        public Optional<SyllabusUploadRecord> findSyllabusUploadByUserAndIdempotencyKey(UUID userId, String key) {
            return uploadsById.values().stream()
                    .filter(u -> u.userId().equals(userId) && key.equals(u.lastIdempotencyKey()))
                    .findFirst();
        }

        @Override
        public int countNonCachedSyllabusUploadsSince(UUID userId, Instant cutoff) {
            return (int) uploadsById.values().stream()
                    .filter(u -> u.userId().equals(userId) && !u.deduplicatedFromCache() && !u.createdAt().isBefore(cutoff))
                    .count();
        }

        @Override
        public SyllabusUploadRecord insertSyllabusUpload(SyllabusUploadRecord record) {
            uploadsById.put(record.uploadId(), record);
            return record;
        }

        @Override
        public SyllabusUploadRecord confirmSyllabusUpload(
                UUID uploadId,
                UUID userId,
                List<ParsedSyllabusTopicItem> updatedTopics,
                Instant confirmedAt,
                Instant updatedAt
        ) {
            SyllabusUploadRecord old = uploadsById.get(uploadId);
            assertNotNull(old);
            SyllabusUploadRecord updated = new SyllabusUploadRecord(
                    old.uploadId(), old.userId(), old.examDefinitionId(), old.examCode(), old.examVersion(),
                    old.imageHash(), old.mimeType(), old.byteSize(), updatedTopics,
                    com.verifiedai.exam.domain.CandidateProfileModels.SyllabusUploadStatus.USER_CONFIRMED,
                    old.deduplicatedFromCache(), old.llmTokensUsed(), old.lastIdempotencyKey(), old.schemaVersion(),
                    false, old.expiresAt(), confirmedAt, old.createdAt(), updatedAt
            );
            uploadsById.put(uploadId, updated);
            return updated;
        }

        @Override
        public List<SyllabusUploadRecord> findSyllabusUploadsByUserId(UUID userId, int limit) {
            return uploadsById.values().stream()
                    .filter(u -> u.userId().equals(userId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public int deleteAllCandidateDataForUser(UUID userId) {
            profilesByUser.remove(userId);
            uploadsById.values().removeIf(u -> u.userId().equals(userId));
            return 1;
        }

        @Override
        public int purgeExpiredSyllabusUploads(Instant cutoff) {
            int before = uploadsById.size();
            uploadsById.values().removeIf(u -> u.expiresAt().isBefore(cutoff));
            return before - uploadsById.size();
        }
    }
}
