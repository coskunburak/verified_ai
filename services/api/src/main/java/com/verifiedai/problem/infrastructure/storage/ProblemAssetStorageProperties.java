package com.verifiedai.problem.infrastructure.storage;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.problem-assets")
public record ProblemAssetStorageProperties(
    String bucket,
    URI endpoint,
    String region,
    String accessKey,
    String secretKey,
    boolean pathStyleAccess,
    Duration presignTtl,
    long maxSizeBytes,
    List<String> allowedContentTypes,
    Duration pendingRetention,
    Duration cleanupInterval
) {
    public ProblemAssetStorageProperties {
        bucket = blankToDefault(bucket, "verified-ai-problem-assets-local");
        region = blankToDefault(region, "us-east-1");
        accessKey = accessKey == null ? "" : accessKey;
        secretKey = secretKey == null ? "" : secretKey;
        presignTtl = presignTtl == null ? Duration.ofMinutes(15) : presignTtl;
        maxSizeBytes = maxSizeBytes <= 0 ? 20L * 1024L * 1024L : maxSizeBytes;
        allowedContentTypes = allowedContentTypes == null || allowedContentTypes.isEmpty()
            ? List.of("image/jpeg", "application/pdf")
            : List.copyOf(allowedContentTypes);
        pendingRetention = pendingRetention == null ? Duration.ofDays(30) : pendingRetention;
        cleanupInterval = cleanupInterval == null ? Duration.ofHours(1) : cleanupInterval;
    }

    public boolean credentialsConfigured() {
        return !accessKey.isBlank() && !secretKey.isBlank();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
