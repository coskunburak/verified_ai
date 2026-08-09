import Foundation

protocol ProblemAssetUploadServicing: Sendable {
    func reserveUpload(_ request: ProblemAssetUploadRequest, idempotencyKey: String) async throws -> ProblemAssetUploadReservation
    func completeUpload(uploadId: UUID, idempotencyKey: String) async throws -> DurableProblemAssetReference
    func preprocessAsset(problemAssetId: UUID) async throws -> ProblemAssetPreprocessingResult
    func getPreprocessing(problemAssetId: UUID) async throws -> ProblemAssetPreprocessingResult
}

protocol PresignedObjectUploading: Sendable {
    func upload(
        fileURL: URL,
        to uploadURL: URL,
        requiredHeaders: [String: String],
        progress: @escaping @Sendable (Double) -> Void
    ) async throws
}
