import SwiftUI

struct AnalyzeMyWorkCaptureView: View {
    @State var viewModel: AnalyzeMyWorkViewModel
    var tutor: TutorViewModel? = nil
    @State private var showingCapture = false
    @State private var gatewayCapture = false
    @State private var reviewingSubmission = false
    @FocusState private var answerFocused: Bool
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
                Toggle("attempt.answer.mode", isOn: $viewModel.answerMode)
                    .disabled(viewModel.busy || viewModel.pending != nil).accessibilityIdentifier("attempt.answer.mode")
                if viewModel.answerMode {
                    Text("attempt.answer.scope")
                    TextField("attempt.answer.label", text: $viewModel.finalAnswer)
                        .focused($answerFocused).submitLabel(.done).onSubmit { answerFocused = false }
                        .textFieldStyle(.roundedBorder).frame(minHeight: 44).autocorrectionDisabled().textInputAutocapitalization(.never)
                        .disabled(viewModel.busy || viewModel.pending != nil)
                        .accessibilityLabel("attempt.answer.label").accessibilityIdentifier("attempt.answer.input")
                    Text("attempt.answer.reasoningOptional")
                } else {
                    Toggle("attempt.domain.confirmReal", isOn: $viewModel.realDomainConfirmed)
                        .disabled(viewModel.busy || viewModel.pending != nil)
                }
                if viewModel.draftNeedsReview {
                    Text("attempt.draft.sourceChanged")
                    Button("attempt.draft.confirmSource") { viewModel.confirmDraftSource() }
                        .disabled(viewModel.busy).accessibilityIdentifier("attempt.draft.confirmSource")
                }
                if let tutor { NavigationLink("tutor.title") { TutorView(viewModel: tutor) } }
                if viewModel.context?.enabled == false { Text("attempt.unavailable") }
                AttemptStepEditorView(text: $viewModel.draft, locked: viewModel.pending != nil || viewModel.busy)
                if viewModel.context != nil && !viewModel.payloadFits { Text("attempt.payloadTooLarge").foregroundStyle(.secondary) }
                Button(LocalizedStringKey(viewModel.answerMode ? "attempt.answer.review" : "attempt.submit")) {
                    if viewModel.answerMode { reviewingSubmission = true } else { viewModel.submit() }
                }
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
                    if let current = viewModel.context?.source, current != result.source { Text("attempt.history.sourceChanged") }
                    AttemptAnalysisResultsView(attempt: result)
                    if result.finalAnswer == nil, let learning = viewModel.learning { AttemptLearningPanel(model: learning, attempt: result) }
                }
                ForEach(viewModel.history) { attempt in
                    Button { viewModel.select(attempt) } label: {
                        VStack(alignment: .leading) {
                            Text("attempt.history")
                            HStack { Text(attempt.createdAt, style: .date); Text(attempt.createdAt, style: .time) }
                        }
                    }.frame(minHeight: 44).accessibilityIdentifier("attempt.history.\(attempt.id.uuidString)")
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
        .sheet(isPresented: $reviewingSubmission) {
            NavigationStack {
                Form {
                    Section("attempt.answer.label") { Text(verbatim: viewModel.finalAnswer).textSelection(.enabled) }
                    ForEach(Array(viewModel.lines.enumerated()), id: \.offset) { _, line in Text(verbatim: line).textSelection(.enabled) }
                    Text("attempt.answer.scope")
                    Button("attempt.answer.save") { reviewingSubmission = false; viewModel.submit() }
                        .disabled(!viewModel.canSubmit).accessibilityIdentifier("attempt.answer.save")
                }
                .navigationTitle("attempt.answer.review")
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("attempt.ocr.cancel") { reviewingSubmission = false } } }
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle("attempt.title")
        .task { viewModel.resume() }
        .onChange(of: viewModel.draft) { _, _ in viewModel.saveDraft() }
        .onChange(of: viewModel.finalAnswer) { _, _ in viewModel.saveDraft() }
        .onChange(of: viewModel.answerMode) { _, _ in viewModel.saveDraft() }
        .onChange(of: viewModel.realDomainConfirmed) { _, _ in viewModel.saveDraft() }
        .onDisappear { if !showingCapture { viewModel.suspend() } }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { viewModel.resume() } else { viewModel.suspend() }
        }
        .accessibilityIdentifier("attempt.screen")
    }
}
