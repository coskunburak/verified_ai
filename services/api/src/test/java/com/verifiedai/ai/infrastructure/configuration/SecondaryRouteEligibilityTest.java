package com.verifiedai.ai.infrastructure.configuration;

import com.verifiedai.ai.application.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecondaryRouteEligibilityTest {
    AiRouteContext context(long budget) {
        return new AiRouteContext(AiCapability.SOLVE,"EQUATION","SOLVE_EQUATION",null,null,false,"LOW",null,null,
            Locale.ENGLISH,Duration.ofSeconds(30),budget,null,Map.of(),null,null,"NO_TRAINING_BY_DEFAULT",AiRouteContext.RouteRole.SECONDARY_SOLVER);
    }
    @Test void defaultNoApprovalPreventsExecution() {
        var route=new FixtureSecondaryRoute(false,"local",true,mock(PromptRegistryValidator.class));
        assertThatThrownBy(()->route.plan(context(0))).isInstanceOf(AiProviderException.class);
    }
    @Test void fixtureRequiresLocalEnvironmentAndDurableLedger() {
        for(String environment:List.of("prod","production","staging"))
            assertThatThrownBy(()->new FixtureSecondaryRoute(true,environment,true,mock(PromptRegistryValidator.class))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new FixtureSecondaryRoute(true,"test",false,mock(PromptRegistryValidator.class))).isInstanceOf(IllegalStateException.class);
    }
    @Test void separatelyNamedFixtureIsFreeSingleAttemptAndPromptBound() {
        var route=new FixtureSecondaryRoute(true,"test",true,mock(PromptRegistryValidator.class));var plan=route.plan(context(0));
        assertThat(plan.primary().model()).isEqualTo("local-fixture-secondary-solver-v1").isNotEqualTo("local-fixture-solver-v1");
        assertThat(plan.promptId()).isEqualTo("secondary-solver");assertThat(plan.fallbackChain()).isEmpty();
        assertThat(plan.maxAttempts()).isEqualTo(1);assertThat(route.attemptCeiling(plan,plan.primary())).isZero();
        assertThat(plan.maxCostMicros()).isZero();assertThat(plan.releaseStage()).isEqualTo(AiRoutePlan.ReleaseStage.LOCAL_ONLY);
    }
    @Test void secondaryApprovalCannotAuthorizePrimaryRole() {
        var route=new FixtureSecondaryRoute(true,"test",true,mock(PromptRegistryValidator.class));
        assertThatThrownBy(()->route.plan(AiRouteContext.basic(AiCapability.SOLVE,Duration.ofSeconds(30)))).isInstanceOf(AiProviderException.class);
    }
}
