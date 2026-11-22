package com.verifiedai.notification.api;

import com.verifiedai.notification.api.dto.NotificationDeliveryCancelResponse;
import com.verifiedai.notification.api.dto.NotificationDeliveryListResponse;
import com.verifiedai.notification.application.NotificationSchedulingService;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/notifications/deliveries", "/v1/notifications/deliveries"})
class NotificationDeliveryController {

    private final NotificationSchedulingService schedulingService;

    NotificationDeliveryController(NotificationSchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @GetMapping
    ResponseEntity<NotificationDeliveryListResponse> listDeliveries(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "20") int limit
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        var deliveries = schedulingService.listDeliveriesForApi(userId, limit);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(NotificationDeliveryListResponse.from(deliveries));
    }

    @PostMapping("/{deliveryId}/cancel")
    ResponseEntity<NotificationDeliveryCancelResponse> cancelDelivery(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID deliveryId
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        var cancelled = schedulingService.cancelForApi(userId, deliveryId);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(NotificationDeliveryCancelResponse.from(cancelled));
    }
}
