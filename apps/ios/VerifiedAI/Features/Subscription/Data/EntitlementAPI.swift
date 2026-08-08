import Foundation

protocol EntitlementServicing: Sendable {
    func currentEntitlement() async throws -> Entitlement
}

final class EntitlementAPI: EntitlementServicing, @unchecked Sendable {
    private let apiClient: APIClient

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func currentEntitlement() async throws -> Entitlement {
        let response: HTTPResponse<EntitlementWireResponse> = try await apiClient.send(
            HTTPRequest(endpoint: Endpoint(path: "/api/v1/me/entitlements", method: .get))
        )
        return try response.body.entitlement()
    }
}

struct EntitlementWireResponse: Decodable, Sendable {
    let id: UUID
    let userId: UUID
    let tier: String
    let source: String
    let status: String
    let effectiveAt: String
    let expiresAt: String?
    let capabilities: [String]
    let version: Int64?

    func entitlement() throws -> Entitlement {
        guard let tier = EntitlementTier(rawValue: tier),
              let source = EntitlementSource(rawValue: source),
              let status = EntitlementStatus(rawValue: status),
              let effectiveAt = Self.parseDate(effectiveAt) else {
            throw NetworkError.decoding("unsupported_entitlement_value")
        }

        return Entitlement(
            id: id,
            userId: userId,
            tier: tier,
            source: source,
            status: status,
            effectiveAt: effectiveAt,
            expiresAt: Self.parseDate(expiresAt),
            capabilities: Set(capabilities.compactMap(PremiumCapability.init(rawValue:))),
            version: version
        )
    }

    private static func parseDate(_ value: String?) -> Date? {
        guard let value else {
            return nil
        }

        let fractionalFormatter = ISO8601DateFormatter()
        fractionalFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = fractionalFormatter.date(from: value) {
            return date
        }

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value)
    }
}
