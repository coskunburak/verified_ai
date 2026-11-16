package com.verifiedai.exam.application;

import com.verifiedai.exam.domain.ExamReadinessCalculator.ActiveTrapSignal;
import com.verifiedai.exam.domain.ExamReadinessCalculator.SkillMasterySignal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port for reading upstream mastery and active mistake DNA signals for deterministic exam readiness computation.
 */
public interface ExamReadinessSignalReader {

    Map<String, SkillMasterySignal> loadSkillMasterySignals(UUID userId);

    List<ActiveTrapSignal> loadActiveTrapSignals(UUID userId);
}
