package com.verifiedai.tutoring.application;

import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.billing.application.LearningOsCapability;
import com.verifiedai.mistake.MistakeTaxonomy;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.tutoring.domain.AttemptDiagnosisOwnership;
import com.verifiedai.tutoring.domain.MicroLessonCard;
import com.verifiedai.tutoring.domain.MicroLessonCompletionRecord;
import com.verifiedai.tutoring.domain.MicroLessonCompletionRequest;
import com.verifiedai.tutoring.domain.MicroLessonCompletionResult;
import com.verifiedai.tutoring.domain.MicroLessonHashVerifier;
import com.verifiedai.tutoring.domain.MicroLessonRepository;
import com.verifiedai.tutoring.domain.MicroLessonTriggerSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MicroLessonApplicationService {
    private CapabilityAccessPolicy access;
    @org.springframework.beans.factory.annotation.Value("${verifiedai.billing.learning-os-tiers-enabled:false}")
    private String learningOsFlag;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAccess(CapabilityAccessPolicy access) {
        this.access = access;
    }

    private boolean tierGuardsEnabled() {
        return "true".equalsIgnoreCase(learningOsFlag);
    }

    private void requireAccess(UUID userId, LearningOsCapability capability) {
        if (access == null) {
            throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.ENTITLEMENT_SYNC_FAILED,
                "Entitlement verification unavailable", true, "RETRY");
        }
        access.require(userId, capability);
    }


    private static final Logger log = LoggerFactory.getLogger(MicroLessonApplicationService.class);
    private static final Pattern IDEMPOTENCY_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{8,128}$");
    private static final Pattern OPTION_ID_PATTERN = Pattern.compile("^[A-D]$");

    private final MicroLessonRepository repository;
    private final MicroLessonHashVerifier hashVerifier;
    private final Clock clock;
    private final boolean microLessonsEnabled;
    private final int minDwellMs;
    private final int maxDwellMs;
    private final Counter servedCounter;
    private final Counter completedCounter;
    private final Counter idempotentReplayCounter;
    private final Counter verificationFailureCounter;

    @Autowired
    public MicroLessonApplicationService(
            MicroLessonRepository repository,
            MicroLessonHashVerifier hashVerifier,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            @Value("${verifiedai.tutoring.micro-lessons-enabled:true}") boolean microLessonsEnabled,
            @Value("${verifiedai.tutoring.micro-lesson-min-dwell-ms:1000}") int minDwellMs,
            @Value("${verifiedai.tutoring.micro-lesson-max-dwell-ms:600000}") int maxDwellMs
    ) {
        this(
                repository,
                hashVerifier,
                meterRegistryProvider.getIfAvailable(),
                Clock.systemUTC(),
                microLessonsEnabled,
                minDwellMs,
                maxDwellMs
        );
    }

    public MicroLessonApplicationService(
            MicroLessonRepository repository,
            MicroLessonHashVerifier hashVerifier,
            MeterRegistry meterRegistry,
            Clock clock,
            boolean microLessonsEnabled,
            int minDwellMs,
            int maxDwellMs
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.hashVerifier = Objects.requireNonNull(hashVerifier, "hashVerifier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.microLessonsEnabled = microLessonsEnabled;
        this.minDwellMs = minDwellMs;
        this.maxDwellMs = maxDwellMs;
        if (meterRegistry != null) {
            this.servedCounter = Counter.builder("verifiedai.micro_lesson.served.total").register(meterRegistry);
            this.completedCounter = Counter.builder("verifiedai.micro_lesson.completed.total").register(meterRegistry);
            this.idempotentReplayCounter = Counter.builder("verifiedai.micro_lesson.idempotent_replay.total").register(meterRegistry);
            this.verificationFailureCounter = Counter.builder("verifiedai.micro_lesson.verification_failure.total").register(meterRegistry);
        } else {
            this.servedCounter = null;
            this.completedCounter = null;
            this.idempotentReplayCounter = null;
            this.verificationFailureCounter = null;
        }
    }

    @Transactional(readOnly = true)
    public MicroLessonCard getTriggeredMicroLesson(
            UUID userId,
            String rawMistakeCode,
            String rawLocale,
            String rawTriggerSource,
            UUID sourceAttemptId
    ) {
        ensureFeatureEnabled();
        String mistakeCode = validateCanonicalMistakeCode(rawMistakeCode);
        String locale = normalizeLocale(rawLocale);
        MicroLessonTriggerSource trigger = MicroLessonTriggerSource.fromNullable(rawTriggerSource);

        boolean eligible;
        if (sourceAttemptId != null) {
            AttemptDiagnosisOwnership ownership = repository.checkAttemptDiagnosisOwnership(
                    userId, sourceAttemptId, mistakeCode
            );
            if (ownership == AttemptDiagnosisOwnership.NOT_FOUND_OR_FOREIGN) {
                throw new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.MISTAKE_DIAGNOSIS_NOT_FOUND,
                        "Diagnosed attempt context not found",
                        false,
                        "Open a micro-lesson from one of your active diagnosed attempts."
                );
            }
            eligible = ownership == AttemptDiagnosisOwnership.OWNED_DIAGNOSIS_ELIGIBLE
                    || repository.hasActiveMistakeDiagnosis(userId, mistakeCode)
                    || repository.hasEligibleContextualTrigger(userId, mistakeCode, trigger, sourceAttemptId);
        } else {
            eligible = repository.hasActiveMistakeDiagnosis(userId, mistakeCode)
                    || repository.hasEligibleContextualTrigger(userId, mistakeCode, trigger, null);
        }

        if (!eligible) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.MICRO_LESSON_TRIGGER_NOT_ELIGIBLE,
                    "Micro-lesson trigger is not active for this mistake pattern",
                    true,
                    "Trigger micro-lessons from an active diagnosed focus pattern, Socratic rung 2, or Twin retry."
            );
        }

        if (tierGuardsEnabled()) {
            boolean previouslyEarned = repository.findCompletionsByUserId(userId).stream()
                    .anyMatch(c -> mistakeCode.equals(c.mistakeCode()));
            if (!previouslyEarned) {
                requireAccess(userId, LearningOsCapability.MICRO_LESSON);
            }
        }
        MicroLessonCard card = loadAndVerifyCard(mistakeCode, locale);
        increment(servedCounter);
        log.info("micro_lesson.served mistakeCode={} locale={} triggerSource={} cardId={}",
                mistakeCode, locale, trigger.name(), card.cardId());
        return card.withTriggerContext(trigger, sourceAttemptId);
    }

    @Transactional
    public MicroLessonCompletionResult completeMicroLesson(
            UUID userId,
            String rawMistakeCode,
            MicroLessonCompletionRequest request
    ) {
        ensureFeatureEnabled();
        if (request == null) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "Completion request body is required",
                    true,
                    "Submit a valid micro-lesson completion payload."
            );
        }

        String mistakeCode = validateCanonicalMistakeCode(rawMistakeCode);
        String locale = normalizeLocale(request.locale());
        MicroLessonTriggerSource trigger = MicroLessonTriggerSource.fromNullable(request.triggerSource());
        String idempotencyKey = validateIdempotencyKey(request.idempotencyKey());
        String selectedOptionId = validateSelectedOptionId(request.selectedOptionId());
        validateDwellTime(request.dwellTimeMs());

        if (request.sourceAttemptId() != null) {
            AttemptDiagnosisOwnership ownership = repository.checkAttemptDiagnosisOwnership(
                    userId, request.sourceAttemptId(), mistakeCode
            );
            if (ownership == AttemptDiagnosisOwnership.NOT_FOUND_OR_FOREIGN) {
                throw new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.MISTAKE_DIAGNOSIS_NOT_FOUND,
                        "Diagnosed attempt context not found",
                        false,
                        "Complete micro-lessons using your own attempt context."
                );
            }
        }

        var existingOpt = repository.findCompletionByIdempotencyKey(userId, idempotencyKey);
        if (existingOpt.isPresent()) {
            return buildReplayResult(existingOpt.get(), mistakeCode, locale);
        }

        if (tierGuardsEnabled()) requireAccess(userId, LearningOsCapability.MICRO_LESSON);
        MicroLessonCard card = loadAndVerifyCard(mistakeCode, locale);
        boolean optionExists = card.interactiveQuickCheck().options().stream()
                .anyMatch(opt -> selectedOptionId.equals(opt.optionId()));
        if (!optionExists) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "Selected option is not valid for this micro-lesson quick check",
                    true,
                    "Select one of the presented quick-check options."
            );
        }

        boolean quickCheckPassed = selectedOptionId.equals(card.interactiveQuickCheck().correctOptionId());
        Instant now = Instant.now(clock);
        MicroLessonCompletionRecord record = new MicroLessonCompletionRecord(
                UUID.randomUUID(),
                userId,
                card.cardId(),
                mistakeCode,
                locale,
                request.sourceAttemptId(),
                trigger,
                selectedOptionId,
                quickCheckPassed,
                request.dwellTimeMs(),
                idempotencyKey,
                false,
                now
        );

        try {
            repository.saveCompletion(record);
        } catch (DataIntegrityViolationException ex) {
            var concurrentOpt = repository.findCompletionByIdempotencyKey(userId, idempotencyKey);
            if (concurrentOpt.isPresent()) {
                return buildReplayResult(concurrentOpt.get(), mistakeCode, locale);
            }
            throw ex;
        }

        increment(completedCounter);
        log.info("micro_lesson.completed mistakeCode={} locale={} triggerSource={} quickCheckPassed={}",
                mistakeCode, locale, trigger.name(), quickCheckPassed);

        return new MicroLessonCompletionResult(
                "1.0.0",
                record.completionId(),
                record.cardId(),
                record.mistakeCode(),
                record.locale(),
                record.quickCheckPassed(),
                record.dwellTimeMs(),
                record.completedAt(),
                false,
                false
        );
    }

    private MicroLessonCompletionResult buildReplayResult(
            MicroLessonCompletionRecord existing,
            String mistakeCode,
            String locale
    ) {
        if (!existing.mistakeCode().equals(mistakeCode) || !existing.locale().equals(locale)) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency key already used for another micro-lesson",
                    false,
                    "Use a fresh idempotencyKey for this micro-lesson completion."
            );
        }
        increment(idempotentReplayCounter);
        return new MicroLessonCompletionResult(
                "1.0.0",
                existing.completionId(),
                existing.cardId(),
                existing.mistakeCode(),
                existing.locale(),
                existing.quickCheckPassed(),
                existing.dwellTimeMs(),
                existing.completedAt(),
                true,
                false
        );
    }

    private MicroLessonCard loadAndVerifyCard(String mistakeCode, String locale) {
        MicroLessonCard card = repository.findVerifiedCard(mistakeCode, locale)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.MICRO_LESSON_NOT_FOUND,
                        "Micro-lesson card not found",
                        false,
                        "Continue with guided Socratic hints."
                ));

        if (!hashVerifier.verify(card)) {
            increment(verificationFailureCounter);
            log.warn("micro_lesson.verification_failed mistakeCode={} locale={} cardId={}",
                    mistakeCode, locale, card.cardId());
            throw new ApiProblemException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiErrorCode.MICRO_LESSON_VERIFICATION_FAILED,
                    "Micro-lesson integrity verification failed",
                    true,
                    "Continue with guided Socratic hints while catalog verification recovers."
            );
        }
        return card;
    }

    private void ensureFeatureEnabled() {
        if (!microLessonsEnabled) {
            throw new ApiProblemException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.MICRO_LESSON_FEATURE_DISABLED,
                    "Micro-lessons are currently disabled",
                    true,
                    "Continue with guided Socratic hints."
            );
        }
    }

    private static String validateCanonicalMistakeCode(String rawMistakeCode) {
        if (rawMistakeCode == null || rawMistakeCode.isBlank()) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "mistakeCode is required",
                    true,
                    "Specify a canonical mistakeCode."
            );
        }
        String normalized = rawMistakeCode.trim().toUpperCase(Locale.ROOT);
        if (!MistakeTaxonomy.CATEGORIES.containsKey(normalized)) {
            throw new ApiProblemException(
                    HttpStatus.NOT_FOUND,
                    ApiErrorCode.MICRO_LESSON_NOT_FOUND,
                    "Unknown mistake pattern code",
                    false,
                    "Specify a valid canonical mistakeCode."
            );
        }
        return normalized;
    }

    private static String normalizeLocale(String rawLocale) {
        if (rawLocale == null || rawLocale.isBlank()) {
            return "tr-TR";
        }
        String normalized = rawLocale.trim().replace('_', '-');
        if (normalized.equalsIgnoreCase("tr") || normalized.equalsIgnoreCase("tr-TR")) {
            return "tr-TR";
        }
        if (normalized.equalsIgnoreCase("en") || normalized.equalsIgnoreCase("en-US")) {
            return "en-US";
        }
        throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                "Unsupported micro-lesson locale",
                true,
                "Supported locales are tr-TR and en-US."
        );
    }

    private static String validateIdempotencyKey(String key) {
        if (key == null || !IDEMPOTENCY_PATTERN.matcher(key.trim()).matches()) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "Invalid idempotencyKey format",
                    true,
                    "Provide an 8-128 character idempotencyKey matching ^[A-Za-z0-9_.:-]+$."
            );
        }
        return key.trim();
    }

    private static String validateSelectedOptionId(String optionId) {
        if (optionId == null || !OPTION_ID_PATTERN.matcher(optionId.trim().toUpperCase(Locale.ROOT)).matches()) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "selectedOptionId must be A, B, C, or D",
                    true,
                    "Choose an option identifier from A to D."
            );
        }
        return optionId.trim().toUpperCase(Locale.ROOT);
    }

    private void validateDwellTime(int dwellTimeMs) {
        if (dwellTimeMs < minDwellMs || dwellTimeMs > maxDwellMs) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "dwellTimeMs out of valid bounds",
                    true,
                    "Ensure dwellTimeMs is between " + minDwellMs + " and " + maxDwellMs + " ms."
            );
        }
    }

    private static void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }
}
