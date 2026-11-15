import SwiftUI

struct OnboardingFlowView: View {
    @Bindable var viewModel: OnboardingViewModel

    var body: some View {
        VStack(spacing: 0) {
            progressHeader

            ScrollView {
                VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                    AppSurfaceCard {
                        stepContent
                    }
                    statusContent
                }
                .padding(SpacingTokens.lg)
                .frame(maxWidth: 620, alignment: .leading)
            }

            Rectangle()
                .fill(ColorTokens.borderSubtle)
                .frame(height: 1)
            actionBar
        }
        .background(ColorTokens.canvas)
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
            HStack {
                AppStatusBadge(
                    verbatimTitle: "Step \(viewModel.currentStep.rawValue + 1)/\(OnboardingStep.allCases.count)",
                    tone: .action
                )
                .monospacedDigit()
                Spacer()
            }

            HStack(spacing: SpacingTokens.xs) {
                ForEach(OnboardingStep.allCases) { step in
                    Capsule()
                        .fill(step.rawValue <= viewModel.currentStep.rawValue ? ColorTokens.action : ColorTokens.surfaceElevated)
                        .frame(height: 6)
                        .overlay(
                            Capsule()
                                .stroke(ColorTokens.borderSubtle, lineWidth: step.rawValue <= viewModel.currentStep.rawValue ? 0 : 1)
                        )
                }
            }
            Text(viewModel.currentStep.title)
                .font(TypographyTokens.sectionTitle)
                .foregroundStyle(ColorTokens.textPrimary)
        }
        .padding(SpacingTokens.lg)
        .background(ColorTokens.surface)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(ColorTokens.borderSubtle)
                .frame(height: 1)
        }
    }

    @ViewBuilder
    private var stepContent: some View {
        switch viewModel.currentStep {
        case .education:
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                Text("What level are you studying?")
                    .font(TypographyTokens.cardTitle)
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
                    .font(TypographyTokens.cardTitle)
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
                    HStack {
                        Text("Daily study target")
                            .font(TypographyTokens.cardTitle)
                            .foregroundStyle(ColorTokens.textPrimary)
                        Spacer()
                        Text("\(viewModel.draft.dailyStudyMinutes)m")
                            .font(TypographyTokens.metricValue)
                            .monospacedDigit()
                            .foregroundStyle(ColorTokens.action)
                    }
                }
                TextField("Timezone", text: $viewModel.draft.timezone)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .padding(SpacingTokens.sm)
                    .background(ColorTokens.surfaceElevated)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                            .stroke(ColorTokens.borderStrong, lineWidth: 1)
                    )
            }
        case .goal:
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                Text("What are you working toward?")
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.textPrimary)
                TextField("Learning goal", text: $viewModel.draft.goalContext, axis: .vertical)
                    .lineLimit(3...5)
                    .padding(SpacingTokens.sm)
                    .background(ColorTokens.surfaceElevated)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                            .stroke(ColorTokens.borderStrong, lineWidth: 1)
                    )
                Text("\(viewModel.draft.goalContext.count)/160")
                    .font(TypographyTokens.caption)
                    .monospacedDigit()
                    .foregroundStyle(viewModel.draft.goalContext.count <= 160 ? ColorTokens.textSecondary : ColorTokens.warning)
            }
        case .review:
            VStack(alignment: .leading, spacing: SpacingTokens.sm) {
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
            AppSurfaceCard(elevated: true) {
                HStack(spacing: SpacingTokens.sm) {
                    ProgressView()
                    Text("Loading profile")
                        .font(TypographyTokens.body)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
                .accessibilityLabel("Loading profile")
            }
        case .saving:
            AppSurfaceCard(elevated: true) {
                HStack(spacing: SpacingTokens.sm) {
                    ProgressView()
                    Text("Saving profile")
                        .font(TypographyTokens.body)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
                .accessibilityLabel("Saving profile")
            }
        case .offline:
            AppSurfaceCard(elevated: true) {
                AppNoticeBanner("You are offline", systemImage: "wifi.slash", tone: .warning)
                Button("Retry") {
                    Task { await viewModel.bootstrap() }
                }
                .buttonStyle(AppSecondaryButtonStyle())
            }
        case .failed:
            if let message = viewModel.message {
                AppNoticeBanner(LocalizedStringKey(message), systemImage: "exclamationmark.triangle", tone: .warning)
            }
        case .idle, .needsOnboarding, .ready:
            EmptyView()
        }
    }

    private var actionBar: some View {
        HStack(spacing: SpacingTokens.sm) {
            if viewModel.currentStep.rawValue > 0 {
                Button("Back") {
                    viewModel.currentStep = OnboardingStep(rawValue: viewModel.currentStep.rawValue - 1) ?? .education
                }
                .buttonStyle(AppSecondaryButtonStyle())
            }

            Button(viewModel.currentStep == .review ? "Finish" : "Continue") {
                Task {
                    if viewModel.currentStep == .review {
                        _ = await viewModel.completeOnboarding()
                    } else {
                        await viewModel.advance()
                    }
                }
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .disabled(viewModel.state == .loading || viewModel.state == .saving)
        }
        .padding(SpacingTokens.lg)
        .background(ColorTokens.surface)
    }

    private func row(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top, spacing: SpacingTokens.sm) {
            Text(label)
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
                .frame(width: 110, alignment: .leading)
            Text(value)
                .font(TypographyTokens.body)
                .monospacedDigit()
                .foregroundStyle(ColorTokens.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, SpacingTokens.xs)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(ColorTokens.borderSubtle)
                .frame(height: 1)
        }
    }
}
