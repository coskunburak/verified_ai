package com.verifiedai.profile.application.tutoring;

import com.verifiedai.profile.application.LearningProfileApplicationService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TutorLearnerContext {
    private final LearningProfileApplicationService profiles;
    public TutorLearnerContext(LearningProfileApplicationService profiles) { this.profiles=profiles; }
    public Snapshot capture(UUID owner) {
        var profile=profiles.getCurrent(owner);
        return new Snapshot("tutor-context-v1",profile.version(),profile.explanationDepth(),profile.preferredLanguage(),"NOT_AVAILABLE");
    }
    public record Snapshot(String schemaVersion, Long profileVersion, String explanationDepth, String language, String mastery) {}
}
