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
        VStack(alignment: .leading, spacing: SpacingTokens.compact) {
            HStack(spacing: SpacingTokens.xs) {
                Image(systemName: "function")
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.warning)
                Text(LocalizedStringKey("counterexample.card.header.title"))
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Text(snapshot.cardId)
                    .font(TypographyTokens.microLabel.monospaced())
                    .foregroundStyle(ColorTokens.warning)
                    .padding(.horizontal, SpacingTokens.xs)
                    .padding(.vertical, 3)
                    .background(ColorTokens.warningSoft, in: Capsule())
            }

            VStack(alignment: .leading, spacing: 6) {
                Text(LocalizedStringKey("counterexample.card.witness.prompt"))
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                Native2DMathExpressionView(latex: snapshot.witnessSubstitutionLatex, highlightRole: .witness)
            }

            Rectangle()
                .fill(ColorTokens.borderSubtle)
                .frame(height: 1)

            HStack(alignment: .top, spacing: SpacingTokens.sm) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(LocalizedStringKey("counterexample.card.lhs.actual"))
                        .font(TypographyTokens.microLabel)
                        .foregroundStyle(ColorTokens.success)
                    Native2DMathExpressionView(latex: snapshot.lhsEvaluatedLatex, highlightRole: .verifiedCorrect)
                }
                .padding(SpacingTokens.sm)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(ColorTokens.successSoft, in: RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    Text(LocalizedStringKey("counterexample.card.rhs.trap"))
                        .font(TypographyTokens.microLabel)
                        .foregroundStyle(ColorTokens.danger)
                    Native2DMathExpressionView(latex: snapshot.rhsFaultyEvaluatedLatex, highlightRole: .trapStepDiff)
                }
                .padding(SpacingTokens.sm)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(ColorTokens.dangerSoft, in: RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous))
            }

            VStack(alignment: .leading, spacing: 6) {
                Native2DMathExpressionView(latex: snapshot.contradictionStatementLatex, highlightRole: .contradiction)
                Text(LocalizedStringKey("counterexample.card.valid_rule.label"))
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                Native2DMathExpressionView(latex: snapshot.validStepLatex, highlightRole: .verifiedRule)
            }
            .padding(SpacingTokens.compact)
            .background(ColorTokens.surfaceElevated, in: RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                    .stroke(ColorTokens.borderSubtle, lineWidth: 1)
            )

            if let onLaunchTwinChallenge {
                Button(action: onLaunchTwinChallenge) {
                    Label(
                        LocalizedStringKey("counterexample.card.cta.prove_it_solo"),
                        systemImage: "shield.lefthalf.filled.badge.checkmark"
                    )
                }
                .buttonStyle(AppPrimaryButtonStyle())
                .accessibilityIdentifier("counterexample_launch_twin_cta")
            }
        }
        .padding(SpacingTokens.md)
        .background(
            RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                .fill(ColorTokens.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                .strokeBorder(ColorTokens.warning.opacity(0.45), lineWidth: 1)
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
