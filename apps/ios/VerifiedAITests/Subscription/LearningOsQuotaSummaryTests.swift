import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class LearningOsQuotaSummaryTests: XCTestCase {
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    func testDecodesAllFourSprint103QuotaSummaryFixtures() throws {
        struct Scenario {
            let name: String
            let tier: LearningOsEffectiveTier
            let legacyTier: EntitlementTier
            let packageCode: LearningPackage
            let used: Int
            let limit: Int
            let remaining: Int
            let hybridUsed: Int
            let hybridLimit: Int
            let breaker: Bool
            let degraded: Bool
            let periodKey: String
        }

        let scenarios: [Scenario] = [
            Scenario(name: "freeQuotaRemaining", tier: .free, legacyTier: .free, packageCode: .free,
                     used: 1, limit: 3, remaining: 2, hybridUsed: 0, hybridLimit: 1,
                     breaker: false, degraded: false, periodKey: "2026-11-22"),
            Scenario(name: "proCostBreakerDegraded", tier: .pro, legacyTier: .pro, packageCode: .pro,
                     used: 92, limit: 150, remaining: 0, hybridUsed: 18, hybridLimit: 150,
                     breaker: true, degraded: true, periodKey: "2026-11"),
            Scenario(name: "coachProNormalUsage", tier: .coachPro, legacyTier: .proPlus, packageCode: .coach,
                     used: 120, limit: 400, remaining: 280, hybridUsed: 45, hybridLimit: 400,
                     breaker: false, degraded: false, periodKey: "2026-11"),
            Scenario(name: "yksSeasonPassQuotaExhausted", tier: .coachPro, legacyTier: .proPlus, packageCode: .season,
                     used: 400, limit: 400, remaining: 0, hybridUsed: 80, hybridLimit: 400,
                     breaker: false, degraded: true, periodKey: "2026-11"),
        ]

        for s in scenarios {
            let json = """
            {
              "effectiveTier": "\(s.tier.rawValue)",
              "legacyTier": "\(s.legacyTier.rawValue)",
              "status": "ACTIVE",
              "source": "\(s.tier == .free ? "DEFAULT_FREE" : "APP_STORE_SUBSCRIPTION")",
              "expiresAt": null,
              "capabilities": ["BASIC_SOLVE"],
              "packageCode": "\(s.packageCode.rawValue)",
              "featureEnabled": true,
              "paidAccessActive": \(s.tier != .free),
              "learningOsCapabilities": ["ATTEMPT_VERIFY", "DETERMINISTIC_TWIN"],
              "accessEvaluatedAt": "2026-11-22T12:00:00Z",
              "quotaSummary": {
                "periodKey": "\(s.periodKey)",
                "aiVerificationsUsed": \(s.used),
                "aiVerificationsLimit": \(s.limit),
                "remainingAiChecks": \(s.remaining),
                "hybridTwinsUsed": \(s.hybridUsed),
                "hybridTwinsLimit": \(s.hybridLimit),
                "deterministicTwinsUnlimited": true,
                "costCircuitBreakerTripped": \(s.breaker),
                "degradedToDeterministicOnly": \(s.degraded),
                "resetsAt": "2026-12-01T00:00:00Z"
              }
            }
            """
            let decoded = try decoder.decode(LearningOsEntitlementV2.self, from: Data(json.utf8))
            let quota = try XCTUnwrap(decoded.quotaSummary, s.name)
            XCTAssertEqual(quota.periodKey, s.periodKey, s.name)
            XCTAssertEqual(quota.aiVerificationsUsed, s.used, s.name)
            XCTAssertEqual(quota.aiVerificationsLimit, s.limit, s.name)
            XCTAssertEqual(quota.remainingAiChecks, s.remaining, s.name)
            XCTAssertEqual(quota.hybridTwinsUsed, s.hybridUsed, s.name)
            XCTAssertEqual(quota.hybridTwinsLimit, s.hybridLimit, s.name)
            XCTAssertTrue(quota.deterministicTwinsUnlimited, s.name)
            XCTAssertEqual(quota.costCircuitBreakerTripped, s.breaker, s.name)
            XCTAssertEqual(quota.degradedToDeterministicOnly, s.degraded, s.name)
            XCTAssertEqual(quota.isQuotaExhausted, s.degraded, s.name)
            XCTAssertNotNil(quota.resetsAtDate, s.name)
            XCTAssertGreaterThanOrEqual(quota.usageProgress, 0.0, s.name)
            XCTAssertLessThanOrEqual(quota.usageProgress, 1.0, s.name)

            let roundTrip = try decoder.decode(QuotaSummary.self, from: try encoder.encode(quota))
            XCTAssertEqual(roundTrip, quota, s.name)
        }
    }

    func testBackwardCompatibleDecodingWithoutQuotaSummary() throws {
        let json = """
        {
          "effectiveTier": "FREE",
          "legacyTier": "FREE",
          "status": "ACTIVE"
        }
        """
        let decoded = try decoder.decode(LearningOsEntitlementV2.self, from: Data(json.utf8))
        XCTAssertNil(decoded.quotaSummary)
    }

    func testEntitlementViewModelPublishesQuotaSummaryAndDegradedHelpers() async {
        let userId = UUID(uuidString: "00000000-0000-4000-8000-000000000701")!
        let scope = EntitlementDisplayCache.Scope(user: userId, session: UUID())
        let quota = QuotaSummary(
            periodKey: "2026-11",
            aiVerificationsUsed: 92,
            aiVerificationsLimit: 150,
            remainingAiChecks: 0,
            hybridTwinsUsed: 18,
            hybridTwinsLimit: 150,
            deterministicTwinsUnlimited: true,
            costCircuitBreakerTripped: true,
            degradedToDeterministicOnly: true,
            resetsAt: "2026-12-01T00:00:00Z"
        )
        let entitlement = Entitlement(
            id: UUID(),
            userId: userId,
            tier: .pro,
            source: .appStoreSubscription,
            status: .active,
            effectiveAt: Date(timeIntervalSince1970: 1_800_000_000),
            expiresAt: nil,
            capabilities: [.basicSolve, .verifiedSolve],
            version: 1
        )
        let learning = LearningOsEntitlementV2(
            effectiveTier: .pro,
            legacyTier: .pro,
            status: .active,
            source: .appStoreSubscription,
            packageCode: .pro,
            featureEnabled: true,
            learningOsCapabilities: [.attemptVerify, .deterministicTwin, .hybridTwin, .mistakePassportFull],
            paidAccessActive: true,
            quotaSummary: quota
        )
        let service = QuotaStubEntitlementService(entitlement: entitlement, learning: learning)
        let viewModel = EntitlementViewModel(
            entitlementAPI: service,
            displayCache: EntitlementDisplayCache(secureStorage: InMemorySecureStorage()),
            networkMonitor: QuotaStubNetworkMonitor(),
            logger: AppLogger(subsystem: "com.verifiedai.learning.tests", category: "quota")
        )
        viewModel.bind(scope)

        await viewModel.bootstrap()

        XCTAssertEqual(viewModel.quotaSummary, quota)
        XCTAssertTrue(viewModel.isDegradedToDeterministicOnly)
        XCTAssertEqual(viewModel.remainingAiChecksText, "0 / 150")
    }
}

@MainActor
private final class QuotaStubNetworkMonitor: NetworkMonitoring {
    var isReachable: Bool = true
}

private actor QuotaStubEntitlementService: EntitlementServicing {
    let entitlement: Entitlement
    let learning: LearningOsEntitlementV2

    init(entitlement: Entitlement, learning: LearningOsEntitlementV2) {
        self.entitlement = entitlement
        self.learning = learning
    }

    func currentEntitlement() async throws -> Entitlement {
        entitlement
    }

    func learningAccess() async throws -> LearningOsEntitlementV2 {
        learning
    }
}
