import Foundation
import Observation

@MainActor @Observable
final class AttemptLearningViewModel {
    private let api: AttemptLearningAPI
    private let owner: UUID
    private let problem: UUID
    private let ownsAccount: @MainActor () async -> Bool
    @ObservationIgnored private var task: Task<Void, Never>?
    private var generation = 0
    private(set) var busy = false
    private(set) var error = false
    private(set) var drills: [CounterfactualTwin] = []
    private(set) var passport: MistakePassport?
    private(set) var methods: [UUID: AttemptAlternativeMethods] = [:]
    var selectedDrill: CounterfactualTwin?
    init(api: AttemptLearningAPI, owner: UUID, problem: UUID, ownsAccount: @escaping @MainActor () async -> Bool) {
        self.api = api; self.owner = owner; self.problem = problem; self.ownsAccount = ownsAccount
    }
    func refresh() { run {
        let drills = try await self.api.twins(problem: self.problem)
        let passport = try await self.api.passport(problem: self.problem)
        return { self.drills = drills; self.passport = passport; if let selected = self.selectedDrill { self.selectedDrill = drills.first { $0.id == selected.id } } }
    } }
    func create(attempt: UUID) { run {
        let drill = try await self.api.create(problem: self.problem, attempt: attempt)
        return { self.replace(drill); self.selectedDrill = drill }
    } }
    func alternatives(attempt: UUID) { run {
        let methods = try await self.api.methods(problem: self.problem, attempt: attempt)
        return { self.methods[attempt] = methods }
    } }
    func answer(_ drill: CounterfactualTwin, choice: Int) {
        guard (0...3).contains(choice), drill.status == "OPEN" else { return }
        run {
            let result = try await self.api.answer(problem: self.problem, attempt: drill.attemptId, drill: drill.id, choice: choice)
            let passport = try? await self.api.passport(problem: self.problem)
            return { self.replace(result); self.selectedDrill = result; if let passport { self.passport = passport } }
        }
    }
    private func replace(_ drill: CounterfactualTwin) { drills.removeAll { $0.id == drill.id }; drills.append(drill) }
    func suspend() { generation += 1; task?.cancel(); task = nil; busy = false }
    func invalidate() { suspend(); drills = []; passport = nil; methods = [:]; selectedDrill = nil }
    private func run(_ work: @escaping @MainActor () async throws -> (@MainActor () -> Void)) {
        guard !busy else { return }; busy = true; error = false
        let expected = generation
        task = Task {
            let owns = await ownsAccount()
            guard !Task.isCancelled, expected == generation else { return }
            guard owns else { invalidate(); return }
            do {
                let commit = try await work()
                let stillOwns = await ownsAccount()
                guard !Task.isCancelled, expected == generation else { return }
                guard stillOwns else { invalidate(); return }
                commit()
            } catch {
                let stillOwns = await ownsAccount()
                guard !Task.isCancelled, expected == generation else { return }
                guard stillOwns else { invalidate(); return }
                self.error = true
            }
            if expected == generation { busy = false }
        }
    }
}
