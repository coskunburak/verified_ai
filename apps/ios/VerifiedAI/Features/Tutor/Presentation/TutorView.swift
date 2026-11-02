import SwiftUI
import UIKit

struct TutorView: View {
    @State var viewModel: TutorViewModel
    @Environment(\.scenePhase) private var scenePhase
    @FocusState private var composerFocused: Bool
    @State private var revealConfirmation = false
    @AccessibilityFocusState private var focusedHint: UUID?
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
                        if let hints = viewModel.hintHistory {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("tutor.hints.title").font(.headline)
                                if viewModel.isSyntheticFixture {
                                    Text("tutor.hints.synthetic").font(.caption).accessibilityIdentifier("tutor.hints.synthetic")
                                }
                                Text("tutor.hints.assistance").font(.caption)
                                Text(LocalizedStringKey(hints.currentLevel?.localizationKey ?? "tutor.hint.NONE"))
                                    .accessibilityIdentifier("tutor.hints.current")
                                ForEach(hints.hints) { hint in
                                    VStack(alignment: .leading) {
                                        Text(LocalizedStringKey(hint.level.localizationKey)).font(.subheadline.bold())
                                        Text(hint.content).textSelection(.enabled)
                                    }
                                    .accessibilityElement(children: .combine)
                                    .accessibilityFocused($focusedHint, equals: hint.id)
                                    .accessibilityIdentifier("tutor.hint.\(hint.level.rawValue)")
                                }
                                if let next = hints.nextLevel {
                                    Button { composerFocused = false; viewModel.requestHint() } label: {
                                        Text("tutor.hints.request") + Text(" · ") + Text(LocalizedStringKey(next.localizationKey))
                                    }
                                    .frame(minHeight: 44).disabled(!viewModel.canRequestHint)
                                    .accessibilityIdentifier("tutor.hints.request")
                                } else {
                                    Text(LocalizedStringKey(hints.availability.localizationKey))
                                        .accessibilityIdentifier("tutor.hints.status")
                                }
                                Button("tutor.reveal.request") { composerFocused = false; revealConfirmation = true }
                                    .frame(minHeight: 44).disabled(!viewModel.canRequestReveal)
                                    .accessibilityIdentifier("tutor.reveal.request")
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
        .confirmationDialog("tutor.reveal.confirmation", isPresented: $revealConfirmation, titleVisibility: .visible) {
            Button("tutor.reveal.confirm") { viewModel.confirmReveal() }
            Button("tutor.reveal.cancel", role: .cancel) {}
        }
        .onChange(of: viewModel.hintHistory?.hints.last?.id) { _, id in
            focusedHint = id
            if id != nil { UIAccessibility.post(notification: .announcement, argument: NSLocalizedString("tutor.hints.delivered", comment: "Hint delivered")) }
        }
        .onChange(of: viewModel.errorKey) { _, key in
            if let key { UIAccessibility.post(notification: .announcement, argument: NSLocalizedString(key, comment: "Tutor error")) }
        }
    }
}
