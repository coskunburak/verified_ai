package com.verifiedai.problem.api.parse;

import com.verifiedai.problem.application.parse.ProblemParseApplicationService;
import com.verifiedai.problem.application.parse.ProblemParseCorrectionApplicationService;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/problem-sessions")
class ProblemParseController {
    private final ProblemParseApplicationService parseApplicationService;
    private final ProblemParseCorrectionApplicationService correctionApplicationService;

    ProblemParseController(
        ProblemParseApplicationService parseApplicationService,
        ProblemParseCorrectionApplicationService correctionApplicationService
    ) {
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

    @PostMapping("/{sessionId}/calculus-parse-revisions")
    @ResponseStatus(HttpStatus.CREATED)
    CreateProblemParseCorrectionResponse createCalculusRevision(
        @AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
        @RequestHeader(name = "Idempotency-Key", required = false) String key,
        @RequestBody String payload
    ) {
        try {
            var root=com.verifiedai.sharedkernel.contracts.VersionedJsonContract.parse(payload);
            var names=new java.util.HashSet<String>(); root.fieldNames().forEachRemaining(names::add);
            if (!names.equals(java.util.Set.of("baseParseId","baseRevision","correctionReason","problem"))
                || !root.path("baseParseId").isTextual() || !root.path("baseRevision").isIntegralNumber()
                || !root.path("baseRevision").canConvertToInt() || !root.path("correctionReason").isTextual())
                throw new IllegalArgumentException();
            var command=new com.verifiedai.problem.application.parse.ProblemParseCorrectionCommand(
                AuthenticatedUser.from(jwt).userId(),sessionId,UUID.fromString(root.path("baseParseId").asText()),
                root.path("baseRevision").intValue(),key,root.path("correctionReason").asText(),root.path("problem").toString());
            return CreateProblemParseCorrectionResponse.from(correctionApplicationService.createCalculusCorrection(command));
        } catch (IllegalArgumentException e) {
            throw new com.verifiedai.sharedkernel.error.ApiProblemException(HttpStatus.BAD_REQUEST,
                com.verifiedai.sharedkernel.error.ApiErrorCode.PARSE_CORRECTION_SCHEMA_INVALID,"Typed calculus correction is invalid",true,"EDIT");
        }
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
}
