package com.verifiedai.mistake.api;

import com.verifiedai.mistake.LearnerMistakeDnaService;
import com.verifiedai.billing.application.EntitlementApplicationService;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mistakes")
public class MistakeDnaController {
    private final LearnerMistakeDnaService service;
    private final EntitlementApplicationService entitlements;
    public MistakeDnaController(LearnerMistakeDnaService service,EntitlementApplicationService entitlements) { this.service=service; this.entitlements=entitlements; }
    @GetMapping("/dna")
    public LearnerMistakeDnaService.View read(@AuthenticationPrincipal Jwt jwt) {
        UUID owner=AuthenticatedUser.from(jwt).userId();
        boolean full = false;
        try {
            full = !"FREE".equals(entitlements.currentAiTier(owner));
        } catch (com.verifiedai.sharedkernel.error.ApiProblemException ex) {
            if (ex.code() != com.verifiedai.sharedkernel.error.ApiErrorCode.ENTITLEMENT_SYNC_FAILED) {
                throw ex;
            }
        } catch (org.springframework.dao.DataAccessException ex) {
            // Preserve owner's read-only top-2 Mistake DNA summary during billing verification outage.
        }
        return service.read(owner,full);
    }
    @GetMapping("/{eventId}/effective-state")
    public LearnerMistakeDnaService.DiagnosisView effective(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID eventId) {
        return service.diagnosis(AuthenticatedUser.from(jwt).userId(),eventId);
    }
    @PostMapping("/{eventId}/dispute")
    public LearnerMistakeDnaService.Result dispute(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID eventId,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody LearnerMistakeDnaService.DisputeInput input) {
        return service.dispute(AuthenticatedUser.from(jwt).userId(),eventId,key,input);
    }
}
