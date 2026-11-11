package com.verifiedai.analytics;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping(path = {"/v1/admin/calibration", "/api/v1/admin/calibration"})
public class MasteryCalibrationAdminController {

    private final MasteryCalibrationEvaluationService evaluationService;

    public MasteryCalibrationAdminController(MasteryCalibrationEvaluationService evaluationService) {
        this.evaluationService = Objects.requireNonNull(evaluationService, "evaluationService cannot be null");
    }

    @GetMapping("/mastery-summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MasteryCalibrationSummaryDto> getMasterySummary(
        @RequestParam(name = "canonicalSkillCode", required = false) String canonicalSkillCode,
        @RequestParam(name = "windowDays", defaultValue = "30") int windowDays
    ) {
        MasteryCalibrationSummaryDto summary = evaluationService.getCalibrationSummary(canonicalSkillCode, windowDays);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().mustRevalidate())
            .body(summary);
    }
}
