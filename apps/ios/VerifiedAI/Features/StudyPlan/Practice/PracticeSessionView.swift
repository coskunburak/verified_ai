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
                VStack(spacing: 20) {
                    // 1-2-1 Session Progress Bar
                    sessionProgressBar

                    // Offline Banner
                    if viewModel.isOffline {
                        HStack {
                            Image(systemName: "wifi.slash")
                            Text(LocalizedStringKey("coach.nba.offline.banner"))
                                .font(.caption)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.secondary.opacity(0.1))
                        .cornerRadius(8)
                    }

                    // Rapid Guess Warning Banner (INV-PRAC-004)
                    if viewModel.rapidGuessWarningActive {
                        HStack(spacing: 12) {
                            Image(systemName: "hand.raised.fill")
                                .foregroundColor(.orange)
                            Text(LocalizedStringKey("practice.rapid_guess.warning"))
                                .font(.subheadline)
                                .foregroundColor(.primary)
                        }
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.orange.opacity(0.12))
                        .cornerRadius(12)
                    }

                    // Trap Focus Area Diagnostic Feedback
                    if let feedback = viewModel.trapFeedback {
                        HStack(spacing: 12) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.purple)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(LocalizedStringKey("practice.trap.detected"))
                                    .font(.subheadline.bold())
                                    .foregroundColor(.purple)
                                Text(feedback)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.purple.opacity(0.1))
                        .cornerRadius(12)
                    }

                    // Trap Closure Celebration
                    if viewModel.trapClosureCelebration {
                        HStack(spacing: 12) {
                            Image(systemName: "sparkles")
                                .foregroundColor(.green)
                            Text("Trap focus area conquered autonomously!")
                                .font(.subheadline.bold())
                                .foregroundColor(.green)
                        }
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color.green.opacity(0.12))
                        .cornerRadius(12)
                    }

                    // Active Question Card
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

                        // Answer Composer
                        if item.itemStatus == .pending {
                            answerComposerSection
                        } else {
                            resultAndNextSection(item: item)
                        }
                    } else if viewModel.isSessionCompleted {
                        sessionCompletedSection
                    }
                }
                .padding()
            }
            .navigationTitle(Text(LocalizedStringKey("practice.session.title")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(action: { showingAbandonConfirmation = true }) {
                        Text(LocalizedStringKey("practice.action.abandon"))
                            .font(.caption)
                            .foregroundColor(.secondary)
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
                Rectangle()
                    .fill(progressColor(for: index))
                    .frame(height: 6)
                    .cornerRadius(3)
            }
        }
        .padding(.horizontal, 4)
    }

    private func progressColor(for index: Int) -> Color {
        guard let s = viewModel.session, index < s.items.count else {
            return Color.secondary.opacity(0.2)
        }
        let item = s.items[index]
        switch item.itemStatus {
        case .submittedCorrect: return .green
        case .submittedIncorrect: return .orange
        case .skipped: return .secondary
        case .pending:
            return index == viewModel.currentItemIndex ? .blue : Color.secondary.opacity(0.2)
        }
    }

    private var answerComposerSection: some View {
        VStack(spacing: 12) {
            TextField("Your answer (e.g. 3, x > -4, 24x...)", text: $viewModel.submittedAnswer)
                .textFieldStyle(.roundedBorder)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)

            Button(action: {
                Task {
                    await viewModel.submitCurrentAnswer()
                }
            }) {
                HStack {
                    if viewModel.isSubmitting {
                        ProgressView().tint(.white)
                    } else {
                        Text(LocalizedStringKey("practice.action.submit"))
                    }
                }
                .font(.body.bold())
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.blue)
                .foregroundColor(.white)
                .cornerRadius(12)
            }
            .disabled(viewModel.isSubmitting || viewModel.submittedAnswer.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .padding()
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(16)
    }

    private func resultAndNextSection(item: PracticeSessionItem) -> some View {
        VStack(spacing: 12) {
            HStack {
                Image(systemName: item.itemStatus == .submittedCorrect ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .foregroundColor(item.itemStatus == .submittedCorrect ? .green : .orange)
                    .font(.title2)

                Text(item.itemStatus == .submittedCorrect ? "Verified Correct!" : "Opportunity for calibration")
                    .font(.headline)
            }

            if viewModel.currentItemIndex + 1 < viewModel.totalItemsCount {
                Button(action: { viewModel.moveToNextQuestion() }) {
                    Text("Next Question")
                        .font(.body.bold())
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                }
            } else {
                Text("Session Complete")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
        .frame(maxWidth: .infinity)
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(16)
    }

    private var sessionCompletedSection: some View {
        VStack(spacing: 16) {
            Image(systemName: "trophy.fill")
                .font(.system(size: 48))
                .foregroundColor(.yellow)

            Text("Session Completed!")
                .font(.title2.bold())

            if let s = viewModel.session {
                Text(String(format: "%d of %d items verified correct", s.verifiedCorrectItems, s.totalItems))
                    .font(.body)
                    .foregroundColor(.secondary)

                if s.trapClosureAchieved {
                    Text("Target trap successfully closed!")
                        .font(.subheadline.bold())
                        .foregroundColor(.green)
                }
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity)
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(20)
    }
}
