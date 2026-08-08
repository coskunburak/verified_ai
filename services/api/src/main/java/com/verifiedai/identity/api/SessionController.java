package com.verifiedai.identity.api;

import com.verifiedai.identity.application.IdentityApplicationService;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class SessionController {
    private final IdentityApplicationService identityApplicationService;

    SessionController(IdentityApplicationService identityApplicationService) {
        this.identityApplicationService = identityApplicationService;
    }

    @GetMapping("/session")
    CurrentSessionResponse currentSession(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return CurrentSessionResponse.from(identityApplicationService.currentSession(user.userId(), user.sessionId()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.OK)
    Map<String, String> logout(@AuthenticationPrincipal Jwt jwt) {
        identityApplicationService.logout(AuthenticatedUser.from(jwt).sessionId());
        return Map.of("status", "loggedOut");
    }
}
