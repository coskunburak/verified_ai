import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class LearningOsEntitlementRefreshTests: XCTestCase {
    private let userA = UUID(uuidString: "00000000-0000-4000-8000-000000000501")!
    private let userB = UUID(uuidString: "00000000-0000-4000-8000-000000000502")!

    func testCacheIsolationAcrossSubjectAndSessionSwitchAndLogoutReset() async throws {
        let storage = InMemorySecureStorage()
        let cache = EntitlementDisplayCache(secureStorage: storage)
        let scopeA = EntitlementDisplayCache.Scope(user: userA, session: UUID())
        let scopeB = EntitlementDisplayCache.Scope(user: userB, session: UUID())

        try await cache.save(entitlement(for: userA, tier: .proPlus), scope: scopeA)
        let cachedForB = try await cache.load(scope: scopeB)
        XCTAssertNil(cachedForB)

        let service = RefreshStubEntitlementService()
        await service.setResponse(
            for: userA,
            legacy: entitlement(for: userA, tier: .pro),
            learning: v2(tier: .pro, status: .active, paid: true)
        )
        await service.setResponse(
            for: userB,
            legacy: entitlement(for: userB, tier: .free),
            learning: v2(tier: .free, status: .active, paid: false)
        )

        let vm = EntitlementViewModel(
            entitlementAPI: service,
            displayCache: cache,
            networkMonitor: RefreshStubNetworkMonitor(),
            logger: AppLogger(subsystem: "com.verifiedai.learning.tests", category: "refresh")
        )
        vm.bind(scopeA)
        await service.setActiveUser(userA)
        await vm.bootstrap()
        XCTAssertEqual(vm.learning?.effectiveTier, .pro)

        // Switching accounts resets in-memory access immediately and loads the new account cleanly.
        vm.bind(scopeB)
        XCTAssertEqual(vm.state, .idle)
        XCTAssertNil(vm.learning)
        await service.setActiveUser(userB)
        await vm.bootstrap()
        XCTAssertEqual(vm.entitlement?.userId, userB)
        XCTAssertEqual(vm.learning?.effectiveTier, .free)

        vm.reset()
        XCTAssertEqual(vm.state, .idle)
        XCTAssertNil(vm.entitlement)
        XCTAssertNil(vm.learning)
    }

    func testStaleAsyncResponseAndUserMismatchNeverOverwriteCurrentState() async throws {
        let storage = InMemorySecureStorage()
        let cache = EntitlementDisplayCache(secureStorage: storage)
        let service = RacyEntitlementService(
            userA: entitlement(for: userA, tier: .proPlus),
            learningA: v2(tier: .coachPro, status: .active, paid: true),
            userB: entitlement(for: userB, tier: .free),
            learningB: v2(tier: .free, status: .active, paid: false)
        )
        let vm = EntitlementViewModel(
            entitlementAPI: service,
            displayCache: cache,
            networkMonitor: RefreshStubNetworkMonitor(),
            logger: AppLogger(subsystem: "com.verifiedai.learning.tests", category: "race")
        )

        let scopeA = EntitlementDisplayCache.Scope(user: userA, session: UUID())
        let scopeB = EntitlementDisplayCache.Scope(user: userB, session: UUID())
        vm.bind(scopeA)

        let staleTask = Task { await vm.bootstrap() }
        await service.waitUntilFirstCallEntered()

        // Account switch occurs while user A's response is still in flight.
        vm.bind(scopeB)
        await vm.bootstrap(force: true)
        await service.releaseFirstCall()
        await staleTask.value

        XCTAssertEqual(vm.entitlement?.userId, userB)
        XCTAssertEqual(vm.learning?.effectiveTier, .free)

        // Mismatched subject in server payload transitions to .failed without caching.
        let wrongScope = EntitlementDisplayCache.Scope(user: UUID(), session: UUID())
        vm.bind(wrongScope)
        await vm.bootstrap(force: true)
        XCTAssertEqual(vm.state, .failed)
        let cachedWrong = try await cache.load(scope: wrongScope)
        XCTAssertNil(cachedWrong)
    }

    func testStatusBannerKeysCoverGraceRetryExpiredRevokedAndPaywallCallback() async {
        let storage = InMemorySecureStorage()
        let cache = EntitlementDisplayCache(secureStorage: storage)
        let service = RefreshStubEntitlementService()
        await service.setActiveUser(userA)
        let vm = EntitlementViewModel(
            entitlementAPI: service,
            displayCache: cache,
            networkMonitor: RefreshStubNetworkMonitor(),
            logger: AppLogger(subsystem: "com.verifiedai.learning.tests", category: "banner")
        )
        vm.bind(EntitlementDisplayCache.Scope(user: userA, session: UUID()))

        await service.setResponse(for: userA, legacy: entitlement(for: userA, tier: .pro), learning: v2(tier: .pro, status: .gracePeriod, paid: true))
        await vm.refresh(force: true)
        XCTAssertEqual(vm.statusBannerKey, "billing.grace")

        await service.setResponse(for: userA, legacy: entitlement(for: userA, tier: .free), learning: v2(tier: .free, status: .gracePeriod, paid: false))
        await vm.refresh(force: true)
        XCTAssertEqual(vm.statusBannerKey, "billing.historyRetained")

        await service.setResponse(for: userA, legacy: entitlement(for: userA, tier: .pro), learning: v2(tier: .pro, status: .billingRetry, paid: true))
        await vm.refresh(force: true)
        XCTAssertEqual(vm.statusBannerKey, "billing.retry")

        await service.setResponse(for: userA, legacy: entitlement(for: userA, tier: .free), learning: v2(tier: .free, status: .revoked, paid: false))
        await vm.refresh(force: true)
        XCTAssertEqual(vm.statusBannerKey, "billing.historyRetained")

        await service.setResponse(for: userA, legacy: entitlement(for: userA, tier: .pro), learning: v2(tier: .pro, status: .active, paid: true))
        await vm.refresh(force: true)
        XCTAssertNil(vm.statusBannerKey)

        var verifiedCallbacks = 0
        let paywall = PaywallViewModel(
            billingAPI: RefreshStubBillingService(entitlement: entitlement(for: userA, tier: .pro)),
            storeRepository: RefreshStubStoreRepo(),
            entitlementAPI: service,
            networkMonitor: RefreshStubNetworkMonitor(),
            logger: AppLogger(subsystem: "com.verifiedai.learning.tests", category: "paywall")
        )
        paywall.onServerEntitlementVerified = { verifiedCallbacks += 1 }
        await paywall.load()
        await paywall.purchase(RefreshStubStoreRepo.sampleProduct)
        XCTAssertEqual(verifiedCallbacks, 1)
        XCTAssertEqual(paywall.refreshedLearningAccess?.effectiveTier, .pro)
    }

    private func entitlement(for user: UUID, tier: EntitlementTier) -> Entitlement {
        Entitlement(
            id: UUID(), userId: user, tier: tier,
            source: tier == .free ? .defaultFree : .appStoreSubscription,
            status: .active, effectiveAt: Date(timeIntervalSince1970: 1_800_000_000),
            expiresAt: nil, capabilities: [.basicSolve], version: 1
        )
    }

    private func v2(tier: LearningOsEffectiveTier, status: EntitlementStatus, paid: Bool) -> LearningOsEntitlementV2 {
        LearningOsEntitlementV2(
            effectiveTier: tier,
            legacyTier: tier == .coachPro ? .proPlus : (tier == .pro ? .pro : .free),
            status: status,
            source: paid ? .appStoreSubscription : .defaultFree,
            expiresAt: "2027-01-10T00:00:00Z",
            capabilities: [.basicSolve],
            packageCode: tier == .coachPro ? .coach : (tier == .pro ? .pro : .free),
            featureEnabled: true,
            learningOsCapabilities: [.attemptVerify, .deterministicTwin],
            historyAccess: LearningOsEntitlementV2.History(mistakePassport: .summary, skillMastery: .summary, priorExams: .summary),
            accessEvaluatedAt: "2026-11-20T12:00:00Z",
            gracePeriodExpiresAt: status == .gracePeriod ? "2026-11-22T00:00:00Z" : nil,
            paidAccessActive: paid
        )
    }
}

private actor RefreshStubEntitlementService: EntitlementServicing {
    private var responses: [UUID: (Entitlement, LearningOsEntitlementV2)] = [:]
    private var activeUser: UUID = UUID()

    func setActiveUser(_ user: UUID) { activeUser = user }
    func setResponse(for user: UUID, legacy: Entitlement, learning: LearningOsEntitlementV2) {
        responses[user] = (legacy, learning)
    }

    func currentEntitlement() async throws -> Entitlement { responses[activeUser]!.0 }
    func learningAccess() async throws -> LearningOsEntitlementV2 { responses[activeUser]!.1 }
}

private actor RacyEntitlementService: EntitlementServicing {
    let userA: Entitlement
    let learningA: LearningOsEntitlementV2
    let userB: Entitlement
    let learningB: LearningOsEntitlementV2
    private var calls = 0
    private var enteredContinuation: CheckedContinuation<Void, Never>?
    private var releaseContinuation: CheckedContinuation<Void, Never>?

    init(userA: Entitlement, learningA: LearningOsEntitlementV2, userB: Entitlement, learningB: LearningOsEntitlementV2) {
        self.userA = userA
        self.learningA = learningA
        self.userB = userB
        self.learningB = learningB
    }

    func waitUntilFirstCallEntered() async {
        if calls > 0 { return }
        await withCheckedContinuation { enteredContinuation = $0 }
    }

    func releaseFirstCall() {
        releaseContinuation?.resume()
        releaseContinuation = nil
    }

    func currentEntitlement() async throws -> Entitlement {
        calls += 1
        if calls == 1 {
            enteredContinuation?.resume()
            enteredContinuation = nil
            await withCheckedContinuation { releaseContinuation = $0 }
            return userA
        }
        return userB
    }

    func learningAccess() async throws -> LearningOsEntitlementV2 {
        calls == 1 ? learningA : learningB
    }
}

private actor RefreshStubBillingService: AppleBillingServicing {
    let entitlement: Entitlement
    init(entitlement: Entitlement) { self.entitlement = entitlement }

    func configuration() async throws -> AppleBillingConfiguration {
        AppleBillingConfiguration(
            appAccountToken: UUID(),
            purchaseAvailable: true,
            environment: .xcode,
            products: [
                AppleBillingProduct(
                    internalPlanId: "pro_monthly",
                    appStoreProductId: RefreshStubStoreRepo.sampleProduct.id,
                    entitlementTier: .pro,
                    subscriptionGroupId: "102",
                    billingPeriod: "P1M"
                )
            ]
        )
    }

    func submit(transaction: StoreTransaction) async throws -> ApplePurchaseSubmissionResult {
        ApplePurchaseSubmissionResult(
            transactionId: transaction.transactionId,
            originalTransactionId: "orig-\(transaction.transactionId)",
            subscriptionStatus: .active,
            entitlement: entitlement,
            duplicate: false
        )
    }
}

private actor RefreshStubStoreRepo: StoreProductRepository {
    static let sampleProduct = StoreProduct(
        id: "local_dev_pro_monthly",
        displayName: "Pro Monthly",
        description: "Pro",
        displayPrice: "$1.00",
        subscriptionPeriod: "1 month"
    )

    func products(for productIdentifiers: [String]) async throws -> [StoreProduct] { [Self.sampleProduct] }
    func purchase(_ product: StoreProduct, appAccountToken: UUID) async throws -> StorePurchaseOutcome {
        .success(StoreTransaction(transactionId: "tx-1", productId: product.id, signedTransactionInfo: "synthetic-jws"))
    }
    func restorePurchases() async throws {}
    func currentEntitlementTransactions() async throws -> [StoreTransaction] { [] }
    func unfinishedTransactions() async throws -> [StoreTransaction] { [] }
    nonisolated func transactionUpdates() -> AsyncStream<StoreTransaction> {
        AsyncStream { $0.finish() }
    }
    func finish(_ transaction: StoreTransaction) async {}
}

@MainActor
private final class RefreshStubNetworkMonitor: NetworkMonitoring {
    var isReachable = true
}
