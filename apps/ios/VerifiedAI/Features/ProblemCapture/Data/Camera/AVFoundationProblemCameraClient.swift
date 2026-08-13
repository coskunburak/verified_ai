@preconcurrency import AVFoundation
import Foundation

final class AVFoundationProblemCameraClient: NSObject, ProblemCameraClient, @unchecked Sendable {
    let session = AVCaptureSession()

    private let sessionQueue = DispatchQueue(label: "com.verifiedai.problem-capture.camera-session")
    private let logger: AppLogger
    private var configured = false
    private var photoOutput: AVCapturePhotoOutput?
    private var pendingPhotoContinuation: CheckedContinuation<Data, Error>?
    private var notificationTokens: [NSObjectProtocol] = []

    init(logger: AppLogger) {
        self.logger = logger
        super.init()
    }

    deinit {
        for token in notificationTokens {
            NotificationCenter.default.removeObserver(token)
        }
    }

    var permissionStatus: CapturePermissionStatus {
        mapAuthorizationStatus(AVCaptureDevice.authorizationStatus(for: .video))
    }

    func requestPermission() async -> CapturePermissionStatus {
        let granted = await AVCaptureDevice.requestAccess(for: .video)
        return granted ? .authorized : permissionStatus
    }

    func start() async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            sessionQueue.async { [weak self] in
                guard let self else {
                    continuation.resume(throwing: CaptureFailure.cameraUnavailable)
                    return
                }

                do {
                    if !self.configured {
                        try self.configureSession()
                    }
                    if !self.session.isRunning {
                        self.session.startRunning()
                    }
                    self.logger.info("problem_capture.camera_ready")
                    continuation.resume()
                } catch {
                    self.logger.warning("problem_capture.camera_start_failed")
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    func stop() {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            if self.session.isRunning {
                self.session.stopRunning()
            }
        }
    }

    func capturePhotoData() async throws -> Data {
        try await withCheckedThrowingContinuation { continuation in
            sessionQueue.async { [weak self] in
                guard let self, let photoOutput = self.photoOutput, self.session.isRunning else {
                    continuation.resume(throwing: CaptureFailure.captureFailed)
                    return
                }
                guard self.pendingPhotoContinuation == nil else {
                    continuation.resume(throwing: CaptureFailure.captureFailed)
                    return
                }

                let settings = AVCapturePhotoSettings()
                if photoOutput.supportedFlashModes.contains(.off) {
                    settings.flashMode = .off
                }
                self.pendingPhotoContinuation = continuation
                photoOutput.capturePhoto(with: settings, delegate: self)
            }
        }
    }

    private func configureSession() throws {
        session.beginConfiguration()
        defer {
            session.commitConfiguration()
        }

        session.sessionPreset = .photo
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            throw CaptureFailure.cameraUnavailable
        }
        try configureFocusAndExposure(for: device)

        let input = try AVCaptureDeviceInput(device: device)
        guard session.canAddInput(input) else {
            throw CaptureFailure.cameraUnavailable
        }
        session.addInput(input)

        let output = AVCapturePhotoOutput()
        guard session.canAddOutput(output) else {
            throw CaptureFailure.cameraUnavailable
        }
        session.addOutput(output)
        photoOutput = output
        configured = true
        observeSessionNotifications()
    }

    private func configureFocusAndExposure(for device: AVCaptureDevice) throws {
        try device.lockForConfiguration()
        defer {
            device.unlockForConfiguration()
        }

        if device.isFocusModeSupported(.continuousAutoFocus) {
            device.focusMode = .continuousAutoFocus
        }
        if device.isExposureModeSupported(.continuousAutoExposure) {
            device.exposureMode = .continuousAutoExposure
        }
        if device.isWhiteBalanceModeSupported(.continuousAutoWhiteBalance) {
            device.whiteBalanceMode = .continuousAutoWhiteBalance
        }
    }

    private func observeSessionNotifications() {
        guard notificationTokens.isEmpty else {
            return
        }

        let center = NotificationCenter.default
        notificationTokens.append(center.addObserver(
            forName: AVCaptureSession.wasInterruptedNotification,
            object: session,
            queue: nil
        ) { [weak self] _ in
            self?.logger.warning("problem_capture.camera_interrupted")
        })
        notificationTokens.append(center.addObserver(
            forName: AVCaptureSession.interruptionEndedNotification,
            object: session,
            queue: nil
        ) { [weak self] _ in
            self?.logger.info("problem_capture.camera_interruption_ended")
        })
        notificationTokens.append(center.addObserver(
            forName: AVCaptureSession.runtimeErrorNotification,
            object: session,
            queue: nil
        ) { [weak self] _ in
            self?.logger.warning("problem_capture.camera_runtime_error")
        })
    }

    private func mapAuthorizationStatus(_ status: AVAuthorizationStatus) -> CapturePermissionStatus {
        switch status {
        case .notDetermined:
            .notDetermined
        case .authorized:
            .authorized
        case .denied:
            .denied
        case .restricted:
            .restricted
        @unknown default:
            .unavailable
        }
    }
}

extension AVFoundationProblemCameraClient: AVCapturePhotoCaptureDelegate {
    func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        sessionQueue.async { [weak self] in
            guard let self, let continuation = self.pendingPhotoContinuation else {
                return
            }
            self.pendingPhotoContinuation = nil

            if error != nil {
                continuation.resume(throwing: CaptureFailure.captureFailed)
                return
            }
            guard let data = photo.fileDataRepresentation() else {
                continuation.resume(throwing: CaptureFailure.captureFailed)
                return
            }
            continuation.resume(returning: data)
        }
    }
}
