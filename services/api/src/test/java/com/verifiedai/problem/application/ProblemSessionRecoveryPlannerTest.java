package com.verifiedai.problem.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.verifiedai.ai.application.AiProvenance;
import com.verifiedai.ai.application.AiUsage;
import com.verifiedai.problem.domain.model.ProblemAssetDerivativeKind;
import com.verifiedai.problem.domain.model.ProblemAssetKind;
import com.verifiedai.problem.domain.model.ProblemAssetQualityOutcome;
import com.verifiedai.problem.domain.model.ProblemAssetSource;
import com.verifiedai.problem.domain.model.ProblemParseSupportStatus;
import com.verifiedai.problem.domain.model.ProblemSessionActiveJobType;
import com.verifiedai.problem.domain.model.ProblemSessionNextAction;
import com.verifiedai.problem.domain.model.ProblemSessionStage;
import com.verifiedai.problem.infrastructure.persistence.CanonicalProblemJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetDerivativeJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemClassificationJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemParseJobJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemParseJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemSessionJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.RecognitionJobJpaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProblemSessionRecoveryPlannerTest {
    private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000004900");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000004901");
    private static final UUID ASSET_ID = UUID.fromString("00000000-0000-0000-0000-000000004902");
    private static final UUID DERIVATIVE_ID = UUID.fromString("00000000-0000-0000-0000-000000004903");
    private final ProblemSessionRecoveryPlanner planner = new ProblemSessionRecoveryPlanner();

    @Test
    void resumesMissingUploadWithoutStartingAiWork() {
        ProblemSessionRecoveryPlan plan = planner.plan(inputs(
            session(),
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            null
        ));

        assertThat(plan.stage()).isEqualTo(ProblemSessionStage.AWAITING_UPLOAD);
        assertThat(plan.nextAction()).isEqualTo(ProblemSessionNextAction.RESUME_UPLOAD);
        assertThat(plan.retryable()).isFalse();
    }

    @Test
    void returnsExactRetryActionForRetryableParseJob() {
        RecognitionJobJpaEntity recognitionJob = recognitionJob();
        recognitionJob.markRunning(NOW.plusSeconds(1));
        recognitionJob.markSucceeded(false, NOW.plusSeconds(2));
        ProblemParseJobJpaEntity parseJob = parseJob();
        parseJob.markRunning(NOW.plusSeconds(3));
        parseJob.markFailure("PROBLEM_PARSE_FAILED", "TIMEOUT", true, NOW.plusSeconds(10), NOW.plusSeconds(4));

        ProblemSessionRecoveryPlan plan = planner.plan(inputs(
            uploadedSession(),
            availableAsset(),
            readyDerivative(),
            recognitionJob,
            parseJob,
            null,
            false,
            null,
            null,
            null
        ));

        assertThat(plan.stage()).isEqualTo(ProblemSessionStage.PARSING);
        assertThat(plan.nextAction()).isEqualTo(ProblemSessionNextAction.RETRY_PARSE);
        assertThat(plan.retryable()).isTrue();
        assertThat(plan.activeJob().type()).isEqualTo(ProblemSessionActiveJobType.PARSE);
        assertThat(plan.activeJob().id()).isEqualTo(parseJob.id());
    }

    @Test
    void requiresParseReviewWhenSelectedParseIsReviewRequired() {
        ProblemParseJpaEntity parse = selectedParse(ProblemParseSupportStatus.REVIEW_REQUIRED, true);

        ProblemSessionRecoveryPlan plan = planner.plan(inputs(
            uploadedSessionWithSelectedParse(parse.id()),
            availableAsset(),
            readyDerivative(),
            succeededRecognitionJob(),
            succeededParseJob(),
            parse,
            true,
            null,
            null,
            null
        ));

        assertThat(plan.stage()).isEqualTo(ProblemSessionStage.PARSE_REVIEW);
        assertThat(plan.nextAction()).isEqualTo(ProblemSessionNextAction.REVIEW_PARSE);
        assertThat(plan.reviewRequired()).isTrue();
    }

    @Test
    void canonicalizesFromSelectedSupportedParseBeforeClassification() {
        ProblemParseJpaEntity parse = selectedParse(ProblemParseSupportStatus.SUPPORTED, false);

        ProblemSessionRecoveryPlan plan = planner.plan(inputs(
            uploadedSessionWithSelectedParse(parse.id()),
            availableAsset(),
            readyDerivative(),
            succeededRecognitionJob(),
            succeededParseJob(),
            parse,
            true,
            null,
            null,
            null
        ));

        assertThat(plan.stage()).isEqualTo(ProblemSessionStage.CANONICALIZATION);
        assertThat(plan.nextAction()).isEqualTo(ProblemSessionNextAction.CANONICALIZE);
    }

    @Test
    void reportsReadyForSolveOnlyAfterSelectedParseCanonicalAndClassificationExist() {
        ProblemParseJpaEntity parse = selectedParse(ProblemParseSupportStatus.SUPPORTED, false);
        CanonicalProblemJpaEntity canonical = canonicalProblem(parse);

        ProblemSessionRecoveryPlan plan = planner.plan(inputs(
            uploadedSessionWithSelectedParse(parse.id()),
            availableAsset(),
            readyDerivative(),
            succeededRecognitionJob(),
            succeededParseJob(),
            parse,
            true,
            canonical,
            null,
            classification(canonical)
        ));

        assertThat(plan.stage()).isEqualTo(ProblemSessionStage.READY_FOR_SOLVE);
        assertThat(plan.nextAction()).isEqualTo(ProblemSessionNextAction.READY_FOR_SOLVE);
    }

    @Test
    void flagsAmbiguousLineageWhenAcceptedParseHistoryExistsWithoutSelectedParse() {
        ProblemSessionRecoveryPlan plan = planner.plan(inputs(
            uploadedSession(),
            availableAsset(),
            readyDerivative(),
            succeededRecognitionJob(),
            succeededParseJob(),
            null,
            true,
            null,
            null,
            null
        ));

        assertThat(plan.stage()).isEqualTo(ProblemSessionStage.TERMINAL);
        assertThat(plan.nextAction()).isEqualTo(ProblemSessionNextAction.NONE);
        assertThat(plan.failureCode()).isEqualTo("PROBLEM_SESSION_LINEAGE_AMBIGUOUS");
    }

    private ProblemSessionRecoveryInputs inputs(
        ProblemSessionJpaEntity session,
        ProblemAssetJpaEntity asset,
        ProblemAssetDerivativeJpaEntity derivative,
        RecognitionJobJpaEntity recognitionJob,
        ProblemParseJobJpaEntity parseJob,
        ProblemParseJpaEntity selectedParse,
        boolean acceptedParseHistoryExists,
        CanonicalProblemJpaEntity canonicalProblem,
        Object unusedClassificationJob,
        ProblemClassificationJpaEntity classification
    ) {
        return new ProblemSessionRecoveryInputs(
            session,
            asset,
            derivative,
            recognitionJob,
            null,
            parseJob,
            selectedParse,
            acceptedParseHistoryExists,
            canonicalProblem,
            null,
            classification
        );
    }

    private ProblemSessionJpaEntity session() {
        return ProblemSessionJpaEntity.create(SESSION_ID, USER_ID, ProblemAssetSource.CAMERA, NOW);
    }

    private ProblemSessionJpaEntity uploadedSession() {
        ProblemSessionJpaEntity session = session();
        session.markAssetUploaded(NOW.plusSeconds(1));
        return session;
    }

    private ProblemSessionJpaEntity uploadedSessionWithSelectedParse(UUID parseId) {
        ProblemSessionJpaEntity session = uploadedSession();
        session.selectParse(parseId, NOW.plusSeconds(2));
        return session;
    }

    private ProblemAssetJpaEntity availableAsset() {
        ProblemAssetJpaEntity asset = ProblemAssetJpaEntity.pending(
            ASSET_ID,
            SESSION_ID,
            USER_ID,
            ProblemAssetSource.CAMERA,
            ProblemAssetKind.IMAGE,
            "problem-assets/source.jpg",
            "image/jpeg",
            1024,
            "0".repeat(64),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            BigDecimal.ONE,
            1200,
            900,
            null,
            NOW.plusSeconds(600),
            NOW,
            "reserve-key",
            "reserve-hash"
        );
        asset.markAvailable(NOW.plusSeconds(1));
        return asset;
    }

    private ProblemAssetDerivativeJpaEntity readyDerivative() {
        return ProblemAssetDerivativeJpaEntity.ready(
            DERIVATIVE_ID,
            ASSET_ID,
            SESSION_ID,
            USER_ID,
            ProblemAssetDerivativeKind.OCR_OPTIMIZED,
            true,
            "problem-assets/derivative.jpg",
            1024,
            "1".repeat(64),
            1200,
            900,
            1200,
            900,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            BigDecimal.ONE,
            "DOCUMENT_PREPROCESSOR",
            "1.0",
            "capture-quality-v1",
            false,
            false,
            false,
            false,
            ProblemAssetQualityOutcome.PASS,
            NOW
        );
    }

    private RecognitionJobJpaEntity recognitionJob() {
        return RecognitionJobJpaEntity.queued(
            UUID.fromString("00000000-0000-0000-0000-000000004904"),
            USER_ID,
            SESSION_ID,
            ASSET_ID,
            DERIVATIVE_ID,
            "route-v1",
            "prompt",
            "v1",
            "recognition-evidence-v1",
            2,
            NOW
        );
    }

    private RecognitionJobJpaEntity succeededRecognitionJob() {
        RecognitionJobJpaEntity job = recognitionJob();
        job.markRunning(NOW.plusSeconds(1));
        job.markSucceeded(false, NOW.plusSeconds(2));
        return job;
    }

    private ProblemParseJobJpaEntity parseJob() {
        return ProblemParseJobJpaEntity.queued(
            UUID.fromString("00000000-0000-0000-0000-000000004905"),
            USER_ID,
            SESSION_ID,
            UUID.fromString("00000000-0000-0000-0000-000000004906"),
            1,
            "route-v1",
            "prompt",
            "v1",
            "problem-parse-v1",
            2,
            NOW
        );
    }

    private ProblemParseJobJpaEntity succeededParseJob() {
        ProblemParseJobJpaEntity job = parseJob();
        job.markRunning(NOW.plusSeconds(3));
        job.markSucceeded(false, NOW.plusSeconds(4));
        return job;
    }

    private ProblemParseJpaEntity selectedParse(ProblemParseSupportStatus supportStatus, boolean reviewRequired) {
        return ProblemParseJpaEntity.fromAi(
            UUID.fromString("00000000-0000-0000-0000-000000004907"),
            UUID.fromString("00000000-0000-0000-0000-000000004905"),
            USER_ID,
            SESSION_ID,
            UUID.fromString("00000000-0000-0000-0000-000000004906"),
            1,
            1,
            supportStatus.name(),
            null,
            reviewRequired,
            "problem-parse-v1",
            "{}",
            "{\"schemaVersion\":\"problem-parse-v1\"}",
            provenance("problem-parse-v1"),
            AiUsage.zeroCost("pricing-v1"),
            10,
            15,
            null,
            NOW.plusSeconds(5)
        );
    }

    private CanonicalProblemJpaEntity canonicalProblem(ProblemParseJpaEntity parse) {
        return new CanonicalProblemJpaEntity(
            UUID.fromString("00000000-0000-0000-0000-000000004908"),
            USER_ID,
            SESSION_ID,
            parse.id(),
            parse.revision(),
            1,
            "canonical-problem-v1",
            "verifier-input-v1",
            "EQUATION",
            "SOLVE_EQUATION",
            "{}",
            "{}",
            "{}",
            NOW.plusSeconds(6)
        );
    }

    private ProblemClassificationJpaEntity classification(CanonicalProblemJpaEntity canonical) {
        return new ProblemClassificationJpaEntity(
            UUID.fromString("00000000-0000-0000-0000-000000004909"),
            UUID.fromString("00000000-0000-0000-0000-000000004910"),
            USER_ID,
            SESSION_ID,
            canonical.id(),
            1,
            "SYSTEM",
            "CLASSIFIED",
            null,
            "curriculum-taxonomy-v1",
            "problem-classification-v1",
            "canonical-classification-projection-v1",
            "MATH",
            "MATH.EQUATIONS",
            "ALGEBRA.LINEAR_EQUATION_ONE_VARIABLE",
            "EASY",
            "difficulty-policy-v1",
            "HIGH",
            "confidence-policy-v1",
            "local",
            "PROBLEM_CLASSIFY",
            "LOCAL_FIXTURE",
            "local-fixture-classifier-v1",
            "problem-classifier",
            "v1",
            "route-v1",
            false,
            10L,
            0L,
            "f".repeat(64),
            NOW.plusSeconds(7)
        );
    }

    private AiProvenance provenance(String schemaVersion) {
        return new AiProvenance(
            "LOCAL_FIXTURE",
            "local-fixture",
            "route-v1",
            "prompt",
            "v1",
            schemaVersion,
            null,
            null,
            false
        );
    }
}
