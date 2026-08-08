package com.verifiedai.billing.infrastructure.appstore;

import com.apple.itunes.storekit.client.APIException;
import com.apple.itunes.storekit.client.AppStoreServerAPIClient;
import com.apple.itunes.storekit.client.GetTransactionHistoryVersion;
import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.HistoryResponse;
import com.apple.itunes.storekit.model.LastTransactionsItem;
import com.apple.itunes.storekit.model.Status;
import com.apple.itunes.storekit.model.StatusResponse;
import com.apple.itunes.storekit.model.SubscriptionGroupIdentifierItem;
import com.apple.itunes.storekit.model.TransactionHistoryRequest;
import com.verifiedai.billing.domain.model.AppStoreEnvironment;
import com.verifiedai.billing.domain.port.AppStoreGatewayUnavailableException;
import com.verifiedai.billing.domain.port.AppStoreServerGateway;
import com.verifiedai.billing.domain.port.AppStoreSubscriptionStatusRecord;
import com.verifiedai.billing.infrastructure.configuration.AppleBillingProperties;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.billing.apple", name = "enabled", havingValue = "true")
class AppleAppStoreServerGateway implements AppStoreServerGateway {
    private final AppStoreServerAPIClient client;

    AppleAppStoreServerGateway(AppleBillingProperties properties) {
        this.client = new AppStoreServerAPIClient(
            properties.privateKeyPem(),
            properties.keyId(),
            properties.issuerId(),
            properties.bundleId(),
            toAppleEnvironment(properties.environment())
        );
    }

    @Override
    public String getTransactionInfo(String transactionId) {
        try {
            return client.getTransactionInfo(transactionId).getSignedTransactionInfo();
        } catch (APIException | IOException exception) {
            throw new AppStoreGatewayUnavailableException("App Store transaction lookup failed", exception);
        }
    }

    @Override
    public List<String> getTransactionHistory(String anyTransactionId) {
        try {
            TransactionHistoryRequest request = new TransactionHistoryRequest()
                .sort(TransactionHistoryRequest.Order.ASCENDING)
                .productTypes(List.of(TransactionHistoryRequest.ProductType.AUTO_RENEWABLE));
            List<String> signedTransactions = new ArrayList<>();
            HistoryResponse response = null;
            do {
                String revision = response == null ? null : response.getRevision();
                response = client.getTransactionHistory(anyTransactionId, revision, request, GetTransactionHistoryVersion.V2);
                signedTransactions.addAll(response.getSignedTransactions());
            } while (Boolean.TRUE.equals(response.getHasMore()));
            return List.copyOf(signedTransactions);
        } catch (APIException | IOException exception) {
            throw new AppStoreGatewayUnavailableException("App Store transaction history lookup failed", exception);
        }
    }

    @Override
    public List<AppStoreSubscriptionStatusRecord> getAllSubscriptionStatuses(String originalTransactionId) {
        try {
            StatusResponse response = client.getAllSubscriptionStatuses(originalTransactionId, new Status[0]);
            if (response.getData() == null) {
                return List.of();
            }
            List<AppStoreSubscriptionStatusRecord> records = new ArrayList<>();
            for (SubscriptionGroupIdentifierItem group : response.getData()) {
                if (group.getLastTransactions() == null) {
                    continue;
                }
                for (LastTransactionsItem item : group.getLastTransactions()) {
                    records.add(new AppStoreSubscriptionStatusRecord(
                        item.getOriginalTransactionId(),
                        item.getRawStatus() != null ? item.getRawStatus() : rawStatus(item.getStatus()),
                        item.getSignedTransactionInfo(),
                        item.getSignedRenewalInfo()
                    ));
                }
            }
            return List.copyOf(records);
        } catch (APIException | IOException exception) {
            throw new AppStoreGatewayUnavailableException("App Store subscription status lookup failed", exception);
        }
    }

    @Override
    public String requestTestNotification() {
        try {
            return client.requestTestNotification().getTestNotificationToken();
        } catch (APIException | IOException exception) {
            throw new AppStoreGatewayUnavailableException("App Store test notification request failed", exception);
        }
    }

    @Override
    public String getTestNotificationStatus(String testNotificationToken) {
        try {
            return client.getTestNotificationStatus(testNotificationToken).toString();
        } catch (APIException | IOException exception) {
            throw new AppStoreGatewayUnavailableException("App Store test notification status lookup failed", exception);
        }
    }

    private static Environment toAppleEnvironment(AppStoreEnvironment environment) {
        return switch (environment) {
            case XCODE -> Environment.XCODE;
            case LOCAL_TESTING -> Environment.LOCAL_TESTING;
            case SANDBOX -> Environment.SANDBOX;
            case PRODUCTION -> Environment.PRODUCTION;
        };
    }

    private static Integer rawStatus(Status status) {
        return status == null ? null : status.getValue();
    }
}
