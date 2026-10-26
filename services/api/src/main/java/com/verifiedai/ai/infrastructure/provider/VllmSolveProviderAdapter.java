package com.verifiedai.ai.infrastructure.provider;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.ai.application.*;
import com.verifiedai.ai.infrastructure.configuration.VllmSolverProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.vllm-solver.enabled", havingValue = "true")
public final class VllmSolveProviderAdapter implements AiProviderAdapter {
    private final VllmSolverProperties config;
    private final Clock clock;
    private final BoundedInferenceHttp http;
    private final Semaphore slots;
    private final String prompt;
    private final JsonNode schema;
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder().streamReadConstraints(
        StreamReadConstraints.builder().maxNestingDepth(80).maxStringLength(131072).build()).build())
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private volatile AiProviderHealth.Snapshot health = new AiProviderHealth.Snapshot(AiProviderHealth.State.UNKNOWN, null, null);

    public VllmSolveProviderAdapter(VllmSolverProperties config, Clock clock, ResourceLoader resources,
        PromptRegistry prompts, OutputSchemaRegistry schemas, @Value("${app.environment:local}") String environment) {
        if (!config.enabled() || !("local".equals(environment) || "test".equals(environment)))
            throw new IllegalStateException("Real solver adapter is restricted to isolated local evaluation");
        this.config = config; this.clock = clock;
        this.http = new BoundedInferenceHttp(config.limits().connectTimeout(), config.apiKey());
        this.slots = new Semaphore(config.limits().maxConcurrency());
        var p = prompts.require("solver", "v002"); var s = schemas.require("solution-candidate-v1");
        try {
            byte[] promptBytes = resource(resources, p.promptArtifactPath(), 65536);
            byte[] schemaBytes = resource(resources, s.artifactPath(), 65536);
            ArtifactIntegrity.verify(promptBytes, p.contentSha256()); ArtifactIntegrity.verify(schemaBytes, s.contentSha256());
            prompt = new String(promptBytes, StandardCharsets.UTF_8); schema = mapper.readTree(schemaBytes);
        } catch (IOException e) { throw new IllegalStateException("Missing controlled solver artifacts"); }
    }

    @Override public String providerId() { return VllmSolverProperties.PROVIDER; }
    @jakarta.annotation.PreDestroy
    public void close() { http.close(); }
    @Override public Set<AiCapability> supportedCapabilities() { return Set.of(AiCapability.SOLVE); }
    @Override public boolean enforcesCostCeiling() {
        // Only the reviewed fixed allocation of already reserved capacity is supported.
        // This is not an external pay-per-token billing guarantee, nor a GPU invoice cap.
        return config.enabled() && "PREPAID_CAPACITY_FIXED_ALLOCATION".equals(config.accounting().billingSemantics());
    }

    public synchronized AiProviderHealth.Snapshot health(AiRouteTarget target) {
        if (!providerId().equals(target.provider()) || !VllmSolverProperties.MODEL.equals(target.model()))
            return new AiProviderHealth.Snapshot(AiProviderHealth.State.UNKNOWN, null, null);
        if (health.observedAt() != null && health.observedAt().plusSeconds(5).isAfter(clock.instant())) return health;
        var state = AiProviderHealth.State.OPEN;
        try {
            JsonNode response = parse(http.exchange(config.endpoint().resolve("/v1/models"), null,
                Duration.ofSeconds(1), 16384, UUID.randomUUID().toString()));
            JsonNode models = response.path("data");
            if (models.isArray() && models.size() == 1 && VllmSolverProperties.MODEL.equals(models.get(0).path("id").asText()))
                state = AiProviderHealth.State.CLOSED;
        } catch (AiProviderException ignored) { /* metadata-only failure; no payload logging */ }
        health = new AiProviderHealth.Snapshot(state, clock.instant(), "authenticated-vllm-models-v1");
        return health;
    }

    @Override public AiSolveResult execute(AiProviderRequest request) {
        long start = System.nanoTime();
        var limits = config.limits(); var plan = request.routePlan();
        if (!(request.command().request() instanceof AiSolveRequest input)
            || request.command().capability() != AiCapability.SOLVE || !request.target().model().equals(VllmSolverProperties.MODEL)
            || !request.target().provider().equals(providerId()) || !VllmSolverProperties.ROUTE.equals(plan.routePolicyVersion())
            || !"solver".equals(plan.promptId()) || !"v002".equals(plan.promptVersion())
            || !"solution-candidate-v1".equals(plan.schemaVersion())
            || !config.accounting().pricingVersion().equals(plan.pricingVersion())) throw fail(AiProviderFailureClass.POLICY_BLOCKED);
        if (request.costCeilingMicros() < config.accounting().attemptAllocationMicros()) throw fail(AiProviderFailureClass.BUDGET_EXCEEDED);
        Duration allowance = minimum(request.attemptTimeout(), limits.responseTimeout());
        if (request.command().executionContext().deadline() != null)
            allowance = minimum(allowance, Duration.between(clock.instant(), request.command().executionContext().deadline()));
        remaining(start, allowance);
        if (!slots.tryAcquire()) throw fail(AiProviderFailureClass.RATE_LIMITED);
        try {
            String language = request.command().routeContext().locale().getLanguage();
            if (!Set.of("tr", "en").contains(language)) throw fail(AiProviderFailureClass.UNSUPPORTED_PAYLOAD);
            ObjectNode source = mapper.createObjectNode();
            source.put("canonicalProblemId", input.canonicalProblemId().toString());
            source.put("problemParseId", input.problemParseId().toString());
            source.put("problemParseRevision", input.problemParseRevision());
            source.set("canonicalProblem", parse(input.canonicalProblemJson().getBytes(StandardCharsets.UTF_8)));
            ObjectNode body = mapper.createObjectNode(); body.put("model", VllmSolverProperties.MODEL);
            var messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", prompt + "\nPublic explanation language: " + language
                + ".\nAuthoritative output schema:\n" + schema);
            messages.addObject().put("role", "user").put("content", source.toString());
            body.putObject("chat_template_kwargs").put("enable_thinking", false);
            String correlation = request.command().executionContext().operationId().toString();
            ObjectNode tokenize = body.deepCopy(); tokenize.put("add_generation_prompt", true);
            JsonNode tokens = parse(http.exchange(config.endpoint().resolve("/tokenize"), bounded(tokenize),
                remaining(start, allowance), limits.maxResponseBytes(), correlation));
            int inputCount = integer(tokens, "count");
            if (integer(tokens, "max_model_len") != limits.contextTokens()) throw fail(AiProviderFailureClass.POLICY_BLOCKED);
            if (inputCount < 1 || inputCount > limits.maxInputTokens()) throw fail(AiProviderFailureClass.UNSUPPORTED_PAYLOAD);
            body.put("max_tokens", limits.maxOutputTokens()).put("temperature", 0).put("top_p", 1)
                .put("seed", 0).put("n", 1).put("stream", false);
            if (limits.structuredOutputs()) body.putObject("structured_outputs").set("json", schema);
            JsonNode response = parse(http.exchange(config.endpoint().resolve("/v1/chat/completions"), bounded(body),
                remaining(start, allowance), limits.maxResponseBytes(), correlation));
            remaining(start, allowance);
            if (!VllmSolverProperties.MODEL.equals(response.path("model").asText())) throw fail(AiProviderFailureClass.POLICY_BLOCKED);
            String responseId = response.path("id").asText("");
            if (!responseId.matches("[A-Za-z0-9_-]{1,128}")) throw fail(AiProviderFailureClass.SCHEMA_INVALID);
            JsonNode choices = response.path("choices");
            if (!choices.isArray() || choices.size() != 1 || !"stop".equals(choices.get(0).path("finish_reason").asText()))
                throw fail(AiProviderFailureClass.SCHEMA_INVALID);
            JsonNode message = choices.get(0).path("message");
            if (!"assistant".equals(message.path("role").asText()) || !message.path("content").isTextual()
                || (message.hasNonNull("tool_calls") && !message.path("tool_calls").isEmpty())
                || message.hasNonNull("function_call") || message.hasNonNull("refusal")) throw fail(AiProviderFailureClass.SCHEMA_INVALID);
            String output = message.path("content").textValue();
            byte[] outputBytes = output.getBytes(StandardCharsets.UTF_8);
            if (outputBytes.length > plan.maxResponseBytes()) throw fail(AiProviderFailureClass.OUTPUT_TOO_LARGE);
            if (!parse(outputBytes).isObject()) throw fail(AiProviderFailureClass.SCHEMA_INVALID);
            int promptTokens = integer(response.path("usage"), "prompt_tokens");
            int outputTokens = integer(response.path("usage"), "completion_tokens");
            int total = integer(response.path("usage"), "total_tokens");
            if (promptTokens != inputCount || promptTokens > limits.maxInputTokens() || outputTokens > limits.maxOutputTokens()
                || (long) promptTokens + outputTokens != total || total > limits.contextTokens()) throw fail(AiProviderFailureClass.SCHEMA_INVALID);
            return new AiSolveResult(output, new AiProvenance(providerId(), VllmSolverProperties.MODEL,
                plan.routePolicyVersion(), plan.promptId(), plan.promptVersion(), plan.schemaVersion(), correlation, responseId, false),
                new AiUsage(promptTokens, outputTokens, 0, 1, config.accounting().attemptAllocationMicros(), "USD", plan.pricingVersion()),
                Duration.ofNanos(System.nanoTime() - start).toMillis());
        } finally { slots.release(); }
    }

    private byte[] bounded(ObjectNode body) {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > config.limits().maxRequestBytes()) throw fail(AiProviderFailureClass.UNSUPPORTED_PAYLOAD);
        return bytes;
    }
    private JsonNode parse(byte[] bytes) {
        try { JsonNode value = mapper.readTree(bytes); if (value == null) throw fail(AiProviderFailureClass.SCHEMA_INVALID); return value; }
        catch (IOException e) { throw fail(AiProviderFailureClass.SCHEMA_INVALID); }
    }
    private static int integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) throw fail(AiProviderFailureClass.SCHEMA_INVALID);
        return value.intValue();
    }
    private static Duration minimum(Duration a, Duration b) { return a.compareTo(b) < 0 ? a : b; }
    private static Duration remaining(long start, Duration allowance) {
        Duration value = allowance.minusNanos(System.nanoTime() - start);
        if (value.isZero() || value.isNegative()) throw fail(AiProviderFailureClass.DEADLINE_EXCEEDED);
        return value;
    }
    private static byte[] resource(ResourceLoader resources, String path, int limit) throws IOException {
        try (var stream = resources.getResource("classpath:" + path).getInputStream()) {
            byte[] bytes = stream.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("Oversized artifact");
            return bytes;
        }
    }
    private static AiProviderException fail(AiProviderFailureClass kind) { return BoundedInferenceHttp.failure(kind); }
}
