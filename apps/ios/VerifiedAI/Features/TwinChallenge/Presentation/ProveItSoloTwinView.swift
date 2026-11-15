import SwiftUI

public struct ProveItSoloTwinView: View {
    @Bindable public var viewModel: TwinChallengeViewModel
    public var onDismiss: (() -> Void)?

    public init(
        viewModel: TwinChallengeViewModel,
        onDismiss: (() -> Void)? = nil
    ) {
        self.viewModel = viewModel
        self.onDismiss = onDismiss
    }

    public var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .accessibilityIdentifier("twin.challenge.loading")
                } else if let error = viewModel.errorMessage, viewModel.challenge == nil {
                    AppSurfaceCard(elevated: true) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.largeTitle)
                            .foregroundStyle(ColorTokens.warning)
                        Text(error)
                            .font(TypographyTokens.body)
                            .foregroundStyle(ColorTokens.textPrimary)
                            .multilineTextAlignment(.center)
                        Button("Retry") {
                            viewModel.loadChallenge()
                        }
                        .buttonStyle(AppPrimaryButtonStyle())
                    }
                    .padding(SpacingTokens.lg)
                } else if let challenge = viewModel.challenge {
                    ScrollView {
                        VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                            skillHeader(challenge)
                            problemCard(challenge)
                            timerBanner(challenge)
                            outcomeCard(challenge)

                            if challenge.status == .activeLocked || challenge.status == .activeUnlocked {
                                inputSection()
                            }
                        }
                        .padding(SpacingTokens.lg)
                    }
                }
            }
            .background(ColorTokens.canvas)
            .navigationTitle(Text("twin.challenge.title"))
            .accessibilityIdentifier("twin.challenge.root")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") {
                        onDismiss?()
                    }
                    .foregroundStyle(ColorTokens.action)
                }
            }
            .alert(
                Text("twin.challenge.early_forfeit_warning"),
                isPresented: $viewModel.showEarlyForfeitConfirmation
            ) {
                Button("Cancel", role: .cancel) {}
                Button(role: .destructive) {
                    viewModel.confirmEarlyForfeitAndReveal()
                } label: {
                    Text("twin.challenge.early_forfeit_confirm")
                }
            }
            .onAppear {
                viewModel.loadChallenge()
            }
            .onDisappear {
                viewModel.cancel()
            }
        }
    }

    private func skillHeader(_ challenge: VerifiedTwinChallenge) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Target Skill: \(challenge.targetSkillCode)")
                .font(TypographyTokens.caption.weight(.semibold))
                .foregroundStyle(ColorTokens.textSecondary)
            Text("Remediating Root Cause: \(challenge.rootCauseSkillCode)")
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.warning)
        }
    }

    private func problemCard(_ challenge: VerifiedTwinChallenge) -> some View {
        AppSurfaceCard {
            Text(challenge.problemStatement)
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textPrimary)

            Text(challenge.problemLatex)
                .font(.system(.title3, design: .monospaced))
                .foregroundStyle(ColorTokens.textPrimary)
                .padding(SpacingTokens.md)
                .frame(maxWidth: .infinity, alignment: .center)
                .background(ColorTokens.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                        .stroke(ColorTokens.borderSubtle, lineWidth: 1)
                )
        }
    }

    private func timerBanner(_ challenge: VerifiedTwinChallenge) -> some View {
        HStack {
            Image(systemName: viewModel.isLocked ? "lock.fill" : "lock.open.fill")
                .foregroundStyle(viewModel.isLocked ? ColorTokens.warning : ColorTokens.success)

            if viewModel.isLocked {
                Text(String(format: NSLocalizedString("twin.challenge.locked_timer", comment: ""), viewModel.remainingSeconds))
                    .font(TypographyTokens.cardTitle)
                    .monospacedDigit()
                    .foregroundStyle(ColorTokens.textPrimary)
            } else {
                Text("Solution Unlocked")
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.success)
            }
            Spacer()
        }
        .padding(SpacingTokens.compact)
        .background(viewModel.isLocked ? ColorTokens.warningSoft : ColorTokens.successSoft)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                .stroke((viewModel.isLocked ? ColorTokens.warning : ColorTokens.success).opacity(0.4), lineWidth: 1)
        )
        .accessibilityIdentifier("twin.challenge.timer")
    }

    @ViewBuilder
    private func outcomeCard(_ challenge: VerifiedTwinChallenge) -> some View {
        if challenge.status == .verifiedUnassistedSuccess {
            VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                HStack {
                    Image(systemName: "checkmark.seal.fill")
                        .foregroundStyle(ColorTokens.success)
                    Text("twin.challenge.success_title")
                        .font(TypographyTokens.cardTitle)
                        .foregroundStyle(ColorTokens.success)
                }
                Text(String(format: NSLocalizedString("twin.challenge.remediation_credit", comment: ""), String(format: "%.4f", challenge.remediationCreditGranted)))
                    .font(TypographyTokens.caption.weight(.semibold))
                    .monospacedDigit()
                    .foregroundStyle(ColorTokens.success)
            }
            .padding(SpacingTokens.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ColorTokens.successSoft)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                    .stroke(ColorTokens.success.opacity(0.45), lineWidth: 1)
            )
            .accessibilityIdentifier("twin.challenge.success_banner")
        } else if challenge.status == .solutionRevealedAssisted {
            VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                HStack {
                    Image(systemName: "info.circle.fill")
                        .foregroundStyle(ColorTokens.action)
                    Text("Solution Revealed")
                        .font(TypographyTokens.cardTitle)
                        .foregroundStyle(ColorTokens.textPrimary)
                }
                Text("twin.challenge.assisted_info")
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
            }
            .padding(SpacingTokens.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ColorTokens.actionSoft)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                    .stroke(ColorTokens.action.opacity(0.4), lineWidth: 1)
            )
            .accessibilityIdentifier("twin.challenge.assisted_banner")
        }
    }

    private func inputSection() -> some View {
        VStack(spacing: SpacingTokens.compact) {
            TextField("Your answer (e.g. x = 5)", text: $viewModel.learnerAnswer)
                .font(.system(.body, design: .monospaced))
                .padding(SpacingTokens.compact)
                .background(ColorTokens.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                        .stroke(ColorTokens.borderStrong, lineWidth: 1)
                )
                .accessibilityIdentifier("twin.challenge.answer_field")

            HStack(spacing: SpacingTokens.sm) {
                Button(action: {
                    viewModel.submitAnswer()
                }) {
                    if viewModel.isSubmitting {
                        ProgressView()
                            .tint(ColorTokens.actionForeground)
                    } else {
                        Text("twin.challenge.submit_answer")
                    }
                }
                .buttonStyle(AppPrimaryButtonStyle())
                .disabled(viewModel.learnerAnswer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || viewModel.isSubmitting)
                .accessibilityIdentifier("twin.challenge.submit_button")

                Button(action: {
                    viewModel.requestRevealSolution()
                }) {
                    Text("twin.challenge.reveal_solution")
                }
                .buttonStyle(AppSecondaryButtonStyle())
                .disabled(viewModel.isSubmitting)
                .accessibilityIdentifier("twin.challenge.reveal_button")
            }
        }
    }
}
