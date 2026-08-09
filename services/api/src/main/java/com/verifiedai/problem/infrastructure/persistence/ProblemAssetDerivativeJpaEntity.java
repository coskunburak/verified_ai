package com.verifiedai.problem.infrastructure.persistence;

import com.verifiedai.problem.domain.model.ProblemAssetDerivativeKind;
import com.verifiedai.problem.domain.model.ProblemAssetDerivativeStatus;
import com.verifiedai.problem.domain.model.ProblemAssetQualityOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "problem_asset_derivatives")
public class ProblemAssetDerivativeJpaEntity {
    @Id
    private UUID id;

    @Column(name = "source_asset_id", nullable = false)
    private UUID sourceAssetId;

    @Column(name = "problem_session_id", nullable = false)
    private UUID problemSessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "derivative_kind", nullable = false)
    private String derivativeKind;

    @Column(nullable = false)
    private String status;

    @Column(name = "selected_for_recognition", nullable = false)
    private boolean selectedForRecognition;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "checksum_algorithm")
    private String checksumAlgorithm;

    @Column(name = "checksum_value")
    private String checksumValue;

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column(name = "source_width", nullable = false)
    private int sourceWidth;

    @Column(name = "source_height", nullable = false)
    private int sourceHeight;

    @Column(name = "crop_x", nullable = false)
    private BigDecimal cropX;

    @Column(name = "crop_y", nullable = false)
    private BigDecimal cropY;

    @Column(name = "crop_width", nullable = false)
    private BigDecimal cropWidth;

    @Column(name = "crop_height", nullable = false)
    private BigDecimal cropHeight;

    @Column(name = "processor_name", nullable = false)
    private String processorName;

    @Column(name = "processor_version", nullable = false)
    private String processorVersion;

    @Column(name = "configuration_version", nullable = false)
    private String configurationVersion;

    @Column(name = "orientation_normalized", nullable = false)
    private boolean orientationNormalized;

    @Column(name = "perspective_applied", nullable = false)
    private boolean perspectiveApplied;

    @Column(name = "contrast_normalized", nullable = false)
    private boolean contrastNormalized;

    @Column(nullable = false)
    private boolean resized;

    @Column(name = "quality_outcome", nullable = false)
    private String qualityOutcome;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    protected ProblemAssetDerivativeJpaEntity() {
    }

    @SuppressWarnings("ParameterNumber")
    private ProblemAssetDerivativeJpaEntity(
        UUID id,
        UUID sourceAssetId,
        UUID problemSessionId,
        UUID userId,
        ProblemAssetDerivativeKind derivativeKind,
        ProblemAssetDerivativeStatus status,
        boolean selectedForRecognition,
        String objectKey,
        String contentType,
        Long sizeBytes,
        String checksumValue,
        Integer width,
        Integer height,
        int sourceWidth,
        int sourceHeight,
        BigDecimal cropX,
        BigDecimal cropY,
        BigDecimal cropWidth,
        BigDecimal cropHeight,
        String processorName,
        String processorVersion,
        String configurationVersion,
        boolean orientationNormalized,
        boolean perspectiveApplied,
        boolean contrastNormalized,
        boolean resized,
        ProblemAssetQualityOutcome qualityOutcome,
        String failureCode,
        Instant now
    ) {
        this.id = id;
        this.sourceAssetId = sourceAssetId;
        this.problemSessionId = problemSessionId;
        this.userId = userId;
        this.derivativeKind = derivativeKind.name();
        this.status = status.name();
        this.selectedForRecognition = selectedForRecognition;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.checksumAlgorithm = checksumValue == null ? null : "SHA-256";
        this.checksumValue = checksumValue;
        this.width = width;
        this.height = height;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.cropX = cropX;
        this.cropY = cropY;
        this.cropWidth = cropWidth;
        this.cropHeight = cropHeight;
        this.processorName = processorName;
        this.processorVersion = processorVersion;
        this.configurationVersion = configurationVersion;
        this.orientationNormalized = orientationNormalized;
        this.perspectiveApplied = perspectiveApplied;
        this.contrastNormalized = contrastNormalized;
        this.resized = resized;
        this.qualityOutcome = qualityOutcome.name();
        this.failureCode = failureCode;
        this.createdAt = now;
        this.updatedAt = now;
        this.completedAt = now;
    }

    @SuppressWarnings("ParameterNumber")
    public static ProblemAssetDerivativeJpaEntity ready(
        UUID id,
        UUID sourceAssetId,
        UUID problemSessionId,
        UUID userId,
        ProblemAssetDerivativeKind derivativeKind,
        boolean selectedForRecognition,
        String objectKey,
        long sizeBytes,
        String checksumValue,
        int width,
        int height,
        int sourceWidth,
        int sourceHeight,
        BigDecimal cropX,
        BigDecimal cropY,
        BigDecimal cropWidth,
        BigDecimal cropHeight,
        String processorName,
        String processorVersion,
        String configurationVersion,
        boolean orientationNormalized,
        boolean perspectiveApplied,
        boolean contrastNormalized,
        boolean resized,
        ProblemAssetQualityOutcome qualityOutcome,
        Instant now
    ) {
        return new ProblemAssetDerivativeJpaEntity(
            id,
            sourceAssetId,
            problemSessionId,
            userId,
            derivativeKind,
            ProblemAssetDerivativeStatus.READY,
            selectedForRecognition,
            objectKey,
            "image/jpeg",
            sizeBytes,
            checksumValue,
            width,
            height,
            sourceWidth,
            sourceHeight,
            cropX,
            cropY,
            cropWidth,
            cropHeight,
            processorName,
            processorVersion,
            configurationVersion,
            orientationNormalized,
            perspectiveApplied,
            contrastNormalized,
            resized,
            qualityOutcome,
            null,
            now
        );
    }

    @SuppressWarnings("ParameterNumber")
    public static ProblemAssetDerivativeJpaEntity failed(
        UUID id,
        UUID sourceAssetId,
        UUID problemSessionId,
        UUID userId,
        ProblemAssetDerivativeKind derivativeKind,
        int sourceWidth,
        int sourceHeight,
        BigDecimal cropX,
        BigDecimal cropY,
        BigDecimal cropWidth,
        BigDecimal cropHeight,
        String processorName,
        String processorVersion,
        String configurationVersion,
        String failureCode,
        Instant now
    ) {
        return new ProblemAssetDerivativeJpaEntity(
            id,
            sourceAssetId,
            problemSessionId,
            userId,
            derivativeKind,
            ProblemAssetDerivativeStatus.FAILED,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            sourceWidth,
            sourceHeight,
            cropX,
            cropY,
            cropWidth,
            cropHeight,
            processorName,
            processorVersion,
            configurationVersion,
            false,
            false,
            false,
            false,
            ProblemAssetQualityOutcome.FAILED,
            failureCode,
            now
        );
    }

    public UUID id() {
        return id;
    }

    public UUID sourceAssetId() {
        return sourceAssetId;
    }

    public UUID problemSessionId() {
        return problemSessionId;
    }

    public UUID userId() {
        return userId;
    }

    public String derivativeKind() {
        return derivativeKind;
    }

    public String status() {
        return status;
    }

    public boolean selectedForRecognition() {
        return selectedForRecognition;
    }

    public String objectKey() {
        return objectKey;
    }

    public String contentType() {
        return contentType;
    }

    public Long sizeBytes() {
        return sizeBytes;
    }

    public String checksumAlgorithm() {
        return checksumAlgorithm;
    }

    public String checksumValue() {
        return checksumValue;
    }

    public Integer width() {
        return width;
    }

    public Integer height() {
        return height;
    }

    public int sourceWidth() {
        return sourceWidth;
    }

    public int sourceHeight() {
        return sourceHeight;
    }

    public BigDecimal cropX() {
        return cropX;
    }

    public BigDecimal cropY() {
        return cropY;
    }

    public BigDecimal cropWidth() {
        return cropWidth;
    }

    public BigDecimal cropHeight() {
        return cropHeight;
    }

    public String processorName() {
        return processorName;
    }

    public String processorVersion() {
        return processorVersion;
    }

    public String configurationVersion() {
        return configurationVersion;
    }

    public boolean orientationNormalized() {
        return orientationNormalized;
    }

    public boolean perspectiveApplied() {
        return perspectiveApplied;
    }

    public boolean contrastNormalized() {
        return contrastNormalized;
    }

    public boolean resized() {
        return resized;
    }

    public String qualityOutcome() {
        return qualityOutcome;
    }

    public String failureCode() {
        return failureCode;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant completedAt() {
        return completedAt;
    }
}
