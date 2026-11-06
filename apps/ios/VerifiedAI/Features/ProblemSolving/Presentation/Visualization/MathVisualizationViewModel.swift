import Foundation
import Observation

@MainActor @Observable
final class MathVisualizationViewModel {
    enum State: Equatable { case idle, loading, ready, unsupported, unavailable, stale, offline, rateLimited, recoverableFailure, unauthorized }
    private(set) var state: State = .idle
    private(set) var value: MathVisualization?
    private(set) var viewport = VisualizationViewport()
    var kind: VisualizationKind = .function
    var showFunction = true
    var showDerivative = true
    var selectedX: Double?
    private let api: any MathVisualizationServicing
    private let problem: UUID
    private let ownsAccount: @MainActor () async -> Bool
    @ObservationIgnored private var task: Task<Void, Never>?
    private var generation = 0
    private let logger = AppLogger(subsystem: "com.verifiedai.learning", category: "visualization")
    init(api: any MathVisualizationServicing, problem: UUID, ownsAccount: @escaping @MainActor () async -> Bool) {
        self.api = api; self.problem = problem; self.ownsAccount = ownsAccount
    }
    var errorKey: String {
        switch state {
        case .unsupported: return value?.result.reasonCode == "PROVENANCE_UNAVAILABLE" ? "visualization.provenance" : "visualization.unsupported"
        case .offline: return "visualization.offline"
        case .stale: return "visualization.stale"
        case .rateLimited: return "visualization.rateLimit"
        case .recoverableFailure: return "visualization.invalid"
        case .unauthorized: return "visualization.unauthorized"
        default: return "visualization.unavailable"
        }
    }
    var visibleSegments: [MathVisualization.Segment] {
        (value?.result.segments ?? []).filter { $0.curve == "FUNCTION" ? showFunction : showDerivative }
    }
    var selectedSample: MathVisualization.Sample? {
        guard let selectedX else { return nil }
        var nearest: MathVisualization.Sample?
        for segment in visibleSegments {
            for point in segment.points where nearest == nil || abs(point.x - selectedX) < abs(nearest!.x - selectedX) {
                nearest = point
            }
        }
        return nearest
    }
    func cancel() {
        generation += 1; task?.cancel(); task = nil; value = nil; selectedX = nil
        if state != .unauthorized { state = .idle }
    }
    func load() {
        guard task == nil, state != .unauthorized else { return }
        if state != .idle { logger.info("visualization.explicit_retry") }
        generation += 1; let current = generation; let requestedKind = kind; let requestedViewport = viewport
        value = nil; selectedX = nil; state = .loading
        task = Task { [weak self] in
            guard let self else { return }
            do {
                guard await ownsAccount() else { if current == generation { state = .unauthorized; task = nil }; return }
                guard !Task.isCancelled, current == generation else { return }
                let source = try await api.context(problem: problem)
                let response = try await api.load(problem: problem, source: source, kind: requestedKind, viewport: requestedViewport)
                let valid = try response.validated(problem: problem, source: source, kind: requestedKind, viewport: requestedViewport)
                let owned = await ownsAccount()
                guard !Task.isCancelled, current == generation else { return }
                guard owned else { state = .unauthorized; task = nil; return }
                value = valid
                switch valid.result.status {
                case .supported: state = .ready
                case .unsupported: state = .unsupported
                case .unavailable: state = .unavailable
                }
            } catch {
                guard !Task.isCancelled, current == generation else { return }
                let owned = await ownsAccount()
                guard !Task.isCancelled, current == generation else { return }
                if !owned { state = .unauthorized }
                else {
                    switch MethodComparisonFailure.classify(error) {
                    case .network: state = .offline
                    case .stale: state = .stale
                    case .authorization: state = .unauthorized
                    case .unavailable: state = .unavailable
                    case .rateLimit: state = .rateLimited
                    default: state = .recoverableFailure
                    }
                }
            }
            if current == generation { task = nil }
        }
    }
    func changeKind() { cancel(); load() }
    func changeViewport(_ next: VisualizationViewport) {
        guard next.valid, next != viewport else { return }
        cancel(); viewport = next; load()
    }
}
