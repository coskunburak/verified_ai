import Foundation
import ImageIO
import PDFKit
import UniformTypeIdentifiers
import UIKit

final class DefaultCapturedAssetStore: CapturedAssetStoring, @unchecked Sendable {
    private let fileManager: FileManager
    private let rootURL: URL
    private let now: @Sendable () -> Date

    init(
        rootURL: URL = FileManager.default.temporaryDirectory.appendingPathComponent("VerifiedAIProblemCapture", isDirectory: true),
        fileManager: FileManager = .default,
        now: @escaping @Sendable () -> Date = Date.init
    ) {
        self.rootURL = rootURL
        self.fileManager = fileManager
        self.now = now
    }

    func storeImageData(_ data: Data, source: CaptureSource, declaredUTType _: String?) async throws -> CapturedAsset {
        guard data.count <= ProblemCaptureLimits.maxImportBytes else {
            throw CaptureFailure.assetTooLarge
        }
        guard let imageSource = CGImageSourceCreateWithData(data as CFData, nil) else {
            throw CaptureFailure.corruptAsset
        }
        let detectedUTType = CGImageSourceGetType(imageSource) as String?
        guard isSupportedImageType(detectedUTType) else {
            throw CaptureFailure.unsupportedAsset
        }
        let dimensions = try imageDimensions(from: imageSource)
        guard dimensions.width <= ProblemCaptureLimits.maxPixelDimension,
              dimensions.height <= ProblemCaptureLimits.maxPixelDimension else {
            throw CaptureFailure.dimensionTooLarge
        }
        guard let normalizedImage = UIImage(data: data)?.problemCaptureNormalizedImage(),
              let sanitizedData = normalizedImage.jpegData(compressionQuality: 0.92),
              let previewData = normalizedImage.problemCapturePreviewData(maxDimension: ProblemCaptureLimits.previewMaxPixelDimension) else {
            throw CaptureFailure.corruptAsset
        }

        try ensureRootDirectory()
        let id = UUID()
        let localURL = rootURL.appendingPathComponent("\(id.uuidString).jpg")
        try sanitizedData.write(to: localURL, options: [.atomic])
        applyFileProtection(to: localURL)

        let storedCGImage = normalizedImage.cgImage
        return CapturedAsset(
            id: id,
            source: source,
            kind: .image,
            localURL: localURL,
            previewData: previewData,
            pixelWidth: storedCGImage?.width ?? dimensions.width,
            pixelHeight: storedCGImage?.height ?? dimensions.height,
            createdAt: now(),
            originalUTType: UTType.jpeg.identifier,
            cropSelection: .fullImage,
            qualityAssessment: .pending
        )
    }

    func storePDF(at url: URL, source: CaptureSource) async throws -> CapturedAsset {
        let hasScope = url.startAccessingSecurityScopedResource()
        defer {
            if hasScope {
                url.stopAccessingSecurityScopedResource()
            }
        }

        let resourceValues = try url.resourceValues(forKeys: [.fileSizeKey, .contentTypeKey])
        guard let fileSize = resourceValues.fileSize,
              fileSize <= ProblemCaptureLimits.maxImportBytes else {
            throw CaptureFailure.assetTooLarge
        }
        guard resourceValues.contentType?.conforms(to: .pdf) == true || url.pathExtension.lowercased() == "pdf" else {
            throw CaptureFailure.unsupportedAsset
        }
        guard let document = PDFDocument(url: url), let page = document.page(at: 0) else {
            throw CaptureFailure.pdfPageUnavailable
        }
        guard let previewImage = renderPreviewImage(for: page),
              let previewData = previewImage.jpegData(compressionQuality: 0.90) else {
            throw CaptureFailure.pdfPageUnavailable
        }

        try ensureRootDirectory()
        let id = UUID()
        let localURL = rootURL.appendingPathComponent("\(id.uuidString).pdf")
        if fileManager.fileExists(atPath: localURL.path) {
            try fileManager.removeItem(at: localURL)
        }
        try fileManager.copyItem(at: url, to: localURL)
        applyFileProtection(to: localURL)

        let bounds = page.bounds(for: .mediaBox)
        return CapturedAsset(
            id: id,
            source: source,
            kind: .pdf,
            localURL: localURL,
            previewData: previewData,
            pixelWidth: Int(bounds.width.rounded(.toNearestOrAwayFromZero)),
            pixelHeight: Int(bounds.height.rounded(.toNearestOrAwayFromZero)),
            createdAt: now(),
            originalUTType: UTType.pdf.identifier,
            cropSelection: .fullImage,
            qualityAssessment: .pending
        )
    }

    func delete(_ asset: CapturedAsset) async {
        try? fileManager.removeItem(at: asset.localURL)
    }

    func cleanUpExpiredAssets() async {
        guard let contents = try? fileManager.contentsOfDirectory(
            at: rootURL,
            includingPropertiesForKeys: [.contentModificationDateKey],
            options: [.skipsHiddenFiles]
        ) else {
            return
        }

        let expiryDate = now().addingTimeInterval(-ProblemCaptureLimits.temporaryAssetTimeToLive)
        for fileURL in contents {
            let modificationDate = (try? fileURL.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            if modificationDate < expiryDate {
                try? fileManager.removeItem(at: fileURL)
            }
        }
    }

    private func ensureRootDirectory() throws {
        if !fileManager.fileExists(atPath: rootURL.path) {
            try fileManager.createDirectory(at: rootURL, withIntermediateDirectories: true)
        }
        applyFileProtection(to: rootURL)
    }

    private func imageDimensions(from source: CGImageSource) throws -> (width: Int, height: Int) {
        guard let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = properties[kCGImagePropertyPixelWidth] as? Int,
              let height = properties[kCGImagePropertyPixelHeight] as? Int else {
            throw CaptureFailure.corruptAsset
        }
        return (width, height)
    }

    private func isSupportedImageType(_ typeIdentifier: String?) -> Bool {
        guard let typeIdentifier, let type = UTType(typeIdentifier) else {
            return false
        }
        return type.conforms(to: .jpeg) ||
        type.conforms(to: .png) ||
        type.identifier == UTType.heic.identifier ||
        type.identifier == "public.heif"
    }

    private func renderPreviewImage(for page: PDFPage) -> UIImage? {
        let bounds = page.bounds(for: .mediaBox)
        guard bounds.width > 0, bounds.height > 0 else {
            return nil
        }
        let maxSide = CGFloat(ProblemCaptureLimits.previewMaxPixelDimension)
        let scale = min(maxSide / max(bounds.width, bounds.height), 2)
        let size = CGSize(width: bounds.width * scale, height: bounds.height * scale)

        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { context in
            UIColor.white.setFill()
            context.fill(CGRect(origin: .zero, size: size))
            context.cgContext.saveGState()
            context.cgContext.translateBy(x: 0, y: size.height)
            context.cgContext.scaleBy(x: scale, y: -scale)
            page.draw(with: .mediaBox, to: context.cgContext)
            context.cgContext.restoreGState()
        }
    }

    private func applyFileProtection(to url: URL) {
        try? fileManager.setAttributes(
            [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
            ofItemAtPath: url.path
        )
    }
}

private extension UIImage {
    func problemCaptureNormalizedImage() -> UIImage {
        if imageOrientation == .up {
            return self
        }

        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        return renderer.image { _ in
            draw(in: CGRect(origin: .zero, size: size))
        }
    }

    func problemCapturePreviewData(maxDimension: Int) -> Data? {
        let largestSide = max(size.width, size.height)
        guard largestSide > 0 else {
            return nil
        }
        let scale = min(1, CGFloat(maxDimension) / largestSide)
        let previewSize = CGSize(width: size.width * scale, height: size.height * scale)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: previewSize, format: format)
        let preview = renderer.image { _ in
            draw(in: CGRect(origin: .zero, size: previewSize))
        }
        return preview.jpegData(compressionQuality: 0.86)
    }
}
