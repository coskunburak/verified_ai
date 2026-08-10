package com.verifiedai.problem.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.ai.application.AiModelGateway;
import com.verifiedai.ai.application.AiProblemClassifyRequest;
import com.verifiedai.ai.application.AiProblemClassifyResult;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProvenance;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.curriculum.application.CurriculumTaxonomyCatalog;
import com.verifiedai.curriculum.application.CurriculumTaxonomySnapshot;
import com.verifiedai.problem.infrastructure.persistence.CanonicalProblemJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.CanonicalProblemJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemClassificationJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemClassificationJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemSessionJpaRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProblemClassificationApplicationService {
    private static final String CLASSIFICATION_SCHEMA_VERSION = "problem-classification-v1";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ProblemSessionJpaRepository sessionRepository;
    private final CanonicalProblemJpaRepository canonicalRepository;
    private final ProblemClassificationJpaRepository classificationRepository;
    private final CurriculumTaxonomyCatalog taxonomyCatalog;
    private final AiModelGateway aiModelGateway;
    private final ProblemClassificationValidator validator;
    private final CapabilityAccessPolicy capabilityAccessPolicy;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final ProblemClassificationMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    @SuppressWarnings("ParameterNumber")
    ProblemClassificationApplicationService(
        ProblemSessionJpaRepository sessionRepository,
        CanonicalProblemJpaRepository canonicalRepository,
        ProblemClassificationJpaRepository classificationRepository,
        CurriculumTaxonomyCatalog taxonomyCatalog,
        AiModelGateway aiModelGateway,
        ProblemClassificationValidator validator,
        CapabilityAccessPolicy capabilityAccessPolicy,
        ObjectMapper objectMapper,
        JdbcTemplate jdbcTemplate,
        Clock clock,
        ProblemClassificationMetrics metrics,
        TransactionTemplate transactionTemplate
    ) {
        this.sessionRepository = sessionRepository;
        this.canonicalRepository = canonicalRepository;
        this.classificationRepository = classificationRepository;
        this.taxonomyCatalog = taxonomyCatalog;
        this.aiModelGateway = aiModelGateway;
        this.validator = validator;
        this.capabilityAccessPolicy = capabilityAccessPolicy;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.metrics = metrics;
        this.transactionTemplate = transactionTemplate;
    }

    public ProblemClassificationResult classify(UUID userId, UUID problemSessionId) {
        long started = System.nanoTime();
        metrics.started();
        try {
            requireActiveAccount(userId);
            capabilityAccessPolicy.requireBasicSolve(userId);

            // Find canonical problem
            CanonicalProblemJpaEntity canonical = canonicalRepository
                .findFirstByProblemSessionIdAndUserIdOrderByCanonicalRevisionDesc(problemSessionId, userId)
                .orElseThrow(() -> problem(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.CLASSIFICATION_FAILED,
                    "Canonical problem is required before classification",
                    true,
                    "CANONICALIZE"
                ));

            CurriculumTaxonomySnapshot ontology = taxonomyCatalog.snapshot();

            // Idempotency check
            var existing = classificationRepository
                .findByCanonicalProblemIdAndOntologyVersionAndSchemaVersion(
                    canonical.id(), ontology.ontologyVersion(), CLASSIFICATION_SCHEMA_VERSION
                );
            if (existing.isPresent()) {
                return resultFor(existing.get());
            }

            // Build classification projection
            ClassificationInputProjection projection = buildProjection(canonical, ontology);

            // Execute AI classification
            AiRoutePlan routePlan = aiModelGateway.routePlan(AiCapability.PROBLEM_CLASSIFY);
            AiProblemClassifyResult aiResult = aiModelGateway.executeProblemClassify(
                new AiProblemClassifyRequest(
                    canonical.id(),
                    problemSessionId,
                    canonical.problemType(),
                    canonical.taskType(),
                    toJson(projection),
                    ontology.ontologyVersion(),
                    toJson(ontology.activeSkillIds()),
                    routePlan.promptId(),
                    routePlan.promptVersion(),
                    routePlan.schemaVersion(),
                    routePlan.timeout()
                )
            );

            if (aiResult.provenance().fallbackUsed()) {
                metrics.fallback();
            }

            // Parse and validate AI response
            JsonNode responseNode = parseResponse(aiResult.rawOutputJson());
            String status = textOrNull(responseNode, "status");
            String primarySkillId = textOrNull(responseNode, "primarySkillId");
            List<String> secondarySkillIds = stringList(responseNode, "secondarySkillIds");
            String difficulty = textOrNull(responseNode, "difficulty");
            String confidence = textOrNull(responseNode, "confidence");

            // Schema validation
            String schemaVersion = textOrNull(responseNode, "schemaVersion");
            if (!CLASSIFICATION_SCHEMA_VERSION.equals(schemaVersion)) {
                metrics.schemaInvalid();
                throw problem(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.CLASSIFICATION_SCHEMA_INVALID,
                    "Classification response schema version is invalid",
                    true,
                    "RETRY"
                );
            }

            // Domain validation
            try {
                validator.validate(status, primarySkillId, secondarySkillIds, difficulty, confidence, ontology.ontologyVersion());
            } catch (ApiProblemException validationException) {
                if (validationException.code() == ApiErrorCode.CLASSIFICATION_SKILL_UNKNOWN
                    || validationException.code() == ApiErrorCode.CLASSIFICATION_HIERARCHY_INVALID) {
                    metrics.ontologyInvalid();
                }
                throw validationException;
            }

            // Derive subject/topic from primary skill (server-side, not trusting AI)
            String subjectId = null;
            String topicId = null;
            if (primarySkillId != null) {
                topicId = taxonomyCatalog.topicForSkill(primarySkillId);
                subjectId = topicId != null ? taxonomyCatalog.subjectForTopic(topicId) : null;
            }

            // Persist classification
            AiProvenance provenance = aiResult.provenance();
            final String derivedSubjectId = subjectId;
            final String derivedTopicId = topicId;
            ProblemClassificationResult result = transactionTemplate.execute(txStatus -> {
                int revision = classificationRepository.maxRevision(problemSessionId) + 1;
                ProblemClassificationJpaEntity entity = new ProblemClassificationJpaEntity(
                    UUID.randomUUID(),
                    userId,
                    problemSessionId,
                    canonical.id(),
                    revision,
                    status,
                    ontology.ontologyVersion(),
                    CLASSIFICATION_SCHEMA_VERSION,
                    derivedSubjectId,
                    derivedTopicId,
                    primarySkillId,
                    secondarySkillIds.isEmpty() ? null : toJson(secondarySkillIds),
                    difficulty,
                    confidence,
                    provenance.provider(),
                    provenance.model(),
                    provenance.promptId(),
                    provenance.promptVersion(),
                    provenance.routePolicyVersion(),
                    provenance.schemaVersion(),
                    provenance.fallbackUsed(),
                    (int) aiResult.providerLatencyMs(),
                    aiResult.usage().estimatedCostMicros(),
                    clock.instant()
                );
                return resultFor(classificationRepository.saveAndFlush(entity));
            });

            // Emit status metrics
            switch (status) {
                case "CLASSIFIED" -> metrics.success();
                case "REVIEW_REQUIRED", "AMBIGUOUS" -> metrics.reviewRequired();
                case "UNKNOWN", "UNSUPPORTED" -> metrics.unknown();
                default -> metrics.failure();
            }
            return result;

        } catch (AiProviderException aiException) {
            metrics.failure();
            throw problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.CLASSIFICATION_PROVIDER_UNAVAILABLE,
                "Classification provider is unavailable",
                aiException.retryable(),
                "RETRY"
            );
        } finally {
            metrics.latency(System.nanoTime() - started);
        }
    }

    public ProblemClassificationResult getClassification(UUID userId, UUID problemSessionId) {
        requireActiveAccount(userId);
        sessionRepository.findByIdAndUserId(problemSessionId, userId)
            .orElseThrow(() -> problem(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.RESOURCE_FORBIDDEN,
                "Problem session was not found",
                false,
                "RETRY"
            ));
        return classificationRepository
            .findFirstByProblemSessionIdAndUserIdOrderByRevisionDesc(problemSessionId, userId)
            .map(this::resultFor)
            .orElseThrow(() -> problem(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.CLASSIFICATION_FAILED,
                "Classification has not been created",
                true,
                "CLASSIFY"
            ));
    }

    private ClassificationInputProjection buildProjection(
        CanonicalProblemJpaEntity canonical,
        CurriculumTaxonomySnapshot ontology
    ) {
        Map<String, Object> display = readJsonMap(canonical.displayJson());
        return new ClassificationInputProjection(
            canonical.problemType(),
            canonical.taskType(),
            (String) display.get("normalizedText"),
            (String) display.get("displayLatex"),
            variableList(display.get("variables")),
            integerValue(display.get("sourceConstraintCount")) + integerValue(display.get("derivedRestrictionCount")),
            ontology.ontologyVersion()
        );
    }

    private ProblemClassificationResult resultFor(ProblemClassificationJpaEntity entity) {
        return new ProblemClassificationResult(
            entity.id(),
            entity.canonicalProblemId(),
            entity.problemSessionId(),
            entity.status(),
            entity.ontologyVersion(),
            entity.subjectId(),
            entity.topicId(),
            entity.primarySkillId(),
            parseSecondarySkillIds(entity.secondarySkillIds()),
            entity.difficulty(),
            entity.confidence(),
            entity.createdAt()
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> parseSecondarySkillIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> result = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return result == null ? List.of() : List.copyOf(result);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private JsonNode parseResponse(String rawJson) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (Exception exception) {
            metrics.schemaInvalid();
            throw problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.CLASSIFICATION_SCHEMA_INVALID,
                "Classification response is not valid JSON",
                true,
                "RETRY"
            );
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private List<String> stringList(JsonNode node, String field) {
        List<String> result = new ArrayList<>();
        JsonNode array = node.path(field);
        if (array.isArray()) {
            for (JsonNode element : array) {
                if (element.isTextual()) {
                    result.add(element.asText());
                }
            }
        }
        return result;
    }

    private Map<String, Object> readJsonMap(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored display JSON is invalid", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize to JSON", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> variableList(Object value) {
        if (value instanceof List<?> raw) {
            return raw.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private int integerValue(Object value) {
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
