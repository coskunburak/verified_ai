import Foundation

final class AppleBillingAPI: AppleBillingServicing, @unchecked Sendable {
    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func configuration() async throws -> AppleBillingConfiguration {
        let response: HTTPResponse<AppleBillingConfigurationWireResponse> = try await apiClient.send(
            HTTPRequest(endpoint: Endpoint(path: "/api/v1/me/billing/apple/configuration", method: .get))
        )
        return try response.body.configuration()
    }

    func submit(transaction: StoreTransaction) async throws -> ApplePurchaseSubmissionResult {
        let body = try encoder.encode(ApplePurchaseEvidenceWireRequest(
            signedTransactionInfo: transaction.signedTransactionInfo,
            source: "STOREKIT_TRANSACTION"
        ))
        let response: HTTPResponse<ApplePurchaseEvidenceWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/me/billing/apple/transactions", method: .post),
                body: body,
                idempotencyKey: "storekit-\(transaction.transactionId)"
            )
        )
        return try response.body.result()
    }
}

private struct AppleBillingConfigurationWireResponse: Decodable {
    let appAccountToken: UUID
    let purchaseAvailable: Bool
    let environment: String
    let products: [AppleBillingProductWireResponse]

    func configuration() throws -> AppleBillingConfiguration {
        guard let environment = AppStoreBillingEnvironment(rawValue: environment) else {
            throw NetworkError.decoding("unsupported_app_store_environment")
        }
        return AppleBillingConfiguration(
            appAccountToken: appAccountToken,
            purchaseAvailable: purchaseAvailable,
            environment: environment,
            products: try products.map { try $0.product() }
        )
    }
}

private struct AppleBillingProductWireResponse: Decodable {
    let internalPlanId: String
    let appStoreProductId: String
    let entitlementTier: String
    let subscriptionGroupId: String?
    let billingPeriod: String?

    func product() throws -> AppleBillingProduct {
        guard let tier = EntitlementTier(rawValue: entitlementTier) else {
            throw NetworkError.decoding("unsupported_billing_product_tier")
        }
        return AppleBillingProduct(
            internalPlanId: internalPlanId,
            appStoreProductId: appStoreProductId,
            entitlementTier: tier,
            subscriptionGroupId: subscriptionGroupId,
            billingPeriod: billingPeriod
        )
    }
}

private struct ApplePurchaseEvidenceWireRequest: Encodable {
    let signedTransactionInfo: String
    let source: String
}

private struct ApplePurchaseEvidenceWireResponse: Decodable {
    let transactionId: String
    let originalTransactionId: String
    let subscriptionStatus: String
    let entitlement: EntitlementWireResponse
    let duplicate: Bool

    func result() throws -> ApplePurchaseSubmissionResult {
        guard let status = EntitlementStatus(rawValue: subscriptionStatus) else {
            throw NetworkError.decoding("unsupported_subscription_status")
        }
        return ApplePurchaseSubmissionResult(
            transactionId: transactionId,
            originalTransactionId: originalTransactionId,
            subscriptionStatus: status,
            entitlement: try entitlement.entitlement(),
            duplicate: duplicate
        )
    }
}
