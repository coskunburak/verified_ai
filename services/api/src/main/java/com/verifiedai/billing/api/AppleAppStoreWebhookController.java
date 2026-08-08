package com.verifiedai.billing.api;

import com.verifiedai.billing.application.AppleBillingApplicationService;
import com.verifiedai.billing.application.AppleNotificationIngestionResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/apple/app-store")
public class AppleAppStoreWebhookController {
    private final AppleBillingApplicationService appleBillingApplicationService;

    AppleAppStoreWebhookController(AppleBillingApplicationService appleBillingApplicationService) {
        this.appleBillingApplicationService = appleBillingApplicationService;
    }

    @PostMapping
    AppleNotificationIngestionResponse ingest(@RequestBody AppleAppStoreNotificationRequest request) {
        return AppleNotificationIngestionResponse.from(appleBillingApplicationService.ingestNotification(
            request == null ? null : request.signedPayload()
        ));
    }
}

record AppleAppStoreNotificationRequest(String signedPayload) {
}

record AppleNotificationIngestionResponse(
    String notificationUuid,
    String processingStatus,
    String subscriptionStatus,
    EntitlementResponse entitlement
) {
    static AppleNotificationIngestionResponse from(AppleNotificationIngestionResult result) {
        return new AppleNotificationIngestionResponse(
            result.notificationUuid(),
            result.processingStatus(),
            result.subscriptionStatus() == null ? null : result.subscriptionStatus().name(),
            result.entitlement() == null ? null : EntitlementResponse.from(result.entitlement())
        );
    }
}
