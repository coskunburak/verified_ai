package com.verifiedai.problem.api.classification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.identity.application.AppleSignInCommand;
import com.verifiedai.identity.application.AuthSessionResult;
import com.verifiedai.identity.application.IdentityApplicationService;
import com.verifiedai.identity.domain.model.AppleIdentityVerifier;
import com.verifiedai.identity.domain.model.VerifiedAppleIdentity;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.application.classification.ProblemClassificationApplicationService;
import com.verifiedai.problem.support.ProblemClassificationIntegrationFixture;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@Import(
    ProblemClassificationControllerTest
        .AuthTestConfiguration.class
)
@TestPropertySource(
    properties =
        "app.problem-classifier.worker-interval=PT1H"
)
final class ProblemClassificationControllerTest
    extends PostgresIntegrationTestSupport {

    @Value("${local.server.port}")
    int port;

    @Autowired
    IdentityApplicationService identityApplicationService;

    @Autowired
    ProblemClassificationApplicationService classificationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient =
        HttpClient.newHttpClient();

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    private ProblemClassificationIntegrationFixture fixture;

    @BeforeEach
    void setUp() {
        fixture =
            new ProblemClassificationIntegrationFixture(
                jdbcTemplate
            );

        fixture.clean();
    }

    @Test
    void classificationEndpointRequiresAuthentication()
        throws Exception {

        UUID sessionId =
            UUID.randomUUID();

        HttpResponse<String> response =
            httpClient.send(
                HttpRequest
                    .newBuilder(
                        uri(
                            "/api/v1/problem-sessions/"
                                + sessionId
                                + "/classification"
                        )
                    )
                    .POST(
                        HttpRequest.BodyPublishers
                            .noBody()
                    )
                    .build(),
                HttpResponse.BodyHandlers
                    .ofString()
            );

        assertThat(
            response.statusCode()
        ).isEqualTo(401);
    }

    @Test
    void getBeforeRequestReturnsNotStarted()
        throws Exception {

        AuthSessionResult auth =
            signIn(
                "classification-not-started"
            );

        var canonical =
            fixture.insertCanonical(
                auth.userId(),
                "EQUATION",
                "SOLVE_EQUATION",
                "x + 1 = 2",
                false
            );

        HttpResponse<String> response =
            httpClient.send(
                authorized(
                    auth,
                    path(
                        canonical.sessionId()
                    )
                )
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers
                    .ofString()
            );

        assertThat(
            response.statusCode()
        ).as(response.body())
            .isEqualTo(200);

        JsonNode body =
            objectMapper.readTree(
                response.body()
            );

        assertThat(
            body.path(
                "jobStatus"
            ).asText()
        ).isEqualTo(
            "NOT_STARTED"
        );

        assertThat(
            body.path(
                "canonicalProblemId"
            ).asText()
        ).isEqualTo(
            canonical
                .canonicalProblemId()
                .toString()
        );
    }

    @Test
    void authenticatedUserCanRequestAndPollClassificationWithoutInternalLeakage()
        throws Exception {

        AuthSessionResult auth =
            signIn(
                "classification-api-user"
            );

        var canonical =
            fixture.insertCanonical(
                auth.userId(),
                "EQUATION",
                "SOLVE_EQUATION",
                "2x + 3 = 9",
                false
            );

        HttpResponse<String> request =
            httpClient.send(
                authorized(
                    auth,
                    path(
                        canonical.sessionId()
                    )
                )
                    .POST(
                        HttpRequest.BodyPublishers
                            .noBody()
                    )
                    .build(),
                HttpResponse.BodyHandlers
                    .ofString()
            );

        assertThat(
            request.statusCode()
        ).as(request.body())
            .isEqualTo(202);

        JsonNode queued =
            objectMapper.readTree(
                request.body()
            );

        assertThat(
            queued.path(
                "jobStatus"
            ).asText()
        ).isEqualTo(
            "QUEUED"
        );

        assertThat(
            queued.path(
                "capability"
            ).asText()
        ).isEqualTo(
            "PROBLEM_CLASSIFY"
        );

        assertThat(
            classificationService
                .runDueClassificationJobs(
                    10
                )
        ).isEqualTo(1);

        HttpResponse<String> current =
            httpClient.send(
                authorized(
                    auth,
                    path(
                        canonical.sessionId()
                    )
                )
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers
                    .ofString()
            );

        assertThat(
            current.statusCode()
        ).as(current.body())
            .isEqualTo(200);

        JsonNode body =
            objectMapper.readTree(
                current.body()
            );

        assertThat(
            body.path(
                "jobStatus"
            ).asText()
        ).isEqualTo(
            "SUCCEEDED"
        );

        assertThat(
            body.path(
                "classificationStatus"
            ).asText()
        ).isEqualTo(
            "CLASSIFIED"
        );

        assertThat(
            body.path(
                "primarySkillId"
            ).asText()
        ).isEqualTo(
            "MATH.EQUATIONS.LINEAR_ONE_VARIABLE"
        );

        assertThat(
            body.path(
                "confidenceBand"
            ).asText()
        ).isEqualTo(
            "MEDIUM"
        );

        assertThat(
            current.body()
        )
            .doesNotContain(
                "requestFingerprint"
            )
            .doesNotContain(
                "estimatedCostMicros"
            )
            .doesNotContain(
                "providerLatencyMs"
            )
            .doesNotContain(
                "rawOutput"
            )
            .doesNotContain(
                "classificationProjection"
            )
            .doesNotContain(
                "candidateSkills"
            );
    }

    @Test
    void wrongUserCannotAccessClassificationSession()
        throws Exception {

        AuthSessionResult owner =
            signIn(
                "classification-owner"
            );

        AuthSessionResult attacker =
            signIn(
                "classification-attacker"
            );

        var canonical =
            fixture.insertCanonical(
                owner.userId(),
                "EQUATION",
                "SOLVE_EQUATION",
                "x + 1 = 2",
                false
            );

        HttpResponse<String> response =
            httpClient.send(
                authorized(
                    attacker,
                    path(
                        canonical.sessionId()
                    )
                )
                    .POST(
                        HttpRequest.BodyPublishers
                            .noBody()
                    )
                    .build(),
                HttpResponse.BodyHandlers
                    .ofString()
            );

        /*
         * Resource ownership is deliberately concealed.
         */
        assertThat(
            response.statusCode()
        ).isEqualTo(404);

        assertThat(
            response.body()
        ).contains(
            "\"code\":\"RESOURCE_FORBIDDEN\""
        );

        assertThat(
            fixture.count(
                "problem_classification_jobs"
            )
        ).isZero();
    }

    private AuthSessionResult signIn(
        String subject
    ) {
        return identityApplicationService
            .signInWithApple(
                new AppleSignInCommand(
                    subject,
                    "unused-code",
                    "nonce"
                )
            );
    }

    private HttpRequest.Builder authorized(
        AuthSessionResult session,
        String path
    ) {
        return HttpRequest
            .newBuilder(
                uri(path)
            )
            .header(
                "Authorization",
                "Bearer "
                    + session.accessToken()
            )
            .header(
                "Accept",
                "application/json"
            );
    }

    private String path(
        UUID sessionId
    ) {
        return "/api/v1/problem-sessions/"
            + sessionId
            + "/classification";
    }

    private URI uri(
        String path
    ) {
        return URI.create(
            "http://localhost:"
                + port
                + path
        );
    }

    @TestConfiguration
    static class AuthTestConfiguration {

        @Bean
        @Primary
        AppleIdentityVerifier
        appleIdentityVerifier() {

            return (
                identityToken,
                rawNonce
            ) ->
                new VerifiedAppleIdentity(
                    identityToken
                );
        }
    }
}
