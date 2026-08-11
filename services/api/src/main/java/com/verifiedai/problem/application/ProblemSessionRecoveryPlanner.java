package com.verifiedai.problem.application;

import com.verifiedai.problem.domain.model.ProblemAssetDerivativeStatus;
import com.verifiedai.problem.domain.model.ProblemClassificationJobStatus;
import com.verifiedai.problem.domain.model.ProblemClassificationStatus;
import com.verifiedai.problem.domain.model.ProblemParseJobStatus;
import com.verifiedai.problem.domain.model.ProblemParseSupportStatus;
import com.verifiedai.problem.domain.model.ProblemSessionActiveJobType;
import com.verifiedai.problem.domain.model.ProblemSessionNextAction;
import com.verifiedai.problem.domain.model.ProblemSessionStage;
import com.verifiedai.problem.domain.model.RecognitionJobStatus;
import com.verifiedai.problem.infrastructure.persistence.ProblemClassificationJobJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemParseJobJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.RecognitionJobJpaEntity;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import org.springframework.stereotype.Component;

@Component
public class ProblemSessionRecoveryPlanner {

    public ProblemSessionRecoveryPlan plan(ProblemSessionRecoveryInputs inputs) {
        if (inputs.availableSourceAsset() == null) {
            return plan(
                ProblemSessionStage.AWAITING_UPLOAD,
                ProblemSessionNextAction.RESUME_UPLOAD,
                false,
                false,
                null,
                null
            );
        }

        if (inputs.recognitionDerivative() == null) {
            return plan(
                ProblemSessionStage.PREPROCESSING,
                ProblemSessionNextAction.START_PREPROCESSING,
                false,
                false,
                null,
                null
            );
        }

        if (ProblemAssetDerivativeStatus.FAILED.name().equals(inputs.recognitionDerivative().status())) {
            return plan(
                ProblemSessionStage.TERMINAL,
                ProblemSessionNextAction.RECAPTURE_OR_REIMPORT,
                false,
                false,
                inputs.recognitionDerivative().failureCode(),
                null
            );
        }

        ProblemSessionRecoveryPlan recognitionPlan = recognitionPlan(inputs);
        if (recognitionPlan != null) {
            return recognitionPlan;
        }

        ProblemSessionRecoveryPlan parseJobPlan = parseJobPlan(inputs);
        if (parseJobPlan != null) {
            return parseJobPlan;
        }

        if (inputs.selectedParse() == null) {
            if (inputs.acceptedParseHistoryExists()) {
                return plan(
                    ProblemSessionStage.TERMINAL,
                    ProblemSessionNextAction.NONE,
                    false,
                    false,
                    ApiErrorCode.PROBLEM_SESSION_LINEAGE_AMBIGUOUS.name(),
                    null
                );
            }
            return plan(
                ProblemSessionStage.PARSING,
                ProblemSessionNextAction.START_PARSE,
                false,
                false,
                null,
                null
            );
        }

        if (ProblemParseSupportStatus.REVIEW_REQUIRED.name().equals(inputs.selectedParse().supportStatus())
            || inputs.selectedParse().reviewRequired()) {
            return plan(
                ProblemSessionStage.PARSE_REVIEW,
                ProblemSessionNextAction.REVIEW_PARSE,
                false,
                true,
                null,
                null
            );
        }

        if (ProblemParseSupportStatus.UNSUPPORTED.name().equals(inputs.selectedParse().supportStatus())) {
            return plan(
                ProblemSessionStage.TERMINAL,
                ProblemSessionNextAction.UNSUPPORTED,
                false,
                false,
                inputs.selectedParse().unsupportedReason(),
                null
            );
        }

        if (inputs.canonicalProblem() == null) {
            return plan(
                ProblemSessionStage.CANONICALIZATION,
                ProblemSessionNextAction.CANONICALIZE,
                false,
                false,
                null,
                null
            );
        }

        ProblemSessionRecoveryPlan classificationPlan = classificationPlan(inputs);
        if (classificationPlan != null) {
            return classificationPlan;
        }

        if (inputs.classification() == null) {
            return plan(
                ProblemSessionStage.CLASSIFICATION,
                ProblemSessionNextAction.START_CLASSIFICATION,
                false,
                false,
                null,
                null
            );
        }

        return switch (ProblemClassificationStatus.valueOf(inputs.classification().status())) {
            case CLASSIFIED -> plan(
                ProblemSessionStage.READY_FOR_SOLVE,
                ProblemSessionNextAction.READY_FOR_SOLVE,
                false,
                false,
                null,
                null
            );
            case REVIEW_REQUIRED -> plan(
                ProblemSessionStage.CLASSIFICATION,
                ProblemSessionNextAction.UNSUPPORTED,
                false,
                true,
                inputs.classification().reviewReason(),
                null
            );
            case UNKNOWN, UNSUPPORTED -> plan(
                ProblemSessionStage.TERMINAL,
                ProblemSessionNextAction.UNSUPPORTED,
                false,
                false,
                inputs.classification().reviewReason(),
                null
            );
        };
    }

    private ProblemSessionRecoveryPlan recognitionPlan(ProblemSessionRecoveryInputs inputs) {
        if (inputs.recognitionJob() == null && inputs.recognitionEvidence() == null) {
            return plan(
                ProblemSessionStage.RECOGNITION,
                ProblemSessionNextAction.START_RECOGNITION,
                false,
                false,
                null,
                null
            );
        }
        if (inputs.recognitionJob() == null) {
            return null;
        }
        RecognitionJobStatus status = RecognitionJobStatus.valueOf(inputs.recognitionJob().status());
        ProblemSessionActiveJob activeJob = activeJob(ProblemSessionActiveJobType.RECOGNITION, inputs.recognitionJob());
        return switch (status) {
            case QUEUED, RUNNING -> plan(
                ProblemSessionStage.RECOGNITION,
                ProblemSessionNextAction.WAIT_RECOGNITION,
                false,
                false,
                inputs.recognitionJob().lastErrorCode(),
                activeJob
            );
            case FAILED_RETRYABLE -> plan(
                ProblemSessionStage.RECOGNITION,
                ProblemSessionNextAction.RETRY_RECOGNITION,
                true,
                false,
                inputs.recognitionJob().lastErrorCode(),
                activeJob
            );
            case FAILED_TERMINAL -> plan(
                ProblemSessionStage.TERMINAL,
                ProblemSessionNextAction.RECAPTURE_OR_REIMPORT,
                false,
                false,
                inputs.recognitionJob().lastErrorCode(),
                activeJob
            );
            case SUCCEEDED -> null;
        };
    }

    private ProblemSessionRecoveryPlan parseJobPlan(ProblemSessionRecoveryInputs inputs) {
        if (inputs.parseJob() == null) {
            return null;
        }
        ProblemParseJobStatus status = ProblemParseJobStatus.valueOf(inputs.parseJob().status());
        ProblemSessionActiveJob activeJob = activeJob(ProblemSessionActiveJobType.PARSE, inputs.parseJob());
        return switch (status) {
            case QUEUED, RUNNING -> plan(
                ProblemSessionStage.PARSING,
                ProblemSessionNextAction.WAIT_PARSE,
                false,
                false,
                inputs.parseJob().lastErrorCode(),
                activeJob
            );
            case FAILED_RETRYABLE -> plan(
                ProblemSessionStage.PARSING,
                ProblemSessionNextAction.RETRY_PARSE,
                true,
                false,
                inputs.parseJob().lastErrorCode(),
                activeJob
            );
            case FAILED_TERMINAL -> plan(
                ProblemSessionStage.TERMINAL,
                ProblemSessionNextAction.RECAPTURE_OR_REIMPORT,
                false,
                false,
                inputs.parseJob().lastErrorCode(),
                activeJob
            );
            case UNSUPPORTED -> plan(
                ProblemSessionStage.TERMINAL,
                ProblemSessionNextAction.UNSUPPORTED,
                false,
                false,
                inputs.parseJob().lastErrorCode(),
                activeJob
            );
            case SUCCEEDED -> null;
        };
    }

    private ProblemSessionRecoveryPlan classificationPlan(ProblemSessionRecoveryInputs inputs) {
        if (inputs.classificationJob() == null) {
            return null;
        }
        ProblemClassificationJobStatus status = ProblemClassificationJobStatus.valueOf(inputs.classificationJob().status());
        ProblemSessionActiveJob activeJob = activeJob(ProblemSessionActiveJobType.CLASSIFICATION, inputs.classificationJob());
        return switch (status) {
            case QUEUED, RUNNING -> plan(
                ProblemSessionStage.CLASSIFICATION,
                ProblemSessionNextAction.WAIT_CLASSIFICATION,
                false,
                false,
                inputs.classificationJob().lastErrorCode(),
                activeJob
            );
            case FAILED_RETRYABLE -> plan(
                ProblemSessionStage.CLASSIFICATION,
                ProblemSessionNextAction.RETRY_CLASSIFICATION,
                true,
                false,
                inputs.classificationJob().lastErrorCode(),
                activeJob
            );
            case FAILED_TERMINAL -> plan(
                ProblemSessionStage.TERMINAL,
                ProblemSessionNextAction.UNSUPPORTED,
                false,
                false,
                inputs.classificationJob().lastErrorCode(),
                activeJob
            );
            case SUCCEEDED -> null;
        };
    }

    private ProblemSessionRecoveryPlan plan(
        ProblemSessionStage stage,
        ProblemSessionNextAction nextAction,
        boolean retryable,
        boolean reviewRequired,
        String failureCode,
        ProblemSessionActiveJob activeJob
    ) {
        return new ProblemSessionRecoveryPlan(stage, nextAction, retryable, reviewRequired, failureCode, activeJob);
    }

    private ProblemSessionActiveJob activeJob(ProblemSessionActiveJobType type, RecognitionJobJpaEntity job) {
        return new ProblemSessionActiveJob(type, job.id(), job.status(), job.attemptCount(), job.maxAttempts(), job.lastErrorCode());
    }

    private ProblemSessionActiveJob activeJob(ProblemSessionActiveJobType type, ProblemParseJobJpaEntity job) {
        return new ProblemSessionActiveJob(type, job.id(), job.status(), job.attemptCount(), job.maxAttempts(), job.lastErrorCode());
    }

    private ProblemSessionActiveJob activeJob(ProblemSessionActiveJobType type, ProblemClassificationJobJpaEntity job) {
        return new ProblemSessionActiveJob(type, job.id(), job.status(), job.attemptCount(), job.maxAttempts(), job.lastErrorCode());
    }
}
