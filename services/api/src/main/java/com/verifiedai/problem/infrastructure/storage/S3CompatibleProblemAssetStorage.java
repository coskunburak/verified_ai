package com.verifiedai.problem.infrastructure.storage;

import com.verifiedai.problem.domain.port.PresignedProblemAssetUpload;
import com.verifiedai.problem.domain.port.ProblemAssetObjectMetadata;
import com.verifiedai.problem.domain.port.ProblemAssetObjectNotFoundException;
import com.verifiedai.problem.domain.port.ProblemAssetStorage;
import com.verifiedai.problem.domain.port.ProblemAssetStorageUnavailableException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
public class S3CompatibleProblemAssetStorage implements ProblemAssetStorage {
    private final ProblemAssetStorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner presigner;

    public S3CompatibleProblemAssetStorage(ProblemAssetStorageProperties properties) {
        this.properties = properties;
        this.s3Client = s3Client(properties);
        this.presigner = s3Presigner(properties);
    }

    @Override
    public PresignedProblemAssetUpload presignPut(String objectKey, String contentType, long sizeBytes, Duration ttl) {
        try {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(objectRequest)
                .build();
            PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
            return new PresignedProblemAssetUpload(
                presigned.url().toURI(),
                java.time.Instant.now().plus(ttl),
                Map.of("Content-Type", contentType)
            );
        } catch (Exception exception) {
            throw new ProblemAssetStorageUnavailableException("Problem asset upload URL could not be generated", exception);
        }
    }

    @Override
    public ProblemAssetObjectMetadata head(String objectKey) {
        try {
            var response = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build());
            return new ProblemAssetObjectMetadata(response.contentLength(), response.contentType());
        } catch (NoSuchKeyException exception) {
            throw new ProblemAssetObjectNotFoundException("Problem asset object was not found");
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new ProblemAssetObjectNotFoundException("Problem asset object was not found");
            }
            throw new ProblemAssetStorageUnavailableException("Problem asset object metadata could not be read", exception);
        } catch (RuntimeException exception) {
            throw new ProblemAssetStorageUnavailableException("Problem asset object metadata could not be read", exception);
        }
    }

    @Override
    public String sha256Hex(String objectKey) {
        try (
            ResponseInputStream<GetObjectResponse> object = s3Client.getObject(GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build())
        ) {
            return sha256Hex(object);
        } catch (NoSuchKeyException exception) {
            throw new ProblemAssetObjectNotFoundException("Problem asset object was not found");
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new ProblemAssetObjectNotFoundException("Problem asset object was not found");
            }
            throw new ProblemAssetStorageUnavailableException("Problem asset bytes could not be verified", exception);
        } catch (Exception exception) {
            throw new ProblemAssetStorageUnavailableException("Problem asset bytes could not be verified", exception);
        }
    }

    @Override
    public void deleteIfExists(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw new ProblemAssetStorageUnavailableException("Problem asset object could not be deleted", exception);
            }
        } catch (RuntimeException exception) {
            throw new ProblemAssetStorageUnavailableException("Problem asset object could not be deleted", exception);
        }
    }

    private static String sha256Hex(InputStream inputStream) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static S3Client s3Client(ProblemAssetStorageProperties properties) {
        var builder = S3Client.builder()
            .region(Region.of(properties.region()))
            .forcePathStyle(properties.pathStyleAccess());
        if (properties.endpoint() != null) {
            builder.endpointOverride(properties.endpoint());
        }
        if (properties.credentialsConfigured()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
            ));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    private static S3Presigner s3Presigner(ProblemAssetStorageProperties properties) {
        var builder = S3Presigner.builder()
            .region(Region.of(properties.region()))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyleAccess())
                .build());
        URI endpoint = properties.endpoint();
        if (endpoint != null) {
            builder.endpointOverride(endpoint);
        }
        if (properties.credentialsConfigured()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
            ));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }
}
