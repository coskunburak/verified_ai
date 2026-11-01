package com.verifiedai.ai.infrastructure.provider;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.ai.application.*;
import com.verifiedai.ai.infrastructure.configuration.GeminiProviderProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Backend-only, Zero-Secret Google Gemini API Provider Adapter.
 * Integrates directly with Gemini API :generateContent with bounded concurrency,
 * strict response schema validation, zero credential leakage, and full spending control compatibility.
 */
@Component
@ConditionalOnProperty(name = "app.ai.gemini.enabled", havingValue = "true")
public final class GeminiAiProviderAdapter implements AiProviderAdapter {

    private final GeminiProviderProperties config;
    private final Clock clock;
    private final ResourceLoader resources;
    private final PromptRegistry prompts;
    private final OutputSchemaRegistry schemas;
    private final GeminiInferenceHttp http;
    private final Semaphore slots;
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder().streamReadConstraints(
        StreamReadConstraints.builder().maxNestingDepth(80).maxStringLength(131072).build()).build())
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final ConcurrentMap<String, String> promptCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, JsonNode> schemaCache = new ConcurrentHashMap<>();
    private volatile AiProviderHealth.Snapshot health = new AiProviderHealth.Snapshot(AiProviderHealth.State.UNKNOWN, null, null);

    public GeminiAiProviderAdapter(
        GeminiProviderProperties config,
        Clock clock,
        ResourceLoader resources,
        PromptRegistry prompts,
        OutputSchemaRegistry schemas,
        @Value("${app.environment:local}") String environment
    ) {
        if (!config.enabled()) {
            throw new IllegalStateException("Gemini provider is disabled by policy");
        }
        this.config = config;
        this.clock = clock;
        this.resources = resources;
        this.prompts = prompts;
        this.schemas = schemas;
        this.http = new GeminiInferenceHttp(config.limits().connectTimeout(), config.apiKey());
        this.slots = new Semaphore(config.limits().maxConcurrency());
    }

    @Override
    public String providerId() {
        return GeminiProviderProperties.PROVIDER;
    }

    @PreDestroy
    public void close() {
        http.close();
    }

    @Override
    public Set<AiCapability> supportedCapabilities() {
        return Set.of(
            AiCapability.SOLVE,
            AiCapability.VISION_PARSE,
            AiCapability.PROBLEM_NORMALIZE,
            AiCapability.PROBLEM_CLASSIFY
        );
    }

    @Override
    public boolean enforcesCostCeiling() {
        return config.enabled() && "PREPAID_CAPACITY_FIXED_ALLOCATION".equals(config.accounting().billingSemantics());
    }

    public synchronized AiProviderHealth.Snapshot health(AiRouteTarget target) {
        if (!providerId().equals(target.provider()) || !config.model().equals(target.model())) {
            return new AiProviderHealth.Snapshot(AiProviderHealth.State.UNKNOWN, null, null);
        }
        if (health.observedAt() != null && health.observedAt().plusSeconds(5).isAfter(clock.instant())) {
            return health;
        }
        var state = AiProviderHealth.State.OPEN;
        try {
            URI modelsUri = URI.create(cleanEndpoint(config.endpoint()) + "/v1beta/models/" + config.model());
            JsonNode response = parse(http.exchange(modelsUri, null, Duration.ofSeconds(2), 16384, UUID.randomUUID().toString()));
            String name = response.path("name").asText("");
            if (name.contains(config.model())) {
                state = AiProviderHealth.State.CLOSED;
            }
        } catch (AiProviderException ignored) {
            // metadata-only health failure; no credential or payload logging
        }
        health = new AiProviderHealth.Snapshot(state, clock.instant(), "authenticated-gemini-models-v1");
        return health;
    }

    @Override
    public AiCapabilityResult execute(AiProviderRequest request) {
        long start = System.nanoTime();
        var limits = config.limits();
        var plan = request.routePlan();

        if (!supports(request.command().capability())
            || !request.target().provider().equals(providerId())
            || !request.target().model().equals(config.model())) {
            throw fail(AiProviderFailureClass.POLICY_BLOCKED);
        }

        if (request.costCeilingMicros() < config.accounting().attemptAllocationMicros()) {
            throw fail(AiProviderFailureClass.BUDGET_EXCEEDED);
        }

        Duration allowance = minimum(request.attemptTimeout(), limits.responseTimeout());
        if (request.command().executionContext().deadline() != null) {
            allowance = minimum(allowance, Duration.between(clock.instant(), request.command().executionContext().deadline()));
        }
        remaining(start, allowance);

        if (!slots.tryAcquire()) {
            throw fail(AiProviderFailureClass.RATE_LIMITED);
        }

        try {
            String language = request.command().routeContext().locale().getLanguage();
            if (!Set.of("tr", "en").contains(language)) {
                throw fail(AiProviderFailureClass.UNSUPPORTED_PAYLOAD);
            }

            String prompt = loadPrompt(plan.promptId(), plan.promptVersion());
            JsonNode schema = loadSchema(plan.schemaVersion());

            // Build request payload matching capability
            ObjectNode userContent = buildUserContent(request.command());
            ObjectNode requestBody = mapper.createObjectNode();

            // Contents array
            var contents = requestBody.putArray("contents");
            var userMsg = contents.addObject();
            userMsg.put("role", "user");
            userMsg.putArray("parts").addObject().put("text", userContent.toString());

            // System instructions
            var systemInstruction = requestBody.putObject("systemInstruction");
            systemInstruction.putArray("parts").addObject()
                .put("text", prompt + "\nPublic explanation language: " + language + ".\nAuthoritative output schema:\n" + schema);

            // Generation config
            var genConfig = requestBody.putObject("generationConfig");
            genConfig.put("responseMimeType", "application/json");
            genConfig.put("temperature", 0.0);
            genConfig.put("maxOutputTokens", limits.maxOutputTokens());

            String correlation = request.command().executionContext().operationId().toString();
            byte[] boundedPayload = bounded(requestBody);

            URI generateUri = URI.create(cleanEndpoint(config.endpoint()) + "/v1beta/models/" + config.model() + ":generateContent");
            JsonNode response = parse(http.exchange(
                generateUri,
                boundedPayload,
                remaining(start, allowance),
                limits.maxResponseBytes(),
                correlation
            ));

            remaining(start, allowance);

            // Validate Gemini candidates array
            JsonNode candidates = response.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw fail(AiProviderFailureClass.SCHEMA_INVALID);
            }

            JsonNode firstCandidate = candidates.get(0);
            String finishReason = firstCandidate.path("finishReason").asText("");
            if ("SAFETY".equals(finishReason) || "RECITATION".equals(finishReason) || "BLOCKLIST".equals(finishReason)) {
                throw fail(AiProviderFailureClass.POLICY_BLOCKED);
            }
            if ("MAX_TOKENS".equals(finishReason)) {
                throw fail(AiProviderFailureClass.OUTPUT_TOO_LARGE);
            }
            if (!"STOP".equals(finishReason)) {
                throw fail(AiProviderFailureClass.SCHEMA_INVALID);
            }

            JsonNode parts = firstCandidate.path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw fail(AiProviderFailureClass.SCHEMA_INVALID);
            }

            String rawText = parts.get(0).path("text").asText("");
            String output = sanitizeOutput(rawText);
            byte[] outputBytes = output.getBytes(StandardCharsets.UTF_8);

            if (outputBytes.length > plan.maxResponseBytes()) {
                throw fail(AiProviderFailureClass.OUTPUT_TOO_LARGE);
            }

            if (!parse(outputBytes).isObject()) {
                throw fail(AiProviderFailureClass.SCHEMA_INVALID);
            }

            // Extract token usage
            JsonNode usageMetadata = response.path("usageMetadata");
            int promptTokens = integer(usageMetadata, "promptTokenCount");
            int outputTokens = integer(usageMetadata, "candidatesTokenCount");
            int totalTokens = integer(usageMetadata, "totalTokenCount");

            if (promptTokens > limits.maxInputTokens() || outputTokens > limits.maxOutputTokens()
                || (long) promptTokens + outputTokens != totalTokens || totalTokens > limits.contextTokens()) {
                throw fail(AiProviderFailureClass.SCHEMA_INVALID);
            }

            String responseId = response.path("responseId").asText("gemini-" + UUID.randomUUID());
            AiProvenance provenance = new AiProvenance(
                providerId(),
                config.model(),
                plan.routePolicyVersion(),
                plan.promptId(),
                plan.promptVersion(),
                plan.schemaVersion(),
                correlation,
                responseId,
                false
            );

            AiUsage usage = new AiUsage(
                promptTokens,
                outputTokens,
                0,
                1,
                config.accounting().attemptAllocationMicros(),
                "USD",
                plan.pricingVersion()
            );

            long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

            return switch (request.command().capability()) {
                case SOLVE -> new AiSolveResult(output, provenance, usage, latencyMs);
                case VISION_PARSE -> new AiVisionParseResult(output, provenance, usage, latencyMs);
                case PROBLEM_NORMALIZE -> new AiProblemNormalizeResult(output, provenance, usage, latencyMs);
                case PROBLEM_CLASSIFY -> new AiProblemClassifyResult(output, provenance, usage, latencyMs);
                default -> throw fail(AiProviderFailureClass.UNSUPPORTED_PAYLOAD);
            };
        } finally {
            slots.release();
        }
    }

    private ObjectNode buildUserContent(AiExecutionCommand command) {
        ObjectNode node = mapper.createObjectNode();
        if (command.request() instanceof AiSolveRequest solveReq) {
            node.put("canonicalProblemId", solveReq.canonicalProblemId().toString());
            node.put("problemParseId", solveReq.problemParseId().toString());
            node.put("problemParseRevision", solveReq.problemParseRevision());
            node.set("canonicalProblem", parse(solveReq.canonicalProblemJson().getBytes(StandardCharsets.UTF_8)));
        } else if (command.request() instanceof AiVisionParseRequest visionReq) {
            node.put("inputAssetId", visionReq.inputAssetId().toString());
            var warnings = node.putArray("upstreamQualityWarnings");
            visionReq.upstreamQualityWarnings().forEach(warnings::add);
        } else if (command.request() instanceof AiProblemNormalizeRequest normReq) {
            node.put("recognitionEvidenceId", normReq.recognitionEvidenceId().toString());
            node.set("recognitionEvidence", parse(normReq.normalizedRecognitionEvidenceJson().getBytes(StandardCharsets.UTF_8)));
        } else if (command.request() instanceof AiProblemClassifyRequest classifyReq) {
            node.put("canonicalProblemId", classifyReq.canonicalProblemId().toString());
            node.put("problemSessionId", classifyReq.problemSessionId().toString());
            node.set("classificationProjection", parse(classifyReq.classificationProjectionJson().getBytes(StandardCharsets.UTF_8)));
            if (classifyReq.candidateSkillsJson() != null) {
                node.set("candidateSkills", parse(classifyReq.candidateSkillsJson().getBytes(StandardCharsets.UTF_8)));
            }
        } else {
            throw fail(AiProviderFailureClass.UNSUPPORTED_PAYLOAD);
        }
        return node;
    }

    private String loadPrompt(String promptId, String promptVersion) {
        String key = promptId + ":" + promptVersion;
        return promptCache.computeIfAbsent(key, k -> {
            try {
                var p = prompts.require(promptId, promptVersion);
                byte[] bytes = resource(resources, p.promptArtifactPath(), 65536);
                ArtifactIntegrity.verify(bytes, p.contentSha256());
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load prompt: " + key, e);
            }
        });
    }

    private JsonNode loadSchema(String schemaVersion) {
        return schemaCache.computeIfAbsent(schemaVersion, k -> {
            try {
                var s = schemas.require(schemaVersion);
                byte[] bytes = resource(resources, s.artifactPath(), 65536);
                ArtifactIntegrity.verify(bytes, s.contentSha256());
                return mapper.readTree(bytes);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load schema: " + schemaVersion, e);
            }
        });
    }

    private static String sanitizeOutput(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private byte[] bounded(ObjectNode body) {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > config.limits().maxRequestBytes()) {
            throw fail(AiProviderFailureClass.UNSUPPORTED_PAYLOAD);
        }
        return bytes;
    }

    private JsonNode parse(byte[] bytes) {
        try {
            JsonNode value = mapper.readTree(bytes);
            if (value == null) throw fail(AiProviderFailureClass.SCHEMA_INVALID);
            return value;
        } catch (IOException e) {
            throw fail(AiProviderFailureClass.SCHEMA_INVALID);
        }
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            throw fail(AiProviderFailureClass.SCHEMA_INVALID);
        }
        return value.intValue();
    }

    private static Duration minimum(Duration a, Duration b) {
        return a.compareTo(b) < 0 ? a : b;
    }

    private static Duration remaining(long start, Duration allowance) {
        Duration value = allowance.minusNanos(System.nanoTime() - start);
        if (value.isZero() || value.isNegative()) {
            throw fail(AiProviderFailureClass.DEADLINE_EXCEEDED);
        }
        return value;
    }

    private static byte[] resource(ResourceLoader resources, String path, int limit) throws IOException {
        try (var stream = resources.getResource("classpath:" + path).getInputStream()) {
            byte[] bytes = stream.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("Oversized artifact: " + path);
            return bytes;
        }
    }

    private static String cleanEndpoint(URI endpoint) {
        return endpoint.toString().replaceAll("/+$", "");
    }

    private static AiProviderException fail(AiProviderFailureClass kind) {
        return GeminiInferenceHttp.failure(kind);
    }
}
