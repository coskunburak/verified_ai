package com.verifiedai.exam.application;

import com.verifiedai.exam.application.ActiveExamNoHintGuard.HintGuardResult;
import com.verifiedai.exam.domain.AssembledAssessment;
import com.verifiedai.exam.domain.AssembledAssessmentItem;
import com.verifiedai.exam.domain.AssembledAssessmentRepository;
import com.verifiedai.exam.domain.ExamClockPolicy;
import com.verifiedai.exam.domain.ExamItemResponseState;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.NavigationPolicy;
import com.verifiedai.exam.domain.ExamScratchpadAsset;
import com.verifiedai.exam.domain.ExamSession;
import com.verifiedai.exam.domain.ExamSession.F28VerificationBatchManifest;
import com.verifiedai.exam.domain.ExamSession.ServerClockSync;
import com.verifiedai.exam.domain.ExamSessionItem;
import com.verifiedai.exam.domain.ExamSessionItem.SessionItemOptionSnapshot;
import com.verifiedai.exam.domain.ExamSessionStatus;
import com.verifiedai.exam.events.ExamItemAnsweredEvent;
import com.verifiedai.exam.events.ExamScratchpadAttachedEvent;
import com.verifiedai.exam.events.ExamSessionStartedEvent;
import com.verifiedai.exam.events.ExamSessionSubmittedEvent;
import com.verifiedai.exam.infrastructure.persistence.ExamSessionRepository;
import com.verifiedai.exam.infrastructure.persistence.ExamSessionRepository.IdempotencyRecord;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ExamSessionRuntimeService {

    private static final Logger LOG = LoggerFactory.getLogger(ExamSessionRuntimeService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    public record StartSessionCommand(
            UUID userId,
            UUID assemblyId,
            Integer offlineGraceBufferSeconds,
            Instant clientTimestamp,
            Boolean trainingEligible
    ) {
    }

    public record StartOrResumeResult(
            ExamSession session,
            ServerClockSync clockSync,
            boolean created
    ) {
    }

    public record SessionStateResult(
            ExamSession session,
            ServerClockSync clockSync
    ) {
    }

    public record AutosaveItemCommand(
            String action,
            String selectedOptionKey,
            BigDecimal numericResponseValue,
            Boolean flaggedForReview,
            List<String> eliminatedOptionKeys,
            int deltaTimeSpentSeconds,
            int expectedAutosaveVersion,
            long clientAutosaveSeq,
            Boolean offlineReplay,
            Instant clientTimestamp,
            Boolean trainingEligible
    ) {
    }

    public record AutosaveResult(
            boolean idempotentReplay,
            Map<String, Object> cachedResponse,
            ExamSession session,
            ExamSessionItem item,
            ServerClockSync clockSync
    ) {
    }

    public record AttachScratchpadCommand(
            int pageNumber,
            String storageKey,
            String mimeType,
            long byteSize,
            String sha256Checksum,
            Integer widthPx,
            Integer heightPx,
            String captureSource,
            String bookletPublisher,
            Instant capturedAt,
            Boolean trainingEligible
    ) {
    }

    public record ScratchpadAttachResult(
            boolean idempotentReplay,
            boolean created,
            Map<String, Object> cachedResponse,
            ExamSession session,
            ExamSessionItem item,
            ExamScratchpadAsset asset,
            ServerClockSync clockSync
    ) {
    }

    public record SubmitSessionCommand(
            String submissionReason,
            Instant clientTimestamp,
            Boolean trainingEligible
    ) {
    }

    public record SubmitSessionResult(
            boolean idempotentReplay,
            Map<String, Object> cachedResponse,
            ExamSession session,
            F28VerificationBatchManifest f28Manifest,
            ServerClockSync clockSync
    ) {
    }

    private final ExamSessionRepository examSessionRepository;
    private final AssembledAssessmentRepository assembledAssessmentRepository;
    private final ExamRepository examRepository;
    private final ActiveExamNoHintGuard noHintGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final JsonMapper jsonMapper;
    private volatile boolean sessionRuntimeEnabled;
    private ExamScoringEngineService examScoringEngineService;

    @Autowired(required = false)
    public void setExamScoringEngineService(ExamScoringEngineService examScoringEngineService) {
        this.examScoringEngineService = examScoringEngineService;
    }

    @Autowired
    public ExamSessionRuntimeService(
            ExamSessionRepository examSessionRepository,
            AssembledAssessmentRepository assembledAssessmentRepository,
            ObjectProvider<ExamRepository> examRepositoryProvider,
            ActiveExamNoHintGuard noHintGuard,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            ObjectProvider<Clock> clockProvider,
            @Value("${verifiedai.exam.session-runtime-enabled:false}") boolean sessionRuntimeEnabled
    ) {
        this(
                examSessionRepository,
                assembledAssessmentRepository,
                examRepositoryProvider.getIfAvailable(),
                noHintGuard,
                eventPublisher,
                meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new),
                clockProvider.getIfAvailable(Clock::systemUTC),
                sessionRuntimeEnabled
        );
    }

    public ExamSessionRuntimeService(
            ExamSessionRepository examSessionRepository,
            AssembledAssessmentRepository assembledAssessmentRepository,
            ExamRepository examRepository,
            ActiveExamNoHintGuard noHintGuard,
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry,
            Clock clock,
            boolean sessionRuntimeEnabled
    ) {
        this.examSessionRepository = Objects.requireNonNull(examSessionRepository, "examSessionRepository cannot be null");
        this.assembledAssessmentRepository = Objects.requireNonNull(assembledAssessmentRepository, "assembledAssessmentRepository cannot be null");
        this.examRepository = examRepository;
        this.noHintGuard = Objects.requireNonNull(noHintGuard, "noHintGuard cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.meterRegistry = meterRegistry == null ? new SimpleMeterRegistry() : meterRegistry;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.sessionRuntimeEnabled = sessionRuntimeEnabled;
        this.jsonMapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    public void setSessionRuntimeEnabled(boolean enabled) {
        this.sessionRuntimeEnabled = enabled;
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public StartOrResumeResult startOrResumeSession(UUID authenticatedUserId, StartSessionCommand command) {
        ensureFeatureEnabled();
        Objects.requireNonNull(command, "command is required");
        assertTrainingNotEligible(command.trainingEligible());

        UUID targetUserId = command.userId() != null ? command.userId() : authenticatedUserId;
        enforceBola(authenticatedUserId, targetUserId);
        if (command.assemblyId() == null) {
            throw new ApiProblemException(
                    HttpStatus.NOT_FOUND,
                    ApiErrorCode.EXAM_ASSEMBLY_NOT_FOUND,
                    "assemblyId is required",
                    false,
                    "Provide a valid assembled assessment ID."
            );
        }

        Instant serverNow = Instant.now(clock);
        long skewMs = ExamClockPolicy.computeClientClockSkewMs(serverNow, command.clientTimestamp());
        recordClockSkewIfAnomalous(skewMs);

        Optional<ExamSession> existingActiveOpt =
                examSessionRepository.findActiveByUserIdAndAssemblyId(targetUserId, command.assemblyId());
        if (existingActiveOpt.isPresent()) {
            ExamSession active = existingActiveOpt.get();
            if (isExpiredForStandardRequest(active, serverNow)) {
                autoExpireSession(active, serverNow, skewMs, "SERVER_DEADLINE_EXPIRED");
            } else {
                ExamSession refreshed = active.withUpdatedHeartbeatAndSkew(serverNow, command.clientTimestamp());
                ExamSession saved = examSessionRepository.updateSessionHeader(refreshed);
                return new StartOrResumeResult(saved, saved.computeClockSync(serverNow), false);
            }
        }

        AssembledAssessment assembly = assembledAssessmentRepository.findById(command.assemblyId())
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.EXAM_ASSEMBLY_NOT_FOUND,
                        "Assembled assessment not found: " + command.assemblyId(),
                        false,
                        "Assemble an assessment before starting a timed exam session."
                ));
        enforceBola(authenticatedUserId, assembly.userId());

        ExamDefinitionMetadata metadata = resolveExamMetadata(assembly);
        int graceBufferSeconds = command.offlineGraceBufferSeconds() == null
                ? ExamClockPolicy.DEFAULT_OFFLINE_GRACE_BUFFER_SECONDS
                : ExamClockPolicy.validateOfflineGraceBufferSeconds(command.offlineGraceBufferSeconds());

        int effectiveDurationSeconds = ExamClockPolicy.computeEffectiveDurationSeconds(
                assembly.baseDurationSeconds(),
                assembly.timeMultiplier()
        );
        Instant serverDeadlineAt = ExamClockPolicy.computeServerDeadlineAt(serverNow, effectiveDurationSeconds);
        Instant graceDeadlineAt = ExamClockPolicy.computeGraceDeadlineAt(serverDeadlineAt, graceBufferSeconds);

        UUID sessionId = UUID.randomUUID();
        List<ExamSessionItem> sessionItems = assembly.items().stream()
                .map(item -> toExamSessionItem(sessionId, item, metadata, serverNow))
                .toList();

        ExamSession newSession = new ExamSession(
                sessionId,
                targetUserId,
                assembly.id(),
                assembly.blueprintId(),
                metadata.examCode(),
                assembly.examVersionNumber(),
                ExamSessionStatus.IN_PROGRESS,
                metadata.navigationPolicy(),
                true,
                assembly.accommodationsSnapshot(),
                assembly.baseDurationSeconds(),
                assembly.timeMultiplier(),
                effectiveDurationSeconds,
                graceBufferSeconds,
                serverNow,
                serverDeadlineAt,
                graceDeadlineAt,
                serverNow,
                null,
                null,
                null,
                null,
                sessionItems.size(),
                0,
                0,
                sessionItems.size(),
                0,
                skewMs,
                null,
                false,
                serverNow,
                serverNow,
                sessionItems
        );

        ExamSession persisted = examSessionRepository.saveSession(newSession);
        eventPublisher.publishEvent(new ExamSessionStartedEvent(
                UUID.randomUUID(),
                persisted.id(),
                persisted.userId(),
                persisted.assemblyId(),
                persisted.blueprintId(),
                persisted.examCode(),
                persisted.effectiveDurationSeconds(),
                persisted.serverDeadlineAt(),
                false,
                serverNow
        ));
        Counter.builder("verifiedai.exam.session.started")
                .tag("examCode", persisted.examCode())
                .register(meterRegistry)
                .increment();

        return new StartOrResumeResult(persisted, persisted.computeClockSync(serverNow), true);
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public SessionStateResult getSessionState(UUID authenticatedUserId, UUID sessionId, Instant clientTimestamp) {
        ensureFeatureEnabled();
        ExamSession session = loadOwnedSession(authenticatedUserId, sessionId);
        Instant serverNow = Instant.now(clock);
        long skewMs = ExamClockPolicy.computeClientClockSkewMs(serverNow, clientTimestamp);
        recordClockSkewIfAnomalous(skewMs);

        if (session.status().isActive()) {
            if (ExamClockPolicy.isPastGraceDeadline(serverNow, session.graceDeadlineAt())
                    || (session.status() == ExamSessionStatus.IN_PROGRESS
                    && ExamClockPolicy.isPastServerDeadline(serverNow, session.serverDeadlineAt()))) {
                String reason = session.status() == ExamSessionStatus.OFFLINE_GRACE_BUFFER
                        ? "OFFLINE_GRACE_EXHAUSTED"
                        : "SERVER_DEADLINE_EXPIRED";
                ExamSession expired = autoExpireSession(session, serverNow, skewMs, reason);
                return new SessionStateResult(expired, expired.computeClockSync(serverNow));
            }
            if (clientTimestamp != null) {
                session = examSessionRepository.updateSessionHeader(
                        session.withUpdatedHeartbeatAndSkew(serverNow, clientTimestamp)
                );
            }
        }
        return new SessionStateResult(session, session.computeClockSync(serverNow));
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public AutosaveResult autosaveItem(
            UUID authenticatedUserId,
            UUID sessionId,
            int itemPosition,
            String idempotencyKey,
            AutosaveItemCommand command
    ) {
        ensureFeatureEnabled();
        Objects.requireNonNull(command, "command is required");
        assertTrainingNotEligible(command.trainingEligible());
        String cleanKey = requireIdempotencyKey(idempotencyKey);

        long startNanos = System.nanoTime();
        String outcome = "error";
        try {
            ExamSession session = loadOwnedSession(authenticatedUserId, sessionId);

            String payloadHash = computeAutosavePayloadHash(itemPosition, command);
            Optional<IdempotencyRecord> existingIdemp =
                    examSessionRepository.findIdempotencyRecord(sessionId, "ITEM_AUTOSAVE", cleanKey);
            if (existingIdemp.isPresent()) {
                IdempotencyRecord record = existingIdemp.get();
                if (!record.requestPayloadHash().equals(payloadHash)) {
                    throw new ApiProblemException(
                            HttpStatus.CONFLICT,
                            ApiErrorCode.EXAM_SESSION_IDEMPOTENCY_CONFLICT,
                            "Idempotency-Key '" + cleanKey + "' was already used with a different autosave payload",
                            false,
                            "Generate a new unique Idempotency-Key for each distinct mutation."
                    );
                }
                Map<String, Object> cached = readMap(record.responseSnapshotJson());
                outcome = "idempotent_replay";
                return new AutosaveResult(true, cached, session, null, session.computeClockSync(Instant.now(clock)));
            }

            if (session.status().isTerminal()) {
                throw new ApiProblemException(
                        HttpStatus.CONFLICT,
                        ApiErrorCode.EXAM_SESSION_ALREADY_TERMINATED,
                        "Exam session " + sessionId + " is already in terminal state: " + session.status(),
                        false,
                        "No further edits are permitted after submission."
                );
            }

            Instant serverNow = Instant.now(clock);
            long skewMs = ExamClockPolicy.computeClientClockSkewMs(serverNow, command.clientTimestamp());
            recordClockSkewIfAnomalous(skewMs);

            boolean offlineReplay = Boolean.TRUE.equals(command.offlineReplay());
            if (ExamClockPolicy.isPastGraceDeadline(serverNow, session.graceDeadlineAt())) {
                autoExpireSession(session, serverNow, skewMs, "OFFLINE_GRACE_EXHAUSTED");
                throw expiredException(session);
            }

            if (ExamClockPolicy.isPastServerDeadline(serverNow, session.serverDeadlineAt())) {
                if (ExamClockPolicy.isWithinOfflineGraceWindow(
                        serverNow,
                        session.serverDeadlineAt(),
                        session.graceDeadlineAt(),
                        offlineReplay
                )) {
                    if (session.status() != ExamSessionStatus.OFFLINE_GRACE_BUFFER) {
                        session = session.enterOfflineGraceBuffer(serverNow, skewMs);
                        Counter.builder("verifiedai.exam.session.grace_buffer_entered")
                                .tag("examCode", session.examCode())
                                .register(meterRegistry)
                                .increment();
                    }
                } else {
                    autoExpireSession(session, serverNow, skewMs, "SERVER_DEADLINE_EXPIRED");
                    throw expiredException(session);
                }
            }

            ExamSessionItem existingItem = session.findItemByPosition(itemPosition)
                    .orElseThrow(() -> new ApiProblemException(
                            HttpStatus.NOT_FOUND,
                            ApiErrorCode.EXAM_ITEM_NOT_FOUND,
                            "Exam item not found at position " + itemPosition,
                            false,
                            "Verify the itemPosition within this exam session."
                    ));

            session.validateSectionNavigationAllowed(existingItem);

            ExamSessionItem updatedItem = existingItem.applyAutosave(
                    command.action(),
                    command.selectedOptionKey(),
                    command.numericResponseValue(),
                    command.flaggedForReview(),
                    command.eliminatedOptionKeys(),
                    command.deltaTimeSpentSeconds(),
                    command.expectedAutosaveVersion(),
                    command.clientAutosaveSeq(),
                    serverNow
            );

            ExamSession updatedSession = session.withReplacedItem(updatedItem, serverNow, skewMs);
            ExamSession savedSession = examSessionRepository.updateSessionAndItem(
                    updatedSession,
                    updatedItem,
                    existingItem.autosaveVersion()
            );
            ExamSessionItem savedItem = savedSession.findItemByPosition(itemPosition).orElse(updatedItem);
            ServerClockSync clockSync = savedSession.computeClockSync(serverNow);

            Map<String, Object> snapshot = buildAutosaveSnapshotMap(savedSession, savedItem, clockSync);
            examSessionRepository.saveIdempotencyRecord(new IdempotencyRecord(
                    UUID.randomUUID(),
                    savedSession.id(),
                    savedSession.userId(),
                    "ITEM_AUTOSAVE",
                    cleanKey,
                    payloadHash,
                    HttpStatus.OK.value(),
                    writeJson(snapshot),
                    false,
                    serverNow
            ));

            eventPublisher.publishEvent(new ExamItemAnsweredEvent(
                    UUID.randomUUID(),
                    savedSession.id(),
                    savedSession.userId(),
                    savedItem.itemPosition(),
                    savedItem.questionCode(),
                    savedItem.responseState(),
                    savedItem.autosaveVersion(),
                    savedItem.timeSpentSeconds(),
                    false,
                    serverNow
            ));

            outcome = "saved";
            return new AutosaveResult(false, snapshot, savedSession, savedItem, clockSync);
        } finally {
            String actionTag = command.action() == null ? "BATCH_PATCH" : command.action();
            Counter.builder("verifiedai.exam.session.autosave")
                    .tag("action", actionTag)
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .increment();
            Timer.builder("verifiedai.exam.session.autosave.latency")
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        }
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public ScratchpadAttachResult attachScratchpad(
            UUID authenticatedUserId,
            UUID sessionId,
            int itemPosition,
            String idempotencyKey,
            AttachScratchpadCommand command
    ) {
        ensureFeatureEnabled();
        Objects.requireNonNull(command, "command is required");
        assertTrainingNotEligible(command.trainingEligible());
        String cleanKey = requireIdempotencyKey(idempotencyKey);

        ExamSession session = loadOwnedSession(authenticatedUserId, sessionId);
        String payloadHash = computeScratchpadPayloadHash(itemPosition, command);

        Optional<IdempotencyRecord> existingIdemp =
                examSessionRepository.findIdempotencyRecord(sessionId, "SCRATCHPAD_ATTACH", cleanKey);
        if (existingIdemp.isPresent()) {
            IdempotencyRecord record = existingIdemp.get();
            if (!record.requestPayloadHash().equals(payloadHash)) {
                throw new ApiProblemException(
                        HttpStatus.CONFLICT,
                        ApiErrorCode.EXAM_SESSION_IDEMPOTENCY_CONFLICT,
                        "Idempotency-Key '" + cleanKey + "' conflicts with an earlier scratchpad attachment payload",
                        false,
                        "Use a unique Idempotency-Key per scratchpad photo."
                );
            }
            Map<String, Object> cached = readMap(record.responseSnapshotJson());
            return new ScratchpadAttachResult(
                    true,
                    false,
                    cached,
                    session,
                    null,
                    null,
                    session.computeClockSync(Instant.now(clock))
            );
        }

        if (session.status().isTerminal()) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_SESSION_ALREADY_TERMINATED,
                    "Exam session is already terminated: " + session.status(),
                    false,
                    "Scratchpad images cannot be attached after exam submission."
            );
        }

        Instant serverNow = Instant.now(clock);
        if (ExamClockPolicy.isPastGraceDeadline(serverNow, session.graceDeadlineAt())) {
            autoExpireSession(session, serverNow, session.clientClockSkewMs(), "OFFLINE_GRACE_EXHAUSTED");
            throw expiredException(session);
        }
        if (ExamClockPolicy.isPastServerDeadline(serverNow, session.serverDeadlineAt())
                && session.status() == ExamSessionStatus.IN_PROGRESS) {
            autoExpireSession(session, serverNow, session.clientClockSkewMs(), "SERVER_DEADLINE_EXPIRED");
            throw expiredException(session);
        }

        ExamSessionItem item = session.findItemByPosition(itemPosition)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.EXAM_ITEM_NOT_FOUND,
                        "Exam item not found at position " + itemPosition,
                        false,
                        "Verify itemPosition before attaching scratchpad media."
                ));

        Instant capturedAt = command.capturedAt() != null ? command.capturedAt() : serverNow;
        ExamScratchpadAsset newAsset = new ExamScratchpadAsset(
                UUID.randomUUID(),
                session.id(),
                item.id(),
                session.userId(),
                item.itemPosition(),
                command.pageNumber(),
                command.storageKey(),
                command.mimeType(),
                command.byteSize(),
                command.sha256Checksum(),
                command.widthPx(),
                command.heightPx(),
                command.captureSource(),
                command.bookletPublisher(),
                "QUEUED_FOR_POST_SUBMIT_BATCH",
                false,
                capturedAt,
                serverNow
        );

        ExamSessionItem updatedItem = item.withScratchpadAssetAdded(newAsset, serverNow);
        ExamSession updatedSession = session.withReplacedItem(updatedItem, serverNow, session.clientClockSkewMs());

        ExamSession savedSession = examSessionRepository.saveScratchpadAsset(updatedSession, updatedItem, newAsset);
        ExamSessionItem savedItem = savedSession.findItemByPosition(itemPosition).orElse(updatedItem);
        ServerClockSync clockSync = savedSession.computeClockSync(serverNow);

        Map<String, Object> snapshot = buildScratchpadSnapshotMap(savedSession, savedItem, newAsset, clockSync);
        examSessionRepository.saveIdempotencyRecord(new IdempotencyRecord(
                UUID.randomUUID(),
                savedSession.id(),
                savedSession.userId(),
                "SCRATCHPAD_ATTACH",
                cleanKey,
                payloadHash,
                HttpStatus.CREATED.value(),
                writeJson(snapshot),
                false,
                serverNow
        ));

        eventPublisher.publishEvent(new ExamScratchpadAttachedEvent(
                UUID.randomUUID(),
                savedSession.id(),
                savedSession.userId(),
                savedItem.itemPosition(),
                newAsset.id(),
                newAsset.pageNumber(),
                newAsset.captureSource(),
                newAsset.bookletPublisher(),
                newAsset.sha256Checksum(),
                false,
                serverNow
        ));
        Counter.builder("verifiedai.exam.session.scratchpad_attached")
                .tag("captureSource", newAsset.captureSource())
                .register(meterRegistry)
                .increment();

        return new ScratchpadAttachResult(false, true, snapshot, savedSession, savedItem, newAsset, clockSync);
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public SubmitSessionResult submitSession(
            UUID authenticatedUserId,
            UUID sessionId,
            String idempotencyKey,
            SubmitSessionCommand command
    ) {
        ensureFeatureEnabled();
        Objects.requireNonNull(command, "command is required");
        assertTrainingNotEligible(command.trainingEligible());
        String cleanKey = requireIdempotencyKey(idempotencyKey);

        ExamSession session = loadOwnedSession(authenticatedUserId, sessionId);
        String normalizedReason = normalizeSubmissionReason(command.submissionReason());
        String payloadHash = computeSubmitPayloadHash(normalizedReason);

        Optional<IdempotencyRecord> existingIdemp =
                examSessionRepository.findIdempotencyRecord(sessionId, "SESSION_SUBMIT", cleanKey);
        if (existingIdemp.isPresent()) {
            IdempotencyRecord record = existingIdemp.get();
            if (!record.requestPayloadHash().equals(payloadHash)) {
                throw new ApiProblemException(
                        HttpStatus.CONFLICT,
                        ApiErrorCode.EXAM_SESSION_IDEMPOTENCY_CONFLICT,
                        "Idempotency-Key '" + cleanKey + "' conflicts with an earlier submission request",
                        false,
                        "Reuse the same submission reason when retrying submit."
                );
            }
            Map<String, Object> cached = readMap(record.responseSnapshotJson());
            return new SubmitSessionResult(
                    true,
                    cached,
                    session,
                    session.f28VerificationBatchManifest(),
                    session.computeClockSync(Instant.now(clock))
            );
        }

        if (session.status().isTerminal()) {
            if (cleanKey.equals(session.submitIdempotencyKey())) {
                return new SubmitSessionResult(
                        true,
                        null,
                        session,
                        session.f28VerificationBatchManifest(),
                        session.computeClockSync(Instant.now(clock))
                );
            }
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_SESSION_ALREADY_TERMINATED,
                    "Exam session is already finalized with status " + session.status(),
                    false,
                    "View the finalized exam receipt."
            );
        }

        Instant serverNow = Instant.now(clock);
        long skewMs = ExamClockPolicy.computeClientClockSkewMs(serverNow, command.clientTimestamp());
        recordClockSkewIfAnomalous(skewMs);

        ExamSessionStatus finalStatus;
        String effectiveReason = normalizedReason;
        if ("USER_ABANDONED".equals(normalizedReason)) {
            finalStatus = ExamSessionStatus.ABANDONED;
        } else if (ExamClockPolicy.isPastGraceDeadline(serverNow, session.graceDeadlineAt())
                || "OFFLINE_GRACE_EXHAUSTED".equals(normalizedReason)) {
            finalStatus = ExamSessionStatus.EXPIRED_AUTO_SUBMITTED;
            effectiveReason = ExamClockPolicy.isPastGraceDeadline(serverNow, session.graceDeadlineAt())
                    ? "OFFLINE_GRACE_EXHAUSTED"
                    : normalizedReason;
        } else if (ExamClockPolicy.isPastServerDeadline(serverNow, session.serverDeadlineAt())
                && session.status() == ExamSessionStatus.IN_PROGRESS) {
            finalStatus = ExamSessionStatus.EXPIRED_AUTO_SUBMITTED;
            effectiveReason = "SERVER_DEADLINE_EXPIRED";
        } else if ("SERVER_DEADLINE_EXPIRED".equals(normalizedReason)) {
            finalStatus = ExamSessionStatus.EXPIRED_AUTO_SUBMITTED;
        } else {
            finalStatus = ExamSessionStatus.SUBMITTED;
        }

        ExamSession finalized = session.submit(finalStatus, effectiveReason, cleanKey, serverNow, skewMs);
        ExamSession saved = examSessionRepository.updateSessionHeader(finalized);
        F28VerificationBatchManifest manifest = saved.f28VerificationBatchManifest() != null
                ? saved.f28VerificationBatchManifest()
                : saved.buildF28Manifest(serverNow);
        ServerClockSync clockSync = saved.computeClockSync(serverNow);

        Map<String, Object> snapshot = buildSubmitSnapshotMap(saved, manifest, clockSync);
        examSessionRepository.saveIdempotencyRecord(new IdempotencyRecord(
                UUID.randomUUID(),
                saved.id(),
                saved.userId(),
                "SESSION_SUBMIT",
                cleanKey,
                payloadHash,
                HttpStatus.OK.value(),
                writeJson(snapshot),
                false,
                serverNow
        ));

        publishSubmittedEvent(saved, serverNow);
        triggerDeterministicAutoScoring(saved);
        return new SubmitSessionResult(false, snapshot, saved, manifest, clockSync);
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public HintGuardResult checkNoHintGuard(UUID authenticatedUserId, UUID sessionId, String requestedSurface) {
        ensureFeatureEnabled();
        ExamSession session = loadOwnedSession(authenticatedUserId, sessionId);
        Instant serverNow = Instant.now(clock);
        if (session.status().isActive() && isExpiredForStandardRequest(session, serverNow)) {
            session = autoExpireSession(session, serverNow, session.clientClockSkewMs(), "SERVER_DEADLINE_EXPIRED");
        }
        return noHintGuard.evaluateForSession(session, requestedSurface, serverNow);
    }

    private ExamSession autoExpireSession(ExamSession session, Instant serverNow, long skewMs, String reason) {
        if (session.status().isTerminal()) {
            return session;
        }
        String key = "auto_expire_" + session.id();
        ExamSession finalized = session.submit(
                ExamSessionStatus.EXPIRED_AUTO_SUBMITTED,
                reason,
                key,
                serverNow,
                skewMs
        );
        ExamSession saved = examSessionRepository.updateSessionHeader(finalized);
        publishSubmittedEvent(saved, serverNow);
        triggerDeterministicAutoScoring(saved);
        LOG.info("Auto-expired exam sessionId={} examCode={} reason={}", saved.id(), saved.examCode(), reason);
        return saved;
    }

    private void triggerDeterministicAutoScoring(ExamSession saved) {
        if (examScoringEngineService != null && examScoringEngineService.isScoringEngineEnabled()) {
            examScoringEngineService.computeOrGetScoreSummary(saved.userId(), saved.id());
        }
    }

    private void publishSubmittedEvent(ExamSession saved, Instant serverNow) {
        int queuedCount = saved.f28VerificationBatchManifest() == null
                ? 0
                : saved.f28VerificationBatchManifest().totalAssets();
        eventPublisher.publishEvent(new ExamSessionSubmittedEvent(
                UUID.randomUUID(),
                saved.id(),
                saved.userId(),
                saved.assemblyId(),
                saved.blueprintId(),
                saved.examCode(),
                saved.status(),
                saved.submissionReason(),
                saved.answeredItemCount(),
                saved.flaggedItemCount(),
                saved.omittedItemCount(),
                saved.scratchpadAttachmentCount(),
                queuedCount,
                false,
                serverNow
        ));
        Counter.builder("verifiedai.exam.session.submitted")
                .tag("finalStatus", saved.status().name())
                .tag("submissionReason", saved.submissionReason())
                .register(meterRegistry)
                .increment();
    }

    private ExamSession loadOwnedSession(UUID authenticatedUserId, UUID sessionId) {
        if (authenticatedUserId == null) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_CANDIDATE_ACCESS_DENIED,
                    "Authenticated user identity is required",
                    false,
                    "Sign in and retry."
            );
        }
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.EXAM_SESSION_NOT_FOUND,
                        "Exam session not found: " + sessionId,
                        false,
                        "Verify the sessionId or start a new timed exam session."
                ));
        enforceBola(authenticatedUserId, session.userId());
        return session;
    }

    private void ensureFeatureEnabled() {
        if (!sessionRuntimeEnabled) {
            throw new ApiProblemException(
                    HttpStatus.NOT_FOUND,
                    ApiErrorCode.EXAM_SESSION_RUNTIME_FEATURE_DISABLED,
                    "Timed mock exam runtime feature is disabled",
                    false,
                    "Enable verifiedai.exam.session-runtime-enabled to use timed exam sessions."
            );
        }
    }

    private static void enforceBola(UUID authenticatedUserId, UUID ownerUserId) {
        if (authenticatedUserId == null || !authenticatedUserId.equals(ownerUserId)) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_CANDIDATE_ACCESS_DENIED,
                    "Learner cannot access another user's exam session or assessment",
                    false,
                    "Access only exam sessions owned by the authenticated learner."
            );
        }
    }

    private static void assertTrainingNotEligible(Boolean trainingEligible) {
        if (Boolean.TRUE.equals(trainingEligible)) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.EXAM_IDEMPOTENCY_KEY_REQUIRED,
                    "Idempotency-Key header is required for mutating exam runtime operations",
                    true,
                    "Send a unique Idempotency-Key header and retry."
            );
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > 128) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.EXAM_IDEMPOTENCY_KEY_REQUIRED,
                    "Idempotency-Key must be at most 128 characters",
                    true,
                    "Provide an Idempotency-Key up to 128 characters."
            );
        }
        return trimmed;
    }

    private static boolean isExpiredForStandardRequest(ExamSession session, Instant serverNow) {
        if (ExamClockPolicy.isPastGraceDeadline(serverNow, session.graceDeadlineAt())) {
            return true;
        }
        return session.status() == ExamSessionStatus.IN_PROGRESS
                && ExamClockPolicy.isPastServerDeadline(serverNow, session.serverDeadlineAt());
    }

    private void recordClockSkewIfAnomalous(long skewMs) {
        if (ExamClockPolicy.isClockSkewAnomaly(skewMs)) {
            Counter.builder("verifiedai.exam.session.clock_skew_anomaly")
                    .register(meterRegistry)
                    .increment();
        }
    }

    private record ExamDefinitionMetadata(
            String examCode,
            String navigationPolicy,
            String defaultSectionCode,
            int defaultSectionOrder,
            BigDecimal defaultNegativeMarkingWeight
    ) {
    }

    private ExamDefinitionMetadata resolveExamMetadata(AssembledAssessment assembly) {
        if (examRepository != null) {
            Optional<ExamDefinition> defOpt = examRepository.findCatalog(null, null, true).stream()
                    .filter(d -> d.id().equals(assembly.examDefinitionId()))
                    .findFirst();
            if (defOpt.isPresent()) {
                ExamDefinition def = defOpt.get();
                String navPolicy = def.navigationPolicy() == NavigationPolicy.SECTION_LOCKED
                        ? "SECTION_LOCKED"
                        : "FREE_NAVIGATION";
                String secCode = def.sections().isEmpty()
                        ? inferDefaultSectionCode(def.examCode())
                        : def.sections().getFirst().sectionCode();
                int secOrder = def.sections().isEmpty() ? 1 : def.sections().getFirst().order();
                BigDecimal negWeight = def.wrongPenaltyFactor() != null
                        ? def.wrongPenaltyFactor()
                        : new BigDecimal("0.2500");
                return new ExamDefinitionMetadata(def.examCode(), navPolicy, secCode, secOrder, negWeight);
            }
        }
        String bpCode = assembly.blueprintCode() == null ? "" : assembly.blueprintCode().toUpperCase();
        String examCode = bpCode.contains("AYT")
                ? "TR_YKS_AYT_MATH"
                : (bpCode.contains("SAT") ? "INTL_SAT_MATH" : "TR_YKS_TYT_MATH");
        String secCode = inferDefaultSectionCode(examCode);
        return new ExamDefinitionMetadata(examCode, "FREE_NAVIGATION", secCode, 1, new BigDecimal("0.2500"));
    }

    private static String inferDefaultSectionCode(String examCode) {
        if (examCode == null) {
            return "TYT_MATH";
        }
        if (examCode.contains("AYT")) {
            return "AYT_MATH";
        }
        if (examCode.contains("SAT")) {
            return "SAT_MOD_1";
        }
        if (examCode.contains("AP_CALC")) {
            return "CALC_PART_A";
        }
        if (examCode.contains("IB")) {
            return "IB_PAPER_1";
        }
        return "TYT_MATH";
    }

    private static ExamSessionItem toExamSessionItem(
            UUID sessionId,
            AssembledAssessmentItem item,
            ExamDefinitionMetadata metadata,
            Instant serverNow
    ) {
        List<SessionItemOptionSnapshot> optionsSnapshot = item.orderedOptions().stream()
                .map(opt -> new SessionItemOptionSnapshot(
                        opt.optionKey(),
                        opt.displayOrder(),
                        opt.contentMarkdown(),
                        opt.contentLatex(),
                        false
                ))
                .toList();

        boolean scratchpadRequired = item.estimatedTimeSeconds() >= 60
                || "HARD".equals(item.difficultyBand().name())
                || "EXPERT".equals(item.difficultyBand().name());

        return new ExamSessionItem(
                UUID.randomUUID(),
                sessionId,
                item.sequenceNumber(),
                item.questionItemId(),
                item.questionCode(),
                metadata.defaultSectionCode(),
                metadata.defaultSectionOrder(),
                item.topicCode(),
                item.itemFormat().name(),
                item.difficultyBand(),
                item.stemMarkdown(),
                optionsSnapshot,
                item.altTextDescription(),
                item.allocatedPoints(),
                metadata.defaultNegativeMarkingWeight(),
                ExamItemResponseState.UNVISITED,
                null,
                null,
                false,
                List.of(),
                0,
                0,
                0L,
                null,
                scratchpadRequired,
                false,
                serverNow,
                serverNow,
                List.of()
        );
    }

    private static ApiProblemException expiredException(ExamSession session) {
        return new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.EXAM_SESSION_EXPIRED,
                "Server deadline expired for exam session " + session.id(),
                false,
                "Review the auto-submitted session summary."
        );
    }

    private static String normalizeSubmissionReason(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            return "USER_INITIATED";
        }
        String trimmed = rawReason.trim();
        if (!ExamSession.ALLOWED_SUBMISSION_REASONS.contains(trimmed)) {
            throw new IllegalArgumentException("Invalid submissionReason: " + trimmed);
        }
        return trimmed;
    }

    private String computeAutosavePayloadHash(int itemPosition, AutosaveItemCommand cmd) {
        String canonical = String.join("|",
                String.valueOf(itemPosition),
                String.valueOf(cmd.action()),
                String.valueOf(cmd.selectedOptionKey()),
                cmd.numericResponseValue() == null ? "null" : cmd.numericResponseValue().toPlainString(),
                String.valueOf(cmd.flaggedForReview()),
                String.valueOf(cmd.eliminatedOptionKeys()),
                String.valueOf(cmd.deltaTimeSpentSeconds()),
                String.valueOf(cmd.expectedAutosaveVersion()),
                String.valueOf(cmd.clientAutosaveSeq()),
                String.valueOf(cmd.offlineReplay())
        );
        return sha256Hex(canonical);
    }

    private String computeScratchpadPayloadHash(int itemPosition, AttachScratchpadCommand cmd) {
        String canonical = String.join("|",
                String.valueOf(itemPosition),
                String.valueOf(cmd.pageNumber()),
                String.valueOf(cmd.storageKey()),
                String.valueOf(cmd.mimeType()),
                String.valueOf(cmd.byteSize()),
                String.valueOf(cmd.sha256Checksum()),
                String.valueOf(cmd.captureSource()),
                String.valueOf(cmd.bookletPublisher())
        );
        return sha256Hex(canonical);
    }

    private String computeSubmitPayloadHash(String submissionReason) {
        return sha256Hex("SUBMIT|" + submissionReason);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private Map<String, Object> buildAutosaveSnapshotMap(
            ExamSession session,
            ExamSessionItem item,
            ServerClockSync clockSync
    ) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schemaVersion", "exam-session-item-autosave-response-v1");
        m.put("sessionId", session.id().toString());
        m.put("status", session.status().name());
        m.put("itemPosition", item.itemPosition());
        m.put("questionCode", item.questionCode());
        m.put("responseState", item.responseState().name());
        m.put("selectedOptionKey", item.selectedOptionKey());
        m.put("numericResponseValue", item.numericResponseValue());
        m.put("flaggedForReview", item.flaggedForReview());
        m.put("eliminatedOptionKeys", item.eliminatedOptionKeys());
        m.put("timeSpentSeconds", item.timeSpentSeconds());
        m.put("autosaveVersion", item.autosaveVersion());
        m.put("clientAutosaveSeq", item.clientAutosaveSeq());
        m.put("answeredItemCount", session.answeredItemCount());
        m.put("flaggedItemCount", session.flaggedItemCount());
        m.put("omittedItemCount", session.omittedItemCount());
        m.put("clockSync", toClockSyncMap(clockSync));
        m.put("llmTokensUsed", 0);
        m.put("trainingEligible", false);
        return m;
    }

    private Map<String, Object> buildScratchpadSnapshotMap(
            ExamSession session,
            ExamSessionItem item,
            ExamScratchpadAsset asset,
            ServerClockSync clockSync
    ) {
        Map<String, Object> assetMap = new LinkedHashMap<>();
        assetMap.put("id", asset.id().toString());
        assetMap.put("sessionId", asset.sessionId().toString());
        assetMap.put("sessionItemId", asset.sessionItemId().toString());
        assetMap.put("itemPosition", asset.itemPosition());
        assetMap.put("pageNumber", asset.pageNumber());
        assetMap.put("storageKey", asset.storageKey());
        assetMap.put("mimeType", asset.mimeType());
        assetMap.put("byteSize", asset.byteSize());
        assetMap.put("sha256Checksum", asset.sha256Checksum());
        assetMap.put("widthPx", asset.widthPx());
        assetMap.put("heightPx", asset.heightPx());
        assetMap.put("captureSource", asset.captureSource());
        assetMap.put("bookletPublisher", asset.bookletPublisher());
        assetMap.put("f28VerificationStatus", asset.f28VerificationStatus());
        assetMap.put("capturedAt", asset.capturedAt().toString());
        assetMap.put("trainingEligible", false);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schemaVersion", "exam-scratchpad-upload-response-v1");
        m.put("sessionId", session.id().toString());
        m.put("itemPosition", item.itemPosition());
        m.put("scratchpadAttachmentCount", session.scratchpadAttachmentCount());
        m.put("itemScratchpadCount", item.scratchpadAssets().size());
        m.put("asset", assetMap);
        m.put("clockSync", toClockSyncMap(clockSync));
        m.put("llmTokensUsed", 0);
        m.put("trainingEligible", false);
        return m;
    }

    private Map<String, Object> buildSubmitSnapshotMap(
            ExamSession session,
            F28VerificationBatchManifest manifest,
            ServerClockSync clockSync
    ) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schemaVersion", "exam-session-submit-response-v1");
        m.put("sessionId", session.id().toString());
        m.put("assemblyId", session.assemblyId().toString());
        m.put("blueprintId", session.blueprintId().toString());
        m.put("examCode", session.examCode());
        m.put("status", session.status().name());
        m.put("submissionReason", session.submissionReason());
        m.put("submittedAt", session.submittedAt().toString());
        m.put("submitIdempotencyKey", session.submitIdempotencyKey());
        m.put("totalItemCount", session.totalItemCount());
        m.put("answeredItemCount", session.answeredItemCount());
        m.put("flaggedItemCount", session.flaggedItemCount());
        m.put("omittedItemCount", session.omittedItemCount());
        m.put("scratchpadAttachmentCount", session.scratchpadAttachmentCount());
        m.put("f28VerificationBatchManifest", jsonMapper.convertValue(manifest, MAP_TYPE));
        m.put("clockSync", toClockSyncMap(clockSync));
        m.put("llmTokensUsed", 0);
        m.put("trainingEligible", false);
        return m;
    }

    private static Map<String, Object> toClockSyncMap(ServerClockSync clockSync) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("serverNow", clockSync.serverNow().toString());
        c.put("remainingSeconds", clockSync.remainingSeconds());
        c.put("graceRemainingSeconds", clockSync.graceRemainingSeconds());
        c.put("clientClockSkewMs", clockSync.clientClockSkewMs());
        c.put("clockSkewAnomaly", clockSync.clockSkewAnomaly());
        c.put("deadlineExpired", clockSync.deadlineExpired());
        c.put("offlineGraceActive", clockSync.offlineGraceActive());
        return c;
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize JSON in ExamSessionRuntimeService", ex);
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return jsonMapper.readValue(json, MAP_TYPE);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to deserialize JSON idempotency snapshot", ex);
        }
    }
}
