package com.verifiedai.problem.application.canonicalization;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
class AttemptMathParserV2Test {
    @Test void conventionalPowerPrecedenceAndTokenOffsetsRemainExplicit() throws Exception {
        var n=new ObjectMapper().readTree(new AttemptMathParserV2(Set.of("x")).parse("-x^2"));
        assertThat(n.path("expression").path("kind").asText()).isEqualTo("UNARY");
        assertThat(n.path("expression").path("operand").path("operator").asText()).isEqualTo("POWER");
        assertThat(n.path("expression").path("tokenRange").path("end").asInt()).isEqualTo(4);
    }
    @Test void intervalMembershipAndExplicitWordModelHaveTypedSyntax() {
        assertThat(new AttemptMathParserV2(Set.of("x")).parse("x in Interval(-inf,2,true,false)")).contains("INTERVAL","MEMBER_OF","NEGATIVE_INFINITY");
        assertThat(new AttemptMathParserV2(Set.of("x")).parse("total(3,4)")).contains("TOTAL");
    }
    @Test void undeclaredSymbolsAndExecutableSyntaxAreRejected() {
        assertThatThrownBy(()->new AttemptMathParserV2(Set.of("x")).parse("y+1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new AttemptMathParserV2(Set.of("x")).parse("__import__(x)")).isInstanceOf(IllegalArgumentException.class);
    }
}
