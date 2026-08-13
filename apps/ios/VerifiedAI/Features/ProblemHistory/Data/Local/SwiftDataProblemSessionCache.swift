import Foundation
import SwiftData

@Model
final class CachedProblemSessionEntity {
    @Attribute(.unique) var problemSessionId: String
    var status: String
    var stage: String
    var inputMode: String
    var nextAction: String
    var retryable: Bool
    var reviewRequired: Bool
    var currentParseRevision: Int?
    var currentParseSource: String?
    var classificationStatus: String?
    var primarySkillId: String?
    var difficulty: String?
    var failureCode: String?
    var createdAt: Date
    var updatedAt: Date
    var completedAt: Date?
    var version: Int64

    init(item: ProblemSessionHistoryItem) {
        self.problemSessionId = item.problemSessionId.uuidString
        self.status = item.status.rawValue
        self.stage = item.stage.rawValue
        self.inputMode = item.inputMode
        self.nextAction = item.nextAction.rawValue
        self.retryable = item.retryable
        self.reviewRequired = item.reviewRequired
        self.currentParseRevision = item.currentParseRevision
        self.currentParseSource = item.currentParseSource
        self.classificationStatus = item.classificationStatus
        self.primarySkillId = item.primarySkillId
        self.difficulty = item.difficulty
        self.failureCode = nil
        self.createdAt = item.createdAt
        self.updatedAt = item.updatedAt
        self.completedAt = item.completedAt
        self.version = 0
    }

    func update(from item: ProblemSessionHistoryItem) {
        status = item.status.rawValue
        stage = item.stage.rawValue
        inputMode = item.inputMode
        nextAction = item.nextAction.rawValue
        retryable = item.retryable
        reviewRequired = item.reviewRequired
        currentParseRevision = item.currentParseRevision
        currentParseSource = item.currentParseSource
        classificationStatus = item.classificationStatus
        primarySkillId = item.primarySkillId
        difficulty = item.difficulty
        createdAt = item.createdAt
        updatedAt = item.updatedAt
        completedAt = item.completedAt
    }

    func update(from detail: ProblemSessionDetail) {
        status = detail.status.rawValue
        stage = detail.stage.rawValue
        inputMode = detail.inputMode
        nextAction = detail.nextAction.rawValue
        retryable = detail.retryable
        reviewRequired = detail.reviewRequired
        currentParseRevision = detail.currentParse?.revision
        currentParseSource = detail.currentParse?.source
        classificationStatus = detail.classification?.status
        primarySkillId = detail.classification?.primarySkillId
        difficulty = detail.classification?.difficulty
        failureCode = detail.failureCode
        createdAt = detail.createdAt
        updatedAt = detail.updatedAt
        completedAt = detail.completedAt
        version = detail.version
    }

    func item() throws -> ProblemSessionHistoryItem {
        guard let id = UUID(uuidString: problemSessionId),
              let status = ProblemSessionStatus(rawValue: status),
              let stage = ProblemSessionStage(rawValue: stage),
              let nextAction = ProblemSessionNextAction(rawValue: nextAction) else {
            throw NetworkError.decoding("unsupported_cached_problem_session")
        }
        return ProblemSessionHistoryItem(
            problemSessionId: id,
            status: status,
            stage: stage,
            inputMode: inputMode,
            nextAction: nextAction,
            retryable: retryable,
            reviewRequired: reviewRequired,
            currentParseRevision: currentParseRevision,
            currentParseSource: currentParseSource,
            classificationStatus: classificationStatus,
            primarySkillId: primarySkillId,
            difficulty: difficulty,
            createdAt: createdAt,
            updatedAt: updatedAt,
            completedAt: completedAt
        )
    }
}

@MainActor
final class SwiftDataProblemSessionCache: ProblemSessionCaching {
    private let container: ModelContainer
    private var context: ModelContext { container.mainContext }

    init(inMemory: Bool = false) throws {
        let configuration = ModelConfiguration(isStoredInMemoryOnly: inMemory)
        container = try ModelContainer(for: CachedProblemSessionEntity.self, configurations: configuration)
    }

    func loadSummaries(limit: Int) throws -> [ProblemSessionHistoryItem] {
        var descriptor = FetchDescriptor<CachedProblemSessionEntity>(
            sortBy: [
                SortDescriptor(\.updatedAt, order: .reverse),
                SortDescriptor(\.problemSessionId, order: .reverse)
            ]
        )
        descriptor.fetchLimit = max(1, limit)
        return try context.fetch(descriptor).map { try $0.item() }
    }

    func save(page: ProblemSessionHistoryPage) throws {
        for item in page.items {
            try upsert(item)
        }
        try context.save()
    }

    func save(detail: ProblemSessionDetail) throws {
        let id = detail.problemSessionId.uuidString
        let entity = try find(id: id) ?? CachedProblemSessionEntity(item: detail.historyItem)
        entity.update(from: detail)
        if entity.modelContext == nil {
            context.insert(entity)
        }
        try context.save()
    }

    func clear() throws {
        let descriptor = FetchDescriptor<CachedProblemSessionEntity>()
        for entity in try context.fetch(descriptor) {
            context.delete(entity)
        }
        try context.save()
    }

    private func upsert(_ item: ProblemSessionHistoryItem) throws {
        let id = item.problemSessionId.uuidString
        if let existing = try find(id: id) {
            existing.update(from: item)
        } else {
            context.insert(CachedProblemSessionEntity(item: item))
        }
    }

    private func find(id: String) throws -> CachedProblemSessionEntity? {
        let descriptor = FetchDescriptor<CachedProblemSessionEntity>(
            predicate: #Predicate { entity in
                entity.problemSessionId == id
            }
        )
        return try context.fetch(descriptor).first
    }
}
