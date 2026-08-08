import UIKit
import UniformTypeIdentifiers
import XCTest
@testable import VerifiedAI

final class CapturedAssetStoreTests: XCTestCase {
    private var rootURL: URL!

    override func setUpWithError() throws {
        rootURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("ProblemCaptureStoreTests-\(UUID().uuidString)", isDirectory: true)
    }

    override func tearDownWithError() throws {
        if let rootURL {
            try? FileManager.default.removeItem(at: rootURL)
        }
    }

    func testStoreImageWritesGeneratedProtectedLocalAsset() async throws {
        let store = makeStore()
        let asset = try await store.storeImageData(try syntheticJPEGData(), source: .photoLibrary, declaredUTType: UTType.jpeg.identifier)

        XCTAssertTrue(FileManager.default.fileExists(atPath: asset.localURL.path))
        XCTAssertEqual(asset.localURL.lastPathComponent, "\(asset.id.uuidString).jpg")
        XCTAssertEqual(asset.kind, .image)
        XCTAssertEqual(asset.source, .photoLibrary)
        XCTAssertEqual(asset.originalUTType, UTType.jpeg.identifier)
        XCTAssertFalse(asset.previewData.isEmpty)
    }

    func testStoreImageGeneratesUniqueFilenames() async throws {
        let store = makeStore()

        let first = try await store.storeImageData(try syntheticJPEGData(), source: .camera, declaredUTType: UTType.jpeg.identifier)
        let second = try await store.storeImageData(try syntheticJPEGData(), source: .camera, declaredUTType: UTType.jpeg.identifier)

        XCTAssertNotEqual(first.localURL, second.localURL)
        XCTAssertTrue(FileManager.default.fileExists(atPath: first.localURL.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: second.localURL.path))
    }

    func testUnsupportedBinaryIsRejected() async {
        let store = makeStore()

        do {
            _ = try await store.storeImageData(Data([0, 1, 2, 3]), source: .file, declaredUTType: "public.data")
            XCTFail("Expected unsupported/corrupt failure")
        } catch let failure as CaptureFailure {
            XCTAssertTrue([.corruptAsset, .unsupportedAsset].contains(failure))
        } catch {
            XCTFail("Unexpected error \(error)")
        }
    }

    func testOversizedImageDataIsRejectedBeforeDecode() async {
        let store = makeStore()

        do {
            _ = try await store.storeImageData(
                Data(repeating: 0, count: ProblemCaptureLimits.maxImportBytes + 1),
                source: .file,
                declaredUTType: UTType.jpeg.identifier
            )
            XCTFail("Expected oversized failure")
        } catch let failure as CaptureFailure {
            XCTAssertEqual(failure, .assetTooLarge)
        } catch {
            XCTFail("Unexpected error \(error)")
        }
    }

    func testPDFImportCopiesFileAndCreatesPreview() async throws {
        let pdfURL = rootURL.appendingPathComponent("fixture.pdf")
        try FileManager.default.createDirectory(at: rootURL, withIntermediateDirectories: true)
        try syntheticPDFData().write(to: pdfURL)
        let store = makeStore(rootURL: rootURL.appendingPathComponent("CaptureStore", isDirectory: true))

        let asset = try await store.storePDF(at: pdfURL, source: .pdf)

        XCTAssertEqual(asset.kind, .pdf)
        XCTAssertEqual(asset.originalUTType, UTType.pdf.identifier)
        XCTAssertTrue(FileManager.default.fileExists(atPath: asset.localURL.path))
        XCTAssertFalse(asset.previewData.isEmpty)
    }

    func testDeleteRemovesStoredAsset() async throws {
        let store = makeStore()
        let asset = try await store.storeImageData(try syntheticJPEGData(), source: .camera, declaredUTType: UTType.jpeg.identifier)

        await store.delete(asset)

        XCTAssertFalse(FileManager.default.fileExists(atPath: asset.localURL.path))
    }

    func testCleanupExpiredAssetsRemovesOldFilesOnly() async throws {
        let now = Date(timeIntervalSince1970: 1_800_100_000)
        let store = makeStore(now: { now })
        try FileManager.default.createDirectory(at: rootURL, withIntermediateDirectories: true)
        let oldURL = rootURL.appendingPathComponent("old.jpg")
        let freshURL = rootURL.appendingPathComponent("fresh.jpg")
        try Data([1]).write(to: oldURL)
        try Data([2]).write(to: freshURL)
        try FileManager.default.setAttributes(
            [.modificationDate: now.addingTimeInterval(-(ProblemCaptureLimits.temporaryAssetTimeToLive + 1))],
            ofItemAtPath: oldURL.path
        )
        try FileManager.default.setAttributes(
            [.modificationDate: now],
            ofItemAtPath: freshURL.path
        )

        await store.cleanUpExpiredAssets()

        XCTAssertFalse(FileManager.default.fileExists(atPath: oldURL.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: freshURL.path))
    }

    private func makeStore(
        rootURL: URL? = nil,
        now: @escaping @Sendable () -> Date = { Date(timeIntervalSince1970: 1_800_000_000) }
    ) -> DefaultCapturedAssetStore {
        DefaultCapturedAssetStore(rootURL: rootURL ?? self.rootURL, now: now)
    }

    private func syntheticJPEGData() throws -> Data {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 320, height: 240))
        let image = renderer.image { _ in
            UIColor(white: 0.93, alpha: 1).setFill()
            UIRectFill(CGRect(x: 0, y: 0, width: 320, height: 240))
            UIColor.black.setStroke()
            let path = UIBezierPath()
            path.lineWidth = 3
            path.move(to: CGPoint(x: 24, y: 72))
            path.addLine(to: CGPoint(x: 296, y: 72))
            path.move(to: CGPoint(x: 24, y: 142))
            path.addLine(to: CGPoint(x: 240, y: 178))
            path.stroke()
        }
        return try XCTUnwrap(image.jpegData(compressionQuality: 0.95))
    }

    private func syntheticPDFData() -> Data {
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 320, height: 420))
        return renderer.pdfData { context in
            context.beginPage()
            UIColor.white.setFill()
            UIRectFill(CGRect(x: 0, y: 0, width: 320, height: 420))
            "x + 3 = 9".draw(
                at: CGPoint(x: 48, y: 96),
                withAttributes: [.font: UIFont.systemFont(ofSize: 28)]
            )
        }
    }
}
