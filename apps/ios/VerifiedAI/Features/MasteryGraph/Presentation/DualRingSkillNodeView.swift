// apps/ios/VerifiedAI/Features/MasteryGraph/Presentation/DualRingSkillNodeView.swift
import SwiftUI

public struct DualRingSkillNodeView: View {
    public let skillNodeId: String
    public let skillTitle: String
    public let metrics: SkillFiveMetricsSnapshot
    public let activeExamTrapCount: Int
    public let onTapNode: () -> Void

    public init(
        skillNodeId: String,
        skillTitle: String,
        metrics: SkillFiveMetricsSnapshot,
        activeExamTrapCount: Int = 0,
        onTapNode: @escaping () -> Void
    ) {
        self.skillNodeId = skillNodeId
        self.skillTitle = skillTitle
        self.metrics = metrics
        self.activeExamTrapCount = activeExamTrapCount
        self.onTapNode = onTapNode
    }

    public var body: some View {
        Button(action: onTapNode) {
            ZStack {
                // Outer Ring: R_{u,s}(t) Retention Probability
                Circle()
                    .stroke(Color.teal.opacity(0.2), lineWidth: 6)
                    .frame(width: 76, height: 76)

                Circle()
                    .trim(from: 0, to: CGFloat(metrics.retentionProbability))
                    .stroke(Color.teal, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .frame(width: 76, height: 76)

                // Inner Ring: M_{u,s} Posterior Mastery Probability
                Circle()
                    .stroke(Color.indigo.opacity(0.2), lineWidth: 8)
                    .frame(width: 56, height: 56)

                Circle()
                    .trim(from: 0, to: CGFloat(metrics.posteriorMasteryProbability))
                    .stroke(Color.indigo, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .frame(width: 56, height: 56)

                // Center percentage text
                Text("\(Int((metrics.posteriorMasteryProbability * 100).rounded()))%")
                    .font(.caption2.bold())
                    .foregroundStyle(.primary)

                // Active Exam Trap Radar Badge
                if activeExamTrapCount > 0 {
                    ZStack {
                        Circle()
                            .fill(Color.orange)
                            .frame(width: 24, height: 24)
                        Text("\(activeExamTrapCount)")
                            .font(.caption2.bold())
                            .foregroundStyle(.white)
                    }
                    .offset(x: 28, y: -28)
                    .accessibilityIdentifier("dual_ring_active_trap_badge_\(skillNodeId)")
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityIdentifier("dual_ring_skill_node_\(skillNodeId)")
        .accessibilityLabel(accessibilityNodeDescription)
    }

    private var accessibilityNodeDescription: String {
        let masteryPct = Int((metrics.posteriorMasteryProbability * 100).rounded())
        let retentionPct = Int((metrics.retentionProbability * 100).rounded())
        let transferPct = Int((metrics.independentCompetenceScore * 100).rounded())
        return "\(skillTitle), Mastery: \(masteryPct)%, Retention: \(retentionPct)%, Transfer: \(transferPct)%, Active Traps: \(activeExamTrapCount)"
    }
}
