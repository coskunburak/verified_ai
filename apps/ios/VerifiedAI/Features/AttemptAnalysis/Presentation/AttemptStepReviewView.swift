import SwiftUI

struct AttemptStepReviewView: View {
    @Binding var lines: [AttemptRecognizedLine]
    let preview: Data?
    let confirm: () -> Void
    let cancel: () -> Void
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
                        Text(verbatim: line.raw).font(.caption).textSelection(.enabled)
                        TextField("attempt.editor.label", text: $line.text, axis: .vertical)
                            .autocorrectionDisabled().textInputAutocapitalization(.never)
                            .onChange(of: line.text) { _, _ in line.confirmed = false }
                        if line.confidence < 0.85 { Label("attempt.ocr.lowConfidence", systemImage: "exclamationmark.triangle") }
                        Toggle("attempt.ocr.lineConfirmed", isOn: $line.confirmed)
                    }
                }
                Button("attempt.ocr.confirm", action: confirm)
                    .disabled(lines.isEmpty || lines.contains { !$0.confirmed || $0.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || $0.text.utf16.count > 256 || $0.text.contains("\n") })
            }
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
