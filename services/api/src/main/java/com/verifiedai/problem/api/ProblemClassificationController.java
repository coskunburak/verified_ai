package com.verifiedai.problem.api;

import com.verifiedai.problem.application.ProblemClassificationApplicationService;
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
class ProblemClassificationController {

    private final ProblemClassificationApplicationService
        classificationApplicationService;

    ProblemClassificationController(
        ProblemClassificationApplicationService
            classificationApplicationService
    ) {
        this.classificationApplicationService =
            classificationApplicationService;
    }

    @PostMapping("/{sessionId}/classification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ProblemClassificationResponse requestClassification(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID sessionId
    ) {
        return ProblemClassificationResponse.from(
            classificationApplicationService
                .requestClassification(
                    AuthenticatedUser.from(jwt).userId(),
                    sessionId
                )
        );
    }

    @GetMapping("/{sessionId}/classification")
    @ResponseStatus(HttpStatus.OK)
    ProblemClassificationResponse getClassification(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID sessionId
    ) {
        return ProblemClassificationResponse.from(
            classificationApplicationService
                .getClassification(
                    AuthenticatedUser.from(jwt).userId(),
                    sessionId
                )
        );
    }
}
