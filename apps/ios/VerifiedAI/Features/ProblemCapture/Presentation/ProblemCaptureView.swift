@preconcurrency import AVFoundation
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers
import UIKit

struct ProblemCaptureView: View {
    @Bindable var viewModel: ProblemCaptureViewModel
    @Bindable var uploadViewModel: ProblemAssetUploadViewModel
    let cameraClient: ProblemCameraClient
    let onDismiss: () -> Void

    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var isFileImporterPresented = false

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Problem Capture")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button {
                            Task {
                                uploadViewModel.cancel()
                                await viewModel.cancel()
                                onDismiss()
                            }
                        } label: {
                            Image(systemName: "xmark")
                        }
                        .accessibilityLabel("Close problem capture")
                    }
                }
        }
        .task {
            if viewModel.state == .idle {
                viewModel.open()
            }
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--ui-testing-problem-capture-review"),
               viewModel.state == .selectingSource {
                await viewModel.importPhotoData(
                    ProblemCaptureUITestAssetFactory.jpegData(),
                    declaredUTType: UTType.jpeg.identifier
                )
            }
            #endif
        }
        .onChange(of: scenePhase) { _, phase in
            viewModel.handleScenePhase(phase)
        }
        .onChange(of: selectedPhotoItem) { _, item in
            guard let item else {
                return
            }
            Task {
                viewModel.beginPhotoImport()
                let data = try? await item.loadTransferable(type: Data.self)
                await viewModel.importPhotoData(data, declaredUTType: item.supportedContentTypes.first?.identifier)
                selectedPhotoItem = nil
            }
        }
        .fileImporter(
            isPresented: $isFileImporterPresented,
            allowedContentTypes: [.pdf, .jpeg, .png, UTType.heic],
            allowsMultipleSelection: false
        ) { result in
            Task {
                switch result {
                case .success(let urls):
                    let url = urls.first
                    let contentType = url.flatMap { try? $0.resourceValues(forKeys: [.contentTypeKey]).contentType }
                    await viewModel.importDocument(at: url, contentType: contentType)
                case .failure:
                    await viewModel.importDocument(at: nil, contentType: nil)
                }
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .idle, .selectingSource:
            sourceSelection
        case .requestingCameraPermission:
            progressView("Requesting camera access")
        case .cameraReady, .capturing:
            cameraCaptureView
        case .importing(let source):
            progressView("Opening \(sourceTitle(source))")
        case .processingLocalAsset:
            progressView("Preparing capture")
        case .reviewing(let asset):
            reviewView(asset)
        case .editingCrop(let asset):
            CropAdjustmentView(asset: asset, viewModel: viewModel)
        case .readyForHandoff(let acceptedAsset):
            acceptedView(acceptedAsset)
        case .recoverableFailure(let failure, let previousAsset):
            failureView(failure, previousAsset: previousAsset)
        case .terminalFailure(let failure):
            failureView(failure, previousAsset: nil)
        }
    }

    private var sourceSelection: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                Text("Scan or import a math problem")
                    .font(TypographyTokens.title)
                    .foregroundStyle(ColorTokens.textPrimary)

                Text("Keep the full problem visible. You can review, crop, retake, or replace it before the next step.")
                    .font(TypographyTokens.body)
                    .foregroundStyle(ColorTokens.textSecondary)

                VStack(spacing: SpacingTokens.md) {
                    Button {
                        Task { await viewModel.chooseCamera() }
                    } label: {
                        ProblemCaptureSourceRow(
                            icon: "camera.viewfinder",
                            title: "Camera",
                            subtitle: "Capture a problem with framing guidance."
                        )
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("problemCapture.source.camera")

                    PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                        ProblemCaptureSourceRow(
                            icon: "photo.on.rectangle",
                            title: "Photo Library",
                            subtitle: "Choose an image with scoped picker access."
                        )
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("problemCapture.source.photoLibrary")

                    Button {
                        viewModel.beginDocumentImport()
                        isFileImporterPresented = true
                    } label: {
                        ProblemCaptureSourceRow(
                            icon: "doc.viewfinder",
                            title: "Files or PDF",
                            subtitle: "Import a supported image or PDF file."
                        )
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("problemCapture.source.file")
                }
            }
            .padding(SpacingTokens.lg)
            .frame(maxWidth: 680, alignment: .leading)
        }
        .background(ColorTokens.background)
    }

    private var cameraCaptureView: some View {
        VStack(spacing: 0) {
            ZStack {
                if let avClient = cameraClient as? AVFoundationProblemCameraClient {
                    CameraPreviewView(session: avClient.session)
                        .accessibilityHidden(true)
                } else {
                    Color.black
                        .overlay {
                            Label("Camera preview unavailable", systemImage: "camera")
                                .foregroundStyle(.white)
                        }
                }

                CaptureGuideOverlay()
                    .padding(SpacingTokens.lg)
            }
            .frame(maxWidth: .infinity)
            .aspectRatio(3 / 4, contentMode: .fit)
            .background(.black)
            .accessibilityElement(children: .combine)
            .accessibilityLabel("Camera preview")
            .accessibilityHint("Place the full math problem inside the guide, then use the shutter button.")

            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                Text("Keep the entire problem inside the frame. Avoid glare and cut-off edges.")
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)

                HStack(spacing: SpacingTokens.md) {
                    PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                        Label("Photo", systemImage: "photo")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .disabled(viewModel.isBusy)

                    Button {
                        Task { await viewModel.capturePhoto() }
                    } label: {
                        Label("Capture", systemImage: "circle.inset.filled")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(viewModel.isBusy)
                    .accessibilityIdentifier("problemCapture.shutter")

                    Button {
                        viewModel.beginDocumentImport()
                        isFileImporterPresented = true
                    } label: {
                        Label("Files", systemImage: "doc")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .disabled(viewModel.isBusy)
                }
            }
            .padding(SpacingTokens.lg)
            .background(ColorTokens.background)
        }
        .background(ColorTokens.background)
    }

    private func reviewView(_ asset: CapturedAsset) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                preview(asset)
                    .accessibilityIdentifier("problemCapture.review.preview")

                VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                    Text("Review capture")
                        .font(TypographyTokens.title)
                        .foregroundStyle(ColorTokens.textPrimary)
                    Text("\(sourceTitle(asset.source)) · \(asset.dimensionsDescription)")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }

                qualitySection(asset)

                VStack(spacing: SpacingTokens.md) {
                    Button {
                        viewModel.editCrop()
                    } label: {
                        Label("Edit Crop", systemImage: "crop")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("problemCapture.review.editCrop")

                    Button {
                        viewModel.accept()
                    } label: {
                        Label("Use This Problem", systemImage: "checkmark.circle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier("problemCapture.review.accept")

                    HStack(spacing: SpacingTokens.md) {
                        Button {
                            Task { await viewModel.retake() }
                        } label: {
                            Label("Retake", systemImage: "arrow.counterclockwise")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)

                        Button {
                            Task { await viewModel.replaceAsset() }
                        } label: {
                            Label("Replace", systemImage: "square.and.arrow.down")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }
            .padding(SpacingTokens.lg)
            .frame(maxWidth: 720, alignment: .leading)
        }
        .background(ColorTokens.background)
    }

    private func qualitySection(_ asset: CapturedAsset) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            if asset.qualityAssessment.issues.isEmpty {
                Label("Capture looks usable for the next step.", systemImage: "checkmark.circle")
                    .font(TypographyTokens.body)
                    .foregroundStyle(ColorTokens.action)
                    .accessibilityIdentifier("problemCapture.quality.ok")
            } else {
                ForEach(asset.qualityAssessment.issues) { issue in
                    QualityIssueBanner(issue: issue)
                }
                Text("You can retake, edit the crop, or continue with this warning.")
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
            }
        }
    }

    private func failureView(_ failure: CaptureFailure, previousAsset: CapturedAsset?) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.lg) {
            Label(viewModel.message ?? "Capture could not continue.", systemImage: "exclamationmark.triangle")
                .font(TypographyTokens.body.weight(.semibold))
                .foregroundStyle(ColorTokens.warning)
                .accessibilityIdentifier("problemCapture.failure.message")

            VStack(spacing: SpacingTokens.md) {
                if failure == .permissionDenied {
                    Button {
                        openURL(URL(string: UIApplication.openSettingsURLString)!)
                    } label: {
                        Label("Open Settings", systemImage: "gear")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }

                PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                    Label("Choose Photo", systemImage: "photo")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)

                Button {
                    viewModel.beginDocumentImport()
                    isFileImporterPresented = true
                } label: {
                    Label("Choose File", systemImage: "doc")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)

                Button {
                    Task { await viewModel.chooseCamera() }
                } label: {
                    Label("Try Camera Again", systemImage: "camera")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)

                if previousAsset != nil {
                    Button {
                        viewModel.editCrop()
                    } label: {
                        Label("Return to Review", systemImage: "arrow.uturn.left")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
        .padding(SpacingTokens.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(ColorTokens.background)
    }

    private func acceptedView(_ acceptedAsset: AcceptedCapturedAsset) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.lg) {
            Text("\(sourceTitle(acceptedAsset.asset.source)) · \(acceptedAsset.asset.dimensionsDescription)")
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)

            switch uploadViewModel.state {
            case .idle, .reserving:
                Label("Reserving secure upload", systemImage: "lock")
                    .font(TypographyTokens.title)
                    .foregroundStyle(ColorTokens.textPrimary)
                ProgressView()
                    .accessibilityIdentifier("problemCapture.upload.reserving")
                Text("The backend is creating a private asset reservation.")
                    .font(TypographyTokens.body)
                    .foregroundStyle(ColorTokens.textSecondary)
            case .uploading(let progress):
                Label("Uploading problem asset", systemImage: "arrow.up.doc")
                    .font(TypographyTokens.title)
                    .foregroundStyle(ColorTokens.textPrimary)
                ProgressView(value: progress)
                    .accessibilityIdentifier("problemCapture.upload.progress")
                Text("\(Int(progress * 100))%")
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
            case .confirming:
                Label("Verifying upload", systemImage: "checkmark.shield")
                    .font(TypographyTokens.title)
                    .foregroundStyle(ColorTokens.textPrimary)
                ProgressView()
                    .accessibilityIdentifier("problemCapture.upload.confirming")
                Text("The backend is checking object size, content type, and SHA-256 integrity.")
                    .font(TypographyTokens.body)
                    .foregroundStyle(ColorTokens.textSecondary)
            case .preprocessing:
                Label("Preparing capture", systemImage: "wand.and.stars")
                    .font(TypographyTokens.title)
                    .foregroundStyle(ColorTokens.textPrimary)
                ProgressView()
                    .accessibilityIdentifier("problemCapture.preprocessing.progress")
                Text("The backend is creating a recognition-ready derivative and checking capture quality.")
                    .font(TypographyTokens.body)
                    .foregroundStyle(ColorTokens.textSecondary)
            case .available(let reference):
                Label("Problem asset ready", systemImage: "checkmark.seal")
                    .font(TypographyTokens.title)
                    .foregroundStyle(ColorTokens.action)
                Text("Asset \(reference.durableAsset.problemAssetId.uuidString) is available for the next ingestion step.")
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                if let derivativeId = reference.preprocessing.preferredRecognitionDerivativeId {
                    Text("Recognition input \(derivativeId.uuidString)")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }

                Button {
                    onDismiss()
                } label: {
                    Label("Done", systemImage: "checkmark")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("problemCapture.accepted.done")
            case .preprocessingWarning(let reference, let acceptedAsset):
                Label(uploadViewModel.message ?? "Capture quality needs review.", systemImage: "exclamationmark.triangle")
                    .font(TypographyTokens.body.weight(.semibold))
                    .foregroundStyle(ColorTokens.warning)
                    .accessibilityIdentifier("problemCapture.preprocessing.warning")
                ForEach(reference.preprocessing.warningSignals) { signal in
                    Label(qualitySignalTitle(signal), systemImage: "exclamationmark.circle")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
                VStack(spacing: SpacingTokens.md) {
                    Button {
                        uploadViewModel.cancel()
                        viewModel.returnToCrop(acceptedAsset.asset)
                    } label: {
                        Label("Edit Crop", systemImage: "crop")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)

                    Button {
                        Task {
                            uploadViewModel.cancel()
                            await viewModel.retake()
                        }
                    } label: {
                        Label("Retake", systemImage: "arrow.counterclockwise")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)

                    Button {
                        Task { await uploadViewModel.continueWithWarning() }
                    } label: {
                        Label("Continue", systemImage: "checkmark")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier("problemCapture.preprocessing.continueWarning")
                }
            case .preprocessingFailed(_, let preprocessing, let acceptedAsset):
                Label(uploadViewModel.message ?? "Preprocessing could not continue.", systemImage: "exclamationmark.triangle")
                    .font(TypographyTokens.body.weight(.semibold))
                    .foregroundStyle(ColorTokens.warning)
                    .accessibilityIdentifier("problemCapture.preprocessing.failure")
                Text(preprocessing?.failureCode ?? "Your local capture is still available.")
                    .font(TypographyTokens.body)
                    .foregroundStyle(ColorTokens.textSecondary)
                VStack(spacing: SpacingTokens.md) {
                    Button {
                        Task { await uploadViewModel.retry() }
                    } label: {
                        Label("Retry", systemImage: "arrow.clockwise")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)

                    Button {
                        uploadViewModel.cancel()
                        viewModel.returnToCrop(acceptedAsset.asset)
                    } label: {
                        Label("Edit Crop", systemImage: "crop")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)

                    Button {
                        Task {
                            uploadViewModel.cancel()
                            await viewModel.retake()
                        }
                    } label: {
                        Label("Retake", systemImage: "arrow.counterclockwise")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
            case .recoverableFailure(let failure, _):
                Label(uploadViewModel.message ?? "Upload could not continue.", systemImage: "exclamationmark.triangle")
                    .font(TypographyTokens.body.weight(.semibold))
                    .foregroundStyle(ColorTokens.warning)
                    .accessibilityIdentifier("problemCapture.upload.failure")
                Text("Your local capture is still available for retry.")
                    .font(TypographyTokens.body)
                    .foregroundStyle(ColorTokens.textSecondary)
                Button {
                    Task { await uploadViewModel.retry() }
                } label: {
                    Label(retryLabel(for: failure), systemImage: "arrow.clockwise")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("problemCapture.upload.retry")
            }
        }
        .padding(SpacingTokens.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(ColorTokens.background)
        .task(id: acceptedAsset.localIdentifier) {
            if uploadViewModel.state == .idle {
                await uploadViewModel.start(acceptedAsset)
            }
        }
    }

    private func progressView(_ title: String) -> some View {
        VStack(spacing: SpacingTokens.md) {
            ProgressView()
            Text(title)
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(ColorTokens.background)
    }

    private func preview(_ asset: CapturedAsset) -> some View {
        Group {
            if let image = UIImage(data: asset.previewData) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .overlay {
                        GeometryReader { geometry in
                            let imageSize = CGSize(width: asset.pixelWidth, height: asset.pixelHeight)
                            let cropRect = CropGeometryMapper.displayRect(
                                from: asset.cropSelection,
                                imageSize: imageSize,
                                containerSize: geometry.size,
                                contentMode: .aspectFit
                            )
                            Rectangle()
                                .path(in: cropRect)
                                .stroke(ColorTokens.action, lineWidth: 3)
                                .accessibilityHidden(true)
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
            } else {
                ColorTokens.surface
                    .overlay {
                        Label("Preview unavailable", systemImage: "photo")
                    }
            }
        }
        .frame(maxWidth: .infinity)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
        .accessibilityLabel("Captured problem preview")
    }

    private func sourceTitle(_ source: CaptureSource) -> String {
        switch source {
        case .camera:
            "Camera"
        case .photoLibrary:
            "Photo Library"
        case .file:
            "File"
        case .pdf:
            "PDF"
        }
    }

    private func qualitySignalTitle(_ signal: ProblemAssetQualitySignal) -> String {
        switch signal.signalType {
        case "BLUR":
            "Blur warning"
        case "GLARE":
            "Glare warning"
        case "CROP_FRAMING":
            "Crop or framing warning"
        case "CONTRAST_READABILITY":
            "Contrast warning"
        case "RESOLUTION":
            "Resolution warning"
        default:
            "Capture quality warning"
        }
    }

    private func retryLabel(for failure: ProblemAssetUploadFailure) -> String {
        if case .preprocessingFailed = failure {
            return "Retry Preprocessing"
        }
        return "Retry Upload"
    }
}

#if DEBUG
private enum ProblemCaptureUITestAssetFactory {
    static func jpegData() -> Data {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 900, height: 1_200))
        let image = renderer.image { context in
            UIColor.white.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 900, height: 1_200))

            UIColor.black.setStroke()
            let problemFrame = CGRect(x: 120, y: 220, width: 660, height: 520)
            UIBezierPath(rect: problemFrame).stroke()

            let paragraph = NSMutableParagraphStyle()
            paragraph.alignment = .left
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 52, weight: .semibold),
                .foregroundColor: UIColor.black,
                .paragraphStyle: paragraph
            ]
            "Solve: 2x + 5 = 17\nShow each step.".draw(
                in: problemFrame.insetBy(dx: 36, dy: 44),
                withAttributes: attributes
            )

            UIColor.darkGray.setStroke()
            UIBezierPath(rect: CGRect(x: 150, y: 790, width: 600, height: 120)).stroke()
        }
        return image.jpegData(compressionQuality: 0.9) ?? Data([0xFF, 0xD8, 0xFF, 0xD9])
    }
}
#endif

private struct ProblemCaptureSourceRow: View {
    let icon: String
    let title: String
    let subtitle: String

    var body: some View {
        HStack(spacing: SpacingTokens.md) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(ColorTokens.action)
                .frame(width: 36)
            VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                Text(title)
                    .font(TypographyTokens.body.weight(.semibold))
                    .foregroundStyle(ColorTokens.textPrimary)
                Text(subtitle)
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .foregroundStyle(ColorTokens.textSecondary)
        }
        .padding(SpacingTokens.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}

private struct CameraPreviewView: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.videoGravity = .resizeAspectFill
        view.previewLayer.session = session
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {
        uiView.previewLayer.session = session
    }

    final class PreviewView: UIView {
        override class var layerClass: AnyClass {
            AVCaptureVideoPreviewLayer.self
        }

        var previewLayer: AVCaptureVideoPreviewLayer {
            layer as! AVCaptureVideoPreviewLayer
        }
    }
}

private struct CaptureGuideOverlay: View {
    var body: some View {
        RoundedRectangle(cornerRadius: RadiusTokens.medium)
            .strokeBorder(style: StrokeStyle(lineWidth: 3, dash: [10, 8]))
            .foregroundStyle(.white)
            .overlay(alignment: .topLeading) {
                Text("Fit the full problem here")
                    .font(TypographyTokens.caption.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(SpacingTokens.sm)
                    .background(.black.opacity(0.55))
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))
                    .padding(SpacingTokens.sm)
            }
            .accessibilityHidden(true)
    }
}

private struct QualityIssueBanner: View {
    let issue: CaptureQualityIssue

    var body: some View {
        Label(title, systemImage: icon)
            .font(TypographyTokens.body)
            .foregroundStyle(ColorTokens.warning)
            .padding(SpacingTokens.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ColorTokens.surface)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
            .accessibilityIdentifier("problemCapture.quality.\(issue.kind.rawValue)")
    }

    private var title: String {
        switch issue.kind {
        case .blur:
            "The image may be blurry."
        case .glare:
            "Glare may make part of the problem hard to read."
        case .framing:
            "Some content may be too close to the edge."
        }
    }

    private var icon: String {
        switch issue.severity {
        case .informational:
            "info.circle"
        case .warning:
            "exclamationmark.triangle"
        case .blocking:
            "xmark.octagon"
        }
    }
}

private struct CropAdjustmentView: View {
    let asset: CapturedAsset
    @Bindable var viewModel: ProblemCaptureViewModel

    @State private var left: Double
    @State private var top: Double
    @State private var right: Double
    @State private var bottom: Double

    init(asset: CapturedAsset, viewModel: ProblemCaptureViewModel) {
        self.asset = asset
        self.viewModel = viewModel
        _left = State(initialValue: asset.cropSelection.x)
        _top = State(initialValue: asset.cropSelection.y)
        _right = State(initialValue: asset.cropSelection.x + asset.cropSelection.width)
        _bottom = State(initialValue: asset.cropSelection.y + asset.cropSelection.height)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                preview

                Text("Adjust crop")
                    .font(TypographyTokens.title)
                    .foregroundStyle(ColorTokens.textPrimary)

                VStack(alignment: .leading, spacing: SpacingTokens.md) {
                    cropSlider("Left edge", value: $left, range: 0...(right - ProblemCaptureLimits.minimumCropSide))
                    cropSlider("Right edge", value: $right, range: (left + ProblemCaptureLimits.minimumCropSide)...1)
                    cropSlider("Top edge", value: $top, range: 0...(bottom - ProblemCaptureLimits.minimumCropSide))
                    cropSlider("Bottom edge", value: $bottom, range: (top + ProblemCaptureLimits.minimumCropSide)...1)
                }
                .onChange(of: left) { _, _ in applyCrop() }
                .onChange(of: right) { _, _ in applyCrop() }
                .onChange(of: top) { _, _ in applyCrop() }
                .onChange(of: bottom) { _, _ in applyCrop() }

                HStack(spacing: SpacingTokens.md) {
                    Button {
                        left = 0
                        top = 0
                        right = 1
                        bottom = 1
                        viewModel.useFullImageCrop()
                    } label: {
                        Label("Full Image", systemImage: "rectangle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)

                    Button {
                        viewModel.finishCropEditing()
                    } label: {
                        Label("Save Crop", systemImage: "checkmark")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier("problemCapture.crop.save")
                }
            }
            .padding(SpacingTokens.lg)
            .frame(maxWidth: 720, alignment: .leading)
        }
        .background(ColorTokens.background)
    }

    private var preview: some View {
        Group {
            if let image = UIImage(data: asset.previewData) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .overlay {
                        GeometryReader { geometry in
                            Rectangle()
                                .path(in: CropGeometryMapper.displayRect(
                                    from: currentCrop,
                                    imageSize: CGSize(width: asset.pixelWidth, height: asset.pixelHeight),
                                    containerSize: geometry.size,
                                    contentMode: .aspectFit
                                ))
                                .stroke(ColorTokens.action, lineWidth: 3)
                        }
                    }
            } else {
                Label("Preview unavailable", systemImage: "photo")
            }
        }
        .frame(maxWidth: .infinity)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
        .accessibilityLabel("Crop preview")
    }

    private var currentCrop: CropSelection {
        CropSelection(x: left, y: top, width: right - left, height: bottom - top).clamped()
    }

    private func cropSlider(_ title: String, value: Binding<Double>, range: ClosedRange<Double>) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.xs) {
            Text(title)
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
            Slider(value: value, in: range)
                .accessibilityLabel(title)
                .accessibilityValue("\(Int(value.wrappedValue * 100)) percent")
        }
    }

    private func applyCrop() {
        viewModel.updateCrop(currentCrop)
    }
}
