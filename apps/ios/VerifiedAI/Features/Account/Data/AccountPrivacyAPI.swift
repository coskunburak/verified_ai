import Foundation

protocol AccountPrivacyServicing: Sendable {
    func currentAccount() async throws -> AccountState
    func requestDataExport() async throws -> DataExportRecord
    func dataExportStatus(exportId: UUID) async throws -> DataExportRecord
    func downloadDataExport(exportId: UUID) async throws -> DataExportDocument
    func requestDeletion() async throws -> DeletionRequest
    func deletionRequest() async throws -> DeletionRequest
    func confirmDeletion(confirmationText: String) async throws -> DeletionRequest
}

final class AccountPrivacyAPI: AccountPrivacyServicing, @unchecked Sendable {
    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func currentAccount() async throws -> AccountState {
        let response: HTTPResponse<AccountStateWireResponse> = try await apiClient.send(
            HTTPRequest(endpoint: Endpoint(path: "/api/v1/me/account", method: .get))
        )
        return try response.body.accountState()
    }

    func requestDataExport() async throws -> DataExportRecord {
        let response: HTTPResponse<DataExportWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/me/data-exports", method: .post),
                idempotencyKey: UUID().uuidString
            )
        )
        return try response.body.record()
    }

    func dataExportStatus(exportId: UUID) async throws -> DataExportRecord {
        let response: HTTPResponse<DataExportWireResponse> = try await apiClient.send(
            HTTPRequest(endpoint: Endpoint(path: "/api/v1/me/data-exports/\(exportId.uuidString)", method: .get))
        )
        return try response.body.record()
    }

    func downloadDataExport(exportId: UUID) async throws -> DataExportDocument {
        let response: HTTPResponse<DataExportContentWireResponse> = try await apiClient.send(
            HTTPRequest(endpoint: Endpoint(path: "/api/v1/me/data-exports/\(exportId.uuidString)/content", method: .get))
        )
        return response.body.document()
    }

    func requestDeletion() async throws -> DeletionRequest {
        let response: HTTPResponse<DeletionRequestWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/me/deletion-request", method: .post),
                idempotencyKey: UUID().uuidString
            )
        )
        return try response.body.deletionRequest()
    }

    func deletionRequest() async throws -> DeletionRequest {
        let response: HTTPResponse<DeletionRequestWireResponse> = try await apiClient.send(
            HTTPRequest(endpoint: Endpoint(path: "/api/v1/me/deletion-request", method: .get))
        )
        return try response.body.deletionRequest()
    }

    func confirmDeletion(confirmationText: String) async throws -> DeletionRequest {
        let body = try encoder.encode(DeletionConfirmationWireRequest(confirmationText: confirmationText))
        let response: HTTPResponse<DeletionRequestWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/me/deletion-request/confirm", method: .post),
                body: body,
                idempotencyKey: UUID().uuidString
            )
        )
        return try response.body.deletionRequest()
    }
}

private struct AccountStateWireResponse: Decodable {
    let userId: UUID
    let status: String
    let createdAt: String
    let deletionRequestedAt: String?
    let deletedAt: String?

    func accountState() throws -> AccountState {
        guard let status = AccountStatus(rawValue: status),
              let createdAt = ISO8601WireDate.parse(createdAt) else {
            throw NetworkError.decoding("unsupported_account_state")
        }
        return AccountState(
            userId: userId,
            status: status,
            createdAt: createdAt,
            deletionRequestedAt: ISO8601WireDate.parse(deletionRequestedAt),
            deletedAt: ISO8601WireDate.parse(deletedAt)
        )
    }
}

private struct DataExportWireResponse: Decodable {
    let exportId: UUID
    let status: String
    let schemaVersion: String
    let requestedAt: String
    let completedAt: String?
    let downloadedAt: String?
    let expiresAt: String

    func record() throws -> DataExportRecord {
        guard let status = DataExportStatus(rawValue: status),
              let requestedAt = ISO8601WireDate.parse(requestedAt),
              let expiresAt = ISO8601WireDate.parse(expiresAt) else {
            throw NetworkError.decoding("unsupported_data_export")
        }
        return DataExportRecord(
            exportId: exportId,
            status: status,
            schemaVersion: schemaVersion,
            requestedAt: requestedAt,
            completedAt: ISO8601WireDate.parse(completedAt),
            downloadedAt: ISO8601WireDate.parse(downloadedAt),
            expiresAt: expiresAt
        )
    }
}

private struct DeletionRequestWireResponse: Decodable {
    let userId: UUID
    let status: String
    let deletionRequestedAt: String?
    let deletedAt: String?

    func deletionRequest() throws -> DeletionRequest {
        guard let status = AccountStatus(rawValue: status) else {
            throw NetworkError.decoding("unsupported_deletion_state")
        }
        return DeletionRequest(
            userId: userId,
            status: status,
            deletionRequestedAt: ISO8601WireDate.parse(deletionRequestedAt),
            deletedAt: ISO8601WireDate.parse(deletedAt)
        )
    }
}

private struct DeletionConfirmationWireRequest: Encodable {
    let confirmationText: String
}

private struct DataExportContentWireResponse: Decodable {
    let schemaVersion: String
    let generatedAt: String?
    let categories: [String]

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: DynamicCodingKey.self)
        let schemaKey = DynamicCodingKey(stringValue: "schemaVersion")!
        let generatedKey = DynamicCodingKey(stringValue: "generatedAt")!
        schemaVersion = try container.decode(String.self, forKey: schemaKey)
        generatedAt = try container.decodeIfPresent(String.self, forKey: generatedKey)
        categories = container.allKeys
            .map(\.stringValue)
            .filter { $0 != "schemaVersion" && $0 != "generatedAt" }
            .sorted()
    }

    func document() -> DataExportDocument {
        DataExportDocument(
            schemaVersion: schemaVersion,
            generatedAt: ISO8601WireDate.parse(generatedAt),
            categories: categories
        )
    }
}

private struct DynamicCodingKey: CodingKey {
    let stringValue: String
    let intValue: Int? = nil

    init?(stringValue: String) {
        self.stringValue = stringValue
    }

    init?(intValue: Int) {
        return nil
    }
}

private enum ISO8601WireDate {
    static func parse(_ value: String?) -> Date? {
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
