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
    func testVerificationClearsOnOfflineSuspendAndNeverEnablesSolveRetry() async throws {
        service.currentJob = try FixturePrimarySolveAPI.decode(state: .generatedUnverified)
        service.decision = FixturePrimarySolveAPI.decisionFixture("VERIFIED")
        let vm = model(); vm.resume(); await settle { vm.verification != nil && !vm.busy }
        XCTAssertEqual(vm.verification?.status, .verified); XCTAssertFalse(vm.canRetry)
        XCTAssertTrue(service.keys.isEmpty); XCTAssertTrue(service.retryKeys.isEmpty)
        network.isReachable = false; vm.refresh()
        XCTAssertNil(vm.verification)
        network.isReachable = true; vm.resume(); await settle { vm.verification != nil && !vm.busy }
        vm.suspend(); XCTAssertNil(vm.verification)
    }
    func testLocalSolveFlowReachesVerificationWhenHostNetworkIsOffline() async throws {
        let suite = "PrimarySolveNetwork-" + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let fixture = FixturePrimarySolveAPI(scenario: "VERIFIED", defaults: defaults, reset: true)
        network.isReachable = false
        let selected = AppDependencies.networkForSolveFlow(service: fixture, history: FixtureSolveHistoryAPI(),
            isFixture: true, live: network)
        let vm = PrimarySolveViewModel(userId: user, sessionId: FixturePrimarySolveAPI.sessionId,
            service: fixture, recovery: store, network: selected, isFixture: true, sleep: { _ in await Task.yield() })
        defer { vm.suspend() }
        vm.resume(); await settle { vm.canStart }; vm.start()
        await settle { vm.verification != nil && !vm.busy }
        XCTAssertEqual(vm.snapshot?.state, .generatedUnverified)
        XCTAssertEqual(vm.verification?.status, .verified)
        XCTAssertNil(vm.errorKey); XCTAssertEqual(fixture.startRequests, 1)
        XCTAssertFalse(network.isReachable)
    }
    func testFixtureFlagAloneCannotBypassLiveOfflineGuard() async throws {
        network.isReachable = false
        let selected = AppDependencies.networkForSolveFlow(service: service, history: FixtureSolveHistoryAPI(),
            isFixture: true, live: network)
        let vm = PrimarySolveViewModel(userId: user, sessionId: session, service: service,
            recovery: store, network: selected, isFixture: true)
        vm.resume(); vm.start(); await Task.yield()
        XCTAssertEqual(vm.errorKey, "solve.error.offline"); XCTAssertTrue(service.keys.isEmpty)
        vm.suspend()
        let fixture = FixturePrimarySolveAPI()
        let ungated = AppDependencies.networkForSolveFlow(service: fixture, history: FixtureSolveHistoryAPI(),
            isFixture: false, live: network)
        XCTAssertFalse(ungated.isReachable)
    }
    func testLateVerificationResponseCannotReappearAfterSuspend() async throws {
        service.currentJob = try FixturePrimarySolveAPI.decode(state: .generatedUnverified)
        service.delayDecision = true; service.decision = FixturePrimarySolveAPI.decisionFixture("VERIFIED")
        let vm = model(); vm.resume(); await settle { service.decisionContinuation != nil }
        vm.suspend(); service.decisionContinuation?.resume(); service.decisionContinuation = nil
        for _ in 0..<20 { await Task.yield() }
        XCTAssertNil(vm.verification); XCTAssertNil(vm.visibleCandidate)
    }
    func testVerificationFailurePreservesGeneratedAnswerWithoutCachedVerdict() async throws {
        service.currentJob = try FixturePrimarySolveAPI.decode(state: .generatedUnverified)
        service.failDecision = true
        let vm = model(); vm.resume(); await settle { vm.verificationUnavailable && !vm.busy }
        XCTAssertNil(vm.verification); XCTAssertNotNil(vm.visibleCandidate); XCTAssertFalse(vm.canRetry)
        vm.suspend()
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
    func testControlledFixturePreservesEveryPhaseUntilAcknowledgedAndPollsTheSameJob() async throws {
        let suite = "PrimarySolveControlled-" + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let fixture = FixturePrimarySolveAPI(defaults: defaults, reset: true, controlledProgression: true)
        let vm = PrimarySolveViewModel(userId: user, sessionId: FixturePrimarySolveAPI.sessionId,
            service: fixture, recovery: store, network: network, isFixture: true, sleep: { _ in await Task.yield() })
        defer { vm.suspend() }
        vm.resume(); await settle { vm.canStart }
        vm.start(); await settle { fixture.waitingForAdvance == .queued }
        let job = try XCTUnwrap(vm.snapshot?.jobId)
        for _ in 0..<20 { await Task.yield() }
        XCTAssertEqual(vm.snapshot?.state, .queued)
        XCTAssertNil(vm.visibleCandidate)
        fixture.advance(from: .running) // An out-of-order acknowledgement cannot skip QUEUED.
        XCTAssertEqual(vm.snapshot?.state, .queued)
        vm.advanceFixture(); await settle { fixture.waitingForAdvance == .running }
        XCTAssertEqual(vm.snapshot?.state, .running); XCTAssertEqual(vm.snapshot?.jobId, job)
        for _ in 0..<20 { await Task.yield() }
        XCTAssertEqual(vm.snapshot?.state, .running)
        XCTAssertNil(vm.visibleCandidate)
        vm.advanceFixture(); await settle { vm.snapshot?.state == .generatedUnverified && !vm.busy }
        XCTAssertEqual(vm.snapshot?.jobId, job); XCTAssertEqual(fixture.startRequests, 1)
        XCTAssertNotNil(vm.visibleCandidate); XCTAssertNil(vm.controlledFixtureState)
    }
    func testControlledFixtureCancellationAndRelaunchResumeRunningWithoutAnotherStart() async throws {
        let suite = "PrimarySolveRelaunch-" + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let fixture = FixturePrimarySolveAPI(defaults: defaults, reset: true, controlledProgression: true)
        let first = PrimarySolveViewModel(userId: user, sessionId: FixturePrimarySolveAPI.sessionId,
            service: fixture, recovery: store, network: network, isFixture: true, sleep: { _ in await Task.yield() })
        defer { first.suspend() }
        first.resume(); await settle { first.canStart }; first.start()
        await settle { fixture.waitingForAdvance == .queued }
        first.advanceFixture(); await settle { fixture.waitingForAdvance == .running }
        let job = try XCTUnwrap(first.snapshot?.jobId)
        first.suspend(); await settle { fixture.waitingForAdvance == nil }
        // A fresh fixture instance models process relaunch; only its isolated persisted state survives.
        let restoredFixture = FixturePrimarySolveAPI(defaults: defaults, controlledProgression: true)
        let restored = PrimarySolveViewModel(userId: user, sessionId: FixturePrimarySolveAPI.sessionId,
            service: restoredFixture, recovery: store, network: network, isFixture: true, sleep: { _ in await Task.yield() })
        defer { restored.suspend() }
        restored.resume(); await settle { restoredFixture.waitingForAdvance == .running }
        XCTAssertEqual(restored.snapshot?.jobId, job); XCTAssertFalse(restored.canStart)
        restored.advanceFixture(); await settle { restored.snapshot?.state == .generatedUnverified && !restored.busy }
        XCTAssertEqual(restored.snapshot?.jobId, job); XCTAssertEqual(restoredFixture.startRequests, 0)
        XCTAssertNotNil(restored.visibleCandidate)
    }
    func testAutomaticFixtureResumesFromPersistedStateAndNeverRewindsTerminalResults() async throws {
        let suite = "PrimarySolveAutomatic-" + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let session = FixturePrimarySolveAPI.sessionId
        let fixture = FixturePrimarySolveAPI(defaults: defaults, reset: true)
        XCTAssertFalse(fixture.controlledProgression)
        let job = try await fixture.start(sessionId: session, key: "synthetic-start")
        let running = try await fixture.read(sessionId: session, jobId: job.jobId)
        XCTAssertEqual(running.state, .running)
        let restored = FixturePrimarySolveAPI(defaults: defaults)
        let current = try await restored.current(sessionId: session)
        XCTAssertEqual(current?.state, .running); XCTAssertEqual(current?.jobId, job.jobId)
        let completed = try await restored.read(sessionId: session, jobId: job.jobId)
        XCTAssertEqual(completed.state, .generatedUnverified)
        let repeated = try await restored.read(sessionId: session, jobId: job.jobId)
        XCTAssertEqual(repeated.state, .generatedUnverified); XCTAssertEqual(repeated.jobId, job.jobId)
        XCTAssertEqual(restored.startRequests, 0)
    }
}

@MainActor
private final class SolveServiceDouble: PrimarySolveServicing {
    var decision: VerificationDecisionSnapshot?
    var delayDecision = false, failDecision = false
    var decisionContinuation: CheckedContinuation<Void, Never>?
    func verification(sessionId: UUID, jobId: UUID) async throws -> VerificationDecisionSnapshot? {
        if delayDecision { await withCheckedContinuation { decisionContinuation = $0 } }
        if failDecision { throw NetworkError.transport("offline") }
        return decision
    }
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
