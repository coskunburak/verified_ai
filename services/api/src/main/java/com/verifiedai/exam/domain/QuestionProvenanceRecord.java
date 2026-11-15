package com.verifiedai.exam.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

public record QuestionProvenanceRecord(
        UUID id,
        UUID questionItemId,
        QuestionProvenanceSourceType sourceType,
        QuestionLicenseClassification licenseClassification,
        String copyrightHolder,
        String sourceReference,
        String authorId,
        String reviewerId,
        ProvenanceVerificationStatus verificationStatus,
        String provenanceHash,
        Instant verifiedAt,
        boolean trainingEligible,
        Instant createdAt
) {
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    public QuestionProvenanceRecord {
        Objects.requireNonNull(sourceType, "sourceType is required");
        Objects.requireNonNull(licenseClassification, "licenseClassification is required");
        Objects.requireNonNull(verificationStatus, "verificationStatus is required");
        if (copyrightHolder == null || copyrightHolder.isBlank()) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_QUESTION_PROVENANCE_UNVERIFIED,
                    "Provenance copyrightHolder is required",
                    true,
                    "Provide a valid copyrightHolder for question provenance."
            );
        }
        if (sourceReference == null || sourceReference.isBlank()) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_QUESTION_PROVENANCE_UNVERIFIED,
                    "Provenance sourceReference is required",
                    true,
                    "Provide a valid sourceReference for question provenance."
            );
        }
        if (authorId == null || authorId.isBlank() || reviewerId == null || reviewerId.isBlank()) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_QUESTION_PROVENANCE_UNVERIFIED,
                    "Both authorId and reviewerId are required for SME provenance",
                    true,
                    "Provide distinct authorId and reviewerId identifiers."
            );
        }
        authorId = authorId.trim();
        reviewerId = reviewerId.trim();
        if (authorId.equalsIgnoreCase(reviewerId)) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_QUESTION_PROVENANCE_UNVERIFIED,
                    "Provenance reviewerId must be distinct from authorId",
                    true,
                    "Assign an independent SME reviewer distinct from the item author."
            );
        }
        if (provenanceHash == null || !SHA256_PATTERN.matcher(provenanceHash).matches()) {
            provenanceHash = computeProvenanceHash(
                    sourceType,
                    licenseClassification,
                    copyrightHolder.trim(),
                    sourceReference.trim(),
                    authorId,
                    reviewerId
            );
        }
        if (verifiedAt == null) {
            verifiedAt = Instant.now();
        }
        if (createdAt == null) {
            createdAt = verifiedAt;
        }
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
    }

    public boolean isSmeVerified() {
        return verificationStatus == ProvenanceVerificationStatus.VERIFIED
                && !authorId.equalsIgnoreCase(reviewerId)
                && provenanceHash != null
                && SHA256_PATTERN.matcher(provenanceHash).matches();
    }

    public static String computeProvenanceHash(
            QuestionProvenanceSourceType sourceType,
            QuestionLicenseClassification licenseClassification,
            String copyrightHolder,
            String sourceReference,
            String authorId,
            String reviewerId
    ) {
        String canonical = String.join("|",
                String.valueOf(sourceType),
                String.valueOf(licenseClassification),
                copyrightHolder == null ? "" : copyrightHolder.trim(),
                sourceReference == null ? "" : sourceReference.trim(),
                authorId == null ? "" : authorId.trim(),
                reviewerId == null ? "" : reviewerId.trim()
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
