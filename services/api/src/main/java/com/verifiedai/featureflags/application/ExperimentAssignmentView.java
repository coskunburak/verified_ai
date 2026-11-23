package com.verifiedai.featureflags.application;

import java.time.Instant;

public record ExperimentAssignmentView(
    String experimentKey,
    int experimentVersion,
    String variantKey,
    String status,
    int bucketValue,
    String reason,
    Instant assignedAt
) {
}
