import SwiftUI
struct AttemptStepEditorView: View {
    @Binding var text: String
    let locked: Bool
    @FocusState private var focused: Bool
    var body: some View {
        VStack(alignment: .leading) {
            Text("attempt.editor.help")
            TextEditor(text: $text).font(.body.monospaced()).frame(minHeight: 180)
                .focused($focused).onChange(of: locked) { _, value in if value { focused = false } }
                .autocorrectionDisabled().textInputAutocapitalization(.never).disabled(locked)
                .accessibilityLabel("attempt.editor.label").accessibilityIdentifier("attempt.editor")
        }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("attempt.keyboard.done") { focused = false }
                    .accessibilityIdentifier("attempt.keyboard.done")
            }
        }
    }
}

@MainActor
struct AttemptStructuredEditorView: View {
    @Bindable var model: AnalyzeMyWorkViewModel
    func stepBinding(_ snapshot: AttemptWorkRequest.Step) -> Binding<AttemptWorkRequest.Step> {
        // SwiftUI can read a disappearing control after its backing draft is cleared.
        // Never retain an array index; a retired row reads its last displayed value.
        let source = model.workDraft?.source
        let predecessor = model.workDraft?.supersedesAttemptId
        let isCurrentDraft = { model.workDraft != nil && model.workDraft?.source == source
            && model.workDraft?.supersedesAttemptId == predecessor }
        return Binding(get: { isCurrentDraft() ? model.workSteps.first { $0.id == snapshot.id } ?? snapshot : snapshot },
                       set: { if isCurrentDraft() { model.updateWorkStep($0) } })
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("attempt.work.scope")
            if let preview = model.workPreview, let image = UIImage(data: preview) {
                Image(uiImage: image).resizable().scaledToFit().accessibilityLabel("attempt.ocr.sourceImage")
            }
            if let work = model.workDraft, work.engine != "MANUAL" {
                Text("attempt.work.originalOCR").font(.headline)
                Text(LocalizedStringKey("attempt.work.provenance." + work.engine)).font(.caption)
                ForEach(work.observations) { observation in
                    Text(verbatim: observation.rawText).font(.body.monospaced()).textSelection(.enabled)
                    if (observation.confidence ?? 0) < 0.85 { Label("attempt.ocr.lowConfidence", systemImage: "exclamationmark.triangle") }
                }
            }
            ForEach(model.workSteps) { step in
                let binding = stepBinding(step)
                VStack(alignment: .leading, spacing: 8) {
                    Text("attempt.work.reviewed").font(.headline)
                    TextEditor(text: binding.text).font(.body.monospaced()).frame(minHeight: 90)
                        .autocorrectionDisabled().textInputAutocapitalization(.never)
                        .accessibilityLabel("attempt.work.reviewed").accessibilityIdentifier("attempt.work.step.\(step.id)")
                    Picker("attempt.work.role", selection: binding.role) {
                        ForEach(AttemptWorkRequest.Role.allCases, id: \.self) { role in Text(LocalizedStringKey("attempt.work.role." + role.rawValue)).tag(role) }
                    }
                    Toggle("attempt.ocr.lineConfirmed", isOn: binding.confirmed)
                    if !step.confirmed || step.role == .ambiguous || step.text.contains("\n") {
                        Label("attempt.work.REVIEW_REQUIRED", systemImage: "exclamationmark.triangle")
                    }
                    HStack {
                        Button { model.moveWorkStep(step.id, by: -1) } label: { Image(systemName: "arrow.up") }.accessibilityLabel("attempt.work.moveUp")
                        Button { model.moveWorkStep(step.id, by: 1) } label: { Image(systemName: "arrow.down") }.accessibilityLabel("attempt.work.moveDown")
                    }.buttonStyle(.bordered).frame(minHeight: 44)
                    Button("attempt.work.split") { model.splitWorkStep(step.id) }
                    Button("attempt.work.merge") { model.mergeWorkStep(step.id) }
                    if model.workDraft?.engine == "MANUAL" && model.workSteps.count > 1 {
                        Button("attempt.work.remove", role: .destructive) { model.removeWorkStep(step.id) }
                    }
                }.padding().background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
            }
            if model.workDraft?.engine == "MANUAL" {
                Button("attempt.work.add") { model.addWorkStep() }.disabled(model.workSteps.count >= 12)
                    .accessibilityIdentifier("attempt.work.add")
            }
        }.disabled(model.editingLocked)
    }
}
