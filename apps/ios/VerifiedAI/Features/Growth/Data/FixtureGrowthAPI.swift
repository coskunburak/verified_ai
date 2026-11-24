#if DEBUG
import Foundation

actor FixtureGrowthAPI: GrowthServicing {
    private var myReferralCode = "VERIFIED8"
    private var shares: [ShareableArtifactDTO] = []
    private var attributedCode: String?
    private var qualified = false
    private var reviewPromptShownCount = 0

    init() {
        let initialToken = "fixtureShareToken001"
        let now = Date()
        let initialShare = ShareableArtifactDTO(
            shareId: UUID(uuidString: "00000000-0000-0000-0000-000000009001")!,
            shareToken: initialToken,
            artifactType: .counterfactualChallengeCard,
            locale: "en",
            displayAlias: "Math Learner",
            referralCode: myReferralCode,
            summaryPayload: [
                "mistakeCode": .string("SIGN_FLIP_LINEAR"),
                "skillCode": .string("MATH.ALG.LINEAR_EQ"),
                "challengeSummaryKey": .string("challenge.sign_flip.v1"),
                "locale": .string("en"),
                "difficultyBand": .string("FOUNDATION")
            ],
            deepLinkUrl: "verified-ai://share/\(initialToken)",
            status: .active,
            rawContentRedacted: true,
            openCount: 2,
            correlationId: "fixture-corr-share-1",
            trainingEligible: false,
            llmTokensUsed: 0,
            expiresAt: now.addingTimeInterval(30 * 86_400),
            revokedAt: nil,
            createdAt: now,
            replayed: false
        )
        self.shares = [initialShare]
    }

    func createShare(_ request: CreateShareArtifactRequestDTO, idempotencyKey: String) async throws -> ShareableArtifactDTO {
        let token = "fixtureToken\(UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(16))"
        let now = Date()
        let share = ShareableArtifactDTO(
            shareId: UUID(),
            shareToken: token,
            artifactType: request.artifactType,
            locale: request.locale,
            displayAlias: request.displayAlias,
            referralCode: request.referralCode ?? myReferralCode,
            summaryPayload: request.summaryPayload,
            deepLinkUrl: "verified-ai://share/\(token)",
            status: .active,
            rawContentRedacted: true,
            openCount: 0,
            correlationId: "fixture-corr-create",
            trainingEligible: false,
            llmTokensUsed: 0,
            expiresAt: now.addingTimeInterval(TimeInterval(request.ttlDays ?? 30) * 86_400),
            revokedAt: nil,
            createdAt: now,
            replayed: false
        )
        shares.insert(share, at: 0)
        return share
    }

    func listShares(limit: Int) async throws -> [ShareableArtifactDTO] {
        Array(shares.prefix(max(1, min(limit, 50))))
    }

    func revokeShare(shareId: UUID, idempotencyKey: String) async throws -> ShareableArtifactDTO {
        guard let index = shares.firstIndex(where: { $0.shareId == shareId }) else {
            throw NetworkError.server(
                problem: ProblemDetails(
                    type: "https://errors.verified-ai-learning.example/growth-share-not-found",
                    title: "Shareable artifact not found",
                    status: 404,
                    code: "GROWTH_SHARE_NOT_FOUND",
                    traceId: "fixture-trace",
                    details: ProblemDetails.RecoveryDetails(recoverable: false, userAction: "CHECK_SHARE_ID")
                )
            )
        }
        let current = shares[index]
        let revoked = ShareableArtifactDTO(
            shareId: current.shareId,
            shareToken: current.shareToken,
            artifactType: current.artifactType,
            locale: current.locale,
            displayAlias: current.displayAlias,
            referralCode: current.referralCode,
            summaryPayload: current.summaryPayload,
            deepLinkUrl: current.deepLinkUrl,
            status: .revoked,
            rawContentRedacted: true,
            openCount: current.openCount,
            correlationId: current.correlationId,
            trainingEligible: false,
            llmTokensUsed: 0,
            expiresAt: current.expiresAt,
            revokedAt: Date(),
            createdAt: current.createdAt,
            replayed: current.status == .revoked
        )
        shares[index] = revoked
        return revoked
    }

    func fetchPublicShare(shareToken: String) async throws -> PublicShareArtifactDTO {
        guard let item = shares.first(where: { $0.shareToken == shareToken }) else {
            throw NetworkError.server(
                problem: ProblemDetails(
                    type: "https://errors.verified-ai-learning.example/growth-share-not-found",
                    title: "Shareable artifact not found",
                    status: 404,
                    code: "GROWTH_SHARE_NOT_FOUND",
                    traceId: "fixture-trace",
                    details: ProblemDetails.RecoveryDetails(recoverable: false, userAction: "CHECK_SHARE_LINK")
                )
            )
        }
        guard item.status == .active else {
            throw NetworkError.server(
                problem: ProblemDetails(
                    type: "https://errors.verified-ai-learning.example/growth-share-unavailable",
                    title: "This shared card is no longer available",
                    status: 410,
                    code: "GROWTH_SHARE_UNAVAILABLE",
                    traceId: "fixture-trace",
                    details: ProblemDetails.RecoveryDetails(recoverable: false, userAction: "REQUEST_NEW_SHARE")
                )
            )
        }
        return PublicShareArtifactDTO(
            shareToken: item.shareToken,
            artifactType: item.artifactType,
            locale: item.locale,
            displayAlias: item.displayAlias,
            referralCode: item.referralCode,
            summaryPayload: item.summaryPayload,
            deepLinkUrl: item.deepLinkUrl,
            rawContentRedacted: true,
            trainingEligible: false,
            llmTokensUsed: 0,
            expiresAt: item.expiresAt,
            createdAt: item.createdAt
        )
    }

    func fetchReferralSummary() async throws -> ReferralSummaryDTO {
        ReferralSummaryDTO(
            referralCode: myReferralCode,
            deepLinkUrl: "verified-ai://referral/\(myReferralCode)",
            active: true,
            totalAttributions: attributedCode == nil ? 0 : 1,
            qualifiedCount: qualified ? 1 : 0,
            rewardedCount: qualified ? 1 : 0,
            totalRewardCredits: qualified ? 5 : 0,
            monthlyAttributionsRemaining: 10,
            annualRewardsRemaining: 12,
            trainingEligible: false,
            llmTokensUsed: 0,
            createdAt: Date().addingTimeInterval(-7 * 86_400)
        )
    }

    func attributeReferral(_ request: ReferralAttributeRequestDTO, idempotencyKey: String) async throws -> ReferralAttributionResultDTO {
        let normalized = request.referralCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if normalized == myReferralCode {
            throw NetworkError.server(
                problem: ProblemDetails(
                    type: "https://errors.verified-ai-learning.example/growth-self-referral-forbidden",
                    title: "Self-referral is not permitted",
                    status: 409,
                    code: "GROWTH_SELF_REFERRAL_FORBIDDEN",
                    traceId: "fixture-trace",
                    details: ProblemDetails.RecoveryDetails(recoverable: false, userAction: "USE_FRIEND_CODE")
                )
            )
        }
        if let existing = attributedCode, existing != normalized {
            throw NetworkError.server(
                problem: ProblemDetails(
                    type: "https://errors.verified-ai-learning.example/growth-referral-already-attributed",
                    title: "Account already has an attributed referral",
                    status: 409,
                    code: "GROWTH_REFERRAL_ALREADY_ATTRIBUTED",
                    traceId: "fixture-trace",
                    details: ProblemDetails.RecoveryDetails(recoverable: false, userAction: "CONTINUE_LEARNING")
                )
            )
        }
        let replayed = attributedCode == normalized
        attributedCode = normalized
        return ReferralAttributionResultDTO(
            attributionId: UUID(uuidString: "00000000-0000-0000-0000-000000009101")!,
            referralCode: normalized,
            attributionSource: request.attributionSource,
            status: .attributed,
            rewardType: nil,
            rewardUnits: 0,
            correlationId: "fixture-corr-attr",
            trainingEligible: false,
            llmTokensUsed: 0,
            attributedAt: Date(),
            qualifiedAt: nil,
            rewardGrantedAt: nil,
            replayed: replayed
        )
    }

    func qualifyReferral(idempotencyKey: String) async throws -> ReferralAttributionResultDTO {
        qualified = true
        let now = Date()
        return ReferralAttributionResultDTO(
            attributionId: UUID(uuidString: "00000000-0000-0000-0000-000000009101")!,
            referralCode: attributedCode ?? "FRIEND24",
            attributionSource: .deepLink,
            status: .rewardGranted,
            rewardType: "BONUS_VERIFIED_SOLVES_CREDIT",
            rewardUnits: 5,
            correlationId: "fixture-corr-qualify",
            trainingEligible: false,
            llmTokensUsed: 0,
            attributedAt: now.addingTimeInterval(-3600),
            qualifiedAt: now,
            rewardGrantedAt: now,
            replayed: false
        )
    }

    func evaluateReviewEligibility(triggerMoment: StoreReviewTriggerMoment, activeTimerRunning: Bool) async throws -> StoreReviewEligibilityDTO {
        if activeTimerRunning {
            return StoreReviewEligibilityDTO(
                eligible: false,
                reasonCode: .activeTimer,
                nextEligibleAt: nil,
                triggerMoment: triggerMoment,
                promptsShownLast365Days: reviewPromptShownCount,
                trainingEligible: false,
                llmTokensUsed: 0
            )
        }
        if reviewPromptShownCount > 0 {
            return StoreReviewEligibilityDTO(
                eligible: false,
                reasonCode: .cooldownActive,
                nextEligibleAt: Date().addingTimeInterval(60 * 86_400),
                triggerMoment: triggerMoment,
                promptsShownLast365Days: reviewPromptShownCount,
                trainingEligible: false,
                llmTokensUsed: 0
            )
        }
        return StoreReviewEligibilityDTO(
            eligible: true,
            reasonCode: .eligible,
            nextEligibleAt: nil,
            triggerMoment: triggerMoment,
            promptsShownLast365Days: reviewPromptShownCount,
            trainingEligible: false,
            llmTokensUsed: 0
        )
    }

    func recordReviewEvent(_ request: StoreReviewEventRequestDTO, idempotencyKey: String) async throws -> StoreReviewEventResponseDTO {
        if request.outcome == .promptShown {
            reviewPromptShownCount += 1
        }
        return StoreReviewEventResponseDTO(
            eventId: UUID(),
            triggerMoment: request.triggerMoment,
            outcome: request.outcome,
            appVersion: request.appVersion,
            locale: request.locale,
            correlationId: "fixture-corr-review",
            trainingEligible: false,
            llmTokensUsed: 0,
            occurredAt: Date(),
            replayed: false
        )
    }
}
#endif
