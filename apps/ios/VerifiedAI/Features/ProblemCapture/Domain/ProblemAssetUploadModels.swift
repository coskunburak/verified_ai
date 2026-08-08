import Foundation

enum ProblemAssetUploadPhase: Equatable, Sendable {
    case idle
    case reserving
    case uploading(progress: Double)
    case confirming
    case available(DurableProblemAssetReference)
    case recoverableFailure(ProblemAssetUploadFailure, AcceptedCapturedAsset)
}

enum ProblemAssetUploadFailure: Equatable, Sendable {
    case offline
    case reservationFailed(String?)
    case uploadFailed
    case completionFailed(String?)
    case cancelled
}

struct ProblemAssetUploadReservation: Equatable, Sendable {
    let uploadId: UUID
    let problemSessionId: UUID
    let problemAssetId: UUID
    let assetStatus: String
    let uploadURL: URL
    let expiresAt: Date
    let requiredHeaders: [String: String]
}

struct DurableProblemAssetReference: Equatable, Sendable {
    let uploadId: UUID
    let problemSessionId: UUID
    let problemAssetId: UUID
    let problemSessionStatus: String
    let assetStatus: String
    let availableAt: Date
}

struct ProblemAssetUploadRequest: Equatable, Sendable {
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
}
