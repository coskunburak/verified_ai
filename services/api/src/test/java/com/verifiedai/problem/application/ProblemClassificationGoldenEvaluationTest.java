package com.verifiedai.problem.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.support.ProblemClassificationIntegrationFixture;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties =
        "app.problem-classifier.worker-interval=PT1H"
)
final class ProblemClassificationGoldenEvaluationTest
    extends PostgresIntegrationTestSupport {

    @Autowired
    ProblemClassificationApplicationService service;

    @Autowired
    JdbcTemplate jdbcTemplate;

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
    void arithmeticEvaluateGoldenCase() {
        assertGolden(
            "ARITHMETIC_EXPRESSION",
            "EVALUATE",
            "2 + 3 * 4",
            "MATH.ARITHMETIC.INTEGER_OPERATIONS"
        );
    }

    @Test
    void algebraSimplifyGoldenCase() {
        assertGolden(
            "ALGEBRAIC_EXPRESSION",
            "SIMPLIFY",
            "2x + 3x",
            "MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS"
        );
    }

    @Test
    void linearEquationGoldenCase() {
        assertGolden(
            "EQUATION",
            "SOLVE_EQUATION",
            "2x + 3 = 9",
            "MATH.EQUATIONS.LINEAR_ONE_VARIABLE"
        );
    }

    @Test
    void quadraticEquationGoldenCase() {
        assertGolden(
            "EQUATION",
            "SOLVE_EQUATION",
            "x^2 - 5x + 6 = 0",
            "MATH.EQUATIONS.QUADRATIC_SOLVING"
        );
    }

    @Test
    void inequalityGoldenCase() {
        assertGolden(
            "INEQUALITY",
            "SOLVE_INEQUALITY",
            "2x + 1 < 7",
            "MATH.EQUATIONS.INEQUALITIES_BASIC"
        );
    }

    private void assertGolden(
        String problemType,
        String taskType,
        String normalizedText,
        String expectedPrimarySkill
    ) {
        UUID userId =
            fixture.insertUser();

        var canonical =
            fixture.insertCanonical(
                userId,
                problemType,
                taskType,
                normalizedText,
                false
            );

        ProblemClassificationStatusResult queued =
            service.requestClassification(
                userId,
                canonical.sessionId()
            );

        assertThat(
            queued.jobStatus()
        ).isEqualTo("QUEUED");

        assertThat(
            service.runDueClassificationJobs(
                10
            )
        ).isEqualTo(1);

        ProblemClassificationStatusResult result =
            service.getClassification(
                userId,
                canonical.sessionId()
            );

        assertThat(
            result.jobStatus()
        ).isEqualTo(
            "SUCCEEDED"
        );

        assertThat(
            result.classificationStatus()
        ).isEqualTo(
            "CLASSIFIED"
        );

        assertThat(
            result.primarySkillId()
        ).isEqualTo(
            expectedPrimarySkill
        );

        assertThat(
            result.subjectId()
        ).isEqualTo(
            "MATH"
        );

        assertThat(
            result.confidenceBand()
        ).isNotEqualTo(
            "HIGH"
        );
    }
}
