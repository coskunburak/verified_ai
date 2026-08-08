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
import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(ProblemAssetUploadApplicationServiceTest.StorageTestConfiguration.class)
final class ProblemAssetUploadApplicationServiceTest extends PostgresIntegrationTestSupport {
    private static final String VALID_CHECKSUM = "f".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");

    @Autowired
    ProblemAssetUploadApplicationService uploadApplicationService;

    @Autowired
    InMemoryProblemAssetStorage storage;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
            truncate table
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
    void reservationCreatesOwnedPendingSessionAndAssetWithBackendObjectKey() {
        UUID userId = insertUser();

        ProblemAssetUploadReservationResult result = uploadApplicationService.reserve(
            userId,
            "reserve-image-1",
            imageCommand()
        );

        assertThat(result.assetStatus()).isEqualTo("PENDING");
        assertThat(result.requiredHeaders()).containsEntry("Content-Type", "image/jpeg");
        assertThat(result.uploadUrl()).contains("/problem-assets/");
        assertThat(count("problem_sessions")).isEqualTo(1);
        assertThat(count("problem_assets")).isEqualTo(1);
        assertThat(value("problem_sessions", "status", "id = '" + result.problemSessionId() + "'")).isEqualTo("CREATED");
        assertThat(value("problem_assets", "status", "id = '" + result.problemAssetId() + "'")).isEqualTo("PENDING");
        assertThat(value("problem_assets", "object_key", "id = '" + result.problemAssetId() + "'"))
            .isEqualTo("problem-assets/" + result.problemSessionId() + "/" + result.problemAssetId() + "/original");
        assertThat(value("problem_assets", "user_id::text", "id = '" + result.problemAssetId() + "'"))
            .isEqualTo(userId.toString());
    }

    @Test
    void reservationIdempotencyReturnsSameAssetAndRejectsDifferentPayload() {
        UUID userId = insertUser();

        ProblemAssetUploadReservationResult first = uploadApplicationService.reserve(userId, "same-key", imageCommand());
        ProblemAssetUploadReservationResult second = uploadApplicationService.reserve(userId, "same-key", imageCommand());

        assertThat(second.problemAssetId()).isEqualTo(first.problemAssetId());
        assertThat(second.problemSessionId()).isEqualTo(first.problemSessionId());
        assertThat(count("problem_assets")).isEqualTo(1);
        assertThatThrownBy(() -> uploadApplicationService.reserve(userId, "same-key", imageCommand(12L, VALID_CHECKSUM)))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void completionVerifiesObjectAndTransitionsToAvailableIdempotently() {
        UUID userId = insertUser();
        ProblemAssetUploadReservationResult reservation = uploadApplicationService.reserve(userId, "reserve-complete", imageCommand());
        storage.put(objectKey(reservation), "image/jpeg", 11L, VALID_CHECKSUM);

        ProblemAssetUploadCompletionResult completed = uploadApplicationService.complete(
            userId,
            reservation.uploadId(),
            "complete-key"
        );
        ProblemAssetUploadCompletionResult duplicate = uploadApplicationService.complete(
            userId,
            reservation.uploadId(),
            "complete-key"
        );

        assertThat(completed.assetStatus()).isEqualTo("AVAILABLE");
        assertThat(completed.problemSessionStatus()).isEqualTo("ASSET_UPLOADED");
        assertThat(completed.availableAt()).isNotNull();
        assertThat(duplicate).isEqualTo(completed);
        assertThat(value("problem_assets", "status", "id = '" + reservation.problemAssetId() + "'")).isEqualTo("AVAILABLE");
        assertThat(value("problem_sessions", "status", "id = '" + reservation.problemSessionId() + "'")).isEqualTo("ASSET_UPLOADED");
    }

    @Test
    void completionRejectsWrongUserWithoutRevealingAsset() {
        UUID ownerId = insertUser();
        UUID attackerId = insertUser();
        ProblemAssetUploadReservationResult reservation = uploadApplicationService.reserve(ownerId, "owner-reserve", imageCommand());
        storage.put(objectKey(reservation), "image/jpeg", 11L, VALID_CHECKSUM);

        assertThatThrownBy(() -> uploadApplicationService.complete(attackerId, reservation.uploadId(), "attacker-complete"))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.RESOURCE_FORBIDDEN);

        assertThat(value("problem_assets", "status", "id = '" + reservation.problemAssetId() + "'")).isEqualTo("PENDING");
    }

    @Test
    void checksumMismatchDeletesObjectAndDoesNotMarkAvailable() {
        UUID userId = insertUser();
        ProblemAssetUploadReservationResult reservation = uploadApplicationService.reserve(userId, "reserve-bad-checksum", imageCommand());
        String objectKey = objectKey(reservation);
        storage.put(objectKey, "image/jpeg", 11L, "0".repeat(64));

        assertThatThrownBy(() -> uploadApplicationService.complete(userId, reservation.uploadId(), "complete-bad-checksum"))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.UPLOAD_CHECKSUM_MISMATCH);

        assertThat(storage.deletedKeys()).contains(objectKey);
        assertThat(value("problem_assets", "status", "id = '" + reservation.problemAssetId() + "'")).isEqualTo("PENDING");
    }

    @Test
    void reservationRejectsOversizeAndUnsupportedMimeBeforePersistingAsset() {
        UUID userId = insertUser();

        assertThatThrownBy(() -> uploadApplicationService.reserve(userId, "too-large", imageCommand(20L * 1024L * 1024L + 1L, VALID_CHECKSUM)))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.UPLOAD_TOO_LARGE);
        assertThatThrownBy(() -> uploadApplicationService.reserve(userId, "bad-mime", new ProblemAssetUploadCommand(
            "camera",
            "image",
            "image/png",
            11L,
            VALID_CHECKSUM,
            1200,
            900,
            null,
            0.0,
            0.0,
            1.0,
            1.0
        )))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.UPLOAD_CONTENT_TYPE_UNSUPPORTED);

        assertThat(count("problem_assets")).isEqualTo(0);
    }

    @Test
    void expiredPendingCompletionMarksReservationExpired() {
        UUID userId = insertUser();
        ProblemAssetUploadReservationResult reservation = uploadApplicationService.reserve(userId, "reserve-expired", imageCommand());
        storage.put(objectKey(reservation), "image/jpeg", 11L, VALID_CHECKSUM);
        jdbcTemplate.update(
            "update problem_assets set upload_expires_at = now() - interval '1 second' where id = ?",
            reservation.problemAssetId()
        );

        assertThatThrownBy(() -> uploadApplicationService.complete(userId, reservation.uploadId(), "complete-expired"))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.UPLOAD_RESERVATION_EXPIRED);

        assertThat(value("problem_assets", "status", "id = '" + reservation.problemAssetId() + "'")).isEqualTo("EXPIRED");
    }

    @Test
    void cleanupExpiresPendingReservationsAndDeletesObjects() {
        UUID userId = insertUser();
        ProblemAssetUploadReservationResult reservation = uploadApplicationService.reserve(userId, "reserve-cleanup", imageCommand());
        String objectKey = objectKey(reservation);
        storage.put(objectKey, "image/jpeg", 11L, VALID_CHECKSUM);
        jdbcTemplate.update(
            "update problem_assets set upload_expires_at = now() - interval '1 second' where id = ?",
            reservation.problemAssetId()
        );

        int expired = uploadApplicationService.expirePendingUploads(10);

        assertThat(expired).isEqualTo(1);
        assertThat(storage.deletedKeys()).contains(objectKey);
        assertThat(value("problem_assets", "status", "id = '" + reservation.problemAssetId() + "'")).isEqualTo("EXPIRED");
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

    private ProblemAssetUploadCommand imageCommand() {
        return imageCommand(11L, VALID_CHECKSUM);
    }

    private ProblemAssetUploadCommand imageCommand(long sizeBytes, String checksum) {
        return new ProblemAssetUploadCommand(
            "camera",
            "image",
            "image/jpeg",
            sizeBytes,
            checksum,
            1200,
            900,
            null,
            0.0,
            0.0,
            1.0,
            1.0
        );
    }

    private String objectKey(ProblemAssetUploadReservationResult reservation) {
        return jdbcTemplate.queryForObject(
            "select object_key from problem_assets where id = ?",
            String.class,
            reservation.problemAssetId()
        );
    }

    private Integer count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private String value(String table, String expression, String predicate) {
        return jdbcTemplate.queryForObject("select " + expression + " from " + table + " where " + predicate, String.class);
    }

    @TestConfiguration
    static class StorageTestConfiguration {
        @Bean
        @Primary
        InMemoryProblemAssetStorage problemAssetStorage() {
            return new InMemoryProblemAssetStorage();
        }
    }

    static final class InMemoryProblemAssetStorage implements ProblemAssetStorage {
        private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
        private final List<String> deletedKeys = new ArrayList<>();

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

        void put(String objectKey, String contentType, long sizeBytes, String checksumSha256) {
            objects.put(objectKey, new StoredObject(contentType, sizeBytes, checksumSha256));
        }

        List<String> deletedKeys() {
            return List.copyOf(deletedKeys);
        }

        void reset() {
            objects.clear();
            deletedKeys.clear();
        }

        private record StoredObject(String contentType, long sizeBytes, String checksumSha256) {
        }
    }
}
