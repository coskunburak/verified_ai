import Foundation
import Observation

enum MethodComparisonFailure: String, Sendable {
    case network, unavailable, stale, authorization, invalid, request, rateLimit
    var key: String { "attempt.methods.error." + rawValue }
    var recoverable: Bool { ![Self.authorization, .invalid, .request].contains(self) }
    static func classify(_ error: Error) -> Self {
        if let error = error as? NetworkError {
            switch error {
            case .transport: return .network
            case .server(let problem):
                if [401, 403, 404].contains(problem.status) { return .authorization }
                if problem.status == 409 { return .stale }
                if problem.status == 429 { return .rateLimit }
                if (500...599).contains(problem.status) { return .unavailable }
                return .request
            case .httpStatus(let status):
                if [401, 403, 404].contains(status) { return .authorization }
                if status == 409 { return .stale }
                if status == 429 { return .rateLimit }
                return (500...599).contains(status) ? .unavailable : .request
            default: return .invalid
            }
        }
        if error is URLError { return .network }
        return .invalid
    }
}
enum MethodComparisonState: Equatable {
    case idle, loading, ready(String), failed(MethodComparisonFailure)
}

@MainActor @Observable
final class AttemptLearningViewModel {
    private let api: any AttemptLearningServicing
    private let owner: UUID
    private let problem: UUID
    private let ownsAccount: @MainActor () async -> Bool
    @ObservationIgnored private var task: Task<Void, Never>?
    private var generation = 0
    private(set) var busy = false
    private(set) var error = false
    private(set) var drills: [CounterfactualTwin] = []
    private(set) var passport: MistakePassport?
    private(set) var methods: [UUID: MethodComparison] = [:]
    private(set) var sourceMethods: MethodComparison?
    private(set) var methodState: MethodComparisonState = .idle
    var methodErrorKey: String? { if case .failed(let failure) = methodState { return failure.key }; return nil }
    var canRetryMethods: Bool { if case .failed(let failure) = methodState { return failure.recoverable }; return true }
    var selectedMethodID = ""
    var compactMethods = false
    private let logger = AppLogger(subsystem: "com.verifiedai.learning", category: "method-comparison")
    var selectedDrill: CounterfactualTwin?
    init(api: any AttemptLearningServicing, owner: UUID, problem: UUID, ownsAccount: @escaping @MainActor () async -> Bool) {
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
    func alternatives(attempt: StudentAttempt) {
        guard !busy else { return }
        methods[attempt.id] = nil
        run(methodOperation: true) {
            let methods = try await self.api.comparison(problem: self.problem, attempt: attempt.id, source: attempt.source)
            return { self.methods[attempt.id] = methods; self.methodState = .ready(methods.evidence.status) }
        }
    }
    func sourceAlternatives() {
        guard !busy else { return }
        sourceMethods = nil
        run(methodOperation: true) {
            let context = try await self.api.context(problem: self.problem)
            let methods = try await self.api.comparison(problem: self.problem, attempt: nil, source: context.source)
            return { self.sourceMethods = methods; self.methodState = .ready(methods.evidence.status) }
        }
    }
    func answer(_ drill: CounterfactualTwin, choice: Int) {
        guard (0...3).contains(choice), drill.status == "OPEN" else { return }
        run {
            let result = try await self.api.answer(problem: self.problem, attempt: drill.attemptId, drill: drill.id, choice: choice)
            let passport = try? await self.api.passport(problem: self.problem)
            return { self.replace(result); self.selectedDrill = result; if let passport { self.passport = passport } }
        }
    }
    private func replace(_ drill: CounterfactualTwin) { drills.removeAll { $0.id == drill.id }; drills.append(drill) }
    func suspend() { generation += 1; task?.cancel(); task = nil; busy = false; sourceMethods = nil; methods = [:]; methodState = .idle }
    func invalidate() { suspend(); drills = []; passport = nil; methods = [:]; selectedDrill = nil; selectedMethodID = ""; compactMethods = false }
    private func run(methodOperation: Bool = false, _ work: @escaping @MainActor () async throws -> (@MainActor () -> Void)) {
        guard !busy else { return }; busy = true; error = false
        let retry: Bool
        if case .failed = methodState { retry = true } else { retry = false }
        if methodOperation { methodState = .loading }
        let started = ContinuousClock.now
        let operation = UUID().uuidString
        let expected = generation
        task = Task {
            let owns = await ownsAccount()
            guard !Task.isCancelled, expected == generation else { return }
            guard owns else { invalidate(); return }
            if methodOperation { logger.info("method_comparison operation=\(operation) phase=start retry=\(retry)") }
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
                let failure = MethodComparisonFailure.classify(error)
                if failure == .authorization { invalidate() }
                if methodOperation { self.methodState = .failed(failure) }
            }
            if methodOperation {
                let outcome: String
                switch methodState {
                case .ready(let value): outcome = value
                case .failed(let failure): outcome = failure.rawValue
                default: outcome = "cancelled"
                }
                logger.info("method_comparison operation=\(operation) outcome=\(outcome) elapsed=\(started.duration(to: .now))")
            }
            if expected == generation { busy = false }
        }
    }
}
