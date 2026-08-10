package com.verifiedai.problem.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.problem.domain.model.ClassificationDifficulty;
import com.verifiedai.problem.domain.model.ProblemClassificationReviewReason;
import com.verifiedai.problem.domain.model.ProblemClassificationStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
final class ProblemClassificationOutputNormalizer {

    private static final Set<String> ROOT_FIELDS =
        Set.of(
            "schemaVersion",
            "ontologyVersion",
            "status",
            "primarySkillId",
            "secondarySkillIds",
            "difficulty",
            "reviewReason"
        );

    private static final int MAX_SKILL_ID_LENGTH = 128;
    private static final int MAX_VERSION_LENGTH = 64;

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    ProblemClassificationProposal normalize(
        String rawOutputJson,
        String expectedSchemaVersion,
        String expectedOntologyVersion
    ) {
        JsonNode root = parse(rawOutputJson);

        if (!root.isObject()) {
            throw invalid(
                "Classification output root must be an object"
            );
        }

        rejectUnknownFields(root);

        String schemaVersion =
            requiredText(
                root,
                "schemaVersion",
                MAX_VERSION_LENGTH
            );

        if (!expectedSchemaVersion.equals(schemaVersion)) {
            throw invalid(
                "Classification schema version mismatch"
            );
        }

        String ontologyVersion =
            requiredText(
                root,
                "ontologyVersion",
                MAX_VERSION_LENGTH
            );

        if (
            !expectedOntologyVersion.equals(
                ontologyVersion
            )
        ) {
            throw invalid(
                "Classification ontology version mismatch"
            );
        }

        ProblemClassificationStatus status =
            requiredEnum(
                root,
                "status",
                ProblemClassificationStatus.class
            );

        String primarySkillId =
            nullableText(
                root,
                "primarySkillId",
                MAX_SKILL_ID_LENGTH
            );

        List<String> secondarySkillIds =
            requiredStringArray(
                root,
                "secondarySkillIds",
                ProblemClassificationContract
                    .MAX_SECONDARY_SKILLS,
                MAX_SKILL_ID_LENGTH
            );

        ClassificationDifficulty difficulty =
            nullableEnum(
                root,
                "difficulty",
                ClassificationDifficulty.class
            );

        ProblemClassificationReviewReason reviewReason =
            nullableEnum(
                root,
                "reviewReason",
                ProblemClassificationReviewReason.class
            );

        return new ProblemClassificationProposal(
            schemaVersion,
            ontologyVersion,
            status,
            primarySkillId,
            secondarySkillIds,
            difficulty,
            reviewReason
        );
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            throw invalid(
                "Classification output is empty"
            );
        }

        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new ProblemClassificationOutputException(
                "Classification output is not valid JSON",
                exception
            );
        }
    }

    private void rejectUnknownFields(
        JsonNode root
    ) {
        Iterator<String> fields =
            root.fieldNames();

        while (fields.hasNext()) {
            String field = fields.next();

            if (!ROOT_FIELDS.contains(field)) {
                throw invalid(
                    "Unknown classification output field: "
                        + field
                );
            }
        }

        for (String field : ROOT_FIELDS) {
            if (!root.has(field)) {
                throw invalid(
                    "Required classification output field is missing: "
                        + field
                );
            }
        }
    }

    private String requiredText(
        JsonNode root,
        String field,
        int maxLength
    ) {
        JsonNode value = root.get(field);

        if (
            value == null
                || !value.isTextual()
        ) {
            throw invalid(
                field + " must be a string"
            );
        }

        String text = value.textValue();

        if (
            text == null
                || text.isBlank()
                || text.length() > maxLength
        ) {
            throw invalid(
                field + " is invalid"
            );
        }

        return text;
    }

    private String nullableText(
        JsonNode root,
        String field,
        int maxLength
    ) {
        JsonNode value = root.get(field);

        if (value == null) {
            throw invalid(
                field + " is required"
            );
        }

        if (value.isNull()) {
            return null;
        }

        if (!value.isTextual()) {
            throw invalid(
                field + " must be a string or null"
            );
        }

        String text = value.textValue();

        if (
            text == null
                || text.isBlank()
                || text.length() > maxLength
        ) {
            throw invalid(
                field + " is invalid"
            );
        }

        return text;
    }

    private List<String> requiredStringArray(
        JsonNode root,
        String field,
        int maxItems,
        int maxLength
    ) {
        JsonNode value = root.get(field);

        if (
            value == null
                || !value.isArray()
        ) {
            throw invalid(
                field + " must be an array"
            );
        }

        if (value.size() > maxItems) {
            throw invalid(
                field + " exceeds maximum size"
            );
        }

        List<String> result =
            new ArrayList<>();

        Set<String> seen =
            new HashSet<>();

        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw invalid(
                    field
                        + " must contain only strings"
                );
            }

            String skillId = item.textValue();

            if (
                skillId == null
                    || skillId.isBlank()
                    || skillId.length() > maxLength
            ) {
                throw invalid(
                    field
                        + " contains an invalid skill ID"
                );
            }

            if (!seen.add(skillId)) {
                throw invalid(
                    field
                        + " contains a duplicate skill ID"
                );
            }

            result.add(skillId);
        }

        return List.copyOf(result);
    }

    private <T extends Enum<T>> T requiredEnum(
        JsonNode root,
        String field,
        Class<T> enumType
    ) {
        String value =
            requiredText(root, field, 64);

        try {
            return Enum.valueOf(
                enumType,
                value
            );
        } catch (IllegalArgumentException exception) {
            throw invalid(
                field + " contains an unknown enum value"
            );
        }
    }

    private <T extends Enum<T>> T nullableEnum(
        JsonNode root,
        String field,
        Class<T> enumType
    ) {
        JsonNode node = root.get(field);

        if (node == null) {
            throw invalid(
                field + " is required"
            );
        }

        if (node.isNull()) {
            return null;
        }

        if (!node.isTextual()) {
            throw invalid(
                field
                    + " must be a string or null"
            );
        }

        try {
            return Enum.valueOf(
                enumType,
                node.textValue()
            );
        } catch (IllegalArgumentException exception) {
            throw invalid(
                field + " contains an unknown enum value"
            );
        }
    }

    private static ProblemClassificationOutputException invalid(
        String message
    ) {
        return new ProblemClassificationOutputException(
            message
        );
    }
}
