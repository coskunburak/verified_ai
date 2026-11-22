package com.verifiedai.notification.api;

import com.verifiedai.notification.api.dto.NotificationDeviceListResponse;
import com.verifiedai.notification.api.dto.NotificationDeviceRegisterRequest;
import com.verifiedai.notification.api.dto.NotificationDeviceResponse;
import com.verifiedai.notification.application.NotificationDeviceService;
import com.verifiedai.notification.application.NotificationDeviceService.DeviceRegistrationOutcome;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/notifications/devices", "/v1/notifications/devices"})
class NotificationDeviceController {

    private final NotificationDeviceService deviceService;

    NotificationDeviceController(NotificationDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    ResponseEntity<NotificationDeviceResponse> registerDevice(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody NotificationDeviceRegisterRequest request
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        DeviceRegistrationOutcome outcome = deviceService.registerDevice(userId, request.toCommand());
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(NotificationDeviceResponse.from(outcome.device()));
    }

    @GetMapping
    ResponseEntity<NotificationDeviceListResponse> listActiveDevices(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        List<NotificationDeviceResponse> items = deviceService.listActiveDevices(userId).stream()
            .map(NotificationDeviceResponse::from)
            .toList();
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(NotificationDeviceListResponse.of(items));
    }

    @DeleteMapping("/{deviceId}")
    ResponseEntity<NotificationDeviceResponse> revokeDevice(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID deviceId
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        NotificationDeviceResponse revoked = NotificationDeviceResponse.from(deviceService.revokeDevice(userId, deviceId));
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(revoked);
    }
}
