package com.verifiedai.problem.application.recognition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.problem.application.asset.ProblemAssetQualitySignalResult;
import com.verifiedai.problem.domain.model.recognition.RecognitionBlockKind;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemAssetDerivativeJpaEntity;
import com.verifiedai.problem.infrastructure.recognition.ProblemRecognitionProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class RecognitionEvidenceNormalizer {
    private static final String COORDINATE_SPACE = "INPUT_ASSET_NORMALIZED";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProblemRecognitionProperties properties;

    RecognitionEvidenceNormalizer(ProblemRecognitionProperties properties) {
        this.properties = properties;
    }

    NormalizedRecognitionEvidence normalize(
        String rawOutputJson,
        String expectedSchemaVersion,
        ProblemAssetDerivativeJpaEntity inputDerivative,
        List<ProblemAssetQualitySignalResult> qualitySignals
    ) {
        JsonNode raw = parse(rawOutputJson);
        requireObject(raw, "root");
        requireText(raw, "schemaVersion", expectedSchemaVersion);
        JsonNode rawBlocks = required(raw, "blocks");
        if (!rawBlocks.isArray()) {
            throw invalid("blocks must be an array");
        }
        if (rawBlocks.size() > properties.maxBlocks()) {
            throw invalid("recognition block count exceeds limit");
        }

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("schemaVersion", expectedSchemaVersion);
        ObjectNode coordinateSpace = normalized.putObject("coordinateSpace");
        coordinateSpace.put("space", COORDINATE_SPACE);
        coordinateSpace.put("version", "input-asset-normalized-v1");
        coordinateSpace.put("inputAssetId", inputDerivative.id().toString());
        coordinateSpace.put("width", inputDerivative.width());
        coordinateSpace.put("height", inputDerivative.height());

        ArrayNode blocks = normalized.putArray("blocks");
        List<RecognitionBlockResult> blockResults = new ArrayList<>();
        int totalTextChars = 0;
        boolean reviewRequired = raw.path("reviewRequired").asBoolean(false);
        for (int index = 0; index < rawBlocks.size(); index++) {
            JsonNode block = rawBlocks.get(index);
            requireObject(block, "block");
            String id = optionalText(block, "id", "block-" + (index + 1));
            String kind = enumText(block, "kind");
            String text = optionalText(block, "text", "");
            if (text.length() > properties.maxTextCharsPerBlock()) {
                throw invalid("recognition block text exceeds per-block limit");
            }
            totalTextChars += text.length();
            if (totalTextChars > properties.maxTotalTextChars()) {
                throw invalid("recognition output text exceeds total limit");
            }

            JsonNode box = required(block, "boundingBox");
            requireObject(box, "boundingBox");
            BigDecimal x = coordinate(box, "x");
            BigDecimal y = coordinate(box, "y");
            BigDecimal width = coordinate(box, "width");
            BigDecimal height = coordinate(box, "height");
            validateBox(x, y, width, height);
            int readingOrder = nonNegativeInt(block, "readingOrder");

            JsonNode confidence = block.path("confidence");
            BigDecimal normalizedConfidence = null;
            String confidenceStatus = "UNKNOWN";
            if (!confidence.isMissingNode() && !confidence.isNull()) {
                requireObject(confidence, "confidence");
                if (confidence.has("normalized") && !confidence.get("normalized").isNull()) {
                    normalizedConfidence = probability(confidence, "normalized");
                    confidenceStatus = normalizedConfidence.compareTo(BigDecimal.valueOf(0.75)) < 0 ? "LOW" : "KNOWN";
                }
            }
            List<String> uncertainty = stringArray(block.path("uncertainty"), "uncertainty", 16, 96);
            List<String> layoutHints = stringArray(block.path("layoutHints"), "layoutHints", 16, 64);
            if ("UNKNOWN".equals(confidenceStatus)
                || "LOW".equals(confidenceStatus)
                || !uncertainty.isEmpty()
                || RecognitionBlockKind.UNKNOWN.name().equals(kind)
                || RecognitionBlockKind.UNREADABLE.name().equals(kind)) {
                reviewRequired = true;
            }

            ObjectNode normalizedBlock = blocks.addObject();
            normalizedBlock.put("id", id);
            normalizedBlock.put("kind", kind);
            normalizedBlock.put("text", text);
            ObjectNode normalizedBox = normalizedBlock.putObject("boundingBox");
            normalizedBox.put("x", x);
            normalizedBox.put("y", y);
            normalizedBox.put("width", width);
            normalizedBox.put("height", height);
            normalizedBlock.put("readingOrder", readingOrder);
            ObjectNode normalizedConfidenceNode = normalizedBlock.putObject("confidence");
            normalizedConfidenceNode.put("status", confidenceStatus);
            if (normalizedConfidence == null) {
                normalizedConfidenceNode.putNull("normalized");
            } else {
                normalizedConfidenceNode.put("normalized", normalizedConfidence);
            }
            if (confidence.has("raw")) {
                normalizedConfidenceNode.set("rawProviderConfidence", confidence.get("raw"));
            }
            ArrayNode uncertaintyNode = normalizedBlock.putArray("uncertainty");
            uncertainty.forEach(uncertaintyNode::add);
            ArrayNode layoutNode = normalizedBlock.putArray("layoutHints");
            layoutHints.forEach(layoutNode::add);

            blockResults.add(new RecognitionBlockResult(
                id,
                kind,
                text,
                new RecognitionBoundingBoxResult(x, y, width, height),
                readingOrder,
                confidenceStatus,
                normalizedConfidence,
                uncertainty,
                layoutHints
            ));
        }

        List<String> documentUncertainty = stringArray(raw.path("documentUncertainty"), "documentUncertainty", 32, 96);
        if (!documentUncertainty.isEmpty()) {
            reviewRequired = true;
        }
        ArrayNode documentUncertaintyNode = normalized.putArray("documentUncertainty");
        documentUncertainty.forEach(documentUncertaintyNode::add);

        ObjectNode upstreamQuality = upstreamQualityEvidence(qualitySignals);
        normalized.set("upstreamQualityEvidence", upstreamQuality.get("qualitySignals"));
        if (qualitySignals.stream().anyMatch(signal -> !"PASS".equals(signal.severity()))) {
            reviewRequired = true;
        }
        normalized.put("reviewRequired", reviewRequired);
        normalized.put("canonicalProblemCreated", false);

        try {
            return new NormalizedRecognitionEvidence(
                objectMapper.writeValueAsString(raw),
                objectMapper.writeValueAsString(normalized),
                objectMapper.writeValueAsString(upstreamQuality),
                reviewRequired,
                blockResults.size(),
                blockResults
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize normalized recognition evidence", exception);
        }
    }

    List<RecognitionBlockResult> blocksFromNormalized(String normalizedEvidenceJson) {
        JsonNode root = parse(normalizedEvidenceJson);
        JsonNode blocks = root.path("blocks");
        if (!blocks.isArray()) {
            return List.of();
        }
        List<RecognitionBlockResult> results = new ArrayList<>();
        for (JsonNode block : blocks) {
            JsonNode box = block.path("boundingBox");
            JsonNode confidence = block.path("confidence");
            results.add(new RecognitionBlockResult(
                block.path("id").asText(),
                block.path("kind").asText(),
                block.path("text").asText(),
                new RecognitionBoundingBoxResult(
                    decimal(box.path("x").decimalValue()),
                    decimal(box.path("y").decimalValue()),
                    decimal(box.path("width").decimalValue()),
                    decimal(box.path("height").decimalValue())
                ),
                block.path("readingOrder").asInt(),
                confidence.path("status").asText("UNKNOWN"),
                confidence.hasNonNull("normalized") ? decimal(confidence.path("normalized").decimalValue()) : null,
                stringArray(block.path("uncertainty"), "uncertainty", 16, 96),
                stringArray(block.path("layoutHints"), "layoutHints", 16, 64)
            ));
        }
        return results;
    }

    private JsonNode parse(String rawOutputJson) {
        try {
            return objectMapper.readTree(rawOutputJson);
        } catch (JsonProcessingException exception) {
            throw invalid("recognition provider output is not valid JSON");
        }
    }

    private ObjectNode upstreamQualityEvidence(List<ProblemAssetQualitySignalResult> qualitySignals) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode signals = root.putArray("qualitySignals");
        for (ProblemAssetQualitySignalResult signal : qualitySignals) {
            ObjectNode node = signals.addObject();
            node.put("signalType", signal.signalType());
            node.put("severity", signal.severity());
            node.put("score", signal.score());
            node.put("threshold", signal.threshold());
            node.put("policyVersion", signal.policyVersion());
            node.put("messageCode", signal.messageCode());
        }
        return root;
    }

    private void validateBox(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height) {
        if (width.compareTo(BigDecimal.ZERO) < 0 || height.compareTo(BigDecimal.ZERO) < 0) {
            throw invalid("recognition bounding box dimensions cannot be negative");
        }
        BigDecimal tolerance = BigDecimal.valueOf(properties.coordinateTolerance());
        if (x.compareTo(BigDecimal.ZERO) < 0
            || y.compareTo(BigDecimal.ZERO) < 0
            || x.compareTo(BigDecimal.ONE) > 0
            || y.compareTo(BigDecimal.ONE) > 0
            || x.add(width).subtract(BigDecimal.ONE).compareTo(tolerance) > 0
            || y.add(height).subtract(BigDecimal.ONE).compareTo(tolerance) > 0) {
            throw invalid("recognition bounding box is outside normalized coordinate space");
        }
    }

    private String enumText(JsonNode node, String field) {
        String value = required(node, field).asText(null);
        try {
            return RecognitionBlockKind.valueOf(value.toUpperCase(Locale.ROOT)).name();
        } catch (RuntimeException exception) {
            throw invalid("unsupported recognition block kind");
        }
    }

    private BigDecimal coordinate(JsonNode node, String field) {
        return decimal(requiredNumber(node, field).decimalValue());
    }

    private BigDecimal probability(JsonNode node, String field) {
        BigDecimal value = decimal(requiredNumber(node, field).decimalValue());
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw invalid("recognition confidence is outside [0,1]");
        }
        return value;
    }

    private int nonNegativeInt(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.canConvertToInt() || value.asInt() < 0) {
            throw invalid(field + " must be a non-negative integer");
        }
        return value.asInt();
    }

    private JsonNode requiredNumber(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isNumber()) {
            throw invalid(field + " must be numeric");
        }
        return value;
    }

    private JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw invalid(field + " is required");
        }
        return value;
    }

    private void requireObject(JsonNode node, String name) {
        if (!node.isObject()) {
            throw invalid(name + " must be an object");
        }
    }

    private void requireText(JsonNode node, String field, String expected) {
        JsonNode value = required(node, field);
        if (!value.isTextual() || !expected.equals(value.asText())) {
            throw invalid(field + " is not the expected schema version");
        }
    }

    private String optionalText(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (!value.isTextual()) {
            throw invalid(field + " must be text");
        }
        return value.asText();
    }

    private List<String> stringArray(JsonNode node, String field, int maxItems, int maxLength) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray() || node.size() > maxItems) {
            throw invalid(field + " must be a bounded array");
        }
        List<String> values = new ArrayList<>();
        Iterator<JsonNode> iterator = node.elements();
        while (iterator.hasNext()) {
            JsonNode value = iterator.next();
            if (!value.isTextual() || value.asText().length() > maxLength) {
                throw invalid(field + " contains an invalid value");
            }
            values.add(value.asText());
        }
        return values;
    }

    private static BigDecimal decimal(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private static RecognitionValidationException invalid(String message) {
        return new RecognitionValidationException(message);
    }
}
