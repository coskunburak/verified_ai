package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bounded HTTP client for Google Gemini API inference.
 * Enforces zero credential logging, bounded memory buffering, and explicit error classification.
 * The API key is strictly passed in the x-goog-api-key request header, never in the URL query string.
 */
final class GeminiInferenceHttp implements AutoCloseable {
    private final HttpClient client;
    private final String apiKey;

    GeminiInferenceHttp(Duration connectTimeout, String apiKey) {
        this.client = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        this.apiKey = apiKey;
    }

    byte[] exchange(URI uri, byte[] body, Duration allowance, int maxBytes, String correlationId) {
        if (allowance.isNegative() || allowance.isZero()) {
            throw failure(AiProviderFailureClass.DEADLINE_EXCEEDED);
        }
        var builder = HttpRequest.newBuilder(uri)
            .timeout(allowance)
            .header("x-goog-api-key", apiKey)
            .header("Accept", "application/json")
            .header("X-Request-Id", correlationId);

        if (body == null) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        }

        var future = client.sendAsync(builder.build(), info -> new LimitedBody(maxBytes));
        try {
            HttpResponse<byte[]> response = future.get(allowance.toNanos(), TimeUnit.NANOSECONDS);
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                throw failure(AiProviderFailureClass.INVALID_AUTH);
            }
            if (status == 429) {
                throw failure(AiProviderFailureClass.RATE_LIMITED);
            }
            if (status >= 500) {
                throw failure(AiProviderFailureClass.PROVIDER_UNAVAILABLE);
            }
            if (status == 400) {
                throw failure(AiProviderFailureClass.SCHEMA_INVALID);
            }
            if (status != 200) {
                throw failure(AiProviderFailureClass.POLICY_BLOCKED);
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("")
                .toLowerCase(Locale.ROOT);
            if (!contentType.startsWith("application/json")) {
                throw failure(AiProviderFailureClass.SCHEMA_INVALID);
            }
            return response.body();
        } catch (TimeoutException e) {
            future.cancel(true);
            throw failure(AiProviderFailureClass.TIMEOUT);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw failure(AiProviderFailureClass.UNKNOWN);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof AiProviderException providerEx) {
                throw providerEx;
            }
            if (cause instanceof java.net.http.HttpTimeoutException) {
                throw failure(AiProviderFailureClass.TIMEOUT);
            }
            if (cause instanceof java.net.ConnectException) {
                throw failure(AiProviderFailureClass.PROVIDER_UNAVAILABLE);
            }
            throw failure(AiProviderFailureClass.UNKNOWN);
        }
    }

    static AiProviderException failure(AiProviderFailureClass kind) {
        // Redacted failure message: never leaks credentials, URL tokens, or request bodies
        return new AiProviderException(kind, false, "Gemini inference request failed: " + kind.name());
    }

    @Override
    public void close() {
        client.shutdownNow();
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        LimitedBody(int limit) {
            this.limit = limit;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            this.subscription = value;
            value.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(failure(AiProviderFailureClass.OUTPUT_TOO_LARGE));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable error) {
            result.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            result.complete(bytes.toByteArray());
        }
    }
}
