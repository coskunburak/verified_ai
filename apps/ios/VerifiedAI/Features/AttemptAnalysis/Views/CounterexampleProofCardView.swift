// apps/ios/VerifiedAI/Features/AttemptAnalysis/Views/CounterexampleProofCardView.swift
import SwiftUI

public struct CounterexampleProofCardView: View {
    public let snapshot: CounterexampleProofSnapshot
    public let onLaunchTwinChallenge: (() -> Void)?
    @Environment(\.locale) private var locale

    public init(
        snapshot: CounterexampleProofSnapshot,
        onLaunchTwinChallenge: (() -> Void)? = nil
    ) {
        self.snapshot = snapshot
        self.onLaunchTwinChallenge = onLaunchTwinChallenge
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                Image(systemName: "function")
                    .font(.headline)
                    .foregroundStyle(.orange)
                Text(LocalizedStringKey("counterexample.card.header.title"))
                    .font(.subheadline.weight(.semibold))
                Spacer()
                Text(snapshot.cardId)
                    .font(.caption2.monospaced())
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.orange.opacity(0.15), in: Capsule())
            }

            VStack(alignment: .leading, spacing: 6) {
                Text(LocalizedStringKey("counterexample.card.witness.prompt"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Native2DMathExpressionView(latex: snapshot.witnessSubstitutionLatex, highlightRole: .witness)
            }

            Divider()

            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(LocalizedStringKey("counterexample.card.lhs.actual"))
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(.green)
                    Native2DMathExpressionView(latex: snapshot.lhsEvaluatedLatex, highlightRole: .verifiedCorrect)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                VStack(alignment: .leading, spacing: 4) {
                    Text(LocalizedStringKey("counterexample.card.rhs.trap"))
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(.orange)
                    Native2DMathExpressionView(latex: snapshot.rhsFaultyEvaluatedLatex, highlightRole: .trapStepDiff)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            VStack(alignment: .leading, spacing: 6) {
                Native2DMathExpressionView(latex: snapshot.contradictionStatementLatex, highlightRole: .contradiction)
                Text(LocalizedStringKey("counterexample.card.valid_rule.label"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Native2DMathExpressionView(latex: snapshot.validStepLatex, highlightRole: .verifiedRule)
            }
            .padding(10)
            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 10))

            if let onLaunchTwinChallenge {
                Button(action: onLaunchTwinChallenge) {
                    Label(
                        LocalizedStringKey("counterexample.card.cta.prove_it_solo"),
                        systemImage: "shield.lefthalf.filled.badge.checkmark"
                    )
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.indigo)
                .accessibilityIdentifier("counterexample_launch_twin_cta")
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(.systemBackground))
                .shadow(color: .black.opacity(0.06), radius: 8, y: 4)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(Color.orange.opacity(0.35), lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("counterexample_proof_card_H01")
        .accessibilityLabel(voiceOverMathematicalSummary)
    }

    private var voiceOverMathematicalSummary: String {
        let identifier = locale.identifier.lowercased()
        let isTurkish = identifier.starts(with: "tr")
        return isTurkish ? snapshot.accessibilitySpeechTr : snapshot.accessibilitySpeechEn
    }
}
