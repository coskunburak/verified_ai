package com.verifiedai.identity.domain.model;

public record VerifiedAppleIdentity(String providerSubject) {
    public VerifiedAppleIdentity {
        if (providerSubject == null || providerSubject.isBlank()) {
            throw new IllegalArgumentException("providerSubject is required");
        }
    }
}
