package com.verifiedai.problem.application.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ProblemParseCorrectionRequestHash {
    private final ObjectMapper objectMapper = new ObjectMapper();

    String hash(
        UUID problemSessionId,
        UUID baseParseId,
        int baseRevision,
        String correctionReason,
        String correctionSchemaVersion,
        String correctedProblemJson
    ) {
        try {
            String canonicalProblem = canonicalJson(objectMapper.readTree(correctedProblemJson));
            String material = String.join(
                "\n",
                problemSessionId.toString(),
                baseParseId.toString(),
                Integer.toString(baseRevision),
                correctionReason == null ? "" : correctionReason,
                correctionSchemaVersion,
                canonicalProblem
            );
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new ProblemParseValidationException(
                ProblemParseValidationFailure.SCHEMA,
                "corrected problem is not valid JSON"
            );
        }
    }

    private String canonicalJson(JsonNode node) throws Exception {
        if (node.isObject()) {
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            StringBuilder builder = new StringBuilder("{");
            for (int index = 0; index < names.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                String name = names.get(index);
                builder
                    .append(objectMapper.writeValueAsString(name))
                    .append(':')
                    .append(canonicalJson(node.get(name)));
            }
            return builder.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder builder = new StringBuilder("[");
            for (int index = 0; index < node.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(canonicalJson(node.get(index)));
            }
            return builder.append(']').toString();
        }
        return objectMapper.writeValueAsString(node);
    }
}
