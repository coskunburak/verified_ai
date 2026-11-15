package com.verifiedai.exam.application;

import com.verifiedai.exam.domain.CandidatePaceCalculator;
import com.verifiedai.exam.domain.CandidateProfileModels;
import com.verifiedai.exam.domain.CandidateProfileModels.AccessibilityAccommodations;
import com.verifiedai.exam.domain.CandidateProfileModels.AdjustedExamSection;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateHorizonHistoryEntry;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileHistoryResponse;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileRecord;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileResponse;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileUpsertRequest;
import com.verifiedai.exam.domain.CandidateProfileModels.PacingAssessment;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.StudentExamTargetResponse;
import com.verifiedai.exam.domain.ExamModels.UpsertStudentExamTargetRequest;
import com.verifiedai.exam.events.CandidateGoalUpdatedEvent;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateProfileService {

    private static final Logger log = LoggerFactory.getLogger(CandidateProfileService.class);
    private static final int DEFAULT_HISTORY_LIMIT = 50;

    private final CandidateProfileRepository profileRepository;
    private final ExamRepository examRepository;
    private final StudentExamTargetService studentExamTargetService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final boolean candidateProfileEnabled;
    private final Counter profileUpsertedCounter;
    private final Counter accommodationUpdatedCounter;
    private final Counter feasibilityWarningCounter;

    @Autowired
    public CandidateProfileService(
            CandidateProfileRepository profileRepository,
            ExamRepository examRepository,
            StudentExamTargetService studentExamTargetService,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            Clock clock,
            @Value("${verifiedai.exam.candidate-profile-enabled:false}") boolean candidateProfileEnabled
    ) {
        this(
                profileRepository,
                examRepository,
                studentExamTargetService,
                eventPublisher,
                meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new),
                clock,
                candidateProfileEnabled
        );
    }

    public CandidateProfileService(
            CandidateProfileRepository profileRepository,
            ExamRepository examRepository,
            StudentExamTargetService studentExamTargetService,
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry,
            Clock clock,
            boolean candidateProfileEnabled
    ) {
        this.profileRepository = Objects.requireNonNull(profileRepository);
        this.examRepository = Objects.requireNonNull(examRepository);
        this.studentExamTargetService = Objects.requireNonNull(studentExamTargetService);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
        this.candidateProfileEnabled = candidateProfileEnabled;
        this.profileUpsertedCounter = meterRegistry.counter("verifiedai_exam_candidate_profile_upserted_total");
        this.accommodationUpdatedCounter = meterRegistry.counter("verifiedai_exam_accommodation_updated_total");
        this.feasibilityWarningCounter = meterRegistry.counter("verifiedai_exam_feasibility_warning_total");
    }

    public record UpsertResult(CandidateProfileResponse response, boolean created) {
    }

    @Transactional
    public UpsertResult upsertProfile(
            UUID authenticatedUserId,
            CandidateProfileUpsertRequest request,
            String headerIdempotencyKey
    ) {
        requireEnabled();
        if (request == null || request.examCode() == null || request.examCode().isBlank()) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.CANDIDATE_STUDY_BUDGET_INVALID,
                    "Exam code is required",
                    true,
                    "Select an exam definition from the catalog before saving your candidate profile."
            );
        }
        if (request.schemaVersion() != null
                && !request.schemaVersion().isBlank()
                && !CandidateProfileModels.CANDIDATE_PROFILE_REQUEST_SCHEMA_VERSION.equals(request.schemaVersion())) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.CANDIDATE_STUDY_BUDGET_INVALID,
                    "Unsupported candidate profile request schema version",
                    true,
                    "Use schemaVersion 'candidate-profile-request-v1'."
            );
        }

        String effectiveIdempotencyKey = resolveIdempotencyKey(request.idempotencyKey(), headerIdempotencyKey);
        if (effectiveIdempotencyKey != null) {
            Optional<CandidateProfileRecord> replayOpt =
                    profileRepository.findProfileByUserAndIdempotencyKey(authenticatedUserId, effectiveIdempotencyKey);
            if (replayOpt.isPresent()) {
                CandidateProfileRecord replay = replayOpt.get();
                ExamDefinition pinnedExam = resolveExamDefinition(replay.activeExamCode(), replay.pinnedExamVersion());
                return new UpsertResult(toResponse(replay, pinnedExam, true), false);
            }
        }

        ExamDefinition exam = resolveExamDefinition(request.examCode().trim(), request.pinnedExamVersion());
        LocalDate today = LocalDate.now(clock);
        CandidatePaceCalculator.validateTargetDate(request.targetDate(), today);
        CandidatePaceCalculator.validateStudyBudget(request.dailyStudyMinutes(), request.weeklyStudyMinutes());
        AccessibilityAccommodations accommodations =
                CandidatePaceCalculator.normalizeAndValidateAccommodations(request.accommodations());

        if (request.targetScore() == null
                || request.targetScore().compareTo(exam.minScore()) < 0
                || request.targetScore().compareTo(exam.maxScore()) > 0) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_TARGET_SCORE_OUT_OF_RANGE,
                    "Target score is outside exam bounds",
                    true,
                    "Enter a target score between " + exam.minScore() + " and " + exam.maxScore() + "."
            );
        }

        Optional<CandidateProfileRecord> existingOpt = profileRepository.findProfileByUserId(authenticatedUserId);
        BigDecimal baselineMastery = CandidatePaceCalculator.normalizeAndValidateBaselineMastery(
                request.baselineMastery(),
                existingOpt.map(CandidateProfileRecord::baselineMastery).orElse(null)
        );

        PacingAssessment pacing = CandidatePaceCalculator.assessPacing(
                exam,
                today,
                request.targetDate(),
                request.dailyStudyMinutes(),
                request.weeklyStudyMinutes(),
                baselineMastery
        );

        String targetSyncIdempotencyKey = effectiveIdempotencyKey != null
                ? "profile-sync:" + effectiveIdempotencyKey
                : "profile-sync:" + UUID.randomUUID();

        UUID syncedTargetId = synchronizePrimaryExamTarget(
                authenticatedUserId,
                exam,
                request.targetScore(),
                request.targetDate(),
                targetSyncIdempotencyKey
        );

        Instant now = Instant.now(clock);
        boolean insertNew = existingOpt.isEmpty();
        UUID profileId = existingOpt.map(CandidateProfileRecord::profileId).orElseGet(UUID::randomUUID);
        Instant createdAt = existingOpt.map(CandidateProfileRecord::createdAt).orElse(now);

        CandidateProfileRecord savedRecord = new CandidateProfileRecord(
                profileId,
                authenticatedUserId,
                exam.id(),
                exam.examCode(),
                exam.examVersion(),
                syncedTargetId,
                exam.jurisdiction(),
                request.targetDate(),
                request.targetScore(),
                request.dailyStudyMinutes(),
                request.weeklyStudyMinutes(),
                accommodations,
                baselineMastery,
                pacing.requiredDailyMinutes(),
                pacing.feasibilityStatus(),
                effectiveIdempotencyKey,
                CandidateProfileModels.CANDIDATE_PROFILE_SCHEMA_VERSION,
                false,
                createdAt,
                now
        );

        String changeReason = resolveChangeReason(request.changeReason(), existingOpt.orElse(null), savedRecord);
        CandidateHorizonHistoryEntry historyEntry = new CandidateHorizonHistoryEntry(
                UUID.randomUUID(),
                profileId,
                exam.examCode(),
                exam.examVersion(),
                existingOpt.map(CandidateProfileRecord::targetDate).orElse(null),
                savedRecord.targetDate(),
                existingOpt.map(CandidateProfileRecord::dailyStudyMinutes).orElse(null),
                savedRecord.dailyStudyMinutes(),
                existingOpt.map(CandidateProfileRecord::weeklyStudyMinutes).orElse(null),
                savedRecord.weeklyStudyMinutes(),
                existingOpt.map(CandidateProfileRecord::targetScore).orElse(null),
                savedRecord.targetScore(),
                accommodations.extraTimeMultiplier(),
                pacing.requiredDailyMinutes(),
                pacing.feasibilityStatus(),
                changeReason,
                now
        );

        CandidateProfileRecord persisted = profileRepository.saveProfileAndRecordHistory(savedRecord, historyEntry, insertNew);

        profileUpsertedCounter.increment();
        if (hasAccommodationChange(existingOpt.map(CandidateProfileRecord::accommodations).orElse(null), accommodations)) {
            accommodationUpdatedCounter.increment();
        }
        if (pacing.feasibilityStatus() == CandidateProfileModels.FeasibilityStatus.AT_RISK
                || pacing.feasibilityStatus() == CandidateProfileModels.FeasibilityStatus.UNREALISTIC) {
            feasibilityWarningCounter.increment();
        }

        eventPublisher.publishEvent(new CandidateGoalUpdatedEvent(
                authenticatedUserId,
                persisted.profileId(),
                persisted.activeExamCode(),
                persisted.pinnedExamVersion(),
                persisted.targetDate(),
                persisted.dailyStudyMinutes(),
                persisted.weeklyStudyMinutes(),
                persisted.targetScore(),
                accommodations.extraTimeMultiplier(),
                pacing.requiredDailyMinutes(),
                pacing.feasibilityStatus().name(),
                changeReason,
                now
        ));

        log.info(
                "Candidate profile persisted profileId={} examCode={} version={} feasibility={} requiredDaily={}",
                persisted.profileId(),
                persisted.activeExamCode(),
                persisted.pinnedExamVersion(),
                persisted.feasibilityStatus(),
                persisted.requiredDailyMinutes()
        );

        return new UpsertResult(toResponse(persisted, exam, pacing, false), insertNew);
    }

    @Transactional(readOnly = true)
    public CandidateProfileResponse getProfile(UUID authenticatedUserId) {
        requireEnabled();
        CandidateProfileRecord record = profileRepository.findProfileByUserId(authenticatedUserId)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.CANDIDATE_PROFILE_NOT_FOUND,
                        "Candidate profile not found",
                        true,
                        "Create your exam goal and study budget profile first."
                ));
        ExamDefinition exam = resolveExamDefinition(record.activeExamCode(), record.pinnedExamVersion());
        return toResponse(record, exam, false);
    }

    @Transactional(readOnly = true)
    public CandidateProfileHistoryResponse getProfileHistory(UUID authenticatedUserId) {
        requireEnabled();
        List<CandidateHorizonHistoryEntry> items =
                profileRepository.findHorizonHistoryByUserId(authenticatedUserId, DEFAULT_HISTORY_LIMIT);
        return new CandidateProfileHistoryResponse(
                CandidateProfileModels.CANDIDATE_PROFILE_HISTORY_SCHEMA_VERSION,
                authenticatedUserId,
                items,
                0,
                false
        );
    }

    private UUID synchronizePrimaryExamTarget(
            UUID userId,
            ExamDefinition exam,
            BigDecimal targetScore,
            LocalDate targetDate,
            String idempotencyKey
    ) {
        try {
            UpsertStudentExamTargetRequest targetRequest = new UpsertStudentExamTargetRequest(
                    "student-exam-target-request-v1",
                    exam.examCode(),
                    exam.examVersion(),
                    exam.blueprintHash(),
                    targetScore,
                    targetDate,
                    1,
                    Boolean.TRUE,
                    idempotencyKey
            );
            var outcome = studentExamTargetService.upsertTarget(userId, targetRequest);
            return outcome.response().targetId();
        } catch (ApiProblemException ex) {
            if (ex.code() == ApiErrorCode.EXAM_FEATURE_DISABLED) {
                log.debug("Target binding flag disabled while syncing candidate profile for examCode={}", exam.examCode());
                return null;
            }
            throw ex;
        }
    }

    private ExamDefinition resolveExamDefinition(String examCode, String pinnedExamVersion) {
        if (pinnedExamVersion != null && !pinnedExamVersion.isBlank()) {
            return examRepository.findByCodeAndVersion(examCode, pinnedExamVersion.trim())
                    .orElseThrow(() -> new ApiProblemException(
                            HttpStatus.NOT_FOUND,
                            ApiErrorCode.EXAM_VERSION_NOT_FOUND,
                            "Exam definition version not found",
                            true,
                            "Select an active exam version from the catalog."
                    ));
        }
        return examRepository.findLatestByCode(examCode, false)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.EXAM_DEFINITION_NOT_FOUND,
                        "Active exam definition not found",
                        true,
                        "Select a supported exam from the exam catalog."
                ));
    }

    private CandidateProfileResponse toResponse(
            CandidateProfileRecord record,
            ExamDefinition exam,
            boolean idempotentReplay
    ) {
        LocalDate today = LocalDate.now(clock);
        PacingAssessment pacing;
        if (record.targetDate().isAfter(today)) {
            pacing = CandidatePaceCalculator.assessPacing(
                    exam,
                    today,
                    record.targetDate(),
                    record.dailyStudyMinutes(),
                    record.weeklyStudyMinutes(),
                    record.baselineMastery()
            );
        } else {
            int effectiveDaily = Math.max(1, (int) Math.round(Math.min((double) record.dailyStudyMinutes(), record.weeklyStudyMinutes() / 7.0d)));
            pacing = new PacingAssessment(
                    0,
                    effectiveDaily,
                    record.requiredDailyMinutes(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    record.feasibilityStatus()
            );
        }
        return toResponse(record, exam, pacing, idempotentReplay);
    }

    private CandidateProfileResponse toResponse(
            CandidateProfileRecord record,
            ExamDefinition exam,
            PacingAssessment pacing,
            boolean idempotentReplay
    ) {
        List<AdjustedExamSection> adjustedSections = CandidatePaceCalculator.calculateAdjustedSections(
                exam,
                record.accommodations().extraTimeMultiplier()
        );
        return new CandidateProfileResponse(
                CandidateProfileModels.CANDIDATE_PROFILE_SCHEMA_VERSION,
                record.profileId(),
                record.userId(),
                record.activeExamDefinitionId(),
                record.activeExamCode(),
                record.pinnedExamVersion(),
                exam.blueprintHash(),
                record.activeTargetId(),
                record.jurisdiction(),
                record.targetDate(),
                record.targetScore(),
                record.dailyStudyMinutes(),
                record.weeklyStudyMinutes(),
                record.accommodations(),
                record.baselineMastery(),
                pacing.daysUntilExam(),
                pacing.effectiveDailyCapacityMinutes(),
                record.requiredDailyMinutes(),
                record.feasibilityStatus(),
                adjustedSections,
                idempotentReplay,
                0,
                false,
                record.createdAt(),
                record.updatedAt()
        );
    }

    private String resolveIdempotencyKey(String bodyKey, String headerKey) {
        String b = bodyKey == null ? null : bodyKey.trim();
        String h = headerKey == null ? null : headerKey.trim();
        if (b != null && b.isBlank()) {
            b = null;
        }
        if (h != null && h.isBlank()) {
            h = null;
        }
        if (b != null && h != null && !b.equals(h)) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.CANDIDATE_STUDY_BUDGET_INVALID,
                    "Conflicting Idempotency-Key header and payload idempotencyKey",
                    true,
                    "Send matching idempotency keys or supply the key in a single location."
            );
        }
        String effective = h != null ? h : b;
        if (effective != null && (effective.length() < 4 || effective.length() > 128)) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.CANDIDATE_STUDY_BUDGET_INVALID,
                    "Idempotency key length must be between 4 and 128 characters",
                    true,
                    "Provide a valid idempotency key between 4 and 128 characters."
            );
        }
        return effective;
    }

    private static String resolveChangeReason(
            String requestedReason,
            CandidateProfileRecord previous,
            CandidateProfileRecord updated
    ) {
        if (requestedReason != null && !requestedReason.isBlank()) {
            return requestedReason.trim();
        }
        if (previous == null) {
            return "INITIAL_SETUP";
        }
        if (!previous.activeExamCode().equals(updated.activeExamCode())) {
            return "EXAM_SWITCH";
        }
        if (!previous.targetDate().equals(updated.targetDate())) {
            return "EXAM_DATE_SHIFT";
        }
        if (previous.dailyStudyMinutes() != updated.dailyStudyMinutes()
                || previous.weeklyStudyMinutes() != updated.weeklyStudyMinutes()) {
            return "PACE_CHANGE";
        }
        return "PROFILE_UPDATE";
    }

    private static boolean hasAccommodationChange(
            AccessibilityAccommodations previous,
            AccessibilityAccommodations current
    ) {
        if (previous == null) {
            return current.extraTimeMultiplier().compareTo(new BigDecimal("1.00")) > 0
                    || current.highContrastEnabled()
                    || current.screenReaderOptimized()
                    || current.reducedMotionEnabled();
        }
        return previous.extraTimeMultiplier().compareTo(current.extraTimeMultiplier()) != 0
                || previous.highContrastEnabled() != current.highContrastEnabled()
                || previous.screenReaderOptimized() != current.screenReaderOptimized()
                || previous.reducedMotionEnabled() != current.reducedMotionEnabled();
    }

    private void requireEnabled() {
        if (!candidateProfileEnabled) {
            throw new ApiProblemException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.EXAM_FEATURE_DISABLED,
                    "Candidate profile feature is currently disabled",
                    true,
                    "Try again later once candidate profile configuration is enabled."
            );
        }
    }
}
