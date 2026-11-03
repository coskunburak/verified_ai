import SwiftUI

struct AnalyzeMyWorkCaptureView: View {
    @State var viewModel: AnalyzeMyWorkViewModel
    var tutor: TutorViewModel? = nil
    @State private var showingCapture = false
    @State private var gatewayCapture = false
    @Environment(\.scenePhase) private var scenePhase
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                if viewModel.isFixture { Label("solve.fixture", systemImage: "testtube.2") }
                Text("attempt.scope").font(.callout)
                if let original = viewModel.context?.originalStatement {
                    AttemptMathView(json: original, fallback: NSLocalizedString("attempt.math.expression", comment: "Original mathematical statement"))
                }
                if let capability = viewModel.context?.handwriting,
                   ["NATIVE_REVIEW", "NATIVE_REVIEW_AND_GATEWAY"].contains(capability), let capture = viewModel.capture {
                    Button("attempt.photo.open") { gatewayCapture = false; capture.capture.open(); showingCapture = true }
                        .disabled(viewModel.busy || viewModel.pending != nil)
                        .accessibilityIdentifier("attempt.photo.open")
                } else if viewModel.context != nil {
                    Text("attempt.handwriting.unavailable")
                        .accessibilityIdentifier("attempt.handwriting.unavailable")
                }
                if viewModel.context?.handwriting == "NATIVE_REVIEW_AND_GATEWAY", let capture = viewModel.capture {
                    Button("attempt.photo.gateway") { gatewayCapture = true; capture.upload.cancel(); capture.capture.open(); showingCapture = true }
                        .disabled(viewModel.busy || viewModel.pending != nil)
                }
                Toggle("attempt.domain.confirmReal", isOn: $viewModel.realDomainConfirmed)
                    .disabled(viewModel.busy || viewModel.pending != nil)
                if let tutor { NavigationLink("tutor.title") { TutorView(viewModel: tutor) } }
                if viewModel.context?.enabled == false { Text("attempt.unavailable") }
                AttemptStepEditorView(text: $viewModel.draft, locked: viewModel.pending != nil || viewModel.busy)
                if viewModel.context != nil && !viewModel.payloadFits { Text("attempt.payloadTooLarge").foregroundStyle(.secondary) }
                Button("attempt.submit") { viewModel.submit() }
                    .buttonStyle(.borderedProminent).frame(minHeight: 44)
                    .disabled(!viewModel.canSubmit).accessibilityIdentifier("attempt.submit")
                if viewModel.busy { ProgressView().accessibilityLabel("attempt.loading") }
                if let error = viewModel.errorKey {
                    Label(LocalizedStringKey(error), systemImage: "exclamationmark.triangle")
                        .accessibilityIdentifier("attempt.error")
                }
                Button(LocalizedStringKey(viewModel.pending == nil ? "attempt.refresh" : "attempt.retry.button")) { viewModel.retry() }
                    .frame(minHeight: 44).disabled(viewModel.busy).accessibilityIdentifier("attempt.retry")
                if let result = viewModel.result {
                    AttemptAnalysisResultsView(attempt: result)
                    if let learning = viewModel.learning { AttemptLearningPanel(model: learning, attempt: result) }
                }
                ForEach(viewModel.history) { attempt in
                    Button { viewModel.select(attempt) } label: {
                        VStack(alignment: .leading) {
                            Text("attempt.history")
                            HStack { Text(attempt.createdAt, style: .date); Text(attempt.createdAt, style: .time) }
                        }
                    }.frame(minHeight: 44)
                }
            }.padding()
        }
        .fullScreenCover(isPresented: $showingCapture) {
            if let c = viewModel.capture {
                ProblemCaptureView(viewModel: c.capture, uploadViewModel: c.upload, problemReviewViewModel: c.review,
                    cameraClient: c.camera, onDismiss: { showingCapture = false }, onRecognizedWork: gatewayCapture ? { reference in
                        await viewModel.extractGateway(reference)
                        await c.capture.cancel()
                        showingCapture = false
                    } : nil, onLocalAcceptance: gatewayCapture ? nil : { accepted in
                        await viewModel.extract(accepted.asset)
                        await c.capture.cancel()
                        showingCapture = false
                    })
            }
        }
        .sheet(isPresented: Binding(get: { !viewModel.recognizedLines.isEmpty && !showingCapture }, set: { _ in })) {
            AttemptStepReviewView(lines: $viewModel.recognizedLines, preview: viewModel.reviewPreview, confirm: { viewModel.acceptRecognizedLines() }, cancel: { viewModel.cancelReview() })
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle("attempt.title")
        .task { viewModel.resume() }
        .onChange(of: viewModel.draft) { _, _ in viewModel.saveDraft() }
        .onChange(of: viewModel.realDomainConfirmed) { _, _ in viewModel.saveDraft() }
        .onDisappear { if !showingCapture { viewModel.suspend() } }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { viewModel.resume() } else { viewModel.suspend() }
        }
        .accessibilityIdentifier("attempt.screen")
    }
}
