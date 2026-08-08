package com.verifiedai.identity.api;

import jakarta.validation.constraints.NotBlank;

public record AppleSignInRequest(
    @NotBlank String identityToken,
    String authorizationCode,
    @NotBlank String nonce
) {
}
