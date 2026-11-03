package com.verifiedai.problem.application.canonicalization;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CanonicalManualStepNormalizerTest {
    final CanonicalManualStepNormalizer normalizer=new CanonicalManualStepNormalizer();
    @Test void manualSyntaxIsTypedWithoutBecomingEvidence() {
        assertThat(normalizer.normalize("2*x + 2 = 6",Set.of("x"))).contains("RELATION","EQUALS","MULTIPLY");
        assertThat(normalizer.normalize("(x+1)^2",Set.of("x"))).contains("POWER");
    }
    @Test void unsafeUnknownDeepAndAmbiguousSyntaxNeedsReview() {
        for(var input:java.util.List.of("__import__('os')","y+1","-x^2","x^(-2)","x < 2","x=2=3","abs(x)",".5*x","01*x","(".repeat(40)+"x"+")".repeat(40)))
            assertThat(normalizer.normalize(input,Set.of("x"))).as(input).isNull();
    }
}
