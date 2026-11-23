package com.verifiedai.featureflags.api.dto;

import com.verifiedai.featureflags.application.ClientRemoteConfigView;
import com.verifiedai.featureflags.application.ClientRemoteConfigView.ClientExperimentEntry;
import com.verifiedai.featureflags.application.ClientRemoteConfigView.ClientFlagEntry;
import java.time.Instant;
import java.util.List;

public record RemoteConfigResponse(
    String schemaVersion,
    String etag,
    int maxAgeSeconds,
    List<RemoteFlagEntry> flags,
    List<RemoteExperimentEntry> experiments,
    List<String> killSwitches,
    Instant evaluatedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public static RemoteConfigResponse from(ClientRemoteConfigView view) {
        return new RemoteConfigResponse(
            view.schemaVersion(),
            view.etag(),
            view.maxAgeSeconds(),
            view.flags().stream().map(RemoteFlagEntry::from).toList(),
            view.experiments().stream().map(RemoteExperimentEntry::from).toList(),
            view.killSwitches(),
            view.evaluatedAt(),
            view.llmTokensUsed(),
            view.trainingEligible()
        );
    }

    public record RemoteFlagEntry(
        String flagKey,
        String category,
        String valueType,
        boolean enabled,
        boolean killSwitchActive,
        String reason
    ) {
        public static RemoteFlagEntry from(ClientFlagEntry entry) {
            return new RemoteFlagEntry(
                entry.flagKey(),
                entry.category(),
                entry.valueType(),
                entry.enabled(),
                entry.killSwitchActive(),
                entry.reason()
            );
        }
    }

    public record RemoteExperimentEntry(
        String experimentKey,
        int experimentVersion,
        String variantKey,
        String status,
        int bucketValue,
        String reason
    ) {
        public static RemoteExperimentEntry from(ClientExperimentEntry entry) {
            return new RemoteExperimentEntry(
                entry.experimentKey(),
                entry.experimentVersion(),
                entry.variantKey(),
                entry.status(),
                entry.bucketValue(),
                entry.reason()
            );
        }
    }
}
