import UIKit
import XCTest
@testable import VerifiedAI

final class CaptureQualityAnalyzerTests: XCTestCase {
    func testSharpSyntheticProblemDoesNotTriggerBlurWarning() async throws {
        let asset = try asset(preview: syntheticProblemImage())
        let assessment = try await DefaultCaptureQualityAnalyzer().analyze(asset: asset)

        XCTAssertFalse(assessment.issues.contains { $0.kind == .blur })
    }

    func testFlatLowDetailImageTriggersBlurWarning() async throws {
        let asset = try asset(preview: flatImage())
        let assessment = try await DefaultCaptureQualityAnalyzer().analyze(asset: asset)

        XCTAssertTrue(assessment.issues.contains { $0.kind == .blur })
    }

    func testLocalizedGlarePatchTriggersGlareWarning() async throws {
        let asset = try asset(preview: glareImage())
        let assessment = try await DefaultCaptureQualityAnalyzer().analyze(asset: asset)

        XCTAssertTrue(assessment.issues.contains { $0.kind == .glare })
    }

    func testSmallCropTriggersFramingWarning() async throws {
        var capture = try asset(preview: syntheticProblemImage())
        capture.cropSelection = CropSelection(x: 0.2, y: 0.2, width: 0.25, height: 0.25)

        let assessment = try await DefaultCaptureQualityAnalyzer().analyze(asset: capture)

        XCTAssertTrue(assessment.issues.contains { $0.kind == .framing })
    }

    private func asset(preview image: UIImage) throws -> CapturedAsset {
        let data = try XCTUnwrap(image.jpegData(compressionQuality: 0.95))
        return CapturedAsset(
            id: UUID(uuidString: "00000000-0000-0000-0000-000000000551")!,
            source: .camera,
            kind: .image,
            localURL: URL(fileURLWithPath: "/tmp/synthetic-capture.jpg"),
            previewData: data,
            pixelWidth: Int(image.size.width),
            pixelHeight: Int(image.size.height),
            createdAt: Date(timeIntervalSince1970: 1_800_000_000),
            originalUTType: "public.jpeg",
            cropSelection: .fullImage,
            qualityAssessment: .pending
        )
    }

    private func syntheticProblemImage() -> UIImage {
        renderImage { rect in
            UIColor(white: 0.92, alpha: 1).setFill()
            UIRectFill(rect)
            UIColor.black.setStroke()
            let path = UIBezierPath()
            path.lineWidth = 4
            path.move(to: CGPoint(x: 32, y: 72))
            path.addLine(to: CGPoint(x: 226, y: 72))
            path.move(to: CGPoint(x: 32, y: 128))
            path.addLine(to: CGPoint(x: 226, y: 170))
            path.move(to: CGPoint(x: 32, y: 210))
            path.addLine(to: CGPoint(x: 180, y: 210))
            path.stroke()
            "2x + 5 = 17".draw(
                at: CGPoint(x: 34, y: 86),
                withAttributes: [.font: UIFont.systemFont(ofSize: 28, weight: .semibold)]
            )
        }
    }

    private func flatImage() -> UIImage {
        renderImage { rect in
            UIColor(white: 0.88, alpha: 1).setFill()
            UIRectFill(rect)
        }
    }

    private func glareImage() -> UIImage {
        renderImage { rect in
            UIColor(white: 0.88, alpha: 1).setFill()
            UIRectFill(rect)
            UIColor.black.setStroke()
            let path = UIBezierPath()
            path.lineWidth = 4
            path.move(to: CGPoint(x: 24, y: 64))
            path.addLine(to: CGPoint(x: 232, y: 64))
            path.move(to: CGPoint(x: 24, y: 190))
            path.addLine(to: CGPoint(x: 232, y: 190))
            path.stroke()
            UIColor.white.setFill()
            UIRectFill(CGRect(x: 92, y: 84, width: 80, height: 72))
        }
    }

    private func renderImage(draw: (CGRect) -> Void) -> UIImage {
        let size = CGSize(width: 256, height: 256)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { _ in
            draw(CGRect(origin: .zero, size: size))
        }
    }
}

