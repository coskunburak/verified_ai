import SwiftUI
import UIKit

struct TutorView: View {
    @State var viewModel: TutorViewModel
    @Environment(\.scenePhase) private var scenePhase
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("tutor.persistenceOnly").accessibilityIdentifier("tutor.notice")
                if viewModel.busy { ProgressView("tutor.loading").accessibilityIdentifier("tutor.loading") }
                if let error = viewModel.errorKey {
                    Text(LocalizedStringKey(error)).accessibilityIdentifier("tutor.error")
                }
                if let session = viewModel.session {
                    if session.status == .closed { Text("tutor.closed").accessibilityIdentifier("tutor.closed") }
                    if !session.sourceCurrent { Text("tutor.stale").accessibilityIdentifier("tutor.stale") }
                    if viewModel.messages.isEmpty { Text("tutor.empty").accessibilityIdentifier("tutor.empty") }
                    ForEach(viewModel.messages) { message in
                        VStack(alignment: .leading) {
                            Text("tutor.learner").font(.caption)
                            Text(message.content).textSelection(.enabled)
                            Text("tutor.stored").font(.caption)
                        }
                        .accessibilityElement(children: .combine)
                        .accessibilityIdentifier("tutor.message.\(message.sequenceNumber)")
                    }
                    Button("tutor.close") { viewModel.close() }
                        .disabled(viewModel.busy || viewModel.pending != nil || session.status == .closed || viewModel.blocked)
                        .accessibilityIdentifier("tutor.close")
                }
                if !viewModel.sessions.isEmpty {
                    Menu("tutor.history") {
                        ForEach(viewModel.sessions) { session in
                            Button(session.createdAt) { viewModel.select(session.id) }
                        }
                    }.disabled(viewModel.busy || viewModel.pending != nil)
                }
                Button("tutor.start") { viewModel.start() }
                    .disabled(!viewModel.loaded || viewModel.busy || viewModel.pending != nil || viewModel.blocked)
                    .accessibilityIdentifier("tutor.start")
                Button(viewModel.pending == nil ? "tutor.refresh" : "tutor.retryAction") { viewModel.retry() }
                    .disabled(viewModel.busy || viewModel.blocked)
                    .accessibilityIdentifier("tutor.retry")
            }.padding()
        }
        .safeAreaInset(edge: .bottom) {
            VStack {
                TextField("tutor.composer", text: $viewModel.draft, axis: .vertical)
                    .lineLimit(1...5).textFieldStyle(.roundedBorder)
                    .disabled(viewModel.pending != nil || viewModel.blocked)
                    .accessibilityIdentifier("tutor.composer")
                Button(viewModel.busy ? "tutor.sending" : "tutor.send") { viewModel.send() }
                    .frame(minHeight: 44).disabled(!viewModel.canSend).accessibilityIdentifier("tutor.send")
            }.padding().background(.regularMaterial)
        }
        .buttonStyle(.bordered)
        .controlSize(.large)
        .navigationTitle("tutor.title")
        .task { viewModel.resume() }
        .onDisappear { viewModel.suspend() }
        .onChange(of: scenePhase) { _, phase in if phase == .active { viewModel.resume() } else { viewModel.suspend() } }
        .onChange(of: viewModel.errorKey) { _, key in
            if let key { UIAccessibility.post(notification: .announcement, argument: NSLocalizedString(key, comment: "Tutor error")) }
        }
    }
}
