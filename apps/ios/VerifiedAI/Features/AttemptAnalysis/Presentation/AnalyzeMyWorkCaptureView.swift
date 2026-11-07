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
                if let original = viewModel.context?.originalStatement {
                    AttemptMathView(json: original, fallback: NSLocalizedString("attempt.math.expression", comment: "Original mathematical statement"))
                }
                // Accepted structured work has its own review/revise flow. Keep the empty
                // manual composer out of the result screen until an explicit revision starts.
                if viewModel.workResult == nil || viewModel.workDraft != nil {
                Text("attempt.scope").font(.callout)
                if let capability = viewModel.context?.handwriting,
                   ["NATIVE_REVIEW", "NATIVE_REVIEW_AND_GATEWAY"].contains(capability), let capture = viewModel.capture {
                    Button("attempt.photo.open") { gatewayCapture = false; capture.capture.open(); showingCapture = true }
                        .disabled(viewModel.editingLocked)
                        .accessibilityIdentifier("attempt.photo.open")
                } else if viewModel.context != nil {
                    Text("attempt.handwriting.unavailable")
                        .accessibilityIdentifier("attempt.handwriting.unavailable")
                }
                if viewModel.context?.handwriting == "NATIVE_REVIEW_AND_GATEWAY", let capture = viewModel.capture {
                    Button("attempt.photo.gateway") { gatewayCapture = true; capture.upload.cancel(); capture.capture.open(); showingCapture = true }
                        .disabled(viewModel.editingLocked)
                }
                if viewModel.context?.structuredWorkEnabled == true && viewModel.workDraft == nil {
                    Button("attempt.work.start") { viewModel.startStructured() }
                        .disabled(viewModel.editingLocked).accessibilityIdentifier("attempt.work.start")
                }
                if viewModel.workDraft == nil {
                Toggle("attempt.answer.mode", isOn: $viewModel.answerMode)
                    .disabled(viewModel.editingLocked).accessibilityIdentifier("attempt.answer.mode")
                if viewModel.answerMode {
                    Text("attempt.answer.scope")
                    TextField("attempt.answer.label", text: $viewModel.finalAnswer)
                        .focused($answerFocused).submitLabel(.done).onSubmit { answerFocused = false }
                        .textFieldStyle(.roundedBorder).frame(minHeight: 44).autocorrectionDisabled().textInputAutocapitalization(.never)
                        .disabled(viewModel.editingLocked)
                        .accessibilityLabel("attempt.answer.label").accessibilityIdentifier("attempt.answer.input")
                    Text("attempt.answer.reasoningOptional")
                } else {
                    Toggle("attempt.domain.confirmReal", isOn: $viewModel.realDomainConfirmed)
                        .disabled(viewModel.editingLocked)
                }
                }
                if viewModel.draftNeedsReview {
                    Text("attempt.draft.sourceChanged")
                    Button("attempt.draft.confirmSource") { viewModel.confirmDraftSource() }
                        .disabled(viewModel.busy).accessibilityIdentifier("attempt.draft.confirmSource")
                }
                if let tutor {
                    NavigationLink { TutorView(viewModel: tutor) } label: {
                        Text("tutor.title").foregroundStyle(ColorTokens.action)
                    }
                    .frame(minHeight: 44)
                }
                if viewModel.context?.enabled == false { Text("attempt.unavailable") }
                if viewModel.workDraft != nil { AttemptStructuredEditorView(model: viewModel) }
                else { AttemptStepEditorView(text: $viewModel.draft, locked: viewModel.editingLocked) }
                if viewModel.context != nil && !viewModel.payloadFits { Text("attempt.payloadTooLarge").foregroundStyle(.secondary) }
                Button(LocalizedStringKey(viewModel.workDraft != nil ? "attempt.work.save" : viewModel.answerMode ? "attempt.answer.review" : "attempt.submit")) {
                    if viewModel.answerMode && viewModel.workDraft == nil { reviewingSubmission = true } else { viewModel.submit() }
                }
                    .buttonStyle(.borderedProminent).frame(minHeight: 44)
                    .disabled(!viewModel.canSubmit).accessibilityIdentifier("attempt.submit")
                }
                if viewModel.busy { ProgressView().accessibilityLabel("attempt.loading").accessibilityIdentifier("attempt.loading") }
                if let error = viewModel.errorKey {
                    Label(LocalizedStringKey(error), systemImage: "exclamationmark.triangle")
                        .accessibilityIdentifier("attempt.error")
                }
                Button(LocalizedStringKey(viewModel.pending == nil && viewModel.pendingWork == nil ? "attempt.refresh" : "attempt.retry.button")) { viewModel.retry() }
                    .frame(minHeight: 44).disabled(viewModel.busy).accessibilityIdentifier("attempt.retry")
                if let result = viewModel.result {
                    if let current = viewModel.context?.source, current != result.source { Text("attempt.history.sourceChanged") }
                    if let work = viewModel.workResult, work.attemptId == result.id {
                        AttemptStructuredWorkView(work: work, evaluated: viewModel.evaluation?.status == .completed)
                        if let evaluation = viewModel.evaluation {
                            AttemptEvaluationResultsView(value: evaluation, work: work)
                            if let classification = viewModel.classification {
                                AttemptMistakeResultsView(value: classification)
                                if classification.creationEnabled && classification.status == .notClassified && !classification.historical {
                                    Button("attempt.classification.action") { viewModel.classifyWork() }
                                        .disabled(!viewModel.canClassify).frame(minHeight: 44)
                                        .accessibilityIdentifier("attempt.classification.action")
                                }
                            }

                            if evaluation.creationEnabled && !evaluation.historical && evaluation.status != .completed {
                                Toggle("attempt.domain.confirmReal", isOn: $viewModel.evaluationRealDomainConfirmed)
                                    .disabled(viewModel.busy || viewModel.pendingEvaluation != nil)
                                    .accessibilityIdentifier("attempt.evaluation.confirmReal")
                                Button("attempt.evaluation.action") { viewModel.evaluateWork() }
                                    .disabled(!viewModel.canEvaluate).frame(minHeight: 44)
                                    .accessibilityIdentifier("attempt.evaluation.action")
                            }
                        }
                        if viewModel.context?.source == work.source && viewModel.context?.structuredWorkEnabled == true {
                            Button("attempt.work.revise") { viewModel.reviseStructured() }.disabled(viewModel.editingLocked)
                                .frame(minHeight: 44).accessibilityIdentifier("attempt.work.revise")
                        }
                    } else { AttemptAnalysisResultsView(attempt: result) }
                    if viewModel.workResult == nil, result.finalAnswer == nil, let learning = viewModel.learning { AttemptLearningPanel(model: learning, attempt: result) }
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
        .foregroundStyle(ColorTokens.textPrimary)
        .tint(ColorTokens.action)
        .background(ColorTokens.background)
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
            AttemptStepReviewView(lines: $viewModel.recognizedLines, preview: viewModel.reviewPreview, confirm: { viewModel.acceptRecognizedLines() }, cancel: { viewModel.cancelReview() }, structured: viewModel.context?.structuredWorkEnabled == true)
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
        .onChange(of: viewModel.workDraft) { _, _ in viewModel.saveDraft() }
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
