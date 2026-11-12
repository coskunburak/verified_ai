package com.verifiedai.studyplan.service;

import com.verifiedai.studyplan.engine.SkillCandidateSignal;

import java.util.List;
import java.util.UUID;

public interface NextBestActionSignalProvider {
    List<SkillCandidateSignal> fetchSignalsForLearner(UUID userId);
}
