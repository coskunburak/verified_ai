package com.verifiedai.exam.application;

import com.verifiedai.exam.domain.ExamSession;
import com.verifiedai.exam.domain.ExamSessionStatus;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Enforces CNT-LOS-10 / INV-EXAM-003 zero-hint isolation during active timed mock exam sessions.
 */
@Component
public class ActiveExamNoHintGuard {

    public record HintGuardResult(
            String schemaVersion,
            UUID sessionId,
            ExamSessionStatus status,
            boolean hintAllowed,
            String policyCode,
            String requestedSurface,
            String neutralExplanationKey,
            Instant serverNow,
            int llmTokensUsed,
            boolean trainingEligible
    ) {
    }

    private final MeterRegistry meterRegistry;

    @Autowired
    public ActiveExamNoHintGuard(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new));
    }

    public ActiveExamNoHintGuard(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry == null ? new SimpleMeterRegistry() : meterRegistry;
    }

    public HintGuardResult evaluateForSession(ExamSession session, String requestedSurface, Instant serverNow) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(serverNow, "serverNow is required");

        String surface = (requestedSurface == null || requestedSurface.isBlank())
                ? "SOCRATIC_HINT_DRAWER"
                : requestedSurface.trim();

        if (session.status().isActive() && session.noHintPolicyEnforced()) {
            recordHintBlocked(surface, session.status().name());
            return new HintGuardResult(
                    "exam-no-hint-guard-response-v1",
                    session.id(),
                    session.status(),
                    false,
                    ApiErrorCode.EXAM_NO_HINT_MODE_ACTIVE.name(),
                    surface,
                    "exam.session.no_hint.explanation",
                    serverNow,
                    0,
                    false
            );
        }

        return new HintGuardResult(
                "exam-no-hint-guard-response-v1",
                session.id(),
                session.status(),
                true,
                "POST_EXAM_REVIEW_ALLOWED",
                surface,
                "exam.session.post_submit_review.allowed",
                serverNow,
                0,
                false
        );
    }

    public void assertHintSurfaceAllowed(ExamSession session, String requestedSurface) {
        Objects.requireNonNull(session, "session is required");
        if (session.status().isActive() && session.noHintPolicyEnforced()) {
            String surface = (requestedSurface == null || requestedSurface.isBlank())
                    ? "SOCRATIC_HINT_DRAWER"
                    : requestedSurface.trim();
            recordHintBlocked(surface, session.status().name());
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_NO_HINT_MODE_ACTIVE,
                    "Hints, Socratic coaching, and solution viewers are disabled while exam session "
                            + session.id() + " is active.",
                    false,
                    "Submit your mock exam session before opening Socratic hints or F28 analysis."
            );
        }
    }

    private void recordHintBlocked(String surface, String status) {
        Counter.builder("verifiedai.exam.session.hint_blocked")
                .tag("surface", surface)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }
}
