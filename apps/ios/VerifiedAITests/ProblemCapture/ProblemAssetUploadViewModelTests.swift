import CryptoKit
import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class ProblemAssetUploadViewModelTests: XCTestCase {
    nonisolated(unsafe) private var rootURL: URL!

    override func setUpWithError() throws {
        rootURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("ProblemAssetUploadViewModelTests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: rootURL, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        if let rootURL {
            try? FileManager.default.removeItem(at: rootURL)
        }
    }

    func testSuccessfulUploadTransitionsToAvailableAndDeletesLocalAsset() async throws {
        let fixture = try makeAcceptedAsset(bytes: Data([1, 2, 3, 4]))
        let api = FakeProblemAssetUploadAPI()
        let uploader = FakePresignedObjectUploader()
        let store = FakeUploadAssetStore()
        let viewModel = makeViewModel(api: api, uploader: uploader, store: store)

        await viewModel.start(fixture.acceptedAsset)

        XCTAssertEqual(viewModel.state, .available(FakeProblemAssetUploadAPI.reference))
        let deletedAssetIDs = await store.deletedAssetIDs()
        let uploadedFileURL = await uploader.uploadedFileURL()
        let requiredHeaders = await uploader.requiredHeaders()
        XCTAssertEqual(deletedAssetIDs, [fixture.acceptedAsset.localIdentifier])
        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.fileURL.path))
        XCTAssertEqual(uploadedFileURL, fixture.fileURL)
        XCTAssertEqual(requiredHeaders, ["Content-Type": "image/jpeg"])
    }

    func testReservationUsesExactLocalBytesMetadataAndStableIdempotencyKey() async throws {
        let bytes = Data("uploaded-bytes".utf8)
        let fixture = try makeAcceptedAsset(
            bytes: bytes,
            cropSelection: CropSelection(x: 0.1, y: 0.2, width: 0.7, height: 0.6)
        )
        let api = FakeProblemAssetUploadAPI()
        let viewModel = makeViewModel(api: api)

        await viewModel.start(fixture.acceptedAsset)

        let receivedReservation = await api.receivedReservation()
        let received = try XCTUnwrap(receivedReservation)
        let expectedChecksum = SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined()
        XCTAssertEqual(received.request.source, "camera")
        XCTAssertEqual(received.request.assetKind, "image")
        XCTAssertEqual(received.request.contentType, "image/jpeg")
        XCTAssertEqual(received.request.sizeBytes, Int64(bytes.count))
        XCTAssertEqual(received.request.checksumSha256, expectedChecksum)
        XCTAssertEqual(received.request.imageWidth, 1200)
        XCTAssertEqual(received.request.imageHeight, 900)
        XCTAssertEqual(received.request.cropX, 0.1)
        XCTAssertEqual(received.request.cropY, 0.2)
        XCTAssertEqual(received.request.cropWidth, 0.7)
        XCTAssertEqual(received.request.cropHeight, 0.6)
        XCTAssertEqual(received.idempotencyKey, "problem-asset-reserve-\(fixture.acceptedAsset.localIdentifier.uuidString)")
        let completionIdempotencyKey = await api.receivedCompletionIdempotencyKey()
        XCTAssertEqual(completionIdempotencyKey, "problem-asset-complete-\(fixture.acceptedAsset.localIdentifier.uuidString)")
    }

    func testPDFReservationUsesPDFContentTypeWithoutImageDimensions() async throws {
        let fixture = try makeAcceptedAsset(
            bytes: Data("%PDF-1.7".utf8),
            kind: .pdf,
            source: .pdf,
            fileExtension: "pdf",
            originalUTType: "com.adobe.pdf"
        )
        let api = FakeProblemAssetUploadAPI()
        let viewModel = makeViewModel(api: api)

        await viewModel.start(fixture.acceptedAsset)

        let receivedReservation = await api.receivedReservation()
        let received = try XCTUnwrap(receivedReservation)
        XCTAssertEqual(received.request.source, "pdf")
        XCTAssertEqual(received.request.assetKind, "pdf")
        XCTAssertEqual(received.request.contentType, "application/pdf")
        XCTAssertNil(received.request.imageWidth)
        XCTAssertNil(received.request.imageHeight)
    }

    func testOfflineFailurePreservesLocalAssetForRetry() async throws {
        let fixture = try makeAcceptedAsset(bytes: Data([9]))
        let api = FakeProblemAssetUploadAPI()
        let viewModel = makeViewModel(api: api, networkMonitor: StubNetworkMonitor(isReachable: false))

        await viewModel.start(fixture.acceptedAsset)

        XCTAssertEqual(viewModel.state, .recoverableFailure(.offline, fixture.acceptedAsset))
        XCTAssertTrue(FileManager.default.fileExists(atPath: fixture.fileURL.path))
        let receivedReservation = await api.receivedReservation()
        XCTAssertNil(receivedReservation)
    }

    func testObjectUploadFailurePreservesLocalAsset() async throws {
        let fixture = try makeAcceptedAsset(bytes: Data([5, 6]))
        let uploader = FakePresignedObjectUploader(error: NetworkError.httpStatus(403))
        let store = FakeUploadAssetStore()
        let viewModel = makeViewModel(uploader: uploader, store: store)

        await viewModel.start(fixture.acceptedAsset)

        XCTAssertEqual(viewModel.state, .recoverableFailure(.uploadFailed, fixture.acceptedAsset))
        let deletedAssetIDs = await store.deletedAssetIDs()
        XCTAssertEqual(deletedAssetIDs, [])
        XCTAssertTrue(FileManager.default.fileExists(atPath: fixture.fileURL.path))
    }

    func testCompletionChecksumFailurePreservesLocalAssetAndRetriesSuccessfully() async throws {
        let fixture = try makeAcceptedAsset(bytes: Data([7, 8]))
        let api = FakeProblemAssetUploadAPI(completeErrors: [
            NetworkError.server(problem: ProblemDetails.fixture(code: "UPLOAD_CHECKSUM_MISMATCH"))
        ])
        let store = FakeUploadAssetStore()
        let viewModel = makeViewModel(api: api, store: store)

        await viewModel.start(fixture.acceptedAsset)

        XCTAssertEqual(
            viewModel.state,
            .recoverableFailure(.completionFailed("UPLOAD_CHECKSUM_MISMATCH"), fixture.acceptedAsset)
        )
        XCTAssertTrue(FileManager.default.fileExists(atPath: fixture.fileURL.path))

        await viewModel.retry()

        XCTAssertEqual(viewModel.state, .available(FakeProblemAssetUploadAPI.reference))
        let reserveCount = await api.reserveCount()
        let completeCount = await api.completeCount()
        let deletedAssetIDs = await store.deletedAssetIDs()
        XCTAssertEqual(reserveCount, 2)
        XCTAssertEqual(completeCount, 2)
        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.fileURL.path))
        XCTAssertEqual(deletedAssetIDs, [fixture.acceptedAsset.localIdentifier])
    }

    private func makeViewModel(
        api: FakeProblemAssetUploadAPI = FakeProblemAssetUploadAPI(),
        uploader: FakePresignedObjectUploader = FakePresignedObjectUploader(),
        store: FakeUploadAssetStore = FakeUploadAssetStore(),
        networkMonitor: StubNetworkMonitor = StubNetworkMonitor(isReachable: true)
    ) -> ProblemAssetUploadViewModel {
        ProblemAssetUploadViewModel(
            uploadAPI: api,
            objectUploader: uploader,
            assetStore: store,
            networkMonitor: networkMonitor,
            logger: AppLogger(subsystem: "com.verifiedai.learning.tests", category: "problem-asset-upload")
        )
    }

    private func makeAcceptedAsset(
        bytes: Data,
        kind: CapturedAssetKind = .image,
        source: CaptureSource = .camera,
        fileExtension: String = "jpg",
        originalUTType: String = "public.jpeg",
        cropSelection: CropSelection = .fullImage
    ) throws -> (acceptedAsset: AcceptedCapturedAsset, fileURL: URL) {
        let id = UUID(uuidString: "00000000-0000-0000-0000-000000000661")!
        let fileURL = rootURL.appendingPathComponent("\(id.uuidString).\(fileExtension)")
        try bytes.write(to: fileURL)
        let asset = CapturedAsset(
            id: id,
            source: source,
            kind: kind,
            localURL: fileURL,
            previewData: Data([1, 2, 3]),
            pixelWidth: 1200,
            pixelHeight: 900,
            createdAt: Date(timeIntervalSince1970: 1_800_000_000),
            originalUTType: originalUTType,
            cropSelection: cropSelection,
            qualityAssessment: .pending
        )
        return (AcceptedCapturedAsset(asset: asset), fileURL)
    }
}

private actor FakeProblemAssetUploadAPI: ProblemAssetUploadServicing {
    struct ReceivedReservation: Equatable {
        let request: ProblemAssetUploadRequest
        let idempotencyKey: String
    }

    static let uploadId = UUID(uuidString: "00000000-0000-0000-0000-000000000701")!
    static let problemSessionId = UUID(uuidString: "00000000-0000-0000-0000-000000000702")!
    static let problemAssetId = UUID(uuidString: "00000000-0000-0000-0000-000000000703")!
    static let reservation = ProblemAssetUploadReservation(
        uploadId: uploadId,
        problemSessionId: problemSessionId,
        problemAssetId: problemAssetId,
        assetStatus: "PENDING",
        uploadURL: URL(string: "http://127.0.0.1:9000/problem-assets/object")!,
        expiresAt: Date(timeIntervalSince1970: 1_800_000_900),
        requiredHeaders: ["Content-Type": "image/jpeg"]
    )
    static let reference = DurableProblemAssetReference(
        uploadId: uploadId,
        problemSessionId: problemSessionId,
        problemAssetId: problemAssetId,
        problemSessionStatus: "ASSET_UPLOADED",
        assetStatus: "AVAILABLE",
        availableAt: Date(timeIntervalSince1970: 1_800_001_000)
    )

    private var reserveErrors: [Error]
    private var completeErrors: [Error]
    private var reservations: [ReceivedReservation] = []
    private var completionKeys: [String] = []
    private var completionUploadIds: [UUID] = []

    init(reserveErrors: [Error] = [], completeErrors: [Error] = []) {
        self.reserveErrors = reserveErrors
        self.completeErrors = completeErrors
    }

    func reserveUpload(
        _ request: ProblemAssetUploadRequest,
        idempotencyKey: String
    ) async throws -> ProblemAssetUploadReservation {
        reservations.append(ReceivedReservation(request: request, idempotencyKey: idempotencyKey))
        if !reserveErrors.isEmpty {
            throw reserveErrors.removeFirst()
        }
        return Self.reservation
    }

    func completeUpload(uploadId: UUID, idempotencyKey: String) async throws -> DurableProblemAssetReference {
        completionUploadIds.append(uploadId)
        completionKeys.append(idempotencyKey)
        if !completeErrors.isEmpty {
            throw completeErrors.removeFirst()
        }
        return Self.reference
    }

    func receivedReservation() -> ReceivedReservation? {
        reservations.last
    }

    func receivedCompletionIdempotencyKey() -> String? {
        completionKeys.last
    }

    func reserveCount() -> Int {
        reservations.count
    }

    func completeCount() -> Int {
        completionKeys.count
    }
}

private actor FakePresignedObjectUploader: PresignedObjectUploading {
    private let error: Error?
    private var lastFileURL: URL?
    private var lastRequiredHeaders: [String: String] = [:]

    init(error: Error? = nil) {
        self.error = error
    }

    func upload(
        fileURL: URL,
        to uploadURL: URL,
        requiredHeaders: [String: String],
        progress: @escaping @Sendable (Double) -> Void
    ) async throws {
        lastFileURL = fileURL
        lastRequiredHeaders = requiredHeaders
        progress(0)
        progress(0.42)
        if let error {
            throw error
        }
        progress(1)
    }

    func uploadedFileURL() -> URL? {
        lastFileURL
    }

    func requiredHeaders() -> [String: String] {
        lastRequiredHeaders
    }
}

private actor FakeUploadAssetStore: CapturedAssetStoring {
    private var deleted: [UUID] = []

    func storeImageData(_ data: Data, source: CaptureSource, declaredUTType: String?) async throws -> CapturedAsset {
        fatalError("Not used by ProblemAssetUploadViewModelTests")
    }

    func storePDF(at url: URL, source: CaptureSource) async throws -> CapturedAsset {
        fatalError("Not used by ProblemAssetUploadViewModelTests")
    }

    func delete(_ asset: CapturedAsset) async {
        deleted.append(asset.id)
        try? FileManager.default.removeItem(at: asset.localURL)
    }

    func cleanUpExpiredAssets() async {}

    func deletedAssetIDs() -> [UUID] {
        deleted
    }
}

private final class StubNetworkMonitor: NetworkMonitoring {
    let isReachable: Bool

    init(isReachable: Bool) {
        self.isReachable = isReachable
    }
}

private extension ProblemDetails {
    static func fixture(code: String) -> ProblemDetails {
        ProblemDetails(
            type: "https://errors.verified-ai-learning.example/\(code.lowercased())",
            title: code,
            status: 400,
            code: code,
            traceId: "trace",
            details: .init(recoverable: true, userAction: "RETRY")
        )
    }
}
