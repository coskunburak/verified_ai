import Foundation

protocol ProblemSessionHistoryServicing: Sendable {
    func history(limit: Int, cursor: String?) async throws -> ProblemSessionHistoryPage
    func detail(problemSessionId: UUID) async throws -> ProblemSessionDetail
}

@MainActor
protocol ProblemSessionCaching: AnyObject {
    func loadSummaries(limit: Int) throws -> [ProblemSessionHistoryItem]
    func save(page: ProblemSessionHistoryPage) throws
    func save(detail: ProblemSessionDetail) throws
    func clear() throws
}
