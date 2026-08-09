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
            let preprocessing = ProblemAssetPreprocessingResult.passFixture(
                sourceAssetId: reference.problemAssetId,
                problemSessionId: reference.problemSessionId
            )
            await assetStore.delete(acceptedAsset.asset)
            state = .available(PreprocessedProblemAssetReference(durableAsset: reference, preprocessing: preprocessing))
            message = "Problem asset is ready for recognition."
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

            state = .preprocessing(reference)
            let preprocessing = try await uploadAPI.preprocessAsset(problemAssetId: reference.problemAssetId)
            guard activeRequestID == requestID else { return }

            let preprocessedReference = PreprocessedProblemAssetReference(
                durableAsset: reference,
                preprocessing: preprocessing
            )
            await applyPreprocessingResult(preprocessedReference, acceptedAsset: acceptedAsset)
        } catch let error as NetworkError {
            applyFailure(error, acceptedAsset: acceptedAsset)
        } catch {
            applyFailure(nil, acceptedAsset: acceptedAsset)
        }
    }

    func retry() async {
        let acceptedAsset: AcceptedCapturedAsset
        switch state {
        case .recoverableFailure(_, let asset), .preprocessingFailed(_, _, let asset):
            acceptedAsset = asset
        default:
            return
        }
        await start(acceptedAsset)
    }

    func continueWithWarning() async {
        guard case .preprocessingWarning(let reference, let acceptedAsset) = state else {
            return
        }
        await assetStore.delete(acceptedAsset.asset)
        logger.info("problem_asset_preprocessing.warning_continued")
        state = .available(reference)
        message = "Problem asset is ready for recognition with capture warnings."
    }

    func startRecognition(_ reference: PreprocessedProblemAssetReference) async {
        let requestID = UUID()
        activeRequestID = requestID

        guard networkMonitor.isReachable else {
            logger.warning("problem_recognition.offline")
            state = .recognitionFailed(reference, nil)
            message = "Reading the problem needs a network connection."
            return
        }

        do {
            state = .startingRecognition(reference)
            message = "Reading the problem."
            let requested = try await uploadAPI.requestRecognition(problemSessionId: reference.durableAsset.problemSessionId)
            guard activeRequestID == requestID else { return }
            await observeRecognition(requested, reference: reference, requestID: requestID)
        } catch let error as NetworkError {
            logger.warning("problem_recognition.failed")
            state = .recognitionFailed(reference, nil)
            if case .server(let problem) = error {
                message = "Reading failed: \(problem.code)"
            } else {
                message = "Reading could not continue."
            }
        } catch {
            logger.warning("problem_recognition.failed")
            state = .recognitionFailed(reference, nil)
            message = "Reading could not continue."
        }
    }

    func retryRecognition() async {
        guard case .recognitionFailed(let reference, _) = state else {
            return
        }
        await startRecognition(reference)
    }

    func startParse(_ reference: RecognizedProblemReference) async {
        let requestID = UUID()
        activeRequestID = requestID

        guard networkMonitor.isReachable else {
            logger.warning("problem_parse.offline")
            state = .parseFailed(reference, nil)
            message = "Understanding the problem needs a network connection."
            return
        }

        do {
            state = .startingParse(reference)
            message = "Understanding the problem."
            let requested = try await uploadAPI.requestParse(problemSessionId: reference.preprocessedAsset.durableAsset.problemSessionId)
            guard activeRequestID == requestID else { return }
            await observeParse(requested, reference: reference, requestID: requestID)
        } catch let error as NetworkError {
            logger.warning("problem_parse.failed")
            state = .parseFailed(reference, nil)
            if case .server(let problem) = error, problem.code == "PROBLEM_UNSUPPORTED" {
                message = "This problem type is not supported yet."
            } else if case .server(let problem) = error {
                message = "Understanding failed: \(problem.code)"
            } else {
                message = "Understanding could not continue."
            }
        } catch {
            logger.warning("problem_parse.failed")
            state = .parseFailed(reference, nil)
            message = "Understanding could not continue."
        }
    }

    func retryParse() async {
        guard case .parseFailed(let reference, _) = state else {
            return
        }
        await startParse(reference)
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
        let wasPreprocessing: Bool
        if case .preprocessing = state {
            wasPreprocessing = true
        } else {
            wasPreprocessing = false
        }
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
            failure = wasPreprocessing ? .preprocessingFailed(problem.code) : .completionFailed(problem.code)
            message = wasPreprocessing ? "Preprocessing could not finish. Your local capture is still available." : "Upload could not be confirmed. Retry is available."
        case .transport, .httpStatus:
            failure = wasPreprocessing ? .preprocessingFailed(nil) : .uploadFailed
            message = wasPreprocessing ? "Preprocessing needs a network connection. Your local capture is still available." : "Upload failed. Your local capture is still available."
        case .invalidURL, .invalidResponse, .decoding, nil:
            failure = wasPreprocessing ? .preprocessingFailed(nil) : .uploadFailed
            message = wasPreprocessing ? "Preprocessing could not continue. Your local capture is still available." : "Upload could not continue. Your local capture is still available."
        }
        logger.warning("problem_asset_upload.failed")
        state = .recoverableFailure(failure, acceptedAsset)
    }

    private func applyPreprocessingResult(
        _ reference: PreprocessedProblemAssetReference,
        acceptedAsset: AcceptedCapturedAsset
    ) async {
        switch reference.preprocessing.qualityOutcome {
        case "PASS":
            await assetStore.delete(acceptedAsset.asset)
            logger.info("problem_asset_preprocessing.pass")
            state = .available(reference)
            message = "Problem asset is ready for recognition."
        case "WARNING":
            logger.warning("problem_asset_preprocessing.warning")
            state = .preprocessingWarning(reference, acceptedAsset)
            message = "Capture quality warnings need review."
        default:
            logger.warning("problem_asset_preprocessing.failed")
            state = .preprocessingFailed(reference.durableAsset, reference.preprocessing, acceptedAsset)
            message = "Preprocessing could not prepare this capture."
        }
    }

    private func observeRecognition(
        _ initial: ProblemRecognitionResult,
        reference: PreprocessedProblemAssetReference,
        requestID: UUID
    ) async {
        var current = initial
        for attempt in 0...5 {
            guard activeRequestID == requestID else { return }
            if applyRecognitionResult(current, reference: reference) {
                return
            }
            state = .recognizing(reference, current)
            message = "Reading the problem."
            let delayNanos = UInt64(min(2.0, 0.4 * Double(attempt + 1)) * 1_000_000_000)
            try? await Task.sleep(nanoseconds: delayNanos)
            guard activeRequestID == requestID else { return }
            do {
                current = try await uploadAPI.getRecognition(problemSessionId: reference.durableAsset.problemSessionId)
            } catch {
                logger.warning("problem_recognition.poll_failed")
                state = .recognitionFailed(reference, current)
                message = "Reading status could not be refreshed."
                return
            }
        }
        state = .recognizing(reference, current)
        message = "Reading is still in progress."
    }

    @discardableResult
    private func applyRecognitionResult(
        _ recognition: ProblemRecognitionResult,
        reference: PreprocessedProblemAssetReference
    ) -> Bool {
        if recognition.isTerminalSuccess {
            let recognized = RecognizedProblemReference(preprocessedAsset: reference, recognition: recognition)
            if recognition.reviewRequired {
                logger.warning("problem_recognition.review_required")
                state = .recognitionReviewRequired(recognized)
                message = "Reading finished with uncertainty."
            } else {
                logger.info("problem_recognition.succeeded")
                state = .recognized(recognized)
                message = "Reading finished."
            }
            return true
        }
        if recognition.isTerminalFailure {
            logger.warning("problem_recognition.terminal_failure")
            state = .recognitionFailed(reference, recognition)
            message = "Reading could not finish."
            return true
        }
        if recognition.isRetryableFailure {
            logger.warning("problem_recognition.retryable_failure")
            state = .recognitionFailed(reference, recognition)
            message = "Reading can be retried."
            return true
        }
        return false
    }

    private func observeParse(
        _ initial: ProblemParseResult,
        reference: RecognizedProblemReference,
        requestID: UUID
    ) async {
        var current = initial
        for attempt in 0...5 {
            guard activeRequestID == requestID else { return }
            if applyParseResult(current, reference: reference) {
                return
            }
            state = .parsing(reference, current)
            message = "Understanding the problem."
            let delayNanos = UInt64(min(2.0, 0.4 * Double(attempt + 1)) * 1_000_000_000)
            try? await Task.sleep(nanoseconds: delayNanos)
            guard activeRequestID == requestID else { return }
            do {
                current = try await uploadAPI.getParse(problemSessionId: reference.preprocessedAsset.durableAsset.problemSessionId)
            } catch {
                logger.warning("problem_parse.poll_failed")
                state = .parseFailed(reference, current)
                message = "Understanding status could not be refreshed."
                return
            }
        }
        state = .parsing(reference, current)
        message = "Understanding is still in progress."
    }

    @discardableResult
    private func applyParseResult(
        _ parse: ProblemParseResult,
        reference: RecognizedProblemReference
    ) -> Bool {
        if parse.isUnsupported {
            logger.warning("problem_parse.unsupported")
            state = .parseUnsupported(reference, parse)
            message = "This problem type is not supported yet."
            return true
        }
        if parse.isTerminalSuccess {
            let parsed = ParsedProblemReference(recognizedProblem: reference, parse: parse)
            if parse.reviewRequired || parse.supportStatus == "REVIEW_REQUIRED" {
                logger.warning("problem_parse.review_required")
                state = .parseReviewRequired(parsed)
                message = "Understanding finished with uncertainty."
            } else {
                logger.info("problem_parse.succeeded")
                state = .parsed(parsed)
                message = "Understanding finished."
            }
            return true
        }
        if parse.isTerminalFailure {
            logger.warning("problem_parse.terminal_failure")
            state = .parseFailed(reference, parse)
            message = "Understanding could not finish."
            return true
        }
        if parse.isRetryableFailure {
            logger.warning("problem_parse.retryable_failure")
            state = .parseFailed(reference, parse)
            message = "Understanding can be retried."
            return true
        }
        return false
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

#if DEBUG
private extension ProblemAssetPreprocessingResult {
    static func passFixture(sourceAssetId: UUID, problemSessionId: UUID) -> ProblemAssetPreprocessingResult {
        ProblemAssetPreprocessingResult(
            sourceAssetId: sourceAssetId,
            problemSessionId: problemSessionId,
            sourceAssetStatus: "AVAILABLE",
            preprocessingStatus: "READY",
            qualityOutcome: "PASS",
            failureCode: nil,
            preferredRecognitionDerivativeId: UUID(uuidString: "00000000-0000-0000-0000-000000000043"),
            derivatives: [],
            qualitySignals: [],
            userRecoveryActions: ["CONTINUE"],
            completedAt: Date()
        )
    }
}
#endif
