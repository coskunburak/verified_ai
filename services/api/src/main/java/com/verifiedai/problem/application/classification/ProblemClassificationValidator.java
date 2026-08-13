package com.verifiedai.problem.application.classification;

import com.verifiedai.curriculum.application.CurriculumTaxonomyCatalog;
import com.verifiedai.curriculum.application.CurriculumTaxonomySnapshot;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationReviewReason;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
final class ProblemClassificationValidator {
    private final CurriculumTaxonomyCatalog taxonomyCatalog;

    ProblemClassificationValidator(
        CurriculumTaxonomyCatalog taxonomyCatalog
    ) {
        this.taxonomyCatalog = taxonomyCatalog;
    }

    ValidatedProblemClassification validate(
        ProblemClassificationProposal proposal
    ) {
        Objects.requireNonNull(proposal, "proposal");

        CurriculumTaxonomySnapshot snapshot =
            taxonomyCatalog.snapshot();

        validateSchemaVersion(proposal);
        validateOntologyVersion(proposal, snapshot);

        if (proposal.status() == null) {
            throw invalid(
                ProblemClassificationValidationFailure.STATUS_SEMANTICS_INVALID,
                "Classification status is required"
            );
        }

        return switch (proposal.status()) {
            case CLASSIFIED ->
                validateClassified(proposal, snapshot);

            case REVIEW_REQUIRED ->
                validateReviewRequired(proposal);

            case UNKNOWN ->
                validateUnknown(proposal);

            case UNSUPPORTED ->
                validateUnsupported(proposal);
        };
    }

    private ValidatedProblemClassification validateClassified(
        ProblemClassificationProposal proposal,
        CurriculumTaxonomySnapshot snapshot
    ) {
        if (proposal.reviewReason() != null) {
            throw invalid(
                ProblemClassificationValidationFailure.REVIEW_REASON_FORBIDDEN,
                "CLASSIFIED result cannot carry a review reason"
            );
        }

        String primarySkillId =
            requireText(
                proposal.primarySkillId(),
                ProblemClassificationValidationFailure.PRIMARY_SKILL_REQUIRED,
                "CLASSIFIED result requires exactly one primary skill"
            );

        if (!taxonomyCatalog.isActiveSkill(primarySkillId)) {
            throw invalid(
                ProblemClassificationValidationFailure.PRIMARY_SKILL_UNKNOWN,
                "Primary skill is not active in the selected ontology"
            );
        }

        String topicId =
            taxonomyCatalog.topicForSkill(primarySkillId);

        if (
            topicId == null
                || !taxonomyCatalog.isActiveTopic(topicId)
        ) {
            throw invalid(
                ProblemClassificationValidationFailure.PRIMARY_SKILL_PARENT_INVALID,
                "Primary skill does not resolve to an active canonical topic"
            );
        }

        String subjectId =
            taxonomyCatalog.subjectForTopic(topicId);

        if (
            subjectId == null
                || !taxonomyCatalog.isActiveSubject(subjectId)
        ) {
            throw invalid(
                ProblemClassificationValidationFailure.PRIMARY_SKILL_PARENT_INVALID,
                "Primary skill topic does not resolve to an active canonical subject"
            );
        }

        if (proposal.difficulty() == null) {
            throw invalid(
                ProblemClassificationValidationFailure.DIFFICULTY_REQUIRED,
                "CLASSIFIED result requires an intrinsic difficulty band"
            );
        }

        List<String> secondarySkills =
            validateSecondarySkills(
                proposal.secondarySkillIds(),
                primarySkillId,
                subjectId,
                snapshot
            );

        return new ValidatedProblemClassification(
            ProblemClassificationStatus.CLASSIFIED,
            null,
            snapshot.ontologyVersion(),
            subjectId,
            topicId,
            primarySkillId,
            secondarySkills,
            proposal.difficulty()
        );
    }

    private ValidatedProblemClassification validateReviewRequired(
        ProblemClassificationProposal proposal
    ) {
        if (proposal.reviewReason() == null) {
            throw invalid(
                ProblemClassificationValidationFailure.REVIEW_REASON_REQUIRED,
                "REVIEW_REQUIRED result must explain why review is required"
            );
        }

        requireNoAuthoritativeClassification(
            proposal,
            ProblemClassificationStatus.REVIEW_REQUIRED
        );

        return nonClassifiedResult(proposal);
    }

    private ValidatedProblemClassification validateUnknown(
        ProblemClassificationProposal proposal
    ) {
        requireNoAuthoritativeClassification(
            proposal,
            ProblemClassificationStatus.UNKNOWN
        );

        if (
            proposal.reviewReason() != null
                && proposal.reviewReason()
                != ProblemClassificationReviewReason.ONTOLOGY_COVERAGE_GAP
                && proposal.reviewReason()
                != ProblemClassificationReviewReason.INSUFFICIENT_SEMANTIC_EVIDENCE
        ) {
            throw invalid(
                ProblemClassificationValidationFailure.STATUS_SEMANTICS_INVALID,
                "UNKNOWN result contains an incompatible review reason"
            );
        }

        return nonClassifiedResult(proposal);
    }

    private ValidatedProblemClassification validateUnsupported(
        ProblemClassificationProposal proposal
    ) {
        requireNoAuthoritativeClassification(
            proposal,
            ProblemClassificationStatus.UNSUPPORTED
        );

        if (proposal.reviewReason() != null) {
            throw invalid(
                ProblemClassificationValidationFailure.REVIEW_REASON_FORBIDDEN,
                "UNSUPPORTED result cannot carry a review reason"
            );
        }

        return nonClassifiedResult(proposal);
    }

    private ValidatedProblemClassification nonClassifiedResult(
        ProblemClassificationProposal proposal
    ) {
        return new ValidatedProblemClassification(
            proposal.status(),
            proposal.reviewReason(),
            proposal.ontologyVersion(),
            null,
            null,
            null,
            List.of(),
            null
        );
    }

    private void requireNoAuthoritativeClassification(
        ProblemClassificationProposal proposal,
        ProblemClassificationStatus status
    ) {
        if (hasText(proposal.primarySkillId())) {
            throw invalid(
                ProblemClassificationValidationFailure.STATUS_SEMANTICS_INVALID,
                status + " result cannot expose an authoritative primary skill"
            );
        }

        if (
            proposal.secondarySkillIds() != null
                && !proposal.secondarySkillIds().isEmpty()
        ) {
            throw invalid(
                ProblemClassificationValidationFailure.STATUS_SEMANTICS_INVALID,
                status + " result cannot expose authoritative secondary skills"
            );
        }

        if (proposal.difficulty() != null) {
            throw invalid(
                ProblemClassificationValidationFailure.STATUS_SEMANTICS_INVALID,
                status + " result cannot expose authoritative difficulty"
            );
        }
    }

    private List<String> validateSecondarySkills(
        List<String> secondarySkillIds,
        String primarySkillId,
        String primarySubjectId,
        CurriculumTaxonomySnapshot snapshot
    ) {
        List<String> skills =
            secondarySkillIds == null
                ? List.of()
                : List.copyOf(secondarySkillIds);

        if (
            skills.size()
                > ProblemClassificationContract.MAX_SECONDARY_SKILLS
        ) {
            throw invalid(
                ProblemClassificationValidationFailure.SECONDARY_SKILL_LIMIT_EXCEEDED,
                "Secondary skill limit exceeded"
            );
        }

        Set<String> seen = new HashSet<>();

        for (String skillId : skills) {
            if (!hasText(skillId)) {
                throw invalid(
                    ProblemClassificationValidationFailure.SECONDARY_SKILL_UNKNOWN,
                    "Secondary skill ID must not be blank"
                );
            }

            if (!seen.add(skillId)) {
                throw invalid(
                    ProblemClassificationValidationFailure.SECONDARY_SKILL_DUPLICATED,
                    "Secondary skill appears more than once"
                );
            }

            if (skillId.equals(primarySkillId)) {
                throw invalid(
                    ProblemClassificationValidationFailure.PRIMARY_SKILL_DUPLICATED_AS_SECONDARY,
                    "Primary skill cannot also be a secondary skill"
                );
            }

            if (!taxonomyCatalog.isActiveSkill(skillId)) {
                throw invalid(
                    ProblemClassificationValidationFailure.SECONDARY_SKILL_UNKNOWN,
                    "Secondary skill is not active in the selected ontology"
                );
            }

            String secondaryTopic =
                taxonomyCatalog.topicForSkill(skillId);

            if (
                secondaryTopic == null
                    || !taxonomyCatalog.isActiveTopic(secondaryTopic)
            ) {
                throw invalid(
                    ProblemClassificationValidationFailure.SECONDARY_SKILL_INCOMPATIBLE,
                    "Secondary skill does not resolve to an active canonical topic"
                );
            }

            String secondarySubject =
                taxonomyCatalog.subjectForTopic(secondaryTopic);

            if (
                secondarySubject == null
                    || !taxonomyCatalog.isActiveSubject(secondarySubject)
            ) {
                throw invalid(
                    ProblemClassificationValidationFailure.SECONDARY_SKILL_INCOMPATIBLE,
                    "Secondary skill does not resolve to an active canonical subject"
                );
            }

            /*
             * V1 compatibility policy:
             *
             * secondary skills may cross topics because a problem such as a
             * quadratic equation may legitimately require an algebra/factoring
             * secondary skill.
             *
             * Crossing subjects is forbidden.
             */
            if (!primarySubjectId.equals(secondarySubject)) {
                throw invalid(
                    ProblemClassificationValidationFailure.SECONDARY_SKILL_INCOMPATIBLE,
                    "Secondary skill belongs to an incompatible subject"
                );
            }

            /*
             * Defensive snapshot integrity check.
             *
             * Catalog methods and snapshot must describe the same ontology.
             */
            if (!snapshot.activeSkillIds().contains(skillId)) {
                throw invalid(
                    ProblemClassificationValidationFailure.SECONDARY_SKILL_UNKNOWN,
                    "Secondary skill is absent from the selected ontology snapshot"
                );
            }
        }

        return List.copyOf(skills);
    }

    private void validateSchemaVersion(
        ProblemClassificationProposal proposal
    ) {
        if (
            !ProblemClassificationContract.SCHEMA_VERSION.equals(
                proposal.schemaVersion()
            )
        ) {
            throw invalid(
                ProblemClassificationValidationFailure.SCHEMA_VERSION_MISMATCH,
                "Classification schema version is not supported"
            );
        }
    }

    private void validateOntologyVersion(
        ProblemClassificationProposal proposal,
        CurriculumTaxonomySnapshot snapshot
    ) {
        if (
            proposal.ontologyVersion() == null
                || !snapshot.ontologyVersion().equals(
                proposal.ontologyVersion()
            )
        ) {
            throw invalid(
                ProblemClassificationValidationFailure.ONTOLOGY_VERSION_MISMATCH,
                "Classification ontology version does not match the validation snapshot"
            );
        }
    }

    private static String requireText(
        String value,
        ProblemClassificationValidationFailure failure,
        String message
    ) {
        if (!hasText(value)) {
            throw invalid(failure, message);
        }

        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ProblemClassificationValidationException invalid(
        ProblemClassificationValidationFailure failure,
        String message
    ) {
        return new ProblemClassificationValidationException(
            failure,
            message
        );
    }
}
