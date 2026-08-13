package com.verifiedai.problem.application.session;

import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemSessionDetailApplicationService {
    private final ProblemSessionProjectionService projectionService;
    private final ProblemSessionMetrics metrics;

    ProblemSessionDetailApplicationService(
        ProblemSessionProjectionService projectionService,
        ProblemSessionMetrics metrics
    ) {
        this.projectionService = projectionService;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public ProblemSessionProjection detail(UUID userId, UUID sessionId) {
        long started = System.nanoTime();
        try {
            ProblemSessionProjection projection = projectionService.detail(userId, sessionId);
            metrics.detailLoaded("SUCCESS");
            return projection;
        } catch (ApiProblemException exception) {
            metrics.detailLoaded(exception.code().name());
            throw exception;
        } finally {
            metrics.detailLatency(System.nanoTime() - started);
        }
    }
}
