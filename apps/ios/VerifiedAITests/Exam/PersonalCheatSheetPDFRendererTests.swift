import XCTest
import PDFKit
@testable import VerifiedAI

@MainActor
final class PersonalCheatSheetPDFRendererTests: XCTestCase {
    func testA4GeometryAndReadableMinimum() {
        XCTAssertEqual(PersonalCheatSheetPDFRenderer.page.width, 595.2, accuracy: 0.01)
        XCTAssertEqual(PersonalCheatSheetPDFRenderer.page.height, 841.8, accuracy: 0.01)
        XCTAssertGreaterThanOrEqual(PersonalCheatSheetPDFRenderer.minimumFontSize, 11)
    }
    func testCleanupWithoutGeneratedFileIsIdempotent() throws {
        let renderer = PersonalCheatSheetPDFRenderer()
        try renderer.cleanup(); try renderer.cleanup()
        XCTAssertNil(renderer.temporaryURL)
    }
}

extension PersonalCheatSheetPDFRendererTests {
    func testWorstCaseIsOnePageWithReadableTextAndCleanup() throws {
        let sheet = try PreExamFixtures.snapshot(user: UUID(), session: UUID(), sheet: true, count: 10)
        let renderer = PersonalCheatSheetPDFRenderer()
        let url = try renderer.render(sheet, turkish: true)
        defer { try? renderer.cleanup() }
        let document = try XCTUnwrap(PDFDocument(url: url))
        XCTAssertEqual(document.pageCount, 1)
        let extracted = document.string ?? ""
        XCTAssertTrue(extracted.contains("Çalışma"), "Missing title text in extracted PDF: \(extracted)")
        XCTAssertTrue(extracted.contains("25"), "Missing counterexample value in extracted PDF: \(extracted)")
        XCTAssertTrue(extracted.contains("Gösterilmeyen"), "Missing footer text in extracted PDF: \(extracted)")
        XCTAssertGreaterThan(renderer.omittedCount, 0)
        let page = try XCTUnwrap(document.page(at: 0))
        XCTAssertEqual(page.bounds(for: .mediaBox).width, 595.2, accuracy: 0.1)
        try renderer.cleanup()
        XCTAssertFalse(FileManager.default.fileExists(atPath: url.path))
    }
}
