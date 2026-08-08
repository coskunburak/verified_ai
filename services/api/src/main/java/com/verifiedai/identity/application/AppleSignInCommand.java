package com.verifiedai.identity.application;

public record AppleSignInCommand(
    String identityToken,
    String authorizationCode,
    String nonce
) {
}
