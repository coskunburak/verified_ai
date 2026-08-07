package com.verifiedai.bootstrap.api;

import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformHealthController {

    private final Environment environment;
    private final DataSource dataSource;
    private final Clock clock;

    PlatformHealthController(Environment environment, DataSource dataSource, Clock clock) {
        this.environment = environment;
        this.dataSource = dataSource;
        this.clock = clock;
    }

    @GetMapping("/health")
    PlatformHealthResponse health() {
        return PlatformHealthResponse.up(environmentName(), CorrelationIds.current(), Instant.now(clock));
    }

    @GetMapping("/readiness")
    ResponseEntity<PlatformHealthResponse> readiness() {
        if (databaseReady()) {
            return ResponseEntity.ok(PlatformHealthResponse.ready(environmentName(), CorrelationIds.current(), Instant.now(clock)));
        }

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(PlatformHealthResponse.notReady(environmentName(), CorrelationIds.current(), Instant.now(clock)));
    }

    private boolean databaseReady() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String environmentName() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(",", profiles);
    }
}
