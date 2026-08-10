package com.verifiedai.problem.infrastructure.classification;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.problem-classifier")
public record ProblemClassifierProperties(
    Duration stuckRunningTimeout,
    Duration workerInterval
) {
}
