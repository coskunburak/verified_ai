package com.verifiedai.problem.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.problem.domain.port.ProblemAssetObjectNotFoundException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Testcontainers
final class S3CompatibleProblemAssetStorageTest {
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final String BUCKET = "verified-ai-problem-assets-test";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:RELEASE.2024-07-16T23-46-41Z")
        .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
        .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
        .withCommand("server", "/data")
        .withExposedPorts(9000)
        .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @Test
    void presignedPutUploadCanBeVerifiedAndDeletedAgainstMinio() throws Exception {
        URI endpoint = URI.create("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        createBucket(endpoint);
        S3CompatibleProblemAssetStorage storage = new S3CompatibleProblemAssetStorage(properties(endpoint));
        byte[] body = "sprint-4.2-presigned-upload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String objectKey = "problem-assets/00000000-0000-0000-0000-000000000421/00000000-0000-0000-0000-000000000422/original";

        var presigned = storage.presignPut(objectKey, "image/jpeg", body.length, Duration.ofMinutes(5));
        HttpResponse<String> uploadResponse = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(presigned.uploadUrl())
                .header("Content-Type", "image/jpeg")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(uploadResponse.statusCode()).as(uploadResponse.body()).isEqualTo(200);
        assertThat(storage.head(objectKey).sizeBytes()).isEqualTo(body.length);
        assertThat(storage.head(objectKey).contentType()).isEqualTo("image/jpeg");
        assertThat(storage.readBytes(objectKey, 1024)).isEqualTo(body);
        assertThat(storage.sha256Hex(objectKey)).isEqualTo(sha256Hex(body));

        byte[] derivativeBody = "sprint-4.3-derived-jpeg".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String derivativeKey = "problem-assets/00000000-0000-0000-0000-000000000421/00000000-0000-0000-0000-000000000422/derivatives/00000000-0000-0000-0000-000000000423/ocr-optimized.jpg";
        storage.putObject(derivativeKey, "image/jpeg", derivativeBody);
        assertThat(storage.head(derivativeKey).contentType()).isEqualTo("image/jpeg");
        assertThat(storage.readBytes(derivativeKey, 1024)).isEqualTo(derivativeBody);
        assertThat(storage.sha256Hex(derivativeKey)).isEqualTo(sha256Hex(derivativeBody));

        storage.deleteIfExists(objectKey);
        storage.deleteIfExists(derivativeKey);

        assertThatThrownBy(() -> storage.head(objectKey))
            .isInstanceOf(ProblemAssetObjectNotFoundException.class);
    }

    private ProblemAssetStorageProperties properties(URI endpoint) {
        return new ProblemAssetStorageProperties(
            BUCKET,
            endpoint,
            "us-east-1",
            ACCESS_KEY,
            SECRET_KEY,
            true,
            Duration.ofMinutes(15),
            20L * 1024L * 1024L,
            List.of("image/jpeg", "application/pdf"),
            Duration.ofDays(30),
            Duration.ofHours(1)
        );
    }

    private void createBucket(URI endpoint) {
        try (S3Client client = S3Client.builder()
            .endpointOverride(endpoint)
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
            .build()) {
            try {
                client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
            } catch (S3Exception exception) {
                if (exception.statusCode() != 409) {
                    throw exception;
                }
            }
        }
    }

    private String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
