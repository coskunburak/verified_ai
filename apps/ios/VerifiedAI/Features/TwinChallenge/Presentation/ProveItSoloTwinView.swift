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
                        .accessibilityIdentifier("twin.challenge.loading")
                } else if let error = viewModel.errorMessage, viewModel.challenge == nil {
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.largeTitle)
                            .foregroundStyle(.orange)
                        Text(error)
                            .multilineTextAlignment(.center)
                        Button("Retry") {
                            viewModel.loadChallenge()
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding()
                } else if let challenge = viewModel.challenge {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 20) {
                            skillHeader(challenge)
                            problemCard(challenge)
                            timerBanner(challenge)
                            outcomeCard(challenge)

                            if challenge.status == .activeLocked || challenge.status == .activeUnlocked {
                                inputSection()
                            }
                        }
                        .padding()
                    }
                }
            }
            .navigationTitle(Text("twin.challenge.title"))
            .accessibilityIdentifier("twin.challenge.root")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") {
                        onDismiss?()
                    }
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
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text("Remediating Root Cause: \(challenge.rootCauseSkillCode)")
                .font(.caption)
                .foregroundStyle(.orange)
        }
    }

    private func problemCard(_ challenge: VerifiedTwinChallenge) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(challenge.problemStatement)
                .font(.body)

            Text(challenge.problemLatex)
                .font(.system(.title3, design: .monospaced))
                .padding()
                .frame(maxWidth: .infinity, alignment: .center)
                .background(Color(uiColor: .tertiarySystemGroupedBackground))
                .cornerRadius(8)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemGroupedBackground))
        .cornerRadius(12)
    }

    private func timerBanner(_ challenge: VerifiedTwinChallenge) -> some View {
        HStack {
            Image(systemName: viewModel.isLocked ? "lock.fill" : "lock.open.fill")
                .foregroundStyle(viewModel.isLocked ? .orange : .green)

            if viewModel.isLocked {
                Text(String(format: NSLocalizedString("twin.challenge.locked_timer", comment: ""), viewModel.remainingSeconds))
                    .font(.subheadline.bold())
            } else {
                Text("Solution Unlocked")
                    .font(.subheadline.bold())
                    .foregroundStyle(.green)
            }
            Spacer()
        }
        .padding()
        .background(viewModel.isLocked ? Color.orange.opacity(0.12) : Color.green.opacity(0.12))
        .cornerRadius(8)
        .accessibilityIdentifier("twin.challenge.timer")
    }

    @ViewBuilder
    private func outcomeCard(_ challenge: VerifiedTwinChallenge) -> some View {
        if challenge.status == .verifiedUnassistedSuccess {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Image(systemName: "checkmark.seal.fill")
                        .foregroundStyle(.green)
                    Text("twin.challenge.success_title")
                        .font(.headline)
                        .foregroundStyle(.green)
                }
                Text(String(format: NSLocalizedString("twin.challenge.remediation_credit", comment: ""), String(format: "%.4f", challenge.remediationCreditGranted)))
                    .font(.subheadline.bold())
                    .foregroundStyle(.green)
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.green.opacity(0.12))
            .cornerRadius(12)
            .accessibilityIdentifier("twin.challenge.success_banner")
        } else if challenge.status == .solutionRevealedAssisted {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Image(systemName: "info.circle.fill")
                        .foregroundStyle(.blue)
                    Text("Solution Revealed")
                        .font(.headline)
                }
                Text("twin.challenge.assisted_info")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.blue.opacity(0.12))
            .cornerRadius(12)
            .accessibilityIdentifier("twin.challenge.assisted_banner")
        }
    }

    private func inputSection() -> some View {
        VStack(spacing: 14) {
            TextField("Your answer (e.g. x = 5)", text: $viewModel.learnerAnswer)
                .textFieldStyle(.roundedBorder)
                .font(.system(.body, design: .monospaced))
                .accessibilityIdentifier("twin.challenge.answer_field")

            HStack(spacing: 12) {
                Button(action: {
                    viewModel.submitAnswer()
                }) {
                    if viewModel.isSubmitting {
                        ProgressView()
                            .tint(.white)
                    } else {
                        Text("twin.challenge.submit_answer")
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.learnerAnswer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || viewModel.isSubmitting)
                .accessibilityIdentifier("twin.challenge.submit_button")

                Button(action: {
                    viewModel.requestRevealSolution()
                }) {
                    Text("twin.challenge.reveal_solution")
                }
                .buttonStyle(.bordered)
                .disabled(viewModel.isSubmitting)
                .accessibilityIdentifier("twin.challenge.reveal_button")
            }
        }
    }
}
