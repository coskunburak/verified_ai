package com.verifiedai.exam.application;

import com.verifiedai.curriculum.application.CurriculumTaxonomyCatalog;
import com.verifiedai.curriculum.application.CurriculumTaxonomySnapshot;
import com.verifiedai.exam.domain.CandidateProfileModels;
import com.verifiedai.exam.domain.CandidateProfileModels.ParsedSyllabusTopicItem;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusConfirmRequest;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusIngestionResponse;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusMappingConfirmation;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusUploadRecord;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusUploadStatus;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusVisionResult;
import com.verifiedai.exam.domain.CurriculumTopicDisambiguator;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.SyllabusMediaValidator;
import com.verifiedai.exam.domain.SyllabusMediaValidator.ValidatedMedia;
import com.verifiedai.exam.events.CandidateGoalUpdatedEvent;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
public class SyllabusIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SyllabusIngestionService.class);

    private final CandidateProfileRepository profileRepository;
    private final ExamRepository examRepository;
    private final CurriculumTaxonomyCatalog taxonomyCatalog;
    private final SyllabusVisionGateway visionGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final boolean syllabusIngestionEnabled;
    private final int monthlyQuota;
    private final int retentionDays;
    private final double similarityThreshold;

    private final Counter uploadedCounter;
    private final Counter cacheHitCounter;
    private final Counter quotaExceededCounter;
    private final Counter userConfirmedCounter;
    private final Timer ingestionLatencyTimer;

    @Autowired
    public SyllabusIngestionService(
            CandidateProfileRepository profileRepository,
            ExamRepository examRepository,
            CurriculumTaxonomyCatalog taxonomyCatalog,
            SyllabusVisionGateway visionGateway,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            Clock clock,
            @Value("${verifiedai.exam.syllabus-ingestion-enabled:false}") boolean syllabusIngestionEnabled,
            @Value("${verifiedai.exam.syllabus-monthly-quota:3}") int monthlyQuota,
            @Value("${verifiedai.exam.syllabus-retention-days:30}") int retentionDays,
            @Value("${verifiedai.exam.syllabus-similarity-threshold:0.70}") double similarityThreshold
    ) {
        this(
                profileRepository,
                examRepository,
                taxonomyCatalog,
                visionGateway,
                eventPublisher,
                meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new),
                clock,
                syllabusIngestionEnabled,
                monthlyQuota,
                retentionDays,
                similarityThreshold
        );
    }

    public SyllabusIngestionService(
            CandidateProfileRepository profileRepository,
            ExamRepository examRepository,
            CurriculumTaxonomyCatalog taxonomyCatalog,
            SyllabusVisionGateway visionGateway,
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry,
            Clock clock,
            boolean syllabusIngestionEnabled,
            int monthlyQuota,
            int retentionDays,
            double similarityThreshold
    ) {
        this.profileRepository = Objects.requireNonNull(profileRepository);
        this.examRepository = Objects.requireNonNull(examRepository);
        this.taxonomyCatalog = Objects.requireNonNull(taxonomyCatalog);
        this.visionGateway = Objects.requireNonNull(visionGateway);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
        this.syllabusIngestionEnabled = syllabusIngestionEnabled;
        this.monthlyQuota = monthlyQuota;
        this.retentionDays = retentionDays;
        this.similarityThreshold = similarityThreshold;
        this.uploadedCounter = meterRegistry.counter("verifiedai_exam_syllabus_uploaded_total");
        this.cacheHitCounter = meterRegistry.counter("verifiedai_exam_syllabus_cache_hit_total");
        this.quotaExceededCounter = meterRegistry.counter("verifiedai_exam_syllabus_quota_exceeded_total");
        this.userConfirmedCounter = meterRegistry.counter("verifiedai_exam_syllabus_user_confirmed_total");
        this.ingestionLatencyTimer = meterRegistry.timer("syllabus_ingestion_latency_seconds");
    }

    public record IngestResult(SyllabusIngestionResponse response, boolean created) {
    }

    @Transactional
    public IngestResult ingestSyllabus(
            UUID authenticatedUserId,
            String examCode,
            String examVersion,
            byte[] rawBytes,
            String declaredMimeType,
            String filenameHint,
            String idempotencyKey
    ) {
        requireEnabled();
        if (examCode == null || examCode.isBlank()) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.EXAM_DEFINITION_NOT_FOUND,
                    "Exam code is required for syllabus ingestion",
                    true,
                    "Select a target exam before uploading your syllabus."
            );
        }

        // Strict Security & Cost Pipeline Order:
        // 1. Validate media magic bytes and 10MB limit BEFORE cache/quota/vision
        ValidatedMedia validatedMedia = SyllabusMediaValidator.validateAndHash(rawBytes, declaredMimeType);

        ExamDefinition exam = resolveExamDefinition(examCode.trim(), examVersion);

        String effectiveIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        if (effectiveIdempotencyKey != null) {
            Optional<SyllabusUploadRecord> idempotentOpt =
                    profileRepository.findSyllabusUploadByUserAndIdempotencyKey(authenticatedUserId, effectiveIdempotencyKey);
            if (idempotentOpt.isPresent()) {
                int remaining = computeRemainingMonthlyQuota(authenticatedUserId, Instant.now(clock));
                return new IngestResult(toResponse(idempotentOpt.get(), false, true, remaining), false);
            }
        }

        // 2. Compute SHA-256 hash & check deduplication cache ($0.00 LLM spend, no quota used)
        Instant now = Instant.now(clock);
        Optional<SyllabusUploadRecord> cachedOpt =
                profileRepository.findSyllabusUploadByUserAndHash(authenticatedUserId, validatedMedia.sha256Hex());
        if (cachedOpt.isPresent()) {
            cacheHitCounter.increment();
            int remaining = computeRemainingMonthlyQuota(authenticatedUserId, now);
            SyllabusUploadRecord cached = cachedOpt.get();
            log.info("Syllabus cache hit uploadId={} hashPrefix={}", cached.uploadId(), validatedMedia.sha256Hex().substring(0, 8));
            return new IngestResult(
                    toResponse(
                            new SyllabusUploadRecord(
                                    cached.uploadId(),
                                    cached.userId(),
                                    cached.examDefinitionId(),
                                    cached.examCode(),
                                    cached.examVersion(),
                                    cached.imageHash(),
                                    cached.mimeType(),
                                    cached.byteSize(),
                                    cached.parsedTopics(),
                                    cached.status(),
                                    true,
                                    0,
                                    cached.lastIdempotencyKey(),
                                    cached.schemaVersion(),
                                    false,
                                    cached.expiresAt(),
                                    cached.confirmedAt(),
                                    cached.createdAt(),
                                    cached.updatedAt()
                            ),
                            true,
                            false,
                            remaining
                    ),
                    false
            );
        }

        // 3. Rolling 30-day upload quota check
        Instant quotaCutoff = now.minus(30, ChronoUnit.DAYS);
        int usedInWindow = profileRepository.countNonCachedSyllabusUploadsSince(authenticatedUserId, quotaCutoff);
        if (usedInWindow >= monthlyQuota) {
            quotaExceededCounter.increment();
            throw new ApiProblemException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    ApiErrorCode.SYLLABUS_QUOTA_EXCEEDED,
                    "Monthly syllabus upload quota exceeded",
                    true,
                    "You can upload up to " + monthlyQuota + " unique syllabi per 30 days. Reuse an existing upload or try again after the window resets."
            );
        }

        // 4. Invoke backend vision extraction & deterministic curriculum disambiguation
        SyllabusVisionResult visionResult = ingestionLatencyTimer.record(() ->
                visionGateway.extractScheduleItems(rawBytes, validatedMedia.detectedMimeType(), filenameHint, exam)
        );
        if (visionResult == null || visionResult.items().isEmpty()) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.SYLLABUS_DISAMBIGUATION_REQUIRED,
                    "No syllabus schedule items could be extracted",
                    true,
                    "Upload a clearer photo or PDF showing topic titles and weekly milestones."
            );
        }

        CurriculumTaxonomySnapshot taxonomy = taxonomyCatalog.snapshot();
        Set<String> examSkills = exam.topicWeights().stream()
                .map(w -> w.skillId())
                .collect(Collectors.toSet());

        List<ParsedSyllabusTopicItem> mappedTopics = CurriculumTopicDisambiguator.disambiguateAll(
                visionResult.items(),
                taxonomy,
                examSkills,
                similarityThreshold
        );

        boolean allAutoConfirmed = mappedTopics.stream().allMatch(ParsedSyllabusTopicItem::autoConfirmed);
        SyllabusUploadStatus initialStatus = allAutoConfirmed
                ? SyllabusUploadStatus.PARSED_AUTO_CONFIRMED
                : SyllabusUploadStatus.NEEDS_DISAMBIGUATION;

        Instant expiresAt = now.plus(retentionDays, ChronoUnit.DAYS);
        SyllabusUploadRecord toInsert = new SyllabusUploadRecord(
                UUID.randomUUID(),
                authenticatedUserId,
                exam.id(),
                exam.examCode(),
                exam.examVersion(),
                validatedMedia.sha256Hex(),
                validatedMedia.detectedMimeType(),
                validatedMedia.byteSize(),
                mappedTopics,
                initialStatus,
                false,
                Math.max(0, visionResult.llmTokensUsed()),
                effectiveIdempotencyKey,
                CandidateProfileModels.SYLLABUS_INGESTION_SCHEMA_VERSION,
                false,
                expiresAt,
                null,
                now,
                now
        );

        SyllabusUploadRecord persisted = profileRepository.insertSyllabusUpload(toInsert);
        uploadedCounter.increment();

        int remainingAfter = Math.max(0, monthlyQuota - (usedInWindow + 1));
        log.info(
                "Syllabus ingested uploadId={} examCode={} status={} items={} tokens={}",
                persisted.uploadId(),
                persisted.examCode(),
                persisted.status(),
                mappedTopics.size(),
                persisted.llmTokensUsed()
        );

        return new IngestResult(toResponse(persisted, false, false, remainingAfter), true);
    }

    @Transactional(readOnly = true)
    public SyllabusIngestionResponse getUpload(UUID authenticatedUserId, UUID uploadId) {
        requireEnabled();
        SyllabusUploadRecord record = profileRepository.findSyllabusUploadById(uploadId)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.SYLLABUS_UPLOAD_NOT_FOUND,
                        "Syllabus upload not found",
                        true,
                        "Verify the upload ID or upload your syllabus again."
                ));
        if (!record.userId().equals(authenticatedUserId)) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.CANDIDATE_PROFILE_ACCESS_DENIED,
                    "Access to another learner's syllabus upload is forbidden",
                    false,
                    "Access only your own syllabus uploads."
            );
        }
        int remaining = computeRemainingMonthlyQuota(authenticatedUserId, Instant.now(clock));
        return toResponse(record, record.deduplicatedFromCache(), false, remaining);
    }

    @Transactional
    public SyllabusIngestionResponse confirmDisambiguation(
            UUID authenticatedUserId,
            UUID uploadId,
            SyllabusConfirmRequest request
    ) {
        requireEnabled();
        if (request == null || request.mappings() == null) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.SYLLABUS_DISAMBIGUATION_REQUIRED,
                    "Confirmation mappings are required",
                    true,
                    "Select canonical curriculum skills for ambiguous topics before confirming."
            );
        }
        if (request.schemaVersion() != null
                && !request.schemaVersion().isBlank()
                && !CandidateProfileModels.SYLLABUS_CONFIRM_REQUEST_SCHEMA_VERSION.equals(request.schemaVersion())) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.SYLLABUS_DISAMBIGUATION_REQUIRED,
                    "Unsupported syllabus confirmation schemaVersion",
                    true,
                    "Use schemaVersion 'syllabus-confirm-request-v1'."
            );
        }

        SyllabusUploadRecord record = profileRepository.findSyllabusUploadById(uploadId)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.SYLLABUS_UPLOAD_NOT_FOUND,
                        "Syllabus upload not found",
                        true,
                        "Verify the upload ID before confirming topic mappings."
                ));
        if (!record.userId().equals(authenticatedUserId)) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.CANDIDATE_PROFILE_ACCESS_DENIED,
                    "Cannot confirm another learner's syllabus upload",
                    false,
                    "Authenticate as the owner of the syllabus upload."
            );
        }

        CurriculumTaxonomySnapshot taxonomy = taxonomyCatalog.snapshot();
        Map<Integer, String> overrideByIndex = new HashMap<>();
        for (SyllabusMappingConfirmation mapping : request.mappings()) {
            if (mapping.itemIndex() == null || mapping.confirmedSkillId() == null || mapping.confirmedSkillId().isBlank()) {
                throw new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.SYLLABUS_DISAMBIGUATION_REQUIRED,
                        "Each confirmation mapping requires itemIndex and confirmedSkillId",
                        true,
                        "Choose a valid curriculum topic for each item."
                );
            }
            String skillId = mapping.confirmedSkillId().trim();
            if (!taxonomy.activeSkillIds().contains(skillId)) {
                throw new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.EXAM_CURRICULUM_SKILL_UNKNOWN,
                        "Unknown curriculum skillId in syllabus confirmation: " + skillId,
                        true,
                        "Pick a curriculum skill from the suggested candidates."
                );
            }
            overrideByIndex.put(mapping.itemIndex(), skillId);
        }

        List<ParsedSyllabusTopicItem> updatedTopics = new ArrayList<>(record.parsedTopics().size());
        for (ParsedSyllabusTopicItem item : record.parsedTopics()) {
            String overrideSkill = overrideByIndex.get(item.itemIndex());
            if (overrideSkill != null) {
                String topicId = taxonomy.skillToTopicMap().getOrDefault(overrideSkill, "MATH.GENERAL");
                updatedTopics.add(new ParsedSyllabusTopicItem(
                        item.itemIndex(),
                        item.rawTopicLabel(),
                        item.targetWeek(),
                        item.targetMonth(),
                        item.extractionConfidence(),
                        overrideSkill,
                        topicId,
                        item.similarityScore(),
                        item.autoConfirmed(),
                        true,
                        item.candidates()
                ));
            } else if (item.matchedSkillId() != null && taxonomy.activeSkillIds().contains(item.matchedSkillId())) {
                updatedTopics.add(new ParsedSyllabusTopicItem(
                        item.itemIndex(),
                        item.rawTopicLabel(),
                        item.targetWeek(),
                        item.targetMonth(),
                        item.extractionConfidence(),
                        item.matchedSkillId(),
                        item.matchedTopicId(),
                        item.similarityScore(),
                        item.autoConfirmed(),
                        true,
                        item.candidates()
                ));
            } else {
                throw new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.SYLLABUS_DISAMBIGUATION_REQUIRED,
                        "Item index " + item.itemIndex() + " still requires learner topic selection",
                        true,
                        "Select a curriculum skill for every highlighted syllabus topic."
                );
            }
        }

        Instant now = Instant.now(clock);
        SyllabusUploadRecord confirmed =
                profileRepository.confirmSyllabusUpload(uploadId, authenticatedUserId, updatedTopics, now, now);
        userConfirmedCounter.increment();

        profileRepository.findProfileByUserId(authenticatedUserId).ifPresent(profile ->
                eventPublisher.publishEvent(new CandidateGoalUpdatedEvent(
                        authenticatedUserId,
                        profile.profileId(),
                        confirmed.examCode(),
                        confirmed.examVersion(),
                        profile.targetDate(),
                        profile.dailyStudyMinutes(),
                        profile.weeklyStudyMinutes(),
                        profile.targetScore(),
                        profile.accommodations().extraTimeMultiplier(),
                        profile.requiredDailyMinutes(),
                        profile.feasibilityStatus().name(),
                        "SYLLABUS_CONFIRMED",
                        now
                ))
        );

        int remaining = computeRemainingMonthlyQuota(authenticatedUserId, now);
        return toResponse(confirmed, confirmed.deduplicatedFromCache(), false, remaining);
    }

    private int computeRemainingMonthlyQuota(UUID userId, Instant now) {
        int used = profileRepository.countNonCachedSyllabusUploadsSince(userId, now.minus(30, ChronoUnit.DAYS));
        return Math.max(0, monthlyQuota - used);
    }

    private ExamDefinition resolveExamDefinition(String examCode, String examVersion) {
        if (examVersion != null && !examVersion.isBlank()) {
            return examRepository.findByCodeAndVersion(examCode, examVersion.trim())
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
                        "Select a valid exam definition from the catalog."
                ));
    }

    private static String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() < 4 || trimmed.length() > 128) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.SYLLABUS_DISAMBIGUATION_REQUIRED,
                    "Idempotency key length must be between 4 and 128 characters",
                    true,
                    "Use an idempotency key between 4 and 128 characters."
            );
        }
        return trimmed;
    }

    private static SyllabusIngestionResponse toResponse(
            SyllabusUploadRecord r,
            boolean deduplicatedFromCache,
            boolean idempotentReplay,
            int remainingMonthlyQuota
    ) {
        return new SyllabusIngestionResponse(
                CandidateProfileModels.SYLLABUS_INGESTION_SCHEMA_VERSION,
                r.uploadId(),
                r.userId(),
                r.examDefinitionId(),
                r.examCode(),
                r.examVersion(),
                r.imageHash(),
                r.mimeType(),
                r.byteSize(),
                r.status(),
                deduplicatedFromCache,
                idempotentReplay,
                remainingMonthlyQuota,
                r.parsedTopics(),
                deduplicatedFromCache || idempotentReplay ? 0 : r.llmTokensUsed(),
                false,
                r.expiresAt(),
                r.confirmedAt(),
                r.createdAt(),
                r.updatedAt()
        );
    }

    private void requireEnabled() {
        if (!syllabusIngestionEnabled) {
            throw new ApiProblemException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.EXAM_FEATURE_DISABLED,
                    "Syllabus ingestion feature is currently disabled",
                    true,
                    "Try again later once syllabus ingestion is enabled."
            );
        }
    }
}
