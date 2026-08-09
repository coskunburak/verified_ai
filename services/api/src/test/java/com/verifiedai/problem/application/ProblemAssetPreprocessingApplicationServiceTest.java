package com.verifiedai.problem.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.domain.port.PresignedProblemAssetUpload;
import com.verifiedai.problem.domain.port.ProblemAssetObjectMetadata;
import com.verifiedai.problem.domain.port.ProblemAssetObjectNotFoundException;
import com.verifiedai.problem.domain.port.ProblemAssetStorage;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(ProblemAssetPreprocessingApplicationServiceTest.PreprocessingStorageTestConfiguration.class)
final class ProblemAssetPreprocessingApplicationServiceTest extends PostgresIntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Autowired
    ProblemAssetPreprocessingApplicationService preprocessingApplicationService;

    @Autowired
    PreprocessingTestStorage storage;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
            truncate table
                problem_asset_quality_evidence,
                problem_asset_derivatives,
                problem_assets,
                problem_sessions,
                entitlements,
                learning_profiles,
                auth_security_events,
                refresh_tokens,
                sessions,
                user_identities,
                users
            cascade
            """);
        storage.reset();
    }

    @Test
    void preprocessingAvailableImageCreatesSelectedDerivativeThumbnailAndQualityEvidence() {
        UUID userId = insertUser();
        UUID assetId = insertAvailableImageAsset(userId, equationJpeg(1200, 900), 0, 0, 1, 1);

        ProblemAssetPreprocessingResult result = preprocessingApplicationService.preprocess(userId, assetId);

        assertThat(result.preprocessingStatus()).isEqualTo("READY");
        assertThat(result.qualityOutcome()).isIn("PASS", "WARNING");
        assertThat(result.preferredRecognitionDerivativeId()).isNotNull();
        assertThat(result.derivatives()).hasSize(2);
        assertThat(result.derivatives())
            .extracting(ProblemAssetDerivativeResult::derivativeKind)
            .containsExactly("OCR_OPTIMIZED", "THUMBNAIL");
        assertThat(result.derivatives())
            .filteredOn(ProblemAssetDerivativeResult::selectedForRecognition)
            .singleElement()
            .extracting(ProblemAssetDerivativeResult::derivativeKind)
            .isEqualTo("OCR_OPTIMIZED");
        assertThat(result.qualitySignals()).hasSize(5);
        assertThat(result.qualitySignals())
            .extracting(ProblemAssetQualitySignalResult::signalType)
            .containsExactly("BLUR", "CONTRAST_READABILITY", "CROP_FRAMING", "GLARE", "RESOLUTION");
        assertThat(count("problem_asset_derivatives")).isEqualTo(2);
        assertThat(count("problem_asset_quality_evidence")).isEqualTo(5);
        assertThat(value("problem_assets", "status", "id = '" + assetId + "'")).isEqualTo("AVAILABLE");
        assertThat(storage.objectCount()).isEqualTo(3);
    }

    @Test
    void preprocessingIsIdempotentForCurrentProcessorConfiguration() {
        UUID userId = insertUser();
        UUID assetId = insertAvailableImageAsset(userId, equationJpeg(1200, 900), 0, 0, 1, 1);

        ProblemAssetPreprocessingResult first = preprocessingApplicationService.preprocess(userId, assetId);
        ProblemAssetPreprocessingResult second = preprocessingApplicationService.preprocess(userId, assetId);

        assertThat(second.preferredRecognitionDerivativeId()).isEqualTo(first.preferredRecognitionDerivativeId());
        assertThat(count("problem_asset_derivatives")).isEqualTo(2);
        assertThat(count("problem_asset_quality_evidence")).isEqualTo(5);
        assertThat(storage.objectCount()).isEqualTo(3);
    }

    @Test
    void preprocessingRejectsWrongUserWithoutRevealingAsset() {
        UUID ownerId = insertUser();
        UUID attackerId = insertUser();
        UUID assetId = insertAvailableImageAsset(ownerId, equationJpeg(1200, 900), 0, 0, 1, 1);

        assertThatThrownBy(() -> preprocessingApplicationService.preprocess(attackerId, assetId))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.RESOURCE_FORBIDDEN);

        assertThat(count("problem_asset_derivatives")).isEqualTo(0);
    }

    @Test
    void pdfSourceRecordsRecoverableUnsupportedFailureWithoutDerivedObject() {
        UUID userId = insertUser();
        UUID assetId = insertAvailablePdfAsset(userId);

        ProblemAssetPreprocessingResult result = preprocessingApplicationService.preprocess(userId, assetId);

        assertThat(result.preprocessingStatus()).isEqualTo("FAILED");
        assertThat(result.qualityOutcome()).isEqualTo("FAILED");
        assertThat(result.failureCode()).isEqualTo("PDF_UNSUPPORTED");
        assertThat(result.userRecoveryActions()).containsExactly("RETAKE", "EDIT_CROP");
        assertThat(count("problem_asset_derivatives")).isEqualTo(1);
        assertThat(value("problem_asset_derivatives", "object_key", "source_asset_id = '" + assetId + "'")).isNull();
    }

    @Test
    void malformedImageRecordsRecoverableFailedLifecycle() {
        UUID userId = insertUser();
        UUID assetId = insertAvailableImageAsset(userId, "not-an-image".getBytes(java.nio.charset.StandardCharsets.UTF_8), 0, 0, 1, 1);

        ProblemAssetPreprocessingResult result = preprocessingApplicationService.preprocess(userId, assetId);

        assertThat(result.preprocessingStatus()).isEqualTo("FAILED");
        assertThat(result.qualityOutcome()).isEqualTo("FAILED");
        assertThat(result.failureCode()).isEqualTo("IMAGE_DECODE_FAILED");
        assertThat(count("problem_asset_derivatives")).isEqualTo(1);
        assertThat(count("problem_asset_quality_evidence")).isEqualTo(0);
        assertThat(storage.objectCount()).isEqualTo(1);
    }

    @Test
    void lowQualityImageReturnsWarningAndDoesNotBlockContinue() {
        UUID userId = insertUser();
        UUID assetId = insertAvailableImageAsset(userId, lowContrastJpeg(480, 360), 0.40, 0.40, 0.20, 0.20);

        ProblemAssetPreprocessingResult result = preprocessingApplicationService.preprocess(userId, assetId);

        assertThat(result.preprocessingStatus()).isEqualTo("READY");
        assertThat(result.qualityOutcome()).isEqualTo("WARNING");
        assertThat(result.userRecoveryActions()).containsExactly("RETAKE", "EDIT_CROP", "CONTINUE");
        assertThat(result.qualitySignals())
            .filteredOn(signal -> "WARNING".equals(signal.severity()))
            .extracting(ProblemAssetQualitySignalResult::signalType)
            .contains("CROP_FRAMING", "RESOLUTION");
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into users (id, status, created_at, updated_at) values (?, 'ACTIVE', ?, ?)",
            userId,
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
        return userId;
    }

    private UUID insertAvailableImageAsset(UUID userId, byte[] bytes, double cropX, double cropY, double cropWidth, double cropHeight) {
        return insertAvailableAsset(userId, "IMAGE", "image/jpeg", bytes, 1200, 900, null, cropX, cropY, cropWidth, cropHeight);
    }

    private UUID insertAvailablePdfAsset(UUID userId) {
        return insertAvailableAsset(
            userId,
            "PDF",
            "application/pdf",
            "%PDF-1.7\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            null,
            null,
            1,
            0,
            0,
            1,
            1
        );
    }

    private UUID insertAvailableAsset(
        UUID userId,
        String assetKind,
        String contentType,
        byte[] bytes,
        Integer imageWidth,
        Integer imageHeight,
        Integer pageCount,
        double cropX,
        double cropY,
        double cropWidth,
        double cropHeight
    ) {
        UUID sessionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        String objectKey = "problem-assets/" + sessionId + "/" + assetId + "/original";
        String inputMode = "PDF".equals(assetKind) ? "PDF" : "CAMERA";
        String sourceType = "PDF".equals(assetKind) ? "PDF" : "CAMERA";
        jdbcTemplate.update(
            "insert into problem_sessions (id, user_id, status, input_mode, created_at, updated_at) values (?, ?, 'ASSET_UPLOADED', ?, ?, ?)",
            sessionId,
            userId,
            inputMode,
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
        jdbcTemplate.update(
            """
            insert into problem_assets (
                id, problem_session_id, user_id, source_type, asset_kind, status, object_key, content_type,
                size_bytes, checksum_algorithm, checksum_value, crop_x, crop_y, crop_width, crop_height,
                image_width, image_height, page_count, retention_class, upload_expires_at, available_at,
                created_at, updated_at, reservation_idempotency_key, reservation_request_hash
            )
            values (?, ?, ?, ?, ?, 'AVAILABLE', ?, ?, ?, 'SHA-256', ?, ?, ?, ?, ?, ?, ?, ?, 'TEMPORARY_RAW', ?, ?, ?, ?, ?, ?)
            """,
            assetId,
            sessionId,
            userId,
            sourceType,
            assetKind,
            objectKey,
            contentType,
            (long) bytes.length,
            sha256Hex(bytes),
            decimal(cropX),
            decimal(cropY),
            decimal(cropWidth),
            decimal(cropHeight),
            imageWidth,
            imageHeight,
            pageCount,
            Timestamp.from(NOW.plus(Duration.ofMinutes(15))),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            "reserve-" + assetId,
            "0".repeat(64)
        );
        storage.putObject(objectKey, contentType, bytes);
        return assetId;
    }

    private Integer count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private String value(String table, String expression, String predicate) {
        return jdbcTemplate.queryForObject("select " + expression + " from " + table + " where " + predicate, String.class);
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(6, java.math.RoundingMode.HALF_UP);
    }

    private static byte[] equationJpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.BLACK);
            graphics.setStroke(new BasicStroke(6));
            graphics.setFont(new Font("Serif", Font.BOLD, 96));
            graphics.drawString("x^2 + 3x = 10", 120, height / 2);
            graphics.drawLine(120, height / 2 + 40, 820, height / 2 + 40);
            graphics.drawString("2x - 5", 120, height / 2 + 140);
        } finally {
            graphics.dispose();
        }
        return jpeg(image);
    }

    private static byte[] lowContrastJpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(224, 224, 224));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new Color(150, 150, 150));
            graphics.setFont(new Font("Serif", Font.PLAIN, 40));
            graphics.drawString("x + 1 = 2", 90, height / 2);
        } finally {
            graphics.dispose();
        }
        return jpeg(image);
    }

    private static byte[] jpeg(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpeg", output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @TestConfiguration
    static class PreprocessingStorageTestConfiguration {
        @Bean
        @Primary
        PreprocessingTestStorage problemAssetStorage() {
            return new PreprocessingTestStorage();
        }
    }

    static final class PreprocessingTestStorage implements ProblemAssetStorage {
        private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
        private final Set<String> deletedKeys = new LinkedHashSet<>();

        @Override
        public PresignedProblemAssetUpload presignPut(String objectKey, String contentType, long sizeBytes, Duration ttl) {
            return new PresignedProblemAssetUpload(
                URI.create("http://127.0.0.1:9000/verified-ai-problem-assets-local/" + objectKey),
                Instant.now().plus(ttl),
                Map.of("Content-Type", contentType)
            );
        }

        @Override
        public ProblemAssetObjectMetadata head(String objectKey) {
            StoredObject object = objects.get(objectKey);
            if (object == null) {
                throw new ProblemAssetObjectNotFoundException("missing");
            }
            return new ProblemAssetObjectMetadata(object.sizeBytes(), object.contentType());
        }

        @Override
        public byte[] readBytes(String objectKey, long maxSizeBytes) {
            StoredObject object = objects.get(objectKey);
            if (object == null) {
                throw new ProblemAssetObjectNotFoundException("missing");
            }
            if (object.sizeBytes() > maxSizeBytes) {
                throw new IllegalStateException("too large");
            }
            return object.bytes().clone();
        }

        @Override
        public void putObject(String objectKey, String contentType, byte[] bytes) {
            objects.put(objectKey, new StoredObject(contentType, bytes.clone(), bytes.length, sha256HexBytes(bytes)));
        }

        @Override
        public String sha256Hex(String objectKey) {
            StoredObject object = objects.get(objectKey);
            if (object == null) {
                throw new ProblemAssetObjectNotFoundException("missing");
            }
            return object.checksumSha256();
        }

        @Override
        public void deleteIfExists(String objectKey) {
            objects.remove(objectKey);
            deletedKeys.add(objectKey);
        }

        int objectCount() {
            return objects.size();
        }

        void reset() {
            objects.clear();
            deletedKeys.clear();
        }

        private record StoredObject(String contentType, byte[] bytes, long sizeBytes, String checksumSha256) {
        }

        private static String sha256HexBytes(byte[] bytes) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
