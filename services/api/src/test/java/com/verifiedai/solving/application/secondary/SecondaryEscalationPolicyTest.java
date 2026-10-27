package com.verifiedai.solving.application.secondary;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.verifiedai.solving.application.secondary.SecondaryEscalationPolicy.Outcome.*;

class SecondaryEscalationPolicyTest {
    final UUID source=UUID.fromString("10000000-0000-0000-0000-000000000001");
    final SecondaryEscalationPolicy policy=new SecondaryEscalationPolicy(0);
    SecondaryEscalationPolicy.Inputs input(boolean issues,boolean enabled,boolean model,boolean budget) {
        return new SecondaryEscalationPolicy.Inputs(source,true,true,true,issues,enabled,model,budget);
    }
    @Test void lowRiskSkipsEvenWhenExecutionIsAvailable() {
        assertThat(policy.decide(input(false,true,true,true)).outcome()).isEqualTo(SKIP);
    }
    @Test void unresolvedIssuesEscalateWithExplicitReasonAndVersion() {
        var d=policy.decide(input(true,true,true,true));assertThat(d.outcome()).isEqualTo(ESCALATE);
        assertThat(d.reason()).isEqualTo("PRIMARY_UNRESOLVED");assertThat(d.policyVersion()).isEqualTo(policy.version());
    }
    @Test void missingModelAndBudgetAndDisabledExecutionFailClosed() {
        assertThat(policy.decide(input(true,true,false,true)).reason()).isEqualTo("NO_APPROVED_INDEPENDENT_MODEL");
        assertThat(policy.decide(input(true,true,true,false)).reason()).isEqualTo("SECONDARY_BUDGET_UNAVAILABLE");
        assertThat(policy.decide(input(true,false,true,true)).reason()).isEqualTo("SECONDARY_DISABLED");
    }
    @Test void staleSourceHasPriorityOverRiskAndBudget() {
        var d=policy.decide(new SecondaryEscalationPolicy.Inputs(source,false,true,true,true,true,false,false));
        assertThat(d.outcome()).isEqualTo(BLOCKED);assertThat(d.reason()).isEqualTo("SOURCE_STALE");
    }
    @Test void unsupportedSkipsAndMissingPrimaryIsConservative() {
        assertThat(policy.decide(new SecondaryEscalationPolicy.Inputs(source,true,false,true,true,true,true,true)).outcome()).isEqualTo(SKIP);
        assertThat(policy.decide(new SecondaryEscalationPolicy.Inputs(source,true,true,false,true,true,true,true)).outcome()).isEqualTo(INSUFFICIENT_EVIDENCE);
    }
    @Test void qcIsStableBoundedAndVersioned() {
        var sample=new SecondaryEscalationPolicy(1000);
        for(int i=0;i<100;i++) assertThat(sample.sampled(source)).isEqualTo(new SecondaryEscalationPolicy(1000).sampled(source));
        assertThat(sample.version()).isNotEqualTo(policy.version());assertThat(policy.sampled(source)).isFalse();
        long count=java.util.stream.IntStream.range(0,10000).mapToObj(i->new UUID(0,i)).filter(sample::sampled).count();
        assertThat(count).isBetween(700L,1300L);
        assertThatThrownBy(()->new SecondaryEscalationPolicy(1001)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SecondaryEscalationPolicy(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
