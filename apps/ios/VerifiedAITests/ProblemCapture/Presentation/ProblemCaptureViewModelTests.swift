import Foundation
import SwiftUI
import XCTest
@testable import VerifiedAI

@MainActor
final class ProblemCaptureViewModelTests: XCTestCase {
    func testOpenStartsSourceSelection() {
        let viewModel = makeViewModel()

        viewModel.open()

        XCTAssertEqual(viewModel.state, .selectingSource)
    }

    func testAuthorizedCameraStartsCameraReadyState() async {
        let camera = FakeCameraClient(permissionStatus: .authorized)
        let viewModel = makeViewModel(camera: camera)

        await viewModel.chooseCamera()

        XCTAssertEqual(viewModel.state, .cameraReady)
        XCTAssertEqual(camera.startCount, 1)
    }

    func testDeniedCameraShowsRecoverablePermissionFailure() async {
        let camera = FakeCameraClient(permissionStatus: .denied)
        let viewModel = makeViewModel(camera: camera)

        await viewModel.chooseCamera()

        XCTAssertEqual(viewModel.state, .recoverableFailure(.permissionDenied, previousAsset: nil))
        XCTAssertEqual(camera.startCount, 0)
    }

    func testCaptureSuccessStopsCameraAndReviewsAnalyzedAsset() async {
        let asset = CapturedAsset.fixture()
        let camera = FakeCameraClient(permissionStatus: .authorized, captureData: Data([1, 2, 3]))
        let store = FakeAssetStore(imageAssets: [asset])
        let analyzer = StubQualityAnalyzer(assessment: CaptureQualityAssessment(
            issues: [CaptureQualityIssue(kind: .blur, severity: .warning)],
            metrics: .unevaluated
        ))
        let viewModel = makeViewModel(camera: camera, store: store, analyzer: analyzer)

        await viewModel.chooseCamera()
        await viewModel.capturePhoto()

        XCTAssertEqual(camera.stopCount, 1)
        guard case .reviewing(let reviewedAsset) = viewModel.state else {
            return XCTFail("Expected reviewing state")
        }
        XCTAssertEqual(reviewedAsset.id, asset.id)
        XCTAssertEqual(reviewedAsset.qualityAssessment.issues.map(\.kind), [.blur])
    }

    func testCaptureFailureIsRecoverable() async {
        let camera = FakeCameraClient(permissionStatus: .authorized, captureError: CaptureFailure.captureFailed)
        let viewModel = makeViewModel(camera: camera)

        await viewModel.chooseCamera()
        await viewModel.capturePhoto()

        XCTAssertEqual(viewModel.state, .recoverableFailure(.captureFailed, previousAsset: nil))
    }

    func testBackgroundDuringCaptureStopsCameraAndShowsRecoverableFailure() {
        let camera = FakeCameraClient(permissionStatus: .authorized)
        let viewModel = makeViewModel(camera: camera)
        viewModel.setStateForTesting(.capturing)

        viewModel.handleScenePhase(.background)

        XCTAssertEqual(viewModel.state, .recoverableFailure(.captureFailed, previousAsset: nil))
        XCTAssertEqual(camera.stopCount, 1)
    }

    func testPhotoImportSuccessReviewsAsset() async {
        let asset = CapturedAsset.fixture(source: .photoLibrary)
        let store = FakeAssetStore(imageAssets: [asset])
        let viewModel = makeViewModel(store: store)

        await viewModel.importPhotoData(Data([0xFF, 0xD8]), declaredUTType: "public.jpeg")

        guard case .reviewing(let reviewedAsset) = viewModel.state else {
            return XCTFail("Expected reviewing state")
        }
        XCTAssertEqual(reviewedAsset.source, .photoLibrary)
    }

    func testPhotoImportFailureIsRecoverable() async {
        let store = FakeAssetStore(error: CaptureFailure.unsupportedAsset)
        let viewModel = makeViewModel(store: store)

        await viewModel.importPhotoData(Data([0]), declaredUTType: "public.data")

        XCTAssertEqual(viewModel.state, .recoverableFailure(.unsupportedAsset, previousAsset: nil))
    }

    func testCropUpdateIsNonDestructiveMetadataAndAddsFramingWarning() {
        let asset = CapturedAsset.fixture(qualityAssessment: CaptureQualityAssessment(issues: [], metrics: .unevaluated))
        let viewModel = makeViewModel()

        viewModel.open()
        viewModel.setStateForTesting(.reviewing(asset))
        viewModel.editCrop()
        viewModel.updateCrop(CropSelection(x: 0.2, y: 0.2, width: 0.2, height: 0.2))
        viewModel.finishCropEditing()

        guard case .reviewing(let reviewedAsset) = viewModel.state else {
            return XCTFail("Expected reviewing state")
        }
        XCTAssertEqual(reviewedAsset.id, asset.id)
        XCTAssertEqual(reviewedAsset.cropSelection, CropSelection(x: 0.2, y: 0.2, width: 0.2, height: 0.2))
        XCTAssertTrue(reviewedAsset.qualityAssessment.issues.contains { $0.kind == .framing })
    }

    func testAcceptProducesLocalHandoffWithoutBackendFields() {
        let asset = CapturedAsset.fixture()
        let viewModel = makeViewModel()
        viewModel.setStateForTesting(.reviewing(asset))

        viewModel.accept()

        XCTAssertEqual(viewModel.state, .readyForHandoff(AcceptedCapturedAsset(asset: asset)))
    }

    func testRetakeDeletesPreviousAssetAndReturnsToCamera() async {
        let asset = CapturedAsset.fixture()
        let store = FakeAssetStore()
        let camera = FakeCameraClient(permissionStatus: .authorized)
        let viewModel = makeViewModel(camera: camera, store: store)
        viewModel.setStateForTesting(.reviewing(asset))

        await viewModel.retake()

        XCTAssertEqual(viewModel.state, .cameraReady)
        let deletedAssetIDs = await store.deletedAssetIDs()
        XCTAssertEqual(deletedAssetIDs, [asset.id])
    }

    func testLateImportResultDoesNotReplaceNewerAsset() async {
        let first = CapturedAsset.fixture(id: UUID(uuidString: "00000000-0000-0000-0000-000000000441")!)
        let second = CapturedAsset.fixture(id: UUID(uuidString: "00000000-0000-0000-0000-000000000442")!)
        let store = FakeAssetStore(imageAssets: [first, second], firstImageDelayNanoseconds: 80_000_000)
        let viewModel = makeViewModel(store: store)

        let lateTask = Task {
            await viewModel.importPhotoData(Data([1]), declaredUTType: "public.jpeg")
        }
        await store.waitForFirstImageCall()
        await viewModel.importPhotoData(Data([2]), declaredUTType: "public.jpeg")
        await lateTask.value

        guard case .reviewing(let reviewedAsset) = viewModel.state else {
            return XCTFail("Expected reviewing state")
        }
        XCTAssertEqual(reviewedAsset.id, second.id)
        let deletedAssetIDs = await store.deletedAssetIDs()
        XCTAssertEqual(deletedAssetIDs, [first.id])
    }

    private func makeViewModel(
        camera: FakeCameraClient = FakeCameraClient(permissionStatus: .authorized),
        store: FakeAssetStore = FakeAssetStore(),
        analyzer: StubQualityAnalyzer = StubQualityAnalyzer()
    ) -> ProblemCaptureViewModel {
        ProblemCaptureViewModel(
            cameraClient: camera,
            assetStore: store,
            qualityAnalyzer: analyzer,
            logger: AppLogger(subsystem: "com.verifiedai.learning.tests", category: "problem-capture")
        )
    }
}

private final class FakeCameraClient: ProblemCameraClient, @unchecked Sendable {
    var permissionStatus: CapturePermissionStatus
    var captureData: Data
    var captureError: Error?
    private(set) var startCount = 0
    private(set) var stopCount = 0

    init(
        permissionStatus: CapturePermissionStatus,
        captureData: Data = Data([0xFF, 0xD8]),
        captureError: Error? = nil
    ) {
        self.permissionStatus = permissionStatus
        self.captureData = captureData
        self.captureError = captureError
    }

    func requestPermission() async -> CapturePermissionStatus {
        permissionStatus
    }

    func start() async throws {
        startCount += 1
    }

    func stop() {
        stopCount += 1
    }

    func capturePhotoData() async throws -> Data {
        if let captureError {
            throw captureError
        }
        return captureData
    }
}

private actor FakeAssetStore: CapturedAssetStoring {
    private var imageAssets: [CapturedAsset]
    private let error: Error?
    private let firstImageDelayNanoseconds: UInt64
    private var imageStoreCount = 0
    private var deleted: [UUID] = []
    private var firstImageCallStarted = false
    private var firstImageCallWaiters: [CheckedContinuation<Void, Never>] = []

    init(
        imageAssets: [CapturedAsset] = [.fixture()],
        error: Error? = nil,
        firstImageDelayNanoseconds: UInt64 = 0
    ) {
        self.imageAssets = imageAssets
        self.error = error
        self.firstImageDelayNanoseconds = firstImageDelayNanoseconds
    }

    func storeImageData(_ data: Data, source: CaptureSource, declaredUTType: String?) async throws -> CapturedAsset {
        if let error {
            throw error
        }
        imageStoreCount += 1
        var asset = imageAssets.isEmpty ? CapturedAsset.fixture(source: source) : imageAssets.removeFirst()
        if imageStoreCount == 1 {
            firstImageCallStarted = true
            let waiters = firstImageCallWaiters
            firstImageCallWaiters.removeAll()
            waiters.forEach { $0.resume() }
            if firstImageDelayNanoseconds > 0 {
                try? await Task.sleep(nanoseconds: firstImageDelayNanoseconds)
            }
        }
        asset = CapturedAsset(
            id: asset.id,
            source: source,
            kind: asset.kind,
            localURL: asset.localURL,
            previewData: asset.previewData,
            pixelWidth: asset.pixelWidth,
            pixelHeight: asset.pixelHeight,
            createdAt: asset.createdAt,
            originalUTType: asset.originalUTType,
            cropSelection: asset.cropSelection,
            qualityAssessment: asset.qualityAssessment
        )
        return asset
    }

    func storePDF(at url: URL, source: CaptureSource) async throws -> CapturedAsset {
        if let error {
            throw error
        }
        return .fixture(source: source, kind: .pdf)
    }

    func delete(_ asset: CapturedAsset) async {
        deleted.append(asset.id)
    }

    func cleanUpExpiredAssets() async {}

    func waitForFirstImageCall() async {
        if firstImageCallStarted {
            return
        }
        await withCheckedContinuation { continuation in
            firstImageCallWaiters.append(continuation)
        }
    }

    func deletedAssetIDs() -> [UUID] {
        deleted
    }
}

private struct StubQualityAnalyzer: CaptureQualityAnalyzing {
    let assessment: CaptureQualityAssessment

    init(assessment: CaptureQualityAssessment = CaptureQualityAssessment(issues: [], metrics: .unevaluated)) {
        self.assessment = assessment
    }

    func analyze(asset: CapturedAsset) async throws -> CaptureQualityAssessment {
        assessment.replacingFramingIssue(for: asset.cropSelection)
    }
}

private extension CapturedAsset {
    static func fixture(
        id: UUID = UUID(uuidString: "00000000-0000-0000-0000-000000000440")!,
        source: CaptureSource = .camera,
        kind: CapturedAssetKind = .image,
        cropSelection: CropSelection = .fullImage,
        qualityAssessment: CaptureQualityAssessment = .pending
    ) -> CapturedAsset {
        CapturedAsset(
            id: id,
            source: source,
            kind: kind,
            localURL: URL(fileURLWithPath: "/tmp/\(id.uuidString).jpg"),
            previewData: Data([1, 2, 3]),
            pixelWidth: 1200,
            pixelHeight: 900,
            createdAt: Date(timeIntervalSince1970: 1_800_000_000),
            originalUTType: "public.jpeg",
            cropSelection: cropSelection,
            qualityAssessment: qualityAssessment
        )
    }
}
