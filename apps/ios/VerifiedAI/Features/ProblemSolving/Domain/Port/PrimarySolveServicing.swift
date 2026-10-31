import Foundation

protocol PrimarySolveServicing: Sendable {
    func verification(sessionId: UUID, jobId: UUID) async throws -> VerificationDecisionSnapshot?
    func current(sessionId: UUID) async throws -> SolveJobSnapshot?
    func start(sessionId: UUID, key: String) async throws -> SolveJobSnapshot
    func read(sessionId: UUID, jobId: UUID) async throws -> SolveJobSnapshot
    func retry(sessionId: UUID, jobId: UUID, key: String) async throws -> SolveJobSnapshot
    func cancel(sessionId: UUID, jobId: UUID) async throws -> SolveJobSnapshot
}


extension PrimarySolveServicing {
    func verification(sessionId: UUID, jobId: UUID) async throws -> VerificationDecisionSnapshot? { nil }
}
