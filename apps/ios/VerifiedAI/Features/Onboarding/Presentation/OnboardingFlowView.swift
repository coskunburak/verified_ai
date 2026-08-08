import SwiftUI

struct OnboardingFlowView: View {
    @Bindable var viewModel: OnboardingViewModel

    var body: some View {
        VStack(spacing: 0) {
            progressHeader

            ScrollView {
                VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                    stepContent
                    statusContent
                }
                .padding(SpacingTokens.lg)
                .frame(maxWidth: 620, alignment: .leading)
            }

            Divider()
            actionBar
        }
        .background(ColorTokens.background)
        .navigationTitle("Profile")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if viewModel.state == .idle {
                await viewModel.bootstrap()
            }
        }
    }

    private var progressHeader: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            HStack(spacing: SpacingTokens.xs) {
                ForEach(OnboardingStep.allCases) { step in
                    Capsule()
                        .fill(step.rawValue <= viewModel.currentStep.rawValue ? ColorTokens.action : ColorTokens.textSecondary.opacity(0.25))
                        .frame(height: 5)
                }
            }
            Text(viewModel.currentStep.title)
                .font(TypographyTokens.title)
                .foregroundStyle(ColorTokens.textPrimary)
        }
        .padding(SpacingTokens.lg)
        .background(ColorTokens.surface)
    }

    @ViewBuilder
    private var stepContent: some View {
        switch viewModel.currentStep {
        case .education:
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                Text("What level are you studying?")
                    .font(TypographyTokens.body)
                    .foregroundStyle(ColorTokens.textPrimary)
                Picker("Education level", selection: $viewModel.draft.educationLevel) {
                    Text("Choose").tag(Optional<EducationLevel>.none)
                    ForEach(EducationLevel.allCases) { level in
                        Text(level.title).tag(Optional(level))
                    }
                }
                .pickerStyle(.inline)
            }
        case .preferences:
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                Text("How should explanations feel?")
                    .font(TypographyTokens.body)
                    .foregroundStyle(ColorTokens.textPrimary)
                Picker("Explanation depth", selection: $viewModel.draft.explanationDepth) {
                    ForEach(ExplanationDepth.allCases) { depth in
                        Text(depth.title).tag(depth)
                    }
                }
                .pickerStyle(.segmented)

                Picker("Language", selection: $viewModel.draft.preferredLanguage) {
                    Text("English").tag("en")
                    Text("Turkish").tag("tr")
                }
                .pickerStyle(.segmented)
            }
        case .schedule:
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                Stepper(value: $viewModel.draft.dailyStudyMinutes, in: 5...240, step: 5) {
                    Text("\(viewModel.draft.dailyStudyMinutes) minutes per day")
                        .font(TypographyTokens.body)
                        .foregroundStyle(ColorTokens.textPrimary)
                }
                TextField("Timezone", text: $viewModel.draft.timezone)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)
            }
        case .goal:
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                Text("What are you working toward?")
                    .font(TypographyTokens.body)
                    .foregroundStyle(ColorTokens.textPrimary)
                TextField("Learning goal", text: $viewModel.draft.goalContext, axis: .vertical)
                    .lineLimit(3...5)
                    .textFieldStyle(.roundedBorder)
                Text("\(viewModel.draft.goalContext.count)/160")
                    .font(TypographyTokens.caption)
                    .foregroundStyle(viewModel.draft.goalContext.count <= 160 ? ColorTokens.textSecondary : ColorTokens.warning)
            }
        case .review:
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                row("Education", viewModel.draft.educationLevel?.title ?? "")
                row("Depth", viewModel.draft.explanationDepth.title)
                row("Language", viewModel.draft.preferredLanguage == "tr" ? "Turkish" : "English")
                row("Daily study", "\(viewModel.draft.dailyStudyMinutes) minutes")
                row("Timezone", viewModel.draft.timezone)
                if !viewModel.draft.goalContext.isEmpty {
                    row("Goal", viewModel.draft.goalContext)
                }
            }
        }
    }

    @ViewBuilder
    private var statusContent: some View {
        switch viewModel.state {
        case .loading:
            ProgressView()
                .accessibilityLabel("Loading profile")
        case .saving:
            ProgressView()
                .accessibilityLabel("Saving profile")
        case .offline:
            VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                Label("You are offline", systemImage: "wifi.slash")
                    .foregroundStyle(ColorTokens.warning)
                Button("Retry") {
                    Task { await viewModel.bootstrap() }
                }
                .buttonStyle(.bordered)
            }
        case .failed:
            if let message = viewModel.message {
                Label(message, systemImage: "exclamationmark.triangle")
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.warning)
            }
        case .idle, .needsOnboarding, .ready:
            EmptyView()
        }
    }

    private var actionBar: some View {
        HStack {
            if viewModel.currentStep.rawValue > 0 {
                Button("Back") {
                    viewModel.currentStep = OnboardingStep(rawValue: viewModel.currentStep.rawValue - 1) ?? .education
                }
                .buttonStyle(.bordered)
            }

            Spacer()

            Button(viewModel.currentStep == .review ? "Finish" : "Continue") {
                Task {
                    if viewModel.currentStep == .review {
                        _ = await viewModel.completeOnboarding()
                    } else {
                        await viewModel.advance()
                    }
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(viewModel.state == .loading || viewModel.state == .saving)
        }
        .padding(SpacingTokens.lg)
        .background(ColorTokens.surface)
    }

    private func row(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(label)
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
                .frame(width: 110, alignment: .leading)
            Text(value)
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
