package com.verifiedai.problem.api.canonicalization;

import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
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
class ProblemCanonicalizationController {
    private final CanonicalProblemApplicationService canonicalProblemApplicationService;

    ProblemCanonicalizationController(
        CanonicalProblemApplicationService canonicalProblemApplicationService
    ) {
        this.canonicalProblemApplicationService = canonicalProblemApplicationService;
    }

    @PostMapping("/{sessionId}/canonicalize")
    @ResponseStatus(HttpStatus.CREATED)
    CanonicalProblemResponse canonicalize(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID sessionId
    ) {
        return CanonicalProblemResponse.from(canonicalProblemApplicationService.canonicalize(
            AuthenticatedUser.from(jwt).userId(),
            sessionId
        ));
    }

    @GetMapping("/{sessionId}/canonical-problem")
    @ResponseStatus(HttpStatus.OK)
    CanonicalProblemResponse getCanonicalProblem(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID sessionId
    ) {
        return CanonicalProblemResponse.from(canonicalProblemApplicationService.getCanonicalProblem(
            AuthenticatedUser.from(jwt).userId(),
            sessionId
        ));
    }
}
