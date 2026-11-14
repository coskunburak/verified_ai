package com.verifiedai.exam.application;

import com.verifiedai.exam.domain.ExamBlueprintValidator;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.ExamDefinitionCatalogResponse;
import com.verifiedai.exam.domain.ExamModels.ExamDefinitionDetailResponse;
import com.verifiedai.exam.domain.ExamModels.ExamDefinitionSummaryDto;
import com.verifiedai.exam.domain.ExamModels.ExamFamily;
import com.verifiedai.exam.domain.ExamModels.ExamTopicWeight;
import com.verifiedai.exam.domain.ExamModels.ExamTopicWeightDto;
import com.verifiedai.exam.domain.ExamModels.Jurisdiction;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Application service for querying versioned exam definitions and curriculum weight blueprints ($0.00 LLM cost).
 */
@Service
public class ExamCatalogService {

    private static final Logger LOG = LoggerFactory.getLogger(ExamCatalogService.class);

    public static final String CATALOG_SCHEMA_VERSION = "exam-definition-catalog-v1";
    public static final String DETAIL_SCHEMA_VERSION = "exam-definition-detail-v1";

    private final ExamRepository repository;
    private final ExamBlueprintValidator blueprintValidator;
    private final Counter catalogQueryCounter;
    private final Counter validationFailureCounter;
    private final boolean catalogEnabled;

    @Autowired
    public ExamCatalogService(
            ExamRepository repository,
            ExamBlueprintValidator blueprintValidator,
            MeterRegistry meterRegistry,
            @Value("${verifiedai.exam.catalog-enabled:false}") boolean catalogEnabled
    ) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.blueprintValidator = Objects.requireNonNull(blueprintValidator, "blueprintValidator cannot be null");
        Objects.requireNonNull(meterRegistry, "meterRegistry cannot be null");
        this.catalogQueryCounter = Counter.builder("verifiedai.exam.catalog.queries")
                .description("Total number of exam catalog and definition queries")
                .register(meterRegistry);
        this.validationFailureCounter = Counter.builder("verifiedai.exam.validation.failures")
                .description("Total number of exam blueprint and request validation failures")
                .register(meterRegistry);
        this.catalogEnabled = catalogEnabled;
    }

    @Transactional(readOnly = true)
    public ExamDefinitionCatalogResponse listDefinitions(
            Jurisdiction jurisdiction,
            ExamFamily examFamily,
            boolean includeDeprecated
    ) {
        requireCatalogEnabled();
        catalogQueryCounter.increment();

        List<ExamDefinition> definitions = repository.findCatalog(jurisdiction, examFamily, includeDeprecated);
        for (ExamDefinition definition : definitions) {
            validateOrCountFailure(definition);
        }

        List<ExamDefinitionSummaryDto> summaries = definitions.stream()
                .map(ExamCatalogService::toSummaryDto)
                .toList();

        LOG.info("exam.catalog.queried jurisdiction={} examFamily={} includeDeprecated={} count={}",
                jurisdiction, examFamily, includeDeprecated, summaries.size());

        return new ExamDefinitionCatalogResponse(
                CATALOG_SCHEMA_VERSION,
                summaries,
                summaries.size(),
                includeDeprecated,
                false,
                0
        );
    }

    @Transactional(readOnly = true)
    public ExamDefinitionDetailResponse getActiveDefinition(String rawExamCode) {
        requireCatalogEnabled();
        catalogQueryCounter.increment();

        String examCode = normalizeExamCode(rawExamCode);
        ExamDefinition definition = repository.findLatestByCode(examCode, false)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.EXAM_DEFINITION_NOT_FOUND,
                        "Active exam definition not found for examCode=" + examCode,
                        false,
                        "SELECT_SUPPORTED_EXAM"
                ));

        validateOrCountFailure(definition);
        LOG.info("exam.definition.fetched examCode={} examVersion={}", examCode, definition.examVersion());
        return toDetailResponse(definition);
    }

    @Transactional(readOnly = true)
    public ExamDefinitionDetailResponse getVersionedDefinition(String rawExamCode, String rawExamVersion) {
        requireCatalogEnabled();
        catalogQueryCounter.increment();

        String examCode = normalizeExamCode(rawExamCode);
        String examVersion = normalizeExamVersion(rawExamVersion);

        ExamDefinition definition = repository.findByCodeAndVersion(examCode, examVersion)
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
                            "Exam version not found for examCode=" + examCode + " version=" + examVersion,
                            false,
                            "SELECT_VALID_VERSION"
                    );
                });

        validateOrCountFailure(definition);
        LOG.info("exam.version.fetched examCode={} examVersion={} status={}",
                examCode, examVersion, definition.status());
        return toDetailResponse(definition);
    }

    private void validateOrCountFailure(ExamDefinition definition) {
        try {
            blueprintValidator.validateBlueprint(definition);
        } catch (ApiProblemException ex) {
            validationFailureCounter.increment();
            throw ex;
        }
    }

    private void requireCatalogEnabled() {
        if (!catalogEnabled) {
            throw new ApiProblemException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.EXAM_FEATURE_DISABLED,
                    "Exam catalog feature is currently disabled",
                    true,
                    "TRY_AGAIN_LATER"
            );
        }
    }

    private static String normalizeExamCode(String rawExamCode) {
        if (rawExamCode == null || rawExamCode.isBlank()) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "examCode path variable is required",
                    true,
                    "PROVIDE_EXAM_CODE"
            );
        }
        return rawExamCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeExamVersion(String rawExamVersion) {
        if (rawExamVersion == null || rawExamVersion.isBlank()) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "examVersion path variable is required",
                    true,
                    "PROVIDE_EXAM_VERSION"
            );
        }
        return rawExamVersion.trim();
    }

    public static ExamDefinitionSummaryDto toSummaryDto(ExamDefinition definition) {
        return new ExamDefinitionSummaryDto(
                definition.id(),
                definition.examCode(),
                definition.examVersion(),
                definition.jurisdiction(),
                definition.examFamily(),
                definition.titleEn(),
                definition.titleTr(),
                definition.status(),
                definition.totalQuestions(),
                definition.durationMinutes(),
                definition.scoringFormulaType(),
                definition.wrongPenaltyFactor(),
                definition.minScore(),
                definition.maxScore(),
                definition.navigationPolicy(),
                definition.blueprintHash(),
                definition.effectiveFrom(),
                definition.deprecatedAt(),
                definition.supersededByVersion(),
                definition.sections().size(),
                definition.topicWeights().size(),
                false
        );
    }

    public static ExamDefinitionDetailResponse toDetailResponse(ExamDefinition definition) {
        return new ExamDefinitionDetailResponse(
                DETAIL_SCHEMA_VERSION,
                definition.id(),
                definition.examCode(),
                definition.examVersion(),
                definition.jurisdiction(),
                definition.examFamily(),
                definition.titleEn(),
                definition.titleTr(),
                definition.status(),
                definition.totalQuestions(),
                definition.durationMinutes(),
                definition.scoringFormulaType(),
                definition.wrongPenaltyFactor(),
                definition.minScore(),
                definition.maxScore(),
                definition.navigationPolicy(),
                definition.blueprintHash(),
                definition.effectiveFrom(),
                definition.deprecatedAt(),
                definition.supersededByVersion(),
                definition.sections(),
                definition.scoringParameters(),
                toTopicWeightDtos(definition.topicWeights()),
                false,
                0
        );
    }

    public static List<ExamTopicWeightDto> toTopicWeightDtos(List<ExamTopicWeight> weights) {
        return weights.stream()
                .map(weight -> new ExamTopicWeightDto(
                        weight.sectionCode(),
                        weight.skillId(),
                        weight.domainCode(),
                        weight.questionCountWeight(),
                        weight.minQuestions(),
                        weight.maxQuestions(),
                        weight.cognitiveLevelMix(),
                        weight.sortOrder()
                ))
                .toList();
    }
}
