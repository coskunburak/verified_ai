import Foundation
import XCTest
@testable import VerifiedAI

final class AnalyticsEventCatalogAndSanitizerTests: XCTestCase {

    func testDecodesCatalogAndIngestResponseFixturesAndVerifiesAllClientEventsAgainstCatalog() throws {
        let repoRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()

        let catalogURL = repoRoot.appendingPathComponent("packages/schemas/analytics/fixtures/analytics-event-catalog-v1.valid.json")
        let responseURL = repoRoot.appendingPathComponent("packages/schemas/analytics/fixtures/analytics-event-ingest-response-v1.valid.json")

        let catalogData = try Data(contentsOf: catalogURL)
        let responseData = try Data(contentsOf: responseURL)

        let catalog = try APIClient.decodeISO8601Response(AnalyticsEventCatalogResponseDTO.self, from: catalogData)
        let ingestResponse = try APIClient.decodeISO8601Response(AnalyticsEventIngestResponseDTO.self, from: responseData)

        XCTAssertEqual(catalog.schemaVersion, "analytics-event-catalog-v1")
        XCTAssertEqual(catalog.catalogVersion, "1.0.0")
        XCTAssertEqual(catalog.eventCount, 56)
        XCTAssertEqual(catalog.events.count, 56)
        XCTAssertEqual(catalog.llmTokensUsed, 0)
        XCTAssertFalse(catalog.trainingEligible)

        XCTAssertEqual(ingestResponse.schemaVersion, "analytics-event-ingest-response-v1")
        XCTAssertEqual(ingestResponse.acceptedCount, 1)
        XCTAssertEqual(ingestResponse.replayedCount, 1)
        XCTAssertEqual(ingestResponse.rejectedCount, 1)
        XCTAssertEqual(ingestResponse.llmTokensUsed, 0)
        XCTAssertFalse(ingestResponse.trainingEligible)
        XCTAssertEqual(ingestResponse.results.count, 3)

        let catalogByName = Dictionary(uniqueKeysWithValues: catalog.events.map { ($0.eventName, $0) })

        let allClientEvents: [AnalyticsClientEvent] = [
            .onboardingStarted(authMethod: .apple, locale: .en, educationLevel: "HIGH_SCHOOL", dailyBudgetMinutes: 20),
            .onboardingCompleted(locale: .tr, educationLevel: "UNIVERSITY", dailyBudgetMinutes: 30),
            .signInCompleted(authMethod: .email, locale: .en, latencyBucket: .sub200ms),
            .problemCaptureStarted(locale: .en),
            .problemCaptureSourceSelected(source: .camera, assetKind: .image, locale: .en),
            .problemCaptureQualityWarning(source: .camera, warningCode: .blur, locale: .tr),
            .problemCaptureAccepted(source: .pdf, assetKind: .pdf, locale: .en),
            .firstVerifiedSolutionViewed(verificationStatus: .verified, entitlementTier: .free, locale: .en),
            .verificationDetailsViewed(verificationStatus: .partiallyVerified, entitlementTier: .pro, locale: .en),
            .solutionAlternativeViewed(verificationStatus: .verified, entitlementTier: .pro, locale: .tr),
            .tutorSessionStarted(pedagogicalPhase: "CLARIFY_GOAL", entitlementTier: .pro, locale: .en),
            .tutorTurnSubmitted(pedagogicalPhase: "ELICIT_CONCEPT", turnCountBucket: "2_5", entitlementTier: .pro, locale: .en),
            .hintRequested(hintLevel: .h1, pedagogicalPhase: "OFFER_MINIMAL_SUPPORT", entitlementTier: .pro, locale: .tr),
            .answerRevealed(pedagogicalPhase: "SUMMARIZE_CONCEPT", entitlementTier: .pro, locale: .en),
            .tutorSessionCompleted(pedagogicalPhase: "COMPLETED", turnCountBucket: "2_5", entitlementTier: .pro, locale: .en),
            .mistakeReviewed(canonicalSkillCode: "MATH.ALG.LINEAR_EQ", mistakeCode: "SIGN_FLIP", entitlementTier: .free, locale: .en),
            .practiceSessionStarted(canonicalSkillCode: "MATH.ALG.LINEAR_EQ", entitlementTier: .pro, locale: .tr),
            .examReadinessViewed(examCode: "TR_YKS_TYT_MATH", jurisdiction: "TR", confidenceTier: "ESTABLISHED", entitlementTier: .coachPro, locale: .tr),
            .paywallViewed(placement: .settings, tierCode: "PRO", entitlementTier: .free, locale: .en),
            .purchaseStarted(placement: .contextual, packageCode: "pro.monthly", tierCode: "PRO", entitlementTier: .free, locale: .tr),
            .purchaseFailed(placement: .settings, packageCode: "pro.monthly", failureReason: "CANCELLED", entitlementTier: .free, locale: .en),
            .restoreCompleted(placement: .settings, entitlementTier: .pro, locale: .en),
            .paywallMistakeMomentShown(placement: .mistakeMoment, tierCode: "PRO", entitlementTier: .free, locale: .tr),
            .paywallTwinSuccessShown(placement: .twinSuccess, tierCode: "PRO", entitlementTier: .free, locale: .en),
            .lifecycleNotificationOpened(category: .studyPlan, intentType: "STUDY_PLAN_DAILY_MISSION", entitlementTier: .free, locale: .tr),
            .weeklyDigestViewed(weekIso: "2026-W47", entitlementTier: .pro, locale: .tr),
        ]

        XCTAssertEqual(allClientEvents.count, 26)
        XCTAssertEqual(Set(allClientEvents.map(\.eventName)).count, 26)

        for event in allClientEvents {
            let entry = try XCTUnwrap(catalogByName[event.eventName], "Missing event \(event.eventName) in catalog")
            XCTAssertTrue(entry.clientAllowed, "Expected clientAllowed=true for \(event.eventName)")
            XCTAssertEqual(entry.eventDomain, event.eventDomain)
            for key in event.properties.keys {
                XCTAssertTrue(entry.allowedPropertyKeys.contains(key), "Key \(key) not allowlisted on \(event.eventName)")
            }
            let dto = try AnalyticsPrivacySanitizer.buildValidatedDTO(from: event)
            XCTAssertFalse(dto.trainingEligible)
            XCTAssertEqual(dto.emittedBy, "CLIENT")
            XCTAssertEqual(dto.sourceModule, "ios_client")
        }
    }

    func testSanitizerRejectsGenericUiEventsServerOnlyEventsPiiAndOversizedPayloads() {
        let now = Date(timeIntervalSince1970: 1_795_424_100)

        XCTAssertThrowsError(
            try AnalyticsPrivacySanitizer.validate(makeDTO(eventName: "button_clicked", properties: [:], now: now))
        ) { error in
            XCTAssertEqual(error as? AnalyticsSanitizationError, .eventUnknown)
        }

        XCTAssertThrowsError(
            try AnalyticsPrivacySanitizer.validate(makeDTO(eventName: "purchase_completed", properties: [:], now: now))
        ) { error in
            XCTAssertEqual(error as? AnalyticsSanitizationError, .sourceForbidden)
        }

        XCTAssertThrowsError(
            try AnalyticsPrivacySanitizer.validate(makeDTO(eventName: "twin_drill.completed", properties: [:], now: now))
        ) { error in
            XCTAssertEqual(error as? AnalyticsSanitizationError, .sourceForbidden)
        }

        XCTAssertThrowsError(
            try AnalyticsPrivacySanitizer.validate(
                makeDTO(eventName: "verification_details_viewed", properties: ["rawProblemText": .string("2x+1=5")], now: now)
            )
        ) { error in
            XCTAssertEqual(error as? AnalyticsSanitizationError, .piiOrRawContentRejected)
        }

        XCTAssertThrowsError(
            try AnalyticsPrivacySanitizer.validate(
                makeDTO(eventName: "verification_details_viewed", properties: ["primarySkillCode": .string("student@example.com")], now: now)
            )
        ) { error in
            XCTAssertEqual(error as? AnalyticsSanitizationError, .piiOrRawContentRejected)
        }

        XCTAssertThrowsError(
            try AnalyticsPrivacySanitizer.validate(
                makeDTO(eventName: "verification_details_viewed", properties: ["primarySkillCode": .string("eyJhbGciOiJIUzI1NiJ9.abc")], now: now)
            )
        ) { error in
            XCTAssertEqual(error as? AnalyticsSanitizationError, .piiOrRawContentRejected)
        }

        XCTAssertThrowsError(
            try AnalyticsPrivacySanitizer.validate(
                makeDTO(eventName: "verification_details_viewed", properties: ["primarySkillCode": .string("https://s3.example.com/signed")], now: now)
            )
        ) { error in
            XCTAssertEqual(error as? AnalyticsSanitizationError, .piiOrRawContentRejected)
        }

        XCTAssertThrowsError(
            try AnalyticsPrivacySanitizer.validate(
                makeDTO(eventName: "verification_details_viewed", properties: ["primarySkillCode": .string("\\frac{1}{2}")], now: now)
            )
        ) { error in
            XCTAssertEqual(error as? AnalyticsSanitizationError, .piiOrRawContentRejected)
        }

        XCTAssertThrowsError(
            try AnalyticsPrivacySanitizer.validate(
                makeDTO(eventName: "verification_details_viewed", properties: ["primarySkillCode": .string(String(repeating: "a", count: 81))], now: now)
            )
        ) { error in
            XCTAssertEqual(error as? AnalyticsSanitizationError, .piiOrRawContentRejected)
        }

        XCTAssertThrowsError(
            try AnalyticsPrivacySanitizer.validate(
                makeDTO(eventName: "onboarding_started", properties: [:], trainingEligible: true, now: now)
            )
        ) { error in
            XCTAssertEqual(error as? AnalyticsSanitizationError, .trainingEligibleForbidden)
        }
    }

    private func makeDTO(
        eventName: String,
        properties: [String: AnalyticsPropertyValue],
        trainingEligible: Bool = false,
        now: Date
    ) -> AnalyticsEventIngestRequestDTO {
        AnalyticsEventIngestRequestDTO(
            clientEventId: "ios-sanitizer-test-0001",
            eventName: eventName,
            emittedBy: "CLIENT",
            sourceModule: "ios_client",
            entitlementTier: "FREE",
            locale: "en",
            appVersion: "1.6.0",
            sessionRefHash: nil,
            latencyMs: nil,
            properties: properties,
            occurredAt: now,
            trainingEligible: trainingEligible
        )
    }
}
