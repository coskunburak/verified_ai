package com.verifiedai.sharedkernel.contracts;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded structural validation of bundled contracts. Local refs only; no remote resolution. */
public final class VersionedJsonContract {
    private static final ObjectMapper MAPPER = new ObjectMapper(JsonFactory.builder().streamReadConstraints(
        StreamReadConstraints.builder().maxNestingDepth(80).maxStringLength(65536).build()).build())
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Map<String, JsonNode> SCHEMAS = new java.util.concurrent.ConcurrentHashMap<>();
    private VersionedJsonContract() { }
    public static JsonNode parse(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > 65536) invalid();
        try { var n = MAPPER.readTree(json); if (n == null) invalid(); return n; }
        catch (java.io.IOException e) { throw new IllegalArgumentException("Invalid versioned JSON"); }
    }
    public static JsonNode validate(String name, String json) {
        if (!Set.of("problem-parse-v2", "canonical-problem-v2", "solution-candidate-v2").contains(name)) invalid();
        var document = parse(json);
        var schema = SCHEMAS.computeIfAbsent(name, key -> {
            try (var stream = VersionedJsonContract.class.getResourceAsStream("/schemas/" + key + ".schema.json")) {
                if (stream == null) throw new IllegalStateException("Bundled contract missing");
                return MAPPER.readTree(stream);
            } catch (java.io.IOException e) { throw new IllegalStateException("Bundled contract unreadable"); }
        });
        check(schema, schema, document, 0);
        return document;
    }
    private static void check(JsonNode root, JsonNode s, JsonNode n, int depth) {
        if (depth > 256) invalid();
        if (s.isBoolean()) { if (!s.booleanValue()) invalid(); return; }
        if (s.has("$ref")) {
            String ref = s.path("$ref").asText(); if (!ref.startsWith("#/$defs/")) invalid();
            var target = root.at(ref.substring(1)); if (target.isMissingNode()) invalid();
            check(root, target, n, depth + 1);
        }
        for (String keyword : List.of("anyOf", "oneOf")) if (s.has(keyword)) {
            int matches = 0;
            for (var option : s.path(keyword)) {
                try { check(root, option, n, depth + 1); matches++; } catch (IllegalArgumentException ignored) { }
            }
            if (matches == 0 || keyword.equals("oneOf") && matches != 1) invalid();
        }
        for (var child : s.path("allOf")) check(root, child, n, depth + 1);
        if (s.has("if")) {
            boolean matches=true;
            try { check(root,s.get("if"),n,depth+1); } catch (IllegalArgumentException e) { matches=false; }
            if (matches && s.has("then")) check(root,s.get("then"),n,depth+1);
            if (!matches && s.has("else")) check(root,s.get("else"),n,depth+1);
        }
        if (s.has("not")) {
            boolean matches=true;
            try { check(root,s.get("not"),n,depth+1); } catch (IllegalArgumentException e) { matches=false; }
            if (matches) invalid();
        }
        if (s.has("const") && !n.equals(s.get("const"))) invalid();
        if (s.has("enum")) { boolean found = false; for (var v : s.path("enum")) found |= v.equals(n); if (!found) invalid(); }
        if (s.has("type")) {
            boolean valid = false;
            var types = s.get("type");
            var list = types.isArray() ? types : MAPPER.createArrayNode().add(types);
            for (var t : list) valid |= switch(t.asText()) {
                case "object" -> n.isObject(); case "array" -> n.isArray(); case "string" -> n.isTextual();
                case "integer" -> n.isIntegralNumber(); case "number" -> n.isNumber();
                case "boolean" -> n.isBoolean(); case "null" -> n.isNull(); default -> false;
            };
            if (!valid) invalid();
        }
        if (n.isObject()) {
            for (var required : s.path("required")) if (!n.has(required.asText())) invalid();
            var fields = n.fields();
            while (fields.hasNext()) {
                var entry = fields.next(); var property = s.path("properties").get(entry.getKey());
                if (property != null) check(root, property, entry.getValue(), depth + 1);
                else if (s.has("additionalProperties") && s.get("additionalProperties").isBoolean()
                    && !s.get("additionalProperties").booleanValue()) invalid();
            }
        }
        if (n.isArray()) {
            if (n.size() < s.path("minItems").asInt(0) || n.size() > s.path("maxItems").asInt(1200)) invalid();
            if (s.path("uniqueItems").asBoolean() && new HashSet<>(iterable(n)).size() != n.size()) invalid();
            if (s.has("items")) for (var item : n) check(root, s.get("items"), item, depth + 1);
        }
        if (n.isTextual()) {
            String text = n.textValue();
            if (text.length() < s.path("minLength").asInt(0) || text.length() > s.path("maxLength").asInt(65536)) invalid();
            if (s.has("pattern") && !java.util.regex.Pattern.compile(s.path("pattern").asText()).matcher(text).find()) invalid();
            if ("uuid".equals(s.path("format").asText()) && !UUID.fromString(text).toString().equals(text)) invalid();
        }
        if (n.isNumber()) {
            if (s.has("minimum") && n.decimalValue().compareTo(s.get("minimum").decimalValue()) < 0) invalid();
            if (s.has("maximum") && n.decimalValue().compareTo(s.get("maximum").decimalValue()) > 0) invalid();
        }
    }
    private static List<JsonNode> iterable(JsonNode n) { var result = new ArrayList<JsonNode>(); n.forEach(result::add); return result; }
    private static void invalid() { throw new IllegalArgumentException("Versioned contract rejected"); }
}
