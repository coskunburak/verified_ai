package com.verifiedai.exam.application;

import com.verifiedai.exam.domain.AppliedAccommodationSnapshot;
import com.verifiedai.exam.domain.AssembledAssessment;
import com.verifiedai.exam.domain.AssembledAssessmentRepository;
import com.verifiedai.exam.domain.AssessmentBlueprint;
import com.verifiedai.exam.domain.AssessmentBlueprintMode;
import com.verifiedai.exam.domain.AssessmentBlueprintRepository;
import com.verifiedai.exam.domain.AssessmentBlueprintStatus;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileRecord;
import com.verifiedai.exam.domain.DeterministicAssessmentAssemblyEngine;
import com.verifiedai.exam.domain.QuestionBankItem;
import com.verifiedai.exam.domain.QuestionBankRepository;
import com.verifiedai.exam.domain.QuestionDifficultyBand;
import com.verifiedai.exam.domain.QuestionItemStatus;
import com.verifiedai.exam.events.AssessmentAssembledEvent;
import com.verifiedai.exam.events.AssessmentBlueprintActivatedEvent;
import com.verifiedai.exam.events.QuestionBankItemPublishedEvent;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamAssessmentAssemblyService {

    public record AssembleResult(
            AssembledAssessment assessment,
            boolean created
    ) {
    }

    public record AssembleCommand(
            UUID examDefinitionId,
            UUID authenticatedUserId,
            UUID requestedUserId,
            UUID candidateProfileId,
            UUID blueprintId,
            String assemblySeed,
            BigDecimal overrideTimeMultiplier,
            Boolean highContrastMode,
            Boolean screenReaderOptimized,
            Boolean extendedBreakPrompts
    ) {
    }

    private final QuestionBankRepository questionBankRepository;
    private final AssessmentBlueprintRepository blueprintRepository;
    private final AssembledAssessmentRepository assembledAssessmentRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final DeterministicAssessmentAssemblyEngine assemblyEngine;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    private volatile boolean questionBankEnabled;
    private volatile boolean blueprintManagementEnabled;
    private volatile boolean assessmentAssemblyEnabled;

    @Autowired
    public ExamAssessmentAssemblyService(
            QuestionBankRepository questionBankRepository,
            AssessmentBlueprintRepository blueprintRepository,
            AssembledAssessmentRepository assembledAssessmentRepository,
            ObjectProvider<CandidateProfileRepository> candidateProfileRepositoryProvider,
            DeterministicAssessmentAssemblyEngine assemblyEngine,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            ObjectProvider<Clock> clockProvider,
            @Value("${verifiedai.exam.question-bank-enabled:false}") boolean questionBankEnabled,
            @Value("${verifiedai.exam.blueprint-management-enabled:false}") boolean blueprintManagementEnabled,
            @Value("${verifiedai.exam.assessment-assembly-enabled:false}") boolean assessmentAssemblyEnabled
    ) {
        this(
                questionBankRepository,
                blueprintRepository,
                assembledAssessmentRepository,
                candidateProfileRepositoryProvider.getIfAvailable(),
                assemblyEngine,
                eventPublisher,
                meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new),
                clockProvider.getIfAvailable(Clock::systemUTC),
                questionBankEnabled,
                blueprintManagementEnabled,
                assessmentAssemblyEnabled
        );
    }

    public ExamAssessmentAssemblyService(
            QuestionBankRepository questionBankRepository,
            AssessmentBlueprintRepository blueprintRepository,
            AssembledAssessmentRepository assembledAssessmentRepository,
            CandidateProfileRepository candidateProfileRepository,
            DeterministicAssessmentAssemblyEngine assemblyEngine,
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry,
            Clock clock,
            boolean questionBankEnabled,
            boolean blueprintManagementEnabled,
            boolean assessmentAssemblyEnabled
    ) {
        this.questionBankRepository = Objects.requireNonNull(questionBankRepository, "questionBankRepository cannot be null");
        this.blueprintRepository = Objects.requireNonNull(blueprintRepository, "blueprintRepository cannot be null");
        this.assembledAssessmentRepository = Objects.requireNonNull(assembledAssessmentRepository, "assembledAssessmentRepository cannot be null");
        this.candidateProfileRepository = candidateProfileRepository;
        this.assemblyEngine = Objects.requireNonNull(assemblyEngine, "assemblyEngine cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.meterRegistry = meterRegistry == null ? new SimpleMeterRegistry() : meterRegistry;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.questionBankEnabled = questionBankEnabled;
        this.blueprintManagementEnabled = blueprintManagementEnabled;
        this.assessmentAssemblyEnabled = assessmentAssemblyEnabled;
    }

    public void setQuestionBankEnabled(boolean enabled) {
        this.questionBankEnabled = enabled;
    }

    public void setBlueprintManagementEnabled(boolean enabled) {
        this.blueprintManagementEnabled = enabled;
    }

    public void setAssessmentAssemblyEnabled(boolean enabled) {
        this.assessmentAssemblyEnabled = enabled;
    }

    @Transactional
    public QuestionBankItem createQuestionBankItem(UUID examDefinitionId, QuestionBankItem draftItem) {
        ensureQuestionBankEnabled();
        if (!examDefinitionId.equals(draftItem.examDefinitionId())) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_VERSION_MISMATCH,
                    "Path examDefinitionId does not match item examDefinitionId",
                    true,
                    "Ensure the request payload matches the URL examDefinitionId."
            );
        }
        Optional<QuestionBankItem> conflict = questionBankRepository.findByQuestionCode(draftItem.questionCode());
        if (conflict.isPresent()) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_QUESTION_CODE_CONFLICT,
                    "Question code already exists: " + draftItem.questionCode(),
                    true,
                    "Use a unique questionCode when creating a new question bank item."
            );
        }
        return questionBankRepository.save(draftItem);
    }

    @Transactional
    public QuestionBankItem publishQuestionBankItem(UUID examDefinitionId, UUID itemId) {
        ensureQuestionBankEnabled();
        QuestionBankItem existing = questionBankRepository.findById(itemId)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.EXAM_QUESTION_ITEM_NOT_FOUND,
                        "Question bank item not found: " + itemId,
                        true,
                        "Verify the question item UUID."
                ));
        if (!existing.examDefinitionId().equals(examDefinitionId)) {
            throw new ApiProblemException(
                    HttpStatus.NOT_FOUND,
                    ApiErrorCode.EXAM_QUESTION_ITEM_NOT_FOUND,
                    "Question bank item " + itemId + " does not belong to exam definition " + examDefinitionId,
                    true,
                    "Verify the examDefinitionId and itemId."
            );
        }

        Instant now = Instant.now(clock);
        QuestionBankItem published = existing.publish(now);
        QuestionBankItem saved = questionBankRepository.save(published);

        Counter.builder("verifiedai.exam.question_bank.published.total")
                .tag("exam_definition_id", examDefinitionId.toString())
                .tag("difficulty_band", saved.difficultyBand().name())
                .register(meterRegistry)
                .increment();

        eventPublisher.publishEvent(new QuestionBankItemPublishedEvent(
                saved.id(),
                saved.examDefinitionId(),
                saved.examVersionNumber(),
                saved.questionCode(),
                saved.topicCode(),
                saved.difficultyBand().name(),
                saved.cognitiveLevel().name(),
                saved.provenance().provenanceHash(),
                now
        ));

        return saved;
    }

    public List<QuestionBankItem> listQuestionBankItems(
            UUID examDefinitionId,
            Integer examVersionNumber,
            QuestionItemStatus status,
            String topicCode,
            QuestionDifficultyBand difficultyBand
    ) {
        ensureQuestionBankEnabled();
        return questionBankRepository.findByExamDefinition(
                examDefinitionId,
                examVersionNumber,
                status,
                topicCode,
                difficultyBand
        );
    }

    @Transactional
    public AssessmentBlueprint createBlueprint(UUID examDefinitionId, AssessmentBlueprint draftBlueprint) {
        ensureBlueprintManagementEnabled();
        if (!examDefinitionId.equals(draftBlueprint.examDefinitionId())) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_VERSION_MISMATCH,
                    "Path examDefinitionId does not match blueprint examDefinitionId",
                    true,
                    "Ensure the blueprint examDefinitionId matches the URL path."
            );
        }
        Optional<AssessmentBlueprint> conflict = blueprintRepository.findByBlueprintCode(draftBlueprint.blueprintCode());
        if (conflict.isPresent()) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_BLUEPRINT_CODE_CONFLICT,
                    "Blueprint code already exists: " + draftBlueprint.blueprintCode(),
                    true,
                    "Provide a unique blueprintCode."
            );
        }
        return blueprintRepository.save(draftBlueprint);
    }

    @Transactional
    public AssessmentBlueprint activateBlueprint(UUID examDefinitionId, UUID blueprintId) {
        ensureBlueprintManagementEnabled();
        AssessmentBlueprint existing = blueprintRepository.findById(blueprintId)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.EXAM_BLUEPRINT_NOT_FOUND,
                        "Assessment blueprint not found: " + blueprintId,
                        true,
                        "Verify the blueprintId."
                ));
        if (!existing.examDefinitionId().equals(examDefinitionId)) {
            throw new ApiProblemException(
                    HttpStatus.NOT_FOUND,
                    ApiErrorCode.EXAM_BLUEPRINT_NOT_FOUND,
                    "Assessment blueprint " + blueprintId + " does not belong to exam definition " + examDefinitionId,
                    true,
                    "Verify the examDefinitionId and blueprintId."
            );
        }

        Instant now = Instant.now(clock);
        AssessmentBlueprint activated = existing.activate(now);
        AssessmentBlueprint saved = blueprintRepository.save(activated);

        Counter.builder("verifiedai.exam.blueprint.activated.total")
                .tag("exam_definition_id", examDefinitionId.toString())
                .tag("blueprint_mode", saved.blueprintMode().name())
                .register(meterRegistry)
                .increment();

        eventPublisher.publishEvent(new AssessmentBlueprintActivatedEvent(
                saved.id(),
                saved.examDefinitionId(),
                saved.examVersionNumber(),
                saved.blueprintCode(),
                saved.blueprintMode().name(),
                saved.totalQuestionCount(),
                saved.blueprintHash(),
                now
        ));

        return saved;
    }

    public List<AssessmentBlueprint> listBlueprints(
            UUID examDefinitionId,
            Integer examVersionNumber,
            AssessmentBlueprintStatus status,
            AssessmentBlueprintMode blueprintMode
    ) {
        ensureBlueprintManagementEnabled();
        return blueprintRepository.findByExamDefinition(
                examDefinitionId,
                examVersionNumber,
                status,
                blueprintMode
        );
    }

    @Transactional
    public AssembleResult assembleAssessment(AssembleCommand command) {
        ensureAssessmentAssemblyEnabled();
        if (command.authenticatedUserId() != null
                && command.requestedUserId() != null
                && !command.authenticatedUserId().equals(command.requestedUserId())) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_CANDIDATE_ACCESS_DENIED,
                    "Authenticated learner cannot assemble an assessment on behalf of another userId",
                    false,
                    "Ensure request userId matches the authenticated session."
            );
        }

        UUID effectiveUserId = command.requestedUserId() != null
                ? command.requestedUserId()
                : command.authenticatedUserId();
        if (effectiveUserId == null) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_CANDIDATE_ACCESS_DENIED,
                    "Missing authenticated learner identity",
                    false,
                    "Sign in and provide a valid userId."
            );
        }

        AssessmentBlueprint blueprint = blueprintRepository.findById(command.blueprintId())
                .orElseThrow(() -> {
                    recordAssemblyFailure(command.examDefinitionId(), ApiErrorCode.EXAM_BLUEPRINT_NOT_FOUND.name());
                    return new ApiProblemException(
                            HttpStatus.NOT_FOUND,
                            ApiErrorCode.EXAM_BLUEPRINT_NOT_FOUND,
                            "Assessment blueprint not found: " + command.blueprintId(),
                            true,
                            "Select an existing active assessment blueprint."
                    );
                });

        if (command.examDefinitionId() != null && !blueprint.examDefinitionId().equals(command.examDefinitionId())) {
            recordAssemblyFailure(command.examDefinitionId(), ApiErrorCode.EXAM_BLUEPRINT_VERSION_MISMATCH.name());
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_VERSION_MISMATCH,
                    "Blueprint examDefinitionId does not match requested examDefinitionId",
                    true,
                    "Select a blueprint belonging to the active exam definition."
            );
        }
        UUID effectiveExamDefId = command.examDefinitionId() != null
                ? command.examDefinitionId()
                : blueprint.examDefinitionId();
        if (blueprint.status() != AssessmentBlueprintStatus.ACTIVE) {
            recordAssemblyFailure(effectiveExamDefId, ApiErrorCode.EXAM_BLUEPRINT_VERSION_MISMATCH.name());
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_VERSION_MISMATCH,
                    "Assessment blueprint is not ACTIVE: " + blueprint.status(),
                    true,
                    "Activate the blueprint before assembling candidate assessments."
            );
        }

        // Idempotent replay check on (userId, blueprintId, assemblySeed)
        Optional<AssembledAssessment> existing = assembledAssessmentRepository.findByUserIdAndBlueprintIdAndSeed(
                effectiveUserId,
                blueprint.id(),
                command.assemblySeed()
        );
        if (existing.isPresent()) {
            return new AssembleResult(existing.get(), false);
        }

        Optional<CandidateProfileRecord> profileOpt = candidateProfileRepository == null
                ? Optional.empty()
                : candidateProfileRepository.findProfileByUserId(effectiveUserId);

        UUID effectiveProfileId = command.candidateProfileId() != null
                ? command.candidateProfileId()
                : profileOpt.map(CandidateProfileRecord::profileId).orElse(null);

        AppliedAccommodationSnapshot accommodations = resolveAccommodations(profileOpt.orElse(null), command);
        Instant now = Instant.now(clock);
        Instant cooldownSince = now.minus(blueprint.allowRecentlySeenCooldownDays(), ChronoUnit.DAYS);
        Set<UUID> recentlySeen = assembledAssessmentRepository.findRecentlySeenQuestionIds(
                effectiveUserId,
                blueprint.examDefinitionId(),
                cooldownSince
        );

        List<QuestionBankItem> pool = questionBankRepository.findByExamDefinition(
                blueprint.examDefinitionId(),
                blueprint.examVersionNumber(),
                QuestionItemStatus.PUBLISHED,
                null,
                null
        );

        AssembledAssessment assembled;
        try {
            assembled = assemblyEngine.assemble(new DeterministicAssessmentAssemblyEngine.AssemblyInput(
                    null,
                    effectiveUserId,
                    effectiveProfileId,
                    blueprint,
                    pool,
                    recentlySeen,
                    command.assemblySeed(),
                    accommodations,
                    now
            ));
        } catch (ApiProblemException ex) {
            recordAssemblyFailure(effectiveExamDefId, ex.code().name());
            throw ex;
        }

        AssembledAssessment saved = assembledAssessmentRepository.save(assembled);
        questionBankRepository.incrementExposureCounts(
                saved.items().stream().map(i -> i.questionItemId()).toList()
        );

        boolean accommodated = saved.timeMultiplier().compareTo(new BigDecimal("1.00")) > 0
                || saved.accommodationsSnapshot().highContrastMode()
                || saved.accommodationsSnapshot().screenReaderOptimized()
                || saved.accommodationsSnapshot().extendedBreakPrompts();

        Counter.builder("verifiedai.exam.assembly.completed.total")
                .tag("exam_definition_id", effectiveExamDefId.toString())
                .tag("blueprint_mode", blueprint.blueprintMode().name())
                .tag("accommodated", String.valueOf(accommodated))
                .register(meterRegistry)
                .increment();

        eventPublisher.publishEvent(new AssessmentAssembledEvent(
                saved.id(),
                saved.userId(),
                saved.blueprintId(),
                saved.examDefinitionId(),
                saved.examVersionNumber(),
                saved.assemblySeed(),
                saved.reproducibilityHash(),
                saved.totalQuestionCount(),
                saved.effectiveDurationSeconds(),
                saved.assembledAt()
        ));

        return new AssembleResult(saved, true);
    }

    public List<AssembledAssessment> listAssembledAssessments(
            UUID authenticatedUserId,
            UUID pathUserId,
            UUID examDefinitionId
    ) {
        ensureAssessmentAssemblyEnabled();
        if (authenticatedUserId != null && !authenticatedUserId.equals(pathUserId)) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_CANDIDATE_ACCESS_DENIED,
                    "Learner cannot list another candidate's assembled assessments",
                    false,
                    "Verify the candidate userId matches the authenticated session."
            );
        }
        return assembledAssessmentRepository.findByUserId(pathUserId, 100).stream()
                .filter(a -> examDefinitionId == null || examDefinitionId.equals(a.examDefinitionId()))
                .toList();
    }

    public AssembledAssessment getAssembledAssessment(
            UUID authenticatedUserId,
            UUID pathUserId,
            UUID assessmentId
    ) {
        ensureAssessmentAssemblyEnabled();
        if (authenticatedUserId != null && !authenticatedUserId.equals(pathUserId)) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_CANDIDATE_ACCESS_DENIED,
                    "Learner cannot access another candidate's assembled assessment",
                    false,
                    "Verify the candidate userId matches the authenticated session."
            );
        }

        AssembledAssessment assessment = assembledAssessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.EXAM_ASSEMBLED_ASSESSMENT_NOT_FOUND,
                        "Assembled assessment not found: " + assessmentId,
                        true,
                        "Verify the assessmentId."
                ));

        if (!assessment.userId().equals(pathUserId)) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_CANDIDATE_ACCESS_DENIED,
                    "Assembled assessment does not belong to candidate " + pathUserId,
                    false,
                    "Access only assessments assembled for your learner account."
            );
        }
        return assessment;
    }

    private AppliedAccommodationSnapshot resolveAccommodations(
            CandidateProfileRecord profile,
            AssembleCommand command
    ) {
        BigDecimal multiplier = command.overrideTimeMultiplier() != null
                ? command.overrideTimeMultiplier()
                : (profile != null ? profile.accommodations().extraTimeMultiplier() : new BigDecimal("1.00"));
        boolean highContrast = command.highContrastMode() != null
                ? command.highContrastMode()
                : (profile != null && profile.accommodations().highContrastEnabled());
        boolean screenReader = command.screenReaderOptimized() != null
                ? command.screenReaderOptimized()
                : (profile != null && profile.accommodations().screenReaderOptimized());
        boolean extendedBreaks = command.extendedBreakPrompts() != null
                ? command.extendedBreakPrompts()
                : (multiplier.compareTo(new BigDecimal("1.00")) > 0);

        return new AppliedAccommodationSnapshot(multiplier, highContrast, screenReader, extendedBreaks);
    }

    private void recordAssemblyFailure(UUID examDefinitionId, String reason) {
        Counter.builder("verifiedai.exam.assembly.failed.total")
                .tag("exam_definition_id", String.valueOf(examDefinitionId))
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    private void ensureQuestionBankEnabled() {
        if (!questionBankEnabled) {
            throw new ApiProblemException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.EXAM_QUESTION_BANK_DISABLED,
                    "Exam question bank management is currently disabled",
                    true,
                    "Enable verifiedai.exam.question-bank-enabled to access question bank endpoints."
            );
        }
    }

    private void ensureBlueprintManagementEnabled() {
        if (!blueprintManagementEnabled) {
            throw new ApiProblemException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.EXAM_BLUEPRINT_DISABLED,
                    "Exam blueprint management is currently disabled",
                    true,
                    "Enable verifiedai.exam.blueprint-management-enabled to access blueprint endpoints."
            );
        }
    }

    private void ensureAssessmentAssemblyEnabled() {
        if (!assessmentAssemblyEnabled) {
            throw new ApiProblemException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.EXAM_ASSESSMENT_ASSEMBLY_DISABLED,
                    "Exam assessment assembly is currently disabled",
                    true,
                    "Enable verifiedai.exam.assessment-assembly-enabled to assemble candidate assessments."
            );
        }
    }
}
