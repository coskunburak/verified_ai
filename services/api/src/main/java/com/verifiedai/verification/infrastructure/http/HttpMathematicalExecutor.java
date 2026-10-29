package com.verifiedai.verification.infrastructure.http;

import com.verifiedai.verification.application.MathematicalExecutor;
import com.verifiedai.verification.domain.model.MathematicalExecution.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpMathematicalExecutor implements MathematicalExecutor, com.verifiedai.verification.application.CalculusExecutor {
    private final HttpClient client;
    private final URI endpoint;
    private final String token;
    private final boolean enabled;
    private final String environment;
    private boolean calculusEnabled;
    private final Semaphore slots = new Semaphore(4);
    private final ExecutionWireCodec codec = new ExecutionWireCodec();
    public HttpMathematicalExecutor(@Value("${app.verification.base-url:http://math-verifier:8090}") String baseUrl,
        @Value("${app.verification.internal-token:local_math_verifier_token_change_me}") String token,
        @Value("${app.verification.execution-enabled:false}") boolean enabled,
        @Value("${app.environment:local}") String environment) {
        var uri = URI.create(baseUrl);
        if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
            || uri.getRawQuery() != null || uri.getRawFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
            throw new IllegalArgumentException("Invalid internal verifier endpoint");
        if (enabled && !List.of("local", "test").contains(environment)
            && (token == null || token.length() < 32 || token.equals("local_math_verifier_token_change_me")))
            throw new IllegalArgumentException("Production internal verifier secret required");
        this.environment=environment;
        this.endpoint = uri.resolve("/internal/v1/verify/execute"); this.token = token; this.enabled = enabled;
        // Uvicorn serves HTTP/1.1; avoid h2c upgrade headers that can interrupt POST body parsing.
        client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public Result execute(Request request) {
        if (!enabled) return Result.failed(request, Reason.DISABLED);
        if (!slots.tryAcquire()) return Result.failed(request, Reason.ADMISSION_LIMIT);
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            var http = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .header("X-Internal-Token", token).header("X-Request-Id", request.correlationId())
                .POST(HttpRequest.BodyPublishers.ofByteArray(codec.request(request))).build();
            pending = client.sendAsync(http, info -> new LimitedBody());
            // Covers the body as well as headers; no retry and no unbounded stream reads.
            var response = pending.get(4500, TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) return Result.failed(request, Reason.NETWORK_FAILURE);
            if (!response.headers().firstValue("Content-Type").orElse("").toLowerCase(java.util.Locale.ROOT).startsWith("application/json"))
                return Result.failed(request, Reason.INVALID_RESPONSE);
            return codec.result(response.body(), request);
        } catch (TimeoutException e) { return Result.failed(request, Reason.TIMEOUT); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return Result.failed(request, Reason.NETWORK_FAILURE); }
        catch (IllegalArgumentException e) { return Result.failed(request, Reason.INVALID_RESPONSE); }
        catch (ExecutionException e) {
            return Result.failed(request, e.getCause() instanceof HttpTimeoutException ? Reason.TIMEOUT
                : e.getCause() instanceof IllegalArgumentException ? Reason.INVALID_RESPONSE : Reason.NETWORK_FAILURE);
        }
        finally { if (pending != null && !pending.isDone()) pending.cancel(true); slots.release(); }
    }
    @org.springframework.beans.factory.annotation.Autowired
    void configureCalculus(@Value("${app.verification.calculus-enabled:false}") boolean enabled,
        @Value("${app.verification.calculus-owner-accepted:false}") boolean accepted,
        @Value("${app.verification.calculus-algorithm:bounded-calculus-v1}") String algorithm) {
        if (enabled && (!List.of("local","test").contains(environment) || !"bounded-calculus-v1".equals(algorithm)))
            throw new IllegalArgumentException("Production calculus is not approved");
        calculusEnabled=enabled && accepted;
    }
    public boolean calculusReady() {
        if (!calculusEnabled || !slots.tryAcquire()) return false;
        CompletableFuture<HttpResponse<byte[]>> pending=null;
        try {
            var request=HttpRequest.newBuilder(endpoint.resolve("/internal/v1/verify/calculus-readiness"))
                .timeout(Duration.ofSeconds(1)).header("X-Internal-Token",token).GET().build();
            pending=client.sendAsync(request, info -> new LimitedBody());
            var response=pending.get(1500,TimeUnit.MILLISECONDS);
            if (response.statusCode()!=200 || !response.headers().firstValue("Content-Type").orElse("")
                .toLowerCase(java.util.Locale.ROOT).startsWith("application/json")) return false;
            var n=com.verifiedai.sharedkernel.contracts.VersionedJsonContract.parse(new String(response.body(),java.nio.charset.StandardCharsets.UTF_8));
            var fields=new java.util.HashSet<String>(); n.fieldNames().forEachRemaining(fields::add);
            return fields.equals(java.util.Set.of("schemaVersion","enabled","algorithm","requestVersion","responseVersion"))
                && n.path("enabled").isBoolean() && n.path("enabled").booleanValue()
                && "calculus-readiness-v1".equals(n.path("schemaVersion").asText())
                && "bounded-calculus-v1".equals(n.path("algorithm").asText())
                && "calculus-execution-v1".equals(n.path("requestVersion").asText())
                && "calculus-execution-result-v1".equals(n.path("responseVersion").asText());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        catch (Exception e) { return false; }
        finally { if (pending!=null && !pending.isDone()) pending.cancel(true); slots.release(); }
    }
    public com.verifiedai.verification.domain.model.CalculusExecution.Result execute(
        com.verifiedai.verification.domain.model.CalculusExecution.Request request) {
        var failure=com.verifiedai.verification.domain.model.CalculusExecution.Reason.WORKER_FAILURE;
        if (!calculusEnabled) return calculusFailure(request,com.verifiedai.verification.domain.model.CalculusExecution.Reason.DISABLED);
        if (!slots.tryAcquire()) return calculusFailure(request,com.verifiedai.verification.domain.model.CalculusExecution.Reason.ADMISSION_LIMIT);
        CompletableFuture<HttpResponse<byte[]>> pending=null;
        try {
            var codec=new CalculusWireCodec();
            var http=HttpRequest.newBuilder(endpoint.resolve("/internal/v1/verify/calculus")).timeout(Duration.ofSeconds(4))
                .header("Content-Type","application/json").header("Accept","application/json")
                .header("X-Internal-Token",token).header("X-Request-Id",request.correlationId())
                .POST(HttpRequest.BodyPublishers.ofByteArray(codec.request(request))).build();
            pending=client.sendAsync(http,info -> new LimitedBody());
            var response=pending.get(4500,TimeUnit.MILLISECONDS);
            if (response.statusCode()!=200) return calculusFailure(request,failure);
            if (!response.headers().firstValue("Content-Type").orElse("").toLowerCase(java.util.Locale.ROOT).startsWith("application/json"))
                return calculusFailure(request,com.verifiedai.verification.domain.model.CalculusExecution.Reason.INVALID_RESPONSE);
            return codec.result(response.body(),request);
        } catch (TimeoutException e) { return calculusFailure(request,com.verifiedai.verification.domain.model.CalculusExecution.Reason.TIMEOUT); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return calculusFailure(request,failure); }
        catch (IllegalArgumentException e) { return calculusFailure(request,com.verifiedai.verification.domain.model.CalculusExecution.Reason.INVALID_RESPONSE); }
        catch (ExecutionException e) { return calculusFailure(request,e.getCause() instanceof HttpTimeoutException
            ? com.verifiedai.verification.domain.model.CalculusExecution.Reason.TIMEOUT
            : e.getCause() instanceof IllegalArgumentException
                ? com.verifiedai.verification.domain.model.CalculusExecution.Reason.INVALID_RESPONSE : failure); }
        finally { if (pending!=null && !pending.isDone()) pending.cancel(true); slots.release(); }
    }
    private static com.verifiedai.verification.domain.model.CalculusExecution.Result calculusFailure(
        com.verifiedai.verification.domain.model.CalculusExecution.Request r,
        com.verifiedai.verification.domain.model.CalculusExecution.Reason reason) {
        return new com.verifiedai.verification.domain.model.CalculusExecution.Result(r.executionId(),r.correlationId(),r.scope(),r.method(),
            "v1","bounded-calculus-v1",Outcome.EXECUTION_FAILED,reason,
            r.restrictions().stream().map(com.verifiedai.verification.domain.model.PlanningSource.Restriction::id).toList(),null,null);
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        public void onNext(List<ByteBuffer> chunks) {
            for (var chunk : chunks) {
                if ((long) bytes.size() + chunk.remaining() > 16384) {
                    subscription.cancel(); body.completeExceptionally(new IllegalArgumentException("Verifier response too large")); return;
                }
                byte[] data = new byte[chunk.remaining()]; chunk.get(data); bytes.writeBytes(data);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { body.completeExceptionally(error); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
