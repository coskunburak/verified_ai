package com.verifiedai.admin.application;

import com.verifiedai.admin.infrastructure.AdminSupportWriteJdbcRepository;
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
 * GDPR / ADR-006 account lifecycle contributor for admin support artifacts (INV-ADM-006).
 * Exports PII-free wrong-answer report metadata (omitting payload_hash, idempotency_key,
 * and reviewer_user_id) and explicitly deletes user-owned grants and reports alongside
 * ON DELETE CASCADE while preserving non-PII operational admin_support_audit_logs.
 */
@Component
@Order(33)
public class AdminSupportPrivacyLifecycleContributor implements AccountDataLifecycleContributor {

    private final AdminSupportWriteJdbcRepository writeRepository;

    public AdminSupportPrivacyLifecycleContributor(AdminSupportWriteJdbcRepository writeRepository) {
        this.writeRepository = Objects.requireNonNull(writeRepository, "writeRepository must not be null");
    }

    @Override
    public String category() {
        return "admin_support";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        List<Map<String, Object>> reports = writeRepository.findWrongAnswerReportsForPrivacyExport(userId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category());
        payload.put("reportCount", reports.size());
        payload.put("wrongAnswerReports", reports);
        payload.put("trainingEligible", false);
        return Collections.unmodifiableMap(payload);
    }

    @Override
    @Transactional
    public void deleteUserData(UUID userId, Instant now) {
        Objects.requireNonNull(userId, "userId must not be null");
        writeRepository.deleteRawContentGrantsByUserId(userId);
        writeRepository.deleteWrongAnswerReportsByUserId(userId);
    }
}
