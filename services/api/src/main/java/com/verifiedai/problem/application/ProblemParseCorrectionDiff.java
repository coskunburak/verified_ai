package com.verifiedai.problem.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class ProblemParseCorrectionDiff {
    private final ObjectMapper objectMapper = new ObjectMapper();

    Set<Category> categories(String parentProblemJson, String correctedProblemJson) {
        try {
            JsonNode parent = objectMapper.readTree(parentProblemJson);
            JsonNode corrected = objectMapper.readTree(correctedProblemJson);
            LinkedHashSet<Category> categories = new LinkedHashSet<>();
            addIfChanged(categories, Category.PROBLEM_TYPE, parent.path("problemType"), corrected.path("problemType"));
            addIfChanged(categories, Category.TASK_TYPE, parent.path("taskType"), corrected.path("taskType"));
            addIfChanged(categories, Category.EXPRESSION, parent.path("expressions"), corrected.path("expressions"));
            addIfChanged(categories, Category.VARIABLES, parent.path("variables"), corrected.path("variables"));
            addIfChanged(categories, Category.CONSTRAINTS, parent.path("constraints"), corrected.path("constraints"));
            addIfChanged(categories, Category.ASSUMPTIONS, parent.path("assumptions"), corrected.path("assumptions"));
            return categories;
        } catch (Exception exception) {
            throw new IllegalStateException("Stored parse JSON cannot be diffed", exception);
        }
    }

    String toJson(Set<Category> categories) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode fields = root.putArray("fields");
            categories.forEach(category -> fields.add(category.name()));
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("Correction diff cannot be serialized", exception);
        }
    }

    List<String> fromJson(String correctedFieldsJson) {
        if (correctedFieldsJson == null || correctedFieldsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode fields = objectMapper.readTree(correctedFieldsJson).path("fields");
            if (!fields.isArray()) {
                return List.of();
            }
            java.util.ArrayList<String> values = new java.util.ArrayList<>();
            fields.forEach(field -> {
                if (field.isTextual()) {
                    values.add(field.asText());
                }
            });
            return List.copyOf(values);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static void addIfChanged(Set<Category> categories, Category category, JsonNode parent, JsonNode corrected) {
        if (!parent.equals(corrected)) {
            categories.add(category);
        }
    }

    enum Category {
        EXPRESSION,
        VARIABLES,
        CONSTRAINTS,
        ASSUMPTIONS,
        TASK_TYPE,
        PROBLEM_TYPE
    }
}
