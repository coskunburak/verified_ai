package com.verifiedai.billing.api;

import com.verifiedai.billing.application.AppleBillingApplicationService;
import com.verifiedai.billing.application.AppleBillingConfigurationResult;
import com.verifiedai.billing.application.AppleBillingProductResult;
import com.verifiedai.billing.application.ApplePurchaseEvidenceResult;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/billing/apple")
public class AppleBillingController {
    private final AppleBillingApplicationService appleBillingApplicationService;

    AppleBillingController(AppleBillingApplicationService appleBillingApplicationService) {
        this.appleBillingApplicationService = appleBillingApplicationService;
    }

    @GetMapping("/configuration")
    AppleBillingConfigurationResponse configuration(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        return AppleBillingConfigurationResponse.from(appleBillingApplicationService.configuration(userId));
    }

    @PostMapping("/transactions")
    ApplePurchaseEvidenceResponse submitTransaction(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody ApplePurchaseEvidenceRequest request
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        return ApplePurchaseEvidenceResponse.from(appleBillingApplicationService.submitTransaction(
            userId,
            request == null ? null : request.signedTransactionInfo(),
            idempotencyKey
        ));
    }
}

record AppleBillingConfigurationResponse(
    UUID appAccountToken,
    boolean purchaseAvailable,
    String environment,
    List<AppleBillingProductResponse> products
) {
    static AppleBillingConfigurationResponse from(AppleBillingConfigurationResult result) {
        return new AppleBillingConfigurationResponse(
            result.appAccountToken(),
            result.purchaseAvailable(),
            result.environment().name(),
            result.products().stream().map(AppleBillingProductResponse::from).toList()
        );
    }
}

record AppleBillingProductResponse(
    String internalPlanId,
    String appStoreProductId,
    String entitlementTier,
    String subscriptionGroupId,
    String billingPeriod
) {
    static AppleBillingProductResponse from(AppleBillingProductResult result) {
        return new AppleBillingProductResponse(
            result.internalPlanId(),
            result.appStoreProductId(),
            result.entitlementTier().name(),
            result.subscriptionGroupId(),
            result.billingPeriod()
        );
    }
}

record ApplePurchaseEvidenceRequest(String signedTransactionInfo, String source) {
}

record ApplePurchaseEvidenceResponse(
    String transactionId,
    String originalTransactionId,
    String subscriptionStatus,
    EntitlementResponse entitlement,
    boolean duplicate
) {
    static ApplePurchaseEvidenceResponse from(ApplePurchaseEvidenceResult result) {
        return new ApplePurchaseEvidenceResponse(
            result.transactionId(),
            result.originalTransactionId(),
            result.subscriptionStatus().name(),
            EntitlementResponse.from(result.entitlement()),
            result.duplicate()
        );
    }
}
