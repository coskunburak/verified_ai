import SwiftUI
/// Step-level evidence only. No invented token diff or inferred misconception.
struct FirstWrongStepDiffView: View {
    var code = "ALG_TRANSFORMATION_INVALID"
    var body: some View { Text(LocalizedStringKey("attempt.mistake." + code)).fixedSize(horizontal: false, vertical: true) }
}
