package com.verifiedai.billing.api;

import com.verifiedai.billing.application.EntitlementApplicationService;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/entitlements")
public class EntitlementController {
    private final EntitlementApplicationService entitlementApplicationService;

    EntitlementController(EntitlementApplicationService entitlementApplicationService) {
        this.entitlementApplicationService = entitlementApplicationService;
    }

    @GetMapping
    EntitlementResponse current(@AuthenticationPrincipal Jwt jwt) {
        return EntitlementResponse.from(entitlementApplicationService.getCurrent(AuthenticatedUser.from(jwt).userId()));
    }
}
