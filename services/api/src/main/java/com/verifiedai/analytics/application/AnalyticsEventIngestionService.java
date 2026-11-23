package com.verifiedai.analytics.application;

import com.verifiedai.analytics.domain.AnalyticsEventCatalog;
import com.verifiedai.analytics.domain.AnalyticsEventSource;
import com.verifiedai.analytics.domain.AnalyticsEventSpec;
import com.verifiedai.analytics.domain.AnalyticsPayloadSanitizer;
import com.verifiedai.analytics.domain.AnalyticsPayloadSanitizer.RawEventInput;
import com.verifiedai.analytics.domain.AnalyticsPayloadSanitizer.SanitizedAnalyticsEvent;
import com.verifiedai.analytics.infrastructure.AnalyticsEventJdbcRepository;
import com.verifiedai.analytics.infrastructure.AnalyticsEventJdbcRepository.StoredAnalyticsEvent;
import com.verifiedai.analytics.infrastructure.AnalyticsProperties;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Application orchestration service implementing {@link AnalyticsEventPort}, client single/batch ingestion,
 * strict server-authority validation, advisory-locked deduplication, and fail-closed feature gating.
 */
@Service
public class AnalyticsEventIngestionService implements AnalyticsEventPort {

    public static final String INGEST_RESPONSE_SCHEMA_VERSION = "analytics-event-ingest-response-v1";
    public static final String CATALOG_SCHEMA_VERSION = "analytics-event-catalog-v1";

    private final AnalyticsEventJdbcRepository repository;
    private final AnalyticsProperties properties;
    private final AnalyticsMetrics metrics;
    private final AnalyticsPayloadSanitizer sanitizer;
    private final Clock clock;
    private final TransactionTemplate requiresNewTx;

    public AnalyticsEventIngestionService(
        AnalyticsEventJdbcRepository repository,
        AnalyticsProperties properties,
        AnalyticsMetrics metrics,
        Clock clock,
        PlatformTransactionManager transactionManager
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.sanitizer = new AnalyticsPayloadSanitizer(
            properties.maxPropertyCount(),
            properties.maxPropertiesBytes()
        );
        TransactionTemplate tx = new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "transactionManager must not be null")
        );
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = tx;
    }

    @Transactional(readOnly = true)
    public CatalogView getCatalogForApi(UUID userId) {
        requireActiveAccount(userId);
        requireEventCatalogEnabled();
        List<CatalogEntryView> entries = AnalyticsEventCatalog.allSpecs().stream()
            .map(spec -> new CatalogEntryView(
                spec.eventName(),
                spec.domain().name(),
                spec.clientAllowed(),
                spec.serverAllowed(),
                spec.allowedPropertyKeys().stream().sorted().toList()
            ))
            .toList();
        return new CatalogView(
            CATALOG_SCHEMA_VERSION,
            AnalyticsEventCatalog.CATALOG_VERSION,
            entries.size(),
            entries,
            0,
            false,
            clock.instant()
        );
    }

    public SingleIngestResult ingestClientEvent(UUID userId, ClientEventInput input, String correlationId) {
        requireActiveAccount(userId);
        requireEventCatalogEnabled();
        if (input == null) {
            throw validationFailed("Analytics event request body is required");
        }

        String normalizedCorrelationId = normalizeCorrelationId(correlationId);
        String resolvedTier = repository.resolveUserEntitlementTier(userId);
        SanitizedAnalyticsEvent sanitized;
        try {
            sanitized = sanitizer.sanitize(
                new RawEventInput(
                    input.clientEventId(),
                    input.eventName(),
                    input.emittedBy(),
                    input.sourceModule(),
                    input.entitlementTier(),
                    input.locale(),
                    input.appVersion(),
                    input.sessionRefHash(),
                    input.latencyMs(),
                    0L,
                    input.properties(),
                    input.occurredAt(),
                    input.trainingEligible()
                ),
                AnalyticsEventSource.CLIENT,
                resolvedTier
            );
        } catch (ApiProblemException ex) {
            metrics.recordRejected(ex.code().name());
            throw ex;
        }

        try {
            AnalyticsIngestOutcome outcome = persistIdempotently(userId, sanitized, normalizedCorrelationId);
            boolean replayed = "REPLAYED".equals(outcome.status());
            return new SingleIngestResult(
                new BatchIngestView(
                    INGEST_RESPONSE_SCHEMA_VERSION,
                    replayed ? 0 : 1,
                    replayed ? 1 : 0,
                    0,
                    List.of(outcome),
                    0,
                    false,
                    outcome.processedAt()
                ),
                replayed
            );
        } catch (ApiProblemException ex) {
            metrics.recordRejected(ex.code().name());
            throw ex;
        }
    }

    public BatchIngestView ingestClientBatch(
        UUID userId,
        List<ClientEventInput> events,
        Boolean trainingEligible,
        String correlationId
    ) {
        requireActiveAccount(userId);
        requireEventCatalogEnabled();
        if (Boolean.TRUE.equals(trainingEligible)) {
            metrics.recordRejected(ApiErrorCode.ANALYTICS_EVENT_VALIDATION_FAILED.name());
            throw validationFailed("Analytics batch requests are never training eligible (ADR-006)");
        }
        if (events == null || events.isEmpty()) {
            metrics.recordRejected(ApiErrorCode.ANALYTICS_EVENT_VALIDATION_FAILED.name());
            throw validationFailed("Analytics batch must contain at least 1 event");
        }
        if (events.size() > properties.maxBatchSize()) {
            metrics.recordRejected(ApiErrorCode.ANALYTICS_BATCH_TOO_LARGE.name());
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.ANALYTICS_BATCH_TOO_LARGE,
                "Analytics batch exceeds maximum size of " + properties.maxBatchSize() + " events",
                false,
                "SPLIT_BATCH"
            );
        }

        String normalizedCorrelationId = normalizeCorrelationId(correlationId);
        String resolvedTier = repository.resolveUserEntitlementTier(userId);

        int acceptedCount = 0;
        int replayedCount = 0;
        int rejectedCount = 0;
        List<AnalyticsIngestOutcome> outcomes = new ArrayList<>(events.size());

        for (ClientEventInput item : events) {
            Instant itemNow = clock.instant();
            String safeClientId = item == null ? null : sanitizeDisplayClientEventId(item.clientEventId());
            String safeEventName = item == null ? null : sanitizeDisplayEventName(item.eventName());
            try {
                if (item == null) {
                    throw validationFailed("Analytics batch item must not be null");
                }
                SanitizedAnalyticsEvent sanitized = sanitizer.sanitize(
                    new RawEventInput(
                        item.clientEventId(),
                        item.eventName(),
                        item.emittedBy(),
                        item.sourceModule(),
                        item.entitlementTier(),
                        item.locale(),
                        item.appVersion(),
                        item.sessionRefHash(),
                        item.latencyMs(),
                        0L,
                        item.properties(),
                        item.occurredAt(),
                        item.trainingEligible()
                    ),
                    AnalyticsEventSource.CLIENT,
                    resolvedTier
                );
                AnalyticsIngestOutcome outcome = persistIdempotently(userId, sanitized, normalizedCorrelationId);
                if ("REPLAYED".equals(outcome.status())) {
                    replayedCount++;
                } else {
                    acceptedCount++;
                }
                outcomes.add(outcome);
            } catch (ApiProblemException ex) {
                rejectedCount++;
                metrics.recordRejected(ex.code().name());
                outcomes.add(new AnalyticsIngestOutcome(
                    safeClientId,
                    safeEventName,
                    "REJECTED",
                    null,
                    ex.code().name(),
                    itemNow
                ));
            }
        }

        return new BatchIngestView(
            INGEST_RESPONSE_SCHEMA_VERSION,
            acceptedCount,
            replayedCount,
            rejectedCount,
            outcomes,
            0,
            false,
            clock.instant()
        );
    }

    @Override
    public AnalyticsIngestOutcome recordServerEvent(RecordAnalyticsEventCommand command) {
        Instant now = clock.instant();
        if (command == null || command.userId() == null) {
            metrics.recordRejected(ApiErrorCode.ANALYTICS_EVENT_VALIDATION_FAILED.name());
            return new AnalyticsIngestOutcome(
                command == null ? null : sanitizeDisplayClientEventId(command.clientEventId()),
                command == null ? null : sanitizeDisplayEventName(command.eventName()),
                "REJECTED",
                null,
                ApiErrorCode.ANALYTICS_EVENT_VALIDATION_FAILED.name(),
                now
            );
        }
        if (!properties.eventCatalogEnabled()) {
            String domain = AnalyticsEventCatalog.find(command.eventName())
                .map(AnalyticsEventSpec::domain)
                .map(Enum::name)
                .orElse("UNKNOWN");
            metrics.recordIngested(domain, AnalyticsEventSource.SERVER.name(), "disabled");
            return new AnalyticsIngestOutcome(
                sanitizeDisplayClientEventId(command.clientEventId()),
                sanitizeDisplayEventName(command.eventName()),
                "REJECTED",
                null,
                ApiErrorCode.ANALYTICS_FEATURE_DISABLED.name(),
                now
            );
        }

        try {
            if (!repository.isAccountActive(command.userId())) {
                metrics.recordRejected(ApiErrorCode.ACCOUNT_NOT_ACTIVE.name());
                return new AnalyticsIngestOutcome(
                    sanitizeDisplayClientEventId(command.clientEventId()),
                    sanitizeDisplayEventName(command.eventName()),
                    "REJECTED",
                    null,
                    ApiErrorCode.ACCOUNT_NOT_ACTIVE.name(),
                    now
                );
            }
            String resolvedTier = repository.resolveUserEntitlementTier(command.userId());
            SanitizedAnalyticsEvent sanitized = sanitizer.sanitize(
                new RawEventInput(
                    command.clientEventId(),
                    command.eventName(),
                    AnalyticsEventSource.SERVER.name(),
                    command.sourceModule(),
                    command.entitlementTier(),
                    command.locale(),
                    command.appVersion(),
                    command.sessionRefHash(),
                    command.latencyMs(),
                    command.aiCostMicros(),
                    command.properties(),
                    command.occurredAt() != null ? command.occurredAt() : now,
                    false
                ),
                AnalyticsEventSource.SERVER,
                resolvedTier
            );
            return persistIdempotently(command.userId(), sanitized, normalizeCorrelationId(command.correlationId()));
        } catch (ApiProblemException ex) {
            metrics.recordRejected(ex.code().name());
            return new AnalyticsIngestOutcome(
                sanitizeDisplayClientEventId(command.clientEventId()),
                sanitizeDisplayEventName(command.eventName()),
                "REJECTED",
                null,
                ex.code().name(),
                now
            );
        }
    }

    private AnalyticsIngestOutcome persistIdempotently(
        UUID userId,
        SanitizedAnalyticsEvent sanitized,
        String correlationId
    ) {
        try {
            return requiresNewTx.execute(status -> {
                repository.lockUserClientEvent(userId, sanitized.clientEventId());
                Instant now = clock.instant();

                Optional<StoredAnalyticsEvent> existingOpt = repository.findByUserIdAndClientEventId(
                    userId,
                    sanitized.clientEventId()
                );
                if (existingOpt.isPresent()) {
                    StoredAnalyticsEvent existing = existingOpt.get();
                    if (!existing.payloadHash().equals(sanitized.payloadHash())) {
                        throw new ApiProblemException(
                            HttpStatus.CONFLICT,
                            ApiErrorCode.ANALYTICS_IDEMPOTENCY_CONFLICT,
                            "clientEventId already exists with a different analytics payload",
                            false,
                            "USE_NEW_CLIENT_EVENT_ID"
                        );
                    }
                    metrics.recordIngested(sanitized.eventDomain().name(), sanitized.emittedBy().name(), "replayed");
                    return new AnalyticsIngestOutcome(
                        sanitized.clientEventId(),
                        sanitized.eventName(),
                        "REPLAYED",
                        existing.id(),
                        null,
                        now
                    );
                }

                UUID eventId = UUID.randomUUID();
                repository.insertEvent(new StoredAnalyticsEvent(
                    eventId,
                    userId,
                    sanitized.clientEventId(),
                    sanitized.payloadHash(),
                    sanitized.eventName(),
                    sanitized.eventDomain().name(),
                    sanitized.catalogVersion(),
                    sanitized.emittedBy().name(),
                    sanitized.sourceModule(),
                    sanitized.entitlementTier(),
                    sanitized.locale(),
                    sanitized.appVersion(),
                    sanitized.sessionRefHash(),
                    sanitized.latencyMs(),
                    sanitized.aiCostMicros(),
                    sanitized.canonicalPropertiesJson(),
                    correlationId,
                    sanitized.occurredAt(),
                    now,
                    false
                ));
                metrics.recordIngested(sanitized.eventDomain().name(), sanitized.emittedBy().name(), "accepted");
                return new AnalyticsIngestOutcome(
                    sanitized.clientEventId(),
                    sanitized.eventName(),
                    "ACCEPTED",
                    eventId,
                    null,
                    now
                );
            });
        } catch (DataIntegrityViolationException duplicate) {
            Optional<StoredAnalyticsEvent> existingOpt = repository.findByUserIdAndClientEventId(
                userId,
                sanitized.clientEventId()
            );
            if (existingOpt.isPresent() && existingOpt.get().payloadHash().equals(sanitized.payloadHash())) {
                metrics.recordIngested(sanitized.eventDomain().name(), sanitized.emittedBy().name(), "replayed");
                return new AnalyticsIngestOutcome(
                    sanitized.clientEventId(),
                    sanitized.eventName(),
                    "REPLAYED",
                    existingOpt.get().id(),
                    null,
                    clock.instant()
                );
            }
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.ANALYTICS_IDEMPOTENCY_CONFLICT,
                "clientEventId already exists with a different analytics payload",
                false,
                "USE_NEW_CLIENT_EVENT_ID"
            );
        }
    }

    private void requireActiveAccount(UUID userId) {
        if (userId == null || !repository.isAccountActive(userId)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ACCOUNT_NOT_ACTIVE,
                "Account is not active",
                false,
                "SIGN_IN"
            );
        }
    }

    private void requireEventCatalogEnabled() {
        if (!properties.eventCatalogEnabled()) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.ANALYTICS_FEATURE_DISABLED,
                "Analytics event catalog is disabled",
                true,
                "RETRY_LATER"
            );
        }
    }

    private static ApiProblemException validationFailed(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.ANALYTICS_EVENT_VALIDATION_FAILED,
            message,
            false,
            "FIX_EVENT_PAYLOAD"
        );
    }

    private static String normalizeCorrelationId(String correlationId) {
        String effective = (correlationId == null || correlationId.isBlank())
            ? CorrelationIds.current()
            : correlationId.trim();
        if (effective == null || effective.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return effective.length() > 64 ? effective.substring(0, 64) : effective;
    }

    private static String sanitizeDisplayClientEventId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }

    private static String sanitizeDisplayEventName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    public record ClientEventInput(
        String clientEventId,
        String eventName,
        String emittedBy,
        String sourceModule,
        String entitlementTier,
        String locale,
        String appVersion,
        String sessionRefHash,
        Integer latencyMs,
        Map<String, Object> properties,
        Instant occurredAt,
        Boolean trainingEligible
    ) {
    }

    public record BatchIngestView(
        String schemaVersion,
        int acceptedCount,
        int replayedCount,
        int rejectedCount,
        List<AnalyticsIngestOutcome> results,
        int llmTokensUsed,
        boolean trainingEligible,
        Instant processedAt
    ) {
        public BatchIngestView {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }

    public record SingleIngestResult(BatchIngestView view, boolean replayed) {
    }

    public record CatalogEntryView(
        String eventName,
        String eventDomain,
        boolean clientAllowed,
        boolean serverAllowed,
        List<String> allowedPropertyKeys
    ) {
        public CatalogEntryView {
            allowedPropertyKeys = allowedPropertyKeys == null ? List.of() : List.copyOf(allowedPropertyKeys);
        }
    }

    public record CatalogView(
        String schemaVersion,
        String catalogVersion,
        int eventCount,
        List<CatalogEntryView> events,
        int llmTokensUsed,
        boolean trainingEligible,
        Instant generatedAt
    ) {
        public CatalogView {
            events = events == null ? List.of() : List.copyOf(events);
        }
    }
}
