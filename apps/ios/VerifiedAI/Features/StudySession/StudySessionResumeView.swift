import SwiftUI

struct StudySessionResumeBannerView: View {
    let session: StudySessionStateModel
    let pendingOfflineCount: Int
    let onContinue: () -> Void
    let onStartFresh: () -> Void

    @ScaledMetric(relativeTo: .body) private var cardPadding: CGFloat = 16
    @ScaledMetric(relativeTo: .body) private var cornerRadius: CGFloat = 12
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            HStack(alignment: .top, spacing: SpacingTokens.sm) {
                Image(systemName: "play.circle.fill")
                    .font(.title3)
                    .foregroundStyle(ColorTokens.action)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                    Text(LocalizedStringKey("study_session.resume.banner_title"))
                        .font(.headline)
                        .foregroundStyle(ColorTokens.textPrimary)

                    Text(LocalizedStringKey("study_session.resume.banner_subtitle"))
                        .font(.subheadline)
                        .foregroundStyle(ColorTokens.textSecondary)

                    Text(String(
                        format: NSLocalizedString("study_session.progress.step_format", comment: "Current study session step progress"),
                        min(session.currentStepIndex + 1, session.totalSteps),
                        session.totalSteps
                    ))
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                }
            }

            if pendingOfflineCount > 0 {
                Label(
                    LocalizedStringKey("study_session.offline.saved_locally"),
                    systemImage: "arrow.triangle.2.circlepath"
                )
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.accent)
            }

            ViewThatFits(in: .horizontal) {
                HStack(spacing: SpacingTokens.sm) {
                    continueButton
                    startFreshButton
                }
                VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                    continueButton
                    startFreshButton
                }
            }
        }
        .padding(cardPadding)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .stroke(ColorTokens.border, lineWidth: 1)
        )
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: session.sessionId)
        .accessibilityElement(children: .contain)
    }

    private var continueButton: some View {
        Button(action: onContinue) {
            Text(LocalizedStringKey("study_session.resume.cta_continue"))
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, SpacingTokens.sm)
                .padding(.horizontal, SpacingTokens.md)
                .background(ColorTokens.action)
                .foregroundStyle(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var startFreshButton: some View {
        Button(action: onStartFresh) {
            Text(LocalizedStringKey("study_session.resume.cta_start_fresh"))
                .font(.subheadline.weight(.medium))
                .frame(maxWidth: .infinity)
                .padding(.vertical, SpacingTokens.sm)
                .padding(.horizontal, SpacingTokens.md)
                .background(ColorTokens.surfaceElevated)
                .foregroundStyle(ColorTokens.textPrimary)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

struct StudySessionOrchestratorBarView: View {
    @ObservedObject var coordinator: StudySessionCoordinator
    let onPause: () -> Void
    let onComplete: () -> Void

    @ScaledMetric(relativeTo: .body) private var barPadding: CGFloat = 12
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            if let summaryKey = coordinator.completionSummaryKey {
                HStack(spacing: SpacingTokens.sm) {
                    Image(systemName: "checkmark.seal.fill")
                        .foregroundStyle(ColorTokens.success)
                        .accessibilityHidden(true)
                    Text(LocalizedStringKey(summaryKey))
                        .font(.subheadline)
                        .foregroundStyle(ColorTokens.textPrimary)
                }
                .accessibilityElement(children: .combine)
            } else if let session = coordinator.activeSession {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: SpacingTokens.md) {
                        statusHeader(session: session)
                        Spacer(minLength: 0)
                        actionButtons
                    }
                    VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                        statusHeader(session: session)
                        actionButtons
                    }
                }
            }
        }
        .padding(barPadding)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                .stroke(ColorTokens.border, lineWidth: 1)
        )
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: coordinator.syncState)
    }

    private func statusHeader(session: StudySessionStateModel) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.xs) {
            Text(LocalizedStringKey(statusLocalizationKey(for: session.state)))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(ColorTokens.textPrimary)

            HStack(spacing: SpacingTokens.sm) {
                Text(String(
                    format: NSLocalizedString("study_session.progress.step_format", comment: "Step progress"),
                    min(session.currentStepIndex + 1, session.totalSteps),
                    session.totalSteps
                ))
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)

                Text(LocalizedStringKey(syncLocalizationKey))
                    .font(TypographyTokens.caption)
                    .foregroundStyle(syncColor)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var actionButtons: some View {
        HStack(spacing: SpacingTokens.sm) {
            Button(action: onPause) {
                Text(LocalizedStringKey("study_session.action.pause"))
                    .font(.caption.weight(.medium))
                    .padding(.vertical, SpacingTokens.xs)
                    .padding(.horizontal, SpacingTokens.sm)
                    .background(ColorTokens.surfaceElevated)
                    .foregroundStyle(ColorTokens.textPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous))
            }
            .buttonStyle(.plain)

            Button(action: onComplete) {
                Text(LocalizedStringKey("study_session.action.complete"))
                    .font(.caption.weight(.semibold))
                    .padding(.vertical, SpacingTokens.xs)
                    .padding(.horizontal, SpacingTokens.sm)
                    .background(ColorTokens.action)
                    .foregroundStyle(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous))
            }
            .buttonStyle(.plain)
        }
    }

    private func statusLocalizationKey(for state: StudySessionState) -> String {
        switch state {
        case .initialized, .active:
            return "study_session.status.active"
        case .paused:
            return "study_session.status.paused"
        case .completed, .abandoned:
            return "study_session.status.completed"
        }
    }

    private var syncLocalizationKey: String {
        switch coordinator.syncState {
        case .savedLocally:
            return "study_session.offline.saved_locally"
        case .syncing:
            return "study_session.offline.syncing"
        case .synced, .attention:
            return "study_session.offline.synced"
        }
    }

    private var syncColor: Color {
        switch coordinator.syncState {
        case .savedLocally, .syncing:
            return ColorTokens.accent
        case .synced:
            return ColorTokens.success
        case .attention:
            return ColorTokens.warning
        }
    }
}
