package com.verifiedai.billing.domain.port;

import com.verifiedai.billing.domain.model.VerifiedAppStoreNotification;
import com.verifiedai.billing.domain.model.VerifiedAppStoreRenewalInfo;
import com.verifiedai.billing.domain.model.VerifiedAppStoreTransaction;

public interface AppStoreSignedDataVerifier {
    VerifiedAppStoreTransaction verifyTransaction(String signedTransactionInfo);

    VerifiedAppStoreRenewalInfo verifyRenewalInfo(String signedRenewalInfo);

    VerifiedAppStoreNotification verifyNotification(String signedPayload);
}
