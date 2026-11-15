import SwiftUI

struct AttemptStepReviewView: View {
    @Binding var lines: [AttemptRecognizedLine]
    let preview: Data?
    let confirm: () -> Void
    let cancel: () -> Void
    var structured = false
    var canContinue: Bool {
        !lines.isEmpty && lines.count <= 12 && lines.allSatisfy {
            AttemptSubmission.validText($0.text) && (structured || ($0.confirmed && !$0.text.contains("\n")))
        }
    }
    var body: some View {
        NavigationStack {
            Form {
                Text("attempt.ocr.review.help")
                if let preview, let image = UIImage(data: preview) {
                    Image(uiImage: image).resizable().scaledToFit()
                        .accessibilityLabel(Text("attempt.ocr.sourceImage"))
                }
                ForEach($lines) { $line in
                    Section {
                        if line.regionReliable, let preview, let image = UIImage(data: preview) {
                            Image(uiImage: image).resizable().scaledToFit()
                                .overlay { GeometryReader { geometry in
                                    Rectangle().stroke(ColorTokens.warning, lineWidth: 2)
                                        .frame(width: geometry.size.width * line.box.width, height: geometry.size.height * line.box.height)
                                        .position(x: geometry.size.width * line.box.midX, y: geometry.size.height * (1 - line.box.midY))
                                } }
                                .accessibilityLabel("attempt.work.sourceRegion")
                        }
                        if !line.regionReliable { Text("attempt.work.regionUnknown").font(TypographyTokens.caption).foregroundStyle(ColorTokens.textSecondary) }
                        Text(verbatim: line.raw).font(TypographyTokens.caption).textSelection(.enabled)
                        TextField("attempt.editor.label", text: $line.text, axis: .vertical)
                            .autocorrectionDisabled().textInputAutocapitalization(.never)
                            .onChange(of: line.text) { _, _ in line.confirmed = false }
                        if line.confidence < 0.85 { Label("attempt.ocr.lowConfidence", systemImage: "exclamationmark.triangle").foregroundStyle(ColorTokens.warning) }
                        Toggle("attempt.ocr.lineConfirmed", isOn: $line.confirmed).tint(ColorTokens.action)
                    }
                }
                Button(LocalizedStringKey(structured ? "attempt.work.continueReview" : "attempt.ocr.confirm"), action: confirm)
                    .buttonStyle(AppPrimaryButtonStyle())
                    .disabled(!canContinue)
            }
            .scrollContentBackground(.hidden)
            .background(ColorTokens.canvas)
            .navigationTitle("attempt.ocr.review")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("attempt.ocr.cancel", action: cancel) } }
        }
        .interactiveDismissDisabled()
    }
}

@MainActor
struct AttemptCaptureDependencies {
    let capture: ProblemCaptureViewModel
    let upload: ProblemAssetUploadViewModel
    let review: ProblemReviewViewModel
    let camera: ProblemCameraClient
}
