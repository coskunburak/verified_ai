import Foundation
import Observation
import SwiftUI
import UniformTypeIdentifiers

@MainActor
@Observable
final class ProblemCaptureViewModel {
    private let cameraClient: ProblemCameraClient
    private let assetStore: CapturedAssetStoring
    private let qualityAnalyzer: CaptureQualityAnalyzing
    private let logger: AppLogger

    private var activeRequestID: UUID?
    private var cameraSuspendedByScene = false

    private(set) var state: ProblemCaptureState = .idle
    private(set) var message: String?

    init(
        cameraClient: ProblemCameraClient,
        assetStore: CapturedAssetStoring,
        qualityAnalyzer: CaptureQualityAnalyzing,
        logger: AppLogger
    ) {
        self.cameraClient = cameraClient
        self.assetStore = assetStore
        self.qualityAnalyzer = qualityAnalyzer
        self.logger = logger

        Task {
            await assetStore.cleanUpExpiredAssets()
        }
    }

    var currentAsset: CapturedAsset? {
        state.currentAsset
    }

    var isBusy: Bool {
        switch state {
        case .requestingCameraPermission, .capturing, .importing, .processingLocalAsset:
            true
        default:
            false
        }
    }

    func open() {
        logger.info("problem_capture.opened")
        message = nil
        state = .selectingSource
    }

    func chooseCamera() async {
        logger.info("problem_capture.source_selected camera")
        message = nil

        switch cameraClient.permissionStatus {
        case .authorized:
            await startCamera()
        case .notDetermined:
            state = .requestingCameraPermission
            let status = await cameraClient.requestPermission()
            if status == .authorized {
                await startCamera()
            } else {
                applyPermissionFailure(status)
            }
        case .denied, .restricted, .unavailable:
            applyPermissionFailure(cameraClient.permissionStatus)
        }
    }

    func beginPhotoImport() {
        logger.info("problem_capture.source_selected photo_library")
        cameraClient.stop()
        message = nil
        state = .importing(.photoLibrary)
    }

    func beginDocumentImport() {
        logger.info("problem_capture.source_selected file")
        cameraClient.stop()
        message = nil
        state = .importing(.file)
    }

    func capturePhoto() async {
        guard state == .cameraReady else {
            return
        }

        state = .capturing
        let requestID = UUID()
        activeRequestID = requestID

        do {
            let data = try await cameraClient.capturePhotoData()
            cameraClient.stop()
            await processImageData(
                data,
                source: .camera,
                declaredUTType: UTType.jpeg.identifier,
                requestID: requestID,
                successEvent: "problem_capture.capture_succeeded",
                failureEvent: "problem_capture.capture_failed"
            )
        } catch {
            guard activeRequestID == requestID else {
                return
            }
            logger.warning("problem_capture.capture_failed")
            state = .recoverableFailure(.captureFailed, previousAsset: nil)
            message = failureMessage(for: .captureFailed)
        }
    }

    func importPhotoData(_ data: Data?, declaredUTType: String?) async {
        guard let data else {
            state = .selectingSource
            return
        }
        let requestID = UUID()
        activeRequestID = requestID
        await processImageData(
            data,
            source: .photoLibrary,
            declaredUTType: declaredUTType,
            requestID: requestID,
            successEvent: "problem_capture.import_succeeded photo_library",
            failureEvent: "problem_capture.import_failed photo_library"
        )
    }

    func importDocument(at url: URL?, contentType: UTType?) async {
        guard let url else {
            state = .selectingSource
            return
        }

        let requestID = UUID()
        activeRequestID = requestID
        let previousAsset = state.currentAsset
        state = .processingLocalAsset
        message = nil

        do {
            let asset: CapturedAsset
            if contentType?.conforms(to: .pdf) == true || url.pathExtension.lowercased() == "pdf" {
                asset = try await assetStore.storePDF(at: url, source: .pdf)
            } else {
                let data = try Data(contentsOf: url)
                asset = try await assetStore.storeImageData(data, source: .file, declaredUTType: contentType?.identifier)
            }
            await finishProcessing(
                asset,
                previousAsset: previousAsset,
                requestID: requestID,
                successEvent: "problem_capture.import_succeeded file",
                failureEvent: "problem_capture.import_failed file"
            )
        } catch {
            await applyProcessingFailure(
                error,
                previousAsset: previousAsset,
                requestID: requestID,
                eventName: "problem_capture.import_failed file"
            )
        }
    }

    func editCrop() {
        guard let asset = state.currentAsset else {
            return
        }
        state = .editingCrop(asset)
    }

    func updateCrop(_ crop: CropSelection) {
        guard case .editingCrop(var asset) = state else {
            return
        }
        let clamped = crop.clamped()
        asset.cropSelection = clamped
        asset.qualityAssessment = asset.qualityAssessment.replacingFramingIssue(for: clamped)
        state = .editingCrop(asset)
    }

    func finishCropEditing() {
        guard case .editingCrop(let asset) = state else {
            return
        }
        logger.info("problem_capture.crop_edited")
        state = .reviewing(asset)
    }

    func useFullImageCrop() {
        updateCrop(.fullImage)
    }

    func retake() async {
        logger.info("problem_capture.retake")
        let asset = state.currentAsset
        activeRequestID = nil
        if let asset {
            await assetStore.delete(asset)
        }
        await chooseCamera()
    }

    func replaceAsset() async {
        let asset = state.currentAsset
        activeRequestID = nil
        cameraClient.stop()
        if let asset {
            await assetStore.delete(asset)
        }
        state = .selectingSource
        message = nil
    }

    func accept() {
        guard let asset = state.currentAsset else {
            return
        }
        activeRequestID = nil
        cameraClient.stop()
        logger.info("problem_capture.accepted")
        state = .readyForHandoff(AcceptedCapturedAsset(asset: asset))
        message = "Captured asset is ready for the next step."
    }

    func cancel() async {
        let asset = state.currentAsset
        activeRequestID = nil
        cameraClient.stop()
        if let asset {
            await assetStore.delete(asset)
        }
        state = .idle
        message = nil
    }

    #if DEBUG
    func setStateForTesting(_ state: ProblemCaptureState) {
        self.state = state
    }
    #endif

    func handleScenePhase(_ scenePhase: ScenePhase) {
        switch scenePhase {
        case .active:
            if cameraSuspendedByScene, state == .cameraReady {
                cameraSuspendedByScene = false
                Task { await startCamera() }
            }
        case .inactive, .background:
            if state == .cameraReady {
                cameraClient.stop()
                cameraSuspendedByScene = true
            } else if state == .capturing {
                activeRequestID = nil
                cameraClient.stop()
                cameraSuspendedByScene = false
                state = .recoverableFailure(.captureFailed, previousAsset: nil)
                message = failureMessage(for: .captureFailed)
            }
        @unknown default:
            cameraClient.stop()
        }
    }

    private func startCamera() async {
        state = .cameraReady
        do {
            try await cameraClient.start()
            message = nil
        } catch {
            logger.warning("problem_capture.camera_unavailable")
            state = .recoverableFailure(.cameraUnavailable, previousAsset: nil)
            message = failureMessage(for: .cameraUnavailable)
        }
    }

    private func processImageData(
        _ data: Data,
        source: CaptureSource,
        declaredUTType: String?,
        requestID: UUID,
        successEvent: String,
        failureEvent: String
    ) async {
        let previousAsset = state.currentAsset
        state = .processingLocalAsset
        message = nil

        do {
            let asset = try await assetStore.storeImageData(data, source: source, declaredUTType: declaredUTType)
            await finishProcessing(
                asset,
                previousAsset: previousAsset,
                requestID: requestID,
                successEvent: successEvent,
                failureEvent: failureEvent
            )
        } catch {
            await applyProcessingFailure(
                error,
                previousAsset: previousAsset,
                requestID: requestID,
                eventName: failureEvent
            )
        }
    }

    private func finishProcessing(
        _ asset: CapturedAsset,
        previousAsset: CapturedAsset?,
        requestID: UUID,
        successEvent: String,
        failureEvent: String
    ) async {
        do {
            var analyzedAsset = asset
            analyzedAsset.qualityAssessment = try await qualityAnalyzer.analyze(asset: asset)

            guard activeRequestID == requestID else {
                await assetStore.delete(asset)
                return
            }
            if let previousAsset, previousAsset.id != analyzedAsset.id {
                await assetStore.delete(previousAsset)
            }

            logger.info(successEvent)
            if !analyzedAsset.qualityAssessment.issues.isEmpty {
                logger.info("problem_capture.quality_warning")
            }
            state = .reviewing(analyzedAsset)
            message = nil
        } catch {
            await applyProcessingFailure(
                error,
                previousAsset: previousAsset,
                requestID: requestID,
                eventName: failureEvent
            )
        }
    }

    private func applyProcessingFailure(
        _ error: Error,
        previousAsset: CapturedAsset?,
        requestID: UUID,
        eventName: String
    ) async {
        guard activeRequestID == requestID else {
            return
        }

        let failure = (error as? CaptureFailure) ?? .importFailed
        logger.warning(eventName)
        state = .recoverableFailure(failure, previousAsset: previousAsset)
        message = failureMessage(for: failure)
    }

    private func applyPermissionFailure(_ status: CapturePermissionStatus) {
        let failure: CaptureFailure
        switch status {
        case .restricted:
            failure = .permissionRestricted
        case .denied:
            failure = .permissionDenied
        default:
            failure = .cameraUnavailable
        }
        logger.warning("problem_capture.permission_denied")
        state = .recoverableFailure(failure, previousAsset: nil)
        message = failureMessage(for: failure)
    }

    private func failureMessage(for failure: CaptureFailure) -> String {
        switch failure {
        case .cameraUnavailable:
            "Camera is not available on this device. Choose a photo or PDF instead."
        case .permissionDenied:
            "Camera access is off. You can enable it in Settings or choose a photo instead."
        case .permissionRestricted:
            "Camera access is restricted on this device. Choose a photo or PDF instead."
        case .captureFailed:
            "Capture failed. Try again or choose a photo."
        case .importFailed:
            "Import failed. Choose another file or try again."
        case .unsupportedAsset:
            "This file type is not supported. Use JPEG, PNG, HEIC, or PDF."
        case .corruptAsset:
            "This file could not be read. Choose another image or PDF."
        case .assetTooLarge:
            "This file is too large for local capture review."
        case .dimensionTooLarge:
            "This image is too large to process safely on this device."
        case .pdfPageUnavailable:
            "This PDF page could not be previewed. Choose another file."
        case .qualityAnalysisFailed:
            "The image could not be checked for capture quality. Choose another file or try again."
        case .tempStorageFailed:
            "The capture could not be stored locally. Try again."
        }
    }
}
