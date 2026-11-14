package com.verifiedai.exam.infrastructure.privacy;

import com.verifiedai.exam.application.ExamRepository;
import com.verifiedai.exam.domain.ExamModels.StudentExamTargetRecord;
import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * GDPR / KVKK Article 17 lifecycle contributor for exam targets and pinned snapshots.
 */
@Component
public class ExamPrivacyLifecycleContributor implements AccountDataLifecycleContributor {

    private static final Logger LOG = LoggerFactory.getLogger(ExamPrivacyLifecycleContributor.class);

    private final ExamRepository examRepository;

    public ExamPrivacyLifecycleContributor(ExamRepository examRepository) {
        this.examRepository = Objects.requireNonNull(examRepository, "examRepository cannot be null");
    }

    @Override
    public String category() {
        return "exam";
    }

    @Override
    public Map<String, Object> exportUserData(UUID userId) {
        List<StudentExamTargetRecord> targets = examRepository.findTargetsByUserId(userId);
        List<Map<String, Object>> exportedTargets = targets.stream()
                .map(target -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("targetId", target.targetId().toString());
                    entry.put("examCode", target.examCode());
                    entry.put("examVersion", target.examVersion());
                    entry.put("blueprintHash", target.blueprintHash());
                    entry.put("targetScore", target.targetScore());
                    entry.put("targetDate", target.targetDate().toString());
                    entry.put("priorityRank", target.priorityRank());
                    entry.put("targetStatus", target.targetStatus().name());
                    entry.put("isPrimary", target.isPrimary());
                    entry.put("llmTokensUsed", target.llmTokensUsed());
                    entry.put("trainingEligible", target.trainingEligible());
                    entry.put("supersededAt", target.supersededAt() != null ? target.supersededAt().toString() : null);
                    entry.put("createdAt", target.createdAt().toString());
                    entry.put("updatedAt", target.updatedAt().toString());
                    return entry;
                })
                .toList();

        Map<String, Object> exportMap = new LinkedHashMap<>();
        exportMap.put("category", category());
        exportMap.put("studentTargetCount", exportedTargets.size());
        exportMap.put("studentTargets", exportedTargets);
        exportMap.put("llmTokensUsed", 0);
        exportMap.put("trainingEligible", false);
        return exportMap;
    }

    @Override
    public void deleteUserData(UUID userId, Instant now) {
        int deletedTargets = examRepository.deleteTargetsByUserId(userId);
        LOG.info("Privacy purge completed for exam module: deletedTargets={}", deletedTargets);
    }
}
