import Foundation

final class ProblemAssetUploadAPI: ProblemAssetUploadServicing, @unchecked Sendable {
    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func reserveUpload(_ request: ProblemAssetUploadRequest, idempotencyKey: String) async throws -> ProblemAssetUploadReservation {
        let body = try encoder.encode(ProblemAssetUploadWireRequest(from: request))
        let response: HTTPResponse<ProblemAssetUploadReservationWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/uploads/presign", method: .post),
                body: body,
                idempotencyKey: idempotencyKey,
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.reservation()
    }

    func completeUpload(uploadId: UUID, idempotencyKey: String) async throws -> DurableProblemAssetReference {
        let response: HTTPResponse<ProblemAssetUploadCompletionWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/uploads/\(uploadId.uuidString)/complete", method: .post),
                idempotencyKey: idempotencyKey,
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.reference()
    }
}

private struct ProblemAssetUploadWireRequest: Encodable {
    let source: String
    let assetKind: String
    let contentType: String
    let sizeBytes: Int64
    let checksumSha256: String
    let imageWidth: Int?
    let imageHeight: Int?
    let pageCount: Int?
    let cropX: Double
    let cropY: Double
    let cropWidth: Double
    let cropHeight: Double

    init(from request: ProblemAssetUploadRequest) {
        source = request.source
        assetKind = request.assetKind
        contentType = request.contentType
        sizeBytes = request.sizeBytes
        checksumSha256 = request.checksumSha256
        imageWidth = request.imageWidth
        imageHeight = request.imageHeight
        pageCount = request.pageCount
        cropX = request.cropX
        cropY = request.cropY
        cropWidth = request.cropWidth
        cropHeight = request.cropHeight
    }
}

private struct ProblemAssetUploadReservationWireResponse: Decodable {
    let uploadId: UUID
    let problemSessionId: UUID
    let problemAssetId: UUID
    let assetStatus: String
    let uploadUrl: String
    let expiresAt: String
    let requiredHeaders: [String: String]

    func reservation() throws -> ProblemAssetUploadReservation {
        guard let uploadURL = URL(string: uploadUrl),
              let expiresAtDate = ISO8601WireDate.parse(expiresAt) else {
            throw NetworkError.decoding("unsupported_upload_reservation")
        }
        return ProblemAssetUploadReservation(
            uploadId: uploadId,
            problemSessionId: problemSessionId,
            problemAssetId: problemAssetId,
            assetStatus: assetStatus,
            uploadURL: uploadURL,
            expiresAt: expiresAtDate,
            requiredHeaders: requiredHeaders
        )
    }
}

private struct ProblemAssetUploadCompletionWireResponse: Decodable {
    let uploadId: UUID
    let problemSessionId: UUID
    let problemAssetId: UUID
    let problemSessionStatus: String
    let assetStatus: String
    let availableAt: String

    func reference() throws -> DurableProblemAssetReference {
        guard let availableAtDate = ISO8601WireDate.parse(availableAt) else {
            throw NetworkError.decoding("unsupported_upload_completion")
        }
        return DurableProblemAssetReference(
            uploadId: uploadId,
            problemSessionId: problemSessionId,
            problemAssetId: problemAssetId,
            problemSessionStatus: problemSessionStatus,
            assetStatus: assetStatus,
            availableAt: availableAtDate
        )
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
