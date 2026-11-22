package com.verifiedai.mastery.application;

import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.billing.application.LearningOsCapability;
import com.verifiedai.mastery.AttemptQueryPort;
import com.verifiedai.mastery.VerifiedAttemptSnapshot;
import com.verifiedai.mastery.api.dto.VerifiedTwinChallengeResponse;
import com.verifiedai.mastery.domain.*;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class CounterfactualTwinChallengeService {
    private CapabilityAccessPolicy access;
    @org.springframework.beans.factory.annotation.Value("${verifiedai.billing.learning-os-tiers-enabled:false}")
    private String learningOsFlag;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAccess(CapabilityAccessPolicy access) {
        this.access = access;
    }

    private boolean tierGuardsEnabled() {
        return "true".equalsIgnoreCase(learningOsFlag);
    }

    private void requireAccess(UUID userId, LearningOsCapability capability) {
        if (access == null) {
            throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.ENTITLEMENT_SYNC_FAILED,
                "Entitlement verification unavailable", true, "RETRY");
        }
        access.require(userId, capability);
    }


    private static final BigDecimal UNASSISTED_REMEDIATION_DELTA = new BigDecimal("0.1500");
    private static final BigDecimal REMEDIATION_EXIT_FLOOR = new BigDecimal("0.5500");

    public record TwinGenerationOutcome(VerifiedTwinChallengeResponse response, boolean createdNew) {}

    private final MasteryStateLockStore store;
    private final AttemptQueryPort attemptQueryPort;
    private final MathVerifierTwinPort mathVerifierTwinPort;
    private final CounterfactualTwinRepository twinRepository;
    private final CounterfactualTwinSubmissionRepository submissionRepository;
    private final LearnerSkillMasteryRepository masteryRepository;
    private final Clock clock;

    public CounterfactualTwinChallengeService(
            MasteryStateLockStore store,
            AttemptQueryPort attemptQueryPort,
            MathVerifierTwinPort mathVerifierTwinPort,
            CounterfactualTwinRepository twinRepository,
            CounterfactualTwinSubmissionRepository submissionRepository,
            LearnerSkillMasteryRepository masteryRepository,
            Clock clock) {
        this.store = store;
        this.attemptQueryPort = attemptQueryPort;
        this.mathVerifierTwinPort = mathVerifierTwinPort;
        this.twinRepository = twinRepository;
        this.submissionRepository = submissionRepository;
        this.masteryRepository = masteryRepository;
        this.clock = clock;
    }

    @Transactional
    public TwinGenerationOutcome generateTwinWithOutcome(UUID tenantId, UUID userId, UUID attemptId) {
        OwnerKey owner = new OwnerKey(tenantId, userId);
        try (LockHandle ignored = store.lock(owner)) {
            VerifiedAttemptSnapshot attempt = attemptQueryPort
                    .getVerifiedAttemptForTwinGeneration(tenantId, userId, attemptId)
                    .orElseThrow(() -> new ResourceNotFoundOrForbiddenException("ATTEMPT_NOT_FOUND"));

            if (!attempt.hasDiagnosedMistake()) {
                throw new UnprocessableDomainRuleException("ATTEMPT_NOT_ELIGIBLE_FOR_TWIN",
                        "Counterfactual twin generation requires a diagnosed mistake on the source attempt.");
            }

            Optional<CounterfactualTwinDrillEntity> existingActive = twinRepository
                    .findActiveIssuedBySourceAttempt(tenantId, userId, attemptId);
            if (existingActive.isPresent()) {
                return new TwinGenerationOutcome(
                        VerifiedTwinMapper.toIssuedResponse(existingActive.get()),
                        false
                );
            }

            if (tierGuardsEnabled()) {
                requireAccess(userId, mathVerifierTwinPort.requiresHybridAccess()
                    ? LearningOsCapability.HYBRID_TWIN : LearningOsCapability.DETERMINISTIC_TWIN);
            }
            GeneratedTwinSpec spec = mathVerifierTwinPort.synthesizeIsomorphicTwin(
                    attempt.curriculumCode(),
                    attempt.targetSkillCode(),
                    attempt.rootCauseSkillCode(),
                    attempt.canonicalProblemAst(),
                    attemptId.toString()
            );

            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            Instant lockUntil = now.plusSeconds(60);

            CounterfactualTwinDrillEntity entity = new CounterfactualTwinDrillEntity(
                    UUID.randomUUID(),
                    tenantId,
                    userId,
                    attemptId,
                    attempt.curriculumCode(),
                    attempt.targetSkillCode(),
                    attempt.rootCauseSkillCode(),
                    spec.problemStatementLatex(),
                    spec.expectedCanonicalExpr(),
                    spec.solutionStepsJson(),
                    spec.generatorSeed(),
                    spec.verifierProofHash(),
                    "ISSUED",
                    now,
                    lockUntil,
                    false
            );

            twinRepository.saveAndFlush(entity);
            return new TwinGenerationOutcome(VerifiedTwinMapper.toIssuedResponse(entity), true);
        }
    }

    @Transactional
    public VerifiedTwinChallengeResponse submitOrRevealTwin(
            UUID tenantId, UUID userId, UUID twinId, TwinSubmissionCommand command) {
        OwnerKey owner = new OwnerKey(tenantId, userId);
        try (LockHandle ignored = store.lock(owner)) {
            CounterfactualTwinDrillEntity twin = twinRepository
                    .findByTenantIdAndUserIdAndTwinId(tenantId, userId, twinId)
                    .orElseThrow(() -> new ResourceNotFoundOrForbiddenException("TWIN_NOT_FOUND"));

            if (!"ISSUED".equals(twin.getStatus()) && !"FAILED_UNASSISTED".equals(twin.getStatus())) {
                throw new ConflictDomainException("TWIN_ALREADY_TERMINAL",
                        "Twin drill is already in terminal status: " + twin.getStatus());
            }

            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

            // CASE A: REVEAL_SOLUTION
            if ("REVEAL_SOLUTION".equals(command.action())) {
                if (now.isBefore(twin.getLockUntil()) && !command.forceEarlyForfeit()) {
                    long remainingSeconds = Duration.between(now, twin.getLockUntil()).toSeconds() + 1;
                    throw new TwinSolutionLockedException(twin.getLockUntil(), remainingSeconds);
                }

                twin.markSolutionRevealedAssisted(now);
                twinRepository.save(twin);

                CounterfactualTwinSubmissionEntity sub = CounterfactualTwinSubmissionEntity.assistedReveal(
                        UUID.randomUUID(), tenantId, userId, twinId, now);
                submissionRepository.save(sub);

                LearnerSkillMasteryEntity mastery = masteryRepository
                        .findByTenantIdAndUserIdAndSkillCode(tenantId, userId, twin.getRootCauseSkillCode())
                        .orElseGet(() -> LearnerSkillMasteryEntity.createInitial(
                                tenantId, userId, twin.getCurriculumCode(), twin.getRootCauseSkillCode(), now));

                return VerifiedTwinMapper.toEvaluatedResponse(
                        twin, sub, mastery.getMasteryScore(), mastery.getMasteryState(), false);
            }

            // CASE B: SUBMIT_ANSWER
            boolean isCorrect = mathVerifierTwinPort.verifySymbolicEquivalence(
                    command.submittedAnswerLatex(),
                    twin.getExpectedCanonicalExpr()
            );

            LearnerSkillMasteryEntity mastery = masteryRepository
                    .findByTenantIdAndUserIdAndSkillCode(tenantId, userId, twin.getRootCauseSkillCode())
                    .orElseGet(() -> LearnerSkillMasteryEntity.createInitial(
                            tenantId, userId, twin.getCurriculumCode(), twin.getRootCauseSkillCode(), now));

            BigDecimal credit = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            boolean remediationCleared = false;

            if (isCorrect) {
                credit = UNASSISTED_REMEDIATION_DELTA;
                twin.markSolvedUnassisted(now);
                mastery.applyVerifiedUnassistedRemediation(credit, REMEDIATION_EXIT_FLOOR, now);
                remediationCleared = !"AT_RISK_REMEDIATION_REQUIRED".equals(mastery.getMasteryState());
            } else {
                twin.markFailedUnassisted();
                mastery.recordFailure(now);
            }

            twinRepository.save(twin);
            masteryRepository.save(mastery);

            CounterfactualTwinSubmissionEntity sub = CounterfactualTwinSubmissionEntity.answerEvaluation(
                    UUID.randomUUID(), tenantId, userId, twinId,
                    command.submittedAnswerLatex(), isCorrect, credit, now);
            submissionRepository.save(sub);

            return VerifiedTwinMapper.toEvaluatedResponse(
                    twin, sub, mastery.getMasteryScore(), mastery.getMasteryState(), remediationCleared);
        }
    }
}
