import Foundation

final class GrowthAPI: GrowthServicing, @unchecked Sendable {
    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func createShare(_ request: CreateShareArtifactRequestDTO, idempotencyKey: String) async throws -> ShareableArtifactDTO {
        let body = try encoder.encode(request)
        let response: HTTPResponse<ShareableArtifactDTO> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/growth/shares", method: .post),
                body: body,
                idempotencyKey: idempotencyKey,
                decodesISO8601Dates: true
            )
        )
        return response.body
    }

    func listShares(limit: Int) async throws -> [ShareableArtifactDTO] {
        let clampedLimit = max(1, min(limit, 50))
        let response: HTTPResponse<ShareableArtifactListResponseDTO> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(
                    path: "/api/v1/growth/shares",
                    method: .get,
                    queryItems: [URLQueryItem(name: "limit", value: String(clampedLimit))]
                ),
                decodesISO8601Dates: true
            )
        )
        return response.body.items
    }

    func revokeShare(shareId: UUID, idempotencyKey: String) async throws -> ShareableArtifactDTO {
        let response: HTTPResponse<ShareableArtifactDTO> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/growth/shares/\(shareId.uuidString)/revoke", method: .post),
                idempotencyKey: idempotencyKey,
                decodesISO8601Dates: true
            )
        )
        return response.body
    }

    func fetchPublicShare(shareToken: String) async throws -> PublicShareArtifactDTO {
        let response: HTTPResponse<PublicShareArtifactDTO> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/growth/shares/public/\(shareToken)", method: .get),
                allowsAuthRefreshRetry: false,
                decodesISO8601Dates: true
            )
        )
        return response.body
    }

    func fetchReferralSummary() async throws -> ReferralSummaryDTO {
        let response: HTTPResponse<ReferralSummaryDTO> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/growth/referrals/me", method: .get),
                decodesISO8601Dates: true
            )
        )
        return response.body
    }

    func attributeReferral(_ request: ReferralAttributeRequestDTO, idempotencyKey: String) async throws -> ReferralAttributionResultDTO {
        let body = try encoder.encode(request)
        let response: HTTPResponse<ReferralAttributionResultDTO> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/growth/referrals/attribute", method: .post),
                body: body,
                idempotencyKey: idempotencyKey,
                decodesISO8601Dates: true
            )
        )
        return response.body
    }

    func qualifyReferral(idempotencyKey: String) async throws -> ReferralAttributionResultDTO {
        let response: HTTPResponse<ReferralAttributionResultDTO> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/growth/referrals/qualify", method: .post),
                idempotencyKey: idempotencyKey,
                decodesISO8601Dates: true
            )
        )
        return response.body
    }

    func evaluateReviewEligibility(triggerMoment: StoreReviewTriggerMoment, activeTimerRunning: Bool) async throws -> StoreReviewEligibilityDTO {
        let response: HTTPResponse<StoreReviewEligibilityDTO> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(
                    path: "/api/v1/growth/review-prompts/eligibility",
                    method: .get,
                    queryItems: [
                        URLQueryItem(name: "triggerMoment", value: triggerMoment.rawValue),
                        URLQueryItem(name: "activeTimerRunning", value: activeTimerRunning ? "true" : "false")
                    ]
                ),
                decodesISO8601Dates: true
            )
        )
        return response.body
    }

    func recordReviewEvent(_ request: StoreReviewEventRequestDTO, idempotencyKey: String) async throws -> StoreReviewEventResponseDTO {
        let body = try encoder.encode(request)
        let response: HTTPResponse<StoreReviewEventResponseDTO> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/growth/review-prompts/events", method: .post),
                body: body,
                idempotencyKey: idempotencyKey,
                decodesISO8601Dates: true
            )
        )
        return response.body
    }
}
