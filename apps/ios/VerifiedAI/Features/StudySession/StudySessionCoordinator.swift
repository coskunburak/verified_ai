import Combine
import Foundation
import SwiftUI

protocol StudySessionTransporting: Sendable {
    func startSession(_ request: StudySessionStartRequest) async throws -> StudySessionStartResponse
    func fetchActiveSession() async throws -> StudySessionStateModel?
    func putCheckpoint(sessionId: UUID, idempotencyKey: String, request: StudySessionCheckpointRequest) async throws -> StudySessionCheckpointResponse
    func pauseSession(sessionId: UUID, request: StudySessionPauseRequest) async throws -> StudySessionStateModel
    func resumeSession(sessionId: UUID, request: StudySessionResumeRequest) async throws -> StudySessionStateModel
    func completeSession(sessionId: UUID, idempotencyKey: String, request: StudySessionCompleteRequest) async throws -> StudySessionCompleteResponse
}

final class APIClientStudySessionTransport: StudySessionTransporting, @unchecked Sendable {
    private let apiClient: APIClient
    private let encoder = StudySessionJSONCoding.makeEncoder()

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func startSession(_ request: StudySessionStartRequest) async throws -> StudySessionStartResponse {
        let body = try encoder.encode(request)
        let httpReq = HTTPRequest<StudySessionStartResponse>(
            endpoint: Endpoint(path: "/v1/study-sessions/start", method: .post),
            body: body
        )
        return try await apiClient.send(httpReq).body
    }

    func fetchActiveSession() async throws -> StudySessionStateModel? {
        let httpReq = HTTPRequest<StudySessionStateModel>(
            endpoint: Endpoint(path: "/v1/study-sessions/active", method: .get)
        )
        do {
            return try await apiClient.send(httpReq).body
        } catch NetworkError.server(let problem) where problem.status == 404 {
            return nil
        } catch NetworkError.httpStatus(404) {
            return nil
        }
    }

    func putCheckpoint(sessionId: UUID, idempotencyKey: String, request: StudySessionCheckpointRequest) async throws -> StudySessionCheckpointResponse {
        let body = try encoder.encode(request)
        let httpReq = HTTPRequest<StudySessionCheckpointResponse>(
            endpoint: Endpoint(path: "/v1/study-sessions/\(sessionId.uuidString.lowercased())/checkpoint", method: .put),
            body: body,
            idempotencyKey: idempotencyKey
        )
        return try await apiClient.send(httpReq).body
    }

    func pauseSession(sessionId: UUID, request: StudySessionPauseRequest) async throws -> StudySessionStateModel {
        let body = try encoder.encode(request)
        let httpReq = HTTPRequest<StudySessionStateModel>(
            endpoint: Endpoint(path: "/v1/study-sessions/\(sessionId.uuidString.lowercased())/pause", method: .post),
            body: body
        )
        return try await apiClient.send(httpReq).body
    }

    func resumeSession(sessionId: UUID, request: StudySessionResumeRequest) async throws -> StudySessionStateModel {
        let body = try encoder.encode(request)
        let httpReq = HTTPRequest<StudySessionStateModel>(
            endpoint: Endpoint(path: "/v1/study-sessions/\(sessionId.uuidString.lowercased())/resume", method: .post),
            body: body
        )
        return try await apiClient.send(httpReq).body
    }

    func completeSession(sessionId: UUID, idempotencyKey: String, request: StudySessionCompleteRequest) async throws -> StudySessionCompleteResponse {
        let body = try encoder.encode(request)
        let httpReq = HTTPRequest<StudySessionCompleteResponse>(
            endpoint: Endpoint(path: "/v1/study-sessions/\(sessionId.uuidString.lowercased())/complete", method: .post),
            body: body,
            idempotencyKey: idempotencyKey
        )
        return try await apiClient.send(httpReq).body
    }
}

enum StudySessionSyncState: Equatable, Sendable {
    case synced
    case savedLocally
    case syncing
    case attention(String)
}

@MainActor
final class StudySessionCoordinator: ObservableObject {

    @Published private(set) var activeSession: StudySessionStateModel?
    @Published private(set) var resumableSession: StudySessionStateModel?
    @Published private(set) var pendingOfflineCount: Int = 0
    @Published private(set) var syncState: StudySessionSyncState = .synced
    @Published private(set) var completionSummaryKey: String?
    @Published private(set) var lastDurationClamped: Bool = false

    private let store: StudySessionOfflineCheckpointStore
    private let transport: StudySessionTransporting
    private let clientInstanceId: String
    private var isReachable: Bool

    init(
        store: StudySessionOfflineCheckpointStore,
        transport: StudySessionTransporting,
        clientInstanceId: String = "ios-device-default",
        isReachable: Bool = true
    ) {
        self.store = store
        self.transport = transport
        self.clientInstanceId = clientInstanceId
        self.isReachable = isReachable
    }

    func updateReachability(userId: UUID, reachable: Bool) async {
        let becameOnline = !isReachable && reachable
        isReachable = reachable
        if becameOnline {
            await flushPendingCheckpoints(userId: userId)
        }
    }

    func bootstrap(userId: UUID) async {
        completionSummaryKey = nil
        if let local = try? await store.loadLatestResumableSession(userId: userId) {
            if local.state == .paused {
                resumableSession = local
            } else {
                activeSession = local
                resumableSession = local
            }
        }
        pendingOfflineCount = (try? await store.allPendingCheckpoints(userId: userId).count) ?? 0
        syncState = pendingOfflineCount > 0 ? .savedLocally : .synced

        guard isReachable else { return }

        await flushPendingCheckpoints(userId: userId)
        if let remote = try? await transport.fetchActiveSession(), remote.userId == userId {
            try? await store.saveSessionSnapshot(remote)
            if remote.state == .paused {
                resumableSession = remote
                activeSession = nil
            } else if remote.state == .active {
                activeSession = remote
                resumableSession = remote
            }
        }
    }

    func startSession(
        userId: UUID,
        sourceContext: StudySessionSourceContext,
        topicCode: String,
        locale: String,
        blueprint: [StudySessionStepBlueprintItem],
        coachSessionId: UUID? = nil
    ) async throws {
        completionSummaryKey = nil
        let request = StudySessionStartRequest(
            coachSessionId: coachSessionId,
            sourceContext: sourceContext,
            topicCode: topicCode,
            locale: locale,
            totalSteps: blueprint.count,
            clientInstanceId: clientInstanceId,
            stepBlueprint: blueprint,
            resumeSnapshot: StudySessionResumeSnapshot.empty(stepIndex: 0)
        )
        let response = try await transport.startSession(request)
        try await store.saveSessionSnapshot(response.session)
        activeSession = response.session
        resumableSession = nil
        syncState = .synced
    }

    func recordCheckpoint(
        userId: UUID,
        stepIndex: Int,
        stepType: StudySessionStepType,
        stepStatus: StudySessionStepStatus,
        draftNotes: String,
        activeDurationSeconds: Int,
        interruptionReason: StudySessionInterruptionReason? = nil
    ) async throws {
        guard let current = activeSession ?? resumableSession, current.userId == userId else {
            return
        }

        let seq = try await store.nextSequenceNumber(sessionId: current.sessionId, userId: userId)
        let key = StudySessionIdempotency.checkpointKey(
            sessionId: current.sessionId,
            stepIndex: stepIndex,
            sequenceNumber: seq
        )
        let currentPending = (try? await store.pendingCheckpoints(sessionId: current.sessionId, userId: userId).count) ?? 0

        let snapshot = StudySessionResumeSnapshot(
            lastStepIndex: stepIndex,
            draftStepNotes: draftNotes,
            offlineQueuedCount: min(100, currentPending + 1),
            activeSubStep: current.resumeSnapshot.activeSubStep,
            uiScrollAnchor: "step-\(stepIndex)",
            mistakeCode: current.resumeSnapshot.mistakeCode
        )

        let request = StudySessionCheckpointRequest(
            sequenceNumber: seq,
            stepIndex: stepIndex,
            stepType: stepType,
            stepStatus: stepStatus,
            reportedActiveDurationSeconds: max(0, min(14400, activeDurationSeconds)),
            offlineQueued: !isReachable,
            clientRecordedAt: Date(),
            idempotencyKey: key,
            interruptionReason: interruptionReason,
            clientInstanceId: clientInstanceId,
            resumeSnapshot: snapshot
        )

        // INV-SESS-002: Persist to local SQLite store BEFORE any network call.
        try await store.enqueueCheckpoint(userId: userId, sessionId: current.sessionId, request: request)
        let updatedLocal = applyLocalProgress(
            session: current,
            stepIndex: stepIndex,
            stepStatus: stepStatus,
            sequenceNumber: seq,
            activeDurationSeconds: activeDurationSeconds,
            snapshot: snapshot,
            interruptionReason: interruptionReason
        )
        try await store.saveSessionSnapshot(updatedLocal)
        activeSession = updatedLocal
        pendingOfflineCount = (try? await store.allPendingCheckpoints(userId: userId).count) ?? 0
        syncState = .savedLocally

        if isReachable {
            await flushPendingCheckpoints(userId: userId)
        }
    }

    func handleScenePhaseChange(
        _ phase: ScenePhase,
        userId: UUID,
        activeDurationSeconds: Int,
        draftNotes: String
    ) async {
        guard phase == .background || phase == .inactive else { return }
        guard let current = activeSession, current.userId == userId, current.state == .active else {
            return
        }
        let stepType = current.stepBlueprint.first(where: { $0.stepIndex == current.currentStepIndex })?.stepType ?? .solveItem
        try? await recordCheckpoint(
            userId: userId,
            stepIndex: current.currentStepIndex,
            stepType: stepType,
            stepStatus: .inProgress,
            draftNotes: draftNotes,
            activeDurationSeconds: activeDurationSeconds,
            interruptionReason: .appBackgrounded
        )
        if isReachable {
            _ = try? await pauseSession(userId: userId, reason: .appBackgrounded, activeDurationSeconds: activeDurationSeconds)
        }
    }

    func flushPendingCheckpoints(userId: UUID) async {
        guard isReachable else {
            syncState = pendingOfflineCount > 0 ? .savedLocally : .synced
            return
        }
        guard let queued = try? await store.allPendingCheckpoints(userId: userId), !queued.isEmpty else {
            pendingOfflineCount = 0
            syncState = .synced
            return
        }

        syncState = .syncing
        for item in queued {
            do {
                let response = try await transport.putCheckpoint(
                    sessionId: item.sessionId,
                    idempotencyKey: item.idempotencyKey,
                    request: item.request
                )
                try await store.markCheckpointSynced(
                    sessionId: item.sessionId,
                    userId: userId,
                    idempotencyKey: item.idempotencyKey
                )
                try await store.saveSessionSnapshot(response.session)
                lastDurationClamped = response.durationClamped
                if activeSession?.sessionId == response.session.sessionId || activeSession == nil {
                    activeSession = response.session
                }
            } catch {
                pendingOfflineCount = (try? await store.allPendingCheckpoints(userId: userId).count) ?? queued.count
                syncState = .savedLocally
                return
            }
        }
        pendingOfflineCount = (try? await store.allPendingCheckpoints(userId: userId).count) ?? 0
        syncState = pendingOfflineCount > 0 ? .savedLocally : .synced
    }

    @discardableResult
    func pauseSession(
        userId: UUID,
        reason: StudySessionInterruptionReason = .userPaused,
        activeDurationSeconds: Int? = nil
    ) async throws -> StudySessionStateModel? {
        guard let current = activeSession, current.userId == userId else { return nil }
        let req = StudySessionPauseRequest(
            interruptionReason: reason,
            reportedActiveDurationSeconds: activeDurationSeconds,
            resumeSnapshot: current.resumeSnapshot
        )
        let paused = try await transport.pauseSession(sessionId: current.sessionId, request: req)
        try await store.saveSessionSnapshot(paused)
        activeSession = nil
        resumableSession = paused
        return paused
    }

    @discardableResult
    func resumePendingSession(userId: UUID) async throws -> StudySessionStateModel? {
        guard let candidate = resumableSession ?? activeSession, candidate.userId == userId else {
            return nil
        }
        if candidate.state == .active {
            activeSession = candidate
            resumableSession = nil
            await flushPendingCheckpoints(userId: userId)
            return candidate
        }
        let req = StudySessionResumeRequest(
            clientInstanceId: clientInstanceId,
            resumeSnapshot: candidate.resumeSnapshot
        )
        let resumed = try await transport.resumeSession(sessionId: candidate.sessionId, request: req)
        try await store.saveSessionSnapshot(resumed)
        activeSession = resumed
        resumableSession = nil
        await flushPendingCheckpoints(userId: userId)
        return resumed
    }

    func completeActiveSession(
        userId: UUID,
        finalStepIndex: Int,
        activeDurationSeconds: Int,
        completionNotes: String? = nil
    ) async throws {
        guard let current = activeSession ?? resumableSession, current.userId == userId else { return }
        await flushPendingCheckpoints(userId: userId)

        let seq = try await store.nextSequenceNumber(sessionId: current.sessionId, userId: userId)
        let key = StudySessionIdempotency.completeKey(sessionId: current.sessionId, sequenceNumber: seq)
        let req = StudySessionCompleteRequest(
            finalStepIndex: finalStepIndex,
            reportedActiveDurationSeconds: max(0, min(14400, activeDurationSeconds)),
            idempotencyKey: key,
            completionNotes: completionNotes,
            resumeSnapshot: current.resumeSnapshot
        )
        let resp = try await transport.completeSession(sessionId: current.sessionId, idempotencyKey: key, request: req)
        try await store.saveSessionSnapshot(resp.session)
        activeSession = resp.session
        resumableSession = nil
        completionSummaryKey = resp.supportiveSummaryKey
        lastDurationClamped = resp.durationClamped
        syncState = .synced
    }

    func purgeLocalData(userId: UUID) async {
        _ = try? await store.purgeUserData(userId: userId)
        if activeSession?.userId == userId { activeSession = nil }
        if resumableSession?.userId == userId { resumableSession = nil }
        pendingOfflineCount = 0
        completionSummaryKey = nil
        syncState = .synced
    }

    private func applyLocalProgress(
        session: StudySessionStateModel,
        stepIndex: Int,
        stepStatus: StudySessionStepStatus,
        sequenceNumber: Int,
        activeDurationSeconds: Int,
        snapshot: StudySessionResumeSnapshot,
        interruptionReason: StudySessionInterruptionReason?
    ) -> StudySessionStateModel {
        let blueprintStatus: StudySessionBlueprintStatus = switch stepStatus {
        case .inProgress: .inProgress
        case .completed: .completed
        case .skipped: .skipped
        }
        let updatedBlueprint = session.stepBlueprint.map { item -> StudySessionStepBlueprintItem in
            guard item.stepIndex == stepIndex, item.status != .completed else { return item }
            var copy = item
            copy.status = blueprintStatus
            return copy
        }
        let completedCount = updatedBlueprint.filter { $0.status == .completed }.count
        return StudySessionStateModel(
            schemaVersion: session.schemaVersion,
            sessionId: session.sessionId,
            userId: session.userId,
            coachSessionId: session.coachSessionId,
            sourceContext: session.sourceContext,
            topicCode: session.topicCode,
            locale: session.locale,
            state: session.state,
            totalSteps: session.totalSteps,
            currentStepIndex: stepIndex,
            completedStepsCount: completedCount,
            lastSequenceNumber: sequenceNumber,
            activeDurationSeconds: max(session.activeDurationSeconds, min(14400, activeDurationSeconds)),
            pausedDurationSeconds: session.pausedDurationSeconds,
            clientInstanceId: clientInstanceId,
            interruptionReason: interruptionReason ?? session.interruptionReason,
            stepBlueprint: updatedBlueprint,
            resumeSnapshot: snapshot,
            startedAt: session.startedAt,
            lastCheckpointAt: Date(),
            pausedAt: session.pausedAt,
            resumedAt: session.resumedAt,
            completedAt: session.completedAt,
            expiresAt: session.expiresAt,
            trainingEligible: false
        )
    }
}
