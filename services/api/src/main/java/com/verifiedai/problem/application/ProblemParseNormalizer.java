package com.verifiedai.problem.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.curriculum.application.CurriculumTaxonomyCatalog;
import com.verifiedai.problem.domain.model.ProblemParseSupportStatus;
import com.verifiedai.problem.domain.model.ProblemParseUnsupportedReason;
import com.verifiedai.problem.infrastructure.persistence.RecognitionEvidenceJpaEntity;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class ProblemParseNormalizer {
    private static final int MAX_EXPRESSIONS = 8;
    private static final int MAX_VARIABLES = 16;
    private static final int MAX_CONSTRAINTS = 16;
    private static final int MAX_ASSUMPTIONS = 16;
    private static final int MAX_SOURCE_REFS = 32;
    private static final int MAX_STRING = 2000;
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Za-z][A-Za-z0-9_]*\\b");
    private static final Set<String> IGNORED_IDENTIFIERS = Set.of(
        "e",
        "pi",
        "sin",
        "cos",
        "tan",
        "sec",
        "csc",
        "cot",
        "log",
        "ln",
        "sqrt",
        "lim",
        "int",
        "dx",
        "dy",
        "d"
    );
    private static final Set<String> ROOT_FIELDS = Set.of(
        "schemaVersion",
        "supportStatus",
        "unsupportedReason",
        "subjectId",
        "topicId",
        "taskType",
        "problemType",
        "expressions",
        "variables",
        "constraints",
        "assumptions",
        "uncertainty",
        "sourceEvidenceRefs",
        "visualQualityRisks",
        "reviewRequired"
    );
    private static final Set<String> EXPRESSION_FIELDS = Set.of(
        "id",
        "role",
        "sourceText",
        "normalizedText",
        "displayLatex",
        "relation",
        "sourceBlockIds"
    );
    private static final Set<String> VARIABLE_FIELDS = Set.of("symbol", "role", "sourceBlockIds");
    private static final Set<String> CONSTRAINT_FIELDS = Set.of("id", "sourceText", "normalizedText", "variables", "explicit", "sourceBlockIds");
    private static final Set<String> ASSUMPTION_FIELDS = Set.of("id", "text", "explicit", "sourceBlockIds");
    private static final Set<String> UNCERTAINTY_FIELDS = Set.of("recognition", "parse", "reviewRequired");
    private static final Set<String> SOURCE_REF_FIELDS = Set.of("blockId", "fieldPath");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CurriculumTaxonomyCatalog taxonomyCatalog;

    ProblemParseNormalizer(CurriculumTaxonomyCatalog taxonomyCatalog) {
        this.taxonomyCatalog = taxonomyCatalog;
    }

    NormalizedProblemParse normalize(
        String rawOutputJson,
        String expectedSchemaVersion,
        RecognitionEvidenceJpaEntity recognitionEvidence
    ) {
        JsonNode raw = parse(rawOutputJson);
        requireObject(raw, "root");
        rejectUnknown(raw, ROOT_FIELDS, "root");
        requireText(raw, "schemaVersion", expectedSchemaVersion);

        Set<String> blockIds = recognitionBlockIds(recognitionEvidence.normalizedEvidenceJson());
        RecognitionContext recognitionContext = recognitionContext(recognitionEvidence.normalizedEvidenceJson());
        List<VisualQualityRisk> visualQualityRisks = visualQualityRisks(recognitionEvidence.upstreamQualityEvidenceJson());

        ProblemParseSupportStatus rawSupportStatus = enumValue(
            requiredText(raw, "supportStatus", 32),
            ProblemParseSupportStatus.class,
            "supportStatus"
        );
        String unsupportedReason = optionalUnsupportedReason(raw);
        String subjectId = nullableText(raw, "subjectId", 64);
        String topicId = nullableText(raw, "topicId", 96);
        String taskType = nullableText(raw, "taskType", 64);
        String problemType = nullableText(raw, "problemType", 64);

        List<ExpressionInfo> expressions = expressions(raw.path("expressions"), blockIds);
        List<VariableInfo> variables = variables(raw.path("variables"), blockIds);
        List<ConstraintInfo> constraints = constraints(raw.path("constraints"), blockIds);
        List<AssumptionInfo> assumptions = assumptions(raw.path("assumptions"), blockIds);
        UncertaintyInfo uncertainty = uncertainty(raw.path("uncertainty"), recognitionContext.uncertainty());
        List<SourceRefInfo> sourceRefs = sourceEvidenceRefs(raw.path("sourceEvidenceRefs"), blockIds);
        requireArray(raw.path("visualQualityRisks"), "visualQualityRisks", 32);
        JsonNode rawReviewRequired = required(raw, "reviewRequired");
        if (!rawReviewRequired.isBoolean()) {
            throw schema("reviewRequired must be boolean");
        }

        validateSupportSemantics(
            rawSupportStatus,
            unsupportedReason,
            subjectId,
            topicId,
            taskType,
            problemType,
            expressions,
            variables,
            constraints,
            sourceRefs
        );

        boolean reviewRequired = rawReviewRequired.asBoolean()
            || recognitionContext.reviewRequired()
            || uncertainty.reviewRequired()
            || !uncertainty.recognition().isEmpty()
            || !uncertainty.parse().isEmpty()
            || visualQualityRisks.stream().anyMatch(risk -> !"PASS".equals(risk.severity()))
            || rawSupportStatus == ProblemParseSupportStatus.REVIEW_REQUIRED;
        ProblemParseSupportStatus supportStatus = rawSupportStatus == ProblemParseSupportStatus.SUPPORTED && reviewRequired
            ? ProblemParseSupportStatus.REVIEW_REQUIRED
            : rawSupportStatus;
        if (supportStatus == ProblemParseSupportStatus.UNSUPPORTED) {
            reviewRequired = false;
        }

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("schemaVersion", expectedSchemaVersion);
        normalized.put("supportStatus", supportStatus.name());
        putNullable(normalized, "unsupportedReason", unsupportedReason);
        putNullable(normalized, "subjectId", subjectId);
        putNullable(normalized, "topicId", topicId);
        putNullable(normalized, "taskType", taskType);
        putNullable(normalized, "problemType", problemType);
        normalized.set("expressions", expressionArray(expressions));
        normalized.set("variables", variableArray(variables));
        normalized.set("constraints", constraintArray(constraints));
        normalized.set("assumptions", assumptionArray(assumptions));
        normalized.set("uncertainty", uncertaintyNode(uncertainty, reviewRequired));
        normalized.set("sourceEvidenceRefs", sourceRefArray(sourceRefs));
        normalized.set("visualQualityRisks", visualQualityRiskArray(visualQualityRisks));
        normalized.put("reviewRequired", reviewRequired);

        try {
            return new NormalizedProblemParse(
                objectMapper.writeValueAsString(raw),
                objectMapper.writeValueAsString(normalized),
                supportStatus.name(),
                unsupportedReason,
                reviewRequired
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize normalized problem parse", exception);
        }
    }

    private void validateSupportSemantics(
        ProblemParseSupportStatus supportStatus,
        String unsupportedReason,
        String subjectId,
        String topicId,
        String taskType,
        String problemType,
        List<ExpressionInfo> expressions,
        List<VariableInfo> variables,
        List<ConstraintInfo> constraints,
        List<SourceRefInfo> sourceRefs
    ) {
        if (supportStatus == ProblemParseSupportStatus.UNSUPPORTED) {
            if (unsupportedReason == null) {
                throw semantic("unsupportedReason is required for unsupported parses");
            }
            if (subjectId != null && !taxonomyCatalog.isActiveSubject(subjectId)) {
                throw semantic("subject is not active");
            }
            if (topicId != null && !taxonomyCatalog.isActiveTopic(topicId)) {
                throw semantic("topic is not active");
            }
            return;
        }
        if (unsupportedReason != null) {
            throw semantic("unsupportedReason is only valid for unsupported parses");
        }
        if (subjectId == null || !taxonomyCatalog.isActiveSubject(subjectId)) {
            throw semantic("subject is required and must be active");
        }
        if (topicId == null || !taxonomyCatalog.isActiveTopic(topicId)) {
            throw semantic("topic is required and must be active");
        }
        if (taskType == null || problemType == null) {
            throw semantic("taskType and problemType are required");
        }
        if (!taskSupported(taskType, problemType, topicId)) {
            throw semantic("task/problem type is outside supported parser scope");
        }
        if (expressions.isEmpty()) {
            throw semantic("supported parser outcome requires expression evidence");
        }
        if (sourceRefs.isEmpty()) {
            throw semantic("supported parser outcome requires source evidence references");
        }
        validateVariables(expressions, variables, constraints);
    }

    private void validateVariables(
        List<ExpressionInfo> expressions,
        List<VariableInfo> variables,
        List<ConstraintInfo> constraints
    ) {
        Set<String> declared = new LinkedHashSet<>();
        for (VariableInfo variable : variables) {
            declared.add(variable.symbol());
        }
        Set<String> referenced = new LinkedHashSet<>();
        for (ExpressionInfo expression : expressions) {
            referenced.addAll(variablesInText(expression.normalizedText()));
        }
        for (ConstraintInfo constraint : constraints) {
            referenced.addAll(constraint.variables());
            referenced.addAll(variablesInText(constraint.normalizedText()));
        }
        if (!declared.equals(referenced)) {
            throw semantic("variables do not match expression and constraint references");
        }
        for (ConstraintInfo constraint : constraints) {
            if (!declared.containsAll(constraint.variables())) {
                throw semantic("constraint references an undeclared variable");
            }
        }
    }

    private static boolean taskSupported(String taskType, String problemType, String topicId) {
        return switch (taskType) {
            case "EVALUATE" -> ("ARITHMETIC_EXPRESSION".equals(problemType) && "MATH.ARITHMETIC".equals(topicId))
                || ("ALGEBRAIC_EXPRESSION".equals(problemType) && "MATH.ALGEBRA".equals(topicId));
            case "SIMPLIFY" -> ("ARITHMETIC_EXPRESSION".equals(problemType) && "MATH.ARITHMETIC".equals(topicId))
                || ("ALGEBRAIC_EXPRESSION".equals(problemType) && "MATH.ALGEBRA".equals(topicId));
            case "SOLVE_EQUATION" -> "EQUATION".equals(problemType) && "MATH.EQUATIONS".equals(topicId);
            case "SOLVE_INEQUALITY" -> "INEQUALITY".equals(problemType) && "MATH.EQUATIONS".equals(topicId);
            case "FIND_FUNCTION_VALUE" -> "FUNCTION".equals(problemType) && "MATH.FUNCTIONS".equals(topicId);
            case "FIND_LIMIT" -> "LIMIT".equals(problemType) && "MATH.CALCULUS.LIMITS".equals(topicId);
            case "DIFFERENTIATE" -> "DERIVATIVE".equals(problemType) && "MATH.CALCULUS.DIFFERENTIATION".equals(topicId);
            case "INTEGRATE" -> "INTEGRAL".equals(problemType) && "MATH.CALCULUS.INTEGRATION".equals(topicId);
            default -> false;
        };
    }

    private List<ExpressionInfo> expressions(JsonNode node, Set<String> blockIds) {
        requireArray(node, "expressions", MAX_EXPRESSIONS);
        List<ExpressionInfo> values = new ArrayList<>();
        for (JsonNode expression : node) {
            requireObject(expression, "expression");
            rejectUnknown(expression, EXPRESSION_FIELDS, "expression");
            String id = requiredText(expression, "id", 64);
            String role = enumText(expression, "role", Set.of("PRIMARY", "GIVEN", "TARGET", "CONSTRAINT"));
            String sourceText = requiredText(expression, "sourceText", MAX_STRING);
            String normalizedText = requiredText(expression, "normalizedText", MAX_STRING);
            String displayLatex = nullableText(expression, "displayLatex", MAX_STRING);
            String relation = nullableEnumText(
                expression,
                "relation",
                Set.of("EQUALS", "LESS_THAN", "LESS_THAN_OR_EQUAL", "GREATER_THAN", "GREATER_THAN_OR_EQUAL")
            );
            List<String> sourceBlockIds = stringArray(expression.path("sourceBlockIds"), "sourceBlockIds", 16, 64);
            requireKnownBlocks(sourceBlockIds, blockIds);
            values.add(new ExpressionInfo(id, role, sourceText, normalizedText, displayLatex, relation, sourceBlockIds));
        }
        return values;
    }

    private List<VariableInfo> variables(JsonNode node, Set<String> blockIds) {
        requireArray(node, "variables", MAX_VARIABLES);
        Map<String, VariableInfo> values = new java.util.LinkedHashMap<>();
        for (JsonNode variable : node) {
            requireObject(variable, "variable");
            rejectUnknown(variable, VARIABLE_FIELDS, "variable");
            String symbol = requiredText(variable, "symbol", 16);
            if (!symbol.matches("^[A-Za-z][A-Za-z0-9_]{0,15}$")) {
                throw schema("variable symbol is invalid");
            }
            String role = enumText(variable, "role", Set.of("VARIABLE", "PARAMETER", "UNKNOWN"));
            List<String> sourceBlockIds = stringArray(variable.path("sourceBlockIds"), "sourceBlockIds", 16, 64);
            requireKnownBlocks(sourceBlockIds, blockIds);
            values.putIfAbsent(symbol, new VariableInfo(symbol, role, sourceBlockIds));
        }
        return new ArrayList<>(values.values());
    }

    private List<ConstraintInfo> constraints(JsonNode node, Set<String> blockIds) {
        requireArray(node, "constraints", MAX_CONSTRAINTS);
        List<ConstraintInfo> values = new ArrayList<>();
        for (JsonNode constraint : node) {
            requireObject(constraint, "constraint");
            rejectUnknown(constraint, CONSTRAINT_FIELDS, "constraint");
            String id = requiredText(constraint, "id", 64);
            String sourceText = requiredText(constraint, "sourceText", MAX_STRING);
            String normalizedText = requiredText(constraint, "normalizedText", MAX_STRING);
            if (!required(constraint, "explicit").isBoolean() || !constraint.path("explicit").asBoolean()) {
                throw schema("constraint explicit must be true");
            }
            List<String> variables = stringArray(constraint.path("variables"), "variables", 16, 16);
            for (String variable : variables) {
                if (!variable.matches("^[A-Za-z][A-Za-z0-9_]{0,15}$")) {
                    throw schema("constraint variable is invalid");
                }
            }
            List<String> sourceBlockIds = stringArray(constraint.path("sourceBlockIds"), "sourceBlockIds", 16, 64);
            requireKnownBlocks(sourceBlockIds, blockIds);
            values.add(new ConstraintInfo(id, sourceText, normalizedText, variables, true, sourceBlockIds));
        }
        return values;
    }

    private List<AssumptionInfo> assumptions(JsonNode node, Set<String> blockIds) {
        requireArray(node, "assumptions", MAX_ASSUMPTIONS);
        List<AssumptionInfo> values = new ArrayList<>();
        for (JsonNode assumption : node) {
            requireObject(assumption, "assumption");
            rejectUnknown(assumption, ASSUMPTION_FIELDS, "assumption");
            String id = requiredText(assumption, "id", 64);
            String text = requiredText(assumption, "text", 1000);
            JsonNode explicit = required(assumption, "explicit");
            if (!explicit.isBoolean()) {
                throw schema("assumption explicit must be boolean");
            }
            List<String> sourceBlockIds = stringArray(assumption.path("sourceBlockIds"), "sourceBlockIds", 16, 64);
            requireKnownBlocks(sourceBlockIds, blockIds);
            values.add(new AssumptionInfo(id, text, explicit.asBoolean(), sourceBlockIds));
        }
        return values;
    }

    private UncertaintyInfo uncertainty(JsonNode node, List<String> upstreamRecognitionUncertainty) {
        requireObject(node, "uncertainty");
        rejectUnknown(node, UNCERTAINTY_FIELDS, "uncertainty");
        LinkedHashSet<String> recognition = new LinkedHashSet<>(upstreamRecognitionUncertainty);
        recognition.addAll(stringArray(node.path("recognition"), "uncertainty.recognition", 64, 128));
        List<String> parse = stringArray(node.path("parse"), "uncertainty.parse", 64, 128);
        JsonNode reviewRequired = required(node, "reviewRequired");
        if (!reviewRequired.isBoolean()) {
            throw schema("uncertainty.reviewRequired must be boolean");
        }
        return new UncertaintyInfo(new ArrayList<>(recognition), parse, reviewRequired.asBoolean());
    }

    private List<SourceRefInfo> sourceEvidenceRefs(JsonNode node, Set<String> blockIds) {
        requireArray(node, "sourceEvidenceRefs", MAX_SOURCE_REFS);
        List<SourceRefInfo> values = new ArrayList<>();
        for (JsonNode sourceRef : node) {
            requireObject(sourceRef, "sourceEvidenceRef");
            rejectUnknown(sourceRef, SOURCE_REF_FIELDS, "sourceEvidenceRef");
            String blockId = requiredText(sourceRef, "blockId", 64);
            String fieldPath = requiredText(sourceRef, "fieldPath", 160);
            requireKnownBlocks(List.of(blockId), blockIds);
            values.add(new SourceRefInfo(blockId, fieldPath));
        }
        return values;
    }

    private Set<String> recognitionBlockIds(String normalizedRecognitionEvidenceJson) {
        JsonNode recognition = parse(normalizedRecognitionEvidenceJson);
        Set<String> ids = new LinkedHashSet<>();
        JsonNode blocks = recognition.path("blocks");
        if (blocks.isArray()) {
            for (JsonNode block : blocks) {
                if (block.path("id").isTextual()) {
                    ids.add(block.path("id").asText());
                }
            }
        }
        return ids;
    }

    private RecognitionContext recognitionContext(String normalizedRecognitionEvidenceJson) {
        JsonNode recognition = parse(normalizedRecognitionEvidenceJson);
        LinkedHashSet<String> uncertainty = new LinkedHashSet<>();
        JsonNode documentUncertainty = recognition.path("documentUncertainty");
        if (documentUncertainty.isArray()) {
            for (JsonNode value : documentUncertainty) {
                if (value.isTextual()) {
                    uncertainty.add(value.asText());
                }
            }
        }
        JsonNode blocks = recognition.path("blocks");
        if (blocks.isArray()) {
            for (JsonNode block : blocks) {
                String blockId = block.path("id").asText("unknown-block");
                JsonNode blockUncertainty = block.path("uncertainty");
                if (blockUncertainty.isArray()) {
                    for (JsonNode value : blockUncertainty) {
                        if (value.isTextual()) {
                            uncertainty.add(blockId + ":" + value.asText());
                        }
                    }
                }
                String confidenceStatus = block.path("confidence").path("status").asText("UNKNOWN");
                if ("UNKNOWN".equals(confidenceStatus) || "LOW".equals(confidenceStatus)) {
                    uncertainty.add(blockId + ":confidence_" + confidenceStatus.toLowerCase(Locale.ROOT));
                }
            }
        }
        return new RecognitionContext(new ArrayList<>(uncertainty), recognition.path("reviewRequired").asBoolean(false));
    }

    private List<VisualQualityRisk> visualQualityRisks(String upstreamQualityEvidenceJson) {
        JsonNode upstream = parse(upstreamQualityEvidenceJson);
        JsonNode signals = upstream.path("qualitySignals");
        if (!signals.isArray()) {
            return List.of();
        }
        List<VisualQualityRisk> risks = new ArrayList<>();
        for (JsonNode signal : signals) {
            String signalType = signal.path("signalType").asText(null);
            String severity = signal.path("severity").asText(null);
            if (signalType != null && severity != null) {
                risks.add(new VisualQualityRisk(signalType, severity, signal.path("messageCode").asText(null)));
            }
        }
        return risks;
    }

    private ArrayNode expressionArray(List<ExpressionInfo> expressions) {
        ArrayNode array = objectMapper.createArrayNode();
        for (ExpressionInfo expression : expressions) {
            ObjectNode node = array.addObject();
            node.put("id", expression.id());
            node.put("role", expression.role());
            node.put("sourceText", expression.sourceText());
            node.put("normalizedText", expression.normalizedText());
            putNullable(node, "displayLatex", expression.displayLatex());
            putNullable(node, "relation", expression.relation());
            node.set("sourceBlockIds", stringArrayNode(expression.sourceBlockIds()));
        }
        return array;
    }

    private ArrayNode variableArray(List<VariableInfo> variables) {
        ArrayNode array = objectMapper.createArrayNode();
        for (VariableInfo variable : variables) {
            ObjectNode node = array.addObject();
            node.put("symbol", variable.symbol());
            node.put("role", variable.role());
            node.set("sourceBlockIds", stringArrayNode(variable.sourceBlockIds()));
        }
        return array;
    }

    private ArrayNode constraintArray(List<ConstraintInfo> constraints) {
        ArrayNode array = objectMapper.createArrayNode();
        for (ConstraintInfo constraint : constraints) {
            ObjectNode node = array.addObject();
            node.put("id", constraint.id());
            node.put("sourceText", constraint.sourceText());
            node.put("normalizedText", constraint.normalizedText());
            node.set("variables", stringArrayNode(constraint.variables()));
            node.put("explicit", constraint.explicit());
            node.set("sourceBlockIds", stringArrayNode(constraint.sourceBlockIds()));
        }
        return array;
    }

    private ArrayNode assumptionArray(List<AssumptionInfo> assumptions) {
        ArrayNode array = objectMapper.createArrayNode();
        for (AssumptionInfo assumption : assumptions) {
            ObjectNode node = array.addObject();
            node.put("id", assumption.id());
            node.put("text", assumption.text());
            node.put("explicit", assumption.explicit());
            node.set("sourceBlockIds", stringArrayNode(assumption.sourceBlockIds()));
        }
        return array;
    }

    private ObjectNode uncertaintyNode(UncertaintyInfo uncertainty, boolean reviewRequired) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("recognition", stringArrayNode(uncertainty.recognition()));
        node.set("parse", stringArrayNode(uncertainty.parse()));
        node.put("reviewRequired", reviewRequired);
        return node;
    }

    private ArrayNode sourceRefArray(List<SourceRefInfo> sourceRefs) {
        ArrayNode array = objectMapper.createArrayNode();
        for (SourceRefInfo sourceRef : sourceRefs) {
            ObjectNode node = array.addObject();
            node.put("blockId", sourceRef.blockId());
            node.put("fieldPath", sourceRef.fieldPath());
        }
        return array;
    }

    private ArrayNode visualQualityRiskArray(List<VisualQualityRisk> risks) {
        ArrayNode array = objectMapper.createArrayNode();
        for (VisualQualityRisk risk : risks) {
            ObjectNode node = array.addObject();
            node.put("signalType", risk.signalType());
            node.put("severity", risk.severity());
            putNullable(node, "messageCode", risk.messageCode());
        }
        return array;
    }

    private ArrayNode stringArrayNode(List<String> values) {
        ArrayNode array = objectMapper.createArrayNode();
        values.forEach(array::add);
        return array;
    }

    private Set<String> variablesInText(String text) {
        Set<String> variables = new LinkedHashSet<>();
        Matcher matcher = IDENTIFIER.matcher(text);
        while (matcher.find()) {
            String symbol = matcher.group();
            int next = matcher.end();
            if (next < text.length() && text.charAt(next) == '(') {
                continue;
            }
            if (!IGNORED_IDENTIFIERS.contains(symbol.toLowerCase(Locale.ROOT))) {
                variables.add(symbol);
            }
        }
        return variables;
    }

    private void requireKnownBlocks(List<String> sourceBlockIds, Set<String> knownBlockIds) {
        if (!knownBlockIds.containsAll(sourceBlockIds)) {
            throw semantic("source evidence references unknown recognition block");
        }
    }

    private JsonNode parse(String rawOutputJson) {
        try {
            return objectMapper.readTree(rawOutputJson);
        } catch (JsonProcessingException exception) {
            throw schema("parser output is not valid JSON");
        }
    }

    private JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw schema(field + " is required");
        }
        return value;
    }

    private String requiredText(JsonNode node, String field, int maxLength) {
        JsonNode value = required(node, field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > maxLength) {
            throw schema(field + " must be bounded text");
        }
        return value.asText();
    }

    private String nullableText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null) {
            throw schema(field + " is required");
        }
        if (value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.asText().length() > maxLength) {
            throw schema(field + " must be null or bounded text");
        }
        return value.asText();
    }

    private void requireText(JsonNode node, String field, String expected) {
        JsonNode value = required(node, field);
        if (!value.isTextual() || !expected.equals(value.asText())) {
            throw schema(field + " is not the expected schema version");
        }
    }

    private String optionalUnsupportedReason(JsonNode node) {
        String reason = nullableText(node, "unsupportedReason", 64);
        if (reason == null) {
            return null;
        }
        enumValue(reason, ProblemParseUnsupportedReason.class, "unsupportedReason");
        return reason;
    }

    private String enumText(JsonNode node, String field, Set<String> allowed) {
        String value = requiredText(node, field, 64);
        if (!allowed.contains(value)) {
            throw schema(field + " has unsupported enum value");
        }
        return value;
    }

    private String nullableEnumText(JsonNode node, String field, Set<String> allowed) {
        String value = nullableText(node, field, 64);
        if (value != null && !allowed.contains(value)) {
            throw schema(field + " has unsupported enum value");
        }
        return value;
    }

    private <T extends Enum<T>> T enumValue(String value, Class<T> enumType, String field) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (RuntimeException exception) {
            throw schema(field + " has unsupported enum value");
        }
    }

    private List<String> stringArray(JsonNode node, String field, int maxItems, int maxLength) {
        requireArray(node, field, maxItems);
        List<String> values = new ArrayList<>();
        Iterator<JsonNode> iterator = node.elements();
        while (iterator.hasNext()) {
            JsonNode value = iterator.next();
            if (!value.isTextual() || value.asText().length() > maxLength) {
                throw schema(field + " contains an invalid value");
            }
            values.add(value.asText());
        }
        return values;
    }

    private void requireArray(JsonNode node, String field, int maxItems) {
        if (!node.isArray() || node.size() > maxItems) {
            throw schema(field + " must be a bounded array");
        }
    }

    private void requireObject(JsonNode node, String name) {
        if (!node.isObject()) {
            throw schema(name + " must be an object");
        }
    }

    private void rejectUnknown(JsonNode node, Set<String> allowed, String name) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            if (!allowed.contains(fields.next())) {
                throw schema(name + " contains an unknown field");
            }
        }
    }

    private static void putNullable(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static ProblemParseValidationException schema(String message) {
        return new ProblemParseValidationException(ProblemParseValidationFailure.SCHEMA, message);
    }

    private static ProblemParseValidationException semantic(String message) {
        return new ProblemParseValidationException(ProblemParseValidationFailure.SEMANTIC, message);
    }

    private record RecognitionContext(List<String> uncertainty, boolean reviewRequired) {
    }

    private record ExpressionInfo(
        String id,
        String role,
        String sourceText,
        String normalizedText,
        String displayLatex,
        String relation,
        List<String> sourceBlockIds
    ) {
    }

    private record VariableInfo(String symbol, String role, List<String> sourceBlockIds) {
    }

    private record ConstraintInfo(
        String id,
        String sourceText,
        String normalizedText,
        List<String> variables,
        boolean explicit,
        List<String> sourceBlockIds
    ) {
    }

    private record AssumptionInfo(String id, String text, boolean explicit, List<String> sourceBlockIds) {
    }

    private record UncertaintyInfo(List<String> recognition, List<String> parse, boolean reviewRequired) {
    }

    private record SourceRefInfo(String blockId, String fieldPath) {
    }

    private record VisualQualityRisk(String signalType, String severity, String messageCode) {
    }
}
