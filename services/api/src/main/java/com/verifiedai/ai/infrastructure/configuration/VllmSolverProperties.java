package com.verifiedai.ai.infrastructure.configuration;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Operator-owned evaluation configuration. Never populated from a solve request. */
@ConfigurationProperties("app.ai.vllm-solver")
public record VllmSolverProperties(boolean enabled, URI endpoint, Set<String> allowedOrigins,
    String apiKey, String evaluationApproval, Release release, Limits limits, Accounting accounting) {
    public static final String PROVIDER = "VLLM_SELF_HOSTED";
    public static final String REPOSITORY = "Qwen/Qwen3.5-4B";
    public static final String REVISION = "851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a";
    public static final String MODEL = "qwen3.5-4b-" + REVISION + "-bf16-vllm0171";
    public static final String ROUTE = "solve-qwen-eval-v1";
    public static final String GATE = "qwen-offline-evaluation-v1";

    public VllmSolverProperties {
        allowedOrigins = allowedOrigins == null ? Set.of() : Set.copyOf(allowedOrigins);
        if (enabled) {
            require(endpoint != null && endpoint.getHost() != null && endpoint.getUserInfo() == null
                && endpoint.getQuery() == null && endpoint.getFragment() == null
                && "/v1".equals(endpoint.getPath()), "Invalid inference endpoint");
            String origin = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
            boolean loopback = Set.of("127.0.0.1", "[::1]").contains(endpoint.getHost());
            require(allowedOrigins.contains(origin) && ("https".equals(endpoint.getScheme())
                || (loopback && "http".equals(endpoint.getScheme()))), "Inference origin is not approved");
            require(apiKey != null && apiKey.matches("[A-Za-z0-9._~-]{24,256}"), "Missing service credential");
            require(evaluationApproval != null && evaluationApproval.matches("[A-Za-z0-9._/-]{8,160}"),
                "Missing owner evaluation approval reference");
            require(release != null && limits != null && accounting != null, "Incomplete inference policy");
        }
    }

    public static VllmSolverProperties disabled() {
        return new VllmSolverProperties(false, null, Set.of(), null, null, null, null, null);
    }

    // Avoid record-generated secret disclosure in diagnostics/configuration logging.
    @Override public String toString() { return "VllmSolverProperties[enabled=" + enabled + ", credentials=REDACTED]"; }

    public record Release(String repository, String revision, String tokenizerRevision, String processorRevision,
        String runtimeVersion, String runtimeImageDigest, String quantization, String deploymentEvidenceSha256) {
        public Release {
            require(REPOSITORY.equals(repository) && REVISION.equals(revision)
                && REVISION.equals(tokenizerRevision) && REVISION.equals(processorRevision), "Unapproved model revision");
            require("0.17.1".equals(runtimeVersion), "Unapproved inference runtime");
            require(runtimeImageDigest != null && runtimeImageDigest.matches("sha256:[a-f0-9]{64}")
                && deploymentEvidenceSha256 != null && deploymentEvidenceSha256.matches("[a-f0-9]{64}"),
                "Missing immutable deployment evidence");
            // Q4 is a target, not a reviewed artifact. Do not accept arbitrary AWQ/GGUF files.
            require("BF16_REFERENCE".equals(quantization), "Quantization artifact is not approved for evaluation");
        }
    }

    public record Limits(int contextTokens, int maxInputTokens, int maxOutputTokens, int maxRequestBytes,
        int maxResponseBytes, Duration connectTimeout, Duration responseTimeout, int maxConcurrency,
        boolean structuredOutputs) {
        public Limits {
            require(contextTokens > 0 && contextTokens <= 8192 && maxInputTokens > 0 && maxOutputTokens > 0
                && maxOutputTokens <= 2048 && (long) maxInputTokens + maxOutputTokens <= contextTokens,
                "Invalid token limits");
            require(maxRequestBytes > 0 && maxRequestBytes <= 65536 && maxResponseBytes > 0
                && maxResponseBytes <= 131072 && maxConcurrency > 0 && maxConcurrency <= 2, "Invalid transport limits");
            require(connectTimeout != null && !connectTimeout.isNegative() && !connectTimeout.isZero()
                && connectTimeout.compareTo(Duration.ofSeconds(2)) <= 0 && responseTimeout != null
                && !responseTimeout.isNegative() && !responseTimeout.isZero()
                && responseTimeout.compareTo(Duration.ofSeconds(30)) <= 0, "Invalid transport timeouts");
        }
    }

    /** Fixed internal allocation, NOT token billing or a claim that idle GPU capacity is free. */
    public record Accounting(String pricingVersion, String billingSemantics, long attemptAllocationMicros,
        long expectedInputMicros, long expectedOutputMicros, long expectedImageMicros) {
        public Accounting {
            require(pricingVersion != null && pricingVersion.matches("[a-z][a-z0-9-]{3,100}")
                && !pricingVersion.contains("fixture") && !pricingVersion.contains("disabled"), "Missing approved pricing version");
            require("PREPAID_CAPACITY_FIXED_ALLOCATION".equals(billingSemantics), "Unbounded provider billing is unsupported");
            require(attemptAllocationMicros > 0 && expectedInputMicros > 0 && expectedOutputMicros > 0
                && expectedImageMicros == 0 && expectedInputMicros <= attemptAllocationMicros
                && expectedOutputMicros <= attemptAllocationMicros - expectedInputMicros, "Invalid approved allocation");
        }
    }

    static void require(boolean value, String message) {
        if (!value) throw new IllegalArgumentException(message);
    }
}
