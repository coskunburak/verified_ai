import SwiftUI
import UIKit

struct TutorView: View {
    @State var viewModel: TutorViewModel
    @Environment(\.scenePhase) private var scenePhase
    @FocusState private var composerFocused: Bool
    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("tutor.socraticNotice").accessibilityIdentifier("tutor.notice")
                    if viewModel.busy { ProgressView(LocalizedStringKey(viewModel.pending?.kind == .turn ? "tutor.responsePending" : "tutor.loading")).accessibilityIdentifier("tutor.loading") }
                    if let error = viewModel.errorKey {
                        Text(LocalizedStringKey(error)).accessibilityIdentifier("tutor.error")
                    }
                    if let session = viewModel.session {
                        Text(LocalizedStringKey(viewModel.phase.localizationKey)).font(.headline).accessibilityIdentifier("tutor.phase")
                        if viewModel.phase == .clarifyGoal { Text("tutor.goalPrompt").accessibilityIdentifier("tutor.goal") }
                        Text("tutor.uncertain").font(.caption).accessibilityIdentifier("tutor.uncertain")
                        if viewModel.phase.terminal { Text("tutor.stopped").accessibilityIdentifier("tutor.stopped") }
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
                            if let turn = viewModel.turns.first(where: { $0.learnerMessageId == message.id }) {
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("tutor.speaker").font(.caption.bold())
                                    Text(turn.content).textSelection(.enabled)
                                    Text("tutor.deterministic").font(.caption)
                                }
                                .padding().frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
                                .accessibilityElement(children: .combine)
                                .accessibilityIdentifier("tutor.reply.\(turn.learnerSequenceNumber)")
                            }
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
            .accessibilityIdentifier("tutor.conversation")
            .scrollDismissesKeyboard(.interactively)
            VStack {
                if viewModel.session?.status == .closed {
                    Text("tutor.closed")
                        .font(.callout)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityIdentifier("tutor.closed")
                }
                Picker("tutor.intent", selection: $viewModel.selectedIntent) {
                    ForEach(TutorIntent.allCases, id: \.self) { intent in
                        Text(LocalizedStringKey(intent.localizationKey)).tag(intent)
                    }
                }.pickerStyle(.menu).disabled(viewModel.busy || viewModel.pending != nil)
                    .accessibilityIdentifier("tutor.intent")
                TextField("tutor.composer", text: $viewModel.draft, axis: .vertical)
                    .lineLimit(1...5).textFieldStyle(.roundedBorder)
                    .focused($composerFocused)
                    .disabled(viewModel.pending != nil || viewModel.blocked)
                    .accessibilityIdentifier("tutor.composer")
                Button(viewModel.busy ? "tutor.sending" : "tutor.send") {
                    composerFocused = false
                    viewModel.send()
                }
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
