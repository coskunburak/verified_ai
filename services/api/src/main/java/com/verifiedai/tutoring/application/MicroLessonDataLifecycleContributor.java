package com.verifiedai.tutoring.application;

import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import com.verifiedai.tutoring.domain.MicroLessonCompletionRecord;
import com.verifiedai.tutoring.domain.MicroLessonRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(28)
public class MicroLessonDataLifecycleContributor implements AccountDataLifecycleContributor {

    private static final Logger log = LoggerFactory.getLogger(MicroLessonDataLifecycleContributor.class);

    private final MicroLessonRepository repository;

    public MicroLessonDataLifecycleContributor(MicroLessonRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public String category() {
        return "micro_lessons";
    }

    @Override
    public Map<String, Object> exportUserData(UUID owner) {
        List<MicroLessonCompletionRecord> records = repository.findCompletionsByUserId(owner);
        List<Map<String, Object>> completions = records.stream()
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("completionId", r.completionId());
                    row.put("cardId", r.cardId());
                    row.put("mistakeCode", r.mistakeCode());
                    row.put("locale", r.locale());
                    if (r.sourceAttemptId() != null) {
                        row.put("sourceAttemptId", r.sourceAttemptId());
                    }
                    row.put("triggerSource", r.triggerSource().name());
                    row.put("selectedOptionId", r.selectedOptionId());
                    row.put("quickCheckPassed", r.quickCheckPassed());
                    row.put("dwellTimeMs", r.dwellTimeMs());
                    row.put("trainingEligible", false);
                    row.put("completedAt", r.completedAt());
                    return row;
                })
                .toList();

        return Map.of(
                "schemaVersion", "micro-lesson-export-v1",
                "trainingEligible", false,
                "completions", completions
        );
    }

    @Override
    public void deleteUserData(UUID owner, Instant now) {
        int deleted = repository.deleteCompletionsByUserId(owner);
        log.info("micro_lesson.privacy_purge deletedCompletions={}", deleted);
    }
}
