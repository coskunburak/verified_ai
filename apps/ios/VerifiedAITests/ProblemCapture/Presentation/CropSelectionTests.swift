import CoreGraphics
import XCTest
@testable import VerifiedAI

final class CropSelectionTests: XCTestCase {
    func testFullImageCropIsValid() {
        XCTAssertTrue(CropSelection.fullImage.isValid)
        XCTAssertEqual(CropSelection.fullImage.retainedArea, 1)
    }

    func testInvalidCropClampsToBoundsAndMinimumSize() {
        let crop = CropSelection(x: -0.5, y: 0.95, width: 0.02, height: 0.02).clamped()

        XCTAssertTrue(crop.isValid)
        XCTAssertEqual(crop.x, 0)
        XCTAssertEqual(crop.width, ProblemCaptureLimits.minimumCropSide)
        XCTAssertEqual(crop.height, ProblemCaptureLimits.minimumCropSide)
        XCTAssertEqual(crop.y, 1 - ProblemCaptureLimits.minimumCropSide)
    }

    func testAspectFitImageRectCentersLetterboxedImage() {
        let rect = CropGeometryMapper.imageRect(
            imageSize: CGSize(width: 400, height: 200),
            containerSize: CGSize(width: 300, height: 300),
            contentMode: .aspectFit
        )

        XCTAssertEqual(rect.origin.x, 0, accuracy: 0.001)
        XCTAssertEqual(rect.origin.y, 75, accuracy: 0.001)
        XCTAssertEqual(rect.width, 300, accuracy: 0.001)
        XCTAssertEqual(rect.height, 150, accuracy: 0.001)
    }

    func testDisplayRectRoundTripsThroughNormalizedCrop() {
        let crop = CropSelection(x: 0.25, y: 0.2, width: 0.5, height: 0.4)
        let displayRect = CropGeometryMapper.displayRect(
            from: crop,
            imageSize: CGSize(width: 400, height: 200),
            containerSize: CGSize(width: 300, height: 300),
            contentMode: .aspectFit
        )
        let normalized = CropGeometryMapper.normalizedCrop(
            from: displayRect,
            imageSize: CGSize(width: 400, height: 200),
            containerSize: CGSize(width: 300, height: 300),
            contentMode: .aspectFit
        )

        XCTAssertEqual(normalized.x, crop.x, accuracy: 0.001)
        XCTAssertEqual(normalized.y, crop.y, accuracy: 0.001)
        XCTAssertEqual(normalized.width, crop.width, accuracy: 0.001)
        XCTAssertEqual(normalized.height, crop.height, accuracy: 0.001)
    }

    func testPixelRectUsesSourcePixelDimensions() {
        let rect = CropGeometryMapper.pixelRect(
            from: CropSelection(x: 0.1, y: 0.2, width: 0.5, height: 0.25),
            pixelWidth: 2000,
            pixelHeight: 1000
        )

        XCTAssertEqual(rect.origin.x, 200)
        XCTAssertEqual(rect.origin.y, 200)
        XCTAssertEqual(rect.width, 1000)
        XCTAssertEqual(rect.height, 250)
    }
}

