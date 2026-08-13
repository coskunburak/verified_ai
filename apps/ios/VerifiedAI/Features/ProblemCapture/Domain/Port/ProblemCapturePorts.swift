import Foundation

protocol ProblemCameraClient: AnyObject, Sendable {
    var permissionStatus: CapturePermissionStatus { get }

    func requestPermission() async -> CapturePermissionStatus
    func start() async throws
    func stop()
    func capturePhotoData() async throws -> Data
}

protocol CapturedAssetStoring: Sendable {
    func storeImageData(_ data: Data, source: CaptureSource, declaredUTType: String?) async throws -> CapturedAsset
    func storePDF(at url: URL, source: CaptureSource) async throws -> CapturedAsset
    func delete(_ asset: CapturedAsset) async
    func cleanUpExpiredAssets() async
}

protocol CaptureQualityAnalyzing: Sendable {
    func analyze(asset: CapturedAsset) async throws -> CaptureQualityAssessment
}
