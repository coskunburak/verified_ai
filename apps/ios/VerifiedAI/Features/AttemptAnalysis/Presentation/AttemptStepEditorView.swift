import SwiftUI
struct AttemptStepEditorView: View {
    @Binding var text: String
    let locked: Bool
    @FocusState private var focused: Bool
    var body: some View {
        VStack(alignment: .leading) {
            Text("attempt.editor.help")
            TextEditor(text: $text).font(.body.monospaced()).frame(minHeight: 180)
                .focused($focused).onChange(of: locked) { _, value in if value { focused = false } }
                .autocorrectionDisabled().textInputAutocapitalization(.never).disabled(locked)
                .accessibilityLabel("attempt.editor.label").accessibilityIdentifier("attempt.editor")
        }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("attempt.keyboard.done") { focused = false }
                    .accessibilityIdentifier("attempt.keyboard.done")
            }
        }
    }
}
