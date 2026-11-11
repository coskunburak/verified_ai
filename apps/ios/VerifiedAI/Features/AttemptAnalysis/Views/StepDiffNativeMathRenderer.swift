// apps/ios/VerifiedAI/Features/AttemptAnalysis/Views/StepDiffNativeMathRenderer.swift
import SwiftUI

public enum MathHighlightRole: String, Sendable, CaseIterable {
    case witness
    case verifiedCorrect
    case trapStepDiff
    case contradiction
    case verifiedRule

    public var tintColor: Color {
        switch self {
        case .witness: return .purple
        case .verifiedCorrect: return .green
        case .trapStepDiff: return .orange
        case .contradiction: return .red
        case .verifiedRule: return .indigo
        }
    }
}

public struct Native2DMathExpressionView: View {
    public let latex: String
    public let highlightRole: MathHighlightRole

    public init(latex: String, highlightRole: MathHighlightRole = .witness) {
        self.latex = latex
        self.highlightRole = highlightRole
    }

    public var body: some View {
        HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 2)
                .fill(highlightRole.tintColor)
                .frame(width: 3)

            NativeMathLabel(
                latex: latex,
                fallback: latex,
                pointSize: 18
            )
            .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 3)
        .background(highlightRole.tintColor.opacity(0.08), in: RoundedRectangle(cornerRadius: 6))
    }
}
