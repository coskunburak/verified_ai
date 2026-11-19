package com.verifiedai.exam.application;

import com.verifiedai.exam.config.ExamProperties;
import com.verifiedai.exam.domain.ExamClockPolicy;
import com.verifiedai.exam.domain.ExamIntegrityCanonicalizer;
import com.verifiedai.exam.domain.ExamSession;
import com.verifiedai.exam.infrastructure.persistence.ExamIntegrityAuditRepository;
import com.verifiedai.exam.infrastructure.persistence.ExamIntegrityAuditRepository.ConsumedNonceRecord;
import com.verifiedai.exam.infrastructure.persistence.ExamIntegrityAuditRepository.ExamIntegrityAuditRecord;
import com.verifiedai.exam.infrastructure.persistence.ExamIntegrityAuditRepository.IntegrityEventType;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Cryptographic submission integrity guard enforcing INV-EXAM-006:
 * session-scoped HKDF-SHA256 proof key derivation, constant-time HMAC-SHA256
 * verification, single-use nonce replay prevention, bounded clock skew checks,
 * fail-closed configuration validation, and KVKK-safe audit persistence.
 */
@Service
public class AssessmentIntegrityGuard {

    private static final Logger LOG = LoggerFactory.getLogger(AssessmentIntegrityGuard.class);
    private static final byte[] HKDF_SALT = "verifiedai-exam-integrity-salt-v1".getBytes(StandardCharsets.UTF_8);
    private static final int MIN_MASTER_KEY_BYTES = 32;
    private static final HexFormat HEX = HexFormat.of();

    public record ExamIntegrityProof(
            String schemaVersion,
            UUID sessionId,
            UUID userId,
            String integrityProtocolVersion,
            String keyId,
            String sessionProofKeyHex,
            Instant issuedAt,
            Instant expiresAt,
            boolean integrityGuardEnabled,
            int llmTokensUsed,
            boolean trainingEligible
    ) {
    }

    public record VerificationOutcome(
            boolean signatureVerified,
            String payloadSha256,
            String savedItemsStateDigest,
            long clientClockSkewMs
    ) {
    }

    private final ExamProperties examProperties;
    private final ExamIntegrityAuditRepository auditRepository;
    private final MeterRegistry meterRegistry;

    @Autowired
    public AssessmentIntegrityGuard(
            ExamProperties examProperties,
            ObjectProvider<ExamIntegrityAuditRepository> auditRepositoryProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        this(
                examProperties,
                auditRepositoryProvider.getIfAvailable(),
                meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new)
        );
    }

    public AssessmentIntegrityGuard(
            ExamProperties examProperties,
            ExamIntegrityAuditRepository auditRepository,
            MeterRegistry meterRegistry
    ) {
        this.examProperties = Objects.requireNonNull(examProperties, "examProperties cannot be null");
        this.auditRepository = auditRepository;
        this.meterRegistry = meterRegistry == null ? new SimpleMeterRegistry() : meterRegistry;
    }

    public boolean isIntegrityGuardEnabled() {
        return examProperties.isIntegrityGuardEnabled();
    }

    public ExamIntegrityProof issueSessionProof(UUID authenticatedUserId, ExamSession session, Instant serverNow) {
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId is required");
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(serverNow, "serverNow is required");

        if (!authenticatedUserId.equals(session.userId())) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_SESSION_FORBIDDEN,
                    "Learner cannot obtain integrity proof for another user's exam session",
                    false,
                    "Request proof only for an owned exam session."
            );
        }
        if (session.status().isTerminal()) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_SESSION_ALREADY_TERMINATED,
                    "Cannot issue integrity proof for finalized session " + session.id(),
                    false,
                    "View the finalized exam receipt."
            );
        }
        if (ExamClockPolicy.isPastGraceDeadline(serverNow, session.graceDeadlineAt())) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_SESSION_EXPIRED,
                    "Integrity proof window has expired alongside the session grace deadline",
                    false,
                    "Submit or review the expired session state."
            );
        }

        if (!examProperties.isIntegrityGuardEnabled()) {
            return new ExamIntegrityProof(
                    "exam-integrity-proof-response-v1",
                    session.id(),
                    session.userId(),
                    ExamIntegrityCanonicalizer.PROTOCOL_V1,
                    safeKeyIdOrDefault(examProperties.getIntegrityActiveKeyId()),
                    null,
                    serverNow,
                    session.graceDeadlineAt(),
                    false,
                    0,
                    false
            );
        }

        byte[] activeMasterKey = requireConfiguredActiveMasterKey();
        String keyId = safeKeyIdOrDefault(examProperties.getIntegrityActiveKeyId());
        ExamIntegrityCanonicalizer.validateKeyId(keyId);
        byte[] proofKey = deriveSessionProofKeyBytes(
                activeMasterKey,
                keyId,
                session.userId(),
                session.id(),
                session.startedAt()
        );
        return new ExamIntegrityProof(
                "exam-integrity-proof-response-v1",
                session.id(),
                session.userId(),
                ExamIntegrityCanonicalizer.PROTOCOL_V1,
                keyId,
                HEX.formatHex(proofKey),
                serverNow,
                session.graceDeadlineAt(),
                true,
                0,
                false
        );
    }

    public VerificationOutcome verifySubmission(
            ExamSession session,
            String idempotencyKey,
            String normalizedSubmissionReason,
            Instant clientTimestamp,
            String submissionSignature,
            String integrityKeyId,
            String submissionNonce,
            String integrityProtocolVersion,
            Instant serverNow
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(serverNow, "serverNow is required");

        long skewMs = ExamClockPolicy.computeClientClockSkewMs(serverNow, clientTimestamp);
        Instant effectiveTimestamp = clientTimestamp != null ? clientTimestamp : serverNow;
        String bodySha256 = ExamIntegrityCanonicalizer.computeCanonicalBodySha256Hex(
                normalizedSubmissionReason,
                effectiveTimestamp
        );
        String itemsDigest = ExamIntegrityCanonicalizer.computeSavedItemsStateDigest(session);

        if (!examProperties.isIntegrityGuardEnabled()) {
            // Legacy compatibility: never claim cryptographic verification when guard is disabled.
            return new VerificationOutcome(false, bodySha256, itemsDigest, skewMs);
        }

        requireConfiguredActiveMasterKey();

        if (isBlank(submissionSignature) || isBlank(integrityKeyId) || isBlank(submissionNonce) || clientTimestamp == null) {
            persistRejectedAudit(
                    session,
                    IntegrityEventType.SIGNATURE_REJECTED,
                    skewMs,
                    bodySha256,
                    submissionNonce,
                    integrityKeyId,
                    idempotencyKey,
                    "SIGNATURE_REQUIRED",
                    serverNow
            );
            incrementRejectedMetric("SIGNATURE_REQUIRED");
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_INTEGRITY_SIGNATURE_REQUIRED,
                    "Signed submission proof is required when assessment integrity guard is enabled",
                    true,
                    "Obtain session integrity proof and resubmit with HMAC-SHA256 signature."
            );
        }

        if (integrityProtocolVersion != null
                && !integrityProtocolVersion.isBlank()
                && !ExamIntegrityCanonicalizer.PROTOCOL_V1.equals(integrityProtocolVersion.trim())) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.EXAM_INTEGRITY_PAYLOAD_INVALID,
                    "Unsupported integrityProtocolVersion: " + integrityProtocolVersion,
                    false,
                    "Use exam-submit-hmac-v1."
            );
        }

        ExamIntegrityCanonicalizer.validateIdempotencyKey(idempotencyKey);
        ExamIntegrityCanonicalizer.validateKeyId(integrityKeyId);
        ExamIntegrityCanonicalizer.validateNonce(submissionNonce);
        ExamIntegrityCanonicalizer.validateSignatureHex(submissionSignature);

        long maxSkewMs = Math.max(1_000L, examProperties.getIntegrityMaxClockSkewMs());
        if (Math.abs(skewMs) > maxSkewMs) {
            persistRejectedAudit(
                    session,
                    IntegrityEventType.CLOCK_SKEW_REJECTED,
                    skewMs,
                    bodySha256,
                    submissionNonce,
                    integrityKeyId.trim(),
                    idempotencyKey,
                    "CLOCK_SKEW_EXCEEDED",
                    serverNow
            );
            incrementRejectedMetric("CLOCK_SKEW_EXCEEDED");
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_INTEGRITY_CLOCK_SKEW_REJECTED,
                    "Client submission timestamp exceeds bounded clock skew tolerance",
                    true,
                    "Synchronize device clock with server time and retry submission."
            );
        }

        byte[] verificationMasterKey = resolveMasterKeyForVerification(integrityKeyId.trim());
        if (verificationMasterKey == null) {
            persistRejectedAudit(
                    session,
                    IntegrityEventType.SIGNATURE_REJECTED,
                    skewMs,
                    bodySha256,
                    submissionNonce,
                    integrityKeyId.trim(),
                    idempotencyKey,
                    "UNKNOWN_KEY_ID",
                    serverNow
            );
            incrementRejectedMetric("UNKNOWN_KEY_ID");
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_INTEGRITY_SIGNATURE_INVALID,
                    "Unknown or expired integrity keyId",
                    true,
                    "Refresh session integrity proof and retry submission."
            );
        }

        byte[] proofKey = deriveSessionProofKeyBytes(
                verificationMasterKey,
                integrityKeyId.trim(),
                session.userId(),
                session.id(),
                session.startedAt()
        );
        String canonicalSigningString = ExamIntegrityCanonicalizer.buildCanonicalSigningString(
                session.userId(),
                session.id(),
                normalizedSubmissionReason,
                idempotencyKey,
                clientTimestamp,
                integrityKeyId.trim(),
                submissionNonce.trim()
        );
        byte[] expectedMac = computeHmacSha256Bytes(proofKey, canonicalSigningString.getBytes(StandardCharsets.UTF_8));
        byte[] providedMac = HEX.parseHex(submissionSignature.trim());

        if (!MessageDigest.isEqual(expectedMac, providedMac)) {
            persistRejectedAudit(
                    session,
                    IntegrityEventType.SIGNATURE_REJECTED,
                    skewMs,
                    bodySha256,
                    submissionNonce,
                    integrityKeyId.trim(),
                    idempotencyKey,
                    "MAC_MISMATCH",
                    serverNow
            );
            incrementRejectedMetric("MAC_MISMATCH");
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_INTEGRITY_SIGNATURE_INVALID,
                    "Submission HMAC signature verification failed",
                    false,
                    "Refresh session state and retry submission."
            );
        }

        String nonceHash = ExamIntegrityCanonicalizer.sha256Hex(submissionNonce.trim());
        String idempHash = ExamIntegrityCanonicalizer.sha256Hex(idempotencyKey.trim());
        if (auditRepository != null) {
            boolean consumed = auditRepository.tryConsumeNonce(new ConsumedNonceRecord(
                    session.id(),
                    session.userId(),
                    nonceHash,
                    idempHash,
                    bodySha256,
                    integrityKeyId.trim(),
                    false,
                    serverNow,
                    session.graceDeadlineAt().plusSeconds(3600)
            ));
            if (!consumed) {
                persistRejectedAudit(
                        session,
                        IntegrityEventType.NONCE_REPLAY_REJECTED,
                        skewMs,
                        bodySha256,
                        submissionNonce,
                        integrityKeyId.trim(),
                        idempotencyKey,
                        "NONCE_ALREADY_USED",
                        serverNow
                );
                incrementRejectedMetric("NONCE_REPLAY_REJECTED");
                throw new ApiProblemException(
                        HttpStatus.CONFLICT,
                        ApiErrorCode.EXAM_INTEGRITY_REPLAY_CONFLICT,
                        "Submission nonce has already been consumed for this exam session",
                        false,
                        "Fetch finalized session receipt or create a fresh submission intent."
                );
            }
            auditRepository.insertAuditLog(new ExamIntegrityAuditRecord(
                    UUID.randomUUID(),
                    session.id(),
                    session.userId(),
                    IntegrityEventType.VERIFIED_SUBMIT,
                    true,
                    skewMs,
                    bodySha256,
                    nonceHash,
                    integrityKeyId.trim(),
                    idempHash,
                    ExamIntegrityCanonicalizer.PROTOCOL_V1,
                    "VERIFIED",
                    false,
                    serverNow
            ));
        }

        incrementVerifiedMetric();
        LOG.info(
                "Exam submission integrity verified: eventCode=VERIFIED_SUBMIT protocolVersion={} skewMs={}",
                ExamIntegrityCanonicalizer.PROTOCOL_V1,
                skewMs
        );
        return new VerificationOutcome(true, bodySha256, itemsDigest, skewMs);
    }

    public static byte[] deriveSessionProofKeyBytes(
            byte[] masterKeyBytes,
            String keyId,
            UUID userId,
            UUID sessionId,
            Instant startedAt
    ) {
        Objects.requireNonNull(masterKeyBytes, "masterKeyBytes is required");
        Objects.requireNonNull(keyId, "keyId is required");
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(startedAt, "startedAt is required");

        String info = "exam-integrity-v1|"
                + keyId.trim() + "|"
                + userId.toString().toLowerCase(Locale.ROOT) + "|"
                + sessionId.toString().toLowerCase(Locale.ROOT) + "|"
                + ExamIntegrityCanonicalizer.formatCanonicalInstant(startedAt);
        byte[] prk = computeHmacSha256Bytes(HKDF_SALT, masterKeyBytes);
        byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);
        byte[] expandInput = new byte[infoBytes.length + 1];
        System.arraycopy(infoBytes, 0, expandInput, 0, infoBytes.length);
        expandInput[infoBytes.length] = 0x01;
        return computeHmacSha256Bytes(prk, expandInput);
    }

    public static byte[] computeHmacSha256Bytes(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 failure", ex);
        }
    }

    public static String computeHmacSha256Hex(byte[] key, byte[] message) {
        return HEX.formatHex(computeHmacSha256Bytes(key, message));
    }

    private byte[] requireConfiguredActiveMasterKey() {
        String raw = examProperties.getIntegrityMasterKey();
        if (raw == null || raw.isBlank()) {
            throw configUnavailable();
        }
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_MASTER_KEY_BYTES) {
            throw configUnavailable();
        }
        return bytes;
    }

    private byte[] resolveMasterKeyForVerification(String keyId) {
        String activeId = safeKeyIdOrDefault(examProperties.getIntegrityActiveKeyId());
        if (activeId.equals(keyId)) {
            return requireConfiguredActiveMasterKey();
        }
        String prevId = examProperties.getIntegrityPreviousKeyId();
        String prevKey = examProperties.getIntegrityPreviousMasterKey();
        if (prevId != null && !prevId.isBlank() && prevId.trim().equals(keyId)
                && prevKey != null && prevKey.getBytes(StandardCharsets.UTF_8).length >= MIN_MASTER_KEY_BYTES) {
            return prevKey.getBytes(StandardCharsets.UTF_8);
        }
        return null;
    }

    private void persistRejectedAudit(
            ExamSession session,
            IntegrityEventType eventType,
            long skewMs,
            String payloadSha256,
            String rawNonce,
            String rawKeyId,
            String rawIdempKey,
            String reasonCode,
            Instant serverNow
    ) {
        if (auditRepository == null) {
            return;
        }
        String nonceHash = rawNonce == null || rawNonce.isBlank()
                ? null
                : ExamIntegrityCanonicalizer.sha256Hex(rawNonce.trim());
        String keyId = rawKeyId == null || rawKeyId.isBlank()
                ? null
                : rawKeyId.trim().substring(0, Math.min(64, rawKeyId.trim().length()));
        String idempHash = rawIdempKey == null || rawIdempKey.isBlank()
                ? null
                : ExamIntegrityCanonicalizer.sha256Hex(rawIdempKey.trim());

        auditRepository.insertAuditLog(new ExamIntegrityAuditRecord(
                UUID.randomUUID(),
                session.id(),
                session.userId(),
                eventType,
                false,
                skewMs,
                payloadSha256,
                nonceHash,
                keyId,
                idempHash,
                ExamIntegrityCanonicalizer.PROTOCOL_V1,
                reasonCode,
                false,
                serverNow
        ));
    }

    private void incrementVerifiedMetric() {
        Counter.builder("exam_integrity_verified")
                .tag("outcome", "VERIFIED")
                .tag("protocol_version", ExamIntegrityCanonicalizer.PROTOCOL_V1)
                .tag("flag_state", "enabled")
                .register(meterRegistry)
                .increment();
        Counter.builder("verifiedai.exam.integrity.verified")
                .tag("outcome", "VERIFIED")
                .tag("protocol_version", ExamIntegrityCanonicalizer.PROTOCOL_V1)
                .tag("flag_state", "enabled")
                .register(meterRegistry)
                .increment();
    }

    private void incrementRejectedMetric(String reasonCode) {
        Counter.builder("tamper_attempt_rejected")
                .tag("outcome", "REJECTED")
                .tag("reason_code", reasonCode)
                .tag("protocol_version", ExamIntegrityCanonicalizer.PROTOCOL_V1)
                .tag("flag_state", "enabled")
                .register(meterRegistry)
                .increment();
        Counter.builder("verifiedai.exam.integrity.rejected")
                .tag("outcome", "REJECTED")
                .tag("reason_code", reasonCode)
                .tag("protocol_version", ExamIntegrityCanonicalizer.PROTOCOL_V1)
                .tag("flag_state", "enabled")
                .register(meterRegistry)
                .increment();
    }

    private static ApiProblemException configUnavailable() {
        return new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.EXAM_INTEGRITY_CONFIGURATION_UNAVAILABLE,
                "Assessment integrity master key configuration is missing or insufficient",
                false,
                "Contact platform operations to configure exam integrity key material."
        );
    }

    private static String safeKeyIdOrDefault(String raw) {
        return (raw == null || raw.isBlank()) ? "exam-k1" : raw.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
