package com.verifiedai.mastery.web;

import com.verifiedai.mastery.api.dto.TwinSubmissionRequest;
import com.verifiedai.mastery.api.dto.VerifiedTwinChallengeResponse;
import com.verifiedai.mastery.application.CounterfactualTwinChallengeService;
import com.verifiedai.mastery.application.TwinSubmissionCommand;
import com.verifiedai.security.VerifiedJwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(path = {"/v1", "/api/v1"})
public class CounterfactualTwinController {

    private final CounterfactualTwinChallengeService twinService;

    public CounterfactualTwinController(CounterfactualTwinChallengeService twinService) {
        this.twinService = twinService;
    }

    @PostMapping("/attempts/{attemptId}/twins")
    public ResponseEntity<VerifiedTwinChallengeResponse> createTwinChallenge(
            @AuthenticationPrincipal VerifiedJwtPrincipal principal,
            @PathVariable("attemptId") UUID attemptId) {

        CounterfactualTwinChallengeService.TwinGenerationOutcome outcome =
                twinService.generateTwinWithOutcome(principal.tenantId(), principal.userId(), attemptId);

        HttpStatus status = outcome.createdNew() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(outcome.response());
    }

    @PostMapping("/twins/{twinId}/submit")
    public ResponseEntity<VerifiedTwinChallengeResponse> submitOrRevealTwin(
            @AuthenticationPrincipal VerifiedJwtPrincipal principal,
            @PathVariable("twinId") UUID twinId,
            @Valid @RequestBody TwinSubmissionRequest request) {

        TwinSubmissionCommand command = new TwinSubmissionCommand(
                request.action(),
                request.submittedAnswerLatex(),
                Boolean.TRUE.equals(request.forceEarlyForfeit())
        );

        VerifiedTwinChallengeResponse response = twinService.submitOrRevealTwin(
                principal.tenantId(),
                principal.userId(),
                twinId,
                command
        );
        return ResponseEntity.ok(response);
    }
}
