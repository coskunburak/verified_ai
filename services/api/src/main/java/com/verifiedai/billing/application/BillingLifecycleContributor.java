package com.verifiedai.billing.application;

import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class BillingLifecycleContributor implements AccountDataLifecycleContributor {
    private final JdbcTemplate jdbcTemplate;
    private final EntitlementApplicationService entitlementApplicationService;

    BillingLifecycleContributor(JdbcTemplate jdbcTemplate, EntitlementApplicationService entitlementApplicationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.entitlementApplicationService = entitlementApplicationService;
    }

    @Override
    public String category() {
        return "billing";
    }

    @Override
    public Map<String, Object> exportUserData(UUID userId) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("entitlement", entitlement(userId));
        export.put("appStoreSubscriptions", appStoreSubscriptions(userId));
        export.put("appStoreTransactions", appStoreTransactions(userId));
        export.put("retentionNote", "Raw App Store JWS and payment credentials are excluded; minimized transaction references are retained for audit/refund/fraud handling.");
        return export;
    }

    @Override
    public void deleteUserData(UUID userId, Instant now) {
        jdbcTemplate.update("delete from commerce_account_tokens where user_id = ?", userId);
        entitlementApplicationService.applyAccountDeleted(userId, now);
        jdbcTemplate.update(
            """
            insert into billing_events (id, external_event_id, user_id, event_type, payload_hash, result, processed_at)
            values (?, ?, ?, 'ACCOUNT_DELETED_COMMERCE_RETAINED', ?, 'RETAINED_MINIMIZED', ?)
            on conflict (external_event_id) do nothing
            """,
            UUID.randomUUID(),
            "account-deleted:" + userId,
            userId,
            "account-deletion",
            java.sql.Timestamp.from(now)
        );
    }

    private Map<String, Object> entitlement(UUID userId) {
        return jdbcTemplate.query(
            """
            select tier, source, status, effective_at, expires_at, original_transaction_id, environment, last_verified_at
            from entitlements
            where user_id = ?
            """,
            preparedStatement -> preparedStatement.setObject(1, userId),
            resultSet -> {
                if (!resultSet.next()) {
                    return Map.of("exists", false);
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("exists", true);
                row.put("tier", resultSet.getString("tier"));
                row.put("source", resultSet.getString("source"));
                row.put("status", resultSet.getString("status"));
                row.put("effectiveAt", instantString(resultSet.getTimestamp("effective_at")));
                row.put("expiresAt", instantString(resultSet.getTimestamp("expires_at")));
                row.put("originalTransactionId", resultSet.getString("original_transaction_id"));
                row.put("environment", resultSet.getString("environment"));
                row.put("lastVerifiedAt", instantString(resultSet.getTimestamp("last_verified_at")));
                return row;
            }
        );
    }

    private List<Map<String, Object>> appStoreSubscriptions(UUID userId) {
        return jdbcTemplate.query(
            """
            select original_transaction_id, current_transaction_id, product_id, environment, status, expires_at, revoked_at, last_reconciled_at
            from app_store_subscriptions
            where user_id = ?
            order by updated_at desc
            """,
            (resultSet, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("originalTransactionId", resultSet.getString("original_transaction_id"));
                row.put("currentTransactionId", resultSet.getString("current_transaction_id"));
                row.put("productId", resultSet.getString("product_id"));
                row.put("environment", resultSet.getString("environment"));
                row.put("status", resultSet.getString("status"));
                row.put("expiresAt", instantString(resultSet.getTimestamp("expires_at")));
                row.put("revokedAt", instantString(resultSet.getTimestamp("revoked_at")));
                row.put("lastReconciledAt", instantString(resultSet.getTimestamp("last_reconciled_at")));
                return row;
            },
            userId
        );
    }

    private List<Map<String, Object>> appStoreTransactions(UUID userId) {
        return jdbcTemplate.query(
            """
            select transaction_id, original_transaction_id, product_id, environment, purchase_date, expires_date, revocation_date
            from app_store_transactions
            where user_id = ?
            order by purchase_date desc nulls last
            limit 50
            """,
            (resultSet, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("transactionId", resultSet.getString("transaction_id"));
                row.put("originalTransactionId", resultSet.getString("original_transaction_id"));
                row.put("productId", resultSet.getString("product_id"));
                row.put("environment", resultSet.getString("environment"));
                row.put("purchaseDate", instantString(resultSet.getTimestamp("purchase_date")));
                row.put("expiresDate", instantString(resultSet.getTimestamp("expires_date")));
                row.put("revocationDate", instantString(resultSet.getTimestamp("revocation_date")));
                return row;
            },
            userId
        );
    }

    private static String instantString(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }
}
