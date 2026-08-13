import CoreGraphics
import Foundation

enum CaptureSource: String, CaseIterable, Equatable, Identifiable, Sendable {
    case camera
    case photoLibrary
    case file
    case pdf

    var id: String { rawValue }
}

enum CapturedAssetKind: String, Equatable, Sendable {
    case image
    case pdf
}

struct CapturedAsset: Equatable, Identifiable, Sendable {
    let id: UUID
    let source: CaptureSource
    let kind: CapturedAssetKind
    let localURL: URL
    let previewData: Data
    let pixelWidth: Int
    let pixelHeight: Int
    let createdAt: Date
    let originalUTType: String
    var cropSelection: CropSelection
    var qualityAssessment: CaptureQualityAssessment

    var dimensionsDescription: String {
        "\(pixelWidth)x\(pixelHeight)"
    }
}

struct AcceptedCapturedAsset: Equatable, Sendable {
    let asset: CapturedAsset

    var localIdentifier: UUID {
        asset.id
    }
}

enum CapturePermissionStatus: Equatable, Sendable {
    case notDetermined
    case authorized
    case denied
    case restricted
    case unavailable
}

enum CaptureQualitySeverity: Int, Comparable, Equatable, Sendable {
    case informational = 0
    case warning = 1
    case blocking = 2

    static func < (lhs: CaptureQualitySeverity, rhs: CaptureQualitySeverity) -> Bool {
        lhs.rawValue < rhs.rawValue
    }
}

enum CaptureQualityIssueKind: String, Equatable, Sendable {
    case blur
    case glare
    case framing
}

struct CaptureQualityIssue: Equatable, Identifiable, Sendable {
    let kind: CaptureQualityIssueKind
    let severity: CaptureQualitySeverity

    var id: String {
        "\(kind.rawValue)-\(severity.rawValue)"
    }
}

struct CaptureQualityMetrics: Equatable, Sendable {
    let sharpness: Double
    let glareRatio: Double
    let cropRetainedArea: Double
    let edgeRisk: Double

    static let unevaluated = CaptureQualityMetrics(
        sharpness: 0,
        glareRatio: 0,
        cropRetainedArea: 1,
        edgeRisk: 0
    )
}

struct CaptureQualityAssessment: Equatable, Sendable {
    let issues: [CaptureQualityIssue]
    let metrics: CaptureQualityMetrics

    static let pending = CaptureQualityAssessment(issues: [], metrics: .unevaluated)

    var highestSeverity: CaptureQualitySeverity? {
        issues.map(\.severity).max()
    }

    var canContinue: Bool {
        !issues.contains { $0.severity == .blocking }
    }

    func replacingFramingIssue(for crop: CropSelection) -> CaptureQualityAssessment {
        var nextIssues = issues.filter { $0.kind != .framing }
        let retainedArea = crop.width * crop.height
        let edgeRisk = crop.edgeRisk
        if retainedArea < ProblemCaptureLimits.minimumRecommendedCropArea ||
            crop.width < ProblemCaptureLimits.minimumRecommendedCropSide ||
            crop.height < ProblemCaptureLimits.minimumRecommendedCropSide ||
            edgeRisk > ProblemCaptureLimits.cropEdgeWarningThreshold {
            nextIssues.append(CaptureQualityIssue(kind: .framing, severity: .warning))
        }
        return CaptureQualityAssessment(
            issues: nextIssues,
            metrics: CaptureQualityMetrics(
                sharpness: metrics.sharpness,
                glareRatio: metrics.glareRatio,
                cropRetainedArea: retainedArea,
                edgeRisk: edgeRisk
            )
        )
    }
}

struct CropSelection: Equatable, Sendable {
    let x: Double
    let y: Double
    let width: Double
    let height: Double

    static let fullImage = CropSelection(x: 0, y: 0, width: 1, height: 1)

    init(x: Double, y: Double, width: Double, height: Double) {
        self.x = x
        self.y = y
        self.width = width
        self.height = height
    }

    var retainedArea: Double {
        width * height
    }

    var edgeRisk: Double {
        1 - retainedArea
    }

    var isValid: Bool {
        x >= -ProblemCaptureLimits.cropTolerance &&
        y >= -ProblemCaptureLimits.cropTolerance &&
        width >= ProblemCaptureLimits.minimumCropSide &&
        height >= ProblemCaptureLimits.minimumCropSide &&
        x + width <= 1 + ProblemCaptureLimits.cropTolerance &&
        y + height <= 1 + ProblemCaptureLimits.cropTolerance
    }

    func clamped() -> CropSelection {
        let safeWidth = min(max(width, ProblemCaptureLimits.minimumCropSide), 1)
        let safeHeight = min(max(height, ProblemCaptureLimits.minimumCropSide), 1)
        let safeX = min(max(x, 0), 1 - safeWidth)
        let safeY = min(max(y, 0), 1 - safeHeight)
        return CropSelection(x: safeX, y: safeY, width: safeWidth, height: safeHeight)
    }
}

enum CropContentMode: Equatable, Sendable {
    case aspectFit
    case aspectFill
}

enum CropGeometryMapper {
    static func imageRect(imageSize: CGSize, containerSize: CGSize, contentMode: CropContentMode) -> CGRect {
        guard imageSize.width > 0, imageSize.height > 0, containerSize.width > 0, containerSize.height > 0 else {
            return .zero
        }

        let widthScale = containerSize.width / imageSize.width
        let heightScale = containerSize.height / imageSize.height
        let scale = contentMode == .aspectFit ? min(widthScale, heightScale) : max(widthScale, heightScale)
        let width = imageSize.width * scale
        let height = imageSize.height * scale
        return CGRect(
            x: (containerSize.width - width) / 2,
            y: (containerSize.height - height) / 2,
            width: width,
            height: height
        )
    }

    static func displayRect(
        from crop: CropSelection,
        imageSize: CGSize,
        containerSize: CGSize,
        contentMode: CropContentMode
    ) -> CGRect {
        let imageRect = imageRect(imageSize: imageSize, containerSize: containerSize, contentMode: contentMode)
        return CGRect(
            x: imageRect.minX + imageRect.width * crop.x,
            y: imageRect.minY + imageRect.height * crop.y,
            width: imageRect.width * crop.width,
            height: imageRect.height * crop.height
        )
    }

    static func normalizedCrop(
        from displayRect: CGRect,
        imageSize: CGSize,
        containerSize: CGSize,
        contentMode: CropContentMode
    ) -> CropSelection {
        let imageRect = imageRect(imageSize: imageSize, containerSize: containerSize, contentMode: contentMode)
        guard !imageRect.isEmpty else {
            return .fullImage
        }

        let visibleRect = displayRect.intersection(imageRect)
        guard !visibleRect.isNull, visibleRect.width > 0, visibleRect.height > 0 else {
            return .fullImage
        }

        return CropSelection(
            x: Double((visibleRect.minX - imageRect.minX) / imageRect.width),
            y: Double((visibleRect.minY - imageRect.minY) / imageRect.height),
            width: Double(visibleRect.width / imageRect.width),
            height: Double(visibleRect.height / imageRect.height)
        ).clamped()
    }

    static func pixelRect(from crop: CropSelection, pixelWidth: Int, pixelHeight: Int) -> CGRect {
        CGRect(
            x: Double(pixelWidth) * crop.x,
            y: Double(pixelHeight) * crop.y,
            width: Double(pixelWidth) * crop.width,
            height: Double(pixelHeight) * crop.height
        ).integral
    }
}

enum CaptureFailure: Error, Equatable, Sendable {
    case cameraUnavailable
    case permissionDenied
    case permissionRestricted
    case captureFailed
    case importFailed
    case unsupportedAsset
    case corruptAsset
    case assetTooLarge
    case dimensionTooLarge
    case pdfPageUnavailable
    case qualityAnalysisFailed
    case tempStorageFailed
}

enum ProblemCaptureState: Equatable, Sendable {
    case idle
    case selectingSource
    case requestingCameraPermission
    case cameraReady
    case capturing
    case importing(CaptureSource)
    case processingLocalAsset
    case reviewing(CapturedAsset)
    case editingCrop(CapturedAsset)
    case readyForHandoff(AcceptedCapturedAsset)
    case recoverableFailure(CaptureFailure, previousAsset: CapturedAsset?)
    case terminalFailure(CaptureFailure)

    var currentAsset: CapturedAsset? {
        switch self {
        case .reviewing(let asset), .editingCrop(let asset):
            asset
        case .readyForHandoff(let accepted):
            accepted.asset
        case .recoverableFailure(_, let previousAsset):
            previousAsset
        default:
            nil
        }
    }
}

enum ProblemCaptureLimits {
    static let maxImportBytes = 20 * 1024 * 1024
    static let maxPixelDimension = 12_000
    static let previewMaxPixelDimension = 1_600
    static let temporaryAssetTimeToLive: TimeInterval = 24 * 60 * 60
    static let minimumCropSide = 0.10
    static let minimumRecommendedCropSide = 0.18
    static let minimumRecommendedCropArea = 0.35
    static let cropEdgeWarningThreshold = 0.65
    static let cropTolerance = 0.000_001
}
