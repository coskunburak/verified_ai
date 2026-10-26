import Foundation

protocol PrimarySolveServicing: Sendable {
    func current(sessionId: UUID) async throws -> SolveJobSnapshot?
    func start(sessionId: UUID, key: String) async throws -> SolveJobSnapshot
    func read(sessionId: UUID, jobId: UUID) async throws -> SolveJobSnapshot
    func retry(sessionId: UUID, jobId: UUID, key: String) async throws -> SolveJobSnapshot
    func cancel(sessionId: UUID, jobId: UUID) async throws -> SolveJobSnapshot
}
