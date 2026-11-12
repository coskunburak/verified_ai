package com.verifiedai.studyplan.practice;

import com.verifiedai.studyplan.practice.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class PracticeSessionService {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeSessionItemRepository itemRepository;
    private final AdaptivePracticeQuestionSelector selector;
    private final SymPyTwinVerifierClient twinVerifierClient;

    public PracticeSessionService(
            PracticeSessionRepository sessionRepository,
            PracticeSessionItemRepository itemRepository,
            AdaptivePracticeQuestionSelector selector,
            SymPyTwinVerifierClient twinVerifierClient
    ) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository cannot be null");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository cannot be null");
        this.selector = Objects.requireNonNull(selector, "selector cannot be null");
        this.twinVerifierClient = Objects.requireNonNull(twinVerifierClient, "twinVerifierClient cannot be null");
    }

    @Transactional
    public PracticeSessionResponse startPracticeSession(UUID userId, StartPracticeSessionRequest request) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        String skillId = request.targetSkillId();
        PracticeSessionMode mode = request.getEffectiveSessionMode();
        BigDecimal defaultMastery = new BigDecimal("0.5500");

        // Determine active trap family for skill if known
        String activeTrap = resolveActiveTrapFamilyForSkill(skillId);

        PracticeSessionEntity session = new PracticeSessionEntity(
                UUID.randomUUID(),
                userId,
                request.recommendationId(),
                mode,
                skillId,
                activeTrap,
                defaultMastery,
                4, // standard 4 items for 1-2-1
                Instant.now()
        );

        selector.allocateSessionItems(
                session,
                userId,
                skillId,
                activeTrap,
                defaultMastery,
                0, // initial streak
                new BigDecimal("0.6000") // exam net ROI
        );

        PracticeSessionEntity saved = sessionRepository.save(session);
        return PracticeSessionResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public PracticeSessionResponse getPracticeSession(UUID sessionId, UUID authenticatedUserId) {
        PracticeSessionEntity session = sessionRepository.findByIdAndUserId(sessionId, authenticatedUserId)
                .orElseThrow(() -> new PracticeSessionNotFoundException(sessionId));
        return PracticeSessionResponse.fromEntity(session);
    }

    @Transactional
    public PracticeItemSubmissionResponse submitPracticeItemAnswer(
            UUID sessionId,
            UUID itemId,
            UUID authenticatedUserId,
            SubmitPracticeItemRequest request
    ) {
        PracticeSessionEntity session = sessionRepository.findByIdAndUserId(sessionId, authenticatedUserId)
                .orElseThrow(() -> new PracticeSessionNotFoundException(sessionId));

        PracticeSessionItemEntity item = session.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new PracticeSessionNotFoundException(sessionId));

        Instant now = Instant.now();

        // 1. Check Independent Verification Lock State (DOC-84 §1, INV-PRAC-004)
        int remainingLockSeconds = 0;
        boolean lockActive = false;
        long elapsedSeconds = 0;

        if (item.isTwinVerification()) {
            if (item.getUnlockedAt() != null) {
                elapsedSeconds = Duration.between(item.getUnlockedAt(), now).getSeconds();
                remainingLockSeconds = (int) Math.max(0, item.getIndependentLockSeconds() - elapsedSeconds);
                lockActive = (remainingLockSeconds > 0);
            } else {
                item.setUnlockedAt(now);
                remainingLockSeconds = item.getIndependentLockSeconds();
                lockActive = true;
            }
        }

        // 2. Handle Hint Unlock Request
        if (request != null && request.isUnlockHintRequested()) {
            if (lockActive) {
                return new PracticeItemSubmissionResponse(
                        item.getItemStatus(),
                        false,
                        false,
                        null,
                        null,
                        null,
                        true,
                        remainingLockSeconds,
                        session.isTrapClosureAchieved(),
                        PracticeSessionResponse.fromEntity(session)
                );
            }
            item.incrementHintsUnlocked();
        }

        // If already submitted, return idempotent response
        if (item.getItemStatus() != PracticeItemStatus.PENDING) {
            return new PracticeItemSubmissionResponse(
                    item.getItemStatus(),
                    item.getItemStatus() == PracticeItemStatus.SUBMITTED_CORRECT,
                    item.isDetectedTrapTriggered(),
                    null,
                    null,
                    null,
                    lockActive,
                    remainingLockSeconds,
                    session.isTrapClosureAchieved(),
                    PracticeSessionResponse.fromEntity(session)
            );
        }

        String submittedAnswer = request != null ? request.submittedAnswerLatex() : null;

        // 3. Rapid Guess Guard (INV-PRAC-004)
        if (item.isTwinVerification() && submittedAnswer != null && !submittedAnswer.isBlank()) {
            long clientElapsed = request != null ? request.getEffectiveElapsedSeconds() : 0;
            long effectiveElapsed = Math.min(elapsedSeconds, clientElapsed > 0 ? clientElapsed : elapsedSeconds);

            if (effectiveElapsed < 8) {
                // Check if answer is incorrect before raising guard
                SymPyTwinVerifierClient.EvaluationResult tentative = twinVerifierClient.evaluateSubmission(
                        item.getTrapFamily() != null ? item.getTrapFamily() : "GENERIC",
                        item.getPromptLatex(),
                        item.getCanonicalSolutionExpr(),
                        item.getTrapDistractorExpr(),
                        submittedAnswer
                );
                if (!tentative.isCorrect()) {
                    throw new RapidGuessGuardException("RAPID_GUESS_GUARD_TRIGGERED: Take a moment to reason algebraically before guessing.");
                }
            }
        }

        // 4. Evaluate Submission with SymPy Verification
        long evalStart = System.currentTimeMillis();
        SymPyTwinVerifierClient.EvaluationResult evalResult = twinVerifierClient.evaluateSubmission(
                item.getTrapFamily() != null ? item.getTrapFamily() : "GENERIC",
                item.getPromptLatex(),
                item.getCanonicalSolutionExpr(),
                item.getTrapDistractorExpr(),
                submittedAnswer
        );
        long evalDuration = System.currentTimeMillis() - evalStart;

        item.setSubmittedAnswerLatex(submittedAnswer);
        item.setSubmittedAt(now);
        item.setVerificationDurationMs(evalDuration);
        item.incrementAttemptCount();

        if (evalResult.isCorrect()) {
            item.setItemStatus(PracticeItemStatus.SUBMITTED_CORRECT);
            session.setVerifiedCorrectItems(session.getVerifiedCorrectItems() + 1);
            session.setCompletedItems(session.getCompletedItems() + 1);

            // Trap closure requires verified correct submission with zero hints unlocked (INV-PRAC-004)
            if (item.isTwinVerification() && item.getHintsUnlockedCount() == 0) {
                session.setTrapClosureAchieved(true);
            }
        } else {
            item.setItemStatus(PracticeItemStatus.SUBMITTED_INCORRECT);
            session.setCompletedItems(session.getCompletedItems() + 1);
            if (evalResult.trapTriggered()) {
                item.setDetectedTrapTriggered(true);
            }
        }

        if (session.getCompletedItems() >= session.getTotalItems()) {
            session.setStatus(PracticeSessionStatus.COMPLETED);
            session.setCompletedAt(now);
        }

        sessionRepository.save(session);

        return new PracticeItemSubmissionResponse(
                item.getItemStatus(),
                evalResult.isCorrect(),
                evalResult.trapTriggered(),
                evalResult.trapDiagnosticKey(),
                evalResult.trapDiagnosticMessageTr(),
                evalResult.trapDiagnosticMessageEn(),
                lockActive,
                remainingLockSeconds,
                session.isTrapClosureAchieved(),
                PracticeSessionResponse.fromEntity(session)
        );
    }

    @Transactional
    public PracticeSessionResponse abandonPracticeSession(
            UUID sessionId,
            UUID authenticatedUserId,
            PracticeSessionAbandonRequest request
    ) {
        PracticeSessionEntity session = sessionRepository.findByIdAndUserId(sessionId, authenticatedUserId)
                .orElseThrow(() -> new PracticeSessionNotFoundException(sessionId));

        session.setStatus(PracticeSessionStatus.ABANDONED);
        session.setAbandonedAt(Instant.now());

        PracticeSessionEntity saved = sessionRepository.save(session);
        return PracticeSessionResponse.fromEntity(saved);
    }

    private String resolveActiveTrapFamilyForSkill(String skillId) {
        if (skillId == null) return null;
        if (skillId.contains("RATIONAL")) return "ALG_DOMAIN_EXCLUSION_MISSED";
        if (skillId.contains("INEQ")) return "INEQ_NEGATIVE_MULT_NO_FLIP";
        if (skillId.contains("RADICAL")) return "ALG_SQUARE_BOTH_SIDES_EXTRANEOUS";
        if (skillId.contains("CHAIN")) return "CALC_CHAIN_RULE_OMITTED";
        return "ALG_DOMAIN_EXCLUSION_MISSED"; // default representative trap
    }
}
