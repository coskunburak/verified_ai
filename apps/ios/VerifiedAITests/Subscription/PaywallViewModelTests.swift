import Foundation
import XCTest
import StoreKitTest
import StoreKit
import SwiftUI
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

    func testActualStoreKitLocalizedProductsAndPeriods() async throws {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "Sprint101", withExtension: "storekit"))
        let session = try SKTestSession(contentsOf: url)
        session.disableDialogs = true
        session.clearTransactions()
        defer { session.clearTransactions() }
        let ids = ["local_dev_monthly", "local_dev_annual", "local_dev_season"]
        let repository = StoreKitProductRepository()
        let products = try await repository.products(for: ids)
        XCTAssertEqual(products.map(\.periodCode), ["P1M", "P1Y", "P6M"])
        let originals = try await Product.products(for: ids)
        for product in products {
            XCTAssertEqual(product.displayPrice, originals.first { $0.id == product.id }?.displayPrice)
        }
        try await session.buyProduct(identifier: ids[0])
        let transactions = try await repository.currentEntitlementTransactions()
        XCTAssertTrue(transactions.contains { $0.productId == ids[0] })
    }

    func testEveryTimedSurfaceSuppressesExplicitAndContextualEntry() {
        let coordinator = PaywallTriggerCoordinator()
        let now = Date(timeIntervalSince1970: 1000)
        for source in [PaywallTriggerCoordinator.Flow.exam, .diagnostic, .twin] {
            for state in [PaywallTriggerCoordinator.Timer.active, .unknown] {
                coordinator.update(source, state)
                XCTAssertFalse(coordinator.permits(eligible: true, enabled: true, acceptedFreeOutput: false, explicit: true, now: now))
            }
            coordinator.update(source, .inactive)
        }
        XCTAssertFalse(coordinator.permits(eligible: true, enabled: true, acceptedFreeOutput: true, explicit: true, now: now))
        XCTAssertFalse(coordinator.permits(eligible: true, enabled: false, acceptedFreeOutput: false, explicit: true, now: now))
        XCTAssertTrue(coordinator.permits(eligible: true, enabled: true, acceptedFreeOutput: false, explicit: false, now: now))
        coordinator.didPresent(at: now)
        XCTAssertFalse(coordinator.permits(eligible: true, enabled: true, acceptedFreeOutput: false, explicit: false, now: now))
    }

    func testMalformedFeedAndDuplicateSKUsSuppressOffers() {
        let offer = BillingOffers.Offer(productId: "local_dev", family: "YKS_SEASON_PASS", billingPeriod: "P1Y", copyKey: "billing.plan.yks_season_pass")
        XCTAssertFalse(BillingOffers(locale: "tr-TR", enabled: true, reason: "AVAILABLE", offers: [offer]).valid(for: "tr-TR"))
        let annual = BillingOffers.Offer(productId: "local_dev", family: "PRO", billingPeriod: "P1Y", copyKey: "billing.plan.pro")
        XCTAssertFalse(BillingOffers(locale: "en-US", enabled: true, reason: "AVAILABLE", offers: [annual, annual]).valid(for: "en-US"))
    }

    func testServerFailureLeavesVerifiedTransactionUnfinished() async {
        let billing = StubAppleBillingService(configuration: .configured(), reject: true)
        let store = StubStoreProductRepository(products: [.pro()], purchaseOutcome: .success(.proTransaction()))
        let model = makeViewModel(billing: billing, store: store)
        await model.load()
        await model.purchase(.pro())
        XCTAssertNil(model.entitlement)
        let finished = await store.finishedTransactions
        XCTAssertTrue(finished.isEmpty)
    }

    func testOfflineAndUnverifiedTransactionsCannotGrantAccess() async {
        let billing = StubAppleBillingService(configuration: .configured())
        let store = StubStoreProductRepository(products: [.pro()], unverified: true)
        let network = StubPaywallNetworkMonitor()
        let model = makeViewModel(billing: billing, store: store, networkMonitor: network)
        network.isReachable = false
        await model.load()
        XCTAssertEqual(model.state, .offline)
        network.isReachable = true
        await model.load()
        await model.purchase(.pro())
        XCTAssertEqual(model.message, "billing.unverified")
        let submitted = await billing.submittedTransactions
        XCTAssertTrue(submitted.isEmpty)
        XCTAssertNil(model.entitlement)
    }

    func testConcurrentPurchaseTapsSubmitOnce() async {
        let billing = StubAppleBillingService(configuration: .configured())
        let store = StubStoreProductRepository(products: [.pro()], purchaseOutcome: .success(.proTransaction()))
        let model = makeViewModel(billing: billing, store: store)
        await model.load()
        async let first: Void = model.purchase(.pro())
        async let second: Void = model.purchase(.pro())
        _ = await (first, second)
        let submitted = await billing.submittedTransactions
        XCTAssertEqual(submitted.count, 1)
    }

    func testCancellationAndEmptyRestore() async {
        let billing = StubAppleBillingService(configuration: .configured())
        let model = makeViewModel(billing: billing, store: StubStoreProductRepository(products: [.pro()]))
        await model.load()
        await model.purchase(.pro())
        XCTAssertEqual(model.state, .cancelled)
        await model.restore()
        XCTAssertEqual(model.message, "billing.noRestorable")
        XCTAssertEqual(model.entitlement?.tier, .free)
    }

    func testCompactSubscriptionLayoutAtAccessibilitySizes() async {
        let billing = StubAppleBillingService(configuration: .configured())
        let model = makeViewModel(billing: billing, store: StubStoreProductRepository(products: [.pro()]))
        await model.load()
        for size in [DynamicTypeSize.xSmall, .accessibility3] {
            for locale in ["tr-TR", "en-US"] {
                for scheme in [ColorScheme.light, .dark] {
                    let view = PaywallView(viewModel: model, contextual: true)
                        .environment(\.locale, Locale(identifier: locale))
                        .environment(\.colorScheme, scheme)
                        .dynamicTypeSize(size)
                    let host = UIHostingController(rootView: view)
                    let measured = host.sizeThatFits(in: CGSize(width: 320, height: 640))
                    XCTAssertGreaterThan(measured.height, 0)
                    XCTAssertLessThanOrEqual(measured.width, 320)
                }
            }
        }
    }

    func testContextualWhitelistRequiresCurrentFlagAndMatchingStorePeriod() async throws {
        let offer = BillingOffers.Offer(productId: StoreProduct.pro().id, family: "PRO", billingPeriod: "P1M", copyKey: "billing.plan.pro")
        let feed = BillingOffers(locale: "tr-TR", enabled: true, reason: "AVAILABLE", offers: [offer])
        var matching = StoreProduct.pro()
        matching.periodCode = "P1M"
        let billing = StubAppleBillingService(configuration: .configured(), feed: feed)
        let model = makeViewModel(billing: billing, store: StubStoreProductRepository(products: [matching]))
        let prepared = await model.prepareContextual(locale: "tr-TR")
        XCTAssertTrue(prepared)
        XCTAssertEqual(model.products.first?.displayPrice, matching.displayPrice)
        await billing.disableCatalog()
        let selected = try XCTUnwrap(model.products.first)
        await model.purchase(selected)
        XCTAssertEqual(model.state, .empty)
        let submitted = await billing.submittedTransactions
        XCTAssertTrue(submitted.isEmpty)
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
    let reject: Bool
    private var feed: BillingOffers?
    private(set) var submittedTransactions: [StoreTransaction] = []

    init(configuration: AppleBillingConfiguration, entitlement: Entitlement = .pro(), reject: Bool = false, feed: BillingOffers? = nil) {
        self.feed = feed
        self.reject = reject
        self.configuration = configuration
        self.entitlement = entitlement
    }

    func disableCatalog() { feed = nil }

    func offers(locale: String) async throws -> BillingOffers {
        feed ?? BillingOffers(locale: locale, enabled: false, reason: "FEATURE_DISABLED", offers: [])
    }

    func configuration() async throws -> AppleBillingConfiguration {
        configuration
    }

    func submit(transaction: StoreTransaction) async throws -> ApplePurchaseSubmissionResult {
        if reject { throw StoreCommerceError.productUnavailable }
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
    let unverified: Bool
    let purchaseOutcome: StorePurchaseOutcome
    let currentTransactions: [StoreTransaction]
    private(set) var requestedProductIdentifiers: [String] = []
    private(set) var finishedTransactions: [StoreTransaction] = []
    private(set) var restoreRequestCount = 0

    init(
        products: [StoreProduct],
        purchaseOutcome: StorePurchaseOutcome = .cancelled,
        currentTransactions: [StoreTransaction] = [],
        unverified: Bool = false
    ) {
        self.products = products
        self.unverified = unverified
        self.purchaseOutcome = purchaseOutcome
        self.currentTransactions = currentTransactions
    }

    func products(for productIdentifiers: [String]) async throws -> [StoreProduct] {
        requestedProductIdentifiers = productIdentifiers
        return products
    }

    func purchase(_ product: StoreProduct, appAccountToken: UUID) async throws -> StorePurchaseOutcome {
        if unverified { throw StoreCommerceError.unverifiedTransaction("local_dev") }
        return purchaseOutcome
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

    func currentPresentationTier() async throws -> String { entitlement.tier.rawValue }

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
