package com.verifiedai.problem.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.identity.application.AppleSignInCommand;
import com.verifiedai.identity.application.AuthSessionResult;
import com.verifiedai.identity.application.IdentityApplicationService;
import com.verifiedai.identity.domain.model.AppleIdentityVerifier;
import com.verifiedai.identity.domain.model.VerifiedAppleIdentity;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.domain.port.PresignedProblemAssetUpload;
import com.verifiedai.problem.domain.port.ProblemAssetObjectMetadata;
import com.verifiedai.problem.domain.port.ProblemAssetObjectNotFoundException;
import com.verifiedai.problem.domain.port.ProblemAssetStorage;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(ProblemAssetUploadControllerTest.UploadControllerTestConfiguration.class)
final class ProblemAssetUploadControllerTest extends PostgresIntegrationTestSupport {
    private static final String VALID_CHECKSUM = "a".repeat(64);

    @Value("${local.server.port}")
    int port;

    @Autowired
    IdentityApplicationService identityApplicationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ControllerTestStorage storage;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    void uploadEndpointsRequireAuthentication() throws Exception {
        HttpResponse<String> response = httpClient.send(
            HttpRequest.newBuilder(uri("/api/v1/uploads/presign"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json")
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"AUTH_TOKEN_EXPIRED\"");
    }

    @Test
    void authenticatedUserCanReserveAndCompleteUpload() throws Exception {
        AuthSessionResult session = signIn("upload-api-user");

        HttpResponse<String> reservation = httpClient.send(
            authorized(session, "/api/v1/uploads/presign")
                .POST(HttpRequest.BodyPublishers.ofString("""
                    {"source":"camera","assetKind":"image","contentType":"image/jpeg","sizeBytes":11,"checksumSha256":"%s","imageWidth":1200,"imageHeight":900,"cropX":0,"cropY":0,"cropWidth":1,"cropHeight":1}
                    """.formatted(VALID_CHECKSUM)))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "controller-reserve")
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(reservation.statusCode()).as(reservation.body()).isEqualTo(201);
        JsonNode reservationJson = objectMapper.readTree(reservation.body());
        UUID uploadId = UUID.fromString(reservationJson.path("uploadId").asText());
        assertThat(reservationJson.path("assetStatus").asText()).isEqualTo("PENDING");
        assertThat(reservationJson.path("requiredHeaders").path("Content-Type").asText()).isEqualTo("image/jpeg");

        String objectKey = jdbcTemplate.queryForObject("select object_key from problem_assets where id = ?", String.class, uploadId);
        storage.put(objectKey, "image/jpeg", 11L, VALID_CHECKSUM);

        HttpResponse<String> completion = httpClient.send(
            authorized(session, "/api/v1/uploads/" + uploadId + "/complete")
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("Idempotency-Key", "controller-complete")
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(completion.statusCode()).as(completion.body()).isEqualTo(200);
        assertThat(completion.body()).contains("\"assetStatus\":\"AVAILABLE\"");
        assertThat(completion.body()).contains("\"problemSessionStatus\":\"ASSET_UPLOADED\"");
    }

    @Test
    void authenticatedUserCanPreprocessAvailableProblemAsset() throws Exception {
        AuthSessionResult session = signIn("preprocess-api-user");
        byte[] imageBytes = equationJpeg();
        UUID assetId = insertAvailableImageAsset(session.userId(), imageBytes);

        HttpResponse<String> preprocessing = httpClient.send(
            authorized(session, "/api/v1/problem-assets/" + assetId + "/preprocess")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(preprocessing.statusCode()).as(preprocessing.body()).isEqualTo(200);
        JsonNode preprocessingJson = objectMapper.readTree(preprocessing.body());
        assertThat(preprocessingJson.path("sourceAssetId").asText()).isEqualTo(assetId.toString());
        assertThat(preprocessingJson.path("preprocessingStatus").asText()).isEqualTo("READY");
        assertThat(preprocessingJson.path("preferredRecognitionDerivativeId").asText()).isNotBlank();
        assertThat(preprocessingJson.path("derivatives")).hasSize(2);
        assertThat(preprocessingJson.path("qualitySignals")).hasSize(5);

        HttpResponse<String> current = httpClient.send(
            authorized(session, "/api/v1/problem-assets/" + assetId + "/preprocessing")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(current.statusCode()).as(current.body()).isEqualTo(200);
        assertThat(current.body()).contains("\"preprocessingStatus\":\"READY\"");
    }

    @Test
    void idempotencyKeyIsRequiredForReservation() throws Exception {
        AuthSessionResult session = signIn("upload-api-missing-idempotency");

        HttpResponse<String> response = httpClient.send(
            authorized(session, "/api/v1/uploads/presign")
                .POST(HttpRequest.BodyPublishers.ofString("""
                    {"source":"camera","assetKind":"image","contentType":"image/jpeg","sizeBytes":11,"checksumSha256":"%s","imageWidth":1200,"imageHeight":900}
                    """.formatted(VALID_CHECKSUM)))
                .header("Content-Type", "application/json")
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"IDEMPOTENCY_KEY_REQUIRED\"");
    }

    private AuthSessionResult signIn(String subject) {
        return identityApplicationService.signInWithApple(new AppleSignInCommand(subject, "unused-code", "nonce"));
    }

    private HttpRequest.Builder authorized(AuthSessionResult session, String path) {
        return HttpRequest.newBuilder(uri(path))
            .header("Authorization", "Bearer " + session.accessToken())
            .header("Accept", "application/json");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private UUID insertAvailableImageAsset(UUID userId, byte[] bytes) {
        UUID sessionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-09T00:00:00Z");
        String objectKey = "problem-assets/" + sessionId + "/" + assetId + "/original";
        jdbcTemplate.update(
            "insert into problem_sessions (id, user_id, status, input_mode, created_at, updated_at) values (?, ?, 'ASSET_UPLOADED', 'CAMERA', ?, ?)",
            sessionId,
            userId,
            Timestamp.from(now),
            Timestamp.from(now)
        );
        jdbcTemplate.update(
            """
            insert into problem_assets (
                id, problem_session_id, user_id, source_type, asset_kind, status, object_key, content_type,
                size_bytes, checksum_algorithm, checksum_value, crop_x, crop_y, crop_width, crop_height,
                image_width, image_height, page_count, retention_class, upload_expires_at, available_at,
                created_at, updated_at, reservation_idempotency_key, reservation_request_hash
            )
            values (?, ?, ?, 'CAMERA', 'IMAGE', 'AVAILABLE', ?, 'image/jpeg', ?, 'SHA-256', ?, 0, 0, 1, 1, 1200, 900, null, 'TEMPORARY_RAW', ?, ?, ?, ?, ?, ?)
            """,
            assetId,
            sessionId,
            userId,
            objectKey,
            (long) bytes.length,
            sha256Hex(bytes),
            Timestamp.from(now.plus(Duration.ofMinutes(15))),
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now),
            "reserve-" + assetId,
            "0".repeat(64)
        );
        storage.putObject(objectKey, "image/jpeg", bytes);
        return assetId;
    }

    private static byte[] equationJpeg() {
        BufferedImage image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font("Serif", Font.BOLD, 96));
            graphics.drawString("x^2 + 3x = 10", 120, 440);
            graphics.drawString("2x - 5", 120, 560);
        } finally {
            graphics.dispose();
        }
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
    static class UploadControllerTestConfiguration {
        @Bean
        @Primary
        AppleIdentityVerifier appleIdentityVerifier() {
            return (identityToken, rawNonce) -> new VerifiedAppleIdentity(identityToken);
        }

        @Bean
        @Primary
        ControllerTestStorage problemAssetStorage() {
            return new ControllerTestStorage();
        }
    }

    static final class ControllerTestStorage implements ProblemAssetStorage {
        private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

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
        public void deleteIfExists(String objectKey) {
            objects.remove(objectKey);
        }

        void put(String objectKey, String contentType, long sizeBytes, String checksumSha256) {
            objects.put(objectKey, new StoredObject(contentType, new byte[(int) sizeBytes], sizeBytes, checksumSha256));
        }

        void reset() {
            objects.clear();
        }

        private static String sha256HexBytes(byte[] bytes) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        private record StoredObject(String contentType, byte[] bytes, long sizeBytes, String checksumSha256) {
        }
    }
}
