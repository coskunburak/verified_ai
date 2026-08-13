package com.verifiedai.problem.infrastructure.persistence.entity;

import com.verifiedai.problem.domain.model.asset.ProblemAssetKind;
import com.verifiedai.problem.domain.model.asset.ProblemAssetRetentionClass;
import com.verifiedai.problem.domain.model.asset.ProblemAssetSource;
import com.verifiedai.problem.domain.model.asset.ProblemAssetStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "problem_assets")
public class ProblemAssetJpaEntity {
    @Id
    private UUID id;

    @Column(name = "problem_session_id", nullable = false)
    private UUID problemSessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "asset_kind", nullable = false)
    private String assetKind;

    @Column(nullable = false)
    private String status;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_algorithm", nullable = false)
    private String checksumAlgorithm;

    @Column(name = "checksum_value", nullable = false)
    private String checksumValue;

    @Column(name = "crop_x", nullable = false)
    private BigDecimal cropX;

    @Column(name = "crop_y", nullable = false)
    private BigDecimal cropY;

    @Column(name = "crop_width", nullable = false)
    private BigDecimal cropWidth;

    @Column(name = "crop_height", nullable = false)
    private BigDecimal cropHeight;

    @Column(name = "image_width")
    private Integer imageWidth;
    @Column(name = "image_height")
    private Integer imageHeight;
    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "retention_class", nullable = false)
    private String retentionClass;

    @Column(name = "upload_expires_at", nullable = false)
    private Instant uploadExpiresAt;

    @Column(name = "available_at")
    private Instant availableAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @Column(name = "reservation_idempotency_key", nullable = false)
    private String reservationIdempotencyKey;

    @Column(name = "reservation_request_hash", nullable = false)
    private String reservationRequestHash;

    protected ProblemAssetJpaEntity() {
    }

    @SuppressWarnings("ParameterNumber")
    private ProblemAssetJpaEntity(
        UUID id,
        UUID problemSessionId,
        UUID userId,
        ProblemAssetSource sourceType,
        ProblemAssetKind assetKind,
        String objectKey,
        String contentType,
        long sizeBytes,
        String checksumValue,
        BigDecimal cropX,
        BigDecimal cropY,
        BigDecimal cropWidth,
        BigDecimal cropHeight,
        Integer imageWidth,
        Integer imageHeight,
        Integer pageCount,
        Instant uploadExpiresAt,
        Instant now,
        String reservationIdempotencyKey,
        String reservationRequestHash
    ) {
        this.id = id;
        this.problemSessionId = problemSessionId;
        this.userId = userId;
        this.sourceType = sourceType.name();
        this.assetKind = assetKind.name();
        this.status = ProblemAssetStatus.PENDING.name();
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.checksumAlgorithm = "SHA-256";
        this.checksumValue = checksumValue;
        this.cropX = cropX;
        this.cropY = cropY;
        this.cropWidth = cropWidth;
        this.cropHeight = cropHeight;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.pageCount = pageCount;
        this.retentionClass = ProblemAssetRetentionClass.TEMPORARY_RAW.name();
        this.uploadExpiresAt = uploadExpiresAt;
        this.createdAt = now;
        this.updatedAt = now;
        this.reservationIdempotencyKey = reservationIdempotencyKey;
        this.reservationRequestHash = reservationRequestHash;
    }

    @SuppressWarnings("ParameterNumber")
    public static ProblemAssetJpaEntity pending(
        UUID id,
        UUID problemSessionId,
        UUID userId,
        ProblemAssetSource sourceType,
        ProblemAssetKind assetKind,
        String objectKey,
        String contentType,
        long sizeBytes,
        String checksumValue,
        BigDecimal cropX,
        BigDecimal cropY,
        BigDecimal cropWidth,
        BigDecimal cropHeight,
        Integer imageWidth,
        Integer imageHeight,
        Integer pageCount,
        Instant uploadExpiresAt,
        Instant now,
        String reservationIdempotencyKey,
        String reservationRequestHash
    ) {
        return new ProblemAssetJpaEntity(
            id,
            problemSessionId,
            userId,
            sourceType,
            assetKind,
            objectKey,
            contentType,
            sizeBytes,
            checksumValue,
            cropX,
            cropY,
            cropWidth,
            cropHeight,
            imageWidth,
            imageHeight,
            pageCount,
            uploadExpiresAt,
            now,
            reservationIdempotencyKey,
            reservationRequestHash
        );
    }

    public UUID id() {
        return id;
    }

    public UUID problemSessionId() {
        return problemSessionId;
    }

    public UUID userId() {
        return userId;
    }

    public String sourceType() {
        return sourceType;
    }

    public String assetKind() {
        return assetKind;
    }

    public String status() {
        return status;
    }

    public String objectKey() {
        return objectKey;
    }

    public String contentType() {
        return contentType;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String checksumAlgorithm() {
        return checksumAlgorithm;
    }

    public String checksumValue() {
        return checksumValue;
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

    public Integer imageWidth() {
        return imageWidth;
    }

    public Integer imageHeight() {
        return imageHeight;
    }

    public Integer pageCount() {
        return pageCount;
    }

    public String retentionClass() {
        return retentionClass;
    }

    public Instant uploadExpiresAt() {
        return uploadExpiresAt;
    }

    public Instant availableAt() {
        return availableAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    public String reservationRequestHash() {
        return reservationRequestHash;
    }

    public boolean pending() {
        return ProblemAssetStatus.PENDING.name().equals(status);
    }

    public boolean available() {
        return ProblemAssetStatus.AVAILABLE.name().equals(status);
    }

    public void markAvailable(Instant now) {
        status = ProblemAssetStatus.AVAILABLE.name();
        availableAt = now;
        updatedAt = now;
    }

    public void markExpired(Instant now) {
        if (pending()) {
            status = ProblemAssetStatus.EXPIRED.name();
            updatedAt = now;
        }
    }

    public void markDeleted(Instant now) {
        status = ProblemAssetStatus.DELETED.name();
        deletedAt = now;
        updatedAt = now;
    }
}
