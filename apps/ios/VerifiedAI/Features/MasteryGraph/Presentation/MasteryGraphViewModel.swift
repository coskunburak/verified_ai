import Foundation
import Observation

@MainActor
@Observable
public final class MasteryGraphViewModel {
    public let curriculumCode: String
    private let service: any SkillGraphServicing
    private let ownsAccount: @MainActor () async -> Bool

    public private(set) var projection: SkillGraphProjection?
    public private(set) var selectedNode: SkillGraphNode?
    public private(set) var isLoading: Bool = false
    public private(set) var errorMessageKey: String?

    @ObservationIgnored
    private var fetchTask: Task<Void, Never>?
    private var generation: Int = 0

    public init(
        curriculumCode: String,
        service: any SkillGraphServicing,
        ownsAccount: @escaping @MainActor () async -> Bool
    ) {
        self.curriculumCode = curriculumCode
        self.service = service
        self.ownsAccount = ownsAccount
    }

    public func loadGraph() {
        fetchTask?.cancel()
        generation += 1
        let currentGen = generation

        isLoading = true
        errorMessageKey = nil

        fetchTask = Task { [weak self] in
            guard let self = self else { return }

            guard await self.ownsAccount() else {
                self.isLoading = false
                self.projection = nil
                self.errorMessageKey = "mastery.graph.error.unauthorized"
                return
            }

            do {
                let result = try await self.service.fetchGraph(curriculum: self.curriculumCode)
                guard !Task.isCancelled, self.generation == currentGen else { return }
                self.projection = result
                self.isLoading = false
            } catch {
                guard !Task.isCancelled, self.generation == currentGen else { return }
                self.projection = nil
                self.isLoading = false
                self.errorMessageKey = "mastery.graph.error.fetch_failed"
            }
        }
    }

    public func selectNode(_ node: SkillGraphNode?) {
        self.selectedNode = node
    }

    public func cancel() {
        fetchTask?.cancel()
        fetchTask = nil
        isLoading = false
    }

    deinit {
        fetchTask?.cancel()
    }
}
