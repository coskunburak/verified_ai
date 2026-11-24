import Foundation
import Observation

@MainActor
@Observable
final class GrowthSharingViewModel {
    private let service: any GrowthServicing
    private let reviewPrompter: any StoreReviewPrompting
    private let fingerprintStore: GrowthDeviceFingerprintStore
    private var safeToPrompt: @MainActor () -> Bool
    private let appVersion: String
    private let localeProvider: @Sendable () -> String

    private(set) var shares: [ShareableArtifactDTO] = []
    private(set) var referralSummary: ReferralSummaryDTO?
    private(set) var lastAttribution: ReferralAttributionResultDTO?
    private(set) var publicPreview: PublicShareArtifactDTO?
    private(set) var publicPreviewErrorMessage: String?
    private(set) var lastReviewEligibility: StoreReviewEligibilityDTO?
    private(set) var statusMessage: String?
    private(set) var errorMessage: String?
    private(set) var isLoading = false
    private(set) var isBusy = false
    var isPublicPreviewPresented = false

    init(
        service: any GrowthServicing,
        reviewPrompter: any StoreReviewPrompting,
        fingerprintStore: GrowthDeviceFingerprintStore = GrowthDeviceFingerprintStore(),
        safeToPrompt: @escaping @MainActor () -> Bool,
        appVersion: String = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0",
        localeProvider: @escaping @Sendable () -> String = {
            Locale.current.language.languageCode?.identifier == "tr" ? "tr" : "en"
        }
    ) {
        self.service = service
        self.reviewPrompter = reviewPrompter
        self.fingerprintStore = fingerprintStore
        self.safeToPrompt = safeToPrompt
        self.appVersion = appVersion
        self.localeProvider = localeProvider
    }

    func loadDashboard() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            async let summaryTask = service.fetchReferralSummary()
            async let sharesTask = service.listShares(limit: 20)
            let (summary, fetchedShares) = try await (summaryTask, sharesTask)
            self.referralSummary = summary
            self.shares = fetchedShares
        } catch {
            self.errorMessage = Self.localizedErrorMessage(for: error)
        }
    }

    func createChallengeShare(displayAlias: String? = nil) async {
        let locale = localeProvider()
        let cleanedAlias = Self.normalizedOptionalAlias(displayAlias)
        let request = CreateShareArtifactRequestDTO(
            artifactType: .counterfactualChallengeCard,
            locale: locale,
            displayAlias: cleanedAlias,
            referralCode: referralSummary?.referralCode,
            summaryPayload: [
                "mistakeCode": .string("SIGN_FLIP_LINEAR"),
                "skillCode": .string("MATH.ALG.LINEAR_EQ"),
                "challengeSummaryKey": .string("challenge.sign_flip.v1"),
                "locale": .string(locale),
                "difficultyBand": .string("FOUNDATION")
            ],
            ttlDays: 30
        )
        await createShare(request: request)
    }

    func createWeeklyMilestoneShare(displayAlias: String? = nil) async {
        let locale = localeProvider()
        let cleanedAlias = Self.normalizedOptionalAlias(displayAlias)
        let request = CreateShareArtifactRequestDTO(
            artifactType: .weeklyProgressSummary,
            locale: locale,
            displayAlias: cleanedAlias,
            referralCode: referralSummary?.referralCode,
            summaryPayload: [
                "weekIso": .string("2026-W47"),
                "verifiedSessionsCount": .int(5),
                "activeDaysCount": .int(4),
                "topSkillDomain": .string("ALGEBRA"),
                "milestoneBadge": .string("CONSISTENT_PRACTICE")
            ],
            ttlDays: 30
        )
        await createShare(request: request)
    }

    private func createShare(request: CreateShareArtifactRequestDTO) async {
        isBusy = true
        errorMessage = nil
        statusMessage = nil
        defer { isBusy = false }

        do {
            let key = "share-\(UUID().uuidString.lowercased())"
            let created = try await service.createShare(request, idempotencyKey: key)
            shares.removeAll { $0.shareId == created.shareId }
            shares.insert(created, at: 0)
            statusMessage = String(localized: "growth.share.status.created")
        } catch {
            errorMessage = Self.localizedErrorMessage(for: error)
        }
    }

    func revokeShare(shareId: UUID) async {
        isBusy = true
        errorMessage = nil
        statusMessage = nil
        defer { isBusy = false }

        do {
            let key = "revoke-\(shareId.uuidString.lowercased())-\(UUID().uuidString.prefix(8).lowercased())"
            let updated = try await service.revokeShare(shareId: shareId, idempotencyKey: key)
            if let idx = shares.firstIndex(where: { $0.shareId == shareId }) {
                shares[idx] = updated
            }
            statusMessage = String(localized: "growth.share.status.revoked")
        } catch {
            errorMessage = Self.localizedErrorMessage(for: error)
        }
    }

    func openPublicShare(token: String) async {
        publicPreview = nil
        publicPreviewErrorMessage = nil
        isPublicPreviewPresented = true
        isBusy = true
        defer { isBusy = false }

        do {
            let preview = try await service.fetchPublicShare(shareToken: token)
            self.publicPreview = preview
        } catch {
            self.publicPreviewErrorMessage = Self.localizedErrorMessage(for: error)
        }
    }

    func dismissPublicPreview() {
        isPublicPreviewPresented = false
        publicPreview = nil
        publicPreviewErrorMessage = nil
    }

    func attributeReferral(code: String, source: ReferralAttributionSource) async {
        let normalizedCode = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !normalizedCode.isEmpty else {
            errorMessage = String(localized: "growth.referral.error.invalid_code")
            return
        }
        isBusy = true
        errorMessage = nil
        statusMessage = nil
        defer { isBusy = false }

        do {
            let fingerprintHash = await fingerprintStore.hashedFingerprint()
            let request = ReferralAttributeRequestDTO(
                referralCode: normalizedCode,
                attributionSource: source,
                deviceFingerprintHash: fingerprintHash
            )
            let key = "attr-\(normalizedCode)-\(UUID().uuidString.prefix(8).lowercased())"
            let result = try await service.attributeReferral(request, idempotencyKey: key)
            self.lastAttribution = result
            self.statusMessage = String(localized: "growth.referral.status.attributed")
            if let summary = try? await service.fetchReferralSummary() {
                self.referralSummary = summary
            }
        } catch {
            errorMessage = Self.localizedErrorMessage(for: error)
        }
    }

    func evaluateAndPromptStoreReviewIfEligible(triggerMoment: StoreReviewTriggerMoment) async {
        guard safeToPrompt() else {
            lastReviewEligibility = StoreReviewEligibilityDTO(
                eligible: false,
                reasonCode: .activeTimer,
                nextEligibleAt: nil,
                triggerMoment: triggerMoment,
                promptsShownLast365Days: lastReviewEligibility?.promptsShownLast365Days ?? 0,
                trainingEligible: false,
                llmTokensUsed: 0
            )
            return
        }

        do {
            let eligibility = try await service.evaluateReviewEligibility(
                triggerMoment: triggerMoment,
                activeTimerRunning: false
            )
            self.lastReviewEligibility = eligibility

            guard eligibility.eligible, safeToPrompt() else {
                return
            }

            reviewPrompter.requestReview()

            let eventRequest = StoreReviewEventRequestDTO(
                triggerMoment: triggerMoment,
                outcome: .promptShown,
                appVersion: appVersion,
                locale: localeProvider()
            )
            let eventKey = "rev-\(triggerMoment.rawValue.lowercased())-\(UUID().uuidString.prefix(8).lowercased())"
            _ = try? await service.recordReviewEvent(eventRequest, idempotencyKey: eventKey)
        } catch {
            // Non-blocking: store review checks must never interrupt learning or study flows.
        }
    }

    func updateSafeToPrompt(_ predicate: @escaping @MainActor () -> Bool) {
        self.safeToPrompt = predicate
    }

    func reset() {
        shares = []
        referralSummary = nil
        lastAttribution = nil
        publicPreview = nil
        publicPreviewErrorMessage = nil
        lastReviewEligibility = nil
        statusMessage = nil
        errorMessage = nil
        isLoading = false
        isBusy = false
        isPublicPreviewPresented = false
    }

    private static func normalizedOptionalAlias(_ raw: String?) -> String? {
        guard let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }

    private static func localizedErrorMessage(for error: Error) -> String {
        if let networkError = error as? NetworkError {
            switch networkError {
            case .server(let problem):
                switch problem.code {
                case "GROWTH_FEATURE_DISABLED":
                    return String(localized: "growth.error.feature_disabled")
                case "GROWTH_SELF_REFERRAL_FORBIDDEN":
                    return String(localized: "growth.referral.error.self_referral")
                case "GROWTH_CIRCULAR_REFERRAL_FORBIDDEN":
                    return String(localized: "growth.referral.error.circular_referral")
                case "GROWTH_REFERRAL_ALREADY_ATTRIBUTED":
                    return String(localized: "growth.referral.error.already_attributed")
                case "GROWTH_ABUSE_LIMIT_EXCEEDED":
                    return String(localized: "growth.referral.error.limit_exceeded")
                case "GROWTH_REFERRAL_CODE_NOT_FOUND":
                    return String(localized: "growth.referral.error.not_found")
                case "GROWTH_SHARE_UNAVAILABLE":
                    return String(localized: "growth.share.error.unavailable")
                case "GROWTH_SHARE_NOT_FOUND":
                    return String(localized: "growth.share.error.not_found")
                case "GROWTH_PII_OR_RAW_CONTENT_REJECTED":
                    return String(localized: "growth.share.error.pii_rejected")
                default:
                    return problem.title
                }
            case .transport:
                return String(localized: "growth.error.offline")
            case .httpStatus(let status) where status == 410:
                return String(localized: "growth.share.error.unavailable")
            case .httpStatus(let status) where status == 503:
                return String(localized: "growth.error.feature_disabled")
            default:
                return String(localized: "growth.error.generic")
            }
        }
        return String(localized: "growth.error.generic")
    }
}
