import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class GrowthLoopsAndDeepLinkTests: XCTestCase {
    private func makeFingerprintStore() throws -> GrowthDeviceFingerprintStore {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("GrowthTests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        addTeardownBlock {
            try? FileManager.default.removeItem(at: dir)
        }
        return GrowthDeviceFingerprintStore(baseDirectory: dir)
    }

    func testDeepLinkRouterMapsShareAndReferralAndRejectsMalformedOrTraversalUrls() throws {
        let router = DeepLinkRouter()

        XCTAssertEqual(
            router.route(for: try XCTUnwrap(URL(string: "verified-ai://share/AbCdEfGhIjKlMnOpQrStUvWx"))),
            .sharedArtifact(token: "AbCdEfGhIjKlMnOpQrStUvWx")
        )
        XCTAssertEqual(
            router.route(for: try XCTUnwrap(URL(string: "verified-ai://referral/VERIFIED8"))),
            .referralInvite(code: "VERIFIED8")
        )

        XCTAssertNil(router.route(for: try XCTUnwrap(URL(string: "verified-ai://share/short"))))
        XCTAssertNil(router.route(for: try XCTUnwrap(URL(string: "verified-ai://share/AbCdEfGhIjKlMnOpQrStUvWx/extra"))))
        XCTAssertNil(router.route(for: try XCTUnwrap(URL(string: "verified-ai://referral/bad_0O1I"))))
        XCTAssertNil(router.route(for: try XCTUnwrap(URL(string: "verified-ai://referral/ABCD2345/extra"))))
        XCTAssertNil(router.route(for: try XCTUnwrap(URL(string: "verified-ai://share/../admin"))))
        XCTAssertNil(router.route(for: try XCTUnwrap(URL(string: "verified-ai://share/%2e%2e/admin"))))
        XCTAssertNil(router.route(for: try XCTUnwrap(URL(string: "https://example.com/share/AbCdEfGhIjKlMnOpQrStUvWx"))))
    }

    func testShareCardCreateRevokeReferralAttributionAndReset() async throws {
        let api = FixtureGrowthAPI()
        let prompter = StubStoreReviewPrompter()
        let fingerprintStore = try makeFingerprintStore()
        let viewModel = GrowthSharingViewModel(
            service: api,
            reviewPrompter: prompter,
            fingerprintStore: fingerprintStore,
            safeToPrompt: { true },
            appVersion: "1.0.0",
            localeProvider: { "en" }
        )

        await viewModel.loadDashboard()
        XCTAssertEqual(viewModel.referralSummary?.referralCode, "VERIFIED8")
        XCTAssertEqual(viewModel.shares.count, 1)

        await viewModel.createChallengeShare(displayAlias: "Deniz K.")
        XCTAssertEqual(viewModel.shares.count, 2)
        let created = try XCTUnwrap(viewModel.shares.first)
        XCTAssertTrue(created.rawContentRedacted)
        XCTAssertFalse(created.trainingEligible)
        XCTAssertEqual(created.llmTokensUsed, 0)

        await viewModel.revokeShare(shareId: created.shareId)
        XCTAssertEqual(viewModel.shares.first?.status, .revoked)

        // Self-referral displays localized error
        await viewModel.attributeReferral(code: "VERIFIED8", source: .manualCode)
        XCTAssertNotNil(viewModel.errorMessage)
        XCTAssertNil(viewModel.lastAttribution)

        // Friend referral succeeds
        await viewModel.attributeReferral(code: "FRIEND24", source: .deepLink)
        XCTAssertNil(viewModel.errorMessage)
        XCTAssertEqual(viewModel.lastAttribution?.status, .attributed)
        XCTAssertEqual(viewModel.lastAttribution?.referralCode, "FRIEND24")

        viewModel.reset()
        XCTAssertTrue(viewModel.shares.isEmpty)
        XCTAssertNil(viewModel.referralSummary)
        XCTAssertNil(viewModel.lastAttribution)
        XCTAssertNil(viewModel.errorMessage)
        XCTAssertNil(viewModel.statusMessage)
    }

    func testStoreReviewPromptSuppressesWhenTimerActiveAndPromptsWhenSafeAndEligible() async throws {
        let api = FixtureGrowthAPI()
        let prompter = StubStoreReviewPrompter()
        let fingerprintStore = try makeFingerprintStore()
        var timerSafe = false

        let viewModel = GrowthSharingViewModel(
            service: api,
            reviewPrompter: prompter,
            fingerprintStore: fingerprintStore,
            safeToPrompt: { timerSafe },
            appVersion: "1.0.0",
            localeProvider: { "en" }
        )

        // Active timer suppresses prompt -> invoke count == 0
        await viewModel.evaluateAndPromptStoreReviewIfEligible(triggerMoment: .independentTwinMastered)
        XCTAssertEqual(prompter.requestCount, 0)
        XCTAssertEqual(viewModel.lastReviewEligibility?.eligible, false)
        XCTAssertEqual(viewModel.lastReviewEligibility?.reasonCode, .activeTimer)

        // Safe & eligible -> invoke count == 1 and PROMPT_SHOWN recorded -> next check hits cooldown
        timerSafe = true
        await viewModel.evaluateAndPromptStoreReviewIfEligible(triggerMoment: .independentTwinMastered)
        XCTAssertEqual(prompter.requestCount, 1)
        XCTAssertEqual(viewModel.lastReviewEligibility?.eligible, true)

        await viewModel.evaluateAndPromptStoreReviewIfEligible(triggerMoment: .weeklyProgressWin)
        XCTAssertEqual(prompter.requestCount, 1)
        XCTAssertEqual(viewModel.lastReviewEligibility?.eligible, false)
        XCTAssertEqual(viewModel.lastReviewEligibility?.reasonCode, .cooldownActive)
    }

    func testPositiveContractFixturesDecodeWithTrainingIneligibleAndZeroTokens() throws {
        let publicShareJson = """
        {
          "shareToken": "AbCdEfGhIjKlMnOpQrStUvWx",
          "artifactType": "COUNTERFACTUAL_CHALLENGE_CARD",
          "locale": "en",
          "displayAlias": "Deniz K.",
          "referralCode": "VERIFIED8",
          "summaryPayload": {
            "mistakeCode": "SIGN_FLIP_LINEAR",
            "skillCode": "MATH.ALG.LINEAR_EQ",
            "challengeSummaryKey": "challenge.sign_flip.v1",
            "locale": "en",
            "difficultyBand": "FOUNDATION"
          },
          "deepLinkUrl": "verified-ai://share/AbCdEfGhIjKlMnOpQrStUvWx",
          "rawContentRedacted": true,
          "trainingEligible": false,
          "llmTokensUsed": 0,
          "expiresAt": "2026-12-24T12:00:00Z",
          "createdAt": "2026-11-24T12:00:00Z"
        }
        """
        let publicShare = try APIClient.decodeISO8601Response(
            PublicShareArtifactDTO.self,
            from: Data(publicShareJson.utf8)
        )
        XCTAssertTrue(publicShare.rawContentRedacted)
        XCTAssertFalse(publicShare.trainingEligible)
        XCTAssertEqual(publicShare.llmTokensUsed, 0)

        let referralSummaryJson = """
        {
          "referralCode": "VERIFIED8",
          "deepLinkUrl": "verified-ai://referral/VERIFIED8",
          "active": true,
          "totalAttributions": 2,
          "qualifiedCount": 1,
          "rewardedCount": 1,
          "totalRewardCredits": 5,
          "monthlyAttributionsRemaining": 8,
          "annualRewardsRemaining": 11,
          "trainingEligible": false,
          "llmTokensUsed": 0,
          "createdAt": "2026-11-24T12:00:00Z"
        }
        """
        let referralSummary = try APIClient.decodeISO8601Response(
            ReferralSummaryDTO.self,
            from: Data(referralSummaryJson.utf8)
        )
        XCTAssertFalse(referralSummary.trainingEligible)
        XCTAssertEqual(referralSummary.llmTokensUsed, 0)

        let reviewEligibilityJson = """
        {
          "eligible": true,
          "reasonCode": "ELIGIBLE",
          "nextEligibleAt": null,
          "triggerMoment": "INDEPENDENT_TWIN_MASTERED",
          "promptsShownLast365Days": 0,
          "trainingEligible": false,
          "llmTokensUsed": 0
        }
        """
        let reviewEligibility = try APIClient.decodeISO8601Response(
            StoreReviewEligibilityDTO.self,
            from: Data(reviewEligibilityJson.utf8)
        )
        XCTAssertTrue(reviewEligibility.eligible)
        XCTAssertFalse(reviewEligibility.trainingEligible)
        XCTAssertEqual(reviewEligibility.llmTokensUsed, 0)
    }
}
