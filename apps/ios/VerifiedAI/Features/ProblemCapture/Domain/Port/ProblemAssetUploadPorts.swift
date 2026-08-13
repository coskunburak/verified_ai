import Foundation

protocol ProblemAssetUploadServicing: Sendable {
    func reserveUpload(_ request: ProblemAssetUploadRequest, idempotencyKey: String) async throws -> ProblemAssetUploadReservation
    func completeUpload(uploadId: UUID, idempotencyKey: String) async throws -> DurableProblemAssetReference
    func preprocessAsset(problemAssetId: UUID) async throws -> ProblemAssetPreprocessingResult
    func getPreprocessing(problemAssetId: UUID) async throws -> ProblemAssetPreprocessingResult
    func requestRecognition(problemSessionId: UUID) async throws -> ProblemRecognitionResult
    func getRecognition(problemSessionId: UUID) async throws -> ProblemRecognitionResult
    func requestParse(problemSessionId: UUID) async throws -> ProblemParseResult
    func getParse(problemSessionId: UUID) async throws -> ProblemParseResult
    func canonicalize(problemSessionId: UUID) async throws -> CanonicalProblemResult
    func getCanonicalProblem(problemSessionId: UUID) async throws -> CanonicalProblemResult
    func requestClassification(problemSessionId: UUID) async throws -> ProblemClassificationResult
    func getClassification(problemSessionId: UUID) async throws -> ProblemClassificationResult
}

protocol PresignedObjectUploading: Sendable {
    func upload(
        fileURL: URL,
        to uploadURL: URL,
        requiredHeaders: [String: String],
        progress: @escaping @Sendable (Double) -> Void
    ) async throws
}
