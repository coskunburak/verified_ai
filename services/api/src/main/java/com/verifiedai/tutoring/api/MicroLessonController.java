package com.verifiedai.tutoring.api;

import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.tutoring.application.MicroLessonApplicationService;
import com.verifiedai.tutoring.domain.MicroLessonCard;
import com.verifiedai.tutoring.domain.MicroLessonCompletionRequest;
import com.verifiedai.tutoring.domain.MicroLessonCompletionResult;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = {"/v1/micro-lessons", "/api/v1/micro-lessons"})
public class MicroLessonController {

    private final MicroLessonApplicationService service;

    public MicroLessonController(MicroLessonApplicationService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @GetMapping("/{mistakeCode}")
    public ResponseEntity<MicroLessonCard> getMicroLessonCard(
            @AuthenticationPrincipal Object principal,
            @PathVariable String mistakeCode,
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) String triggerSource,
            @RequestParam(required = false) UUID sourceAttemptId,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage
    ) {
        UUID userId = resolveUserId(principal);
        String resolvedLocale = (locale != null && !locale.isBlank()) ? locale : resolveLocaleHeader(acceptLanguage);
        MicroLessonCard card = service.getTriggeredMicroLesson(
                userId,
                mistakeCode,
                resolvedLocale,
                triggerSource,
                sourceAttemptId
        );
        return ResponseEntity.ok(card);
    }

    @PostMapping("/{mistakeCode}/complete")
    public ResponseEntity<MicroLessonCompletionResult> completeMicroLesson(
            @AuthenticationPrincipal Object principal,
            @PathVariable String mistakeCode,
            @RequestBody MicroLessonCompletionRequest request
    ) {
        UUID userId = resolveUserId(principal);
        MicroLessonCompletionResult receipt = service.completeMicroLesson(userId, mistakeCode, request);
        return ResponseEntity.ok(receipt);
    }

    private static String resolveLocaleHeader(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return "tr-TR";
        }
        String lower = acceptLanguage.toLowerCase();
        if (lower.startsWith("en")) {
            return "en-US";
        }
        return "tr-TR";
    }

    private static UUID resolveUserId(Object principal) {
        if (principal instanceof AuthenticatedLearnerPrincipal alp) {
            return alp.learnerId();
        }
        if (principal instanceof VerifiedJwtPrincipal vjp) {
            return vjp.userId();
        }
        if (principal instanceof Jwt jwt && jwt.getSubject() != null) {
            return UUID.fromString(jwt.getSubject());
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            try {
                return UUID.fromString(auth.getName());
            } catch (IllegalArgumentException ignored) {
            }
        }
        throw new ApiProblemException(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.AUTH_CREDENTIALS_INVALID,
                "Authentication required",
                false,
                "SIGN_IN"
        );
    }
}
