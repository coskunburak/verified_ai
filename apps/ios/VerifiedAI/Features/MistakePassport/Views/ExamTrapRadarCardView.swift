// apps/ios/VerifiedAI/Features/MistakePassport/Views/ExamTrapRadarCardView.swift
import SwiftUI

public struct ExamTrapRadarCardView: View {
    public let trap: ExamTrapItemDto
    public let onLaunchTwin: ((UUID) -> Void)?

    public init(trap: ExamTrapItemDto, onLaunchTwin: ((UUID) -> Void)? = nil) {
        self.trap = trap
        self.onLaunchTwin = onLaunchTwin
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.compact) {
            // Header: Category Pill & Status Badge
            HStack(spacing: SpacingTokens.sm) {
                Text(LocalizedStringKey(trap.localizedTrapTitleKey))
                    .font(TypographyTokens.sectionTitle)
                    .foregroundStyle(ColorTokens.textPrimary)

                Spacer()

                lifecycleBadge(trap.lifecycleStatus)
            }

            // Non-stigmatizing coaching framing description
            Text(LocalizedStringKey(trap.localizedCoachingFramingKey))
                .font(TypographyTokens.bodyMedium)
                .foregroundStyle(ColorTokens.textSecondary)
                .fixedSize(horizontal: false, vertical: true)

            Divider()
                .overlay(ColorTokens.borderSubtle)

            // 5-Metric mini indicators (Transfer Rate, Posterior Mastery, Retention)
            HStack(spacing: SpacingTokens.md) {
                metricColumn(
                    title: "Transfer",
                    value: trap.skillMetrics.independentCompetenceScore,
                    color: ColorTokens.action
                )
                metricColumn(
                    title: "Mastery",
                    value: trap.skillMetrics.posteriorMasteryProbability,
                    color: ColorTokens.success
                )
                metricColumn(
                    title: "Retention",
                    value: trap.skillMetrics.retentionProbability,
                    color: ColorTokens.textPrimary
                )
            }

            // Counterexample Preview (if attached)
            if let counterexample = trap.latestCounterexample {
                HStack(spacing: 6) {
                    Image(systemName: "function")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.accent)
                    Text(counterexample.witnessSubstitutionLatex)
                        .font(TypographyTokens.monoCode)
                        .foregroundStyle(ColorTokens.textSecondary)
                    Spacer()
                    Text(counterexample.cardId)
                        .font(TypographyTokens.microLabel)
                        .foregroundStyle(ColorTokens.accent)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(ColorTokens.accentSoft, in: Capsule())
                }
                .padding(SpacingTokens.sm)
                .background(
                    ColorTokens.surfaceElevated,
                    in: RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous)
                        .stroke(ColorTokens.borderSubtle, lineWidth: 1)
                )
            }

            // Active Twin Remediation Link CTA
            if let twinLink = trap.activeTwinLink, let onLaunchTwin {
                Button(action: { onLaunchTwin(twinLink.twinDrillId) }) {
                    Label(
                        LocalizedStringKey("counterexample.card.cta.prove_it_solo"),
                        systemImage: "shield.lefthalf.filled.badge.checkmark"
                    )
                }
                .buttonStyle(AppPrimaryButtonStyle())
                .accessibilityIdentifier("trap_card_launch_twin_\(trap.mistakeCode)")
            }
        }
        .padding(SpacingTokens.md)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                .strokeBorder(cardBorderColor(trap.lifecycleStatus), lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam_trap_card_\(trap.mistakeCode)")
    }

    private func metricColumn(title: String, value: Double, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(TypographyTokens.microLabel)
                .foregroundStyle(ColorTokens.textSecondary)
            Text("\(Int((value * 100).rounded()))%")
                .font(TypographyTokens.cardTitle)
                .monospacedDigit()
                .foregroundStyle(color)
        }
    }

    private func lifecycleBadge(_ status: TrapLifecycleStatus) -> some View {
        HStack(spacing: 4) {
            if status == .stableResolved {
                Image(systemName: "checkmark.seal.fill")
                    .font(TypographyTokens.microLabel)
            } else if status == .resolvedPendingRetention {
                Image(systemName: "lock.clock.fill")
                    .font(TypographyTokens.microLabel)
            }

            Text(LocalizedStringKey(status.localizedKey))
                .font(TypographyTokens.microLabel)
        }
        .padding(.horizontal, SpacingTokens.sm)
        .padding(.vertical, 4)
        .background(badgeBackground(status))
        .foregroundStyle(badgeColor(status))
        .clipShape(Capsule())
    }

    private func badgeColor(_ status: TrapLifecycleStatus) -> Color {
        switch status {
        case .recurringPattern: return ColorTokens.accent
        case .inRemediation: return ColorTokens.action
        case .resolvedPendingRetention: return ColorTokens.textPrimary
        case .stableResolved: return ColorTokens.success
        }
    }

    private func badgeBackground(_ status: TrapLifecycleStatus) -> Color {
        switch status {
        case .recurringPattern: return ColorTokens.accentSoft
        case .inRemediation: return ColorTokens.actionSoft
        case .resolvedPendingRetention: return ColorTokens.surfaceElevated
        case .stableResolved: return ColorTokens.successSoft
        }
    }

    private func cardBorderColor(_ status: TrapLifecycleStatus) -> Color {
        switch status {
        case .recurringPattern: return ColorTokens.accent.opacity(0.32)
        case .inRemediation: return ColorTokens.action.opacity(0.32)
        case .resolvedPendingRetention: return ColorTokens.borderStrong
        case .stableResolved: return ColorTokens.success.opacity(0.32)
        }
    }
}
