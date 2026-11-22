import SwiftUI

struct CapabilityGate<AllowedContent: View, DeniedContent: View>: View {
    let entitlement: Entitlement?
    let capability: PremiumCapability
    @ViewBuilder let allowed: () -> AllowedContent
    @ViewBuilder let denied: () -> DeniedContent

    var body: some View {
        if entitlement?.allows(capability) == true {
            allowed()
        } else {
            denied()
        }
    }
}

struct UpgradeRequiredView: View {
    let capability: PremiumCapability

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Label(capability.title, systemImage: "lock")
                .foregroundStyle(ColorTokens.textPrimary)
            Text("\(capability.minimumTier.title) required")
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
        }
        .padding(SpacingTokens.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
    }
}

enum LearningOsDecision: Equatable, Sendable { case allowed, readOnlySummary, upgrade, pendingServerVerification }

extension EnvironmentValues {
    @Entry var learningOsAccess: EntitlementViewModel? = nil
}

struct LearningOsActionGate: ViewModifier {
    @Environment(\.learningOsAccess) private var access
    let capability: LearningOsCapability

    func body(content: Content) -> some View {
        let decision = access?.decision(capability) ?? .pendingServerVerification
        VStack(alignment: .leading, spacing: SpacingTokens.xs) {
            content.disabled(decision != .allowed)
            if decision != .allowed {
                HStack(alignment: .top, spacing: SpacingTokens.xs) {
                    Image(systemName: decision == .pendingServerVerification ? "clock.badge.checkmark" : "lock.circle")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                        .accessibilityHidden(true)
                    Text(decision == .pendingServerVerification
                         ? LocalizedStringKey("billing.verifyAccess")
                         : LocalizedStringKey("billing.historyRetained"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(SpacingTokens.sm)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(ColorTokens.surface)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                        .stroke(ColorTokens.borderSubtle, lineWidth: 1)
                )
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("billing.gate.\(capability.rawValue)")
            }
        }
    }
}

struct LearningOsStatusBannerView: View {
    let key: String

    var body: some View {
        let tone: AppBadgeTone = key == "billing.grace" ? .action : .warning
        HStack(alignment: .top, spacing: SpacingTokens.xs) {
            Image(systemName: key == "billing.grace" ? "checkmark.shield" : "info.circle")
                .font(TypographyTokens.caption)
                .foregroundStyle(tone.foreground)
                .accessibilityHidden(true)
            Text(LocalizedStringKey(key))
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(SpacingTokens.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(tone.background)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                .stroke(tone.foreground.opacity(0.28), lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("billing.statusBanner")
    }
}

struct QuotaUsageCardView: View {
    let summary: QuotaSummary

    var body: some View {
        AppSurfaceCard {
            AppSectionHeader(
                LocalizedStringKey("billing.quota.title"),
                subtitle: LocalizedStringKey("billing.quota.subtitle")
            )

            ViewThatFits(in: .horizontal) {
                HStack(alignment: .firstTextBaseline, spacing: SpacingTokens.sm) {
                    Text(LocalizedStringKey("billing.quota.remainingLabel"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                    Spacer(minLength: SpacingTokens.xs)
                    Text("\(summary.remainingAiChecks) / \(summary.aiVerificationsLimit)")
                        .font(TypographyTokens.monoCode)
                        .foregroundStyle(ColorTokens.textPrimary)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(LocalizedStringKey("billing.quota.remainingLabel"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("\(summary.remainingAiChecks) / \(summary.aiVerificationsLimit)")
                        .font(TypographyTokens.monoCode)
                        .foregroundStyle(ColorTokens.textPrimary)
                }
            }

            ProgressView(value: summary.usageProgress)
                .tint(ColorTokens.stepHighlight)
                .accessibilityLabel(LocalizedStringKey("billing.quota.title"))
                .accessibilityValue("\(summary.remainingAiChecks) / \(summary.aiVerificationsLimit)")

            ViewThatFits(in: .horizontal) {
                HStack(alignment: .firstTextBaseline, spacing: SpacingTokens.sm) {
                    Text(LocalizedStringKey("billing.quota.hybridLabel"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                    Spacer(minLength: SpacingTokens.xs)
                    Text("\(summary.hybridTwinsUsed) / \(summary.hybridTwinsLimit)")
                        .font(TypographyTokens.monoCode)
                        .foregroundStyle(ColorTokens.textPrimary)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(LocalizedStringKey("billing.quota.hybridLabel"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("\(summary.hybridTwinsUsed) / \(summary.hybridTwinsLimit)")
                        .font(TypographyTokens.monoCode)
                        .foregroundStyle(ColorTokens.textPrimary)
                }
            }

            if summary.deterministicTwinsUnlimited {
                HStack(alignment: .top, spacing: SpacingTokens.xs) {
                    Image(systemName: "checkmark.seal.fill")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.success)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(LocalizedStringKey("billing.quota.deterministicUnlimitedBadge"))
                            .font(TypographyTokens.caption.weight(.semibold))
                            .foregroundStyle(ColorTokens.textPrimary)
                            .fixedSize(horizontal: false, vertical: true)
                        Text(LocalizedStringKey("billing.quota.deterministicUnlimitedDetail"))
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .padding(SpacingTokens.sm)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(ColorTokens.successSoft)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                        .stroke(ColorTokens.success.opacity(0.28), lineWidth: 1)
                )
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("billing.quota.deterministicBadge")
            }

            if summary.costCircuitBreakerTripped {
                AppNoticeBanner(
                    LocalizedStringKey("billing.quota.circuitBreakerNotice"),
                    systemImage: "shield.checkmark",
                    tone: .stepHighlight
                )
                .accessibilityIdentifier("billing.quota.degradedNotice")
            } else if summary.degradedToDeterministicOnly {
                AppNoticeBanner(
                    LocalizedStringKey("billing.quota.exhaustedNotice"),
                    systemImage: "info.circle",
                    tone: .neutral
                )
                .accessibilityIdentifier("billing.quota.degradedNotice")
            }

            ViewThatFits(in: .horizontal) {
                HStack(alignment: .firstTextBaseline, spacing: SpacingTokens.sm) {
                    Text(LocalizedStringKey("billing.quota.resetsAtLabel"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                    Spacer(minLength: SpacingTokens.xs)
                    Text(formattedResetDate)
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textPrimary)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(LocalizedStringKey("billing.quota.resetsAtLabel"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(formattedResetDate)
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            Text(LocalizedStringKey("billing.quota.noOverageNote"))
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityIdentifier("billing.quota.card")
    }

    private var formattedResetDate: String {
        guard let date = summary.resetsAtDate else { return summary.resetsAt }
        return date.formatted(date: .abbreviated, time: .shortened)
    }
}
