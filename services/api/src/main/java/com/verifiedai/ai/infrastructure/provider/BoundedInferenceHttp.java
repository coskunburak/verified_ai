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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** No redirects, retries, payload logs, or unbounded body buffering. */
final class BoundedInferenceHttp implements AutoCloseable {
    private final HttpClient client;
    private final String credential;
    BoundedInferenceHttp(Duration connectTimeout, String credential) {
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER).build();
        this.credential = credential;
    }

    byte[] exchange(URI uri, byte[] body, Duration allowance, int maxBytes, String requestId) {
        if (allowance.isNegative() || allowance.isZero()) throw failure(AiProviderFailureClass.DEADLINE_EXCEEDED);
        var builder = HttpRequest.newBuilder(uri).timeout(allowance)
            .header("Authorization", "Bearer " + credential).header("Accept", "application/json")
            .header("X-Request-Id", requestId);
        if (body == null) builder.GET();
        else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body));
        var future = client.sendAsync(builder.build(), info -> new LimitedBody(maxBytes));
        try {
            // Includes full streamed body, not just arrival of headers.
            HttpResponse<byte[]> response = future.get(allowance.toNanos(), TimeUnit.NANOSECONDS);
            int status = response.statusCode();
            if (status == 401 || status == 403) throw failure(AiProviderFailureClass.INVALID_AUTH);
            if (status == 429) throw failure(AiProviderFailureClass.RATE_LIMITED);
            if (status >= 500) throw failure(AiProviderFailureClass.PROVIDER_UNAVAILABLE);
            if (status != 200) throw failure(AiProviderFailureClass.POLICY_BLOCKED);
            if (!response.headers().firstValue("Content-Type").orElse("").toLowerCase(java.util.Locale.ROOT)
                .startsWith("application/json")) throw failure(AiProviderFailureClass.SCHEMA_INVALID);
            return response.body();
        } catch (TimeoutException e) {
            future.cancel(true);
            throw failure(AiProviderFailureClass.TIMEOUT);
        } catch (InterruptedException e) {
            future.cancel(true); Thread.currentThread().interrupt();
            throw failure(AiProviderFailureClass.UNKNOWN);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof AiProviderException provider) throw provider;
            if (cause instanceof java.net.http.HttpTimeoutException) throw failure(AiProviderFailureClass.TIMEOUT);
            throw failure(AiProviderFailureClass.UNKNOWN);
        }
    }

    static AiProviderException failure(AiProviderFailureClass kind) {
        // Uncertain remote work is never automatically replayed, including 429/5xx.
        return new AiProviderException(kind, false, "Controlled inference request failed: " + kind.name());
    }

    @Override public void close() { client.shutdownNow(); }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;
        LimitedBody(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(failure(AiProviderFailureClass.OUTPUT_TOO_LARGE)); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
