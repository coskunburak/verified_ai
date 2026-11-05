package com.verifiedai.visualization.infrastructure;
import com.verifiedai.visualization.application.VisualizationVerifier;
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
public class HttpVisualizationVerifier implements VisualizationVerifier {
    private final URI endpoint;
    private final String token;
    private final Semaphore slots=new Semaphore(2);
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build();
    public HttpVisualizationVerifier(@Value("${app.verification.base-url:http://math-verifier:8090}") String base,
        @Value("${app.verification.internal-token:local_math_verifier_token_change_me}") String token) {
        var uri=URI.create(base);
        if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null
            ||uri.getRawQuery()!=null||uri.getRawFragment()!=null||!(uri.getPath().isEmpty()||uri.getPath().equals("/")))
            throw new IllegalArgumentException("Invalid internal endpoint");
        endpoint=uri.resolve("/internal/v1/verify/visualization"); this.token=token;
    }
    public String execute(UUID execution,String request) {
        if(!slots.tryAcquire()) throw new Failure(Reason.ADMISSION_LIMIT);
        CompletableFuture<HttpResponse<byte[]>> pending=null;
        try {
            if(request.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65536) throw new IllegalArgumentException();
            var http=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(4)).header("Content-Type","application/json")
                .header("Accept","application/json").header("X-Internal-Token",token).header("X-Request-Id",execution.toString())
                .POST(HttpRequest.BodyPublishers.ofString(request)).build();
            pending=client.sendAsync(http,info->new LimitedBody());
            var response=pending.get(4500,TimeUnit.MILLISECONDS);
            if(response.statusCode()!=200||!response.headers().firstValue("Content-Type").orElse("").startsWith("application/json")) throw new IllegalArgumentException();
            return new String(response.body(),java.nio.charset.StandardCharsets.UTF_8);
        } catch(Failure ex) { throw ex; }
        catch(TimeoutException ex) { throw new Failure(Reason.TIMEOUT); }
        catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new Failure(Reason.WORKER_FAILURE); }
        catch(ExecutionException ex) { throw new Failure(ex.getCause() instanceof HttpTimeoutException ? Reason.TIMEOUT : Reason.WORKER_FAILURE); }
        catch(Exception ex) { throw new Failure(Reason.INVALID_RESPONSE); }
        finally { if(pending!=null&&!pending.isDone()) pending.cancel(true); slots.release(); }
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
