package com.verifiedai.problem.application.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.problem.infrastructure.persistence.entity.RecognitionEvidenceJpaEntity;
import org.springframework.stereotype.Component;

@Component
class ProblemParseDocumentValidator {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProblemParseNormalizer normalizer;

    ProblemParseDocumentValidator(ProblemParseNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    NormalizedProblemParse validateUserCorrection(
        String correctedProblemJson,
        String expectedSchemaVersion,
        RecognitionEvidenceJpaEntity recognitionEvidence
    ) {
        try {
            JsonNode parsed = objectMapper.readTree(correctedProblemJson);
            if (!parsed.isObject()) {
                throw new ProblemParseValidationException(
                    ProblemParseValidationFailure.SCHEMA,
                    "corrected problem must be a JSON object"
                );
            }
            ObjectNode sanitized = ((ObjectNode) parsed).deepCopy();
            sanitized.put("schemaVersion", expectedSchemaVersion);
            sanitized.put("supportStatus", "SUPPORTED");
            sanitized.putNull("unsupportedReason");
            sanitized.put("reviewRequired", false);
            JsonNode uncertainty = sanitized.path("uncertainty");
            if (uncertainty.isObject()) {
                ((ObjectNode) uncertainty).put("reviewRequired", false);
            }
            return normalizer.normalize(
                objectMapper.writeValueAsString(sanitized),
                expectedSchemaVersion,
                recognitionEvidence
            );
        } catch (ProblemParseValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProblemParseValidationException(
                ProblemParseValidationFailure.SCHEMA,
                "corrected problem is not valid JSON"
            );
        }
    }
}
