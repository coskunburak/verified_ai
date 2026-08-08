package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.AppStoreEnvironment;
import com.verifiedai.billing.domain.model.AppStoreNotificationProcessingStatus;
import com.verifiedai.billing.domain.model.AppStoreProductMapping;
import com.verifiedai.billing.domain.model.AppStoreSubscriptionStatus;
import com.verifiedai.billing.domain.model.EntitlementStatus;
import com.verifiedai.billing.domain.model.VerifiedAppStoreNotification;
import com.verifiedai.billing.domain.model.VerifiedAppStoreRenewalInfo;
import com.verifiedai.billing.domain.model.VerifiedAppStoreTransaction;
import com.verifiedai.billing.domain.port.AppStoreGatewayUnavailableException;
import com.verifiedai.billing.domain.port.AppStoreServerGateway;
import com.verifiedai.billing.domain.port.AppStoreSignedDataVerifier;
import com.verifiedai.billing.domain.port.AppStoreSubscriptionStatusRecord;
import com.verifiedai.billing.domain.port.AppStoreVerificationFailedException;
import com.verifiedai.billing.infrastructure.persistence.AppStoreBillingRepository;
import com.verifiedai.billing.infrastructure.persistence.AppStoreBillingRepository.BillingEventRecord;
import com.verifiedai.billing.infrastructure.persistence.AppStoreBillingRepository.SubscriptionProjection;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppleBillingApplicationService {
    private final AppleProductCatalog productCatalog;
    private final AppStoreSignedDataVerifier signedDataVerifier;
    private final AppStoreServerGateway appStoreServerGateway;
    private final AppStoreBillingRepository billingRepository;
    private final EntitlementApplicationService entitlementApplicationService;
    private final Clock clock;
    private final AppleBillingMetrics metrics;

    AppleBillingApplicationService(
        AppleProductCatalog productCatalog,
        AppStoreSignedDataVerifier signedDataVerifier,
        AppStoreServerGateway appStoreServerGateway,
        AppStoreBillingRepository billingRepository,
        EntitlementApplicationService entitlementApplicationService,
        Clock clock,
        AppleBillingMetrics metrics
    ) {
        this.productCatalog = productCatalog;
        this.signedDataVerifier = signedDataVerifier;
        this.appStoreServerGateway = appStoreServerGateway;
        this.billingRepository = billingRepository;
        this.entitlementApplicationService = entitlementApplicationService;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional
    public AppleBillingConfigurationResult configuration(UUID userId) {
        requireActiveAccount(userId);
        UUID appAccountToken = billingRepository.appAccountTokenForUser(userId, clock.instant());
        return productCatalog.configurationFor(appAccountToken);
    }

    @Transactional
    public ApplePurchaseEvidenceResult submitTransaction(
        UUID userId,
        String signedTransactionInfo,
        String idempotencyKey
    ) {
        requireActiveAccount(userId);
        requireIdempotencyKey(idempotencyKey);
        if (signedTransactionInfo == null || signedTransactionInfo.isBlank()) {
            throw problem(HttpStatus.BAD_REQUEST, ApiErrorCode.PURCHASE_EVIDENCE_REJECTED, "Signed transaction evidence is required", "RETRY");
        }

        String payloadDigest = digest(signedTransactionInfo);
        String externalEventId = "purchase:" + userId + ":" + idempotencyKey.trim();
        boolean duplicate = idempotentDuplicate(externalEventId, payloadDigest);
        Instant now = clock.instant();

        VerifiedAppStoreTransaction transaction = verifyTransaction(signedTransactionInfo);
        validateConfiguredEnvironment(transaction.environment());
        AppStoreProductMapping product = requireProduct(transaction.productId());
        validateAppAccountTokenOwnership(userId, transaction, now);
        validateTransactionOwnership(userId, transaction);

        AppStoreSubscriptionStatus subscriptionStatus = determineStatus(transaction, null, null, now);
        billingRepository.upsertTransaction(userId, transaction, payloadDigest, now);
        billingRepository.upsertSubscription(userId, transaction, null, product, subscriptionStatus, now);
        EntitlementResult entitlement = recalculateEntitlement(userId, now);
        billingRepository.upsertBillingEvent(
            externalEventId,
            userId,
            "PURCHASE_TRANSACTION",
            payloadDigest,
            subscriptionStatus.name(),
            now
        );
        metrics.purchaseEvidenceAccepted(subscriptionStatus, duplicate);
        return new ApplePurchaseEvidenceResult(
            transaction.transactionId(),
            transaction.originalTransactionId(),
            subscriptionStatus,
            entitlement,
            duplicate
        );
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public AppleNotificationIngestionResult ingestNotification(String signedPayload) {
        if (signedPayload == null || signedPayload.isBlank()) {
            throw problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.APP_STORE_NOTIFICATION_INVALID,
                "Apple notification signedPayload is required",
                "RETRY"
            );
        }

        Instant now = clock.instant();
        String payloadDigest = digest(signedPayload);
        VerifiedAppStoreNotification notification = verifyNotification(signedPayload);
        boolean inserted = billingRepository.insertNotificationIfAbsent(notification, payloadDigest, now);
        if (!inserted) {
            metrics.notificationProcessed(notification.notificationType(), AppStoreNotificationProcessingStatus.PROCESSED);
            return new AppleNotificationIngestionResult(
                notification.notificationUuid(),
                AppStoreNotificationProcessingStatus.PROCESSED.name(),
                null,
                null
            );
        }

        billingRepository.markNotification(
            notification.notificationUuid(),
            AppStoreNotificationProcessingStatus.PROCESSING,
            now,
            null,
            null
        );
        try {
            AppleNotificationIngestionResult result = processNotification(notification, payloadDigest, now);
            billingRepository.markNotification(
                notification.notificationUuid(),
                AppStoreNotificationProcessingStatus.PROCESSED,
                now,
                null,
                null
            );
            metrics.notificationProcessed(notification.notificationType(), AppStoreNotificationProcessingStatus.PROCESSED);
            return result;
        } catch (ApiProblemException exception) {
            billingRepository.markNotification(
                notification.notificationUuid(),
                AppStoreNotificationProcessingStatus.FAILED_TERMINAL,
                now,
                exception.code().name(),
                exception.getMessage()
            );
            metrics.notificationProcessed(notification.notificationType(), AppStoreNotificationProcessingStatus.FAILED_TERMINAL);
            return new AppleNotificationIngestionResult(
                notification.notificationUuid(),
                AppStoreNotificationProcessingStatus.FAILED_TERMINAL.name(),
                null,
                null
            );
        }
    }

    @Transactional
    public ApplePurchaseEvidenceResult reconcileFromTransaction(UUID userId, String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            throw problem(HttpStatus.BAD_REQUEST, ApiErrorCode.APP_STORE_RECONCILIATION_FAILED, "Transaction id is required", "RETRY");
        }
        try {
            ApplePurchaseEvidenceResult result = null;
            for (AppStoreSubscriptionStatusRecord subscriptionStatus : appStoreServerGateway.getAllSubscriptionStatuses(transactionId)) {
                if (subscriptionStatus.signedTransactionInfo() == null || subscriptionStatus.signedTransactionInfo().isBlank()) {
                    continue;
                }
                result = processSubscriptionStatus(userId, subscriptionStatus, clock.instant());
            }
            if (result == null) {
                for (String signedTransaction : appStoreServerGateway.getTransactionHistory(transactionId)) {
                    result = submitTransaction(userId, signedTransaction, "reconcile-" + digest(signedTransaction));
                }
            }
            if (result == null) {
                String signedTransaction = appStoreServerGateway.getTransactionInfo(transactionId);
                result = submitTransaction(userId, signedTransaction, "reconcile-" + digest(signedTransaction));
            }
            return result;
        } catch (AppStoreGatewayUnavailableException exception) {
            throw problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.APP_STORE_RECONCILIATION_FAILED,
                "App Store reconciliation is temporarily unavailable",
                "RETRY"
            );
        }
    }

    private ApplePurchaseEvidenceResult processSubscriptionStatus(
        UUID userId,
        AppStoreSubscriptionStatusRecord subscriptionStatus,
        Instant now
    ) {
        String signedTransactionInfo = subscriptionStatus.signedTransactionInfo();
        String payloadDigest = digest(signedTransactionInfo);
        VerifiedAppStoreTransaction transaction = verifyTransaction(signedTransactionInfo);
        VerifiedAppStoreRenewalInfo renewalInfo = verifyRenewalInfo(subscriptionStatus.signedRenewalInfo());
        validateConfiguredEnvironment(transaction.environment());
        AppStoreProductMapping product = requireProduct(transaction.productId());
        validateAppAccountTokenOwnership(userId, transaction, now);
        validateTransactionOwnership(userId, transaction);

        AppStoreSubscriptionStatus status = subscriptionStatus.appStoreStatus() == null
            ? determineStatus(transaction, renewalInfo, null, now)
            : statusFromAppleRawStatus(subscriptionStatus.appStoreStatus(), transaction, renewalInfo, null, now);
        billingRepository.upsertTransaction(userId, transaction, payloadDigest, now);
        billingRepository.upsertSubscription(userId, transaction, renewalInfo, product, status, now);
        EntitlementResult entitlement = recalculateEntitlement(userId, now);
        billingRepository.upsertBillingEvent(
            "reconcile-status:" + userId + ":" + transaction.originalTransactionId() + ":" + payloadDigest,
            userId,
            "APP_STORE_SUBSCRIPTION_STATUS",
            payloadDigest,
            status.name(),
            now
        );
        return new ApplePurchaseEvidenceResult(
            transaction.transactionId(),
            transaction.originalTransactionId(),
            status,
            entitlement,
            false
        );
    }

    private AppleNotificationIngestionResult processNotification(
        VerifiedAppStoreNotification notification,
        String payloadDigest,
        Instant now
    ) {
        VerifiedAppStoreTransaction transaction = notification.transaction();
        if (transaction == null) {
            return new AppleNotificationIngestionResult(
                notification.notificationUuid(),
                AppStoreNotificationProcessingStatus.PROCESSED.name(),
                null,
                null
            );
        }
        validateConfiguredEnvironment(transaction.environment());
        AppStoreProductMapping product = requireProduct(transaction.productId());
        UUID userId = resolveNotificationUser(notification)
            .orElseThrow(() -> problem(
                HttpStatus.ACCEPTED,
                ApiErrorCode.APP_STORE_NOTIFICATION_INVALID,
                "Apple notification does not match a known platform account",
                "CONTACT_SUPPORT"
            ));
        validateTransactionOwnership(userId, transaction);

        AppStoreSubscriptionStatus subscriptionStatus = determineStatus(
            transaction,
            notification.renewalInfo(),
            notification,
            now
        );
        billingRepository.upsertTransaction(userId, transaction, payloadDigest, now);
        billingRepository.upsertSubscription(userId, transaction, notification.renewalInfo(), product, subscriptionStatus, now);
        EntitlementResult entitlement = recalculateEntitlement(userId, now);
        billingRepository.upsertBillingEvent(
            "notification:" + notification.notificationUuid(),
            userId,
            notification.notificationType(),
            payloadDigest,
            subscriptionStatus.name(),
            now
        );
        return new AppleNotificationIngestionResult(
            notification.notificationUuid(),
            AppStoreNotificationProcessingStatus.PROCESSED.name(),
            subscriptionStatus,
            entitlement
        );
    }

    private EntitlementResult recalculateEntitlement(UUID userId, Instant now) {
        if (!billingRepository.userActive(userId)) {
            return entitlementApplicationService.applyAccountDeleted(userId, now);
        }
        Optional<SubscriptionProjection> subscription = billingRepository.highestAccessibleSubscription(userId);
        if (subscription.isEmpty()) {
            return entitlementApplicationService.applyDefaultFree(userId, now);
        }
        SubscriptionProjection projection = subscription.get();
        AppStoreProductMapping product = productCatalog.findByAppStoreProductId(projection.productId())
            .orElse(null);
        if (product == null) {
            return entitlementApplicationService.applyDefaultFree(userId, now);
        }
        return entitlementApplicationService.applyAppStoreSubscription(
            userId,
            product.entitlementTier(),
            EntitlementStatus.valueOf(projection.status().name()),
            now,
            projection.expiresAt(),
            projection.originalTransactionId(),
            projection.environment(),
            now
        );
    }

    private void requireActiveAccount(UUID userId) {
        if (!billingRepository.userActive(userId)) {
            throw problem(HttpStatus.FORBIDDEN, ApiErrorCode.ACCOUNT_NOT_ACTIVE, "Account is not active", "SIGN_IN");
        }
    }

    private Optional<UUID> resolveNotificationUser(VerifiedAppStoreNotification notification) {
        VerifiedAppStoreTransaction transaction = notification.transaction();
        if (transaction.appAccountToken() != null) {
            Optional<UUID> owner = billingRepository.findUserByAppAccountToken(transaction.appAccountToken());
            if (owner.isPresent()) {
                return owner;
            }
        }
        return billingRepository.findSubscriptionOwner(transaction.environment(), transaction.originalTransactionId());
    }

    private void validateAppAccountTokenOwnership(UUID userId, VerifiedAppStoreTransaction transaction, Instant now) {
        UUID expectedToken = billingRepository.appAccountTokenForUser(userId, now);
        UUID appAccountToken = transaction.appAccountToken();
        if (appAccountToken == null) {
            Optional<UUID> subscriptionOwner = billingRepository.findSubscriptionOwner(
                transaction.environment(),
                transaction.originalTransactionId()
            );
            if (subscriptionOwner.filter(userId::equals).isPresent()) {
                return;
            }
            metrics.purchaseEvidenceRejected("missing_app_account_token");
            throw problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.PURCHASE_EVIDENCE_REJECTED,
                "Transaction is missing the expected App Account Token",
                "RESTORE"
            );
        }

        Optional<UUID> appAccountOwner = billingRepository.findUserByAppAccountToken(appAccountToken);
        if (appAccountOwner.isPresent() && !appAccountOwner.get().equals(userId)) {
            metrics.purchaseEvidenceRejected("app_account_token_owned_by_other_user");
            throw problem(
                HttpStatus.CONFLICT,
                ApiErrorCode.PURCHASE_ALREADY_OWNED_BY_OTHER_ACCOUNT,
                "Transaction belongs to another platform account",
                "CONTACT_SUPPORT"
            );
        }
        if (!expectedToken.equals(appAccountToken)) {
            metrics.purchaseEvidenceRejected("app_account_token_mismatch");
            throw problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.PURCHASE_EVIDENCE_REJECTED,
                "Transaction App Account Token does not match the authenticated account",
                "RESTORE"
            );
        }
    }

    private void validateTransactionOwnership(UUID userId, VerifiedAppStoreTransaction transaction) {
        billingRepository.findTransactionOwner(transaction.environment(), transaction.transactionId())
            .filter(owner -> !owner.equals(userId))
            .ifPresent(owner -> {
                metrics.purchaseEvidenceRejected("transaction_owned_by_other_user");
                throw problem(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.PURCHASE_ALREADY_OWNED_BY_OTHER_ACCOUNT,
                    "Transaction belongs to another platform account",
                    "CONTACT_SUPPORT"
                );
            });
        billingRepository.findSubscriptionOwner(transaction.environment(), transaction.originalTransactionId())
            .filter(owner -> !owner.equals(userId))
            .ifPresent(owner -> {
                metrics.purchaseEvidenceRejected("subscription_owned_by_other_user");
                throw problem(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.PURCHASE_ALREADY_OWNED_BY_OTHER_ACCOUNT,
                    "Subscription belongs to another platform account",
                    "CONTACT_SUPPORT"
                );
            });
    }

    private boolean idempotentDuplicate(String externalEventId, String payloadDigest) {
        Optional<BillingEventRecord> event = billingRepository.findBillingEvent(externalEventId);
        if (event.isEmpty()) {
            return false;
        }
        if (!event.get().payloadHash().equals(payloadDigest)) {
            throw problem(
                HttpStatus.CONFLICT,
                ApiErrorCode.IDEMPOTENCY_KEY_REUSED,
                "Idempotency-Key was reused with different purchase evidence",
                "RETRY"
            );
        }
        return true;
    }

    private AppStoreProductMapping requireProduct(String appStoreProductId) {
        return productCatalog.findByAppStoreProductId(appStoreProductId)
            .orElseThrow(() -> {
                metrics.purchaseEvidenceRejected("unknown_product");
                return problem(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.PURCHASE_EVIDENCE_REJECTED,
                    "Transaction product is not configured",
                    "CONTACT_SUPPORT"
                );
            });
    }

    private void validateConfiguredEnvironment(AppStoreEnvironment environment) {
        if (productCatalog.environment() != environment) {
            throw problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.PURCHASE_EVIDENCE_REJECTED,
                "Transaction environment does not match backend billing configuration",
                "CONTACT_SUPPORT"
            );
        }
    }

    private AppStoreSubscriptionStatus determineStatus(
        VerifiedAppStoreTransaction transaction,
        VerifiedAppStoreRenewalInfo renewalInfo,
        VerifiedAppStoreNotification notification,
        Instant now
    ) {
        if (notification != null && notification.appStoreStatus() != null) {
            return statusFromAppleRawStatus(
                notification.appStoreStatus(),
                transaction,
                renewalInfo,
                notification.notificationType(),
                now
            );
        }
        return statusFromPayload(
            transaction,
            renewalInfo,
            notification == null ? null : notification.notificationType(),
            now
        );
    }

    private AppStoreSubscriptionStatus statusFromAppleRawStatus(
        int appStoreStatus,
        VerifiedAppStoreTransaction transaction,
        VerifiedAppStoreRenewalInfo renewalInfo,
        String notificationType,
        Instant now
    ) {
        return switch (appStoreStatus) {
            case 1 -> AppStoreSubscriptionStatus.ACTIVE;
            case 2 -> AppStoreSubscriptionStatus.EXPIRED;
            case 3 -> AppStoreSubscriptionStatus.BILLING_RETRY;
            case 4 -> AppStoreSubscriptionStatus.GRACE_PERIOD;
            case 5 -> AppStoreSubscriptionStatus.REVOKED;
            default -> statusFromPayload(transaction, renewalInfo, notificationType, now);
        };
    }

    private AppStoreSubscriptionStatus statusFromPayload(
        VerifiedAppStoreTransaction transaction,
        VerifiedAppStoreRenewalInfo renewalInfo,
        String notificationType,
        Instant now
    ) {
        if (transaction.revoked() || "REFUND".equals(notificationType) || "REVOKE".equals(notificationType)) {
            return AppStoreSubscriptionStatus.REVOKED;
        }
        if ("EXPIRED".equals(notificationType)) {
            return AppStoreSubscriptionStatus.EXPIRED;
        }
        if (renewalInfo != null
            && renewalInfo.gracePeriodExpiresDate() != null
            && renewalInfo.gracePeriodExpiresDate().isAfter(now)) {
            return AppStoreSubscriptionStatus.GRACE_PERIOD;
        }
        if (renewalInfo != null && Boolean.TRUE.equals(renewalInfo.inBillingRetryPeriod())) {
            return AppStoreSubscriptionStatus.BILLING_RETRY;
        }
        if (transaction.expired(now)) {
            return AppStoreSubscriptionStatus.EXPIRED;
        }
        return AppStoreSubscriptionStatus.ACTIVE;
    }

    private VerifiedAppStoreTransaction verifyTransaction(String signedTransactionInfo) {
        try {
            return signedDataVerifier.verifyTransaction(signedTransactionInfo);
        } catch (AppStoreVerificationFailedException exception) {
            metrics.purchaseEvidenceRejected("verification_failed");
            throw problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.PURCHASE_VERIFICATION_FAILED,
                "Apple transaction verification failed",
                "RETRY"
            );
        }
    }

    private VerifiedAppStoreNotification verifyNotification(String signedPayload) {
        try {
            return signedDataVerifier.verifyNotification(signedPayload);
        } catch (AppStoreVerificationFailedException exception) {
            throw problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.APP_STORE_NOTIFICATION_INVALID,
                "Apple notification verification failed",
                "RETRY"
            );
        }
    }

    private VerifiedAppStoreRenewalInfo verifyRenewalInfo(String signedRenewalInfo) {
        if (signedRenewalInfo == null || signedRenewalInfo.isBlank()) {
            return null;
        }
        try {
            return signedDataVerifier.verifyRenewalInfo(signedRenewalInfo);
        } catch (AppStoreVerificationFailedException exception) {
            throw problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.PURCHASE_VERIFICATION_FAILED,
                "Apple renewal verification failed",
                "RETRY"
            );
        }
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                "Idempotency-Key header is required",
                "RETRY"
            );
        }
        if (idempotencyKey.length() > 96) {
            throw problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                "Idempotency-Key header is too long",
                "RETRY"
            );
        }
    }

    private static ApiProblemException problem(
        HttpStatus status,
        ApiErrorCode code,
        String title,
        String userAction
    ) {
        return new ApiProblemException(status, code, title, true, userAction);
    }

    private static String digest(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
