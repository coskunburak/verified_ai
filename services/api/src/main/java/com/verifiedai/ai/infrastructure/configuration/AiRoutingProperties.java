package com.verifiedai.ai.infrastructure.configuration;

import com.verifiedai.ai.application.AiRouteEligibility;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.routing")
public record AiRoutingProperties(Duration healthMaxAge, List<AiRouteEligibility> eligibility) {
    public AiRoutingProperties {
        healthMaxAge = healthMaxAge == null ? Duration.ofSeconds(30) : healthMaxAge;
        eligibility = eligibility == null ? List.of() : List.copyOf(eligibility);
        if (healthMaxAge.isNegative() || healthMaxAge.isZero() || healthMaxAge.compareTo(Duration.ofMinutes(5)) > 0
            || eligibility.size() > 64) {
            throw new IllegalArgumentException("Invalid bounded routing policy");
        }
        long unique = eligibility.stream().map(c -> c.capability() + ":" + c.target()).distinct().count();
        if (unique != eligibility.size()) {
            throw new IllegalArgumentException("Duplicate model eligibility");
        }
    }
}
