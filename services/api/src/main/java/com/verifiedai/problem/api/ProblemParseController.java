package com.verifiedai.problem.api;

import com.verifiedai.problem.application.CanonicalProblemApplicationService;
import com.verifiedai.problem.application.ProblemParseApplicationService;
import com.verifiedai.problem.application.ProblemParseCorrectionApplicationService;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/problem-sessions")
class ProblemParseController {
    private final CanonicalProblemApplicationService canonicalProblemApplicationService;
    private final ProblemParseApplicationService parseApplicationService;
    private final ProblemParseCorrectionApplicationService correctionApplicationService;

    ProblemParseController(
        CanonicalProblemApplicationService canonicalProblemApplicationService,
        ProblemParseApplicationService parseApplicationService,
        ProblemParseCorrectionApplicationService correctionApplicationService
    ) {
        this.canonicalProblemApplicationService = canonicalProblemApplicationService;
        this.parseApplicationService = parseApplicationService;
        this.correctionApplicationService = correctionApplicationService;
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

    @GetMapping("/{sessionId}/parse-review")
    @ResponseStatus(HttpStatus.OK)
    ProblemParseReviewResponse getParseReview(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID sessionId
    ) {
        return ProblemParseReviewResponse.from(correctionApplicationService.getParseReview(
            AuthenticatedUser.from(jwt).userId(),
            sessionId
        ));
    }

    @PostMapping("/{sessionId}/parse-revisions")
    @ResponseStatus(HttpStatus.CREATED)
    CreateProblemParseCorrectionResponse createParseCorrection(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID sessionId,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody CreateProblemParseCorrectionRequest request
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        return CreateProblemParseCorrectionResponse.from(correctionApplicationService.createCorrection(
            request.toCommand(userId, sessionId, idempotencyKey)
        ));
    }

    @GetMapping("/{sessionId}/parse-revisions")
    @ResponseStatus(HttpStatus.OK)
    ProblemParseRevisionHistoryResponse getParseRevisionHistory(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID sessionId
    ) {
        return ProblemParseRevisionHistoryResponse.from(
            sessionId,
            correctionApplicationService.getRevisionHistory(
                AuthenticatedUser.from(jwt).userId(),
                sessionId
            )
        );
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
