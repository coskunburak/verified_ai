package com.verifiedai.problem.application.classification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

final class ProblemClassificationRequestFingerprint {

    private ProblemClassificationRequestFingerprint() {
    }

    static String create(
        UUID canonicalProblemId,
        int canonicalProblemRevision,
        String ontologyVersion,
        String projectionVersion,
        String schemaVersion,
        String promptId,
        String promptVersion,
        String routePolicyVersion
    ) {
        String canonicalInput = String.join(
            "\n",
            canonicalProblemId.toString(),
            Integer.toString(canonicalProblemRevision),
            ontologyVersion,
            projectionVersion,
            schemaVersion,
            promptId,
            promptVersion,
            routePolicyVersion
        );

        try {
            MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

            byte[] hash =
                digest.digest(
                    canonicalInput.getBytes(
                        StandardCharsets.UTF_8
                    )
                );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is not available",
                exception
            );
        }
    }
}
