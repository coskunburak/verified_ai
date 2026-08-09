package com.verifiedai.problem.infrastructure.parser;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.problem-parser")
public record ProblemParserProperties(
    Duration stuckRunningTimeout
) {
}
