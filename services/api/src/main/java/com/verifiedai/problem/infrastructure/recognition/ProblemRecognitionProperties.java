package com.verifiedai.problem.infrastructure.recognition;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.problem-recognition")
public record ProblemRecognitionProperties(
    long maxInputBytes,
    int maxBlocks,
    int maxTextCharsPerBlock,
    int maxTotalTextChars,
    double coordinateTolerance,
    Duration stuckRunningTimeout,
    Duration workerInterval
) {
}
