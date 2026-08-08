import Foundation

enum AppStoreBillingEnvironment: String, Codable, Equatable, Sendable {
    case xcode = "XCODE"
    case localTesting = "LOCAL_TESTING"
    case sandbox = "SANDBOX"
    case production = "PRODUCTION"
}

struct AppleBillingProduct: Codable, Equatable, Identifiable, Sendable {
    let internalPlanId: String
    let appStoreProductId: String
    let entitlementTier: EntitlementTier
    let subscriptionGroupId: String?
    let billingPeriod: String?

    var id: String { appStoreProductId }
}

struct AppleBillingConfiguration: Equatable, Sendable {
    let appAccountToken: UUID
    let purchaseAvailable: Bool
    let environment: AppStoreBillingEnvironment
    let products: [AppleBillingProduct]
}

struct StoreProduct: Equatable, Identifiable, Sendable {
    let id: String
    let displayName: String
    let description: String
    let displayPrice: String
    let subscriptionPeriod: String?
}

struct StoreTransaction: Equatable, Sendable {
    let transactionId: String
    let productId: String
    let signedTransactionInfo: String
}

enum StorePurchaseOutcome: Equatable, Sendable {
    case success(StoreTransaction)
    case pending
    case cancelled
}

enum StoreCommerceError: Error, Equatable {
    case productUnavailable
    case unverifiedTransaction(String)
}

protocol StoreProductRepository: Sendable {
    func products(for productIdentifiers: [String]) async throws -> [StoreProduct]
    func purchase(_ product: StoreProduct, appAccountToken: UUID) async throws -> StorePurchaseOutcome
    func restorePurchases() async throws
    func currentEntitlementTransactions() async throws -> [StoreTransaction]
    func unfinishedTransactions() async throws -> [StoreTransaction]
    func transactionUpdates() -> AsyncStream<StoreTransaction>
    func finish(_ transaction: StoreTransaction) async
}

protocol AppleBillingServicing: Sendable {
    func configuration() async throws -> AppleBillingConfiguration
    func submit(transaction: StoreTransaction) async throws -> ApplePurchaseSubmissionResult
}

struct ApplePurchaseSubmissionResult: Equatable, Sendable {
    let transactionId: String
    let originalTransactionId: String
    let subscriptionStatus: EntitlementStatus
    let entitlement: Entitlement
    let duplicate: Bool
}
