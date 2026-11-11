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
        VStack(alignment: .leading, spacing: 12) {
            // Header: Category Pill & Status Badge
            HStack(spacing: 8) {
                Text(LocalizedStringKey(trap.localizedTrapTitleKey))
                    .font(.headline)
                    .foregroundStyle(.primary)

                Spacer()

                lifecycleBadge(trap.lifecycleStatus)
            }

            // Non-stigmatizing coaching framing description
            Text(LocalizedStringKey(trap.localizedCoachingFramingKey))
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            Divider()

            // 5-Metric mini indicators (Transfer Rate, Posterior Mastery, Retention)
            HStack(spacing: 16) {
                metricColumn(
                    title: "Transfer",
                    value: trap.skillMetrics.independentCompetenceScore,
                    color: .teal
                )
                metricColumn(
                    title: "Mastery",
                    value: trap.skillMetrics.posteriorMasteryProbability,
                    color: .indigo
                )
                metricColumn(
                    title: "Retention",
                    value: trap.skillMetrics.retentionProbability,
                    color: .blue
                )
            }

            // Counterexample Preview (if attached)
            if let counterexample = trap.latestCounterexample {
                HStack(spacing: 6) {
                    Image(systemName: "function")
                        .font(.caption)
                        .foregroundStyle(.orange)
                    Text(counterexample.witnessSubstitutionLatex)
                        .font(.caption.monospaced())
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text(counterexample.cardId)
                        .font(.caption2.bold())
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.orange.opacity(0.12), in: Capsule())
                }
                .padding(8)
                .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 8))
            }

            // Active Twin Remediation Link CTA
            if let twinLink = trap.activeTwinLink, let onLaunchTwin {
                Button(action: { onLaunchTwin(twinLink.twinDrillId) }) {
                    Label(
                        LocalizedStringKey("counterexample.card.cta.prove_it_solo"),
                        systemImage: "shield.lefthalf.filled.badge.checkmark"
                    )
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.indigo)
                .accessibilityIdentifier("trap_card_launch_twin_\(trap.mistakeCode)")
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color(.systemBackground))
                .shadow(color: .black.opacity(0.04), radius: 6, y: 3)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(cardBorderColor(trap.lifecycleStatus), lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam_trap_card_\(trap.mistakeCode)")
    }

    private func metricColumn(title: String, value: Double, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text("\(Int((value * 100).rounded()))%")
                .font(.caption.weight(.semibold))
                .foregroundStyle(color)
        }
    }

    private func lifecycleBadge(_ status: TrapLifecycleStatus) -> some View {
        HStack(spacing: 4) {
            if status == .stableResolved {
                Image(systemName: "checkmark.seal.fill")
                    .font(.caption2)
            } else if status == .resolvedPendingRetention {
                Image(systemName: "lock.clock.fill")
                    .font(.caption2)
            }

            Text(LocalizedStringKey(status.localizedKey))
                .font(.caption2.bold())
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(badgeColor(status).opacity(0.15))
        .foregroundStyle(badgeColor(status))
        .clipShape(Capsule())
    }

    private func badgeColor(_ status: TrapLifecycleStatus) -> Color {
        switch status {
        case .recurringPattern: return .orange
        case .inRemediation: return .purple
        case .resolvedPendingRetention: return .blue
        case .stableResolved: return .green
        }
    }

    private func cardBorderColor(_ status: TrapLifecycleStatus) -> Color {
        switch status {
        case .recurringPattern: return Color.orange.opacity(0.3)
        case .inRemediation: return Color.purple.opacity(0.3)
        case .resolvedPendingRetention: return Color.blue.opacity(0.2)
        case .stableResolved: return Color.green.opacity(0.25)
        }
    }
}
