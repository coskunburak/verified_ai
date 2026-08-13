package com.verifiedai.problem.application.classification;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class ProblemClassificationRequestFingerprintTest {

    private static final UUID CANONICAL_ID =
        UUID.fromString(
            "11111111-2222-3333-4444-555555555555"
        );

    @Test
    void sameLogicalInputProducesSameFingerprint() {
        String first =
            fingerprint(
                CANONICAL_ID,
                3,
                "curriculum-v1-seed",
                "problem-classification-projection-v1",
                "problem-classification-v1",
                "problem-classifier",
                "v001",
                "problem-classifier-route-v1"
            );

        String second =
            fingerprint(
                CANONICAL_ID,
                3,
                "curriculum-v1-seed",
                "problem-classification-projection-v1",
                "problem-classification-v1",
                "problem-classifier",
                "v001",
                "problem-classifier-route-v1"
            );

        assertThat(first)
            .isEqualTo(second)
            .hasSize(64)
            .matches("[0-9a-f]{64}");
    }

    @Test
    void canonicalProblemIdChangesFingerprint() {
        assertDifferent(
            fingerprint(
                UUID.randomUUID(),
                3,
                "curriculum-v1-seed",
                "problem-classification-projection-v1",
                "problem-classification-v1",
                "problem-classifier",
                "v001",
                "problem-classifier-route-v1"
            )
        );
    }

    @Test
    void canonicalRevisionChangesFingerprint() {
        assertDifferent(
            fingerprint(
                CANONICAL_ID,
                4,
                "curriculum-v1-seed",
                "problem-classification-projection-v1",
                "problem-classification-v1",
                "problem-classifier",
                "v001",
                "problem-classifier-route-v1"
            )
        );
    }

    @Test
    void ontologyVersionChangesFingerprint() {
        assertDifferent(
            fingerprint(
                CANONICAL_ID,
                3,
                "curriculum-v2",
                "problem-classification-projection-v1",
                "problem-classification-v1",
                "problem-classifier",
                "v001",
                "problem-classifier-route-v1"
            )
        );
    }

    @Test
    void projectionVersionChangesFingerprint() {
        assertDifferent(
            fingerprint(
                CANONICAL_ID,
                3,
                "curriculum-v1-seed",
                "projection-v2",
                "problem-classification-v1",
                "problem-classifier",
                "v001",
                "problem-classifier-route-v1"
            )
        );
    }

    @Test
    void schemaVersionChangesFingerprint() {
        assertDifferent(
            fingerprint(
                CANONICAL_ID,
                3,
                "curriculum-v1-seed",
                "problem-classification-projection-v1",
                "problem-classification-v2",
                "problem-classifier",
                "v001",
                "problem-classifier-route-v1"
            )
        );
    }

    @Test
    void promptIdentityChangesFingerprint() {
        assertDifferent(
            fingerprint(
                CANONICAL_ID,
                3,
                "curriculum-v1-seed",
                "problem-classification-projection-v1",
                "problem-classification-v1",
                "problem-classifier-v2",
                "v002",
                "problem-classifier-route-v1"
            )
        );
    }

    @Test
    void routePolicyVersionChangesFingerprint() {
        assertDifferent(
            fingerprint(
                CANONICAL_ID,
                3,
                "curriculum-v1-seed",
                "problem-classification-projection-v1",
                "problem-classification-v1",
                "problem-classifier",
                "v001",
                "problem-classifier-route-v2"
            )
        );
    }

    private static void assertDifferent(
        String candidate
    ) {
        assertThat(candidate)
            .isNotEqualTo(
                baseline()
            );
    }

    private static String baseline() {
        return fingerprint(
            CANONICAL_ID,
            3,
            "curriculum-v1-seed",
            "problem-classification-projection-v1",
            "problem-classification-v1",
            "problem-classifier",
            "v001",
            "problem-classifier-route-v1"
        );
    }

    private static String fingerprint(
        UUID canonicalProblemId,
        int canonicalRevision,
        String ontologyVersion,
        String projectionVersion,
        String schemaVersion,
        String promptId,
        String promptVersion,
        String routePolicyVersion
    ) {
        return ProblemClassificationRequestFingerprint
            .create(
                canonicalProblemId,
                canonicalRevision,
                ontologyVersion,
                projectionVersion,
                schemaVersion,
                promptId,
                promptVersion,
                routePolicyVersion
            );
    }
}
