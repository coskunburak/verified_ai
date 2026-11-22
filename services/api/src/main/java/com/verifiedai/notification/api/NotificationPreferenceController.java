package com.verifiedai.notification.api;

import com.verifiedai.notification.api.dto.NotificationPreferencesPatchRequest;
import com.verifiedai.notification.api.dto.NotificationPreferencesResponse;
import com.verifiedai.notification.application.NotificationPreferenceService;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/notifications", "/v1/notifications"})
class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    NotificationPreferenceController(NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping("/preferences")
    ResponseEntity<NotificationPreferencesResponse> getPreferences(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        return privateResponse(NotificationPreferencesResponse.from(preferenceService.getPreferencesForApi(userId)));
    }

    @PatchMapping("/preferences")
    ResponseEntity<NotificationPreferencesResponse> updatePreferences(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody NotificationPreferencesPatchRequest request
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        var updated = preferenceService.updatePreferences(userId, request.toCommand());
        return privateResponse(NotificationPreferencesResponse.from(updated));
    }

    private static <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);
    }
}
