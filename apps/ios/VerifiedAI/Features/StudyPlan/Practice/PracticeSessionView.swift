import SwiftUI

public struct PracticeSessionView: View {
    @ObservedObject public var viewModel: PracticeSessionViewModel
    @State private var showingAbandonConfirmation: Bool = false

    public init(viewModel: PracticeSessionViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: SpacingTokens.lg) {
                    sessionProgressBar

                    if viewModel.isOffline {
                        AppNoticeBanner("coach.nba.offline.banner", systemImage: "wifi.slash", tone: .warning)
                    }

                    if viewModel.rapidGuessWarningActive {
                        AppNoticeBanner("practice.rapid_guess.warning", systemImage: "hand.raised.fill", tone: .warning)
                    }

                    if let feedback = viewModel.trapFeedback {
                        AppSurfaceCard(elevated: true) {
                            HStack(spacing: SpacingTokens.compact) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundStyle(ColorTokens.warning)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(LocalizedStringKey("practice.trap.detected"))
                                        .font(TypographyTokens.cardTitle)
                                        .foregroundStyle(ColorTokens.warning)
                                    Text(feedback)
                                        .font(TypographyTokens.caption)
                                        .foregroundStyle(ColorTokens.textSecondary)
                                }
                            }
                        }
                        .overlay(
                            RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                                .stroke(ColorTokens.warning.opacity(0.45), lineWidth: 1)
                        )
                    }

                    if viewModel.trapClosureCelebration {
                        AppNoticeBanner("Trap focus area conquered autonomously!", systemImage: "sparkles", tone: .success)
                    }

                    if let item = viewModel.currentItem {
                        PracticeQuestionCardView(
                            item: item,
                            remainingLockSeconds: viewModel.remainingLockSeconds,
                            isLockActive: viewModel.isLockActive,
                            urgencyPhase: viewModel.urgencyPhase,
                            onHintRequested: {
                                Task {
                                    await viewModel.requestHint()
                                }
                            }
                        )

                        if item.itemStatus == .pending {
                            answerComposerSection
                        } else {
                            resultAndNextSection(item: item)
                        }
                    } else if viewModel.isSessionCompleted {
                        sessionCompletedSection
                    }
                }
                .padding(SpacingTokens.lg)
            }
            .background(ColorTokens.canvas)
            .navigationTitle(Text(LocalizedStringKey("practice.session.title")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(action: { showingAbandonConfirmation = true }) {
                        Text(LocalizedStringKey("practice.action.abandon"))
                            .font(TypographyTokens.caption.weight(.semibold))
                            .foregroundStyle(ColorTokens.textSecondary)
                    }
                }
            }
            .confirmationDialog(
                "Are you sure you want to end this practice session?",
                isPresented: $showingAbandonConfirmation,
                titleVisibility: .visible
            ) {
                Button("End Session", role: .destructive) {
                    Task {
                        await viewModel.abandonSession()
                    }
                }
                Button("Cancel", role: .cancel) {}
            }
        }
    }

    private var sessionProgressBar: some View {
        HStack(spacing: 6) {
            ForEach(0..<viewModel.totalItemsCount, id: \.self) { index in
                RoundedRectangle(cornerRadius: 3, style: .continuous)
                    .fill(progressColor(for: index))
                    .frame(height: 6)
            }
        }
        .padding(.horizontal, SpacingTokens.xs)
    }

    private func progressColor(for index: Int) -> Color {
        guard let s = viewModel.session, index < s.items.count else {
            return ColorTokens.surfaceElevated
        }
        let item = s.items[index]
        switch item.itemStatus {
        case .submittedCorrect: return ColorTokens.success
        case .submittedIncorrect: return ColorTokens.warning
        case .skipped: return ColorTokens.textTertiary
        case .pending:
            return index == viewModel.currentItemIndex ? ColorTokens.action : ColorTokens.surfaceElevated
        }
    }

    private var answerComposerSection: some View {
        AppSurfaceCard {
            TextField("Your answer (e.g. 3, x > -4, 24x...)", text: $viewModel.submittedAnswer)
                .padding(SpacingTokens.compact)
                .background(ColorTokens.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                        .stroke(ColorTokens.borderStrong, lineWidth: 1)
                )
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)

            Button(action: {
                Task {
                    await viewModel.submitCurrentAnswer()
                }
            }) {
                HStack {
                    if viewModel.isSubmitting {
                        ProgressView().tint(ColorTokens.actionForeground)
                    } else {
                        Text(LocalizedStringKey("practice.action.submit"))
                    }
                }
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .disabled(viewModel.isSubmitting || viewModel.submittedAnswer.trimmingCharacters(in: .whitespaces).isEmpty)
        }
    }

    private func resultAndNextSection(item: PracticeSessionItem) -> some View {
        AppSurfaceCard(elevated: true) {
            HStack(spacing: SpacingTokens.sm) {
                Image(systemName: item.itemStatus == .submittedCorrect ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .foregroundStyle(item.itemStatus == .submittedCorrect ? ColorTokens.success : ColorTokens.warning)
                    .font(.title2)

                Text(item.itemStatus == .submittedCorrect ? "Verified Correct!" : "Opportunity for calibration")
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.textPrimary)
            }

            if viewModel.currentItemIndex + 1 < viewModel.totalItemsCount {
                Button(action: { viewModel.moveToNextQuestion() }) {
                    Text("Next Question")
                }
                .buttonStyle(AppPrimaryButtonStyle())
            } else {
                Text("Session Complete")
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
            }
        }
    }

    private var sessionCompletedSection: some View {
        AppSurfaceCard {
            VStack(spacing: SpacingTokens.md) {
                Image(systemName: "trophy.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(ColorTokens.action)

                Text("Session Completed!")
                    .font(TypographyTokens.sectionTitle)
                    .foregroundStyle(ColorTokens.textPrimary)

                if let s = viewModel.session {
                    Text(String(format: "%d of %d items verified correct", s.verifiedCorrectItems, s.totalItems))
                        .font(TypographyTokens.body.monospacedDigit())
                        .foregroundStyle(ColorTokens.textSecondary)

                    if s.trapClosureAchieved {
                        AppStatusBadge(verbatimTitle: "Target trap closed", systemImage: "checkmark.seal.fill", tone: .success)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, SpacingTokens.lg)
        }
    }
}
