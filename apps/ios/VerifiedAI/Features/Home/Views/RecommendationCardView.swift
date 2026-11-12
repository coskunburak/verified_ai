import SwiftUI

public struct RecommendationCardView: View {
    public let recommendation: LearnerRecommendationDto
    public let onAccept: () -> Void
    public let onDismiss: () -> Void

    public init(
        recommendation: LearnerRecommendationDto,
        onAccept: @escaping () -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.recommendation = recommendation
        self.onAccept = onAccept
        self.onDismiss = onDismiss
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            headerRow

            skillInfoSection

            mathPreviewSection

            rationaleBox

            actionsRow
        }
        .padding(SpacingTokens.md)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
        .overlay {
            RoundedRectangle(cornerRadius: RadiusTokens.medium)
                .stroke(urgencyBorderColor, lineWidth: recommendation.urgencyLevel == .high ? 1.5 : 1)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("recommendation.card.\(recommendation.id.uuidString)")
    }

    private var headerRow: some View {
        HStack {
            Label(
                LocalizedStringKey(recommendation.recommendationType.localizedKey),
                systemImage: recommendation.recommendationType.systemIcon
            )
            .font(TypographyTokens.caption.weight(.semibold))
            .foregroundStyle(badgeColor)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(badgeColor.opacity(0.12), in: Capsule())

            Spacer()

            VStack(alignment: .trailing, spacing: 2) {
                Text("\(Int(recommendation.priorityScore))%")
                    .font(.system(.caption, design: .monospaced).weight(.bold))
                    .foregroundStyle(urgencyBorderColor)
                ProgressView(value: recommendation.priorityScore, total: 100.0)
                    .progressViewStyle(LinearProgressViewStyle(tint: urgencyBorderColor))
                    .frame(width: 54)
            }
            .accessibilityLabel("Priority score \(Int(recommendation.priorityScore)) percent")
        }
    }

    private var skillInfoSection: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.xs) {
            Text(recommendation.targetSkillCode)
                .font(.system(.headline, design: .rounded).weight(.semibold))
                .foregroundStyle(ColorTokens.textPrimary)
                .lineLimit(2)

            if let mistake = recommendation.targetMistakeCode {
                HStack(spacing: 4) {
                    Image(systemName: "exclamationmark.shield")
                    Text(mistake)
                }
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.accent)
            }
        }
    }

    private var mathPreviewSection: some View {
        HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 2)
                .fill(badgeColor)
                .frame(width: 3)

            Text(recommendation.mathPreviewLatex)
                .font(.system(.subheadline, design: .serif).italic())
                .foregroundStyle(ColorTokens.textSecondary)
                .lineLimit(1)
        }
        .padding(.horizontal, SpacingTokens.sm)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))
    }

    private var rationaleBox: some View {
        HStack(alignment: .top, spacing: SpacingTokens.sm) {
            Image(systemName: "sparkles")
                .font(.caption)
                .foregroundStyle(ColorTokens.action)
                .padding(.top, 2)

            Text(recommendation.explainableRationaleText)
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(SpacingTokens.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ColorTokens.action.opacity(0.06))
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))
    }

    private var actionsRow: some View {
        HStack(spacing: SpacingTokens.sm) {
            Button(action: onAccept) {
                Label("Start Practice", systemImage: "arrow.right.circle.fill")
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .frame(height: 40)
            }
            .buttonStyle(.borderedProminent)
            .tint(ColorTokens.action)
            .accessibilityIdentifier("recommendation.action.accept")

            Button(action: onDismiss) {
                Text("Later")
                    .font(.subheadline)
                    .frame(minWidth: 64)
                    .frame(height: 40)
            }
            .buttonStyle(.bordered)
            .tint(ColorTokens.textSecondary)
            .accessibilityIdentifier("recommendation.action.dismiss")
        }
    }

    private var badgeColor: Color {
        switch recommendation.recommendationType {
        case .prerequisiteRepair: return ColorTokens.accent
        case .mistakeRepair: return ColorTokens.warning
        case .spacedRetention: return Color.blue
        case .highRoiTopic: return ColorTokens.action
        }
    }

    private var urgencyBorderColor: Color {
        switch recommendation.urgencyLevel {
        case .high: return ColorTokens.action
        case .medium: return ColorTokens.accent
        case .standard: return ColorTokens.border
        }
    }
}
