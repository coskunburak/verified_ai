import Foundation
import UIKit

struct DefaultCaptureQualityAnalyzer: CaptureQualityAnalyzing {
    static let blurWarningSharpnessThreshold = 6.0
    static let glareWarningCellRatioThreshold = 0.28
    static let glareWarningTotalRatioCeiling = 0.42

    func analyze(asset: CapturedAsset) async throws -> CaptureQualityAssessment {
        guard let image = UIImage(data: asset.previewData), let cgImage = image.cgImage else {
            throw CaptureFailure.qualityAnalysisFailed
        }
        guard let sample = PixelSample(cgImage: cgImage, maxSide: 96) else {
            throw CaptureFailure.qualityAnalysisFailed
        }

        let sharpness = sample.laplacianSharpness()
        let glare = sample.localizedGlareRatio()
        var issues: [CaptureQualityIssue] = []

        if sharpness < Self.blurWarningSharpnessThreshold {
            issues.append(CaptureQualityIssue(kind: .blur, severity: .warning))
        }
        if glare.cellRatio > Self.glareWarningCellRatioThreshold && glare.totalRatio < Self.glareWarningTotalRatioCeiling {
            issues.append(CaptureQualityIssue(kind: .glare, severity: .warning))
        }

        let retainedArea = asset.cropSelection.retainedArea
        let edgeRisk = asset.cropSelection.edgeRisk
        if retainedArea < ProblemCaptureLimits.minimumRecommendedCropArea || edgeRisk > ProblemCaptureLimits.cropEdgeWarningThreshold {
            issues.append(CaptureQualityIssue(kind: .framing, severity: .warning))
        }

        return CaptureQualityAssessment(
            issues: issues,
            metrics: CaptureQualityMetrics(
                sharpness: sharpness,
                glareRatio: glare.cellRatio,
                cropRetainedArea: retainedArea,
                edgeRisk: edgeRisk
            )
        )
    }
}

private struct PixelSample {
    let width: Int
    let height: Int
    let luma: [Double]

    init?(cgImage: CGImage, maxSide: Int) {
        guard cgImage.width > 0, cgImage.height > 0 else {
            return nil
        }
        let scale = min(1, Double(maxSide) / Double(max(cgImage.width, cgImage.height)))
        self.width = max(1, Int(Double(cgImage.width) * scale))
        self.height = max(1, Int(Double(cgImage.height) * scale))
        var bytes = [UInt8](repeating: 0, count: width * height * 4)
        guard let context = CGContext(
            data: &bytes,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            return nil
        }

        context.interpolationQuality = .medium
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

        var luma = [Double]()
        luma.reserveCapacity(width * height)
        for pixel in stride(from: 0, to: bytes.count, by: 4) {
            let red = Double(bytes[pixel]) / 255
            let green = Double(bytes[pixel + 1]) / 255
            let blue = Double(bytes[pixel + 2]) / 255
            luma.append((0.2126 * red) + (0.7152 * green) + (0.0722 * blue))
        }
        self.luma = luma
    }

    func laplacianSharpness() -> Double {
        guard width >= 3, height >= 3 else {
            return 0
        }
        var total = 0.0
        var count = 0
        for y in 1..<(height - 1) {
            for x in 1..<(width - 1) {
                let center = value(x: x, y: y)
                let laplacian = abs((4 * center) - value(x: x - 1, y: y) - value(x: x + 1, y: y) - value(x: x, y: y - 1) - value(x: x, y: y + 1))
                total += laplacian
                count += 1
            }
        }
        return (total / Double(max(count, 1))) * 255
    }

    func localizedGlareRatio() -> (cellRatio: Double, totalRatio: Double) {
        guard width >= 4, height >= 4 else {
            return (0, 0)
        }

        let columns = 8
        let rows = 8
        var totalGlarePixels = 0
        var highestCellRatio = 0.0
        for row in 0..<rows {
            for column in 0..<columns {
                let xRange = cellRange(index: column, count: columns, maximum: width)
                let yRange = cellRange(index: row, count: rows, maximum: height)
                var glarePixels = 0
                var totalPixels = 0
                for y in yRange {
                    for x in xRange {
                        totalPixels += 1
                        if isGlareLikePixel(x: x, y: y) {
                            glarePixels += 1
                        }
                    }
                }
                totalGlarePixels += glarePixels
                highestCellRatio = max(highestCellRatio, Double(glarePixels) / Double(max(totalPixels, 1)))
            }
        }

        return (
            cellRatio: highestCellRatio,
            totalRatio: Double(totalGlarePixels) / Double(width * height)
        )
    }

    private func isGlareLikePixel(x: Int, y: Int) -> Bool {
        let value = value(x: x, y: y)
        guard value > 0.97 else {
            return false
        }
        let xMin = max(0, x - 1)
        let xMax = min(width - 1, x + 1)
        let yMin = max(0, y - 1)
        let yMax = min(height - 1, y + 1)
        var localContrast = 0.0
        var samples = 0
        for yy in yMin...yMax {
            for xx in xMin...xMax {
                localContrast += abs(value - self.value(x: xx, y: yy))
                samples += 1
            }
        }
        return localContrast / Double(max(samples, 1)) < 0.018
    }

    private func cellRange(index: Int, count: Int, maximum: Int) -> Range<Int> {
        let start = (maximum * index) / count
        let end = (maximum * (index + 1)) / count
        return start..<max(start + 1, end)
    }

    private func value(x: Int, y: Int) -> Double {
        luma[(y * width) + x]
    }
}

