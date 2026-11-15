import XCTest
@testable import VerifiedAI

final class ExamSessionSQLiteStoreTests: XCTestCase {

    func testSessionOutboxScratchpadPersistenceAndMultiUserIsolation() async throws {
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let userA = UUID(uuidString: "91000000-0000-4000-8000-000000000a01")!
        let userB = UUID(uuidString: "91000000-0000-4000-8000-000000000b02")!
        let assemblyA = UUID(uuidString: "94100000-0000-4000-8000-000000000011")!
        let assemblyB = UUID(uuidString: "94100000-0000-4000-8000-000000000022")!

        let sessionA = makeSampleSession(sessionId: UUID(), userId: userA, assemblyId: assemblyA)
        let sessionB = makeSampleSession(sessionId: UUID(), userId: userB, assemblyId: assemblyB)

        await store.saveExamSession(sessionA)
        await store.saveExamSession(sessionB)

        let loadedA = await store.loadExamSession(userId: userA, sessionId: sessionA.sessionId)
        XCTAssertEqual(loadedA?.sessionId, sessionA.sessionId)
        let loadedActiveA = await store.loadActiveExamSession(userId: userA, assemblyId: assemblyA)
        XCTAssertEqual(loadedActiveA?.sessionId, sessionA.sessionId)

        // BOLA/multi-user isolation: userB cannot load userA's session
        let crossRead = await store.loadExamSession(userId: userB, sessionId: sessionA.sessionId)
        XCTAssertNil(crossRead)

        // Outbox CRUD & isolation
        let outboxEntryA = ExamAutosaveOutboxEntry(
            sessionId: sessionA.sessionId,
            userId: userA,
            itemPosition: 1,
            idempotencyKey: "outbox-a-1",
            request: ExamSessionItemAutosaveRequestDTO(
                action: .selectOption,
                selectedOptionKey: "B",
                expectedAutosaveVersion: 1,
                clientAutosaveSeq: 1,
                offlineReplay: true,
                clientTimestamp: "2026-11-15T10:01:00Z",
                trainingEligible: false
            ),
            enqueuedAt: "2026-11-15T10:01:00Z"
        )
        let outboxEntryB = ExamAutosaveOutboxEntry(
            sessionId: sessionB.sessionId,
            userId: userB,
            itemPosition: 1,
            idempotencyKey: "outbox-b-1",
            request: ExamSessionItemAutosaveRequestDTO(
                action: .selectOption,
                selectedOptionKey: "A",
                expectedAutosaveVersion: 1,
                clientAutosaveSeq: 1,
                offlineReplay: true,
                clientTimestamp: "2026-11-15T10:01:00Z",
                trainingEligible: false
            ),
            enqueuedAt: "2026-11-15T10:01:00Z"
        )
        await store.enqueueAutosaveOutbox(outboxEntryA)
        await store.enqueueAutosaveOutbox(outboxEntryB)

        let userAEntries = await store.loadAutosaveOutbox(userId: userA, sessionId: sessionA.sessionId)
        XCTAssertEqual(userAEntries.map(\.id), [outboxEntryA.id])
        let crossOutbox = await store.loadAutosaveOutbox(userId: userB, sessionId: sessionA.sessionId)
        XCTAssertTrue(crossOutbox.isEmpty)

        // User B attempting to delete User A's entry is a no-op
        await store.removeAutosaveOutboxEntry(userId: userB, id: outboxEntryA.id)
        let stillThere = await store.loadAutosaveOutbox(userId: userA, sessionId: sessionA.sessionId)
        XCTAssertEqual(stillThere.count, 1)

        // Local scratchpad draft CRUD & isolation
        let draftA = ExamLocalScratchpadDraft(
            sessionId: sessionA.sessionId,
            userId: userA,
            itemPosition: 1,
            pageNumber: 1,
            captureSource: .paperDenemeBooklet,
            bookletPublisher: "Bilgi Sarmal",
            storageKey: "users/\(userA.uuidString.lowercased())/exam-sessions/\(sessionA.sessionId.uuidString.lowercased())/scratchpad/p1.jpg",
            byteSize: 4096,
            sha256Checksum: String(repeating: "a", count: 64),
            idempotencyKey: "scratch-a-1",
            createdAt: "2026-11-15T10:02:00Z"
        )
        let draftB = ExamLocalScratchpadDraft(
            sessionId: sessionB.sessionId,
            userId: userB,
            itemPosition: 1,
            pageNumber: 1,
            captureSource: .digitalMockScratchpad,
            storageKey: "users/\(userB.uuidString.lowercased())/exam-sessions/\(sessionB.sessionId.uuidString.lowercased())/scratchpad/p1.jpg",
            byteSize: 2048,
            sha256Checksum: String(repeating: "b", count: 64),
            idempotencyKey: "scratch-b-1",
            createdAt: "2026-11-15T10:02:00Z"
        )
        await store.saveLocalScratchpadDraft(draftA)
        await store.saveLocalScratchpadDraft(draftB)

        let draftsA = await store.loadLocalScratchpadDrafts(userId: userA, sessionId: sessionA.sessionId)
        XCTAssertEqual(draftsA.map(\.id), [draftA.id])

        // Purge User A leaves User B intact
        await store.clearUserData(userId: userA)
        let purgedSessionA = await store.loadExamSession(userId: userA, sessionId: sessionA.sessionId)
        let purgedOutboxA = await store.loadAutosaveOutbox(userId: userA, sessionId: sessionA.sessionId)
        let purgedDraftsA = await store.loadLocalScratchpadDrafts(userId: userA, sessionId: sessionA.sessionId)
        XCTAssertNil(purgedSessionA)
        XCTAssertTrue(purgedOutboxA.isEmpty)
        XCTAssertTrue(purgedDraftsA.isEmpty)

        let preservedSessionB = await store.loadExamSession(userId: userB, sessionId: sessionB.sessionId)
        let preservedOutboxB = await store.loadAutosaveOutbox(userId: userB, sessionId: sessionB.sessionId)
        let preservedDraftsB = await store.loadLocalScratchpadDrafts(userId: userB, sessionId: sessionB.sessionId)
        XCTAssertEqual(preservedSessionB?.sessionId, sessionB.sessionId)
        XCTAssertEqual(preservedOutboxB.count, 1)
        XCTAssertEqual(preservedDraftsB.count, 1)
    }

    private func makeSampleSession(sessionId: UUID, userId: UUID, assemblyId: UUID) -> ExamSessionDTO {
        let accommodations = AppliedAccommodationSnapshotDTO(
            extraTimeMultiplier: 1.0,
            highContrastMode: false,
            screenReaderOptimized: false,
            extendedBreakPrompts: false
        )
        let clockSync = ExamServerClockSyncDTO(
            serverNow: "2026-11-15T10:00:00Z",
            remainingSeconds: 900,
            graceRemainingSeconds: 120
        )
        let item = ExamSessionItemDTO(
            id: UUID(),
            itemPosition: 1,
            questionItemId: UUID(),
            questionCode: "TYT-MATH-2026-INT-001",
            sectionCode: "MAIN",
            sectionOrder: 1,
            topicCode: "MATH.ARITHMETIC.INTEGER_OPERATIONS",
            itemFormat: "SINGLE_CHOICE_MCQ",
            difficultyBand: .easy,
            stemMarkdown: "Evaluate (-18) + 27 - (-9)",
            optionsSnapshot: [],
            figureAltText: nil,
            maxPoints: 1.0,
            negativeMarkingWeight: 0.25,
            responseState: .unvisited,
            selectedOptionKey: nil,
            numericResponseValue: nil,
            flaggedForReview: false,
            eliminatedOptionKeys: [],
            timeSpentSeconds: 0,
            autosaveVersion: 1,
            clientAutosaveSeq: 0,
            lastAutosavedAt: nil,
            scratchpadRequired: true,
            scratchpadAssets: [],
            trainingEligible: false
        )
        return ExamSessionDTO(
            sessionId: sessionId,
            userId: userId,
            assemblyId: assemblyId,
            blueprintId: UUID(),
            examCode: "TR_YKS_TYT_MATH",
            examVersionNumber: 1,
            status: .inProgress,
            navigationPolicy: "FREE_NAVIGATION",
            accommodationsSnapshot: accommodations,
            baseDurationSeconds: 900,
            extraTimeMultiplier: 1.0,
            effectiveDurationSeconds: 900,
            startedAt: "2026-11-15T10:00:00Z",
            serverDeadlineAt: "2026-11-15T10:15:00Z",
            graceDeadlineAt: "2026-11-15T10:17:00Z",
            lastHeartbeatAt: "2026-11-15T10:00:00Z",
            totalItemCount: 1,
            answeredItemCount: 0,
            flaggedItemCount: 0,
            omittedItemCount: 1,
            scratchpadAttachmentCount: 0,
            clockSync: clockSync,
            sectionProgress: [],
            items: [item]
        )
    }
}
