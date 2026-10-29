package com.verifiedai.calculus;

import java.io.*;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.*;

/** Starts the real existing Python app only when the owner executes an opted-in test. */
public final class CalculusHttpHarness implements AutoCloseable {
    private final Process process;
    private final int port;
    public CalculusHttpHarness() {
        var reader=Executors.newSingleThreadExecutor();
        Process started=null;
        try {
            var builder=new ProcessBuilder(Objects.requireNonNull(System.getenv("VERIFIEDAI_VERIFIER_PYTHON")),
                "tests/contract/calculus_http_server.py").directory(Path.of("../math-verifier").toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT);
            builder.environment().put("APP_ENV","test"); builder.environment().put("MATH_VERIFIER_CALCULUS_ENABLED","true");
            builder.environment().put("MATH_VERIFIER_INTERNAL_TOKEN","local_math_verifier_token_change_me");
            builder.environment().put("PYTHONPATH",".");
            started=builder.start(); process=started;
            port=Integer.parseInt(reader.submit(()->new BufferedReader(new InputStreamReader(process.getInputStream())).readLine()).get(15,TimeUnit.SECONDS));
        } catch (Exception e) {
            if (started!=null) { started.descendants().forEach(ProcessHandle::destroyForcibly); started.destroyForcibly(); }
            throw new IllegalStateException("Owner Python HTTP fixture unavailable",e);
        } finally { reader.shutdownNow(); }
    }
    public String url() { return "http://127.0.0.1:"+port; }
    public void close() {
        process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly();
        try { process.waitFor(5,TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
