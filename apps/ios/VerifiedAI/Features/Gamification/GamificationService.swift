import Foundation
import Observation

public protocol GamificationServicing: Sendable {
    func fetchSummary(timezone: String) async throws -> GamificationSummaryResponse
    func recordActivity(_ request: GamificationActivityRecordRequest) async throws -> GamificationSummaryResponse
}

public final class GamificationService: GamificationServicing, @unchecked Sendable {
    private let apiClient: APIClient
    private let encoder = GamificationJSONCoding.makeEncoder()

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    public func fetchSummary(timezone: String = TimeZone.current.identifier) async throws -> GamificationSummaryResponse {
        let request = HTTPRequest<GamificationSummaryResponse>(
            endpoint: Endpoint(
                path: "/v1/gamification/summary",
                method: .get,
                queryItems: [URLQueryItem(name: "timezone", value: timezone)]
            )
        )
        return try await apiClient.send(request).body
    }

    public func recordActivity(_ request: GamificationActivityRecordRequest) async throws -> GamificationSummaryResponse {
        let bodyData = try encoder.encode(request)
        let httpRequest = HTTPRequest<GamificationSummaryResponse>(
            endpoint: Endpoint(
                path: "/v1/gamification/activity",
                method: .post
            ),
            body: bodyData,
            idempotencyKey: request.idempotencyKey
        )
        return try await apiClient.send(httpRequest).body
    }
}

@MainActor
@Observable
public final class GamificationViewModel {
    public enum State: Equatable, Sendable {
        case idle
        case loading
        case loaded(GamificationSummaryResponse)
        case degraded(message: String)
        case error(message: String)
    }

    public private(set) var state: State = .idle
    public var isMilestoneSheetPresented: Bool = false

    private let service: GamificationServicing

    public var summary: GamificationSummaryResponse? {
        if case .loaded(let response) = state {
            return response
        }
        return nil
    }

    public init(service: GamificationServicing) {
        self.service = service
    }

    public func loadSummary(timezone: String = TimeZone.current.identifier) async {
        if case .idle = state {
            state = .loading
        }
        do {
            let response = try await service.fetchSummary(timezone: timezone)
            state = .loaded(response)
        } catch {
            applyFailure(error)
        }
    }

    public func recordVerifiedActivity(_ request: GamificationActivityRecordRequest) async {
        do {
            let response = try await service.recordActivity(request)
            state = .loaded(response)
        } catch {
            applyFailure(error)
        }
    }

    public func purgeLocalState() {
        isMilestoneSheetPresented = false
        state = .idle
    }

    private func applyFailure(_ error: Error) {
        if let networkError = error as? NetworkError {
            switch networkError {
            case .server(let problem):
                if problem.status == 503 || problem.code == "GAMIFICATION_FEATURE_DISABLED" {
                    state = .degraded(message: problem.title)
                    return
                }
                state = .error(message: problem.title)
                return
            case .httpStatus(let status):
                if status == 401 || status == 403 {
                    state = .error(message: "Session refresh required")
                } else if (500...599).contains(status) {
                    state = .degraded(message: "Service temporarily unavailable")
                } else {
                    state = .error(message: "Unexpected status (\(status))")
                }
                return
            case .transport(let message):
                state = .degraded(message: message)
                return
            case .invalidURL, .invalidResponse, .decoding:
                state = .degraded(message: networkError.localizedDescription)
                return
            }
        }
        state = .error(message: error.localizedDescription)
    }
}
