import Foundation
import ImageIO
import UIKit
@preconcurrency import Vision

struct AttemptRecognizedLine: Identifiable, Sendable {
    let id: UUID
    let raw: String
    var text: String
    let confidence: Float
    let box: CGRect
    var confirmed = false
}

/// Device-only OCR is transcription, never mathematical evidence. Each line requires review.
actor NativeAttemptExtraction {
    enum Failure: Error { case unreadable, tooManyLines, cancelled }
    func preview(_ asset: CapturedAsset) -> Data? {
        guard let source = CGImageSourceCreateWithURL(asset.localURL as CFURL, nil),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: 1600
              ] as CFDictionary),
              let crop = image.cropping(to: CropGeometryMapper.pixelRect(from: asset.cropSelection,
                  pixelWidth: image.width, pixelHeight: image.height)) else { return nil }
        return UIImage(cgImage: crop).jpegData(compressionQuality: 0.8)
    }
    func extract(_ asset: CapturedAsset) throws -> [AttemptRecognizedLine] {
        try Task.checkCancellation()
        guard asset.qualityAssessment.canContinue,
              let source = CGImageSourceCreateWithURL(asset.localURL as CFURL, nil),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: 2400
              ] as CFDictionary),
              let crop = image.cropping(to: CropGeometryMapper.pixelRect(from: asset.cropSelection,
                  pixelWidth: image.width, pixelHeight: image.height)) else { throw Failure.unreadable }
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = false
        request.recognitionLanguages = ["en-US"]
        request.minimumTextHeight = 0.012
        try VNImageRequestHandler(cgImage: crop, options: [:]).perform([request])
        try Task.checkCancellation()
        let observations = (request.results ?? []).sorted {
            if abs($0.boundingBox.midY - $1.boundingBox.midY) > 0.015 { return $0.boundingBox.midY > $1.boundingBox.midY }
            return $0.boundingBox.minX < $1.boundingBox.minX
        }
        guard !observations.isEmpty else { throw Failure.unreadable }
        guard observations.count <= 12 else { throw Failure.tooManyLines }
        return try observations.map { observation in
            guard let candidate = observation.topCandidates(1).first, !candidate.string.isEmpty,
                  candidate.string.utf16.count <= 256 else { throw Failure.unreadable }
            return AttemptRecognizedLine(id: UUID(), raw: candidate.string, text: candidate.string,
                confidence: candidate.confidence, box: observation.boundingBox)
        }
    }
}
