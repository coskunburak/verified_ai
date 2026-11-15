import SwiftUI
/// Step-level evidence only. No invented token diff or inferred misconception.
struct FirstWrongStepDiffView: View {
    var code = "ALG_TRANSFORMATION_INVALID"
    var body: some View {
        HStack(alignment: .top, spacing: SpacingTokens.sm) {
            Image(systemName: "xmark.octagon.fill")
                .font(TypographyTokens.caption.weight(.semibold))
                .foregroundStyle(ColorTokens.danger)
            Text(LocalizedStringKey("attempt.mistake." + code))
                .font(TypographyTokens.caption.weight(.semibold))
                .foregroundStyle(ColorTokens.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(SpacingTokens.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ColorTokens.dangerSoft, in: RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous)
                .stroke(ColorTokens.danger.opacity(0.45), lineWidth: 1)
        )
    }
}
