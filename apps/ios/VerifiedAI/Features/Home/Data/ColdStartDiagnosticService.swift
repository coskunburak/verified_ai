import Foundation

public enum ColdStartServiceError: Error, Equatable, Sendable {
    case notCompleted
    case featureDisabled
    case offline
    case unexpected(String)
}

public protocol ColdStartDiagnosticServicing: Sendable {
    func fetchLatestCalibration(learnerId: UUID, daysInactive: Int?) async throws -> ColdStartDiagnosticResult
    func submitDiagnostic(learnerId: UUID, request: ColdStartDiagnosticRequest) async throws -> ColdStartDiagnosticResult
}

final class HTTPColdStartDiagnosticService: ColdStartDiagnosticServicing, @unchecked Sendable {
    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func fetchLatestCalibration(learnerId: UUID, daysInactive: Int? = nil) async throws -> ColdStartDiagnosticResult {
        var queryItems: [URLQueryItem] = []
        if let daysInactive {
            queryItems.append(URLQueryItem(name: "daysInactive", value: String(daysInactive)))
        }
        let endpoint = Endpoint(
            path: "/v1/study-plan/cold-start",
            method: .get,
            queryItems: queryItems
        )
        let httpRequest = HTTPRequest<ColdStartDiagnosticResult>(
            endpoint: endpoint,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func submitDiagnostic(learnerId: UUID, request payload: ColdStartDiagnosticRequest) async throws -> ColdStartDiagnosticResult {
        let bodyData = try encoder.encode(payload)
        let endpoint = Endpoint(
            path: "/v1/study-plan/cold-start",
            method: .post
        )
        let httpRequest = HTTPRequest<ColdStartDiagnosticResult>(
            endpoint: endpoint,
            body: bodyData,
            idempotencyKey: payload.idempotencyKey,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    private func mapNetworkError(_ error: NetworkError) -> ColdStartServiceError {
        switch error {
        case .server(let problem):
            if problem.status == 404 || problem.code == "COLD_START_DIAGNOSTIC_NOT_FOUND" {
                return .notCompleted
            }
            if problem.status == 503 || problem.code == "COLD_START_FEATURE_DISABLED" {
                return .featureDisabled
            }
            return .unexpected(problem.title)
        case .httpStatus(404):
            return .notCompleted
        case .httpStatus(503):
            return .featureDisabled
        case .transport:
            return .offline
        default:
            return .unexpected(error.localizedDescription)
        }
    }
}

public final class MockColdStartDiagnosticService: ColdStartDiagnosticServicing, @unchecked Sendable {
    private let lock = NSLock()
    private var _fetchCalls: [(learnerId: UUID, daysInactive: Int?)] = []
    private var _submitCalls: [(learnerId: UUID, request: ColdStartDiagnosticRequest)] = []

    public var stubbedFetchResult: Result<ColdStartDiagnosticResult, Error> = .failure(ColdStartServiceError.notCompleted)
    public var stubbedSubmitResult: Result<ColdStartDiagnosticResult, Error>?

    public var fetchCalls: [(learnerId: UUID, daysInactive: Int?)] {
        lock.withLock { _fetchCalls }
    }

    public var submitCalls: [(learnerId: UUID, request: ColdStartDiagnosticRequest)] {
        lock.withLock { _submitCalls }
    }

    public init() {}

    public func fetchLatestCalibration(learnerId: UUID, daysInactive: Int?) async throws -> ColdStartDiagnosticResult {
        lock.withLock { _fetchCalls.append((learnerId, daysInactive)) }
        return try stubbedFetchResult.get()
    }

    public func submitDiagnostic(learnerId: UUID, request: ColdStartDiagnosticRequest) async throws -> ColdStartDiagnosticResult {
        lock.withLock { _submitCalls.append((learnerId, request)) }
        if let stubbedSubmitResult {
            return try stubbedSubmitResult.get()
        }
        return ColdStartLocalEvaluator.buildOfflinePreview(learnerId: learnerId, request: request)
    }
}
