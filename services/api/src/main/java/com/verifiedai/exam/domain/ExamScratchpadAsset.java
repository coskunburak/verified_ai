package com.verifiedai.exam.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

public record ExamScratchpadAsset(
        UUID id,
        UUID sessionId,
        UUID sessionItemId,
        UUID userId,
        int itemPosition,
        int pageNumber,
        String storageKey,
        String mimeType,
        long byteSize,
        String sha256Checksum,
        Integer widthPx,
        Integer heightPx,
        String captureSource,
        String bookletPublisher,
        String f28VerificationStatus,
        boolean trainingEligible,
        Instant capturedAt,
        Instant createdAt
) {
    public static final int MAX_PAGES_PER_ITEM = 4;
    public static final long MAX_BYTE_SIZE = 15_728_640L;
    public static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/heic");
    public static final Set<String> ALLOWED_CAPTURE_SOURCES = Set.of("DIGITAL_MOCK_SCRATCHPAD", "PAPER_DENEME_BOOKLET");
    public static final Set<String> ALLOWED_F28_STATUSES = Set.of("QUEUED_FOR_POST_SUBMIT_BATCH", "NOT_APPLICABLE");
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    public ExamScratchpadAsset {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(sessionItemId, "sessionItemId is required");
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(capturedAt, "capturedAt is required");
        Objects.requireNonNull(createdAt, "createdAt is required");

        if (itemPosition < 1) {
            throw invalidAsset("itemPosition must be >= 1");
        }
        if (pageNumber < 1 || pageNumber > MAX_PAGES_PER_ITEM) {
            throw invalidAsset("pageNumber must be between 1 and " + MAX_PAGES_PER_ITEM);
        }
        String expectedPrefix = "users/" + userId + "/exam-sessions/" + sessionId + "/scratchpad/";
        if (storageKey == null || storageKey.isBlank()) {
            throw forbiddenStorageKey("storageKey is required");
        }
        storageKey = storageKey.trim();
        if (!storageKey.startsWith(expectedPrefix)
                || storageKey.length() <= expectedPrefix.length()
                || storageKey.contains("..")
                || storageKey.contains("\\")
                || storageKey.length() > 512) {
            throw forbiddenStorageKey("storageKey must stay scoped to " + expectedPrefix);
        }

        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType.trim())) {
            throw invalidAsset("mimeType must be one of image/jpeg, image/png, or image/heic");
        }
        mimeType = mimeType.trim();

        if (byteSize < 1 || byteSize > MAX_BYTE_SIZE) {
            throw invalidAsset("byteSize must be between 1 and " + MAX_BYTE_SIZE);
        }
        if (sha256Checksum == null || !SHA256_PATTERN.matcher(sha256Checksum.trim()).matches()) {
            throw invalidAsset("sha256Checksum must be a 64-character lowercase hex SHA-256 digest");
        }
        sha256Checksum = sha256Checksum.trim();

        if (widthPx != null && (widthPx < 200 || widthPx > 12_000)) {
            throw invalidAsset("widthPx must be between 200 and 12000");
        }
        if (heightPx != null && (heightPx < 200 || heightPx > 12_000)) {
            throw invalidAsset("heightPx must be between 200 and 12000");
        }

        if (captureSource == null || !ALLOWED_CAPTURE_SOURCES.contains(captureSource.trim())) {
            throw invalidAsset("captureSource must be DIGITAL_MOCK_SCRATCHPAD or PAPER_DENEME_BOOKLET");
        }
        captureSource = captureSource.trim();

        if (bookletPublisher != null) {
            String trimmedPublisher = bookletPublisher.trim();
            bookletPublisher = trimmedPublisher.isEmpty() ? null : trimmedPublisher;
            if (bookletPublisher != null && bookletPublisher.length() > 120) {
                throw invalidAsset("bookletPublisher must be at most 120 characters");
            }
        }

        f28VerificationStatus = f28VerificationStatus == null
                ? "QUEUED_FOR_POST_SUBMIT_BATCH"
                : f28VerificationStatus.trim();
        if (!ALLOWED_F28_STATUSES.contains(f28VerificationStatus)) {
            throw invalidAsset("f28VerificationStatus is invalid");
        }

        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
    }

    private static ApiProblemException forbiddenStorageKey(String message) {
        return new ApiProblemException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.EXAM_SCRATCHPAD_STORAGE_KEY_FORBIDDEN,
                message,
                false,
                "Upload scratchpad assets only inside your active exam session storage prefix."
        );
    }

    private static ApiProblemException invalidAsset(String message) {
        return new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.EXAM_SCRATCHPAD_INVALID_ASSET,
                message,
                true,
                "Capture a valid scratchpad image (JPEG/PNG/HEIC, up to 15 MiB) and retry."
        );
    }
}
