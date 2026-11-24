import Foundation

protocol GrowthServicing: Sendable {
    func createShare(_ request: CreateShareArtifactRequestDTO, idempotencyKey: String) async throws -> ShareableArtifactDTO
    func listShares(limit: Int) async throws -> [ShareableArtifactDTO]
    func revokeShare(shareId: UUID, idempotencyKey: String) async throws -> ShareableArtifactDTO
    func fetchPublicShare(shareToken: String) async throws -> PublicShareArtifactDTO
    func fetchReferralSummary() async throws -> ReferralSummaryDTO
    func attributeReferral(_ request: ReferralAttributeRequestDTO, idempotencyKey: String) async throws -> ReferralAttributionResultDTO
    func qualifyReferral(idempotencyKey: String) async throws -> ReferralAttributionResultDTO
    func evaluateReviewEligibility(triggerMoment: StoreReviewTriggerMoment, activeTimerRunning: Bool) async throws -> StoreReviewEligibilityDTO
    func recordReviewEvent(_ request: StoreReviewEventRequestDTO, idempotencyKey: String) async throws -> StoreReviewEventResponseDTO
}

protocol StoreReviewPrompting: Sendable {
    @MainActor
    func requestReview()
}
