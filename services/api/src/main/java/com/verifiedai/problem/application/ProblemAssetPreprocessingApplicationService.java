package com.verifiedai.problem.application;

import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.problem.domain.model.ProblemAssetDerivativeKind;
import com.verifiedai.problem.domain.model.ProblemAssetKind;
import com.verifiedai.problem.domain.model.ProblemAssetQualityOutcome;
import com.verifiedai.problem.domain.model.ProblemAssetQualitySeverity;
import com.verifiedai.problem.domain.port.ProblemAssetObjectNotFoundException;
import com.verifiedai.problem.domain.port.ProblemAssetStorage;
import com.verifiedai.problem.domain.port.ProblemAssetStorageUnavailableException;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetDerivativeJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetDerivativeJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetQualityEvidenceJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetQualityEvidenceJpaRepository;
import com.verifiedai.problem.infrastructure.preprocessing.Java2DProblemAssetImagePreprocessor;
import com.verifiedai.problem.infrastructure.preprocessing.ProblemAssetImagePreprocessingException;
import com.verifiedai.problem.infrastructure.preprocessing.ProblemAssetImagePreprocessingResult;
import com.verifiedai.problem.infrastructure.preprocessing.ProblemAssetPreprocessingProperties;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemAssetPreprocessingApplicationService {
    private final ProblemAssetJpaRepository assetRepository;
    private final ProblemAssetDerivativeJpaRepository derivativeRepository;
    private final ProblemAssetQualityEvidenceJpaRepository qualityEvidenceRepository;
    private final ProblemAssetStorage storage;
    private final Java2DProblemAssetImagePreprocessor imagePreprocessor;
    private final ProblemAssetPreprocessingProperties properties;
    private final CapabilityAccessPolicy capabilityAccessPolicy;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final ProblemAssetPreprocessingMetrics metrics;

    ProblemAssetPreprocessingApplicationService(
        ProblemAssetJpaRepository assetRepository,
        ProblemAssetDerivativeJpaRepository derivativeRepository,
        ProblemAssetQualityEvidenceJpaRepository qualityEvidenceRepository,
        ProblemAssetStorage storage,
        Java2DProblemAssetImagePreprocessor imagePreprocessor,
        ProblemAssetPreprocessingProperties properties,
        CapabilityAccessPolicy capabilityAccessPolicy,
        JdbcTemplate jdbcTemplate,
        Clock clock,
        ProblemAssetPreprocessingMetrics metrics
    ) {
        this.assetRepository = assetRepository;
        this.derivativeRepository = derivativeRepository;
        this.qualityEvidenceRepository = qualityEvidenceRepository;
        this.storage = storage;
        this.imagePreprocessor = imagePreprocessor;
        this.properties = properties;
        this.capabilityAccessPolicy = capabilityAccessPolicy;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional
    public ProblemAssetPreprocessingResult preprocess(UUID userId, UUID sourceAssetId) {
        long started = System.nanoTime();
        metrics.started();
        requireActiveAccount(userId);
        capabilityAccessPolicy.requireBasicSolve(userId);
        ProblemAssetJpaEntity source = assetRepository.findByIdAndUserIdForUpdate(sourceAssetId, userId)
            .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_FORBIDDEN, "Problem asset was not found", false, "RETRY"));
        requireAvailable(source);

        if (currentOcrDerivative(source).isPresent()) {
            ProblemAssetPreprocessingResult result = resultFor(source);
            metrics.latency(System.nanoTime() - started);
            return result;
        }

        try {
            ProblemAssetPreprocessingResult result = ProblemAssetKind.PDF.name().equals(source.assetKind())
                ? recordFailure(source, "PDF_UNSUPPORTED")
                : preprocessImage(source);
            if (!ProblemAssetQualityOutcome.FAILED.name().equals(result.qualityOutcome())) {
                metrics.success(result.qualityOutcome() == null ? result.preprocessingStatus() : result.qualityOutcome());
            }
            metrics.latency(System.nanoTime() - started);
            return result;
        } catch (ProblemAssetStorageUnavailableException exception) {
            throw storageProblem(exception);
        }
    }

    @Transactional(readOnly = true)
    public ProblemAssetPreprocessingResult getPreprocessing(UUID userId, UUID sourceAssetId) {
        requireActiveAccount(userId);
        ProblemAssetJpaEntity source = assetRepository.findByIdAndUserId(sourceAssetId, userId)
            .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_FORBIDDEN, "Problem asset was not found", false, "RETRY"));
        return resultFor(source);
    }

    private ProblemAssetPreprocessingResult preprocessImage(ProblemAssetJpaEntity source) {
        if (!ProblemAssetKind.IMAGE.name().equals(source.assetKind())) {
            return recordFailure(source, "ASSET_KIND_UNSUPPORTED");
        }
        if (!"image/jpeg".equalsIgnoreCase(source.contentType())) {
            return recordFailure(source, "IMAGE_CONTENT_TYPE_UNSUPPORTED");
        }

        List<String> writtenKeys = new ArrayList<>();
        try {
            byte[] sourceBytes = storage.readBytes(source.objectKey(), properties.maxSourceBytes());
            ProblemAssetImagePreprocessingResult processed = imagePreprocessor.process(
                sourceBytes,
                source.cropX().doubleValue(),
                source.cropY().doubleValue(),
                source.cropWidth().doubleValue(),
                source.cropHeight().doubleValue()
            );
            Instant now = clock.instant();
            var derivativesByKind = processed.derivatives()
                .stream()
                .collect(Collectors.toMap(ProblemAssetImagePreprocessingResult.DerivativeImage::kind, Function.identity()));
            ProblemAssetImagePreprocessingResult.DerivativeImage ocr = derivativesByKind.get(ProblemAssetDerivativeKind.OCR_OPTIMIZED);
            ProblemAssetImagePreprocessingResult.DerivativeImage thumbnail = derivativesByKind.get(ProblemAssetDerivativeKind.THUMBNAIL);
            if (ocr == null || thumbnail == null) {
                throw new ProblemAssetImagePreprocessingException(
                    ApiErrorCode.ASSET_DERIVATIVE_GENERATION_FAILED,
                    "DERIVATIVE_KIND_MISSING",
                    "Preprocessor did not produce required derivatives"
                );
            }

            UUID ocrDerivativeId = UUID.randomUUID();
            UUID thumbnailDerivativeId = UUID.randomUUID();
            String ocrKey = derivativeObjectKey(source, ocrDerivativeId, ProblemAssetDerivativeKind.OCR_OPTIMIZED);
            String thumbnailKey = derivativeObjectKey(source, thumbnailDerivativeId, ProblemAssetDerivativeKind.THUMBNAIL);
            storage.putObject(ocrKey, "image/jpeg", ocr.bytes());
            writtenKeys.add(ocrKey);
            storage.putObject(thumbnailKey, "image/jpeg", thumbnail.bytes());
            writtenKeys.add(thumbnailKey);

            ProblemAssetDerivativeJpaEntity ocrEntity = readyDerivative(source, ocrDerivativeId, ocrKey, ocr, processed, true, now);
            ProblemAssetDerivativeJpaEntity thumbnailEntity = readyDerivative(source, thumbnailDerivativeId, thumbnailKey, thumbnail, processed, false, now);
            derivativeRepository.save(ocrEntity);
            derivativeRepository.save(thumbnailEntity);
            qualityEvidenceRepository.saveAll(processed.qualitySignals()
                .stream()
                .map(signal -> qualityEvidence(ocrEntity, signal, now))
                .toList());
            derivativeRepository.flush();
            qualityEvidenceRepository.flush();
            processed.qualitySignals()
                .stream()
                .filter(signal -> signal.severity() == ProblemAssetQualitySeverity.WARNING)
                .forEach(signal -> metrics.warning(signal.signalType().name()));
            processed.derivatives().forEach(derivative -> metrics.derivativeGenerated(derivative.kind().name()));
            return resultFor(source);
        } catch (ProblemAssetImagePreprocessingException exception) {
            cleanupWrittenObjects(writtenKeys);
            return recordFailure(source, exception.failureCode());
        } catch (ProblemAssetObjectNotFoundException exception) {
            cleanupWrittenObjects(writtenKeys);
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.UPLOAD_OBJECT_NOT_FOUND, "Source asset object was not found", true, "RETRY");
        } catch (ProblemAssetStorageUnavailableException exception) {
            cleanupWrittenObjects(writtenKeys);
            throw exception;
        } catch (RuntimeException exception) {
            cleanupWrittenObjects(writtenKeys);
            throw exception;
        }
    }

    private ProblemAssetPreprocessingResult recordFailure(ProblemAssetJpaEntity source, String failureCode) {
        if (currentOcrDerivative(source).isPresent()) {
            return resultFor(source);
        }
        Instant now = clock.instant();
        ProblemAssetDerivativeJpaEntity failure = ProblemAssetDerivativeJpaEntity.failed(
            UUID.randomUUID(),
            source.id(),
            source.problemSessionId(),
            source.userId(),
            ProblemAssetDerivativeKind.OCR_OPTIMIZED,
            source.imageWidth() == null ? 1 : source.imageWidth(),
            source.imageHeight() == null ? 1 : source.imageHeight(),
            source.cropX(),
            source.cropY(),
            source.cropWidth(),
            source.cropHeight(),
            properties.processorName(),
            properties.processorVersion(),
            properties.configurationVersion(),
            failureCode,
            now
        );
        derivativeRepository.saveAndFlush(failure);
        metrics.failed(failureCode);
        return resultFor(source);
    }

    private ProblemAssetDerivativeJpaEntity readyDerivative(
        ProblemAssetJpaEntity source,
        UUID derivativeId,
        String objectKey,
        ProblemAssetImagePreprocessingResult.DerivativeImage derivative,
        ProblemAssetImagePreprocessingResult processed,
        boolean selectedForRecognition,
        Instant now
    ) {
        return ProblemAssetDerivativeJpaEntity.ready(
            derivativeId,
            source.id(),
            source.problemSessionId(),
            source.userId(),
            derivative.kind(),
            selectedForRecognition,
            objectKey,
            derivative.bytes().length,
            sha256Hex(derivative.bytes()),
            derivative.width(),
            derivative.height(),
            processed.sourceWidth(),
            processed.sourceHeight(),
            source.cropX(),
            source.cropY(),
            source.cropWidth(),
            source.cropHeight(),
            properties.processorName(),
            properties.processorVersion(),
            properties.configurationVersion(),
            processed.orientationNormalized(),
            processed.perspectiveApplied(),
            processed.contrastNormalized(),
            derivative.resized(),
            processed.qualityOutcome(),
            now
        );
    }

    private ProblemAssetQualityEvidenceJpaEntity qualityEvidence(
        ProblemAssetDerivativeJpaEntity derivative,
        ProblemAssetImagePreprocessingResult.QualitySignal signal,
        Instant now
    ) {
        return new ProblemAssetQualityEvidenceJpaEntity(
            UUID.randomUUID(),
            derivative.id(),
            derivative.sourceAssetId(),
            derivative.userId(),
            signal.signalType(),
            signal.severity(),
            decimal(signal.score()),
            decimal(signal.threshold()),
            properties.configurationVersion(),
            signal.messageCode(),
            now
        );
    }

    private ProblemAssetPreprocessingResult resultFor(ProblemAssetJpaEntity source) {
        List<ProblemAssetDerivativeResult> derivatives = derivativeRepository
            .findBySourceAssetIdAndUserIdOrderByCreatedAtDesc(source.id(), source.userId())
            .stream()
            .map(ProblemAssetDerivativeResult::from)
            .sorted(Comparator.comparing(ProblemAssetDerivativeResult::derivativeKind))
            .toList();
        List<ProblemAssetQualitySignalResult> qualitySignals = qualityEvidenceRepository
            .findBySourceAssetIdAndUserIdOrderByCreatedAtAsc(source.id(), source.userId())
            .stream()
            .map(ProblemAssetQualitySignalResult::from)
            .sorted(Comparator.comparing(ProblemAssetQualitySignalResult::signalType))
            .toList();

        ProblemAssetDerivativeResult preferred = derivatives.stream()
            .filter(ProblemAssetDerivativeResult::selectedForRecognition)
            .filter(derivative -> "READY".equals(derivative.status()))
            .findFirst()
            .orElse(null);
        ProblemAssetDerivativeResult ocr = derivatives.stream()
            .filter(derivative -> ProblemAssetDerivativeKind.OCR_OPTIMIZED.name().equals(derivative.derivativeKind()))
            .findFirst()
            .orElse(null);
        String status = preprocessingStatus(derivatives);
        String qualityOutcome = ocr == null ? null : ocr.qualityOutcome();
        String failureCode = ocr == null ? null : ocr.failureCode();
        return new ProblemAssetPreprocessingResult(
            source.id(),
            source.problemSessionId(),
            source.status(),
            status,
            qualityOutcome,
            failureCode,
            preferred == null ? null : preferred.derivativeId(),
            derivatives,
            qualitySignals,
            userRecoveryActions(status, qualityOutcome),
            ocr == null ? null : ocr.completedAt()
        );
    }

    private String preprocessingStatus(List<ProblemAssetDerivativeResult> derivatives) {
        if (derivatives.isEmpty()) {
            return "NOT_STARTED";
        }
        boolean ready = derivatives.stream().anyMatch(derivative -> "READY".equals(derivative.status()));
        return ready ? "READY" : "FAILED";
    }

    private List<String> userRecoveryActions(String preprocessingStatus, String qualityOutcome) {
        if ("NOT_STARTED".equals(preprocessingStatus)) {
            return List.of("PREPROCESS");
        }
        if (ProblemAssetQualityOutcome.PASS.name().equals(qualityOutcome)) {
            return List.of("CONTINUE");
        }
        if (ProblemAssetQualityOutcome.WARNING.name().equals(qualityOutcome)) {
            return List.of("RETAKE", "EDIT_CROP", "CONTINUE");
        }
        return List.of("RETAKE", "EDIT_CROP");
    }

    private java.util.Optional<ProblemAssetDerivativeJpaEntity> currentOcrDerivative(ProblemAssetJpaEntity source) {
        return derivativeRepository.findBySourceAssetIdAndUserIdAndDerivativeKindAndProcessorNameAndProcessorVersionAndConfigurationVersion(
            source.id(),
            source.userId(),
            ProblemAssetDerivativeKind.OCR_OPTIMIZED.name(),
            properties.processorName(),
            properties.processorVersion(),
            properties.configurationVersion()
        );
    }

    private void cleanupWrittenObjects(List<String> writtenKeys) {
        for (String key : writtenKeys) {
            try {
                storage.deleteIfExists(key);
            } catch (ProblemAssetStorageUnavailableException ignored) {
                // Best effort cleanup; the caller still returns the original preprocessing/storage failure.
            }
        }
    }

    private void requireAvailable(ProblemAssetJpaEntity source) {
        if (!source.available()) {
            throw problem(HttpStatus.CONFLICT, ApiErrorCode.ASSET_PREPROCESSING_FAILED, "Source asset is not available for preprocessing", true, "RETRY");
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

    private String derivativeObjectKey(ProblemAssetJpaEntity source, UUID derivativeId, ProblemAssetDerivativeKind kind) {
        return "problem-assets/"
            + source.problemSessionId()
            + "/"
            + source.id()
            + "/derivatives/"
            + derivativeId
            + "/"
            + kind.name().toLowerCase(Locale.ROOT).replace('_', '-')
            + ".jpg";
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

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
