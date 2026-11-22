import Foundation
import StoreKitTest
import XCTest
@testable import VerifiedAI

@MainActor
final class LearningOsCapabilityGateTests: XCTestCase {
    private let userId = UUID(uuidString: "00000000-0000-4000-8000-000000000401")!

    func testDecisionMatrixCoversAllEightCapabilitiesAcrossTiersStatusesAndFlags() async {
        let freeCaps: Set<LearningOsCapability> = [.attemptVerify, .deterministicTwin]
        let proCaps: Set<LearningOsCapability> = [.attemptVerify, .deterministicTwin, .hybridTwin, .mistakePassportFull]
        let allCaps = Set(LearningOsCapability.allCases)

        await assertMatrix(
            v2(tier: .free, status: .active, package: .free, paid: false, enabled: true, caps: freeCaps),
            allowed: freeCaps
        )
        await assertMatrix(
            v2(tier: .pro, status: .active, package: .pro, paid: true, enabled: true, caps: proCaps),
            allowed: proCaps
        )
        await assertMatrix(
            v2(tier: .coachPro, status: .active, package: .coach, paid: true, enabled: true, caps: allCaps),
            allowed: allCaps
        )
        await assertMatrix(
            v2(tier: .coachPro, status: .active, package: .season, paid: true, enabled: true, caps: allCaps),
            allowed: allCaps
        )
        await assertMatrix(
            v2(tier: .coachPro, status: .gracePeriod, package: .coach, paid: true, enabled: true, caps: allCaps),
            allowed: allCaps
        )
        await assertMatrix(
            v2(tier: .free, status: .gracePeriod, package: .free, paid: false, enabled: true, caps: freeCaps),
            allowed: freeCaps
        )
        await assertMatrix(
            v2(tier: .pro, status: .billingRetry, package: .pro, paid: true, enabled: true, caps: proCaps),
            allowed: proCaps
        )
        await assertMatrix(
            v2(tier: .free, status: .billingRetry, package: .free, paid: false, enabled: true, caps: freeCaps),
            allowed: freeCaps
        )
        await assertMatrix(
            v2(tier: .free, status: .expired, package: .free, paid: false, enabled: true, caps: freeCaps),
            allowed: freeCaps
        )
        await assertMatrix(
            v2(tier: .free, status: .revoked, package: .free, paid: false, enabled: true, caps: freeCaps),
            allowed: freeCaps
        )

        // When featureEnabled == false, LearningOsActionGate stays open for parity.
        let flagOff = v2(tier: .free, status: .active, package: .free, paid: false, enabled: false, caps: freeCaps)
        let flagOffVM = makeViewModel(learning: flagOff)
        await flagOffVM.bootstrap(force: true)
        for cap in LearningOsCapability.allCases {
            XCTAssertEqual(flagOffVM.decision(cap), .allowed)
        }

        // Offline/unverified or missing capabilities fail closed to .pendingServerVerification.
        let missingCapsVM = makeViewModel(
            learning: LearningOsEntitlementV2(
                effectiveTier: .pro, legacyTier: .pro, status: .active,
                packageCode: .pro, featureEnabled: true, learningOsCapabilities: nil
            )
        )
        await missingCapsVM.bootstrap(force: true)
        XCTAssertEqual(missingCapsVM.decision(.hybridTwin), .pendingServerVerification)

        let offlineNetwork = GateStubNetworkMonitor(isReachable: false)
        let offlineVM = makeViewModel(learning: flagOff, network: offlineNetwork)
        await offlineVM.bootstrap(force: true)
        XCTAssertEqual(offlineVM.decision(.attemptVerify), .pendingServerVerification)
        XCTAssertEqual(offlineVM.decision(.dailyCoachMission), .pendingServerVerification)
    }

    func testStoreKitLocalPurchaseWithoutServerVerificationNeverUnlocksCapabilities() async throws {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "Sprint102", withExtension: "storekit"))
        let session = try SKTestSession(contentsOf: url)
        session.disableDialogs = true
        session.clearTransactions()
        defer { session.clearTransactions() }

        try await session.buyProduct(identifier: "local_dev_pro_monthly")
        let localTransactions = try await StoreKitProductRepository().currentEntitlementTransactions()
        XCTAssertTrue(localTransactions.contains { $0.productId == "local_dev_pro_monthly" })

        let freeAccess = v2(
            tier: .free, status: .active, package: .free, paid: false, enabled: true,
            caps: [.attemptVerify, .deterministicTwin]
        )
        let vm = makeViewModel(learning: freeAccess)
        await vm.bootstrap(force: true)

        XCTAssertEqual(vm.decision(.attemptVerify), .allowed)
        XCTAssertEqual(vm.decision(.hybridTwin), .upgrade)
        XCTAssertEqual(vm.decision(.mistakePassportFull), .readOnlySummary)
        XCTAssertEqual(vm.decision(.dailyCoachMission), .upgrade)
    }

    private func assertMatrix(_ learning: LearningOsEntitlementV2, allowed: Set<LearningOsCapability>) async {
        let vm = makeViewModel(learning: learning)
        await vm.bootstrap(force: true)
        for cap in LearningOsCapability.allCases {
            if allowed.contains(cap) {
                XCTAssertEqual(vm.decision(cap), .allowed, "Expected allowed for \(cap)")
            } else {
                let expected: LearningOsDecision = cap == .mistakePassportFull ? .readOnlySummary : .upgrade
                XCTAssertEqual(vm.decision(cap), expected, "Expected \(expected) for \(cap)")
            }
        }
    }

    private func makeViewModel(
        learning: LearningOsEntitlementV2,
        network: GateStubNetworkMonitor = GateStubNetworkMonitor()
    ) -> EntitlementViewModel {
        let legacy = Entitlement(
            id: UUID(), userId: userId, tier: .free, source: .defaultFree, status: .active,
            effectiveAt: Date(timeIntervalSince1970: 1_800_000_000), expiresAt: nil,
            capabilities: [.basicSolve], version: 1
        )
        let vm = EntitlementViewModel(
            entitlementAPI: GateStubEntitlementService(legacy: legacy, learning: learning),
            displayCache: EntitlementDisplayCache(secureStorage: InMemorySecureStorage()),
            networkMonitor: network,
            logger: AppLogger(subsystem: "com.verifiedai.learning.tests", category: "gate")
        )
        vm.bind(EntitlementDisplayCache.Scope(user: userId, session: UUID()))
        return vm
    }

    private func v2(
        tier: LearningOsEffectiveTier,
        status: EntitlementStatus,
        package: LearningPackage,
        paid: Bool,
        enabled: Bool,
        caps: Set<LearningOsCapability>
    ) -> LearningOsEntitlementV2 {
        LearningOsEntitlementV2(
            effectiveTier: tier,
            legacyTier: tier == .coachPro ? .proPlus : (tier == .pro ? .pro : .free),
            status: status,
            source: paid ? .appStoreSubscription : .defaultFree,
            expiresAt: "2027-01-10T00:00:00Z",
            capabilities: paid ? Set(PremiumCapability.allCases) : [.basicSolve],
            packageCode: package,
            featureEnabled: enabled,
            learningOsCapabilities: caps,
            historyAccess: LearningOsEntitlementV2.History(
                mistakePassport: paid ? .full : .summary,
                skillMastery: paid ? .full : .summary,
                priorExams: .summary
            ),
            accessEvaluatedAt: "2026-11-20T12:00:00Z",
            gracePeriodExpiresAt: status == .gracePeriod ? "2026-11-22T00:00:00Z" : nil,
            paidAccessActive: paid
        )
    }
}

private actor GateStubEntitlementService: EntitlementServicing {
    let legacy: Entitlement
    let learning: LearningOsEntitlementV2

    init(legacy: Entitlement, learning: LearningOsEntitlementV2) {
        self.legacy = legacy
        self.learning = learning
    }

    func currentEntitlement() async throws -> Entitlement { legacy }
    func learningAccess() async throws -> LearningOsEntitlementV2 { learning }
}

@MainActor
private final class GateStubNetworkMonitor: NetworkMonitoring {
    var isReachable: Bool
    init(isReachable: Bool = true) { self.isReachable = isReachable }
}
