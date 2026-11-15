import SwiftUI
import UIKit

struct TutorView: View {
    @State var viewModel: TutorViewModel
    var microLessonViewModel: MicroLessonViewModel? = nil
    var microLessonMistakeCode: String? = nil
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dynamicTypeSize) private var typeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var composerFocused: Bool
    @State private var revealConfirmation = false
    @State private var showMicroLessonDrawer = false
    @AccessibilityFocusState private var focusedHint: UUID?

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: SpacingTokens.md) {
                    AppNoticeBanner("tutor.socraticNotice", systemImage: "sparkles", tone: .action)
                        .accessibilityIdentifier("tutor.notice")

                    if viewModel.busy {
                        HStack(spacing: SpacingTokens.sm) {
                            ProgressView()
                                .tint(ColorTokens.action)
                            Text(LocalizedStringKey(viewModel.pending?.kind == .turn ? "tutor.responsePending" : "tutor.loading"))
                                .font(TypographyTokens.caption)
                                .foregroundStyle(ColorTokens.textSecondary)
                        }
                        .padding(SpacingTokens.compact)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(ColorTokens.surface)
                        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
                        )
                        .accessibilityIdentifier("tutor.loading")
                    }

                    if let error = viewModel.errorKey {
                        AppNoticeBanner(LocalizedStringKey(error), systemImage: "exclamationmark.triangle", tone: .danger)
                            .accessibilityIdentifier("tutor.error")
                    }

                    if let session = viewModel.session {
                        AppSurfaceCard(padding: SpacingTokens.compact) {
                            HStack {
                                Text(LocalizedStringKey(viewModel.phase.localizationKey))
                                    .font(TypographyTokens.sectionTitle)
                                    .foregroundStyle(ColorTokens.textPrimary)
                                    .accessibilityIdentifier("tutor.phase")
                                Spacer()
                                if viewModel.phase.terminal {
                                    AppStatusBadge("tutor.stopped", systemImage: "stop.circle", tone: .neutral)
                                        .accessibilityIdentifier("tutor.stopped")
                                }
                            }
                            if viewModel.phase == .clarifyGoal {
                                Text("tutor.goalPrompt")
                                    .font(TypographyTokens.caption)
                                    .foregroundStyle(ColorTokens.textSecondary)
                                    .accessibilityIdentifier("tutor.goal")
                            }
                            Text("tutor.uncertain")
                                .font(TypographyTokens.caption)
                                .foregroundStyle(ColorTokens.textTertiary)
                                .accessibilityIdentifier("tutor.uncertain")
                            if !session.sourceCurrent {
                                AppNoticeBanner("tutor.stale", systemImage: "arrow.triangle.2.circlepath", tone: .warning)
                                    .accessibilityIdentifier("tutor.stale")
                            }
                        }

                        if viewModel.messages.isEmpty {
                            AppEmptyState(systemImage: "bubble.left.and.bubble.right", title: "tutor.empty")
                                .background(ColorTokens.surface)
                                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                                        .stroke(ColorTokens.borderSubtle, lineWidth: 1)
                                )
                                .accessibilityIdentifier("tutor.empty")
                        }

                        LazyVStack(alignment: .leading, spacing: SpacingTokens.md) {
                            ForEach(viewModel.messages) { message in
                                AppChatBubble(isLearner: true, speakerLabel: "tutor.learner", footnote: "tutor.stored") {
                                    Text(message.content)
                                        .textSelection(.enabled)
                                }
                                .accessibilityElement(children: .combine)
                                .accessibilityIdentifier("tutor.message.\(message.sequenceNumber)")

                                if let turn = viewModel.turns.first(where: { $0.learnerMessageId == message.id }) {
                                    AppChatBubble(isLearner: false, speakerLabel: "tutor.speaker", footnote: "tutor.deterministic") {
                                        Text(turn.content)
                                            .textSelection(.enabled)
                                    }
                                    .accessibilityElement(children: .combine)
                                    .accessibilityIdentifier("tutor.reply.\(turn.learnerSequenceNumber)")
                                }
                            }
                        }

                        if let hints = viewModel.hintHistory {
                            AppSurfaceCard {
                                AppSectionHeader("tutor.hints.title") {
                                    AppStatusBadge(
                                        LocalizedStringKey(hints.currentLevel?.localizationKey ?? "tutor.hint.NONE"),
                                        systemImage: "lightbulb",
                                        tone: .action
                                    )
                                    .accessibilityIdentifier("tutor.hints.current")
                                }

                                if viewModel.isSyntheticFixture {
                                    AppNoticeBanner("tutor.hints.synthetic", systemImage: "testtube.2", tone: .warning)
                                        .accessibilityIdentifier("tutor.hints.synthetic")
                                }

                                Text("tutor.hints.assistance")
                                    .font(TypographyTokens.caption)
                                    .foregroundStyle(ColorTokens.textSecondary)

                                ForEach(hints.hints) { hint in
                                    VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                                        Text(LocalizedStringKey(hint.level.localizationKey))
                                            .font(TypographyTokens.cardTitle)
                                            .foregroundStyle(ColorTokens.stepHighlight)
                                        Text(hint.content)
                                            .font(TypographyTokens.body)
                                            .foregroundStyle(ColorTokens.textPrimary)
                                            .textSelection(.enabled)
                                    }
                                    .padding(SpacingTokens.compact)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(ColorTokens.stepHighlightSoft)
                                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                                            .stroke(ColorTokens.stepHighlight.opacity(0.28), lineWidth: 1)
                                    )
                                    .accessibilityElement(children: .combine)
                                    .accessibilityFocused($focusedHint, equals: hint.id)
                                    .accessibilityIdentifier("tutor.hint.\(hint.level.rawValue)")
                                }

                                if let next = hints.nextLevel {
                                    Button {
                                        composerFocused = false
                                        viewModel.requestHint()
                                    } label: {
                                        Text("tutor.hints.request") + Text(" · ") + Text(LocalizedStringKey(next.localizationKey))
                                    }
                                    .buttonStyle(AppSecondaryButtonStyle())
                                    .disabled(!viewModel.canRequestHint)
                                    .accessibilityIdentifier("tutor.hints.request")
                                } else {
                                    Text(LocalizedStringKey(hints.availability.localizationKey))
                                        .font(TypographyTokens.caption)
                                        .foregroundStyle(ColorTokens.textSecondary)
                                        .accessibilityIdentifier("tutor.hints.status")
                                }

                                Button("tutor.reveal.request") {
                                    composerFocused = false
                                    revealConfirmation = true
                                }
                                .buttonStyle(AppSecondaryButtonStyle())
                                .disabled(!viewModel.canRequestReveal)
                                .accessibilityIdentifier("tutor.reveal.request")

                                if hints.hints.count >= 2,
                                   let microVM = microLessonViewModel,
                                   let mistakeCode = microLessonMistakeCode {
                                    Button("micro_lesson.cta.open_lesson") {
                                        composerFocused = false
                                        showMicroLessonDrawer = true
                                        Task {
                                            await microVM.load(
                                                mistakeCode: mistakeCode,
                                                triggerSource: .socraticRung2
                                            )
                                        }
                                    }
                                    .buttonStyle(AppPrimaryButtonStyle())
                                    .accessibilityIdentifier("tutor.micro_lesson.open")
                                }
                            }
                        }

                        Button("tutor.close") { viewModel.close() }
                            .buttonStyle(AppSecondaryButtonStyle())
                            .disabled(viewModel.busy || viewModel.pending != nil || session.status == .closed || viewModel.blocked)
                            .accessibilityIdentifier("tutor.close")
                    }

                    if !viewModel.sessions.isEmpty {
                        Menu("tutor.history") {
                            ForEach(viewModel.sessions) { session in
                                Button { viewModel.select(session.id) } label: {
                                    if let date = ISO8601DateFormatter().date(from: session.createdAt) {
                                        Text(date, format: .dateTime.year().month().day().hour().minute())
                                    } else {
                                        Text("tutor.history.session")
                                    }
                                }
                            }
                        }
                        .disabled(viewModel.busy || viewModel.pending != nil)
                        .accessibilityIdentifier("tutor.history")
                    }

                    HStack(spacing: SpacingTokens.sm) {
                        Button("tutor.start") { viewModel.start() }
                            .buttonStyle(AppPrimaryButtonStyle())
                            .disabled(!viewModel.loaded || viewModel.busy || viewModel.pending != nil || viewModel.blocked)
                            .accessibilityIdentifier("tutor.start")

                        Button(viewModel.pending == nil ? "tutor.refresh" : "tutor.retryAction") { viewModel.retry() }
                            .buttonStyle(AppSecondaryButtonStyle())
                            .disabled(viewModel.busy || viewModel.blocked)
                            .accessibilityIdentifier("tutor.retry")
                    }

                    if typeSize.isAccessibilitySize { composer }
                }
                .padding(SpacingTokens.md)
            }
            .accessibilityIdentifier("tutor.conversation")
            .scrollDismissesKeyboard(.interactively)

            if !typeSize.isAccessibilitySize { composer }
        }
        .background(ColorTokens.canvas.ignoresSafeArea())
        .transaction { if reduceMotion { $0.animation = nil } }
        .navigationTitle("tutor.title")
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                if typeSize.isAccessibilitySize {
                    Button { composerFocused = false } label: {
                        Image(systemName: "keyboard.chevron.compact.down").frame(minWidth: 44, minHeight: 44)
                    }
                    .accessibilityLabel("tutor.keyboard.dismiss")
                    .accessibilityIdentifier("tutor.keyboard.dismiss")
                    Spacer()
                    Button {
                        composerFocused = false
                        viewModel.send()
                    } label: {
                        Image(systemName: "paperplane.fill").frame(minWidth: 44, minHeight: 44)
                    }
                    .disabled(!viewModel.canSend)
                    .accessibilityLabel(LocalizedStringKey(viewModel.busy ? "tutor.sending" : "tutor.send"))
                    .accessibilityIdentifier("tutor.send")
                }
            }
        }
        .task { viewModel.resume() }
        .onDisappear { viewModel.suspend() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { viewModel.resume() } else { viewModel.suspend() }
        }
        .confirmationDialog("tutor.reveal.confirmation", isPresented: $revealConfirmation, titleVisibility: .visible) {
            Button("tutor.reveal.confirm") { viewModel.confirmReveal() }
            Button("tutor.reveal.cancel", role: .cancel) {}
        }
        .sheet(isPresented: $showMicroLessonDrawer) {
            if let microVM = microLessonViewModel {
                MicroLessonDrawerView(
                    viewModel: microVM,
                    onReturnToPractice: { showMicroLessonDrawer = false },
                    onFallbackToHints: { showMicroLessonDrawer = false }
                )
            }
        }
        .onChange(of: viewModel.hintHistory?.hints.last?.id) { _, id in
            focusedHint = id
        }
        .onChange(of: viewModel.errorKey) { _, key in
            if let key {
                UIAccessibility.post(notification: .announcement, argument: NSLocalizedString(key, comment: "Tutor error"))
            }
        }
    }

    private var composer: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            if viewModel.session?.status == .closed {
                Text("tutor.closed")
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityIdentifier("tutor.closed")
            }

            HStack(spacing: SpacingTokens.sm) {
                Picker("tutor.intent", selection: $viewModel.selectedIntent) {
                    ForEach(TutorIntent.allCases, id: \.self) { intent in
                        Text(LocalizedStringKey(intent.localizationKey)).tag(intent)
                    }
                }
                .pickerStyle(.menu)
                .disabled(viewModel.busy || viewModel.pending != nil)
                .accessibilityIdentifier("tutor.intent")
                Spacer()
            }

            if viewModel.session?.status == .active, viewModel.pending == nil, !viewModel.blocked {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: SpacingTokens.xs) {
                        quickPromptChip("Why this step?", intent: .explainConcept)
                        quickPromptChip("Check my algebra", intent: .attemptStep)
                        quickPromptChip("Next hint", intent: .smallHint)
                    }
                }
            }

            TextField("tutor.composer", text: $viewModel.draft, axis: .vertical)
                .lineLimit(1...5)
                .padding(.horizontal, SpacingTokens.compact)
                .padding(.vertical, 10)
                .background(ColorTokens.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                        .stroke(composerFocused ? ColorTokens.borderStrong : ColorTokens.borderSubtle, lineWidth: 1)
                )
                .focused($composerFocused)
                .disabled(viewModel.pending != nil || viewModel.blocked)
                .accessibilityIdentifier("tutor.composer")

            if !typeSize.isAccessibilitySize || !composerFocused {
                Button(viewModel.busy ? "tutor.sending" : "tutor.send") {
                    composerFocused = false
                    viewModel.send()
                }
                .buttonStyle(AppPrimaryButtonStyle())
                .disabled(!viewModel.canSend)
                .accessibilityIdentifier("tutor.send")
            }
        }
        .padding(SpacingTokens.md)
        .background(ColorTokens.surface)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(ColorTokens.borderSubtle)
                .frame(height: 1)
        }
    }

    private func quickPromptChip(_ prompt: String, intent: TutorIntent) -> some View {
        Button {
            viewModel.selectedIntent = intent
            viewModel.draft = prompt
            composerFocused = true
        } label: {
            Text(prompt)
                .font(TypographyTokens.microLabel)
                .foregroundStyle(ColorTokens.stepHighlight)
                .padding(.horizontal, SpacingTokens.sm)
                .padding(.vertical, 6)
                .background(ColorTokens.stepHighlightSoft)
                .clipShape(Capsule())
                .overlay(
                    Capsule()
                        .stroke(ColorTokens.stepHighlight.opacity(0.24), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}
