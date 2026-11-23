package com.verifiedai.featureflags.application;

import com.verifiedai.featureflags.infrastructure.FeatureFlagJdbcRepository;
import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * GDPR / ADR-006 account lifecycle contributor for user-scoped experiment assignments (INV-FF-006).
 * Exports strictly PII-free assignment records and deletes user rows explicitly alongside ON DELETE CASCADE.
 */
@Component
@Order(32)
public class FeatureFlagPrivacyLifecycleContributor implements AccountDataLifecycleContributor {

    private final FeatureFlagJdbcRepository repository;

    public FeatureFlagPrivacyLifecycleContributor(FeatureFlagJdbcRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public String category() {
        return "featureflags";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        List<Map<String, Object>> assignments = repository.findUserAssignmentsForExport(userId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category());
        payload.put("assignmentCount", assignments.size());
        payload.put("experimentAssignments", assignments);
        payload.put("trainingEligible", false);
        return Collections.unmodifiableMap(payload);
    }

    @Override
    @Transactional
    public void deleteUserData(UUID userId, Instant now) {
        Objects.requireNonNull(userId, "userId must not be null");
        repository.deleteAssignmentsByUserId(userId);
    }
}
