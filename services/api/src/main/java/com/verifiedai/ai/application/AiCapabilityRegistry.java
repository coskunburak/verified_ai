package com.verifiedai.ai.application;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AiCapabilityRegistry {

    private final Map<AiCapability, AiCapabilityDescriptor> descriptors;

    public AiCapabilityRegistry(
        Collection<AiCapabilityDescriptor> descriptors
    ) {
        Objects.requireNonNull(
            descriptors,
            "descriptors are required"
        );

        EnumMap<AiCapability, AiCapabilityDescriptor> indexed =
            new EnumMap<>(AiCapability.class);

        for (AiCapabilityDescriptor descriptor : descriptors) {
            AiCapabilityDescriptor existing =
                indexed.put(
                    descriptor.capability(),
                    descriptor
                );

            if (existing != null) {
                throw new IllegalStateException(
                    "Duplicate AI capability descriptor: "
                        + descriptor.capability()
                );
            }
        }

        EnumSet<AiCapability> missing =
            EnumSet.allOf(AiCapability.class);

        missing.removeAll(indexed.keySet());

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Missing AI capability descriptors: " + missing
            );
        }

        this.descriptors = Map.copyOf(indexed);
    }

    public AiCapabilityDescriptor require(
        AiCapability capability
    ) {
        AiCapabilityDescriptor descriptor =
            descriptors.get(capability);

        if (descriptor == null) {
            throw new IllegalArgumentException(
                "Unknown AI capability: " + capability
            );
        }

        return descriptor;
    }

    public Map<AiCapability, AiCapabilityDescriptor> all() {
        return descriptors;
    }

    public static AiCapabilityRegistry defaults() {
        return new AiCapabilityRegistry(
            List.of(
                descriptor(
                    AiCapability.VISION_PARSE,
                    "problem",
                    "vision-route-v1",
                    "vision-recognition",
                    "recognition-evidence-v1",
                    AiCapabilityDescriptor.CostBucket.INGESTION
                ),
                descriptor(
                    AiCapability.PROBLEM_NORMALIZE,
                    "problem",
                    "problem-parser-route-v1",
                    "problem-parser",
                    "problem-parse-v1",
                    AiCapabilityDescriptor.CostBucket.INGESTION
                ),
                descriptor(
                    AiCapability.PROBLEM_CLASSIFY,
                    "problem",
                    "problem-classifier-route-v1",
                    "problem-classifier",
                    "problem-classification-v1",
                    AiCapabilityDescriptor.CostBucket.INGESTION
                ),

                future(
                    AiCapability.SOLVE,
                    "solving",
                    "solve-route-v1",
                    AiCapabilityDescriptor.CostBucket.SOLVING
                ),
                future(
                    AiCapability.ARBITRATE,
                    "verification",
                    "arbitrate-route-v1",
                    AiCapabilityDescriptor.CostBucket.VERIFICATION
                ),
                future(
                    AiCapability.EXPLAIN,
                    "solving",
                    "explain-route-v1",
                    AiCapabilityDescriptor.CostBucket.SOLVING
                ),
                future(
                    AiCapability.MISTAKE_CLASSIFY,
                    "mistake",
                    "mistake-classifier-route-v1",
                    AiCapabilityDescriptor.CostBucket.VERIFICATION
                ),
                future(
                    AiCapability.TUTOR,
                    "tutoring",
                    "tutor-route-v1",
                    AiCapabilityDescriptor.CostBucket.TUTORING
                ),
                future(
                    AiCapability.PRACTICE_GENERATE,
                    "practice",
                    "practice-generate-route-v1",
                    AiCapabilityDescriptor.CostBucket.GENERATION
                )
            )
        );
    }

    private static AiCapabilityDescriptor descriptor(
        AiCapability capability,
        String owner,
        String routePolicyVersion,
        String promptId,
        String schemaVersion,
        AiCapabilityDescriptor.CostBucket costBucket
    ) {
        return new AiCapabilityDescriptor(
            capability,
            owner,
            routePolicyVersion,
            promptId,
            schemaVersion,
            true,
            false,
            false,
            true,
            true,
            AiCapabilityDescriptor.PrivacyClass
                .STUDENT_PROBLEM_CONTENT,
            costBucket
        );
    }

    private static AiCapabilityDescriptor future(
        AiCapability capability,
        String owner,
        String routePolicyVersion,
        AiCapabilityDescriptor.CostBucket costBucket
    ) {
        return new AiCapabilityDescriptor(
            capability,
            owner,
            routePolicyVersion,
            null,
            null,
            true,
            false,
            false,
            true,
            true,
            AiCapabilityDescriptor.PrivacyClass
                .STUDENT_PROBLEM_CONTENT,
            costBucket
        );
    }
}
