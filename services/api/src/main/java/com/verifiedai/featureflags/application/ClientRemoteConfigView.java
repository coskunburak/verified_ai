package com.verifiedai.featureflags.application;

import java.time.Instant;
import java.util.List;

public record ClientRemoteConfigView(
    String schemaVersion,
    String etag,
    int maxAgeSeconds,
    List<ClientFlagEntry> flags,
    List<ClientExperimentEntry> experiments,
    List<String> killSwitches,
    Instant evaluatedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public ClientRemoteConfigView {
        flags = flags == null ? List.of() : List.copyOf(flags);
        experiments = experiments == null ? List.of() : List.copyOf(experiments);
        killSwitches = killSwitches == null ? List.of() : List.copyOf(killSwitches);
    }

    public record ClientFlagEntry(
        String flagKey,
        String category,
        String valueType,
        boolean enabled,
        boolean killSwitchActive,
        String reason
    ) {
    }

    public record ClientExperimentEntry(
        String experimentKey,
        int experimentVersion,
        String variantKey,
        String status,
        int bucketValue,
        String reason
    ) {
    }
}
