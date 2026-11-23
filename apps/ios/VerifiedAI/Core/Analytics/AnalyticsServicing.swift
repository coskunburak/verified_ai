import Foundation

public protocol AnalyticsBatchTransport: Sendable {
    func sendBatch(_ request: AnalyticsEventBatchRequestDTO) async throws -> AnalyticsEventIngestResponseDTO
    func fetchCatalog() async throws -> AnalyticsEventCatalogResponseDTO
}

public protocol AnalyticsTracking: Sendable {
    func track(_ event: AnalyticsClientEvent) async
}

final class HTTPAnalyticsAPI: AnalyticsBatchTransport, @unchecked Sendable {
    private let apiClient: APIClient

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func sendBatch(_ request: AnalyticsEventBatchRequestDTO) async throws -> AnalyticsEventIngestResponseDTO {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        let body = try encoder.encode(request)
        let response: HTTPResponse<AnalyticsEventIngestResponseDTO> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/analytics/events/batch", method: .post),
                body: body,
                decodesISO8601Dates: true
            )
        )
        return response.body
    }

    func fetchCatalog() async throws -> AnalyticsEventCatalogResponseDTO {
        let response: HTTPResponse<AnalyticsEventCatalogResponseDTO> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/analytics/catalog", method: .get),
                decodesISO8601Dates: true
            )
        )
        return response.body
    }
}

public actor FixtureAnalyticsTracker: AnalyticsTracking {
    public private(set) var trackedEvents: [AnalyticsClientEvent] = []

    public init() {}

    public func track(_ event: AnalyticsClientEvent) async {
        trackedEvents.append(event)
    }

    public func reset() {
        trackedEvents.removeAll()
    }
}

public struct NoOpAnalyticsTracker: AnalyticsTracking {
    public init() {}

    public func track(_ event: AnalyticsClientEvent) async {}
}
