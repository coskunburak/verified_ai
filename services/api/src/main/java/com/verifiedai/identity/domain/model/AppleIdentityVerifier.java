package com.verifiedai.identity.domain.model;

public interface AppleIdentityVerifier {
    VerifiedAppleIdentity verify(String identityToken, String rawNonce);
}
