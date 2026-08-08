package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.EntitlementSource;
import com.verifiedai.billing.domain.model.EntitlementStatus;
import com.verifiedai.billing.domain.model.EntitlementTier;
import com.verifiedai.billing.domain.model.PremiumCapability;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EntitlementResult(
    UUID id,
    UUID userId,
    EntitlementTier tier,
    EntitlementSource source,
    EntitlementStatus status,
    Instant effectiveAt,
    Instant expiresAt,
    List<PremiumCapability> capabilities,
    Long version
) {
    public boolean allows(PremiumCapability capability) {
        return status.grantsAccess() && tier.includes(capability.minimumTier());
    }
}
