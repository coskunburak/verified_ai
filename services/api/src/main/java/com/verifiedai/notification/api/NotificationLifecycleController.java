package com.verifiedai.notification.api;

import com.verifiedai.notification.api.dto.LifecycleSummaryResponse;
import com.verifiedai.notification.application.LifecycleMessagingService;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/notifications/lifecycle-summary", "/v1/notifications/lifecycle-summary"})
class NotificationLifecycleController {

    private final LifecycleMessagingService lifecycleService;

    NotificationLifecycleController(LifecycleMessagingService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @GetMapping
    ResponseEntity<LifecycleSummaryResponse> getLifecycleSummary(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "20") int limit
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        var summary = lifecycleService.getLifecycleSummaryForApi(userId, limit);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(LifecycleSummaryResponse.from(summary));
    }
}
