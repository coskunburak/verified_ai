package com.verifiedai.problem.application;

import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.problem.domain.model.ProblemParseSource;
import com.verifiedai.problem.domain.model.ProblemParseSupportStatus;
import com.verifiedai.problem.domain.model.ProblemSessionStatus;
import com.verifiedai.problem.infrastructure.persistence.ProblemParseJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemParseJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemSessionJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemSessionJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.RecognitionEvidenceJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.RecognitionEvidenceJpaRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProblemParseCorrectionApplicationService {
    static final String CORRECTION_SCHEMA_VERSION = "parse-correction-v1";
    private static final int MAX_CORRECTION_BYTES = 32_768;
    private static final int MAX_CORRECTIONS_PER_SESSION_PER_HOUR = 20;

    private final ProblemSessionJpaRepository sessionRepository;
    private final ProblemParseJpaRepository parseRepository;
    private final RecognitionEvidenceJpaRepository evidenceRepository;
    private final CapabilityAccessPolicy capabilityAccessPolicy;
    private final ProblemParseDocumentValidator documentValidator;
    private final ProblemParseCorrectionRequestHash requestHash;
    private final ProblemParseSelectionPolicy selectionPolicy;
    private final ProblemParseCorrectionDiff correctionDiff;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final ProblemParseCorrectionMetrics metrics;
    private final ProblemSessionLifecyclePolicy lifecyclePolicy;
    private final ProblemSessionMetrics sessionMetrics;
    private final TransactionTemplate transactionTemplate;

    @SuppressWarnings("ParameterNumber")
    ProblemParseCorrectionApplicationService(
        ProblemSessionJpaRepository sessionRepository,
        ProblemParseJpaRepository parseRepository,
        RecognitionEvidenceJpaRepository evidenceRepository,
        CapabilityAccessPolicy capabilityAccessPolicy,
        ProblemParseDocumentValidator documentValidator,
        ProblemParseCorrectionRequestHash requestHash,
        ProblemParseSelectionPolicy selectionPolicy,
        ProblemParseCorrectionDiff correctionDiff,
        JdbcTemplate jdbcTemplate,
        Clock clock,
        ProblemParseCorrectionMetrics metrics,
        ProblemSessionLifecyclePolicy lifecyclePolicy,
        ProblemSessionMetrics sessionMetrics,
        TransactionTemplate transactionTemplate
    ) {
        this.sessionRepository = sessionRepository;
        this.parseRepository = parseRepository;
        this.evidenceRepository = evidenceRepository;
        this.capabilityAccessPolicy = capabilityAccessPolicy;
        this.documentValidator = documentValidator;
        this.requestHash = requestHash;
        this.selectionPolicy = selectionPolicy;
        this.correctionDiff = correctionDiff;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.metrics = metrics;
        this.lifecyclePolicy = lifecyclePolicy;
        this.sessionMetrics = sessionMetrics;
        this.transactionTemplate = transactionTemplate;
    }

    public ProblemParseReviewResult getParseReview(UUID userId, UUID problemSessionId) {
        requireActiveAccount(userId);
        capabilityAccessPolicy.requireBasicSolve(userId);
        ProblemSessionJpaEntity session = sessionRepository.findByIdAndUserId(problemSessionId, userId)
            .orElseThrow(() -> concealedSession());
        ProblemParseJpaEntity current = currentParse(session, userId, problemSessionId);
        long revisionCount = parseRepository.countByProblemSessionIdAndUserId(problemSessionId, userId);
        return new ProblemParseReviewResult(
            problemSessionId,
            new ProblemParseReviewResult.CurrentParse(
                current.id(),
                current.revision(),
                current.source(),
                current.supportStatus(),
                current.reviewRequired(),
                current.normalizedProblemJson(),
                current.createdAt()
            ),
            revisionCount,
            canCorrect(current)
        );
    }

    public ProblemParseCorrectionResult createCorrection(ProblemParseCorrectionCommand command) {
        metrics.request();
        requireActiveAccount(command.userId());
        capabilityAccessPolicy.requireBasicSolve(command.userId());
        requireIdempotencyKey(command.idempotencyKey());
        requireBase(command);
        requireCorrectionSize(command.correctedProblemJson());
        String reason = correctionReason(command.correctionReason());
        try {
            String hash = hash(command, reason);
            ProblemParseCorrectionResult replay = idempotentReplay(command, hash);
            if (replay != null) {
                return replay;
            }
            return transactionTemplate.execute(status -> {
                ProblemParseCorrectionResult replayInsideTransaction = idempotentReplay(command, hash);
                if (replayInsideTransaction != null) {
                    return replayInsideTransaction;
                }
                ProblemSessionJpaEntity session = sessionRepository
                    .findByIdAndUserIdForUpdate(command.problemSessionId(), command.userId())
                    .orElseThrow(() -> concealedSession());
                enforceCorrectionRate(command.userId(), command.problemSessionId());
                ProblemParseJpaEntity current = currentParse(session, command.userId(), command.problemSessionId());
                if (!canCorrect(current)) {
                    metrics.failure("not_allowed");
                    throw problem(
                        HttpStatus.CONFLICT,
                        ApiErrorCode.PARSE_CORRECTION_NOT_ALLOWED,
                        "Current parse cannot be corrected",
                        true,
                        "REPARSE"
                    );
                }
                if (!current.id().equals(command.baseParseId()) || current.revision() != command.baseRevision()) {
                    metrics.conflict();
                    throw problem(
                        HttpStatus.CONFLICT,
                        ApiErrorCode.PARSE_REVISION_CONFLICT,
                        "Problem parse changed while editing",
                        true,
                        "REVIEW_LATEST"
                    );
                }
                RecognitionEvidenceJpaEntity evidence = evidenceRepository
                    .findByIdAndUserId(current.recognitionEvidenceId(), command.userId())
                    .filter(candidate -> candidate.problemSessionId().equals(command.problemSessionId()))
                    .filter(candidate -> candidate.revision() == current.recognitionEvidenceRevision())
                    .orElseThrow(() -> problem(
                        HttpStatus.CONFLICT,
                        ApiErrorCode.PROBLEM_PARSE_NOT_FOUND,
                        "Recognition evidence for the selected parse is unavailable",
                        true,
                        "RECOGNIZE"
                    ));
                NormalizedProblemParse normalized = documentValidator.validateUserCorrection(
                    command.correctedProblemJson(),
                    current.schemaVersion(),
                    evidence
                );
                Set<ProblemParseCorrectionDiff.Category> changedFields = correctionDiff.categories(
                    current.normalizedProblemJson(),
                    normalized.normalizedProblemJson()
                );
                if (changedFields.isEmpty()) {
                    metrics.invalid("unchanged");
                    throw problem(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.PARSE_CORRECTION_INVALID,
                        "Correction did not change editable parse fields",
                        true,
                        "EDIT"
                    );
                }
                Instant now = clock.instant();
                ProblemParseJpaEntity created = ProblemParseJpaEntity.fromUserCorrection(
                    UUID.randomUUID(),
                    current.id(),
                    command.userId(),
                    command.problemSessionId(),
                    current.recognitionEvidenceId(),
                    current.recognitionEvidenceRevision(),
                    parseRepository.maxRevision(command.problemSessionId()) + 1,
                    normalized.supportStatus(),
                    normalized.unsupportedReason(),
                    normalized.reviewRequired(),
                    current.schemaVersion(),
                    normalized.normalizedProblemJson(),
                    command.idempotencyKey().trim(),
                    hash,
                    reason,
                    correctionDiff.toJson(changedFields),
                    CORRECTION_SCHEMA_VERSION,
                    now
                );
                ProblemParseJpaEntity saved = parseRepository.saveAndFlush(created);
                if (selectionPolicy.shouldSelectUserCorrection()) {
                    session.selectParse(saved.id(), now);
                    if (!ProblemParseSupportStatus.UNSUPPORTED.name().equals(normalized.supportStatus())) {
                        transition(
                            session,
                            normalized.reviewRequired() ? ProblemSessionStatus.REVIEW_REQUIRED : ProblemSessionStatus.PARSED,
                            now
                        );
                    }
                    metrics.selectionChanged(ProblemParseSource.USER.name());
                }
                metrics.success(reason);
                return resultFor(saved, true);
            });
        } catch (ProblemParseValidationException exception) {
            metrics.invalid(exception.failure().name().toLowerCase());
            if (exception.failure() == ProblemParseValidationFailure.SCHEMA) {
                throw problem(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.PARSE_CORRECTION_SCHEMA_INVALID,
                    exception.getMessage(),
                    true,
                    "EDIT"
                );
            }
            throw problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.PARSE_CORRECTION_SEMANTIC_INVALID,
                exception.getMessage(),
                true,
                "EDIT"
            );
        }
    }

    public List<ProblemParseRevisionSummary> getRevisionHistory(UUID userId, UUID problemSessionId) {
        requireActiveAccount(userId);
        capabilityAccessPolicy.requireBasicSolve(userId);
        ProblemSessionJpaEntity session = sessionRepository.findByIdAndUserId(problemSessionId, userId)
            .orElseThrow(() -> concealedSession());
        UUID selectedParseId = session.currentParseId();
        return parseRepository.findAllByProblemSessionIdAndUserIdOrderByRevisionDesc(problemSessionId, userId)
            .stream()
            .map(parse -> new ProblemParseRevisionSummary(
                parse.id(),
                parse.revision(),
                parse.source(),
                parse.parentParseId(),
                parse.id().equals(selectedParseId),
                parse.supportStatus(),
                parse.reviewRequired(),
                parse.correctionReason(),
                correctionDiff.fromJson(parse.correctedFieldsJson()),
                parse.createdAt()
            ))
            .toList();
    }

    private ProblemParseCorrectionResult idempotentReplay(ProblemParseCorrectionCommand command, String hash) {
        return parseRepository
            .findByCorrectionIdempotencyKeyAndUserIdAndProblemSessionId(
                command.idempotencyKey().trim(),
                command.userId(),
                command.problemSessionId()
            )
            .map(existing -> {
                if (!hash.equals(existing.correctionRequestHash())) {
                    metrics.conflict();
                    throw problem(
                        HttpStatus.CONFLICT,
                        ApiErrorCode.PARSE_CORRECTION_IDEMPOTENCY_CONFLICT,
                        "Idempotency-Key was reused with different correction content",
                        false,
                        "RETRY_WITH_NEW_KEY"
                    );
                }
                metrics.idempotentReplay();
                UUID selectedParseId = sessionRepository
                    .findByIdAndUserId(command.problemSessionId(), command.userId())
                    .map(ProblemSessionJpaEntity::currentParseId)
                    .orElse(null);
                return resultFor(existing, existing.id().equals(selectedParseId));
            })
            .orElse(null);
    }

    private ProblemParseJpaEntity currentParse(ProblemSessionJpaEntity session, UUID userId, UUID problemSessionId) {
        UUID selectedParseId = session.currentParseId();
        if (selectedParseId == null) {
            throw problem(
                HttpStatus.CONFLICT,
                ApiErrorCode.PROBLEM_PARSE_NOT_FOUND,
                "No parse is selected",
                true,
                "PARSE"
            );
        }
        return parseRepository.findByIdAndUserIdAndProblemSessionId(selectedParseId, userId, problemSessionId)
            .orElseThrow(() -> problem(
                HttpStatus.CONFLICT,
                ApiErrorCode.PROBLEM_PARSE_NOT_FOUND,
                "Selected parse was not found",
                true,
                "PARSE"
            ));
    }

    private ProblemParseCorrectionResult resultFor(ProblemParseJpaEntity parse, boolean selected) {
        return new ProblemParseCorrectionResult(
            parse.problemSessionId(),
            parse.id(),
            parse.revision(),
            parse.source(),
            parse.parentParseId(),
            selected,
            parse.supportStatus(),
            parse.reviewRequired(),
            true,
            parse.createdAt()
        );
    }

    private void enforceCorrectionRate(UUID userId, UUID problemSessionId) {
        long count = parseRepository.countByProblemSessionIdAndUserIdAndSourceAndCreatedAtGreaterThanEqual(
            problemSessionId,
            userId,
            ProblemParseSource.USER.name(),
            clock.instant().minus(Duration.ofHours(1))
        );
        if (count >= MAX_CORRECTIONS_PER_SESSION_PER_HOUR) {
            metrics.failure("rate_limited");
            throw problem(
                HttpStatus.TOO_MANY_REQUESTS,
                ApiErrorCode.RATE_LIMIT_EXCEEDED,
                "Too many parse corrections",
                true,
                "RETRY_LATER"
            );
        }
    }

    private String hash(ProblemParseCorrectionCommand command, String reason) {
        return requestHash.hash(
            command.problemSessionId(),
            command.baseParseId(),
            command.baseRevision(),
            reason,
            CORRECTION_SCHEMA_VERSION,
            command.correctedProblemJson()
        );
    }

    private static boolean canCorrect(ProblemParseJpaEntity parse) {
        return !ProblemParseSupportStatus.UNSUPPORTED.name().equals(parse.supportStatus());
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                "Idempotency-Key is required",
                true,
                "RETRY"
            );
        }
    }

    private static void requireBase(ProblemParseCorrectionCommand command) {
        if (command.baseParseId() == null || command.baseRevision() < 1) {
            throw problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.PARSE_CORRECTION_INVALID,
                "Base parse id and revision are required",
                true,
                "EDIT"
            );
        }
    }

    private static void requireCorrectionSize(String correctedProblemJson) {
        if (correctedProblemJson == null || correctedProblemJson.isBlank()) {
            throw problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.PARSE_CORRECTION_SCHEMA_INVALID,
                "Corrected problem document is required",
                true,
                "EDIT"
            );
        }
        if (correctedProblemJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_CORRECTION_BYTES) {
            throw problem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                ApiErrorCode.PARSE_CORRECTION_TOO_LARGE,
                "Corrected problem document is too large",
                true,
                "EDIT"
            );
        }
    }

    private static String correctionReason(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            return null;
        }
        try {
            return ProblemParseCorrectionReason.valueOf(rawReason.trim()).name();
        } catch (RuntimeException exception) {
            throw problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.PARSE_CORRECTION_INVALID,
                "Correction reason is not supported",
                true,
                "EDIT"
            );
        }
    }

    private void requireActiveAccount(UUID userId) {
        String userStatus = jdbcTemplate.query(
            "select status from users where id = ?",
            preparedStatement -> preparedStatement.setObject(1, userId),
            resultSet -> resultSet.next() ? resultSet.getString("status") : null
        );
        if (!"ACTIVE".equals(userStatus)) {
            throw problem(HttpStatus.FORBIDDEN, ApiErrorCode.ACCOUNT_NOT_ACTIVE, "Account is not active", false, "SIGN_IN");
        }
    }

    private void transition(ProblemSessionJpaEntity session, ProblemSessionStatus target, Instant now) {
        ProblemSessionStatus current = ProblemSessionStatus.valueOf(session.status());
        if (current == target) {
            return;
        }
        if (current == ProblemSessionStatus.ASSET_UPLOADED
            && (target == ProblemSessionStatus.PARSED || target == ProblemSessionStatus.REVIEW_REQUIRED)) {
            transition(session, ProblemSessionStatus.PARSING, now);
            current = ProblemSessionStatus.valueOf(session.status());
        }
        lifecyclePolicy.requireTransition(current, target);
        if (target == ProblemSessionStatus.PARSED) {
            session.markParsed(now);
        } else if (target == ProblemSessionStatus.REVIEW_REQUIRED) {
            session.markReviewRequired(now);
        } else if (target == ProblemSessionStatus.PARSING) {
            session.markParsing(now);
        }
        sessionMetrics.lifecycleTransition(current.name(), target.name());
    }

    private static ApiProblemException concealedSession() {
        return problem(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.RESOURCE_FORBIDDEN,
            "Problem session was not found",
            false,
            "RETRY"
        );
    }

    private static ApiProblemException problem(
        HttpStatus status,
        ApiErrorCode code,
        String title,
        boolean recoverable,
        String userAction
    ) {
        return new ApiProblemException(status, code, title, recoverable, userAction);
    }
}
