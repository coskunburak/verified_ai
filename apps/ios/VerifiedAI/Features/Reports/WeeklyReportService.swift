import Foundation

protocol WeeklyReportServicing: Sendable {
    func fetchWeeklyDigest(weekIso: String, locale: String, forceRefresh: Bool) async throws -> WeeklyDigestV1
    func createSharePacket(weekIso: String, request: WeeklySharePacketRequest) async throws -> WeeklySharePacketV1
}

final class HTTPWeeklyReportService: WeeklyReportServicing, @unchecked Sendable {
    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func fetchWeeklyDigest(weekIso: String, locale: String, forceRefresh: Bool) async throws -> WeeklyDigestV1 {
        let encodedWeek = weekIso.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? weekIso
        let endpoint = Endpoint(
            path: "/v1/reports/weekly/\(encodedWeek)",
            method: .get,
            queryItems: [
                URLQueryItem(name: "locale", value: locale),
                URLQueryItem(name: "forceRefresh", value: forceRefresh ? "true" : "false")
            ]
        )
        let request = HTTPRequest<WeeklyDigestV1>(endpoint: endpoint, allowsAuthRefreshRetry: true)
        return try await apiClient.send(request).body
    }

    func createSharePacket(weekIso: String, request payload: WeeklySharePacketRequest) async throws -> WeeklySharePacketV1 {
        let encodedWeek = weekIso.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? weekIso
        let bodyData = try encoder.encode(payload)
        let endpoint = Endpoint(
            path: "/v1/reports/weekly/\(encodedWeek)/share-packet",
            method: .post
        )
        let request = HTTPRequest<WeeklySharePacketV1>(
            endpoint: endpoint,
            body: bodyData,
            allowsAuthRefreshRetry: true
        )
        return try await apiClient.send(request).body
    }
}

final class MockWeeklyReportService: WeeklyReportServicing, @unchecked Sendable {
    private let lock = NSLock()
    private var _fetchCalls: [(weekIso: String, locale: String, forceRefresh: Bool)] = []
    private var _shareCalls: [(weekIso: String, request: WeeklySharePacketRequest)] = []

    var stubbedDigest: Result<WeeklyDigestV1, Error>
    var stubbedSharePacket: Result<WeeklySharePacketV1, Error>?

    var fetchCalls: [(weekIso: String, locale: String, forceRefresh: Bool)] {
        lock.withLock { _fetchCalls }
    }

    var shareCalls: [(weekIso: String, request: WeeklySharePacketRequest)] {
        lock.withLock { _shareCalls }
    }

    init(
        stubbedDigest: Result<WeeklyDigestV1, Error> = .success(WeeklyReportFixtures.sampleDigest()),
        stubbedSharePacket: Result<WeeklySharePacketV1, Error>? = nil
    ) {
        self.stubbedDigest = stubbedDigest
        self.stubbedSharePacket = stubbedSharePacket
    }

    func fetchWeeklyDigest(weekIso: String, locale: String, forceRefresh: Bool) async throws -> WeeklyDigestV1 {
        lock.withLock {
            _fetchCalls.append((weekIso: weekIso, locale: locale, forceRefresh: forceRefresh))
        }
        return try stubbedDigest.get()
    }

    func createSharePacket(weekIso: String, request: WeeklySharePacketRequest) async throws -> WeeklySharePacketV1 {
        lock.withLock {
            _shareCalls.append((weekIso: weekIso, request: request))
        }
        if let stubbedSharePacket {
            return try stubbedSharePacket.get()
        }
        return WeeklyReportFixtures.sampleSharePacket(
            weekIso: weekIso,
            role: request.recipientRole,
            alias: (request.displayAlias?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false)
                ? request.displayAlias!.trimmingCharacters(in: .whitespacesAndNewlines)
                : "Learner A.",
            locale: request.locale ?? "en"
        )
    }
}
