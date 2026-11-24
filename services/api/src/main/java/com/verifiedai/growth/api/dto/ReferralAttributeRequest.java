package com.verifiedai.growth.api.dto;

public record ReferralAttributeRequest(
    String referralCode,
    String attributionSource,
    String deviceFingerprintHash,
    Boolean trainingEligible
) {
}
