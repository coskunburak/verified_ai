import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class LearningOsEntitlementV2DecoderTests: XCTestCase {
    private let decoder = JSONDecoder()

    func testDecodesAllSixteenSyntheticStates() throws {
        let fixtures: [(name: String, tier: LearningOsEffectiveTier, package: LearningPackage?, status: EntitlementStatus, paid: Bool, capCount: Int, passport: LearningOsEntitlementV2.HistoryLevel)] = [
            ("free", .free, .free, .active, false, 2, .summary),
            ("restored", .coachPro, .coach, .active, true, 8, .full),
            ("canceledUntilExpiry", .coachPro, .coach, .active, true, 8, .full),
            ("expired", .free, .free, .expired, false, 2, .summary),
            ("revoked", .free, .free, .revoked, false, 2, .summary),
            ("proActive", .pro, .pro, .active, true, 4, .full),
            ("coachActive", .coachPro, .coach, .active, true, 8, .full),
            ("seasonActive", .coachPro, .season, .active, true, 8, .full),
            ("graceWithinWindow", .coachPro, .coach, .gracePeriod, true, 8, .full),
            ("graceExpired", .free, .free, .gracePeriod, false, 2, .summary),
            ("billingRetryBeforeExpiry", .coachPro, .coach, .billingRetry, true, 8, .full),
            ("billingRetryAfterExpiry", .free, .free, .billingRetry, false, 2, .summary),
            ("refundImmediate", .free, .free, .revoked, false, 2, .summary),
            ("renewedAfterExpiry", .coachPro, .coach, .active, true, 8, .full),
            ("tutorAddonNotActive", .free, .free, .active, false, 2, .summary),
            ("flagDisabled", .coachPro, .coach, .active, true, 2, .full),
        ]

        XCTAssertEqual(fixtures.count, 16)
        for fixture in fixtures {
            let featureEnabled = fixture.name != "flagDisabled"
            let rawCaps: [String] = switch fixture.capCount {
            case 8: LearningOsCapability.allCases.map(\.rawValue)
            case 4: ["ATTEMPT_VERIFY", "DETERMINISTIC_TWIN", "HYBRID_TWIN", "MISTAKE_PASSPORT_FULL"]
            default: ["ATTEMPT_VERIFY", "DETERMINISTIC_TWIN"]
            }
            let legacyTier: String = switch fixture.tier {
            case .free where fixture.name == "free" || fixture.name == "tutorAddonNotActive": "FREE"
            case .pro: "PRO"
            default: "PRO_PLUS"
            }
            let json = """
            {
              "effectiveTier": "\(fixture.tier.rawValue)",
              "legacyTier": "\(legacyTier)",
              "status": "\(fixture.status.rawValue)",
              "source": "\(fixture.tier == .free && fixture.status == .active ? "DEFAULT_FREE" : "APP_STORE_SUBSCRIPTION")",
              "expiresAt": "2027-01-10T00:00:00Z",
              "capabilities": ["BASIC_SOLVE"],
              "packageCode": "\(fixture.package?.rawValue ?? "FREE")",
              "featureEnabled": \(featureEnabled),
              "learningOsCapabilities": \(encodeStringArray(rawCaps)),
              "historyAccess": {
                "mistakePassport": "\(fixture.passport.rawValue)",
                "skillMastery": "\(fixture.passport.rawValue)",
                "priorExams": "SUMMARY"
              },
              "accessEvaluatedAt": "2026-11-20T12:00:00Z",
              "gracePeriodExpiresAt": \(fixture.status == .gracePeriod ? "\"2026-11-22T00:00:00Z\"" : "null"),
              "paidAccessActive": \(fixture.paid)
            }
            """
            let decoded = try decoder.decode(LearningOsEntitlementV2.self, from: Data(json.utf8))
            XCTAssertEqual(decoded.effectiveTier, fixture.tier, fixture.name)
            XCTAssertEqual(decoded.packageCode, fixture.package, fixture.name)
            XCTAssertEqual(decoded.status, fixture.status, fixture.name)
            XCTAssertEqual(decoded.paidAccessActive, fixture.paid, fixture.name)
            XCTAssertEqual(decoded.featureEnabled, featureEnabled, fixture.name)
            XCTAssertEqual(decoded.learningOsCapabilities?.count, fixture.capCount, fixture.name)
            XCTAssertEqual(decoded.historyAccess?.mistakePassport, fixture.passport, fixture.name)
            XCTAssertEqual(decoded.historyAccess?.priorExams, .summary, fixture.name)
        }
    }

    func testRejectsSeasonOrAddonAsEffectiveTierAndUnknownEnumsAndHandlesOptionals() throws {
        for badTier in ["YKS_SEASON_PASS", "TUTOR_COPILOT_PACK", "UNKNOWN"] {
            let payload = """
            {"effectiveTier":"\(badTier)","legacyTier":"PRO_PLUS","status":"ACTIVE"}
            """
            XCTAssertThrowsError(try decoder.decode(LearningOsEntitlementV2.self, from: Data(payload.utf8)), badTier)
        }

        let badPackage = """
        {"effectiveTier":"COACH_PRO","legacyTier":"PRO_PLUS","status":"ACTIVE","packageCode":"TUTOR_COPILOT_PACK"}
        """
        XCTAssertThrowsError(try decoder.decode(LearningOsEntitlementV2.self, from: Data(badPackage.utf8)))

        let badStatus = """
        {"effectiveTier":"PRO","legacyTier":"PRO","status":"PAUSED"}
        """
        XCTAssertThrowsError(try decoder.decode(LearningOsEntitlementV2.self, from: Data(badStatus.utf8)))

        let minimal = """
        {"effectiveTier":"FREE","legacyTier":"FREE","status":"ACTIVE"}
        """
        let decodedMinimal = try decoder.decode(LearningOsEntitlementV2.self, from: Data(minimal.utf8))
        XCTAssertEqual(decodedMinimal.effectiveTier, .free)
        XCTAssertNil(decodedMinimal.packageCode)
        XCTAssertNil(decodedMinimal.gracePeriodExpiresAt)
        XCTAssertFalse(LearningOsAddOn.tutorCopilotPack.isAvailable)
    }

    private func encodeStringArray(_ values: [String]) -> String {
        let quoted = values.map { "\"\($0)\"" }.joined(separator: ",")
        return "[\(quoted)]"
    }
}
