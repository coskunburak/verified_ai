import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class PrimarySolveViewModelTests: XCTestCase {
    private var service: SolveServiceDouble!
    private var store: SolveMemoryRecovery!
    private var network: SolveNetworkDouble!
    private let user = UUID(), session = UUID()
    override func setUp() async throws { service = SolveServiceDouble(); store = SolveMemoryRecovery(); network = SolveNetworkDouble() }
    private func model(fast: Bool = false) -> PrimarySolveViewModel {
        PrimarySolveViewModel(userId: user, sessionId: session, service: service, recovery: store, network: network,
            sleep: { nanos in if fast { await Task.yield() } else { try await Task.sleep(nanoseconds: nanos) } })
    }
    private func settle(_ predicate: () -> Bool) async {
        for _ in 0..<200 { if predicate() { return }; await Task.yield() }
        XCTFail("Expected bounded asynchronous transition")
    }
    func testQueuedRunningGeneratedStopsPollingAndRepeatedTapDoesNotDuplicate() async throws {
        let vm = model(fast: true); vm.resume(); await settle { vm.canStart }
        vm.start(); vm.start()
        await settle { vm.snapshot?.state == .generatedUnverified && !vm.busy }
        XCTAssertEqual(service.keys.count, 1); XCTAssertEqual(service.readStates, [.running, .generatedUnverified])
        XCTAssertNotNil(vm.visibleCandidate); XCTAssertFalse(vm.canRetry)
        let reads = service.readStates.count; await Task.yield(); XCTAssertEqual(service.readStates.count, reads)
        vm.suspend()
    }
    func testAmbiguousPostReusesPersistedKeyAcrossRelaunch() async throws {
        service.failStart = true
        let first = model(); first.resume(); await settle { first.canStart }
        first.start(); await settle { first.errorKey != nil && !first.busy }
        XCTAssertNotNil(store.marker); let key = try XCTUnwrap(store.marker?.key)
        first.suspend()
        let second = model(fast: true); second.resume(); await settle { second.canRecover }
        service.failStart = false; second.recoverRequest()
        await settle { second.snapshot?.state == .generatedUnverified && !second.busy }
        XCTAssertEqual(service.keys, [key, key]); XCTAssertNil(store.marker); second.suspend()
    }
    func testForegroundRestoresServerJobWithoutPostingAndDismissalNeverCancels() async throws {
        service.currentJob = try FixturePrimarySolveAPI.decode(state: .queued)
        let vm = model(); vm.resume(); await settle { vm.snapshot?.state == .queued && !vm.busy }
        vm.suspend(); XCTAssertNil(vm.visibleCandidate)
        service.currentJob = try FixturePrimarySolveAPI.decode(state: .generatedUnverified)
        vm.resume(); await settle { vm.snapshot?.state == .generatedUnverified && !vm.busy }
        XCTAssertEqual(service.keys.count, 0); XCTAssertEqual(service.cancels, 0); vm.suspend()
    }
    func testOfflineDoesNotSubmitAndUncertainOutcomeNeverRetries() async throws {
        network.isReachable = false
        let vm = model(); vm.resume(); vm.start(); await Task.yield()
        XCTAssertEqual(vm.errorKey, "solve.error.offline"); XCTAssertTrue(service.keys.isEmpty)
        network.isReachable = true; service.currentJob = try FixturePrimarySolveAPI.decode(state: .outcomeUnknown)
        vm.resume(); await settle { vm.snapshot?.state == .outcomeUnknown }
        vm.retry(); await Task.yield(); XCTAssertTrue(service.retryKeys.isEmpty); XCTAssertTrue(service.readStates.isEmpty)
        vm.suspend()
    }
    func testRetryOnlyEligibleAttemptUsesNewKeyAndCancellationIsExplicit() async throws {
        service.currentJob = try FixturePrimarySolveAPI.decode(state: .retryableFailure)
        let vm = model(fast: true); vm.resume(); await settle { vm.canRetry }
        vm.retry(); await settle { vm.snapshot?.state == .generatedUnverified && !vm.busy }
        XCTAssertEqual(service.retryKeys.count, 1); XCTAssertTrue(service.keys.isEmpty)
        service.currentJob = try FixturePrimarySolveAPI.decode(state: .retryableFailure, attempt: 3)
        vm.refresh(); await settle { vm.snapshot?.attempt == 3 }; XCTAssertFalse(vm.canRetry)
        vm.suspend()
        service.currentJob = try FixturePrimarySolveAPI.decode(state: .running)
        let cancellable = model(); cancellable.resume(); await settle { cancellable.canCancel }
        cancellable.cancel(); await settle { cancellable.snapshot?.state == .cancelled }
        XCTAssertEqual(service.cancels, 1); cancellable.suspend()
    }
    func testReviewHasUncertaintyButStaleAndErrorNeverExposeAnswer() async throws {
        service.currentJob = try FixturePrimarySolveAPI.decode(state: .reviewRequired)
        let vm = model(); vm.resume(); await settle { vm.visibleCandidate != nil }
        XCTAssertFalse(vm.visibleCandidate!.ambiguities.isEmpty)
        service.currentJob = try FixturePrimarySolveAPI.decode(state: .stale)
        vm.refresh(); await settle { vm.snapshot?.state == .stale }; XCTAssertNil(vm.visibleCandidate)
        vm.suspend()
    }
    func testPollingPausesAtBoundAndDismissCancelsFurtherReads() async throws {
        service.alwaysRunning = true
        service.currentJob = try FixturePrimarySolveAPI.decode(state: .queued)
        let vm = model(fast: true); vm.resume()
        await settle { vm.pollingPaused }
        XCTAssertEqual(service.readStates.count, 15)
        vm.suspend(); let count = service.readStates.count
        await Task.yield(); XCTAssertEqual(service.readStates.count, count); XCTAssertEqual(service.cancels, 0)
    }
    func testStorageFailurePreventsPOST() async {
        let vm = model(); vm.resume(); await settle { vm.canStart }; store.failSave = true
        vm.start(); await settle { vm.errorKey != nil }; XCTAssertTrue(service.keys.isEmpty); vm.suspend()
    }
}

@MainActor
private final class SolveServiceDouble: PrimarySolveServicing {
    var currentJob: SolveJobSnapshot?
    var keys: [String] = [], retryKeys: [String] = []
    var readStates: [SolveState] = []
    var failStart = false, alwaysRunning = false, cancels = 0
    func current(sessionId: UUID) async throws -> SolveJobSnapshot? { currentJob }
    func start(sessionId: UUID, key: String) async throws -> SolveJobSnapshot {
        keys.append(key)
        if failStart { throw NetworkError.transport("timeout") }
        currentJob = try FixturePrimarySolveAPI.decode(state: .queued); return currentJob!
    }
    func read(sessionId: UUID, jobId: UUID) async throws -> SolveJobSnapshot {
        let state: SolveState = alwaysRunning || readStates.isEmpty ? .running : .generatedUnverified
        readStates.append(state); currentJob = try FixturePrimarySolveAPI.decode(state: state, jobId: jobId); return currentJob!
    }
    func retry(sessionId: UUID, jobId: UUID, key: String) async throws -> SolveJobSnapshot {
        retryKeys.append(key); currentJob = try FixturePrimarySolveAPI.decode(state: .queued, attempt: 2); return currentJob!
    }
    func cancel(sessionId: UUID, jobId: UUID) async throws -> SolveJobSnapshot {
        cancels += 1; currentJob = try FixturePrimarySolveAPI.decode(state: .cancelled, jobId: jobId); return currentJob!
    }
}
@MainActor private final class SolveMemoryRecovery: SolveRecoveryStoring {
    var marker: SolveRecoveryMarker?; var failSave = false
    func load(userId: UUID, sessionId: UUID) throws -> SolveRecoveryMarker? { marker }
    func save(_ marker: SolveRecoveryMarker, userId: UUID, sessionId: UUID) throws {
        if failSave { throw SolveContractError.recoveryStorage }; self.marker = marker
    }
    func remove(userId: UUID, sessionId: UUID) throws { marker = nil }
    func clearAll() throws { marker = nil }
}
@MainActor private final class SolveNetworkDouble: NetworkMonitoring { var isReachable = true }
