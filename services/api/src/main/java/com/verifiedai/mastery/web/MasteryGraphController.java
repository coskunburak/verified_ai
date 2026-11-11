package com.verifiedai.mastery.web;

import com.verifiedai.mastery.api.dto.SkillGraphProjectionResponse;
import com.verifiedai.mastery.application.KnowledgeGraphProjectionService;
import com.verifiedai.security.VerifiedJwtPrincipal;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = {"/v1/mastery", "/api/v1/mastery"})
@Validated
public class MasteryGraphController {

    private final KnowledgeGraphProjectionService projectionService;

    public MasteryGraphController(KnowledgeGraphProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @GetMapping("/graph")
    public ResponseEntity<SkillGraphProjectionResponse> getSkillGraph(
            @AuthenticationPrincipal VerifiedJwtPrincipal principal,
            @RequestParam("curriculum")
            @Pattern(regexp = "^[A-Z0-9_\\-]{3,64}$", message = "Invalid curriculum code format")
            String curriculumCode) {

        SkillGraphProjectionResponse response = projectionService.projectGraph(
                principal.tenantId(),
                principal.userId(),
                curriculumCode
        );
        return ResponseEntity.ok(response);
    }
}
