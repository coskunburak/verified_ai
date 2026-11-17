import SwiftUI

/// Native presentation owns each destination model for the lifetime of its sheet.
@MainActor
struct PostExamRemediationDestination: View {
    let route: ExamRemediationRoute
    let attributionModel: PostExamAttributionViewModel

    @ViewBuilder var body: some View {
        switch route.destination {
        case .practice(let mode):
            ExamNativePracticeDestination(route: route, mode: mode, attributionModel: attributionModel)
        case .microLesson(let mistakeCode):
            ExamNativeMicroLessonDestination(mistakeCode: mistakeCode, attributionModel: attributionModel)
        }
    }
}

@MainActor
private struct ExamNativePracticeDestination: View {
    let route: ExamRemediationRoute
    let mode: PracticeSessionMode
    @StateObject private var model: PracticeSessionViewModel
    @Environment(\.dismiss) private var dismiss

    init(route: ExamRemediationRoute, mode: PracticeSessionMode, attributionModel: PostExamAttributionViewModel) {
        self.route = route
        self.mode = mode
        _model = StateObject(wrappedValue: attributionModel.makePracticeViewModel(for: route))
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text(mode == .trapVerificationDrill ? "İkiz Soru" : "Hedefli Alıştırma")
                    .font(.headline).fixedSize(horizontal: false, vertical: true)
                Spacer()
                Button("Kapat") { dismiss() }
            }.padding()
            if route.actionType == .microLesson {
                Text("Bu beceriyi hedefli alıştırmayla pekiştir.")
                    .fixedSize(horizontal: false, vertical: true).padding(.horizontal)
            }
            // The existing practice screen has no initial-load error panel.
            // Keep its error/retry path visible instead of showing an empty session.
            if let error = model.errorMessage {
                Text(error).fixedSize(horizontal: false, vertical: true).padding()
                if model.session == nil {
                    Button("Tekrar Dene") { Task { await load() } }
                        .disabled(model.isSubmitting)
                }
            } else if model.session == nil {
                ProgressView("Alıştırma hazırlanıyor").padding()
            }
            PracticeSessionView(viewModel: model)
        }
        .background(ColorTokens.background)
        .accessibilityIdentifier("postExamAttribution.nativePracticeDestination")
        .task { if model.session == nil { await load() } }
        .task {
            do {
                while !Task.isCancelled {
                    try await Task.sleep(for: .seconds(1))
                    model.tickVerificationTimer()
                }
            } catch { /* Sheet dismissal cancels the countdown. */ }
        }
        .onChange(of: model.session?.status) { _, status in
            if status == .abandoned { dismiss() }
        }
    }

    private func load() async {
        await model.loadOrCreateSession(targetSkillId: route.skillId, sessionMode: mode)
    }
}

@MainActor
private struct ExamNativeMicroLessonDestination: View {
    let mistakeCode: String
    @StateObject private var model: MicroLessonViewModel
    @Environment(\.dismiss) private var dismiss

    init(mistakeCode: String, attributionModel: PostExamAttributionViewModel) {
        self.mistakeCode = mistakeCode
        _model = StateObject(wrappedValue: attributionModel.makeMicroLessonViewModel())
    }

    var body: some View {
        MicroLessonDrawerView(viewModel: model, onReturnToPractice: { dismiss() }, onFallbackToHints: { dismiss() })
            .accessibilityIdentifier("postExamAttribution.nativeMicroLessonDestination")
            .task { await model.load(mistakeCode: mistakeCode, locale: "tr-TR", triggerSource: .coachRepairDrawer) }
    }
}
