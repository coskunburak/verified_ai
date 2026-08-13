package com.verifiedai.problem.application.canonicalization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.problem.infrastructure.persistence.entity.CanonicalProblemJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemParseJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemSessionJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.repository.CanonicalProblemJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.repository.ProblemParseJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.repository.ProblemSessionJpaRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CanonicalProblemApplicationService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ProblemSessionJpaRepository sessionRepository;
    private final ProblemParseJpaRepository parseRepository;
    private final CanonicalProblemJpaRepository canonicalRepository;
    private final CapabilityAccessPolicy capabilityAccessPolicy;
    private final CanonicalProblemBuilder canonicalProblemBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final CanonicalProblemMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    @SuppressWarnings("ParameterNumber")
    CanonicalProblemApplicationService(
        ProblemSessionJpaRepository sessionRepository,
        ProblemParseJpaRepository parseRepository,
        CanonicalProblemJpaRepository canonicalRepository,
        CapabilityAccessPolicy capabilityAccessPolicy,
        CanonicalProblemBuilder canonicalProblemBuilder,
        JdbcTemplate jdbcTemplate,
        Clock clock,
        CanonicalProblemMetrics metrics,
        TransactionTemplate transactionTemplate
    ) {
        this.sessionRepository = sessionRepository;
        this.parseRepository = parseRepository;
        this.canonicalRepository = canonicalRepository;
        this.capabilityAccessPolicy = capabilityAccessPolicy;
        this.canonicalProblemBuilder = canonicalProblemBuilder;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.metrics = metrics;
        this.transactionTemplate = transactionTemplate;
    }

    public CanonicalProblemResult canonicalize(UUID userId, UUID problemSessionId) {
        long started = System.nanoTime();
        try {
            requireActiveAccount(userId);
            capabilityAccessPolicy.requireBasicSolve(userId);
            CanonicalProblemResult result = transactionTemplate.execute(status -> {
                ProblemSessionJpaEntity session = sessionRepository.findByIdAndUserIdForUpdate(problemSessionId, userId)
                    .orElseThrow(() -> problem(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.RESOURCE_FORBIDDEN,
                        "Problem session was not found",
                        false,
                        "RETRY"
                    ));
                ProblemParseJpaEntity parse = selectedParse(session, userId, problemSessionId);
                return canonicalRepository
                    .findByProblemParseIdAndProblemParseRevisionAndSchemaVersion(
                        parse.id(),
                        parse.revision(),
                        CanonicalProblemBuilder.CANONICAL_SCHEMA_VERSION
                    )
                    .map(this::resultFor)
                    .orElseGet(() -> createCanonicalProblem(userId, problemSessionId, parse));
            });
            metrics.success(result.problemType(), result.taskType());
            return result;
        } catch (CanonicalizationException exception) {
            metrics.failure(exception.failure());
            if (exception.failure() == CanonicalizationFailure.COMPLEXITY_LIMIT) {
                metrics.complexityRejected();
            }
            if (exception.failure() == CanonicalizationFailure.UNSUPPORTED_PARSE) {
                throw problem(HttpStatus.CONFLICT, ApiErrorCode.PROBLEM_UNSUPPORTED, exception.getMessage(), true, "PARSE");
            }
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.PROBLEM_PARSE_FAILED, exception.getMessage(), true, "REVIEW");
        } finally {
            metrics.latency(System.nanoTime() - started);
        }
    }

    public CanonicalProblemResult getCanonicalProblem(UUID userId, UUID problemSessionId) {
        requireActiveAccount(userId);
        ProblemSessionJpaEntity session = sessionRepository.findByIdAndUserId(problemSessionId, userId)
            .orElseThrow(() -> problem(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.RESOURCE_FORBIDDEN,
                "Problem session was not found",
                false,
                "RETRY"
            ));
        ProblemParseJpaEntity parse = selectedParse(session, userId, problemSessionId);
        return canonicalRepository.findByProblemParseIdAndProblemParseRevisionAndSchemaVersion(
                parse.id(),
                parse.revision(),
                CanonicalProblemBuilder.CANONICAL_SCHEMA_VERSION
            )
            .map(this::resultFor)
            .orElseThrow(() -> problem(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.PROBLEM_PARSE_FAILED,
                "Canonical problem has not been created",
                true,
                "CANONICALIZE"
            ));
    }

    private ProblemParseJpaEntity selectedParse(ProblemSessionJpaEntity session, UUID userId, UUID problemSessionId) {
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

    private CanonicalProblemResult createCanonicalProblem(
        UUID userId,
        UUID problemSessionId,
        ProblemParseJpaEntity parse
    ) {
        CanonicalizationDocuments documents = canonicalProblemBuilder.build(parse);
        int canonicalRevision = canonicalRepository.maxCanonicalRevision(problemSessionId) + 1;
        CanonicalProblemJpaEntity entity = new CanonicalProblemJpaEntity(
            UUID.randomUUID(),
            userId,
            problemSessionId,
            parse.id(),
            parse.revision(),
            canonicalRevision,
            CanonicalProblemBuilder.CANONICAL_SCHEMA_VERSION,
            CanonicalProblemBuilder.VERIFIER_SCHEMA_VERSION,
            documents.canonicalProblem().problemType(),
            documents.canonicalProblem().taskType(),
            toJson(documents.canonicalProblem()),
            toJson(documents.verifierInput()),
            toJson(documents.display()),
            clock.instant()
        );
        return resultFor(canonicalRepository.saveAndFlush(entity));
    }

    private CanonicalProblemResult resultFor(CanonicalProblemJpaEntity entity) {
        Map<String, Object> display = readDisplay(entity.displayJson());
        return new CanonicalProblemResult(
            entity.id(),
            entity.problemSessionId(),
            entity.problemParseId(),
            entity.problemParseRevision(),
            entity.canonicalRevision(),
            entity.schemaVersion(),
            entity.verifierSchemaVersion(),
            entity.problemType(),
            entity.taskType(),
            (String) display.get("normalizedText"),
            (String) display.get("displayLatex"),
            variableList(display.get("variables")),
            integer(display.get("sourceConstraintCount")),
            integer(display.get("derivedRestrictionCount")),
            entity.createdAt()
        );
    }

    private Map<String, Object> readDisplay(String displayJson) {
        try {
            return objectMapper.readValue(displayJson, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored canonical display JSON is invalid", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Canonical problem document cannot be serialized", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private java.util.List<String> variableList(Object value) {
        if (value instanceof java.util.List<?> raw) {
            return raw.stream().map(Object::toString).toList();
        }
        return java.util.List.of();
    }

    private int integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
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
