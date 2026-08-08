package com.verifiedai.billing.domain.port;

import java.util.List;

public interface AppStoreServerGateway {
    String getTransactionInfo(String transactionId);

    List<String> getTransactionHistory(String anyTransactionId);

    List<AppStoreSubscriptionStatusRecord> getAllSubscriptionStatuses(String originalTransactionId);

    String requestTestNotification();

    String getTestNotificationStatus(String testNotificationToken);
}
