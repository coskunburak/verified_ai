package com.verifiedai.problem.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ProblemParseSelectionPolicy {
    boolean shouldSelectAiParse(UUID currentParseId) {
        return currentParseId == null;
    }

    boolean shouldSelectUserCorrection() {
        return true;
    }
}
