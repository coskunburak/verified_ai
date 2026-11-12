import SwiftUI

public struct PracticeQuestionCardView: View {
    public let item: PracticeSessionItem
    public let remainingLockSeconds: Int
    public let isLockActive: Bool
    public let urgencyPhase: VerificationUrgencyPhase
    public let onHintRequested: () -> Void

    public init(
        item: PracticeSessionItem,
        remainingLockSeconds: Int,
        isLockActive: Bool,
        urgencyPhase: VerificationUrgencyPhase,
        onHintRequested: @escaping () -> Void
    ) {
        self.item = item
        self.remainingLockSeconds = remainingLockSeconds
        self.isLockActive = isLockActive
        self.urgencyPhase = urgencyPhase
        self.onHintRequested = onHintRequested
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Header: Slot Role & Difficulty Badge
            HStack {
                Text(LocalizedStringKey(item.slotRole.localizedTitleKey))
                    .font(.caption.bold())
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(slotBadgeColor.opacity(0.15))
                    .foregroundColor(slotBadgeColor)
                    .clipShape(Capsule())

                Spacer()

                Text(String(format: "ZPD: %.2f - %.2f", item.zpdWindow.lowerBound, item.zpdWindow.upperBound))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            // 60-Second Independent Verification Lock Banner (if twin)
            if item.isTwinVerification {
                HStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .stroke(Color.secondary.opacity(0.2), lineWidth: 3)
                            .frame(width: 36, height: 36)

                        if isLockActive {
                            Circle()
                                .trim(from: 0, to: CGFloat(remainingLockSeconds) / 60.0)
                                .stroke(timerColor, lineWidth: 3)
                                .frame(width: 36, height: 36)
                                .rotationEffect(.degrees(-90))

                            Text("\(remainingLockSeconds)")
                                .font(.system(size: 13, weight: .bold, design: .monospaced))
                                .foregroundColor(timerColor)
                        } else {
                            Image(systemName: "checkmark")
                                .font(.caption.bold())
                                .foregroundColor(.green)
                        }
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(isLockActive ? LocalizedStringKey("practice.lock.active") : LocalizedStringKey("practice.lock.unlocked"))
                            .font(.subheadline.bold())
                            .foregroundColor(isLockActive ? .primary : .secondary)

                        if isLockActive {
                            Text("Independent reasoning window")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                    }

                    Spacer()

                    Button(action: onHintRequested) {
                        HStack(spacing: 4) {
                            Image(systemName: isLockActive ? "lock.fill" : "lightbulb.fill")
                            Text(LocalizedStringKey("practice.action.hint"))
                        }
                        .font(.caption.bold())
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(isLockActive ? Color.secondary.opacity(0.1) : Color.orange.opacity(0.15))
                        .foregroundColor(isLockActive ? .secondary : .orange)
                        .clipShape(Capsule())
                    }
                    .disabled(isLockActive)
                }
                .padding(12)
                .background(Color(UIColor.secondarySystemBackground))
                .cornerRadius(12)
            }

            // Instruction
            Text(item.promptInstructionEn)
                .font(.body)
                .foregroundColor(.secondary)

            // LaTeX Math Problem Rendering via NativeMathLabel (ADR-010 Zero-WebView)
            VStack(alignment: .leading, spacing: 8) {
                NativeMathLabel(
                    latex: item.promptLatex,
                    fallback: item.promptLatex,
                    pointSize: 20
                )
                .frame(minHeight: 50)
                .padding(.vertical, 8)
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(UIColor.tertiarySystemBackground))
            .cornerRadius(12)
        }
        .padding(16)
        .background(Color(UIColor.systemBackground))
        .cornerRadius(16)
        .shadow(color: Color.black.opacity(0.05), radius: 6, x: 0, y: 2)
    }

    private var slotBadgeColor: Color {
        switch item.slotRole {
        case .trapClosure: return .purple
        case .spacedDrill: return .blue
        case .examSynthesis: return .green
        }
    }

    private var timerColor: Color {
        switch urgencyPhase {
        case .calm: return .blue
        case .focused: return .orange
        case .finalCountdown: return .red
        case .unlocked: return .green
        }
    }
}
