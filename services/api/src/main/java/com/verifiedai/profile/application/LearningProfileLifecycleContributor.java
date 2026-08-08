package com.verifiedai.profile.application;

import com.verifiedai.profile.infrastructure.persistence.LearningProfileJpaRepository;
import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class LearningProfileLifecycleContributor implements AccountDataLifecycleContributor {
    private final LearningProfileJpaRepository profileRepository;
    private final JdbcTemplate jdbcTemplate;

    LearningProfileLifecycleContributor(LearningProfileJpaRepository profileRepository, JdbcTemplate jdbcTemplate) {
        this.profileRepository = profileRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String category() {
        return "learningProfile";
    }

    @Override
    public Map<String, Object> exportUserData(UUID userId) {
        return profileRepository.findByUserId(userId)
            .map(profile -> {
                Map<String, Object> export = new LinkedHashMap<>();
                export.put("exists", true);
                export.put("educationLevel", profile.educationLevel());
                export.put("preferredLanguage", profile.preferredLanguage());
                export.put("explanationDepth", profile.explanationDepth());
                export.put("dailyStudyMinutes", profile.dailyStudyMinutes());
                export.put("timezone", profile.timezone());
                export.put("goalContext", profile.goalContext());
                export.put("onboardingStatus", profile.onboardingStatus());
                export.put("createdAt", profile.createdAt().toString());
                export.put("updatedAt", profile.updatedAt().toString());
                return export;
            })
            .orElseGet(() -> Map.of("exists", false));
    }

    @Override
    public void deleteUserData(UUID userId, Instant now) {
        jdbcTemplate.update("delete from learning_profiles where user_id = ?", userId);
    }
}
