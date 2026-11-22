package com.verifiedai.billing.api;

import com.verifiedai.billing.application.BillingOfferCatalogService;
import com.verifiedai.billing.application.EntitlementApplicationService;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
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

    BillingReadController(BillingOfferCatalogService catalog, EntitlementApplicationService entitlements) {
        this.catalog = catalog;
        this.entitlements = entitlements;
    }

    @PostMapping("/interactions/{kind}")
    ResponseEntity<java.util.Map<String, Boolean>> interaction(@PathVariable String kind) {
        return privateResponse(java.util.Map.of("recorded", catalog.recordInteraction(kind)));
    }

    @GetMapping("/offers")
    ResponseEntity<BillingOfferCatalogService.Catalog> offers(@RequestParam(defaultValue = "en-US") String locale) {
        return privateResponse(catalog.offers(locale));
    }

    @GetMapping("/entitlements")
    ResponseEntity<com.verifiedai.billing.application.EffectiveEntitlementAccess> entitlement(@AuthenticationPrincipal Jwt jwt) {
        return privateResponse(entitlements.access(AuthenticatedUser.from(jwt).userId()));
    }

    private static <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);
    }
}
