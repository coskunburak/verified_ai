package com.verifiedai.identity.api;

import com.verifiedai.identity.application.AppleSignInCommand;
import com.verifiedai.identity.application.EmailSignInCommand;
import com.verifiedai.identity.application.EmailSignUpCommand;
import com.verifiedai.identity.application.IdentityApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class IdentityController {
    private final IdentityApplicationService identityApplicationService;

    IdentityController(IdentityApplicationService identityApplicationService) {
        this.identityApplicationService = identityApplicationService;
    }

    @PostMapping("/apple")
    @ResponseStatus(HttpStatus.OK)
    AuthSessionResponse signInWithApple(@Valid @RequestBody AppleSignInRequest request) {
        return AuthSessionResponse.from(identityApplicationService.signInWithApple(
            new AppleSignInCommand(request.identityToken(), request.authorizationCode(), request.nonce())
        ));
    }

    @PostMapping("/email/sign-up")
    @ResponseStatus(HttpStatus.OK)
    AuthSessionResponse signUpWithEmail(@Valid @RequestBody EmailAuthRequest request) {
        return AuthSessionResponse.from(identityApplicationService.signUpWithEmail(
            new EmailSignUpCommand(request.email(), request.password())
        ));
    }

    @PostMapping("/email/sign-in")
    @ResponseStatus(HttpStatus.OK)
    AuthSessionResponse signInWithEmail(@Valid @RequestBody EmailAuthRequest request) {
        return AuthSessionResponse.from(identityApplicationService.signInWithEmail(
            new EmailSignInCommand(request.email(), request.password())
        ));
    }

    @PostMapping("/guest")
    @ResponseStatus(HttpStatus.OK)
    AuthSessionResponse continueAsGuest() {
        return AuthSessionResponse.from(identityApplicationService.continueAsGuest());
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    AuthSessionResponse refresh(@Valid @RequestBody RefreshSessionRequest request) {
        return AuthSessionResponse.from(identityApplicationService.refresh(request.refreshToken()));
    }
}
