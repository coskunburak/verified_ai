package com.verifiedai.problem.application.asset;

import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.problem.application.session.ProblemSessionLifecyclePolicy;
import com.verifiedai.problem.application.session.ProblemSessionMetrics;
import com.verifiedai.problem.domain.model.asset.ProblemAssetKind;
import com.verifiedai.problem.domain.model.asset.ProblemAssetSource;
import com.verifiedai.problem.domain.model.asset.ProblemAssetStatus;
import com.verifiedai.problem.domain.model.session.ProblemSessionStatus;
import com.verifiedai.problem.domain.port.PresignedProblemAssetUpload;
import com.verifiedai.problem.domain.port.ProblemAssetObjectMetadata;
import com.verifiedai.problem.domain.port.ProblemAssetObjectNotFoundException;
import com.verifiedai.problem.domain.port.ProblemAssetStorage;
import com.verifiedai.problem.domain.port.ProblemAssetStorageUnavailableException;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemAssetJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemSessionJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.repository.ProblemAssetJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.repository.ProblemSessionJpaRepository;
import com.verifiedai.problem.infrastructure.storage.ProblemAssetStorageProperties;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemAssetUploadApplicationService {
    private static final Pattern CHECKSUM_PATTERN = Pattern.compile("^[a-f0-9]{64}$");
    private static final int MAX_PIXEL_DIMENSION = 12000;
    private static final int MAX_PAGE_COUNT = 500;

    private final ProblemSessionJpaRepository sessionRepository;
    private final ProblemAssetJpaRepository assetRepository;
    private final ProblemAssetStorage storage;
    private final ProblemAssetStorageProperties properties;
    private final CapabilityAccessPolicy capabilityAccessPolicy;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final ProblemAssetUploadMetrics metrics;
    private final ProblemSessionLifecyclePolicy lifecyclePolicy;
    private final ProblemSessionMetrics sessionMetrics;

    ProblemAssetUploadApplicationService(
        ProblemSessionJpaRepository sessionRepository,
        ProblemAssetJpaRepository assetRepository,
        ProblemAssetStorage storage,
        ProblemAssetStorageProperties properties,
        CapabilityAccessPolicy capabilityAccessPolicy,
        JdbcTemplate jdbcTemplate,
        Clock clock,
        ProblemAssetUploadMetrics metrics,
        ProblemSessionLifecyclePolicy lifecyclePolicy,
        ProblemSessionMetrics sessionMetrics
    ) {
        this.sessionRepository = sessionRepository;
        this.assetRepository = assetRepository;
        this.storage = storage;
        this.properties = properties;
        this.capabilityAccessPolicy = capabilityAccessPolicy;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.metrics = metrics;
        this.lifecyclePolicy = lifecyclePolicy;
        this.sessionMetrics = sessionMetrics;
    }

    @Transactional
    public ProblemAssetUploadReservationResult reserve(UUID userId, String idempotencyKey, ProblemAssetUploadCommand command) {
        long started = System.nanoTime();
        String assetKindMetric = "UNKNOWN";
        try {
            requireIdempotencyKey(idempotencyKey);
            requireCommand(command);
            ProblemAssetKind assetKind = normalizeKind(command.assetKind());
            assetKindMetric = assetKind.name();
            requireActiveAccount(userId);
            capabilityAccessPolicy.requireBasicSolve(userId);
            ValidatedUploadRequest request = validate(command, assetKind);
            String requestHash = requestHash(request);

            var existing = assetRepository.findByUserIdAndReservationIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                ProblemAssetJpaEntity asset = existing.get();
                rejectDifferentIdempotentPayload(asset, requestHash);
                if (asset.pending() && !asset.uploadExpiresAt().isAfter(clock.instant())) {
                    asset.markExpired(clock.instant());
                    metrics.pendingExpired();
                    throw problem(HttpStatus.GONE, ApiErrorCode.UPLOAD_RESERVATION_EXPIRED, "Upload reservation has expired", true, "RETRY");
                }
                return reservationResult(asset, remainingTtl(asset));
            }

            Instant now = clock.instant();
            UUID sessionId = UUID.randomUUID();
            UUID assetId = UUID.randomUUID();
            String objectKey = objectKey(sessionId, assetId);
            Instant expiresAt = now.plus(properties.presignTtl());
            ProblemSessionJpaEntity session = ProblemSessionJpaEntity.create(sessionId, userId, request.source(), now);
            ProblemAssetJpaEntity asset = ProblemAssetJpaEntity.pending(
                assetId,
                sessionId,
                userId,
                request.source(),
                request.kind(),
                objectKey,
                request.contentType(),
                request.sizeBytes(),
                request.checksumSha256(),
                decimal(request.cropX()),
                decimal(request.cropY()),
                decimal(request.cropWidth()),
                decimal(request.cropHeight()),
                request.imageWidth(),
                request.imageHeight(),
                request.pageCount(),
                expiresAt,
                now,
                idempotencyKey,
                requestHash
            );
            sessionRepository.saveAndFlush(session);
            assetRepository.saveAndFlush(asset);
            ProblemAssetUploadReservationResult result = reservationResult(asset, properties.presignTtl());
            metrics.reservationSuccess(asset.assetKind());
            metrics.presignLatency(System.nanoTime() - started);
            return result;
        } catch (ApiProblemException exception) {
            metrics.reservationFailure(assetKindMetric, exception.code().name());
            throw exception;
        } catch (ProblemAssetStorageUnavailableException exception) {
            metrics.reservationFailure(assetKindMetric, ApiErrorCode.UPLOAD_STORAGE_UNAVAILABLE.name());
            throw storageProblem(exception);
        }
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public ProblemAssetUploadCompletionResult complete(UUID userId, UUID uploadId, String idempotencyKey) {
        long started = System.nanoTime();
        requireIdempotencyKey(idempotencyKey);
        requireActiveAccount(userId);
        ProblemAssetJpaEntity asset = assetRepository.findByIdAndUserIdForUpdate(uploadId, userId)
            .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_FORBIDDEN, "Upload reservation was not found", false, "RETRY"));
        String assetKind = asset.assetKind();
        try {
            ProblemSessionJpaEntity session = sessionRepository.findByIdAndUserIdForUpdate(asset.problemSessionId(), userId)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_FORBIDDEN, "Problem session was not found", false, "RETRY"));
            if (asset.available()) {
                return completionResult(asset, session);
            }
            if (!asset.pending()) {
                throw problem(HttpStatus.CONFLICT, ApiErrorCode.UPLOAD_INVALID_STATE, "Upload reservation is not pending", false, "RETRY");
            }
            Instant now = clock.instant();
            if (!asset.uploadExpiresAt().isAfter(now)) {
                asset.markExpired(now);
                metrics.pendingExpired();
                throw problem(HttpStatus.GONE, ApiErrorCode.UPLOAD_RESERVATION_EXPIRED, "Upload reservation has expired", true, "RETRY");
            }

            verifyStoredObject(asset);
            asset.markAvailable(now);
            transition(session, ProblemSessionStatus.ASSET_UPLOADED, now);
            metrics.completeSuccess(assetKind);
            metrics.completionVerificationLatency(System.nanoTime() - started);
            return completionResult(asset, session);
        } catch (ApiProblemException exception) {
            metrics.completeFailure(assetKind, exception.code().name());
            throw exception;
        }
    }

    @Transactional
    public int expirePendingUploads(int limit) {
        Instant now = clock.instant();
        int expired = 0;
        var assets = assetRepository.findByStatusAndUploadExpiresAtBeforeOrderByUploadExpiresAtAsc(
            ProblemAssetStatus.PENDING.name(),
            now,
            PageRequest.of(0, Math.max(1, limit))
        );
        for (ProblemAssetJpaEntity asset : assets) {
            try {
                storage.deleteIfExists(asset.objectKey());
                asset.markExpired(now);
                metrics.pendingExpired();
                expired += 1;
            } catch (ProblemAssetStorageUnavailableException exception) {
                throw storageProblem(exception);
            }
        }
        return expired;
    }

    private ProblemAssetUploadReservationResult reservationResult(ProblemAssetJpaEntity asset, Duration ttl) {
        PresignedProblemAssetUpload presigned = storage.presignPut(
            asset.objectKey(),
            asset.contentType(),
            asset.sizeBytes(),
            ttl
        );
        return new ProblemAssetUploadReservationResult(
            asset.id(),
            asset.problemSessionId(),
            asset.id(),
            asset.status(),
            presigned.uploadUrl().toString(),
            asset.uploadExpiresAt(),
            presigned.requiredHeaders()
        );
    }

    private void verifyStoredObject(ProblemAssetJpaEntity asset) {
        try {
            ProblemAssetObjectMetadata metadata = storage.head(asset.objectKey());
            if (metadata.sizeBytes() != asset.sizeBytes()) {
                storage.deleteIfExists(asset.objectKey());
                metrics.sizeMismatch(asset.assetKind());
                throw problem(HttpStatus.CONFLICT, ApiErrorCode.UPLOAD_SIZE_MISMATCH, "Uploaded object size does not match reservation", true, "RETRY");
            }
            if (!asset.contentType().equalsIgnoreCase(normalizeContentType(metadata.contentType()))) {
                storage.deleteIfExists(asset.objectKey());
                throw problem(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.UPLOAD_CONTENT_TYPE_UNSUPPORTED,
                    "Uploaded object content type does not match reservation",
                    true,
                    "RETRY"
                );
            }
            String actualChecksum = storage.sha256Hex(asset.objectKey());
            if (!asset.checksumValue().equals(actualChecksum)) {
                storage.deleteIfExists(asset.objectKey());
                metrics.checksumMismatch(asset.assetKind());
                throw problem(HttpStatus.CONFLICT, ApiErrorCode.UPLOAD_CHECKSUM_MISMATCH, "Uploaded object checksum does not match reservation", true, "RETRY");
            }
        } catch (ProblemAssetObjectNotFoundException exception) {
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.UPLOAD_OBJECT_NOT_FOUND, "Uploaded object was not found", true, "RETRY");
        } catch (ProblemAssetStorageUnavailableException exception) {
            throw storageProblem(exception);
        }
    }

    private ValidatedUploadRequest validate(ProblemAssetUploadCommand command, ProblemAssetKind assetKind) {
        ProblemAssetSource source = normalizeSource(command.source());
        String contentType = normalizeContentType(command.contentType());
        if (!properties.allowedContentTypes().contains(contentType)) {
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.UPLOAD_CONTENT_TYPE_UNSUPPORTED, "Content type is not supported", true, "RETRY");
        }
        if (command.sizeBytes() <= 0 || command.sizeBytes() > properties.maxSizeBytes()) {
            throw problem(HttpStatus.PAYLOAD_TOO_LARGE, ApiErrorCode.UPLOAD_TOO_LARGE, "Upload is too large", true, "RETRY");
        }
        String checksum = normalizeChecksum(command.checksumSha256());
        Double cropX = command.cropX() == null ? 0 : command.cropX();
        Double cropY = command.cropY() == null ? 0 : command.cropY();
        Double cropWidth = command.cropWidth() == null ? 1 : command.cropWidth();
        Double cropHeight = command.cropHeight() == null ? 1 : command.cropHeight();
        validateCrop(cropX, cropY, cropWidth, cropHeight);

        Integer imageWidth = command.imageWidth();
        Integer imageHeight = command.imageHeight();
        Integer pageCount = command.pageCount();
        if (assetKind == ProblemAssetKind.IMAGE) {
            if (!"image/jpeg".equals(contentType)) {
                throw problem(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.UPLOAD_CONTENT_TYPE_UNSUPPORTED, "Image uploads must be JPEG in Sprint 4.2", true, "RETRY");
            }
            validateImageDimensions(imageWidth, imageHeight);
            pageCount = null;
        } else {
            if (!"application/pdf".equals(contentType)) {
                throw problem(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.UPLOAD_CONTENT_TYPE_UNSUPPORTED, "PDF uploads must use application/pdf", true, "RETRY");
            }
            imageWidth = null;
            imageHeight = null;
            if (pageCount != null && (pageCount <= 0 || pageCount > MAX_PAGE_COUNT)) {
                throw problem(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.UPLOAD_INVALID_STATE, "PDF page count is out of range", true, "RETRY");
            }
        }

        return new ValidatedUploadRequest(
            source,
            assetKind,
            contentType,
            command.sizeBytes(),
            checksum,
            imageWidth,
            imageHeight,
            pageCount,
            cropX,
            cropY,
            cropWidth,
            cropHeight
        );
    }

    private static void validateImageDimensions(Integer imageWidth, Integer imageHeight) {
        if (imageWidth == null || imageHeight == null) {
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.UPLOAD_INVALID_STATE, "Image dimensions are required", true, "RETRY");
        }
        if (imageWidth <= 0 || imageWidth > MAX_PIXEL_DIMENSION || imageHeight <= 0 || imageHeight > MAX_PIXEL_DIMENSION) {
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.UPLOAD_INVALID_STATE, "Image dimensions are out of range", true, "RETRY");
        }
    }

    private static void validateCrop(double x, double y, double width, double height) {
        if (x < 0 || y < 0 || width <= 0 || height <= 0 || x + width > 1 || y + height > 1) {
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.UPLOAD_INVALID_STATE, "Crop metadata is out of range", true, "RETRY");
        }
    }

    private static ProblemAssetKind normalizeKind(String value) {
        if (value == null || value.isBlank()) {
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.UPLOAD_INVALID_STATE, "Asset kind is required", true, "RETRY");
        }
        try {
            return ProblemAssetKind.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.UPLOAD_INVALID_STATE, "Asset kind is unsupported", true, "RETRY");
        }
    }

    private static ProblemAssetSource normalizeSource(String value) {
        if (value == null || value.isBlank()) {
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.UPLOAD_INVALID_STATE, "Capture source is required", true, "RETRY");
        }
        String normalized = value.trim()
            .replace("photoLibrary", "photo_library")
            .replace('-', '_')
            .toUpperCase(Locale.ROOT);
        try {
            return ProblemAssetSource.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.UPLOAD_INVALID_STATE, "Capture source is unsupported", true, "RETRY");
        }
    }

    private static String normalizeContentType(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeChecksum(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!CHECKSUM_PATTERN.matcher(normalized).matches()) {
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.UPLOAD_INVALID_STATE, "SHA-256 checksum is invalid", true, "RETRY");
        }
        return normalized;
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw problem(HttpStatus.BAD_REQUEST, ApiErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key is required", true, "RETRY");
        }
    }

    private static void requireCommand(ProblemAssetUploadCommand command) {
        if (command == null) {
            throw problem(HttpStatus.BAD_REQUEST, ApiErrorCode.UPLOAD_INVALID_STATE, "Upload reservation request body is required", true, "RETRY");
        }
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

    private static ApiProblemException problem(
        HttpStatus status,
        ApiErrorCode code,
        String title,
        boolean recoverable,
        String userAction
    ) {
        return new ApiProblemException(status, code, title, recoverable, userAction);
    }

    private static ApiProblemException storageProblem(RuntimeException exception) {
        return new ApiProblemException(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiErrorCode.UPLOAD_STORAGE_UNAVAILABLE,
            "Object storage is temporarily unavailable",
            true,
            "RETRY"
        );
    }

    private static void rejectDifferentIdempotentPayload(ProblemAssetJpaEntity asset, String requestHash) {
        if (!asset.reservationRequestHash().equals(requestHash)) {
            throw problem(HttpStatus.CONFLICT, ApiErrorCode.IDEMPOTENCY_KEY_REUSED, "Idempotency-Key was reused with different upload metadata", false, "RETRY");
        }
    }

    private static String requestHash(ValidatedUploadRequest request) {
        String fingerprint = String.join("|",
            request.source().name(),
            request.kind().name(),
            request.contentType(),
            Long.toString(request.sizeBytes()),
            request.checksumSha256(),
            String.valueOf(request.imageWidth()),
            String.valueOf(request.imageHeight()),
            String.valueOf(request.pageCount()),
            String.valueOf(request.cropX()),
            String.valueOf(request.cropY()),
            String.valueOf(request.cropWidth()),
            String.valueOf(request.cropHeight())
        );
        return sha256Hex(fingerprint);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String objectKey(UUID sessionId, UUID assetId) {
        return "problem-assets/" + sessionId + "/" + assetId + "/original";
    }

    private Duration remainingTtl(ProblemAssetJpaEntity asset) {
        Duration duration = Duration.between(clock.instant(), asset.uploadExpiresAt());
        return duration.isNegative() || duration.isZero() ? Duration.ofSeconds(1) : duration;
    }

    private static ProblemAssetUploadCompletionResult completionResult(
        ProblemAssetJpaEntity asset,
        ProblemSessionJpaEntity session
    ) {
        return new ProblemAssetUploadCompletionResult(
            asset.id(),
            asset.problemSessionId(),
            asset.id(),
            session.status(),
            asset.status(),
            asset.availableAt()
        );
    }

    private void transition(ProblemSessionJpaEntity session, ProblemSessionStatus target, Instant now) {
        ProblemSessionStatus current = ProblemSessionStatus.valueOf(session.status());
        if (current == target) {
            return;
        }
        if (target == ProblemSessionStatus.ASSET_UPLOADED
            && current != ProblemSessionStatus.CREATED
            && current != ProblemSessionStatus.FAILED) {
            return;
        }
        lifecyclePolicy.requireTransition(current, target);
        if (target == ProblemSessionStatus.ASSET_UPLOADED) {
            session.markAssetUploaded(now);
        }
        sessionMetrics.lifecycleTransition(current.name(), target.name());
    }

    private static BigDecimal decimal(Double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private record ValidatedUploadRequest(
        ProblemAssetSource source,
        ProblemAssetKind kind,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        Integer imageWidth,
        Integer imageHeight,
        Integer pageCount,
        Double cropX,
        Double cropY,
        Double cropWidth,
        Double cropHeight
    ) {
    }
}
