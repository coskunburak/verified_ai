package com.verifiedai.billing.infrastructure.persistence;

import com.verifiedai.billing.domain.model.AppStoreEnvironment;
import com.verifiedai.billing.domain.model.AppStoreNotificationProcessingStatus;
import com.verifiedai.billing.domain.model.AppStoreProductMapping;
import com.verifiedai.billing.domain.model.AppStoreSubscriptionStatus;
import com.verifiedai.billing.domain.model.VerifiedAppStoreNotification;
import com.verifiedai.billing.domain.model.VerifiedAppStoreRenewalInfo;
import com.verifiedai.billing.domain.model.VerifiedAppStoreTransaction;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AppStoreBillingRepository {
    private final JdbcTemplate jdbcTemplate;

    public AppStoreBillingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID appAccountTokenForUser(UUID userId, Instant now) {
        UUID token = UUID.randomUUID();
        jdbcTemplate.update(
            """
            insert into commerce_account_tokens (user_id, app_account_token, created_at)
            values (?, ?, ?)
            on conflict (user_id) do nothing
            """,
            userId,
            token,
            timestamp(now)
        );
        return jdbcTemplate.query(
            "select app_account_token from commerce_account_tokens where user_id = ?",
            preparedStatement -> preparedStatement.setObject(1, userId),
            resultSet -> {
                if (!resultSet.next()) {
                    throw new IllegalStateException("App Account Token was not created for user");
                }
                return resultSet.getObject("app_account_token", UUID.class);
            }
        );
    }

    public boolean userActive(UUID userId) {
        return Boolean.TRUE.equals(jdbcTemplate.query(
            "select status = 'ACTIVE' from users where id = ?",
            preparedStatement -> preparedStatement.setObject(1, userId),
            resultSet -> resultSet.next() ? resultSet.getBoolean(1) : false
        ));
    }

    public Optional<UUID> findUserByAppAccountToken(UUID appAccountToken) {
        return jdbcTemplate.query(
            "select user_id from commerce_account_tokens where app_account_token = ?",
            preparedStatement -> preparedStatement.setObject(1, appAccountToken),
            resultSet -> resultSet.next()
                ? Optional.of(resultSet.getObject("user_id", UUID.class))
                : Optional.empty()
        );
    }

    public Optional<UUID> findTransactionOwner(AppStoreEnvironment environment, String transactionId) {
        return jdbcTemplate.query(
            """
            select user_id
            from app_store_transactions
            where environment = ? and transaction_id = ?
            """,
            preparedStatement -> {
                preparedStatement.setString(1, environment.name());
                preparedStatement.setString(2, transactionId);
            },
            resultSet -> resultSet.next()
                ? Optional.of(resultSet.getObject("user_id", UUID.class))
                : Optional.empty()
        );
    }

    public Optional<UUID> findSubscriptionOwner(AppStoreEnvironment environment, String originalTransactionId) {
        return jdbcTemplate.query(
            """
            select user_id
            from app_store_subscriptions
            where environment = ? and original_transaction_id = ?
            """,
            preparedStatement -> {
                preparedStatement.setString(1, environment.name());
                preparedStatement.setString(2, originalTransactionId);
            },
            resultSet -> resultSet.next()
                ? Optional.of(resultSet.getObject("user_id", UUID.class))
                : Optional.empty()
        );
    }

    public Optional<BillingEventRecord> findBillingEvent(String externalEventId) {
        return jdbcTemplate.query(
            """
            select payload_hash, result
            from billing_events
            where external_event_id = ?
            """,
            preparedStatement -> preparedStatement.setString(1, externalEventId),
            resultSet -> resultSet.next()
                ? Optional.of(new BillingEventRecord(resultSet.getString("payload_hash"), resultSet.getString("result")))
                : Optional.empty()
        );
    }

    public void upsertBillingEvent(
        String externalEventId,
        UUID userId,
        String eventType,
        String payloadHash,
        String result,
        Instant processedAt
    ) {
        jdbcTemplate.update(
            """
            insert into billing_events (id, external_event_id, user_id, event_type, payload_hash, result, processed_at)
            values (?, ?, ?, ?, ?, ?, ?)
            on conflict (external_event_id) do update set
                user_id = excluded.user_id,
                event_type = excluded.event_type,
                result = excluded.result,
                processed_at = excluded.processed_at
            where billing_events.payload_hash = excluded.payload_hash
            """,
            UUID.randomUUID(),
            externalEventId,
            userId,
            eventType,
            payloadHash,
            result,
            timestamp(processedAt)
        );
    }

    public boolean insertNotificationIfAbsent(
        VerifiedAppStoreNotification notification,
        String payloadDigest,
        Instant receivedAt
    ) {
        int rows = jdbcTemplate.update(
            """
            insert into app_store_notifications (
                id, notification_uuid, notification_type, subtype, environment, app_store_status, signed_date,
                processing_status, payload_digest, received_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (notification_uuid) do nothing
            """,
            UUID.randomUUID(),
            notification.notificationUuid(),
            notification.notificationType(),
            notification.subtype(),
            notification.environment().name(),
            notification.appStoreStatus(),
            timestamp(notification.signedDate()),
            AppStoreNotificationProcessingStatus.RECEIVED.name(),
            payloadDigest,
            timestamp(receivedAt)
        );
        return rows == 1;
    }

    public void markNotification(
        String notificationUuid,
        AppStoreNotificationProcessingStatus status,
        Instant processedAt,
        String failureCode,
        String failureMessage
    ) {
        jdbcTemplate.update(
            """
            update app_store_notifications
            set processing_status = ?,
                processed_at = ?,
                failure_code = ?,
                failure_message = ?,
                version = version + 1
            where notification_uuid = ?
            """,
            status.name(),
            status == AppStoreNotificationProcessingStatus.PROCESSING ? null : timestamp(processedAt),
            failureCode,
            abbreviate(failureMessage),
            notificationUuid
        );
    }

    public void upsertTransaction(UUID userId, VerifiedAppStoreTransaction transaction, String payloadDigest, Instant now) {
        upsertTransaction(userId, transaction, payloadDigest, now, null);
    }

    public boolean upsertTransaction(UUID userId, VerifiedAppStoreTransaction transaction, String payloadDigest, Instant now, String offerFamily) {
        UUID candidate = UUID.randomUUID();
        var ids = jdbcTemplate.queryForList(
            """
            insert into app_store_transactions (
                id, user_id, transaction_id, original_transaction_id, product_id, subscription_group_id,
                app_account_token, environment, purchase_date, original_purchase_date, expires_date,
                revocation_date, transaction_reason, ownership_type, signed_date, payload_digest, created_at, updated_at, offer_family
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (environment, transaction_id) do update set
                user_id = excluded.user_id,
                original_transaction_id = excluded.original_transaction_id,
                product_id = excluded.product_id,
                subscription_group_id = excluded.subscription_group_id,
                app_account_token = excluded.app_account_token,
                purchase_date = excluded.purchase_date,
                original_purchase_date = excluded.original_purchase_date,
                expires_date = excluded.expires_date,
                revocation_date = excluded.revocation_date,
                transaction_reason = excluded.transaction_reason,
                ownership_type = excluded.ownership_type,
                signed_date = excluded.signed_date,
                payload_digest = excluded.payload_digest,
                updated_at = excluded.updated_at
            where app_store_transactions.user_id = excluded.user_id
              and app_store_transactions.revocation_date is null
              and (excluded.revocation_date is not null or app_store_transactions.signed_date is null
                   or excluded.signed_date >= app_store_transactions.signed_date)
            returning id
            """,
            UUID.class,
            candidate,
            userId,
            transaction.transactionId(),
            transaction.originalTransactionId(),
            transaction.productId(),
            transaction.subscriptionGroupId(),
            transaction.appAccountToken(),
            transaction.environment().name(),
            timestamp(transaction.purchaseDate()),
            timestamp(transaction.originalPurchaseDate()),
            timestamp(transaction.expiresDate()),
            timestamp(transaction.revocationDate()),
            transaction.transactionReason(),
            transaction.ownershipType(),
            timestamp(transaction.signedDate()),
            payloadDigest,
            timestamp(now),
            timestamp(now),
            offerFamily
        );
        return ids.contains(candidate);
    }

    public void upsertSubscription(
        UUID userId,
        VerifiedAppStoreTransaction transaction,
        VerifiedAppStoreRenewalInfo renewalInfo,
        AppStoreProductMapping product,
        AppStoreSubscriptionStatus status,
        Instant now, Instant stateSignedAt
    ) {
        if (status == AppStoreSubscriptionStatus.REVOKED) {
            jdbcTemplate.update("""
                update app_store_transactions set access_revoked=true
                where user_id=? and environment=? and transaction_id=?
                """, userId, transaction.environment().name(), transaction.transactionId());
        }
        jdbcTemplate.update(
            """
            insert into app_store_subscriptions (
                id, user_id, original_transaction_id, current_transaction_id, product_id, subscription_group_id,
                environment, status, auto_renew_status, renewal_product_id, expires_at, grace_period_expires_at,
                revoked_at, last_transaction_signed_at, last_renewal_signed_at, last_reconciled_at, created_at, updated_at, state_signed_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (environment, original_transaction_id) do update set
                user_id = excluded.user_id,
                current_transaction_id = excluded.current_transaction_id,
                product_id = excluded.product_id,
                subscription_group_id = excluded.subscription_group_id,
                status = excluded.status,
                state_signed_at = excluded.state_signed_at,
                auto_renew_status = excluded.auto_renew_status,
                renewal_product_id = excluded.renewal_product_id,
                expires_at = excluded.expires_at,
                grace_period_expires_at = excluded.grace_period_expires_at,
                revoked_at = excluded.revoked_at,
                last_transaction_signed_at = excluded.last_transaction_signed_at,
                last_renewal_signed_at = excluded.last_renewal_signed_at,
                last_reconciled_at = excluded.last_reconciled_at,
                updated_at = excluded.updated_at,
                version = app_store_subscriptions.version + 1
            where app_store_subscriptions.user_id = excluded.user_id
              and ((excluded.status='REVOKED' and excluded.current_transaction_id=app_store_subscriptions.current_transaction_id)
                or ((app_store_subscriptions.status <> 'REVOKED'
                     or excluded.current_transaction_id <> app_store_subscriptions.current_transaction_id)
                    and (app_store_subscriptions.state_signed_at is null
                      or excluded.state_signed_at > app_store_subscriptions.state_signed_at
                      or (excluded.state_signed_at = app_store_subscriptions.state_signed_at
                        and (excluded.status = app_store_subscriptions.status or excluded.status <> 'ACTIVE')))
                    and not exists (select 1 from app_store_transactions old_t
                        where old_t.environment=app_store_subscriptions.environment
                          and old_t.transaction_id=app_store_subscriptions.current_transaction_id
                          and old_t.purchase_date > (select new_t.purchase_date from app_store_transactions new_t
                              where new_t.environment=excluded.environment and new_t.transaction_id=excluded.current_transaction_id))
                    and (app_store_subscriptions.last_transaction_signed_at is null
                      or excluded.last_transaction_signed_at >= app_store_subscriptions.last_transaction_signed_at)
                    and (app_store_subscriptions.last_renewal_signed_at is null
                      or excluded.last_renewal_signed_at >= app_store_subscriptions.last_renewal_signed_at
                      or excluded.last_transaction_signed_at > app_store_subscriptions.last_transaction_signed_at)
                    and not exists (select 1 from app_store_transactions t where t.environment=excluded.environment
                      and t.transaction_id=excluded.current_transaction_id and (t.access_revoked or t.revocation_date is not null))))
            """,
            UUID.randomUUID(),
            userId,
            transaction.originalTransactionId(),
            transaction.transactionId(),
            product.appStoreProductId(),
            product.subscriptionGroupId(),
            transaction.environment().name(),
            status.name(),
            renewalInfo == null ? null : renewalInfo.autoRenewStatus(),
            renewalInfo == null ? null : renewalInfo.autoRenewProductId(),
            timestamp(transaction.expiresDate()),
            renewalInfo == null ? null : timestamp(renewalInfo.gracePeriodExpiresDate()),
            timestamp(transaction.revocationDate()),
            timestamp(transaction.signedDate()),
            renewalInfo == null ? null : timestamp(renewalInfo.signedDate()),
            timestamp(now),
            timestamp(now),
            timestamp(now), timestamp(stateSignedAt)
        );
    }

    public java.util.List<SubscriptionProjection> subscriptions(UUID userId) {
        return jdbcTemplate.query("""
            select s.product_id, s.environment, s.original_transaction_id, s.expires_at, s.grace_period_expires_at,
                t.offer_family, t.purchase_date, case when t.access_revoked or t.revocation_date is not null
                then 'REVOKED' else s.status end as status
            from app_store_subscriptions s join app_store_transactions t on t.user_id=s.user_id
                and t.environment=s.environment and t.transaction_id=s.current_transaction_id
                and t.product_id=s.product_id and t.original_transaction_id=s.original_transaction_id
            where s.user_id=? order by s.updated_at desc, s.original_transaction_id
            """, (rs, row) -> mapSubscriptionProjection(rs), userId);
    }

    public record BillingEventRecord(String payloadHash, String result) {
    }

    public record SubscriptionProjection(
        String productId,
        AppStoreSubscriptionStatus status,
        Instant expiresAt,
        String originalTransactionId,
        AppStoreEnvironment environment, Instant graceUntil, Instant purchasedAt, boolean season
    ) {
    }

    private static SubscriptionProjection mapSubscriptionProjection(ResultSet resultSet) throws SQLException {
        Timestamp expiresAt = resultSet.getTimestamp("expires_at");
        return new SubscriptionProjection(
            resultSet.getString("product_id"),
            AppStoreSubscriptionStatus.valueOf(resultSet.getString("status")),
            expiresAt == null ? null : expiresAt.toInstant(),
            resultSet.getString("original_transaction_id"),
            AppStoreEnvironment.valueOf(resultSet.getString("environment")),
            resultSet.getTimestamp("grace_period_expires_at") == null ? null : resultSet.getTimestamp("grace_period_expires_at").toInstant(),
            resultSet.getTimestamp("purchase_date") == null ? null : resultSet.getTimestamp("purchase_date").toInstant(),
            "YKS_SEASON_PASS".equals(resultSet.getString("offer_family"))
        );
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static String abbreviate(String value) {
        if (value == null || value.length() <= 255) {
            return value;
        }
        return value.substring(0, 255);
    }
}
