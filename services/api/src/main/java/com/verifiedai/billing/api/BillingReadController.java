package com.verifiedai.billing.api;

import com.verifiedai.billing.application.BillingOfferCatalogService;
import com.verifiedai.billing.application.EntitlementApplicationService;
import com.verifiedai.billing.application.TierResolutionPolicy;
import com.verifiedai.billing.domain.model.EntitlementSource;
import com.verifiedai.billing.domain.model.EntitlementStatus;
import com.verifiedai.billing.domain.model.EntitlementTier;
import com.verifiedai.billing.domain.model.PremiumCapability;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/billing", "/v1/billing"})
class BillingReadController {
    private final BillingOfferCatalogService catalog;
    private final EntitlementApplicationService entitlements;
    private final Clock clock;

    BillingReadController(BillingOfferCatalogService catalog, EntitlementApplicationService entitlements, Clock clock) {
        this.catalog = catalog;
        this.entitlements = entitlements;
        this.clock = clock;
    }

    @PostMapping("/interactions/{kind}")
    ResponseEntity<java.util.Map<String, Boolean>> interaction(@PathVariable String kind) {
        return privateResponse(java.util.Map.of("recorded", catalog.recordInteraction(kind)));
    }

    @GetMapping("/offers")
    ResponseEntity<BillingOfferCatalogService.Catalog> offers(@RequestParam(defaultValue = "en-US") String locale) {
        return privateResponse(catalog.offers(locale));
    }

    record EntitlementV2(String effectiveTier, EntitlementTier legacyTier, EntitlementStatus status,
        EntitlementSource source, Instant expiresAt, List<PremiumCapability> capabilities) {}

    @GetMapping("/entitlements")
    ResponseEntity<EntitlementV2> entitlement(@AuthenticationPrincipal Jwt jwt) {
        var current = entitlements.getCurrent(AuthenticatedUser.from(jwt).userId());
        boolean expired = current.expiresAt() != null && !current.expiresAt().isAfter(clock.instant());
        boolean active = current.status().grantsAccess() && !expired;
        var status = expired && current.status().grantsAccess() ? EntitlementStatus.EXPIRED : current.status();
        return privateResponse(new EntitlementV2(active ? TierResolutionPolicy.presentation(current.tier()) : "FREE",
            current.tier(), status, current.source(), current.expiresAt(), active ? current.capabilities() : List.of()));
    }

    private static <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);
    }
}
