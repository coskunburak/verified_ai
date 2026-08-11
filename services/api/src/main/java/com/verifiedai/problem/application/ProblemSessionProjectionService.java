package com.verifiedai.problem.application;

import com.verifiedai.problem.domain.model.ProblemAssetDerivativeKind;
import com.verifiedai.problem.domain.model.ProblemAssetDerivativeStatus;
import com.verifiedai.problem.domain.model.ProblemAssetStatus;
import com.verifiedai.problem.infrastructure.persistence.CanonicalProblemJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.CanonicalProblemJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetDerivativeJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetDerivativeJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemClassificationJobJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemClassificationJobJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemClassificationJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemClassificationJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemParseJobJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemParseJobJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemParseJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemParseJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemSessionJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemSessionJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.RecognitionEvidenceJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.RecognitionEvidenceJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.RecognitionJobJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.RecognitionJobJpaRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemSessionProjectionService {
    private final ProblemSessionJpaRepository sessionRepository;
    private final ProblemAssetJpaRepository assetRepository;
    private final ProblemAssetDerivativeJpaRepository derivativeRepository;
    private final RecognitionJobJpaRepository recognitionJobRepository;
    private final RecognitionEvidenceJpaRepository recognitionEvidenceRepository;
    private final ProblemParseJobJpaRepository parseJobRepository;
    private final ProblemParseJpaRepository parseRepository;
    private final CanonicalProblemJpaRepository canonicalRepository;
    private final ProblemClassificationJobJpaRepository classificationJobRepository;
    private final ProblemClassificationJpaRepository classificationRepository;
    private final ProblemSessionRecoveryPlanner recoveryPlanner;
    private final JdbcTemplate jdbcTemplate;
    private final ProblemSessionMetrics metrics;

    @SuppressWarnings("ParameterNumber")
    ProblemSessionProjectionService(
        ProblemSessionJpaRepository sessionRepository,
        ProblemAssetJpaRepository assetRepository,
        ProblemAssetDerivativeJpaRepository derivativeRepository,
        RecognitionJobJpaRepository recognitionJobRepository,
        RecognitionEvidenceJpaRepository recognitionEvidenceRepository,
        ProblemParseJobJpaRepository parseJobRepository,
        ProblemParseJpaRepository parseRepository,
        CanonicalProblemJpaRepository canonicalRepository,
        ProblemClassificationJobJpaRepository classificationJobRepository,
        ProblemClassificationJpaRepository classificationRepository,
        ProblemSessionRecoveryPlanner recoveryPlanner,
        JdbcTemplate jdbcTemplate,
        ProblemSessionMetrics metrics
    ) {
        this.sessionRepository = sessionRepository;
        this.assetRepository = assetRepository;
        this.derivativeRepository = derivativeRepository;
        this.recognitionJobRepository = recognitionJobRepository;
        this.recognitionEvidenceRepository = recognitionEvidenceRepository;
        this.parseJobRepository = parseJobRepository;
        this.parseRepository = parseRepository;
        this.canonicalRepository = canonicalRepository;
        this.classificationJobRepository = classificationJobRepository;
        this.classificationRepository = classificationRepository;
        this.recoveryPlanner = recoveryPlanner;
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public ProblemSessionProjection detail(UUID userId, UUID sessionId) {
        requireActiveAccount(userId);
        ProblemSessionJpaEntity session = sessionRepository.findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> problem(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.PROBLEM_SESSION_NOT_FOUND,
                "Problem session was not found",
                false,
                "NONE"
            ));
        ProblemSessionProjection projection = projectAll(userId, List.of(session), true).get(0);
        return projection;
    }

    @Transactional(readOnly = true)
    public List<ProblemSessionProjection> projectAll(UUID userId, List<ProblemSessionJpaEntity> sessions, boolean failOnAmbiguous) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<UUID> sessionIds = sessions.stream().map(ProblemSessionJpaEntity::id).toList();
        Map<UUID, ProblemAssetJpaEntity> availableAssets = latestBySession(
            assetRepository.findByProblemSessionIdInAndUserIdAndStatusOrderByCreatedAtDesc(
                sessionIds,
                userId,
                ProblemAssetStatus.AVAILABLE.name()
            ),
            ProblemAssetJpaEntity::problemSessionId
        );
        Map<UUID, ProblemAssetDerivativeJpaEntity> derivatives = recognitionDerivatives(derivativeRepository
            .findByProblemSessionIdInAndUserIdOrderByCreatedAtDesc(sessionIds, userId));
        Map<UUID, RecognitionJobJpaEntity> recognitionJobs = latestBySession(
            recognitionJobRepository.findByProblemSessionIdInAndUserIdOrderByCreatedAtDesc(sessionIds, userId),
            RecognitionJobJpaEntity::problemSessionId
        );
        Map<UUID, RecognitionEvidenceJpaEntity> recognitionEvidence = latestBySession(
            recognitionEvidenceRepository.findByProblemSessionIdInAndUserIdOrderByCreatedAtDesc(sessionIds, userId),
            RecognitionEvidenceJpaEntity::problemSessionId
        );
        Map<UUID, ProblemParseJobJpaEntity> parseJobs = latestBySession(
            parseJobRepository.findByProblemSessionIdInAndUserIdOrderByCreatedAtDesc(sessionIds, userId),
            ProblemParseJobJpaEntity::problemSessionId
        );
        Set<UUID> acceptedParseSessionIds = new HashSet<>(parseRepository.findAcceptedParseSessionIds(userId, sessionIds));
        Map<UUID, ProblemParseJpaEntity> selectedParses = selectedParses(userId, sessions);
        Map<UUID, CanonicalProblemJpaEntity> canonicalProblems = canonicalProblems(userId, selectedParses);
        Map<UUID, ProblemClassificationJobJpaEntity> classificationJobs = classificationJobs(userId, canonicalProblems);
        Map<UUID, ProblemClassificationJpaEntity> classifications = classifications(userId, canonicalProblems);

        return sessions
            .stream()
            .map(session -> project(
                session,
                availableAssets.get(session.id()),
                derivatives.get(session.id()),
                recognitionJobs.get(session.id()),
                recognitionEvidence.get(session.id()),
                parseJobs.get(session.id()),
                selectedParses.get(session.id()),
                acceptedParseSessionIds.contains(session.id()),
                canonicalProblems.get(session.id()),
                classificationJobs.get(session.id()),
                classifications.get(session.id()),
                failOnAmbiguous
            ))
            .toList();
    }

    private ProblemSessionProjection project(
        ProblemSessionJpaEntity session,
        ProblemAssetJpaEntity availableAsset,
        ProblemAssetDerivativeJpaEntity derivative,
        RecognitionJobJpaEntity recognitionJob,
        RecognitionEvidenceJpaEntity recognitionEvidence,
        ProblemParseJobJpaEntity parseJob,
        ProblemParseJpaEntity selectedParse,
        boolean acceptedParseHistoryExists,
        CanonicalProblemJpaEntity canonicalProblem,
        ProblemClassificationJobJpaEntity classificationJob,
        ProblemClassificationJpaEntity classification,
        boolean failOnAmbiguous
    ) {
        ProblemSessionRecoveryPlan plan = recoveryPlanner.plan(new ProblemSessionRecoveryInputs(
            session,
            availableAsset,
            derivative,
            recognitionJob,
            recognitionEvidence,
            parseJob,
            selectedParse,
            acceptedParseHistoryExists,
            canonicalProblem,
            classificationJob,
            classification
        ));
        metrics.recoveryPlanned(plan.stage(), plan.nextAction());
        if (ApiErrorCode.PROBLEM_SESSION_LINEAGE_AMBIGUOUS.name().equals(plan.failureCode())) {
            metrics.ambiguousLineage();
            if (failOnAmbiguous) {
                throw problem(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.PROBLEM_SESSION_LINEAGE_AMBIGUOUS,
                    "Problem session selected parse lineage is ambiguous",
                    false,
                    "CONTACT_SUPPORT"
                );
            }
        }
        return new ProblemSessionProjection(
            session.id(),
            session.status(),
            plan.stage(),
            session.inputMode(),
            plan.nextAction(),
            plan.retryable(),
            plan.reviewRequired(),
            plan.failureCode(),
            selectedParseSummary(selectedParse),
            canonicalSummary(canonicalProblem),
            classificationSummary(classification),
            plan.activeJob(),
            session.createdAt(),
            session.updatedAt(),
            session.completedAt(),
            session.version()
        );
    }

    private Map<UUID, ProblemParseJpaEntity> selectedParses(UUID userId, List<ProblemSessionJpaEntity> sessions) {
        List<UUID> parseIds = sessions.stream()
            .map(ProblemSessionJpaEntity::currentParseId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (parseIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProblemParseJpaEntity> byParseId = new HashMap<>();
        parseRepository.findByIdInAndUserId(parseIds, userId)
            .forEach(parse -> byParseId.put(parse.id(), parse));
        Map<UUID, ProblemParseJpaEntity> bySessionId = new HashMap<>();
        sessions.forEach(session -> {
            ProblemParseJpaEntity parse = byParseId.get(session.currentParseId());
            if (parse != null && parse.problemSessionId().equals(session.id())) {
                bySessionId.put(session.id(), parse);
            }
        });
        return bySessionId;
    }

    private Map<UUID, CanonicalProblemJpaEntity> canonicalProblems(
        UUID userId,
        Map<UUID, ProblemParseJpaEntity> selectedParses
    ) {
        List<UUID> parseIds = selectedParses.values()
            .stream()
            .map(ProblemParseJpaEntity::id)
            .distinct()
            .toList();
        if (parseIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProblemParseJpaEntity> byParseId = new HashMap<>();
        selectedParses.values().forEach(parse -> byParseId.put(parse.id(), parse));
        Map<UUID, CanonicalProblemJpaEntity> bySessionId = new HashMap<>();
        canonicalRepository
            .findByProblemParseIdInAndUserIdAndSchemaVersionOrderByCanonicalRevisionDesc(
                parseIds,
                userId,
                CanonicalProblemBuilder.CANONICAL_SCHEMA_VERSION
            )
            .forEach(canonical -> {
                ProblemParseJpaEntity parse = byParseId.get(canonical.problemParseId());
                if (parse != null
                    && canonical.problemParseRevision() == parse.revision()
                    && canonical.problemSessionId().equals(parse.problemSessionId())) {
                    bySessionId.putIfAbsent(canonical.problemSessionId(), canonical);
                }
            });
        return bySessionId;
    }

    private Map<UUID, ProblemClassificationJobJpaEntity> classificationJobs(
        UUID userId,
        Map<UUID, CanonicalProblemJpaEntity> canonicalProblems
    ) {
        List<UUID> canonicalIds = canonicalProblems.values()
            .stream()
            .map(CanonicalProblemJpaEntity::id)
            .distinct()
            .toList();
        if (canonicalIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProblemClassificationJobJpaEntity> bySessionId = new HashMap<>();
        classificationJobRepository
            .findByCanonicalProblemIdInAndUserIdOrderByCreatedAtDesc(canonicalIds, userId)
            .forEach(job -> bySessionId.putIfAbsent(job.problemSessionId(), job));
        return bySessionId;
    }

    private Map<UUID, ProblemClassificationJpaEntity> classifications(
        UUID userId,
        Map<UUID, CanonicalProblemJpaEntity> canonicalProblems
    ) {
        List<UUID> canonicalIds = canonicalProblems.values()
            .stream()
            .map(CanonicalProblemJpaEntity::id)
            .distinct()
            .toList();
        if (canonicalIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProblemClassificationJpaEntity> bySessionId = new HashMap<>();
        classificationRepository
            .findByCanonicalProblemIdInAndUserIdOrderByRevisionDesc(canonicalIds, userId)
            .forEach(classification -> bySessionId.putIfAbsent(classification.problemSessionId(), classification));
        return bySessionId;
    }

    private Map<UUID, ProblemAssetDerivativeJpaEntity> recognitionDerivatives(List<ProblemAssetDerivativeJpaEntity> derivatives) {
        Map<UUID, ProblemAssetDerivativeJpaEntity> bySessionId = new HashMap<>();
        derivatives.forEach(derivative -> {
            if (ProblemAssetDerivativeKind.OCR_OPTIMIZED.name().equals(derivative.derivativeKind())) {
                boolean selectedReady = derivative.selectedForRecognition()
                    && ProblemAssetDerivativeStatus.READY.name().equals(derivative.status());
                if (selectedReady) {
                    bySessionId.put(derivative.problemSessionId(), derivative);
                } else {
                    bySessionId.putIfAbsent(derivative.problemSessionId(), derivative);
                }
            }
        });
        return bySessionId;
    }

    private ProblemSessionCurrentParseSummary selectedParseSummary(ProblemParseJpaEntity parse) {
        if (parse == null) {
            return null;
        }
        return new ProblemSessionCurrentParseSummary(
            parse.id(),
            parse.revision(),
            parse.source(),
            parse.supportStatus(),
            parse.reviewRequired()
        );
    }

    private ProblemSessionCanonicalSummary canonicalSummary(CanonicalProblemJpaEntity canonical) {
        if (canonical == null) {
            return null;
        }
        return new ProblemSessionCanonicalSummary(
            canonical.id(),
            canonical.canonicalRevision(),
            canonical.problemParseId(),
            canonical.problemParseRevision(),
            canonical.problemType(),
            canonical.taskType()
        );
    }

    private ProblemSessionClassificationSummary classificationSummary(ProblemClassificationJpaEntity classification) {
        if (classification == null) {
            return null;
        }
        return new ProblemSessionClassificationSummary(
            classification.id(),
            classification.revision(),
            classification.status(),
            classification.primarySkillId(),
            classification.difficulty(),
            classification.reviewReason()
        );
    }

    private void requireActiveAccount(UUID userId) {
        String status = jdbcTemplate.query(
            "select status from users where id = ?",
            preparedStatement -> preparedStatement.setObject(1, userId),
            resultSet -> resultSet.next() ? resultSet.getString("status") : null
        );
        if (!"ACTIVE".equals(status)) {
            throw problem(HttpStatus.FORBIDDEN, ApiErrorCode.ACCOUNT_NOT_ACTIVE, "Account is not active", false, "SIGN_IN");
        }
    }

    private static <T> Map<UUID, T> latestBySession(List<T> items, Function<T, UUID> sessionId) {
        Map<UUID, T> bySession = new HashMap<>();
        items.forEach(item -> bySession.putIfAbsent(sessionId.apply(item), item));
        return bySession;
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
