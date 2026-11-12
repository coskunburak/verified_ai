import SwiftUI

public struct SpacedReviewCardView: View {
    public let item: SpacedQueueItem
    public let onStartReview: () -> Void
    public let onDismiss: () -> Void

    public init(
        item: SpacedQueueItem,
        onStartReview: @escaping () -> Void,
        onDismiss: @escaping () -> Void = {}
    ) {
        self.item = item
        self.onStartReview = onStartReview
        self.onDismiss = onDismiss
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            headerRow

            skillTitleSection

            mathPreviewSection

            retentionPillRow

            actionButtonsRow
        }
        .padding(16)
        .background(Color(UIColor.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(badgeColor.opacity(0.3), lineWidth: item.triggerType == .trapImmunity7D ? 1.5 : 1.0)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("spaced.card.\(item.itemId.uuidString)")
    }

    private var headerRow: some View {
        HStack(spacing: 8) {
            Label(
                LocalizedStringKey(item.pedagogicalBadgeKey),
                systemImage: item.triggerType.systemIcon
            )
            .font(.caption.weight(.semibold))
            .foregroundStyle(badgeColor)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(badgeColor.opacity(0.12), in: Capsule())

            Spacer()

            if item.triggerType == .trapImmunity7D {
                HStack(spacing: 4) {
                    Image(systemName: "checkmark.shield.fill")
                        .font(.caption2)
                        .foregroundColor(.green)
                    Text("168h Verified")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(.green)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(Color.green.opacity(0.1), in: Capsule())
            }
        }
    }

    private var skillTitleSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(item.skillDisplayName)
                .font(.headline)
                .foregroundColor(.primary)
                .lineLimit(2)

            Text(item.canonicalSkillCode)
                .font(.caption2.monospaced())
                .foregroundColor(.secondary)
        }
    }

    private var mathPreviewSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Strict ADR-010 Compliance: Native 2D Math Rendering with Zero WebView
            NativeMathLabel(
                latex: item.promptPreviewLatex,
                fallback: item.promptPreviewLatex,
                pointSize: 18
            )
            .frame(minHeight: 44)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(UIColor.tertiarySystemBackground))
            .cornerRadius(10)
        }
    }

    private var retentionPillRow: some View {
        HStack {
            Text(String(format: NSLocalizedString("studyplan.spaced.retention.pill", comment: "Retention pill"), Int64(item.retentionPercentageInt)))
                .font(.caption.weight(.medium))
                .foregroundColor(.secondary)

            Spacer()

            Text(String(format: "H: %.1fd", item.halfLifeDays))
                .font(.caption.monospaced())
                .foregroundColor(.secondary)
        }
    }

    private var actionButtonsRow: some View {
        HStack(spacing: 12) {
            Button(action: onStartReview) {
                HStack(spacing: 6) {
                    Image(systemName: "arrow.triangle.2.circlepath")
                    Text(LocalizedStringKey("studyplan.spaced.action.start"))
                }
                .font(.subheadline.bold())
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(badgeColor)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }

            Button(action: onDismiss) {
                Image(systemName: "clock.arrow.2.circlepath")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .padding(10)
                    .background(Color(UIColor.tertiarySystemBackground))
                    .clipShape(Circle())
            }
            .accessibilityLabel("Schedule for later")
        }
    }

    private var badgeColor: Color {
        switch item.triggerType {
        case .trapImmunity7D:
            return .purple
        case .retentionDecay:
            return .blue
        }
    }
}
