package com.verifiedai.exam.api;

import com.verifiedai.exam.application.PreExamReviewService;
import com.verifiedai.exam.application.PersonalCheatSheetService;
import com.verifiedai.exam.domain.PreExamEntityTags;
import com.verifiedai.exam.infrastructure.persistence.PreExamStudySessionRepository.Snapshot;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/v1/exams", "/api/v1/exams"})
public class PreExamController {
    private final PreExamReviewService reviews;
    private final PersonalCheatSheetService sheets;
    public PreExamController(PreExamReviewService reviews, PersonalCheatSheetService sheets) {
        this.reviews=reviews; this.sheets=sheets;
    }
    @GetMapping("/pre-exam-review/{examCode}")
    public ResponseEntity<String> review(@PathVariable String examCode,
            @RequestHeader(value="If-None-Match",required=false) String tag, Authentication auth) {
        Snapshot row=reviews.review(resolveLearnerId(auth),examCode);
        return conditional(row.reviewJson(),row.reviewEtag(),tag);
    }
    @GetMapping("/cheat-sheet/{examCode}")
    public ResponseEntity<String> sheet(@PathVariable String examCode,
            @RequestHeader(value="If-None-Match",required=false) String tag, Authentication auth) {
        Snapshot row=sheets.sheet(resolveLearnerId(auth),examCode);
        return conditional(row.sheetJson(),row.sheetEtag(),tag);
    }
    @PostMapping("/pre-exam-review/sessions/{sessionId}/export-events")
    public ResponseEntity<Void> exported(@PathVariable UUID sessionId,
            @RequestHeader(value="Idempotency-Key",required=false) String key, Authentication auth) {
        reviews.export(resolveLearnerId(auth),sessionId,key);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private ResponseEntity<String> conditional(String json,String digest,String header) {
        if (PreExamEntityTags.matches(header,digest)) return ResponseEntity.status(304)
                .cacheControl(CacheControl.noStore()).eTag(PreExamEntityTags.quote(digest)).build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .eTag(PreExamEntityTags.quote(digest)).body(json);
    }
    @ExceptionHandler(PreExamReviewService.Failure.class)
    public ResponseEntity<ProblemDetail> failure(PreExamReviewService.Failure failure) {
        var detail=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(failure.status),failure.code);
        detail.setProperty("code",failure.code); detail.setProperty("errorCode",failure.code);
        return ResponseEntity.status(failure.status).cacheControl(CacheControl.noStore()).body(detail);
    }
    private static UUID resolveLearnerId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiProblemException(
                    HttpStatus.UNAUTHORIZED,
                    ApiErrorCode.AUTH_TOKEN_EXPIRED,
                    "Authentication token is missing or invalid",
                    false,
                    "SIGN_IN"
            );
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedLearnerPrincipal learnerPrincipal) {
            return learnerPrincipal.learnerId();
        }
        if (principal instanceof Jwt jwt) {
            return VerifiedJwtPrincipal.from(jwt).learnerId();
        }
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        String name = authentication.getName();
        if (name != null && !name.isBlank()) {
            try {
                return UUID.fromString(name.trim());
            } catch (IllegalArgumentException ignored) {
                throw new AccessDeniedException("Authenticated principal does not carry a valid learner identifier");
            }
        }
        throw new AccessDeniedException("Authenticated principal is not permitted to access exam readiness endpoints");
    }
}
