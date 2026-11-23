package com.verifiedai.featureflags.application;

import java.util.UUID;

/**
 * Single public Spring Modulith SPI exposed by com.verifiedai.featureflags to peer modules.
 * Peer modules must depend ONLY on com.verifiedai.featureflags.application.. (INV-FF-007).
 */
public interface FeatureFlagEvaluationPort {

    boolean evaluateBoolean(String flagKey, UUID userId, boolean fallbackValue);

    FlagEvaluationResult evaluateFlag(String flagKey, UUID userId);

    String evaluateConfigJson(String flagKey, UUID userId, String fallbackJson);

    ExperimentAssignmentView assignExperiment(String experimentKey, UUID userId);

    ClientRemoteConfigView evaluateClientBundle(UUID userId);
}
