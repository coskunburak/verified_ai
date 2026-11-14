package com.verifiedai.studyplan.quality;

import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.studyplan.quality.QualityGateModels.StudyPlanQualityGateReport;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * Admin-only (ROLE_ADMIN) Phase 08 Exit Gate telemetry and recommendation quality audit endpoint
 * ({@code GET /v1/admin/study-plan/quality-gate} and {@code /api/v1/admin/study-plan/quality-gate}).
 */
@RestController
@RequestMapping({"/v1/admin/study-plan/quality-gate", "/api/v1/admin/study-plan/quality-gate"})
public class AdminQualityGateController {

    private final PlannerQualityGateService qualityGateService;

    public AdminQualityGateController(PlannerQualityGateService qualityGateService) {
        this.qualityGateService = Objects.requireNonNull(qualityGateService, "qualityGateService cannot be null");
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudyPlanQualityGateReport> getQualityGateReport(
            @RequestParam(name = "persistSnapshot", required = false, defaultValue = "false") boolean persistSnapshot,
            Authentication authentication
    ) {
        UUID actorId = resolveActorId(authentication);
        StudyPlanQualityGateReport report = qualityGateService.evaluateQualityGate(actorId, persistSnapshot);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(report);
    }

    private static UUID resolveActorId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedLearnerPrincipal learnerPrincipal) {
            return learnerPrincipal.learnerId();
        }
        if (principal instanceof Jwt jwt) {
            return VerifiedJwtPrincipal.from(jwt).learnerId();
        }
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        try {
            return authentication.getName() != null ? UUID.fromString(authentication.getName()) : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
