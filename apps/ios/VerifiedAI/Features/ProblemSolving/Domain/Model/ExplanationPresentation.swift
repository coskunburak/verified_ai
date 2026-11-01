import Foundation

/// A projection of an already validated candidate. Never evaluates claims or creates evidence.
struct ExplanationPresentation: Equatable, Sendable {
    let candidate: SolutionCandidate
    let mode: ExplanationDepth

    var visibleSteps: [SolutionStep] {
        mode == .quick ? Array(candidate.steps.suffix(1)) : candidate.steps
    }
    var omittedStepCount: Int { candidate.steps.count - visibleSteps.count }
    var usesIndividualDisclosure: Bool { mode == .beginner }
    var availabilityKey: String { "explanation.availability.\(mode.rawValue)" }

    /// These are vocabulary definitions, never candidate-specific mathematical assertions.
    /// Catalog v1 is deliberately limited to expression evaluation and equation terminology.
    var prerequisiteKey: String? {
        guard mode == .beginner else { return nil }
        switch (candidate.problemType, candidate.taskType, candidate.method) {
        case (.arithmetic, .evaluate, .arithmetic): return "explanation.term.expression"
        case (.equation, .equation, .isolate), (.equation, .equation, .algebra):
            return "explanation.term.equation"
        default: return nil
        }
    }

    func dependencies(for step: SolutionStep) -> [SolutionStep] {
        guard mode == .deep else { return [] }
        return candidate.steps.filter { step.dependsOn.contains($0.id) }
    }
}
