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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
        public void deleteIfExists(String objectKey) {
            objects.remove(objectKey);
        }

        void put(String objectKey, String contentType, long sizeBytes, String checksumSha256) {
            objects.put(objectKey, new StoredObject(contentType, sizeBytes, checksumSha256));
        }

        void reset() {
            objects.clear();
        }

        private record StoredObject(String contentType, long sizeBytes, String checksumSha256) {
        }
    }
}
