package com.verifiedai.featureflags.api.dto;

import com.verifiedai.featureflags.application.ExperimentDefinitionView;
import com.verifiedai.featureflags.application.ExperimentDefinitionView.VariantWeightView;
import com.verifiedai.featureflags.application.FeatureFlagDefinitionView;
import com.verifiedai.featureflags.application.FeatureFlagGovernanceService.FeatureFlagListView;
import java.time.Instant;
import java.util.List;

public record FeatureFlagListResponse(
    String schemaVersion,
    int flagCount,
    int experimentCount,
    List<FeatureFlagItem> flags,
    List<ExperimentItem> experiments,
    Instant generatedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public static FeatureFlagListResponse from(FeatureFlagListView view) {
        return new FeatureFlagListResponse(
            view.schemaVersion(),
            view.flagCount(),
            view.experimentCount(),
            view.flags().stream().map(FeatureFlagItem::from).toList(),
            view.experiments().stream().map(ExperimentItem::from).toList(),
            view.generatedAt(),
            view.llmTokensUsed(),
            view.trainingEligible()
        );
    }

    public record FeatureFlagItem(
        String flagKey,
        String category,
        String valueType,
        boolean clientSafe,
        boolean enabled,
        boolean killSwitchActive,
        int rolloutBasisPoints,
        String bucketingSalt,
        String defaultValueJson,
        String currentValueJson,
        String owner,
        String purpose,
        Instant plannedRemovalAt,
        long version,
        boolean trainingEligible,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static FeatureFlagItem from(FeatureFlagDefinitionView view) {
            return new FeatureFlagItem(
                view.flagKey(),
                view.category(),
                view.valueType(),
                view.clientSafe(),
                view.enabled(),
                view.killSwitchActive(),
                view.rolloutBasisPoints(),
                view.bucketingSalt(),
                view.defaultValueJson(),
                view.currentValueJson(),
                view.owner(),
                view.purpose(),
                view.plannedRemovalAt(),
                view.version(),
                view.trainingEligible(),
                view.createdAt(),
                view.updatedAt()
            );
        }
    }

    public record ExperimentItem(
        String experimentKey,
        String linkedFlagKey,
        String status,
        boolean clientSafe,
        int rolloutBasisPoints,
        String bucketingSalt,
        String controlVariant,
        List<VariantItem> variants,
        String owner,
        String purpose,
        Instant plannedRemovalAt,
        int experimentVersion,
        long version,
        boolean trainingEligible,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static ExperimentItem from(ExperimentDefinitionView view) {
            return new ExperimentItem(
                view.experimentKey(),
                view.linkedFlagKey(),
                view.status(),
                view.clientSafe(),
                view.rolloutBasisPoints(),
                view.bucketingSalt(),
                view.controlVariant(),
                view.variants().stream().map(VariantItem::from).toList(),
                view.owner(),
                view.purpose(),
                view.plannedRemovalAt(),
                view.experimentVersion(),
                view.version(),
                view.trainingEligible(),
                view.createdAt(),
                view.updatedAt()
            );
        }
    }

    public record VariantItem(String key, int weightBasisPoints) {
        public static VariantItem from(VariantWeightView view) {
            return new VariantItem(view.key(), view.weightBasisPoints());
        }
    }
}
