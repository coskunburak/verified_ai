package com.verifiedai.problem.api.recognition;

import com.verifiedai.problem.application.recognition.ProblemRecognitionApplicationService;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/problem-sessions")
class ProblemRecognitionController {
    private final ProblemRecognitionApplicationService recognitionApplicationService;

    ProblemRecognitionController(ProblemRecognitionApplicationService recognitionApplicationService) {
        this.recognitionApplicationService = recognitionApplicationService;
    }

    @PostMapping("/{sessionId}/recognition")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ProblemRecognitionResponse requestRecognition(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID sessionId
    ) {
        return ProblemRecognitionResponse.from(recognitionApplicationService.requestRecognition(
            AuthenticatedUser.from(jwt).userId(),
            sessionId
        ));
    }

    @GetMapping("/{sessionId}/recognition")
    @ResponseStatus(HttpStatus.OK)
    ProblemRecognitionResponse getRecognition(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID sessionId
    ) {
        return ProblemRecognitionResponse.from(recognitionApplicationService.getRecognition(
            AuthenticatedUser.from(jwt).userId(),
            sessionId
        ));
    }
}
