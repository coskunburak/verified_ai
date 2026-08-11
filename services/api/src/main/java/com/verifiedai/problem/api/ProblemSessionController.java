package com.verifiedai.problem.api;

import com.verifiedai.problem.application.ProblemSessionDetailApplicationService;
import com.verifiedai.problem.application.ProblemSessionHistoryApplicationService;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/problem-sessions")
class ProblemSessionController {
    private final ProblemSessionHistoryApplicationService historyApplicationService;
    private final ProblemSessionDetailApplicationService detailApplicationService;

    ProblemSessionController(
        ProblemSessionHistoryApplicationService historyApplicationService,
        ProblemSessionDetailApplicationService detailApplicationService
    ) {
        this.historyApplicationService = historyApplicationService;
        this.detailApplicationService = detailApplicationService;
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    ProblemSessionHistoryResponse history(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(name = "limit", required = false) Integer limit,
        @RequestParam(name = "cursor", required = false) String cursor
    ) {
        return ProblemSessionHistoryResponse.from(historyApplicationService.history(
            AuthenticatedUser.from(jwt).userId(),
            limit,
            cursor
        ));
    }

    @GetMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.OK)
    ProblemSessionDetailResponse detail(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID sessionId
    ) {
        return ProblemSessionDetailResponse.from(detailApplicationService.detail(
            AuthenticatedUser.from(jwt).userId(),
            sessionId
        ));
    }
}
