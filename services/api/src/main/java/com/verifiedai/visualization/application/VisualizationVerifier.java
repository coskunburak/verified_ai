package com.verifiedai.visualization.application;
import java.util.UUID;
public interface VisualizationVerifier {
    String execute(UUID execution,String request);
    enum Reason { TIMEOUT, RESOURCE_FAILURE, WORKER_FAILURE, ADMISSION_LIMIT, INVALID_RESPONSE }
    final class Failure extends IllegalStateException {
        private final Reason reason;
        public Failure(Reason reason) { super(reason.name()); this.reason=reason; }
        public Reason reason() { return reason; }
    }
}
