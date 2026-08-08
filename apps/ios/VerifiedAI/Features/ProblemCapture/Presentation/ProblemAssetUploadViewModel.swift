import CryptoKit
import Foundation
import Observation

@MainActor
@Observable
final class ProblemAssetUploadViewModel {
    private let uploadAPI: ProblemAssetUploadServicing
    private let objectUploader: PresignedObjectUploading
    private let assetStore: CapturedAssetStoring
    private let networkMonitor: NetworkMonitoring
    private let logger: AppLogger

    private var activeRequestID: UUID?
    private(set) var state: ProblemAssetUploadPhase = .idle
    private(set) var message: String?

    init(
        uploadAPI: ProblemAssetUploadServicing,
        objectUploader: PresignedObjectUploading,
        assetStore: CapturedAssetStoring,
        networkMonitor: NetworkMonitoring,
        logger: AppLogger
    ) {
        self.uploadAPI = uploadAPI
        self.objectUploader = objectUploader
        self.assetStore = assetStore
        self.networkMonitor = networkMonitor
        self.logger = logger
    }

    func start(_ acceptedAsset: AcceptedCapturedAsset) async {
        let requestID = UUID()
        activeRequestID = requestID

        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--ui-testing-problem-capture-review") {
            let reference = DurableProblemAssetReference(
                uploadId: acceptedAsset.localIdentifier,
                problemSessionId: UUID(uuidString: "00000000-0000-0000-0000-000000000041")!,
                problemAssetId: UUID(uuidString: "00000000-0000-0000-0000-000000000042")!,
                problemSessionStatus: "ASSET_UPLOADED",
                assetStatus: "AVAILABLE",
                availableAt: Date()
            )
            await assetStore.delete(acceptedAsset.asset)
            state = .available(reference)
            message = "Problem asset uploaded successfully."
            return
        }
        #endif

        guard networkMonitor.isReachable else {
            logger.warning("problem_asset_upload.offline")
            state = .recoverableFailure(.offline, acceptedAsset)
            message = "Upload needs a network connection. Your local capture is still available."
            return
        }

        do {
            state = .reserving
            message = nil
            let uploadRequest = try makeUploadRequest(from: acceptedAsset.asset)
            let reservation = try await uploadAPI.reserveUpload(
                uploadRequest,
                idempotencyKey: reservationIdempotencyKey(for: acceptedAsset)
            )
            guard activeRequestID == requestID else { return }

            state = .uploading(progress: 0)
            try await objectUploader.upload(
                fileURL: acceptedAsset.asset.localURL,
                to: reservation.uploadURL,
                requiredHeaders: reservation.requiredHeaders
            ) { [weak self] progress in
                Task { @MainActor in
                    guard let self, self.activeRequestID == requestID else { return }
                    self.state = .uploading(progress: min(max(progress, 0), 1))
                }
            }
            guard activeRequestID == requestID else { return }

            state = .confirming
            let reference = try await uploadAPI.completeUpload(
                uploadId: reservation.uploadId,
                idempotencyKey: completionIdempotencyKey(for: acceptedAsset)
            )
            guard activeRequestID == requestID else { return }

            await assetStore.delete(acceptedAsset.asset)
            logger.info("problem_asset_upload.available")
            state = .available(reference)
            message = "Problem asset uploaded successfully."
        } catch let error as NetworkError {
            applyFailure(error, acceptedAsset: acceptedAsset)
        } catch {
            applyFailure(nil, acceptedAsset: acceptedAsset)
        }
    }

    func retry() async {
        guard case .recoverableFailure(_, let acceptedAsset) = state else {
            return
        }
        await start(acceptedAsset)
    }

    func cancel() {
        activeRequestID = nil
        state = .idle
        message = nil
    }

    func reset() {
        activeRequestID = nil
        state = .idle
        message = nil
    }

    private func applyFailure(_ error: NetworkError?, acceptedAsset: AcceptedCapturedAsset) {
        let failure: ProblemAssetUploadFailure
        switch error {
        case .server(let problem) where problem.code == "UPLOAD_RESERVATION_EXPIRED":
            failure = .reservationFailed(problem.code)
            message = "Upload reservation expired. Retry will create a fresh reservation."
        case .server(let problem) where problem.code == "UPLOAD_TOO_LARGE":
            failure = .reservationFailed(problem.code)
            message = "This capture is too large to upload."
        case .server(let problem) where problem.code == "UPLOAD_CHECKSUM_MISMATCH":
            failure = .completionFailed(problem.code)
            message = "Upload integrity check failed. Retry will upload the local file again."
        case .server(let problem):
            failure = .completionFailed(problem.code)
            message = "Upload could not be confirmed. Retry is available."
        case .transport, .httpStatus:
            failure = .uploadFailed
            message = "Upload failed. Your local capture is still available."
        case .invalidURL, .invalidResponse, .decoding, nil:
            failure = .uploadFailed
            message = "Upload could not continue. Your local capture is still available."
        }
        logger.warning("problem_asset_upload.failed")
        state = .recoverableFailure(failure, acceptedAsset)
    }

    private func makeUploadRequest(from asset: CapturedAsset) throws -> ProblemAssetUploadRequest {
        let data = try Data(contentsOf: asset.localURL)
        let checksum = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        let isPDF = asset.kind == .pdf
        return ProblemAssetUploadRequest(
            source: sourceWireValue(asset.source),
            assetKind: isPDF ? "pdf" : "image",
            contentType: isPDF ? "application/pdf" : "image/jpeg",
            sizeBytes: Int64(data.count),
            checksumSha256: checksum,
            imageWidth: isPDF ? nil : asset.pixelWidth,
            imageHeight: isPDF ? nil : asset.pixelHeight,
            pageCount: nil,
            cropX: asset.cropSelection.x,
            cropY: asset.cropSelection.y,
            cropWidth: asset.cropSelection.width,
            cropHeight: asset.cropSelection.height
        )
    }

    private func sourceWireValue(_ source: CaptureSource) -> String {
        switch source {
        case .camera:
            "camera"
        case .photoLibrary:
            "photoLibrary"
        case .file:
            "file"
        case .pdf:
            "pdf"
        }
    }

    private func reservationIdempotencyKey(for acceptedAsset: AcceptedCapturedAsset) -> String {
        "problem-asset-reserve-\(acceptedAsset.localIdentifier.uuidString)"
    }

    private func completionIdempotencyKey(for acceptedAsset: AcceptedCapturedAsset) -> String {
        "problem-asset-complete-\(acceptedAsset.localIdentifier.uuidString)"
    }
}
