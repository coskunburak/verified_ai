package com.verifiedai.exam.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.exam.application.AssessmentIntegrityGuard;
import com.verifiedai.exam.application.AssessmentIntegrityGuard.ExamIntegrityProof;
import com.verifiedai.exam.application.ExamQualityGateService;
import com.verifiedai.exam.application.ExamQualityGateService.GateStatus;
import com.verifiedai.exam.application.ExamSessionRuntimeService;
import com.verifiedai.exam.application.ExamSessionRuntimeService.StartSessionCommand;
import com.verifiedai.exam.application.ExamSessionRuntimeService.SubmitSessionCommand;
import com.verifiedai.exam.config.ExamProperties;
import com.verifiedai.exam.domain.AppliedAccommodationSnapshot;
import com.verifiedai.exam.domain.AssembledAssessment;
import com.verifiedai.exam.domain.AssembledAssessmentItem;
import com.verifiedai.exam.domain.AssembledAssessmentRepository;
import com.verifiedai.exam.domain.AssembledAssessmentStatus;
import com.verifiedai.exam.domain.AssemblyDistributionSummary;
import com.verifiedai.exam.domain.AssessmentBlueprintMode;
import com.verifiedai.exam.domain.CognitiveTaxonomyLevel;
import com.verifiedai.exam.domain.ExamIntegrityCanonicalizer;
import com.verifiedai.exam.domain.QuestionDifficultyBand;
import com.verifiedai.exam.domain.QuestionItemFormat;
import com.verifiedai.exam.domain.QuestionOption;
import com.verifiedai.exam.infrastructure.persistence.ExamIntegrityAuditRepository;
import com.verifiedai.exam.infrastructure.persistence.ExamIntegrityAuditRepository.IntegrityEventType;
import com.verifiedai.exam.infrastructure.privacy.ExamPrivacyLifecycleContributor;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

class ExamIntegrityAuditRepositoryIntegrationTest extends PostgresIntegrationTestSupport {

    private static final UUID EXAM_DEF_ID = UUID.fromString("91000000-0000-4000-8000-000000000101");
    private static final UUID BLUEPRINT_ID = UUID.fromString("94000000-0000-4000-8000-000000000101");
    private static final UUID SEEDED_Q1_ID = UUID.fromString("93000000-0000-4000-8000-000000000101");
    private static final String TEST_MASTER_KEY = "synthetic-pg-it-master-key-min-32-bytes-9988776655";

    @Autowired private ExamProperties examProperties;
    @Autowired private AssembledAssessmentRepository assembledAssessmentRepository;
    @Autowired private ExamSessionRuntimeService runtimeService;
    @Autowired private ExamIntegrityAuditRepository integrityAuditRepository;
    @Autowired private ExamPrivacyLifecycleContributor privacyContributor;
    @Autowired private ExamQualityGateService qualityGateService;
    @Autowired private JdbcClient jdbcClient;

    private boolean prevIntegrityEnabled;
    private String prevMasterKey;
    private String prevKeyId;
    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setup() {
        prevIntegrityEnabled = examProperties.isIntegrityGuardEnabled();
        prevMasterKey = examProperties.getIntegrityMasterKey();
        prevKeyId = examProperties.getIntegrityActiveKeyId();
        runtimeService.setSessionRuntimeEnabled(true);
        examProperties.setIntegrityGuardEnabled(true);
        examProperties.setIntegrityActiveKeyId("exam-k1");
        examProperties.setIntegrityMasterKey(TEST_MASTER_KEY);
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
    }

    @AfterEach
    void cleanup() {
        examProperties.setIntegrityGuardEnabled(prevIntegrityEnabled);
        examProperties.setIntegrityMasterKey(prevMasterKey);
        examProperties.setIntegrityActiveKeyId(prevKeyId);
        if (userA != null) {
            privacyContributor.deleteUserData(userA, Instant.now());
        }
        if (userB != null) {
            privacyContributor.deleteUserData(userB, Instant.now());
        }
    }

    @Test
    void verifiesFlywayV054ToV064AuditNoRollbackConcurrentNonceAndPrivacyLifecycle() throws Exception {
        // 1. Flyway V054..V064 verified by ExamQualityGateService against live PostgreSQL while missing owner evidence remains BLOCKED
        var gateReport = qualityGateService.evaluateReadOnlyGate();
        assertThat(gateReport.checks().flywayV054ToV064().status()).isEqualTo(GateStatus.PASS);
        assertThat(gateReport.checks().flywayV054ToV064().verifiedMinVersion()).isEqualTo("054");
        assertThat(gateReport.checks().flywayV054ToV064().verifiedMaxVersion()).isEqualTo("064");
        assertThat(gateReport.gateStatus()).isEqualTo(GateStatus.BLOCKED);
        assertThat(gateReport.phase9ExitAuthorized()).isFalse();

        Instant now = Instant.now();
        UUID assemblyA = saveSyntheticAssembly(userA);
        var startedA = runtimeService.startOrResumeSession(userA, new StartSessionCommand(userA, assemblyA, 120, now, false));
        UUID sessionAId = startedA.session().id();

        ExamIntegrityProof proofA = runtimeService.obtainIntegrityProof(userA, sessionAId);
        assertThat(proofA.integrityGuardEnabled()).isTrue();
        assertThat(proofA.sessionProofKeyHex()).hasSize(64);

        // 2. Rejected 403 signature commits SIGNATURE_REJECTED audit row without terminating session or consuming nonce
        String nonce = "nonce_pg_it_00000001";
        assertThatThrownBy(() -> runtimeService.submitSession(
                userA,
                sessionAId,
                "pg-bad-sig-key",
                new SubmitSessionCommand("USER_INITIATED", now, false, "0".repeat(64), "exam-k1", nonce, "exam-submit-hmac-v1")
        )).isInstanceOf(ApiProblemException.class);

        var logsAfterReject = integrityAuditRepository.findBySessionId(sessionAId);
        assertThat(logsAfterReject).hasSize(1);
        assertThat(logsAfterReject.getFirst().eventType()).isEqualTo(IntegrityEventType.SIGNATURE_REJECTED);
        assertThat(logsAfterReject.getFirst().signatureVerified()).isFalse();

        // 3. Same nonce still succeeds once valid signature is supplied; concurrent duplicate fails safely
        String validSig = sign(proofA.sessionProofKeyHex(), userA, sessionAId, "USER_INITIATED", "pg-valid-key-1", now, "exam-k1", nonce);
        String validSigOtherKey = sign(proofA.sessionProofKeyHex(), userA, sessionAId, "USER_INITIATED", "pg-valid-key-2", now, "exam-k1", nonce);

        var pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Future<String> f1 = pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    runtimeService.submitSession(userA, sessionAId, "pg-valid-key-1",
                            new SubmitSessionCommand("USER_INITIATED", now, false, validSig, "exam-k1", nonce, "exam-submit-hmac-v1"));
                    return "OK";
                } catch (ApiProblemException ex) {
                    return ex.code().name();
                }
            });
            Future<String> f2 = pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    runtimeService.submitSession(userA, sessionAId, "pg-valid-key-2",
                            new SubmitSessionCommand("USER_INITIATED", now, false, validSigOtherKey, "exam-k1", nonce, "exam-submit-hmac-v1"));
                    return "OK";
                } catch (ApiProblemException ex) {
                    return ex.code().name();
                }
            });
            ready.await();
            go.countDown();
            List<String> outcomes = List.of(f1.get(), f2.get());
            assertThat(outcomes).contains("OK");
            assertThat(outcomes.stream().filter("OK"::equals).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        // 4.training_eligible CHECK constraint rejects TRUE at DB level
        assertThatThrownBy(() -> jdbcClient.sql("""
                INSERT INTO exam_integrity_audit_logs (
                    id, session_id, user_id, event_type, signature_verified, payload_sha256, reason_code, training_eligible
                ) VALUES (:id, :sid, :uid, 'VERIFIED_SUBMIT', TRUE, :sha, 'CHK_TEST', TRUE)
                """)
                .param("id", UUID.randomUUID())
                .param("sid", sessionAId)
                .param("uid", userA)
                .param("sha", "a".repeat(64))
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);

        // 5. Privacy export & child-first deletion isolation (userB unaffected)
        UUID assemblyB = saveSyntheticAssembly(userB);
        var startedB = runtimeService.startOrResumeSession(userB, new StartSessionCommand(userB, assemblyB, 120, now, false));
        ExamIntegrityProof proofB = runtimeService.obtainIntegrityProof(userB, startedB.session().id());
        String sigB = sign(proofB.sessionProofKeyHex(), userB, startedB.session().id(), "USER_INITIATED", "pg-userb-key", now, "exam-k1", "nonce_userb_00000001");
        runtimeService.submitSession(userB, startedB.session().id(), "pg-userb-key",
                new SubmitSessionCommand("USER_INITIATED", now, false, sigB, "exam-k1", "nonce_userb_00000001", "exam-submit-hmac-v1"));

        Map<String, Object> exportA = privacyContributor.exportUserData(userA);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exportedAuditsA = (List<Map<String, Object>>) exportA.get("examIntegrityAuditLogs");
        assertThat(exportedAuditsA).isNotEmpty();
        assertThat(exportedAuditsA.getFirst()).doesNotContainKeys("nonceHash", "idempotencyKeyHash", "sessionProofKeyHex");

        // Age audit log past 30 days on userB and verify TTL purge removes aged audit without harming fresh logs
        jdbcClient.sql("UPDATE exam_integrity_audit_logs SET created_at = :oldTs WHERE user_id = :uid")
                .param("oldTs", Timestamp.from(now.minus(Duration.ofDays(31))))
                .param("uid", userB)
                .update();

        privacyContributor.deleteUserData(userA, now);
        assertThat(integrityAuditRepository.findByUserId(userA, 50)).isEmpty();
        assertThat(integrityAuditRepository.findByUserId(userB, 50)).hasSize(1);

        int purged = integrityAuditRepository.purgeExpired(now, Duration.ofDays(30));
        assertThat(purged).isGreaterThanOrEqualTo(1);
        assertThat(integrityAuditRepository.findByUserId(userB, 50)).isEmpty();
    }

    private UUID saveSyntheticAssembly(UUID owner) {
        UUID assemblyId = UUID.randomUUID();
        Instant now = Instant.now();
        AssembledAssessment assembled = new AssembledAssessment(
                assemblyId,
                owner,
                null,
                BLUEPRINT_ID,
                "BP-TYT-MATH-DIAG-2026-V1",
                "TYT Mathematics Diagnostic Baseline",
                AssessmentBlueprintMode.DIAGNOSTIC_BASELINE,
                EXAM_DEF_ID,
                1,
                "s910-pg-" + owner.toString().substring(0, 8),
                "d".repeat(64),
                5,
                900,
                new BigDecimal("1.00"),
                900,
                new AppliedAccommodationSnapshot(new BigDecimal("1.00"), false, false, false),
                new AssemblyDistributionSummary(Map.of("MATH.ARITHMETIC.INTEGER_OPERATIONS", 5), Map.of("EASY", 5), Map.of("RECALL", 5), Map.of("TYT_MATH", 5), 0),
                AssembledAssessmentStatus.ASSEMBLED,
                now,
                now.plusSeconds(86400),
                false,
                java.util.stream.IntStream.rangeClosed(1, 5)
                        .mapToObj(seq -> {
                            UUID qId = seq == 1
                                    ? SEEDED_Q1_ID
                                    : UUID.fromString(String.format("93000000-0000-4000-8000-0000000001%02d", seq));
                            return new AssembledAssessmentItem(
                                    UUID.randomUUID(),
                                    assemblyId,
                                    qId,
                                    "TYT-MATH-2026-INT-00" + seq,
                                    seq,
                                    "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                                    null,
                                    QuestionItemFormat.SINGLE_CHOICE_MCQ,
                                    QuestionDifficultyBand.EASY,
                                    CognitiveTaxonomyLevel.RECALL,
                                    "Evaluate $2 + 3$ #" + seq,
                                    null,
                                    null,
                                    false,
                                    List.of("A", "B", "C", "D", "E"),
                                    List.of(
                                            new QuestionOption(UUID.randomUUID(), qId, "A", 1, "4", null, false, null, false),
                                            new QuestionOption(UUID.randomUUID(), qId, "B", 2, "5", null, true, null, false),
                                            new QuestionOption(UUID.randomUUID(), qId, "C", 3, "6", null, false, null, false),
                                            new QuestionOption(UUID.randomUUID(), qId, "D", 4, "7", null, false, null, false),
                                            new QuestionOption(UUID.randomUUID(), qId, "E", 5, "8", null, false, null, false)
                                    ),
                                    new BigDecimal("1.00"),
                                    60,
                                    false
                            );
                        })
                        .toList()
        );
        assembledAssessmentRepository.save(assembled);
        return assemblyId;
    }

    private static String sign(
            String proofHex,
            UUID userId,
            UUID sessionId,
            String reason,
            String idemp,
            Instant ts,
            String keyId,
            String nonce
    ) {
        String canonical = ExamIntegrityCanonicalizer.buildCanonicalSigningString(
                userId, sessionId, reason, idemp, ts, keyId, nonce
        );
        return AssessmentIntegrityGuard.computeHmacSha256Hex(
                HexFormat.of().parseHex(proofHex),
                canonical.getBytes(StandardCharsets.UTF_8)
        );
    }
}
