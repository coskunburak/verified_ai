package com.verifiedai.identity.api;

import jakarta.validation.constraints.NotBlank;

public record RefreshSessionRequest(@NotBlank String refreshToken) {
}
