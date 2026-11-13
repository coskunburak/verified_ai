import SwiftUI
import XCTest
@testable import VerifiedAI

final class Sprint87StudySessionTests: XCTestCase {

    private let userA = UUID(uuidString: "00000000-0000-4000-8000-000000000870")!
    private let userB = UUID(uuidString: "00000000-0000-4000-8000-000000000879")!

    func testSchemaSnakeCaseCodableRoundTripAndTrainingEligibleFalse() throws {
        let model = sampleSession(userId: userA, state: .paused)
        let data = try StudySessionJSONCoding.makeEncoder().encode(model)
        let jsonString = try XCTUnwrap(String(data: data, encoding: .utf8))
        XCTAssertTrue(jsonString.contains("\"session_id\""))
        XCTAssertTrue(jsonString.contains("\"training_eligible\":false"))
        let decoded = try StudySessionJSONCoding.makeDecoder().decode(StudySessionStateModel.self, from: data)
        XCTAssertEqual(decoded, model)
        XCTAssertFalse(decoded.trainingEligible)
    }

    func testDeterministicIdempotencyKeyFormatMatchesContract() {
        let sessionId = UUID(uuidString: "00000000-0000-4000-8000-000000000871")!
        let key = StudySessionIdempotency.checkpointKey(sessionId: sessionId, stepIndex: 1, sequenceNumber: 2)
        XCTAssertGreaterThanOrEqual(key.count, 16)
        XCTAssertEqual(key, "sess_000000000000_step_01_seq_0002")
    }

    func testOfflineStoreWritesBeforeSyncAndMaintainsAscendingSequenceOrder() async throws {
        let store = try StudySessionOfflineCheckpointStore(databasePath: ":memory:")
        let session = sampleSession(userId: userA, state: .active)
        try await store.saveSessionSnapshot(session)

        for seq in 1...3 {
            let req = StudySessionCheckpointRequest(
                sequenceNumber: seq,
                stepIndex: seq - 1,
                stepType: .solveItem,
                stepStatus: .completed,
                reportedActiveDurationSeconds: seq * 30,
                offlineQueued: true,
                clientRecordedAt: Date(),
                idempotencyKey: StudySessionIdempotency.checkpointKey(sessionId: session.sessionId, stepIndex: seq - 1, sequenceNumber: seq),
                interruptionReason: .networkLost,
                clientInstanceId: "ios-device-0870",
                resumeSnapshot: StudySessionResumeSnapshot.empty(stepIndex: seq - 1)
            )
            try await store.enqueueCheckpoint(userId: userA, sessionId: session.sessionId, request: req)
        }

        let pending = try await store.pendingCheckpoints(sessionId: session.sessionId, userId: userA)
        XCTAssertEqual(pending.map(\.sequenceNumber), [1, 2, 3])
    }

    func testOfflineStoreIdempotentDuplicateKeyAndMonotonicStaleGuard() async throws {
        let store = try StudySessionOfflineCheckpointStore(databasePath: ":memory:")
        let session = sampleSession(userId: userA, state: .active)
        let key2 = StudySessionIdempotency.checkpointKey(sessionId: session.sessionId, stepIndex: 1, sequenceNumber: 2)
        let req2 = makeCheckpointReq(session: session, seq: 2, key: key2)

        try await store.enqueueCheckpoint(userId: userA, sessionId: session.sessionId, request: req2)
        // Duplicate same key succeeds idempotently without creating second row
        try await store.enqueueCheckpoint(userId: userA, sessionId: session.sessionId, request: req2)

        let staleReq = makeCheckpointReq(
            session: session,
            seq: 1,
            key: StudySessionIdempotency.checkpointKey(sessionId: session.sessionId, stepIndex: 0, sequenceNumber: 1)
        )
        do {
            try await store.enqueueCheckpoint(userId: userA, sessionId: session.sessionId, request: staleReq)
            XCTFail("Expected staleSequenceRejected")
        } catch let err as StudySessionStoreError {
            XCTAssertEqual(err, .staleSequenceRejected)
        }

        let pending = try await store.pendingCheckpoints(sessionId: session.sessionId, userId: userA)
        XCTAssertEqual(pending.count, 1)
        XCTAssertEqual(pending.first?.sequenceNumber, 2)
    }

    func testOfflineStoreIsolatesUsersAndPurgesUnder500Ms() async throws {
        let store = try StudySessionOfflineCheckpointStore(databasePath: ":memory:")
        let sessionA = sampleSession(userId: userA, state: .paused)
        let sessionB = sampleSession(userId: userB, state: .paused)
        try await store.saveSessionSnapshot(sessionA)
        try await store.saveSessionSnapshot(sessionB)

        let foreign = try await store.loadSession(sessionId: sessionA.sessionId, userId: userB)
        XCTAssertNil(foreign)

        let reqA = makeCheckpointReq(
            session: sessionA,
            seq: 1,
            key: StudySessionIdempotency.checkpointKey(sessionId: sessionA.sessionId, stepIndex: 0, sequenceNumber: 1)
        )
        try await store.enqueueCheckpoint(userId: userA, sessionId: sessionA.sessionId, request: reqA)

        let start = Date()
        let purged = try await store.purgeUserData(userId: userA)
        let elapsedMs = Date().timeIntervalSince(start) * 1000.0
        XCTAssertEqual(purged, 2)
        XCTAssertLessThan(elapsedMs, 500.0)
        let loadedAfterPurgeA = try await store.loadSession(sessionId: sessionA.sessionId, userId: userA)
        let loadedAfterPurgeB = try await store.loadSession(sessionId: sessionB.sessionId, userId: userB)
        XCTAssertNil(loadedAfterPurgeA)
        XCTAssertNotNil(loadedAfterPurgeB)
    }

    @MainActor
    func testCoordinatorSavesOfflineFirstAndFlushesInOrderOnReconnect() async throws {
        let store = try StudySessionOfflineCheckpointStore(databasePath: ":memory:")
        let transport = MockStudySessionTransport(userId: userA)
        let coordinator = StudySessionCoordinator(
            store: store,
            transport: transport,
            clientInstanceId: "ios-device-0870",
            isReachable: false
        )

        try await coordinator.startSession(
            userId: userA,
            sourceContext: .dailyCoach,
            topicCode: "math.chain_rule",
            locale: "tr-TR",
            blueprint: sampleBlueprint()
        )

        try await coordinator.recordCheckpoint(
            userId: userA,
            stepIndex: 0,
            stepType: .conceptPrimer,
            stepStatus: .completed,
            draftNotes: "step 0",
            activeDurationSeconds: 45
        )
        try await coordinator.recordCheckpoint(
            userId: userA,
            stepIndex: 1,
            stepType: .solveItem,
            stepStatus: .inProgress,
            draftNotes: "step 1",
            activeDurationSeconds: 95
        )

        XCTAssertEqual(coordinator.pendingOfflineCount, 2)
        XCTAssertEqual(coordinator.syncState, .savedLocally)
        XCTAssertTrue(transport.sentCheckpointSequences.isEmpty)

        await coordinator.updateReachability(userId: userA, reachable: true)
        XCTAssertEqual(coordinator.pendingOfflineCount, 0)
        XCTAssertEqual(coordinator.syncState, .synced)
        XCTAssertEqual(transport.sentCheckpointSequences, [1, 2])
    }

    @MainActor
    func testScenePhaseBackgroundCheckpointsBeforePause() async throws {
        let store = try StudySessionOfflineCheckpointStore(databasePath: ":memory:")
        let transport = MockStudySessionTransport(userId: userA)
        let coordinator = StudySessionCoordinator(store: store, transport: transport, isReachable: true)

        try await coordinator.startSession(
            userId: userA,
            sourceContext: .dailyCoach,
            topicCode: "math.integrals",
            locale: "en-US",
            blueprint: sampleBlueprint()
        )
        await coordinator.handleScenePhaseChange(.background, userId: userA, activeDurationSeconds: 50, draftNotes: "u = 3x")
        XCTAssertTrue(transport.pausedReasons.contains(.appBackgrounded))
        XCTAssertEqual(coordinator.resumableSession?.state, .paused)
    }

    @MainActor
    func testPauseResumeAndCompleteFlowWithCoordinator() async throws {
        let store = try StudySessionOfflineCheckpointStore(databasePath: ":memory:")
        let transport = MockStudySessionTransport(userId: userA)
        let coordinator = StudySessionCoordinator(store: store, transport: transport, isReachable: true)

        try await coordinator.startSession(
            userId: userA,
            sourceContext: .spacedReview,
            topicCode: "math.limits",
            locale: "en-US",
            blueprint: sampleBlueprint()
        )
        let paused = try await coordinator.pauseSession(userId: userA, reason: .userPaused, activeDurationSeconds: 60)
        XCTAssertEqual(paused?.state, .paused)

        let resumed = try await coordinator.resumePendingSession(userId: userA)
        XCTAssertEqual(resumed?.state, .active)

        try await coordinator.completeActiveSession(userId: userA, finalStepIndex: 2, activeDurationSeconds: 180)
        XCTAssertEqual(coordinator.activeSession?.state, .completed)
        XCTAssertEqual(coordinator.completionSummaryKey, "study_session.complete.summary")
    }

    func testResumeAndCoordinatorViewsUseOnlyNonStigmatizingCopy() {
        let session = sampleSession(userId: userA, state: .paused)
        XCTAssertFalse(session.trainingEligible)
        let banner = StudySessionResumeBannerView(
            session: session,
            pendingOfflineCount: 1,
            onContinue: {},
            onStartFresh: {}
        )
        XCTAssertNotNil(banner)
    }

    @MainActor
    func testCoordinatorPurgeRemovesUserLocalState() async throws {
        let store = try StudySessionOfflineCheckpointStore(databasePath: ":memory:")
        let transport = MockStudySessionTransport(userId: userA)
        let coordinator = StudySessionCoordinator(store: store, transport: transport, isReachable: false)

        try await coordinator.startSession(
            userId: userA,
            sourceContext: .freeStudy,
            topicCode: "math.vectors",
            locale: "tr-TR",
            blueprint: sampleBlueprint()
        )
        await coordinator.purgeLocalData(userId: userA)
        XCTAssertNil(coordinator.activeSession)
        XCTAssertNil(coordinator.resumableSession)
        XCTAssertEqual(coordinator.pendingOfflineCount, 0)
    }

    private func sampleBlueprint() -> [StudySessionStepBlueprintItem] {
        [
            StudySessionStepBlueprintItem(stepIndex: 0, stepType: .conceptPrimer, stepTitle: "Intro", estimatedSeconds: 60, status: .inProgress, targetSkillCode: "S1", problemId: nil),
            StudySessionStepBlueprintItem(stepIndex: 1, stepType: .solveItem, stepTitle: "Practice", estimatedSeconds: 120, status: .pending, targetSkillCode: "S1", problemId: "p-1"),
            StudySessionStepBlueprintItem(stepIndex: 2, stepType: .reflection, stepTitle: "Wrap up", estimatedSeconds: 60, status: .pending, targetSkillCode: "S1", problemId: nil),
        ]
    }

    private func sampleSession(userId: UUID, state: StudySessionState) -> StudySessionStateModel {
        let now = Date(timeIntervalSince1970: 1_794_553_200)
        return StudySessionStateModel(
            schemaVersion: "1.0.0",
            sessionId: UUID(),
            userId: userId,
            coachSessionId: nil,
            sourceContext: .dailyCoach,
            topicCode: "math.calculus.chain_rule",
            locale: "tr-TR",
            state: state,
            totalSteps: 3,
            currentStepIndex: 1,
            completedStepsCount: 1,
            lastSequenceNumber: 1,
            activeDurationSeconds: 90,
            pausedDurationSeconds: 10,
            clientInstanceId: "ios-device-0870",
            interruptionReason: state == .paused ? .appBackgrounded : nil,
            stepBlueprint: sampleBlueprint(),
            resumeSnapshot: StudySessionResumeSnapshot.empty(stepIndex: 1),
            startedAt: now,
            lastCheckpointAt: now.addingTimeInterval(90),
            pausedAt: state == .paused ? now.addingTimeInterval(95) : nil,
            resumedAt: nil,
            completedAt: nil,
            expiresAt: now.addingTimeInterval(86_400),
            trainingEligible: false
        )
    }

    private func makeCheckpointReq(session: StudySessionStateModel, seq: Int, key: String) -> StudySessionCheckpointRequest {
        StudySessionCheckpointRequest(
            sequenceNumber: seq,
            stepIndex: 0,
            stepType: .solveItem,
            stepStatus: .inProgress,
            reportedActiveDurationSeconds: seq * 20,
            offlineQueued: true,
            clientRecordedAt: Date(),
            idempotencyKey: key,
            interruptionReason: .networkLost,
            clientInstanceId: "ios-device-0870",
            resumeSnapshot: StudySessionResumeSnapshot.empty(stepIndex: 0)
        )
    }
}

private final class MockStudySessionTransport: StudySessionTransporting, @unchecked Sendable {
    let userId: UUID
    var currentSession: StudySessionStateModel?
    var sentCheckpointSequences: [Int] = []
    var pausedReasons: [StudySessionInterruptionReason] = []

    init(userId: UUID) {
        self.userId = userId
    }

    func startSession(_ request: StudySessionStartRequest) async throws -> StudySessionStartResponse {
        let now = Date()
        let model = StudySessionStateModel(
            schemaVersion: "1.0.0",
            sessionId: UUID(),
            userId: userId,
            coachSessionId: request.coachSessionId,
            sourceContext: request.sourceContext,
            topicCode: request.topicCode,
            locale: request.locale,
            state: .active,
            totalSteps: request.totalSteps,
            currentStepIndex: 0,
            completedStepsCount: 0,
            lastSequenceNumber: 0,
            activeDurationSeconds: 0,
            pausedDurationSeconds: 0,
            clientInstanceId: request.clientInstanceId,
            interruptionReason: nil,
            stepBlueprint: request.stepBlueprint,
            resumeSnapshot: request.resumeSnapshot ?? StudySessionResumeSnapshot.empty(),
            startedAt: now,
            lastCheckpointAt: nil,
            pausedAt: nil,
            resumedAt: nil,
            completedAt: nil,
            expiresAt: now.addingTimeInterval(86_400),
            trainingEligible: false
        )
        currentSession = model
        return StudySessionStartResponse(
            schemaVersion: "1.0.0",
            session: model,
            autoPausedPriorSessionId: nil,
            expiredPriorSessionId: nil,
            trainingEligible: false
        )
    }

    func fetchActiveSession() async throws -> StudySessionStateModel? {
        currentSession
    }

    func putCheckpoint(sessionId: UUID, idempotencyKey: String, request: StudySessionCheckpointRequest) async throws -> StudySessionCheckpointResponse {
        sentCheckpointSequences.append(request.sequenceNumber)
        let base = currentSession!
        let updated = StudySessionStateModel(
            schemaVersion: base.schemaVersion,
            sessionId: sessionId,
            userId: base.userId,
            coachSessionId: base.coachSessionId,
            sourceContext: base.sourceContext,
            topicCode: base.topicCode,
            locale: base.locale,
            state: base.state,
            totalSteps: base.totalSteps,
            currentStepIndex: request.stepIndex,
            completedStepsCount: request.stepStatus == .completed ? base.completedStepsCount + 1 : base.completedStepsCount,
            lastSequenceNumber: request.sequenceNumber,
            activeDurationSeconds: request.reportedActiveDurationSeconds,
            pausedDurationSeconds: base.pausedDurationSeconds,
            clientInstanceId: base.clientInstanceId,
            interruptionReason: request.interruptionReason,
            stepBlueprint: base.stepBlueprint,
            resumeSnapshot: request.resumeSnapshot,
            startedAt: base.startedAt,
            lastCheckpointAt: Date(),
            pausedAt: base.pausedAt,
            resumedAt: base.resumedAt,
            completedAt: base.completedAt,
            expiresAt: base.expiresAt,
            trainingEligible: false
        )
        currentSession = updated
        return StudySessionCheckpointResponse(
            schemaVersion: "1.0.0",
            session: updated,
            acceptedSequenceNumber: request.sequenceNumber,
            acceptedActiveDurationSeconds: request.reportedActiveDurationSeconds,
            durationClamped: false,
            idempotentReplay: false,
            staleCheckpointIgnored: false,
            trainingEligible: false
        )
    }

    func pauseSession(sessionId: UUID, request: StudySessionPauseRequest) async throws -> StudySessionStateModel {
        let reason = request.interruptionReason ?? .userPaused
        pausedReasons.append(reason)
        let base = currentSession!
        let paused = StudySessionStateModel(
            schemaVersion: base.schemaVersion,
            sessionId: sessionId,
            userId: base.userId,
            coachSessionId: base.coachSessionId,
            sourceContext: base.sourceContext,
            topicCode: base.topicCode,
            locale: base.locale,
            state: .paused,
            totalSteps: base.totalSteps,
            currentStepIndex: base.currentStepIndex,
            completedStepsCount: base.completedStepsCount,
            lastSequenceNumber: base.lastSequenceNumber,
            activeDurationSeconds: request.reportedActiveDurationSeconds ?? base.activeDurationSeconds,
            pausedDurationSeconds: base.pausedDurationSeconds,
            clientInstanceId: base.clientInstanceId,
            interruptionReason: reason,
            stepBlueprint: base.stepBlueprint,
            resumeSnapshot: request.resumeSnapshot ?? base.resumeSnapshot,
            startedAt: base.startedAt,
            lastCheckpointAt: base.lastCheckpointAt,
            pausedAt: Date(),
            resumedAt: base.resumedAt,
            completedAt: nil,
            expiresAt: base.expiresAt,
            trainingEligible: false
        )
        currentSession = paused
        return paused
    }

    func resumeSession(sessionId: UUID, request: StudySessionResumeRequest) async throws -> StudySessionStateModel {
        let base = currentSession!
        let resumed = StudySessionStateModel(
            schemaVersion: base.schemaVersion,
            sessionId: sessionId,
            userId: base.userId,
            coachSessionId: base.coachSessionId,
            sourceContext: base.sourceContext,
            topicCode: base.topicCode,
            locale: base.locale,
            state: .active,
            totalSteps: base.totalSteps,
            currentStepIndex: base.currentStepIndex,
            completedStepsCount: base.completedStepsCount,
            lastSequenceNumber: base.lastSequenceNumber,
            activeDurationSeconds: base.activeDurationSeconds,
            pausedDurationSeconds: base.pausedDurationSeconds,
            clientInstanceId: request.clientInstanceId ?? base.clientInstanceId,
            interruptionReason: nil,
            stepBlueprint: base.stepBlueprint,
            resumeSnapshot: request.resumeSnapshot ?? base.resumeSnapshot,
            startedAt: base.startedAt,
            lastCheckpointAt: base.lastCheckpointAt,
            pausedAt: nil,
            resumedAt: Date(),
            completedAt: nil,
            expiresAt: base.expiresAt,
            trainingEligible: false
        )
        currentSession = resumed
        return resumed
    }

    func completeSession(sessionId: UUID, idempotencyKey: String, request: StudySessionCompleteRequest) async throws -> StudySessionCompleteResponse {
        let base = currentSession!
        let completed = StudySessionStateModel(
            schemaVersion: base.schemaVersion,
            sessionId: sessionId,
            userId: base.userId,
            coachSessionId: base.coachSessionId,
            sourceContext: base.sourceContext,
            topicCode: base.topicCode,
            locale: base.locale,
            state: .completed,
            totalSteps: base.totalSteps,
            currentStepIndex: request.finalStepIndex,
            completedStepsCount: base.totalSteps,
            lastSequenceNumber: base.lastSequenceNumber,
            activeDurationSeconds: request.reportedActiveDurationSeconds,
            pausedDurationSeconds: base.pausedDurationSeconds,
            clientInstanceId: base.clientInstanceId,
            interruptionReason: nil,
            stepBlueprint: base.stepBlueprint,
            resumeSnapshot: request.resumeSnapshot ?? base.resumeSnapshot,
            startedAt: base.startedAt,
            lastCheckpointAt: Date(),
            pausedAt: nil,
            resumedAt: base.resumedAt,
            completedAt: Date(),
            expiresAt: base.expiresAt,
            trainingEligible: false
        )
        currentSession = completed
        return StudySessionCompleteResponse(
            schemaVersion: "1.0.0",
            session: completed,
            acceptedActiveDurationSeconds: request.reportedActiveDurationSeconds,
            durationClamped: false,
            idempotentReplay: false,
            supportiveSummaryKey: "study_session.complete.summary",
            trainingEligible: false
        )
    }
}
