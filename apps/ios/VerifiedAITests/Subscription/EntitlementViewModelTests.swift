import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class EntitlementViewModelTests: XCTestCase {
    func testBootstrapLoadsServerEntitlementAndCachesDisplayState() async throws {
        let storage = InMemorySecureStorage()
        let cache = EntitlementDisplayCache(secureStorage: storage)
        let service = StubEntitlementService(entitlement: .free())
        let viewModel = makeViewModel(service: service, cache: cache)

        await viewModel.bootstrap()

        XCTAssertEqual(viewModel.state, .ready(.free()))
        XCTAssertEqual(viewModel.entitlement, .free())
        let cached = try await cache.load()
        XCTAssertEqual(cached, .free())
        let requestCount = await service.requestCount
        XCTAssertEqual(requestCount, 1)
    }

    func testFreeEntitlementAllowsBasicAndDeniesProOnlyCapabilities() {
        let entitlement = Entitlement.free()

        XCTAssertTrue(entitlement.allows(.basicSolve))
        XCTAssertFalse(entitlement.allows(.verifiedSolve))
        XCTAssertFalse(entitlement.allows(.mockExam))
    }

    func testOfflineBootstrapUsesCachedPresentationState() async throws {
        let cache = EntitlementDisplayCache(secureStorage: InMemorySecureStorage())
        try await cache.save(.pro())
        let network = StubNetworkMonitor(isReachable: false)
        let viewModel = makeViewModel(
            service: StubEntitlementService(entitlement: .free()),
            cache: cache,
            networkMonitor: network
        )

        await viewModel.bootstrap()

        XCTAssertEqual(viewModel.state, .offline(.pro()))
        XCTAssertEqual(viewModel.entitlement, .pro())
        XCTAssertEqual(viewModel.message, "Offline. Showing last known access.")
    }

    func testNetworkFailureWithoutCacheShowsFailureState() async {
        let service = StubEntitlementService(entitlement: .free(), error: TestError.network)
        let viewModel = makeViewModel(service: service)

        await viewModel.bootstrap()

        XCTAssertEqual(viewModel.state, .failed)
        XCTAssertNil(viewModel.entitlement)
    }

    func testServerRefreshOverridesTamperedCachedPresentationState() async throws {
        let cache = EntitlementDisplayCache(secureStorage: InMemorySecureStorage())
        try await cache.save(.proPlus())
        let network = StubNetworkMonitor(isReachable: false)
        let service = StubEntitlementService(entitlement: .free())
        let viewModel = makeViewModel(service: service, cache: cache, networkMonitor: network)

        await viewModel.bootstrap()
        XCTAssertEqual(viewModel.entitlement, .proPlus())

        network.isReachable = true
        await viewModel.bootstrap(force: true)

        XCTAssertEqual(viewModel.state, .ready(.free()))
        XCTAssertEqual(viewModel.entitlement, .free())
        XCTAssertFalse(viewModel.allows(.verifiedSolve))
    }

    private func makeViewModel(
        service: StubEntitlementService,
        cache: EntitlementDisplayCache = EntitlementDisplayCache(secureStorage: InMemorySecureStorage()),
        networkMonitor: StubNetworkMonitor = StubNetworkMonitor()
    ) -> EntitlementViewModel {
        EntitlementViewModel(
            entitlementAPI: service,
            displayCache: cache,
            networkMonitor: networkMonitor,
            logger: AppLogger(subsystem: "com.verifiedai.learning.tests", category: "entitlement")
        )
    }
}

@MainActor
private final class StubNetworkMonitor: NetworkMonitoring {
    var isReachable: Bool

    init(isReachable: Bool = true) {
        self.isReachable = isReachable
    }
}

private actor StubEntitlementService: EntitlementServicing {
    let entitlement: Entitlement
    let error: Error?
    private(set) var requestCount = 0

    init(entitlement: Entitlement, error: Error? = nil) {
        self.entitlement = entitlement
        self.error = error
    }

    func currentEntitlement() async throws -> Entitlement {
        requestCount += 1
        if let error {
            throw error
        }
        return entitlement
    }
}

private enum TestError: Error {
    case network
}

private extension Entitlement {
    static func free() -> Entitlement {
        entitlement(tier: .free, capabilities: [.basicSolve])
    }

    static func pro() -> Entitlement {
        entitlement(tier: .pro, capabilities: [.basicSolve, .verifiedSolve, .advancedTutor, .mistakeHistory, .adaptivePlan])
    }

    static func proPlus() -> Entitlement {
        entitlement(tier: .proPlus, capabilities: Set(PremiumCapability.allCases))
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
