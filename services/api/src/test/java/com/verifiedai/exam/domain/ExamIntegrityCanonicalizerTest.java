package com.verifiedai.exam.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.exam.application.AssessmentIntegrityGuard;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExamIntegrityCanonicalizerTest {

    private static final byte[] SYNTHETIC_MASTER_KEY =
            "synthetic-test-master-key-minimum-32-bytes-0123456789".getBytes(StandardCharsets.UTF_8);
    private static final UUID USER_ID = UUID.fromString("91000000-0000-4000-8000-000000000a01");
    private static final UUID SESSION_ID = UUID.fromString("95000000-0000-4000-8000-000000000001");
    private static final Instant STARTED_AT = Instant.parse("2026-11-18T10:00:00.000Z");

    private record Vector(
            String keyId,
            String reason,
            String idempotencyKey,
            String timestamp,
            String nonce,
            String expectedBodySha256,
            String expectedProofKeyHex,
            String expectedSignatureHex
    ) {}

    private static final List<Vector> CROSS_PLATFORM_VECTORS = List.of(
            new Vector("exam-k1", "USER_INITIATED", "idemp-v1-0001", "2026-11-18T10:15:00.000Z", "nonce_vec_0000000001",
                    "69127b8f22be1e5948c8de321dc1a1a546555e277780638df7f14104312db3c7",
                    "f9e76f355883e9520cb3f6375a697b72851b42b2aecd6c2b03a5371c046bd034",
                    "1491766360640074b34ee3c6c684618d94c4589185430351d525ad530696cf88"),
            new Vector("exam-k1", "USER_INITIATED", "idemp-v1-0002", "2026-11-18T10:15:01.250Z", "nonce_vec_0000000002",
                    "53baa063ae1c4f5ccba0eb0605fd2c0e80384b472e51e454644c17638dc742ea",
                    "f9e76f355883e9520cb3f6375a697b72851b42b2aecd6c2b03a5371c046bd034",
                    "b28a8194464e18bbda2f9a485fbafd59dfdb3ea033f9c1aa732ba42d3fdcf3d2"),
            new Vector("exam-k1", "SERVER_DEADLINE_EXPIRED", "idemp-v1-0003", "2026-11-18T10:24:00.999Z", "nonce_vec_0000000003",
                    "c1932c455b25115794d9edfedbf25a525e35cc41a208a7162960e43ed3bd2b5f",
                    "f9e76f355883e9520cb3f6375a697b72851b42b2aecd6c2b03a5371c046bd034",
                    "818fcac843a290cca817987705acbb955021356842f64ad692db90d807e49255"),
            new Vector("exam-k1", "OFFLINE_GRACE_EXHAUSTED", "idemp-v1-0004", "2026-11-18T10:26:00.001Z", "nonce_vec_0000000004",
                    "c316cae22810d9dae60eeaaf43c02d1716715900642f9366999e4d16e6d12801",
                    "f9e76f355883e9520cb3f6375a697b72851b42b2aecd6c2b03a5371c046bd034",
                    "51eec75b3b2cc57b25c0c0a82e819e84ec950600bcc15d50e4976666a00d1701"),
            new Vector("exam-k1", "USER_ABANDONED", "idemp-v1-0005", "2026-11-18T10:05:30.500Z", "nonce_vec_0000000005",
                    "a2506d83b6361a7ac46ed2d1599450e34375b47c10672c8e86b381e024696229",
                    "f9e76f355883e9520cb3f6375a697b72851b42b2aecd6c2b03a5371c046bd034",
                    "385e96e70c1ac869b41c657bbc1aab61fa3e1fc75cc87a10bf9bf15270fd1b80"),
            new Vector("exam-k2", "USER_INITIATED", "idemp-v1-0006", "2026-11-18T10:15:00.123Z", "nonce_vec_0000000006",
                    "20d8055391c2db4dd443f575c9350dc73f88ac5753fe24d9fbc980b3d8ffda67",
                    "4e6df0aea348a008077e6a89b3c898d967043e41c8d9537b78f0692ca2576388",
                    "38128b16f20e866b193888752c3fae083a5ade3fdd52e2a51eb0f083422f48a0"),
            new Vector("exam-k1", "USER_INITIATED", "submit-key-alpha-beta", "2026-11-18T10:16:00.000Z", "nonce_Alpha_00000007",
                    "f0e4473aa63a065303f2bf9e1184fd8f7466ba0ad6fae9079cd0a11664a5f577",
                    "f9e76f355883e9520cb3f6375a697b72851b42b2aecd6c2b03a5371c046bd034",
                    "5b591141484de1004abec2461b0d16acf5325015f637c3ac51d0c87cb36b4509"),
            new Vector("exam-k1", "SERVER_DEADLINE_EXPIRED", "submit-key-deadline-08", "2026-11-18T10:24:00.000Z", "nonce_Beta__00000008",
                    "46124aa45eff5e8c6502465ca305bdc57b6ac27d96ef6481efca2f87628b9965",
                    "f9e76f355883e9520cb3f6375a697b72851b42b2aecd6c2b03a5371c046bd034",
                    "99b92deb697416ff310c4144c83e45d830699cf309bca97528546da57484f134"),
            new Vector("exam-k1", "OFFLINE_GRACE_EXHAUSTED", "submit-key-grace-09", "2026-11-18T10:25:59.999Z", "nonce_Gamma_00000009",
                    "c1e2deb1dab0b005a9a810c6543fcb964a0d91c8ffc200123684cc3988322221",
                    "f9e76f355883e9520cb3f6375a697b72851b42b2aecd6c2b03a5371c046bd034",
                    "a243aee599585cab6d88df3807b4bdb362349a253862ddd1913e5e58acfa6490"),
            new Vector("exam-k2", "USER_INITIATED", "submit-key-rot-10", "2026-11-18T10:19:45.042Z", "nonce_Delta_00000010",
                    "4cde2cd7cd9c55ad065c305cb134218099222e22cdd517ed998ace13b3faa535",
                    "4e6df0aea348a008077e6a89b3c898d967043e41c8d9537b78f0692ca2576388",
                    "dc74e35bcf67d60541de1c9d4c8a590a90d75338cc13439e498540450f5a5197")
    );

    @Test
    void matchesAllTenCrossPlatformCanonicalHkdfAndHmacVectorsByteForByte() {
        assertThat(CROSS_PLATFORM_VECTORS).hasSize(10);
        for (Vector v : CROSS_PLATFORM_VECTORS) {
            Instant ts = Instant.parse(v.timestamp());
            String bodyHash = ExamIntegrityCanonicalizer.computeCanonicalBodySha256Hex(v.reason(), ts);
            assertThat(bodyHash).isEqualTo(v.expectedBodySha256());

            byte[] proofKey = AssessmentIntegrityGuard.deriveSessionProofKeyBytes(
                    SYNTHETIC_MASTER_KEY,
                    v.keyId(),
                    USER_ID,
                    SESSION_ID,
                    STARTED_AT
            );
            assertThat(java.util.HexFormat.of().formatHex(proofKey)).isEqualTo(v.expectedProofKeyHex());

            String canonical = ExamIntegrityCanonicalizer.buildCanonicalSigningString(
                    USER_ID,
                    SESSION_ID,
                    v.reason(),
                    v.idempotencyKey(),
                    ts,
                    v.keyId(),
                    v.nonce()
            );
            String sig = AssessmentIntegrityGuard.computeHmacSha256Hex(
                    proofKey,
                    canonical.getBytes(StandardCharsets.UTF_8)
            );
            assertThat(sig).isEqualTo(v.expectedSignatureHex());
        }
    }

    @Test
    void normalizesDualRoutePathsAndRejectsControlCharacters() {
        String v1Path = "/v1/exams/sessions/" + SESSION_ID + "/submit";
        String apiV1Path = "/api/v1/exams/sessions/" + SESSION_ID.toString().toUpperCase() + "/submit";
        assertThat(ExamIntegrityCanonicalizer.normalizeSubmitPath(v1Path, SESSION_ID))
                .isEqualTo(ExamIntegrityCanonicalizer.normalizeSubmitPath(apiV1Path, SESSION_ID));

        assertThatThrownBy(() -> ExamIntegrityCanonicalizer.buildCanonicalSigningString(
                USER_ID,
                SESSION_ID,
                "USER_INITIATED",
                "bad\nkey",
                Instant.parse("2026-11-18T10:15:00Z"),
                "exam-k1",
                "nonce_vec_0000000001"
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                        .isEqualTo(ApiErrorCode.EXAM_INTEGRITY_PAYLOAD_INVALID));

        assertThatThrownBy(() -> ExamIntegrityCanonicalizer.validateSignatureHex("UPPERCASEHEX" + "0".repeat(52)))
                .isInstanceOf(ApiProblemException.class);
    }

    @Test
    void savedItemsStateDigestIsDeterministicAcrossItemOrdering() {
        ExamSessionItem item1 = sampleItem(1, "B", 2);
        ExamSessionItem item2 = sampleItem(2, "D", 4);
        ExamSession sForward = sampleSession(List.of(item1, item2));
        ExamSession sReversed = sampleSession(List.of(item2, item1));

        String d1 = ExamIntegrityCanonicalizer.computeSavedItemsStateDigest(sForward);
        String d2 = ExamIntegrityCanonicalizer.computeSavedItemsStateDigest(sReversed);
        assertThat(d1).hasSize(64).isEqualTo(d2);
    }

    private static ExamSessionItem sampleItem(int pos, String selected, int version) {
        return new ExamSessionItem(
                UUID.nameUUIDFromBytes(("item-" + pos).getBytes(StandardCharsets.UTF_8)),
                SESSION_ID,
                pos,
                UUID.nameUUIDFromBytes(("q-" + pos).getBytes(StandardCharsets.UTF_8)),
                "TYT-Q-" + pos,
                "TYT_MATH",
                1,
                "MATH.ALGEBRA",
                "SINGLE_CHOICE_MCQ",
                QuestionDifficultyBand.MEDIUM,
                "Soru " + pos,
                List.of(),
                null,
                BigDecimal.ONE,
                new BigDecimal("0.25"),
                ExamItemResponseState.ANSWERED,
                selected,
                null,
                false,
                List.of(),
                15,
                version,
                version,
                STARTED_AT,
                false,
                false,
                STARTED_AT,
                STARTED_AT,
                List.of()
        );
    }

    private static ExamSession sampleSession(List<ExamSessionItem> items) {
        return new ExamSession(
                SESSION_ID,
                USER_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TR_YKS_TYT_MATH",
                1,
                ExamSessionStatus.IN_PROGRESS,
                "FREE_NAVIGATION",
                true,
                new AppliedAccommodationSnapshot(new BigDecimal("1.00"), false, false, false),
                1200,
                new BigDecimal("1.00"),
                1200,
                120,
                STARTED_AT,
                STARTED_AT.plusSeconds(1200),
                STARTED_AT.plusSeconds(1320),
                STARTED_AT,
                null,
                null,
                null,
                null,
                items.size(),
                items.size(),
                0,
                0,
                0,
                0L,
                null,
                false,
                STARTED_AT,
                STARTED_AT,
                items
        );
    }
}
