import SwiftUI

struct CounterfactualTwinSheetView: View {
    let drill: CounterfactualTwin
    let busy: Bool
    let failure: Bool
    let refresh: () -> Void
    let submit: (Int) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var selected: Int?
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    TimelineView(.periodic(from: .now, by: 1)) { context in
                        let remaining = max(0, 60 - Int(context.date.timeIntervalSince(drill.createdAt)))
                        Text(String(format: NSLocalizedString("attempt.twin.timer", comment: "Practice time"), remaining))
                    }
                    Text("attempt.twin.timerHelp").font(.caption)
                    Text(verbatim: drill.domainLabel)
                    math(drill.problem)
                    ForEach(drill.options.indices, id: \.self) { index in
                        Button { selected = index } label: {
                            HStack { Image(systemName: selected == index ? "largecircle.fill.circle" : "circle"); math(drill.options[index]) }
                        }.buttonStyle(.bordered).disabled(busy || drill.status == "COMPLETED")
                    }
                    if drill.status == "OPEN" {
                        Button("attempt.twin.answer") { if let selected { submit(selected) } }
                            .disabled(selected == nil || busy).buttonStyle(.borderedProminent)
                    } else {
                        Label(drill.correct == true ? "attempt.twin.correct" : "attempt.twin.incorrect", systemImage: drill.correct == true ? "checkmark.circle" : "xmark.circle")
                        if let correct = drill.correctOption { math(drill.options[correct]) }
                        if let explanation = drill.explanationKey { Text(LocalizedStringKey(explanation)) }
                    }
                    if busy { ProgressView() }
                    if failure { Text("attempt.learning.unavailable"); Button("attempt.learning.refresh", action: refresh).disabled(busy) }
                }.padding()
            }.onAppear { selected = drill.selectedOption }
                .onChange(of: drill.selectedOption) { _, value in selected = value }
                .navigationTitle("attempt.twin.title")
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("attempt.twin.close") { dismiss() } } }
        }
    }
    private func math(_ value: AttemptJSON) -> some View {
        AttemptMathView(json: String(data: (try? JSONEncoder().encode(value)) ?? Data(), encoding: .utf8) ?? "", fallback: NSLocalizedString("attempt.math.expression", comment: "Math expression"))
    }
}

struct AttemptLearningPanel: View {
    @Bindable var model: AttemptLearningViewModel
    let attempt: StudentAttempt
    @Environment(\.scenePhase) private var scenePhase
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if attempt.analysis?.firstWrongStepIndex != nil {
                Button("attempt.twin.title") { model.create(attempt: attempt.id) }.disabled(model.busy)
            }
            Button("attempt.methods.title") { model.alternatives(attempt: attempt) }.disabled(model.busy)
            if let methods = model.methods[attempt.id] { AlternativeMethodComparisonView(attempt: attempt, evidence: methods.evidence) }
            if model.busy { ProgressView() }
            if model.error { Text(LocalizedStringKey(model.methodErrorKey ?? "attempt.learning.unavailable")) }
            Button("attempt.learning.refresh") { model.refresh() }.disabled(model.busy)
            if !model.drills.isEmpty {
                Text("attempt.twin.history").font(.headline)
                ForEach(model.drills) { drill in
                    Button { model.selectedDrill = drill } label: {
                        HStack { Text(drill.createdAt, style: .date); Text(LocalizedStringKey("attempt.twin." + drill.status)) }
                    }
                }
            }
            if let passport = model.passport {
                DisclosureGroup("attempt.passport.title") {
                    if passport.events.isEmpty { Text("attempt.passport.empty") }
                    ForEach(passport.events.reversed()) { event in
                        VStack(alignment: .leading) {
                            Text(LocalizedStringKey("attempt.mistake." + event.mistake_code))
                            Text(event.assessed_at, style: .date)
                            Text(event.correct ? "attempt.twin.correct" : "attempt.twin.incorrect")
                        }.padding(.vertical, 4)
                    }
                    ForEach(passport.mastery) { skill in
                        VStack(alignment: .leading) {
                            Text(LocalizedStringKey("attempt.mistake." + String(skill.skill_id.split(separator: ":").last ?? "")))
                            ProgressView(value: skill.score).accessibilityLabel("attempt.mastery.score")
                            Text(String(format: NSLocalizedString("attempt.mastery.evidence", comment: "Mastery evidence"), skill.evidence_count))
                            Text(String(format: NSLocalizedString("attempt.mastery.confidence", comment: "Evidence confidence"), Int(skill.confidence * 100)))
                            Text(skill.last_assessed_at, style: .date)
                        }
                    }
                }
            }
        }
        .task { model.refresh() }
        .onDisappear { model.suspend() }
        .onChange(of: scenePhase) { _, phase in if phase == .active { model.refresh() } else { model.suspend() } }
        .sheet(item: $model.selectedDrill) { drill in
            CounterfactualTwinSheetView(drill: drill, busy: model.busy, failure: model.error, refresh: { model.refresh() }) { model.answer(drill, choice: $0) }
        }
    }
}
