package com.verifiedai.exam.application;

import com.verifiedai.exam.domain.ExamBlueprintValidator;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.PinnedBlueprintSnapshot;
import com.verifiedai.exam.domain.ExamModels.ReadinessGapPreview;
import com.verifiedai.exam.domain.ExamModels.StudentExamTargetListResponse;
import com.verifiedai.exam.domain.ExamModels.StudentExamTargetRecord;
import com.verifiedai.exam.domain.ExamModels.StudentExamTargetResponse;
import com.verifiedai.exam.domain.ExamModels.TargetBindingOutcome;
import com.verifiedai.exam.domain.ExamModels.TargetStatus;
import com.verifiedai.exam.domain.ExamModels.UpsertStudentExamTargetRequest;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service managing learner exam target bindings, idempotency, immutable blueprint snapshots,
 * active-primary target superseding, and strict BOLA enforcement ($0.00 LLM cost).
 */
@Service
public class StudentExamTargetService {

    private static final Logger LOG = LoggerFactory.getLogger(StudentExamTargetService.class);

    public static final String TARGET_SCHEMA_VERSION = "student-exam-target-v1";
    public static final String TARGET_LIST_SCHEMA_VERSION = "student-exam-target-list-v1";
    public static final String READINESS_PREVIEW_SCHEMA_VERSION = "readiness-gap-preview-v1";
    private static final String ESTIMATION_BASIS = "BLUEPRINT_MIDPOINT_PRELIMINARY";

    private final ExamRepository repository;
    private final ExamBlueprintValidator blueprintValidator;
    private final Clock clock;
    private final JsonMapper jsonMapper;
    private final Counter targetBindingCounter;
    private final Counter validationFailureCounter;
    private final boolean targetBindingEnabled;

    @Autowired
    public StudentExamTargetService(
            ExamRepository repository,
            ExamBlueprintValidator blueprintValidator,
            MeterRegistry meterRegistry,
            Clock clock,
            @Value("${verifiedai.exam.target-binding-enabled:false}") boolean targetBindingEnabled
    ) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.blueprintValidator = Objects.requireNonNull(blueprintValidator, "blueprintValidator cannot be null");
        Objects.requireNonNull(meterRegistry, "meterRegistry cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.jsonMapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        this.targetBindingCounter = Counter.builder("verifiedai.exam.target.bindings")
                .description("Total number of student exam target bindings created or replayed")
                .register(meterRegistry);
        this.validationFailureCounter = Counter.builder("verifiedai.exam.validation.failures")
                .description("Total number of exam target validation failures")
                .register(meterRegistry);
        this.targetBindingEnabled = targetBindingEnabled;
    }

    @Transactional
    public TargetBindingOutcome upsertTarget(UUID authenticatedUserId, UpsertStudentExamTargetRequest request) {
        requireFeatureEnabled();
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        String examCode = request.examCode().trim().toUpperCase(Locale.ROOT);
        String requestedVersion = request.examVersion() != null && !request.examVersion().isBlank()
                ? request.examVersion().trim()
                : null;
        String idempotencyKey = request.idempotencyKey().trim();
        int priorityRank = request.priorityRank() != null ? request.priorityRank() : 1;
        boolean isPrimary = request.isPrimary() == null || request.isPrimary();

        if (request.targetDate().isBefore(LocalDate.now(clock).minusDays(1))) {
            validationFailureCounter.increment();
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "targetDate must not be in the past",
                    true,
                    "SELECT_FUTURE_TARGET_DATE"
            );
        }

        Optional<StudentExamTargetRecord> existingOpt =
                repository.findTargetByUserIdAndIdempotencyKey(authenticatedUserId, idempotencyKey);
        if (existingOpt.isPresent()) {
            return handleExistingIdempotencyRecord(
                    existingOpt.get(),
                    examCode,
                    requestedVersion,
                    request.expectedBlueprintHash(),
                    request.targetScore(),
                    request.targetDate(),
                    priorityRank,
                    isPrimary
            );
        }

        ExamDefinition definition = resolveDefinitionForBinding(examCode, requestedVersion);

        try {
            blueprintValidator.validateTargetBindingEligibility(
                    definition,
                    request.expectedBlueprintHash(),
                    request.targetScore()
            );
        } catch (ApiProblemException ex) {
            validationFailureCounter.increment();
            throw ex;
        }

        Instant now = clock.instant();
        PinnedBlueprintSnapshot snapshot = toPinnedSnapshot(definition);
        String snapshotJson = writeJson(snapshot);

        StudentExamTargetRecord candidate = new StudentExamTargetRecord(
                UUID.randomUUID(),
                authenticatedUserId,
                definition.id(),
                definition.examCode(),
                definition.examVersion(),
                definition.blueprintHash(),
                request.targetScore().setScale(2, RoundingMode.HALF_UP),
                request.targetDate(),
                priorityRank,
                TargetStatus.ACTIVE,
                isPrimary,
                idempotencyKey,
                snapshotJson,
                null,
                0,
                false,
                now,
                now
        );

        try {
            if (isPrimary) {
                repository.supersedeActivePrimaryTargets(authenticatedUserId, now);
            }
            StudentExamTargetRecord inserted = repository.insertTarget(candidate);
            targetBindingCounter.increment();
            LOG.info("exam.target.bound examCode={} examVersion={} isPrimary={}",
                    inserted.examCode(), inserted.examVersion(), inserted.isPrimary());
            return new TargetBindingOutcome(toResponse(inserted, snapshot, false), true);
        } catch (DataIntegrityViolationException ex) {
            Optional<StudentExamTargetRecord> concurrentOpt =
                    repository.findTargetByUserIdAndIdempotencyKey(authenticatedUserId, idempotencyKey);
            if (concurrentOpt.isPresent()) {
                return handleExistingIdempotencyRecord(
                        concurrentOpt.get(),
                        examCode,
                        requestedVersion,
                        request.expectedBlueprintHash(),
                        request.targetScore(),
                        request.targetDate(),
                        priorityRank,
                        isPrimary
                );
            }
            validationFailureCounter.increment();
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_VERSION_CONFLICT,
                    "Concurrent exam target conflict encountered; please retry with a fresh request",
                    true,
                    "RETRY_WITH_FRESH_STATE"
            );
        }
    }

    @Transactional(readOnly = true)
    public StudentExamTargetListResponse listTargets(UUID authenticatedUserId) {
        requireFeatureEnabled();
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId cannot be null");

        List<StudentExamTargetResponse> responses = repository.findTargetsByUserId(authenticatedUserId).stream()
                .map(record -> toResponse(record, readPinnedSnapshot(record.snapshotPayloadJson()), false))
                .toList();

        return new StudentExamTargetListResponse(
                TARGET_LIST_SCHEMA_VERSION,
                authenticatedUserId,
                responses,
                responses.size(),
                0,
                false
        );
    }

    @Transactional(readOnly = true)
    public StudentExamTargetResponse getTarget(UUID authenticatedUserId, UUID targetId) {
        requireFeatureEnabled();
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId cannot be null");
        Objects.requireNonNull(targetId, "targetId cannot be null");

        StudentExamTargetRecord record = repository.findTargetById(targetId)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.EXAM_TARGET_NOT_FOUND,
                        "Exam target not found for targetId=" + targetId,
                        false,
                        "SELECT_VALID_TARGET"
                ));

        if (!record.userId().equals(authenticatedUserId)) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_ACCESS_DENIED,
                    "Cross-learner access to student exam target is forbidden",
                    false,
                    "USE_OWN_LEARNER_ACCOUNT"
            );
        }

        return toResponse(record, readPinnedSnapshot(record.snapshotPayloadJson()), false);
    }

    private TargetBindingOutcome handleExistingIdempotencyRecord(
            StudentExamTargetRecord existing,
            String examCode,
            String requestedVersion,
            String expectedBlueprintHash,
            BigDecimal targetScore,
            LocalDate targetDate,
            int priorityRank,
            boolean isPrimary
    ) {
        boolean matchesVersion = requestedVersion == null || existing.examVersion().equals(requestedVersion);
        boolean matchesHash = expectedBlueprintHash == null || expectedBlueprintHash.isBlank()
                || existing.blueprintHash().equalsIgnoreCase(expectedBlueprintHash.trim());
        boolean samePayload = existing.examCode().equals(examCode)
                && matchesVersion
                && matchesHash
                && existing.targetScore().compareTo(targetScore) == 0
                && existing.targetDate().equals(targetDate)
                && existing.priorityRank() == priorityRank
                && existing.isPrimary() == isPrimary;

        if (!samePayload) {
            validationFailureCounter.increment();
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_VERSION_CONFLICT,
                    "Idempotency key already used with different exam target parameters",
                    true,
                    "USE_NEW_IDEMPOTENCY_KEY"
            );
        }

        targetBindingCounter.increment();
        PinnedBlueprintSnapshot snapshot = readPinnedSnapshot(existing.snapshotPayloadJson());
        return new TargetBindingOutcome(toResponse(existing, snapshot, true), false);
    }

    private ExamDefinition resolveDefinitionForBinding(String examCode, String requestedVersion) {
        if (requestedVersion != null) {
            return repository.findByCodeAndVersion(examCode, requestedVersion)
                    .orElseThrow(() -> {
                        if (repository.findLatestByCode(examCode, true).isEmpty()) {
                            return new ApiProblemException(
                                    HttpStatus.NOT_FOUND,
                                    ApiErrorCode.EXAM_DEFINITION_NOT_FOUND,
                                    "Exam definition not found for examCode=" + examCode,
                                    false,
                                    "SELECT_SUPPORTED_EXAM"
                            );
                        }
                        return new ApiProblemException(
                                HttpStatus.NOT_FOUND,
                                ApiErrorCode.EXAM_VERSION_NOT_FOUND,
                                "Exam version not found for examCode=" + examCode + " version=" + requestedVersion,
                                false,
                                "SELECT_VALID_VERSION"
                        );
                    });
        }

        return repository.findLatestByCode(examCode, false)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.EXAM_DEFINITION_NOT_FOUND,
                        "Active exam definition not found for examCode=" + examCode,
                        false,
                        "SELECT_SUPPORTED_EXAM"
                ));
    }

    private void requireFeatureEnabled() {
        if (!targetBindingEnabled) {
            throw new ApiProblemException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.EXAM_FEATURE_DISABLED,
                    "Student exam target binding feature is currently disabled",
                    true,
                    "TRY_AGAIN_LATER"
            );
        }
    }

    private StudentExamTargetResponse toResponse(
            StudentExamTargetRecord record,
            PinnedBlueprintSnapshot snapshot,
            boolean idempotentReplay
    ) {
        ReadinessGapPreview readinessPreview = computeReadinessGapPreview(
                snapshot.minScore(),
                snapshot.maxScore(),
                record.targetScore()
        );
        return new StudentExamTargetResponse(
                TARGET_SCHEMA_VERSION,
                record.targetId(),
                record.userId(),
                record.examDefinitionId(),
                record.examCode(),
                record.examVersion(),
                record.blueprintHash(),
                record.targetScore(),
                record.targetDate(),
                record.priorityRank(),
                record.targetStatus(),
                record.isPrimary(),
                record.idempotencyKey(),
                readinessPreview,
                snapshot,
                record.supersededAt(),
                idempotentReplay,
                0,
                false,
                record.createdAt(),
                record.updatedAt()
        );
    }

    static ReadinessGapPreview computeReadinessGapPreview(
            BigDecimal minScore,
            BigDecimal maxScore,
            BigDecimal targetScore
    ) {
        BigDecimal baselineFloor = minScore.compareTo(BigDecimal.ZERO) > 0 ? minScore : BigDecimal.ZERO;
        BigDecimal estimated = baselineFloor
                .add(maxScore.subtract(baselineFloor).multiply(new BigDecimal("0.50")))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal gapPoints = targetScore.subtract(estimated).setScale(2, RoundingMode.HALF_UP);
        return new ReadinessGapPreview(
                READINESS_PREVIEW_SCHEMA_VERSION,
                estimated,
                targetScore.setScale(2, RoundingMode.HALF_UP),
                gapPoints,
                ESTIMATION_BASIS,
                0,
                false
        );
    }

    private static PinnedBlueprintSnapshot toPinnedSnapshot(ExamDefinition definition) {
        return new PinnedBlueprintSnapshot(
                definition.id(),
                definition.examCode(),
                definition.examVersion(),
                definition.jurisdiction(),
                definition.examFamily(),
                definition.titleEn(),
                definition.titleTr(),
                definition.totalQuestions(),
                definition.durationMinutes(),
                definition.scoringFormulaType(),
                definition.wrongPenaltyFactor(),
                definition.minScore(),
                definition.maxScore(),
                definition.navigationPolicy(),
                definition.blueprintHash(),
                definition.effectiveFrom(),
                definition.sections(),
                definition.scoringParameters(),
                ExamCatalogService.toTopicWeightDtos(definition.topicWeights()),
                false
        );
    }

    private String writeJson(PinnedBlueprintSnapshot snapshot) {
        try {
            return jsonMapper.writeValueAsString(snapshot);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize pinned blueprint snapshot", ex);
        }
    }

    private PinnedBlueprintSnapshot readPinnedSnapshot(String rawJson) {
        try {
            return jsonMapper.readValue(rawJson, PinnedBlueprintSnapshot.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to deserialize pinned blueprint snapshot", ex);
        }
    }
}
