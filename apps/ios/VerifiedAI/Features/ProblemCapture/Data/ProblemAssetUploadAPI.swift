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

    func preprocessAsset(problemAssetId: UUID) async throws -> ProblemAssetPreprocessingResult {
        let response: HTTPResponse<ProblemAssetPreprocessingWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-assets/\(problemAssetId.uuidString)/preprocess", method: .post),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.result()
    }

    func getPreprocessing(problemAssetId: UUID) async throws -> ProblemAssetPreprocessingResult {
        let response: HTTPResponse<ProblemAssetPreprocessingWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-assets/\(problemAssetId.uuidString)/preprocessing", method: .get),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.result()
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

private struct ProblemAssetPreprocessingWireResponse: Decodable {
    let sourceAssetId: UUID
    let problemSessionId: UUID
    let sourceAssetStatus: String
    let preprocessingStatus: String
    let qualityOutcome: String?
    let failureCode: String?
    let preferredRecognitionDerivativeId: UUID?
    let derivatives: [ProblemAssetDerivativeWireResponse]
    let qualitySignals: [ProblemAssetQualitySignalWireResponse]
    let userRecoveryActions: [String]
    let completedAt: String?

    func result() throws -> ProblemAssetPreprocessingResult {
        let completedAtDate = ISO8601WireDate.parse(completedAt)
        if completedAt != nil, completedAtDate == nil {
            throw NetworkError.decoding("unsupported_preprocessing_completion_date")
        }
        return ProblemAssetPreprocessingResult(
            sourceAssetId: sourceAssetId,
            problemSessionId: problemSessionId,
            sourceAssetStatus: sourceAssetStatus,
            preprocessingStatus: preprocessingStatus,
            qualityOutcome: qualityOutcome,
            failureCode: failureCode,
            preferredRecognitionDerivativeId: preferredRecognitionDerivativeId,
            derivatives: derivatives.map(\.derivative),
            qualitySignals: qualitySignals.map(\.signal),
            userRecoveryActions: userRecoveryActions,
            completedAt: completedAtDate
        )
    }
}

private struct ProblemAssetDerivativeWireResponse: Decodable {
    let derivativeId: UUID
    let derivativeKind: String
    let status: String
    let selectedForRecognition: Bool
    let contentType: String?
    let sizeBytes: Int64?
    let checksumSha256: String?
    let width: Int?
    let height: Int?
    let processorName: String
    let processorVersion: String
    let configurationVersion: String
    let orientationNormalized: Bool
    let perspectiveApplied: Bool
    let contrastNormalized: Bool
    let resized: Bool
    let qualityOutcome: String
    let failureCode: String?

    var derivative: ProblemAssetDerivative {
        ProblemAssetDerivative(
            derivativeId: derivativeId,
            derivativeKind: derivativeKind,
            status: status,
            selectedForRecognition: selectedForRecognition,
            contentType: contentType,
            sizeBytes: sizeBytes,
            checksumSha256: checksumSha256,
            width: width,
            height: height,
            processorName: processorName,
            processorVersion: processorVersion,
            configurationVersion: configurationVersion,
            orientationNormalized: orientationNormalized,
            perspectiveApplied: perspectiveApplied,
            contrastNormalized: contrastNormalized,
            resized: resized,
            qualityOutcome: qualityOutcome,
            failureCode: failureCode
        )
    }
}

private struct ProblemAssetQualitySignalWireResponse: Decodable {
    let signalType: String
    let severity: String
    let score: Double
    let threshold: Double
    let policyVersion: String
    let messageCode: String

    var signal: ProblemAssetQualitySignal {
        ProblemAssetQualitySignal(
            signalType: signalType,
            severity: severity,
            score: score,
            threshold: threshold,
            policyVersion: policyVersion,
            messageCode: messageCode
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
