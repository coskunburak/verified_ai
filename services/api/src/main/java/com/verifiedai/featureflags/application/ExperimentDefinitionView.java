package com.verifiedai.featureflags.application;

import java.time.Instant;
import java.util.List;

public record ExperimentDefinitionView(
    String experimentKey,
    String linkedFlagKey,
    String status,
    boolean clientSafe,
    int rolloutBasisPoints,
    String bucketingSalt,
    String controlVariant,
    List<VariantWeightView> variants,
    String owner,
    String purpose,
    Instant plannedRemovalAt,
    int experimentVersion,
    long version,
    boolean trainingEligible,
    Instant createdAt,
    Instant updatedAt
) {
    public ExperimentDefinitionView {
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    public record VariantWeightView(String key, int weightBasisPoints) {
    }
}
