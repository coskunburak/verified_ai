package com.verifiedai.verification.domain.policy;

import com.verifiedai.verification.application.VerificationSourceReader;
import com.verifiedai.verification.domain.model.PlanningSource;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.verifiedai.verification.PlanningTestData.*;
import static org.assertj.core.api.Assertions.*;

class CompositeSourceScopeTest {
    PlanningSource source(PlanningSource.Domain domain, PlanningSource.Restriction... restrictions) {
        var original = new VerificationSourceReader(CODEC).read(equation());
        return new PlanningSource(original.identity(), original.problemType(), original.taskType(), original.statements(), Map.of("x", domain),
            List.of(restrictions), List.of(), Arrays.stream(restrictions).map(PlanningSource.Restriction::id).toList(), List.of(), false);
    }
    PlanningSource.Restriction restriction(String id, PlanningSource.Relation relation, String value) {
        return new PlanningSource.Restriction(id, relation, x(), number(value));
    }
    @Test void contradictoryOpenBoundsAndExcludedSingletonAreEmpty() {
        assertThat(CompositeSourceScope.inspect(source(PlanningSource.Domain.REAL,
            restriction("positive", PlanningSource.Relation.GREATER_THAN, "0"), restriction("negative", PlanningSource.Relation.LESS_THAN, "0"))))
            .isEqualTo(CompositeSourceScope.State.EMPTY);
        assertThat(CompositeSourceScope.inspect(source(PlanningSource.Domain.REAL,
            restriction("one", PlanningSource.Relation.EQUALS, "1"), restriction("not-one", PlanningSource.Relation.NOT_EQUALS, "1"))))
            .isEqualTo(CompositeSourceScope.State.EMPTY);
    }
    @Test void integerDomainsNeedAnIntegerWitnessAndUnknownIsNotReal() {
        var lower = restriction("lower", PlanningSource.Relation.GREATER_THAN, "0");
        var upper = restriction("upper", PlanningSource.Relation.LESS_THAN, "1");
        assertThat(CompositeSourceScope.inspect(source(PlanningSource.Domain.INTEGER, lower, upper))).isEqualTo(CompositeSourceScope.State.EMPTY);
        assertThat(CompositeSourceScope.inspect(source(PlanningSource.Domain.REAL, lower, upper))).isEqualTo(CompositeSourceScope.State.NONEMPTY);
        assertThat(CompositeSourceScope.inspect(source(PlanningSource.Domain.UNKNOWN))).isEqualTo(CompositeSourceScope.State.UNKNOWN);
    }
    @Test void hugeIntervalsAreNotEnumeratedAndNonlinearRestrictionsStayUnknown() {
        assertThat(CompositeSourceScope.inspect(source(PlanningSource.Domain.INTEGER,
            restriction("lower", PlanningSource.Relation.GREATER_THAN, "-1000000000000000000000000000000000"),
            restriction("upper", PlanningSource.Relation.LESS_THAN, "1000000000000000000000000000000000"))))
            .isEqualTo(CompositeSourceScope.State.NONEMPTY);
        var power = new com.verifiedai.solving.domain.model.canonical.MathExpression.Binary(
            com.verifiedai.solving.domain.model.canonical.MathExpression.BinaryOperator.POWER, x(), number("2"));
        assertThat(CompositeSourceScope.inspect(source(PlanningSource.Domain.REAL,
            new PlanningSource.Restriction("nonlinear", PlanningSource.Relation.EQUALS, power, number("-1")))))
            .isEqualTo(CompositeSourceScope.State.UNKNOWN);
    }
}
