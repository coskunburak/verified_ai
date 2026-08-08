import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class PaywallViewModelTests: XCTestCase {
    func testLoadUsesBackendConfigurationBeforeStoreKitProducts() async {
        let billing = StubAppleBillingService(configuration: .configured())
        let store = StubStoreProductRepository(products: [.pro()])
        let viewModel = makeViewModel(billing: billing, store: store)

        await viewModel.load()

        XCTAssertEqual(viewModel.state, .ready)
        XCTAssertEqual(viewModel.products, [.pro()])
        let requested = await store.requestedProductIdentifiers
        XCTAssertEqual(requested, ["com.verifiedai.pro.monthly"])
    }

    func testPurchaseSubmitsVerifiedTransactionBeforeFinishingStoreKitTransaction() async {
        let billing = StubAppleBillingService(configuration: .configured(), entitlement: .pro())
        let store = StubStoreProductRepository(products: [.pro()], purchaseOutcome: .success(.proTransaction()))
        let viewModel = makeViewModel(billing: billing, store: store)

        await viewModel.load()
        await viewModel.purchase(.pro())

        XCTAssertEqual(viewModel.state, .completed(.pro()))
        let submissions = await billing.submittedTransactions
        XCTAssertEqual(submissions, [.proTransaction()])
        let finished = await store.finishedTransactions
        XCTAssertEqual(finished, [.proTransaction()])
    }

    func testPendingPurchaseDoesNotFinishOrGrantAccessLocally() async {
        let billing = StubAppleBillingService(configuration: .configured(), entitlement: .pro())
        let store = StubStoreProductRepository(products: [.pro()], purchaseOutcome: .pending)
        let viewModel = makeViewModel(billing: billing, store: store)

        await viewModel.load()
        await viewModel.purchase(.pro())

        XCTAssertEqual(viewModel.state, .pending)
        XCTAssertNil(viewModel.entitlement)
        let submissions = await billing.submittedTransactions
        XCTAssertTrue(submissions.isEmpty)
        let finished = await store.finishedTransactions
        XCTAssertTrue(finished.isEmpty)
    }

    func testRestoreSubmitsCurrentEntitlementTransactionToBackend() async {
        let billing = StubAppleBillingService(configuration: .configured(), entitlement: .pro())
        let store = StubStoreProductRepository(products: [.pro()], currentTransactions: [.proTransaction()])
        let viewModel = makeViewModel(billing: billing, store: store)

        await viewModel.restore()

        XCTAssertEqual(viewModel.state, .completed(.pro()))
        let restored = await store.restoreRequestCount
        XCTAssertEqual(restored, 1)
        let submissions = await billing.submittedTransactions
        XCTAssertEqual(submissions, [.proTransaction()])
    }

    private func makeViewModel(
        billing: StubAppleBillingService,
        store: StubStoreProductRepository,
        networkMonitor: StubPaywallNetworkMonitor = StubPaywallNetworkMonitor()
    ) -> PaywallViewModel {
        PaywallViewModel(
            billingAPI: billing,
            storeRepository: store,
            entitlementAPI: StubPaywallEntitlementService(entitlement: .free()),
            networkMonitor: networkMonitor,
            logger: AppLogger(subsystem: "com.verifiedai.learning.tests", category: "paywall")
        )
    }
}

private actor StubAppleBillingService: AppleBillingServicing {
    let configuration: AppleBillingConfiguration
    let entitlement: Entitlement
    private(set) var submittedTransactions: [StoreTransaction] = []

    init(configuration: AppleBillingConfiguration, entitlement: Entitlement = .pro()) {
        self.configuration = configuration
        self.entitlement = entitlement
    }

    func configuration() async throws -> AppleBillingConfiguration {
        configuration
    }

    func submit(transaction: StoreTransaction) async throws -> ApplePurchaseSubmissionResult {
        submittedTransactions.append(transaction)
        return ApplePurchaseSubmissionResult(
            transactionId: transaction.transactionId,
            originalTransactionId: "original-\(transaction.transactionId)",
            subscriptionStatus: .active,
            entitlement: entitlement,
            duplicate: false
        )
    }
}

private actor StubStoreProductRepository: StoreProductRepository {
    let products: [StoreProduct]
    let purchaseOutcome: StorePurchaseOutcome
    let currentTransactions: [StoreTransaction]
    private(set) var requestedProductIdentifiers: [String] = []
    private(set) var finishedTransactions: [StoreTransaction] = []
    private(set) var restoreRequestCount = 0

    init(
        products: [StoreProduct],
        purchaseOutcome: StorePurchaseOutcome = .cancelled,
        currentTransactions: [StoreTransaction] = []
    ) {
        self.products = products
        self.purchaseOutcome = purchaseOutcome
        self.currentTransactions = currentTransactions
    }

    func products(for productIdentifiers: [String]) async throws -> [StoreProduct] {
        requestedProductIdentifiers = productIdentifiers
        return products
    }

    func purchase(_ product: StoreProduct, appAccountToken: UUID) async throws -> StorePurchaseOutcome {
        purchaseOutcome
    }

    func restorePurchases() async throws {
        restoreRequestCount += 1
    }

    func currentEntitlementTransactions() async throws -> [StoreTransaction] {
        currentTransactions
    }

    func unfinishedTransactions() async throws -> [StoreTransaction] {
        []
    }

    nonisolated func transactionUpdates() -> AsyncStream<StoreTransaction> {
        AsyncStream { continuation in
            continuation.finish()
        }
    }

    func finish(_ transaction: StoreTransaction) async {
        finishedTransactions.append(transaction)
    }
}

private actor StubPaywallEntitlementService: EntitlementServicing {
    let entitlement: Entitlement

    init(entitlement: Entitlement) {
        self.entitlement = entitlement
    }

    func currentEntitlement() async throws -> Entitlement {
        entitlement
    }
}

@MainActor
private final class StubPaywallNetworkMonitor: NetworkMonitoring {
    var isReachable = true
}

private extension AppleBillingConfiguration {
    static func configured() -> AppleBillingConfiguration {
        AppleBillingConfiguration(
            appAccountToken: UUID(uuidString: "10000000-0000-0000-0000-000000000001")!,
            purchaseAvailable: true,
            environment: .xcode,
            products: [
                AppleBillingProduct(
                    internalPlanId: "pro_monthly",
                    appStoreProductId: "com.verifiedai.pro.monthly",
                    entitlementTier: .pro,
                    subscriptionGroupId: "verifiedai-main",
                    billingPeriod: "P1M"
                )
            ]
        )
    }
}

private extension StoreProduct {
    static func pro() -> StoreProduct {
        StoreProduct(
            id: "com.verifiedai.pro.monthly",
            displayName: "Verified AI Pro",
            description: "Verified solve and adaptive tutoring",
            displayPrice: "$9.99",
            subscriptionPeriod: "1 month"
        )
    }
}

private extension StoreTransaction {
    static func proTransaction() -> StoreTransaction {
        StoreTransaction(
            transactionId: "2000000001",
            productId: "com.verifiedai.pro.monthly",
            signedTransactionInfo: "signed-jws"
        )
    }
}

private extension Entitlement {
    static func free() -> Entitlement {
        entitlement(tier: .free, capabilities: [.basicSolve])
    }

    static func pro() -> Entitlement {
        entitlement(tier: .pro, capabilities: [.basicSolve, .verifiedSolve, .advancedTutor, .mistakeHistory, .adaptivePlan])
    }

    private static func entitlement(tier: EntitlementTier, capabilities: Set<PremiumCapability>) -> Entitlement {
        Entitlement(
            id: UUID(uuidString: "00000000-0000-0000-0000-000000000303")!,
            userId: UUID(uuidString: "00000000-0000-0000-0000-000000000101")!,
            tier: tier,
            source: tier == .free ? .defaultFree : .appStoreSubscription,
            status: .active,
            effectiveAt: Date(timeIntervalSince1970: 1_800_000_000),
            expiresAt: nil,
            capabilities: capabilities,
            version: 0
        )
    }
}
