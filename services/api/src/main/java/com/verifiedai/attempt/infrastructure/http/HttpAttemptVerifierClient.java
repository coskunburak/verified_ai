package com.verifiedai.attempt.infrastructure.http;

import com.verifiedai.attempt.application.AttemptVerifierClient;
import com.verifiedai.attempt.domain.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpAttemptVerifierClient implements AttemptVerifierClient {
    private final URI endpoint;
    private final String token;
    private final HttpClient client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Semaphore slots=new Semaphore(2);
    private final ObjectMapper mapper=new ObjectMapper(JsonFactory.builder().streamReadConstraints(
        StreamReadConstraints.builder().maxNestingDepth(40).maxStringLength(1024).build()).build())
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    public HttpAttemptVerifierClient(@Value("${app.verification.base-url:http://math-verifier:8090}") String base,
        @Value("${app.verification.internal-token:local_math_verifier_token_change_me}") String token) {
        var uri=URI.create(base);
        if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getRawUserInfo()!=null
            || uri.getRawQuery()!=null || uri.getRawFragment()!=null || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
            throw new IllegalArgumentException("Invalid internal verifier endpoint");
        endpoint=uri.resolve("/internal/v1/verify/attempt"); this.token=token;
    }
    public AttemptAnalysisResult verify(UUID id,int count,String request) {
        if(request==null) return AttemptAnalysisResult.unavailable(id,count,AttemptAnalysisResult.Reason.UNSUPPORTED_MATH);
        if(!slots.tryAcquire()) return AttemptAnalysisResult.unavailable(id,count,AttemptAnalysisResult.Reason.ADMISSION_LIMIT);
        CompletableFuture<HttpResponse<byte[]>> pending=null;
        try {
            if(request.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65536) throw new IllegalArgumentException();
            boolean v2="attempt-verification-v2".equals(mapper.readTree(request).path("schemaVersion").asText());
            var http=HttpRequest.newBuilder(v2?endpoint.resolve("/internal/v1/verify/attempt-v2"):endpoint).timeout(Duration.ofSeconds(4)).header("Content-Type","application/json")
                .header("Accept","application/json").header("X-Internal-Token",token).header("X-Request-Id",id.toString())
                .POST(HttpRequest.BodyPublishers.ofString(request)).build();
            pending=client.sendAsync(http,info->new LimitedBody());
            var response=pending.get(4500,TimeUnit.MILLISECONDS);
            if(response.statusCode()!=200 || !response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))
                return AttemptAnalysisResult.unavailable(id,count,AttemptAnalysisResult.Reason.WORKER_FAILURE);
            var result=mapper.readValue(response.body(),AttemptAnalysisResult.class);
            result.validateFor(id,count);
            if(v2 != "attempt-analysis-v2".equals(result.schemaVersion())) throw new IllegalArgumentException();
            return result;
        } catch(TimeoutException e) { return AttemptAnalysisResult.unavailable(id,count,AttemptAnalysisResult.Reason.TIMEOUT); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); return AttemptAnalysisResult.unavailable(id,count,AttemptAnalysisResult.Reason.WORKER_FAILURE); }
        catch(ExecutionException e) { return AttemptAnalysisResult.unavailable(id,count,
            e.getCause() instanceof HttpTimeoutException ? AttemptAnalysisResult.Reason.TIMEOUT : AttemptAnalysisResult.Reason.WORKER_FAILURE); }
        catch(Exception e) { return AttemptAnalysisResult.unavailable(id,count,AttemptAnalysisResult.Reason.INVALID_RESPONSE); }
        finally { if(pending!=null && !pending.isDone()) pending.cancel(true); slots.release(); }
    }
    public AttemptLearningEvidence learning(UUID id,String action,String request) {
        if(!slots.tryAcquire()) throw new IllegalStateException("Verifier admission limit");
        CompletableFuture<HttpResponse<byte[]>> pending=null;
        try {
            if(request.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65536) throw new IllegalArgumentException();
            var http=HttpRequest.newBuilder(endpoint.resolve("/internal/v1/verify/attempt-learning")).timeout(Duration.ofSeconds(4))
                .header("Content-Type","application/json").header("Accept","application/json")
                .header("X-Internal-Token",token).header("X-Request-Id",id.toString())
                .POST(HttpRequest.BodyPublishers.ofString(request)).build();
            pending=client.sendAsync(http,info->new LimitedBody()); var response=pending.get(4500,TimeUnit.MILLISECONDS);
            if(response.statusCode()!=200 || !response.headers().firstValue("Content-Type").orElse("").startsWith("application/json")) throw new IllegalArgumentException();
            var evidence=mapper.readValue(response.body(),AttemptLearningEvidence.class); evidence.validateFor(id,action); return evidence;
        } catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("Verifier unavailable"); }
        catch(Exception ex) { throw new IllegalStateException("Verifier unavailable"); }
        finally { if(pending!=null && !pending.isDone()) pending.cancel(true); slots.release(); }
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
