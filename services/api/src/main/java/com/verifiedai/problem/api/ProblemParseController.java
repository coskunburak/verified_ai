package com.verifiedai.problem.api;

import com.verifiedai.problem.application.ProblemParseApplicationService;
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
class ProblemParseController {
    private final ProblemParseApplicationService parseApplicationService;

    ProblemParseController(ProblemParseApplicationService parseApplicationService) {
        this.parseApplicationService = parseApplicationService;
    }

    @PostMapping("/{sessionId}/parse")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ProblemParseResponse requestParse(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID sessionId
    ) {
        return ProblemParseResponse.from(parseApplicationService.requestParse(
            AuthenticatedUser.from(jwt).userId(),
            sessionId
        ));
    }

    @GetMapping("/{sessionId}/parse")
    @ResponseStatus(HttpStatus.OK)
    ProblemParseResponse getParse(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID sessionId
    ) {
        return ProblemParseResponse.from(parseApplicationService.getParse(
            AuthenticatedUser.from(jwt).userId(),
            sessionId
        ));
    }
}
