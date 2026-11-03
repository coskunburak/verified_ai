package com.verifiedai.problem.application.attempt;
import java.util.Set;
/** Syntax only; null means unsupported. Never a correctness or OCR claim. */
public interface ManualStepNormalizer {
    default String normalizeV2(String text, Set<String> declaredVariables) { return normalize(text, declaredVariables); }
    String normalize(String text, Set<String> declaredVariables);
}
