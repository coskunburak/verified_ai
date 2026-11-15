import SwiftUI

struct AnalyzeMyWorkCaptureView: View {
    @State var viewModel: AnalyzeMyWorkViewModel
    var tutor: TutorViewModel? = nil
    @State private var editingOcr = false
    @State private var ocrStep = 1
    @State private var correctedOcr = ""
    @State private var showingCapture = false
    @State private var gatewayCapture = false
    @State private var reviewingSubmission = false
    @FocusState private var answerFocused: Bool
    @Environment(\.scenePhase) private var scenePhase
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                if viewModel.isFixture {
                    AppNoticeBanner("solve.fixture", systemImage: "testtube.2", tone: .neutral)
                }
                if let original = viewModel.context?.originalStatement {
                    AppSurfaceCard {
                        AttemptMathView(json: original, fallback: NSLocalizedString("attempt.math.expression", comment: "Original mathematical statement"))
                    }
                }
                // Accepted structured work has its own review/revise flow. Keep the empty
                // manual composer out of the result screen until an explicit revision starts.
                if viewModel.workResult == nil || viewModel.workDraft != nil {
                AppSurfaceCard {
                Text("attempt.scope")
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                if let capability = viewModel.context?.handwriting,
                   ["NATIVE_REVIEW", "NATIVE_REVIEW_AND_GATEWAY"].contains(capability), let capture = viewModel.capture {
                    Button("attempt.photo.open") { gatewayCapture = false; capture.capture.open(); showingCapture = true }
                        .buttonStyle(AppSecondaryButtonStyle())
                        .disabled(viewModel.editingLocked)
                        .accessibilityIdentifier("attempt.photo.open")
                } else if viewModel.context != nil {
                    AppNoticeBanner("attempt.handwriting.unavailable", systemImage: "hand.draw", tone: .neutral)
                        .accessibilityIdentifier("attempt.handwriting.unavailable")
                }
                if viewModel.context?.handwriting == "NATIVE_REVIEW_AND_GATEWAY", let capture = viewModel.capture {
                    Button("attempt.photo.gateway") { gatewayCapture = true; capture.upload.cancel(); capture.capture.open(); showingCapture = true }
                        .buttonStyle(AppSecondaryButtonStyle())
                        .disabled(viewModel.editingLocked)
                }
                if viewModel.context?.structuredWorkEnabled == true && viewModel.workDraft == nil {
                    Button("attempt.work.start") { viewModel.startStructured() }
                        .buttonStyle(AppSecondaryButtonStyle())
                        .disabled(viewModel.editingLocked).accessibilityIdentifier("attempt.work.start")
                }
                if viewModel.workDraft == nil {
                Toggle("attempt.answer.mode", isOn: $viewModel.answerMode)
                    .tint(ColorTokens.action)
                    .disabled(viewModel.editingLocked).accessibilityIdentifier("attempt.answer.mode")
                if viewModel.answerMode {
                    Text("attempt.answer.scope")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                    TextField("attempt.answer.label", text: $viewModel.finalAnswer)
                        .focused($answerFocused).submitLabel(.done).onSubmit { answerFocused = false }
                        .padding(SpacingTokens.sm)
                        .background(ColorTokens.surfaceElevated)
                        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous)
                                .stroke(ColorTokens.borderStrong, lineWidth: 1)
                        )
                        .frame(minHeight: 44).autocorrectionDisabled().textInputAutocapitalization(.never)
                        .disabled(viewModel.editingLocked)
                        .accessibilityLabel("attempt.answer.label").accessibilityIdentifier("attempt.answer.input")
                    Text("attempt.answer.reasoningOptional")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textTertiary)
                } else {
                    Toggle("attempt.domain.confirmReal", isOn: $viewModel.realDomainConfirmed)
                        .tint(ColorTokens.action)
                        .disabled(viewModel.editingLocked)
                }
                }
                if viewModel.draftNeedsReview {
                    AppNoticeBanner("attempt.draft.sourceChanged", systemImage: "exclamationmark.triangle", tone: .warning)
                    Button("attempt.draft.confirmSource") { viewModel.confirmDraftSource() }
                        .buttonStyle(AppSecondaryButtonStyle())
                        .disabled(viewModel.busy).accessibilityIdentifier("attempt.draft.confirmSource")
                }
                if let tutor {
                    NavigationLink { TutorView(viewModel: tutor) } label: {
                        HStack {
                            Image(systemName: "bubble.left.and.text.bubble.right")
                            Text("tutor.title")
                        }
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .foregroundStyle(ColorTokens.action)
                        .background(ColorTokens.actionSoft)
                        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                    }
                }
                if viewModel.context?.enabled == false { Text("attempt.unavailable").foregroundStyle(ColorTokens.textSecondary) }
                if viewModel.workDraft != nil { AttemptStructuredEditorView(model: viewModel) }
                else { AttemptStepEditorView(text: $viewModel.draft, locked: viewModel.editingLocked) }
                if viewModel.context != nil && !viewModel.payloadFits { Text("attempt.payloadTooLarge").foregroundStyle(ColorTokens.warning) }
                Button(LocalizedStringKey(viewModel.workDraft != nil ? "attempt.work.save" : viewModel.answerMode ? "attempt.answer.review" : "attempt.submit")) {
                    if viewModel.answerMode && viewModel.workDraft == nil { reviewingSubmission = true } else { viewModel.submit() }
                }
                    .buttonStyle(AppPrimaryButtonStyle())
                    .disabled(!viewModel.canSubmit).accessibilityIdentifier("attempt.submit")
                }
                }
                if viewModel.busy { ProgressView().accessibilityLabel("attempt.loading").accessibilityIdentifier("attempt.loading") }
                if let error = viewModel.errorKey {
                    AppNoticeBanner(LocalizedStringKey(error), systemImage: "exclamationmark.triangle", tone: .warning)
                        .accessibilityIdentifier("attempt.error")
                }
                Button(LocalizedStringKey(viewModel.pending == nil && viewModel.pendingWork == nil ? "attempt.refresh" : "attempt.retry.button")) { viewModel.retry() }
                    .buttonStyle(AppSecondaryButtonStyle())
                    .disabled(viewModel.busy).accessibilityIdentifier("attempt.retry")
                if viewModel.pendingCorrection != nil {
                    AppNoticeBanner("attempt.dna.pending", systemImage: "clock", tone: .neutral)
                    Button("attempt.dna.reviewHistory") { viewModel.reviewCorrectionHistory() }
                        .buttonStyle(AppSecondaryButtonStyle())
                        .disabled(viewModel.busy).accessibilityIdentifier("attempt.dna.reviewHistory")
                }
                if let result = viewModel.result {
                    if let current = viewModel.context?.source, current != result.source { Text("attempt.history.sourceChanged") }
                    if let work = viewModel.workResult, work.attemptId == result.id {
                        AttemptStructuredWorkView(work: work, evaluated: viewModel.evaluation?.status == .completed)
                        if let evaluation = viewModel.evaluation {
                            AttemptEvaluationResultsView(value: evaluation, work: work)
                            if let classification = viewModel.classification {
                                AttemptMistakeResultsView(value: classification)
                                switch viewModel.masteryState {
                                case .hidden: EmptyView()
                                case .loading: ProgressView("attempt.mastery.loading").accessibilityIdentifier("attempt.mastery.loading")
                                case .empty: Text("attempt.mastery.empty").accessibilityIdentifier("attempt.mastery.empty")
                                case .failed:
                                    Text("attempt.mastery.failed")
                                    Button("attempt.mastery.retry") { viewModel.retryMastery() }
                                        .frame(minHeight: 44).disabled(viewModel.busy).accessibilityIdentifier("attempt.mastery.retry")
                                case .evidence(let outcome):
                                    Label(LocalizedStringKey(outcome.labelKey), systemImage: "info.circle")
                                        .fixedSize(horizontal: false, vertical: true)
                                        .accessibilityElement(children: .combine)
                                        .accessibilityIdentifier("attempt.mastery.evidence")
                                    if let skill = viewModel.masterySkill {
                                        SkillRetentionNode(skill: skill)
                                    }
                                }
                                if viewModel.effectiveMistake?.effectiveStatus == "DISPUTED_OR_CORRECTED" {
                                    Label("attempt.dna.excluded", systemImage: "checkmark.shield")
                                        .accessibilityIdentifier("attempt.dna.excluded")
                                }
                                if viewModel.effectiveMistake?.enabled == true {
                                    Button("attempt.dna.dispute") { viewModel.disputeDiagnosis() }
                                        .disabled(!viewModel.canDispute).frame(minHeight: 44)
                                        .accessibilityIdentifier("attempt.dna.dispute")
                                    if work.engine != "MANUAL" {
                                        Button("attempt.dna.correct") {
                                            ocrStep = classification.classification?.firstWrongStepIndex ?? 1
                                            correctedOcr = work.steps[ocrStep - 1].reviewedText
                                            editingOcr = true
                                        }.disabled(!viewModel.canCorrectOcr).frame(minHeight: 44)
                                            .accessibilityIdentifier("attempt.dna.correct")
                                    }
                                }
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
                        AppSurfaceCard(elevated: true) {
                            HStack {
                                Text("attempt.history")
                                    .font(TypographyTokens.cardTitle)
                                    .foregroundStyle(ColorTokens.textPrimary)
                                Spacer()
                                HStack(spacing: SpacingTokens.xs) {
                                    Text(attempt.createdAt, style: .date)
                                    Text(attempt.createdAt, style: .time)
                                }
                                .font(TypographyTokens.caption.monospacedDigit())
                                .foregroundStyle(ColorTokens.textSecondary)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .frame(minHeight: 44)
                    .accessibilityIdentifier("attempt.history.\(attempt.id.uuidString)")
                }
            }.padding(SpacingTokens.lg)
        }
        .foregroundStyle(ColorTokens.textPrimary)
        .tint(ColorTokens.action)
        .background(ColorTokens.canvas)
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
        .sheet(isPresented: $editingOcr) {
            NavigationStack {
                Form {
                    if let work = viewModel.workResult {
                        Picker("attempt.dna.step", selection: $ocrStep) {
                            ForEach(work.steps) { step in Text("\(step.index)").tag(step.index) }
                        }
                        .onChange(of: ocrStep) { _, index in correctedOcr = work.steps[index - 1].reviewedText }
                        TextField("attempt.dna.correctedText", text: $correctedOcr, axis: .vertical)
                            .autocorrectionDisabled().textInputAutocapitalization(.never)
                            .accessibilityIdentifier("attempt.dna.correctedText")
                        Text("attempt.dna.reevaluate")
                        Button("attempt.dna.save") {
                            viewModel.correctOcr(step: ocrStep, text: correctedOcr); editingOcr = false
                        }.disabled(!viewModel.canCorrectOcr || !AttemptSubmission.validText(correctedOcr)
                            || correctedOcr.contains("\n") || correctedOcr.contains("\r") || correctedOcr == work.steps[ocrStep - 1].reviewedText)
                            .accessibilityIdentifier("attempt.dna.save")
                    }
                }
                .navigationTitle("attempt.dna.correct")
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("attempt.ocr.cancel") { editingOcr = false } } }
            }
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

struct SkillRetentionNode: View {
    let skill: MasterySkillsView.Skill
    @ScaledMetric private var diameter = 52.0

    var body: some View {
        if let retention = skill.retentionScore, let confidence = skill.confidence, let due = skill.reassessmentDue {
            VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                HStack {
                    ZStack {
                        Circle().stroke(ColorTokens.border, lineWidth: 3)
                        Circle().trim(from: 0, to: retention).stroke(ColorTokens.action, lineWidth: 4)
                        Circle().inset(by: 9).stroke(ColorTokens.border, lineWidth: 6)
                        Circle().inset(by: 9).trim(from: 0, to: skill.posteriorMastery)
                            .stroke(ColorTokens.textPrimary, lineWidth: 6)
                    }
                    .rotationEffect(.degrees(-90))
                    .frame(width: diameter, height: diameter)
                    .accessibilityHidden(true)
                    Text(LocalizedStringKey("attempt.classification.skill." + skill.canonicalSkillCode))
                        .font(.headline)
                }
                LabeledContent("attempt.temporal.mastery", value: skill.posteriorMastery.formatted(.percent))
                LabeledContent("attempt.temporal.retention", value: retention.formatted(.percent))
                LabeledContent("attempt.temporal.confidence", value: confidence.formatted(.percent))
                Text(due ? LocalizedStringKey("attempt.temporal.due") : LocalizedStringKey("attempt.temporal.notDue"))
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("attempt.mastery.rings." + skill.canonicalSkillCode)
        }
    }
}
