package com.verifiedai.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.verifiedai.exam.application.AssessmentIntegrityGuard.ExamIntegrityProof;
import com.verifiedai.exam.application.AssessmentIntegrityGuard.VerificationOutcome;
import com.verifiedai.exam.config.ExamProperties;
import com.verifiedai.exam.domain.AppliedAccommodationSnapshot;
import com.verifiedai.exam.domain.ExamIntegrityCanonicalizer;
import com.verifiedai.exam.domain.ExamSession;
import com.verifiedai.exam.domain.ExamSessionStatus;
import com.verifiedai.exam.infrastructure.persistence.ExamIntegrityAuditRepository;
import com.verifiedai.exam.infrastructure.persistence.ExamIntegrityAuditRepository.IntegrityEventType;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssessmentIntegrityGuardTest {

    private static final String MASTER_KEY_1 = "synthetic-test-master-key-minimum-32-bytes-0123456789";
    private static final String MASTER_KEY_2 = "synthetic-test-master-key-rotated-32-bytes-abcdefghij";
    private static final UUID USER_ID = UUID.fromString("91000000-0000-4000-8000-000000000a01");
    private static final UUID OTHER_USER_ID = UUID.fromString("91000000-0000-4000-8000-000000000a99");
    private static final UUID SESSION_ID = UUID.fromString("95000000-0000-4000-8000-000000000001");
    private static final Instant STARTED_AT = Instant.parse("2026-11-18T10:00:00.000Z");

    private ExamProperties props;
    private ExamIntegrityAuditRepository auditRepository;
    private SimpleMeterRegistry meterRegistry;
    private AssessmentIntegrityGuard guard;

    @BeforeEach
    void setUp() {
        props = new ExamProperties();
        props.setIntegrityGuardEnabled(true);
        props.setIntegrityActiveKeyId("exam-k1");
        props.setIntegrityMasterKey(MASTER_KEY_1);
        props.setIntegrityMaxClockSkewMs(30_000L);
        auditRepository = mock(ExamIntegrityAuditRepository.class);
        when(auditRepository.tryConsumeNonce(any())).thenReturn(true);
        meterRegistry = new SimpleMeterRegistry();
        guard = new AssessmentIntegrityGuard(props, auditRepository, meterRegistry);
    }

    @Test
    void flagOffAllowsLegacyUnsignedSubmitAndNeverEmitsVerifiedMetric() {
        props.setIntegrityGuardEnabled(false);
        ExamSession session = sampleSession(ExamSessionStatus.IN_PROGRESS);
        Instant now = STARTED_AT.plusSeconds(300);

        ExamIntegrityProof proof = guard.issueSessionProof(USER_ID, session, now);
        assertThat(proof.integrityGuardEnabled()).isFalse();
        assertThat(proof.sessionProofKeyHex()).isNull();

        VerificationOutcome outcome = guard.verifySubmission(
                session, "legacy-submit-key", "USER_INITIATED", now,
                null, null, null, null, now
        );
        assertThat(outcome.signatureVerified()).isFalse();
        assertThat(meterRegistry.find("exam_integrity_verified").counter()).isNull();
        verify(auditRepository, never()).insertAuditLog(any());
    }

    @Test
    void failsClosed503WhenGuardEnabledAndMasterKeyMissingOrTooShort() {
        props.setIntegrityMasterKey("too-short");
        ExamSession session = sampleSession(ExamSessionStatus.IN_PROGRESS);
        Instant now = STARTED_AT.plusSeconds(300);

        assertThatThrownBy(() -> guard.issueSessionProof(USER_ID, session, now))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException p = (ApiProblemException) ex;
                    assertThat(p.status().value()).isEqualTo(503);
                    assertThat(p.code()).isEqualTo(ApiErrorCode.EXAM_INTEGRITY_CONFIGURATION_UNAVAILABLE);
                });

        assertThatThrownBy(() -> guard.verifySubmission(
                session, "key-1", "USER_INITIATED", now,
                "a".repeat(64), "exam-k1", "nonce_valid_00000001", "exam-submit-hmac-v1", now
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).status().value()).isEqualTo(503));
    }

    @Test
    void enforcesOwnershipProofIssuanceValidSignatureKeyRotationAndBoundarySkew() {
        ExamSession session = sampleSession(ExamSessionStatus.IN_PROGRESS);
        Instant now = STARTED_AT.plusSeconds(300);

        // Foreign user BOLA on proof issuance -> 403
        assertThatThrownBy(() -> guard.issueSessionProof(OTHER_USER_ID, session, now))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).status().value()).isEqualTo(403));

        // Issue active proof for owner
        ExamIntegrityProof proof = guard.issueSessionProof(USER_ID, session, now);
        assertThat(proof.integrityGuardEnabled()).isTrue();
        assertThat(proof.sessionProofKeyHex()).matches("^[0-9a-f]{64}$");

        // Boundary skew T + 30_000ms succeeds; T + 30_001ms rejects with 403
        Instant boundaryOkTs = now.plusMillis(30_000L);
        String nonceOk = "nonce_boundary_00000001";
        String idempOk = "submit-boundary-ok";
        String sigOk = signWithProofHex(proof.sessionProofKeyHex(), USER_ID, SESSION_ID, "USER_INITIATED", idempOk, boundaryOkTs, "exam-k1", nonceOk);
        VerificationOutcome okOutcome = guard.verifySubmission(
                session, idempOk, "USER_INITIATED", boundaryOkTs,
                sigOk, "exam-k1", nonceOk, "exam-submit-hmac-v1", now
        );
        assertThat(okOutcome.signatureVerified()).isTrue();
        assertThat(meterRegistry.get("exam_integrity_verified").counter().count()).isEqualTo(1.0);

        // Key rotation: active becomes exam-k2, previous stays exam-k1 (verify-only)
        props.setIntegrityActiveKeyId("exam-k2");
        props.setIntegrityMasterKey(MASTER_KEY_2);
        props.setIntegrityPreviousKeyId("exam-k1");
        props.setIntegrityPreviousMasterKey(MASTER_KEY_1);

        String rotNonce = "nonce_rotation_00000002";
        String rotSig = signWithProofHex(proof.sessionProofKeyHex(), USER_ID, SESSION_ID, "USER_INITIATED", "submit-rot", now, "exam-k1", rotNonce);
        VerificationOutcome rotOutcome = guard.verifySubmission(
                session, "submit-rot", "USER_INITIATED", now,
                rotSig, "exam-k1", rotNonce, "exam-submit-hmac-v1", now
        );
        assertThat(rotOutcome.signatureVerified()).isTrue();

        // Skew T + 30_001ms rejected with 403 CLOCK_SKEW_REJECTED
        Instant badSkewTs = now.plusMillis(30_001L);
        String sigSkew = signWithProofHex(proof.sessionProofKeyHex(), USER_ID, SESSION_ID, "USER_INITIATED", "submit-skew", badSkewTs, "exam-k1", "nonce_skew_0000000003");
        assertThatThrownBy(() -> guard.verifySubmission(
                session, "submit-skew", "USER_INITIATED", badSkewTs,
                sigSkew, "exam-k1", "nonce_skew_0000000003", "exam-submit-hmac-v1", now
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                        .isEqualTo(ApiErrorCode.EXAM_INTEGRITY_CLOCK_SKEW_REJECTED));
    }

    @Test
    void badMacRejects403WithoutConsumingNonceAndReusedNonceRejects409() {
        ExamSession session = sampleSession(ExamSessionStatus.IN_PROGRESS);
        Instant now = STARTED_AT.plusSeconds(300);
        ExamIntegrityProof proof = guard.issueSessionProof(USER_ID, session, now);

        String nonce = "nonce_tamper_000000001";
        String validSig = signWithProofHex(proof.sessionProofKeyHex(), USER_ID, SESSION_ID, "USER_INITIATED", "submit-1", now, "exam-k1", nonce);
        String tamperedSig = (validSig.startsWith("0") ? "1" : "0") + validSig.substring(1);

        assertThatThrownBy(() -> guard.verifySubmission(
                session, "submit-1", "USER_INITIATED", now,
                tamperedSig, "exam-k1", nonce, "exam-submit-hmac-v1", now
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                        .isEqualTo(ApiErrorCode.EXAM_INTEGRITY_SIGNATURE_INVALID));

        // 403 bad MAC must NOT consume the nonce
        verify(auditRepository, never()).tryConsumeNonce(any());
        var captor = ArgumentCaptor.forClass(ExamIntegrityAuditRepository.ExamIntegrityAuditRecord.class);
        verify(auditRepository).insertAuditLog(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(IntegrityEventType.SIGNATURE_REJECTED);
        assertThat(captor.getValue().signatureVerified()).isFalse();

        // Now replay conflict when DB returns false for tryConsumeNonce (even with valid MAC for submit-2)
        String validSigSubmit2 = signWithProofHex(proof.sessionProofKeyHex(), USER_ID, SESSION_ID, "USER_INITIATED", "submit-2", now, "exam-k1", nonce);
        when(auditRepository.tryConsumeNonce(any())).thenReturn(false);
        assertThatThrownBy(() -> guard.verifySubmission(
                session, "submit-2", "USER_INITIATED", now,
                validSigSubmit2, "exam-k1", nonce, "exam-submit-hmac-v1", now
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException p = (ApiProblemException) ex;
                    assertThat(p.status().value()).isEqualTo(409);
                    assertThat(p.code()).isEqualTo(ApiErrorCode.EXAM_INTEGRITY_REPLAY_CONFLICT);
                });
    }

    private static String signWithProofHex(
            String proofHex,
            UUID userId,
            UUID sessionId,
            String reason,
            String idempotencyKey,
            Instant clientTs,
            String keyId,
            String nonce
    ) {
        String canonical = ExamIntegrityCanonicalizer.buildCanonicalSigningString(
                userId, sessionId, reason, idempotencyKey, clientTs, keyId, nonce
        );
        return AssessmentIntegrityGuard.computeHmacSha256Hex(
                HexFormat.of().parseHex(proofHex),
                canonical.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static ExamSession sampleSession(ExamSessionStatus status) {
        return new ExamSession(
                SESSION_ID,
                USER_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TR_YKS_TYT_MATH",
                1,
                status,
                "FREE_NAVIGATION",
                true,
                new AppliedAccommodationSnapshot(new BigDecimal("1.00"), false, false, false),
                1440,
                new BigDecimal("1.00"),
                1440,
                120,
                STARTED_AT,
                STARTED_AT.plusSeconds(1440),
                STARTED_AT.plusSeconds(1560),
                STARTED_AT,
                null,
                null,
                null,
                null,
                1,
                0,
                0,
                1,
                0,
                0L,
                null,
                false,
                STARTED_AT,
                STARTED_AT,
                List.of()
        );
    }
}
