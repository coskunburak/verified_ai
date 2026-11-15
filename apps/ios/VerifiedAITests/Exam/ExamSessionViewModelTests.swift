import XCTest
@testable import VerifiedAI

@MainActor
final class ExamSessionViewModelTests: XCTestCase {

    func testStartSessionSelectOptionFlagEliminateAttachScratchpadAndSubmit() async throws {
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let service = FixtureExamAPIService()
        let userA = UUID(uuidString: "91000000-0000-4000-8000-000000000a01")!
        let assemblyId = UUID(uuidString: "94100000-0000-4000-8000-000000000001")!

        let vm = ExamSessionViewModel(
            apiService: service,
            offlineStore: store,
            assemblyId: assemblyId,
            userId: userA
        )

        await vm.startOrResumeSession()

        guard let started = vm.session else {
            XCTFail("Expected started exam session")
            return
        }
        XCTAssertTrue(started.status.isActive)
        XCTAssertGreaterThan(vm.remainingSeconds, 0)
        XCTAssertTrue(started.noHintPolicyEnforced)
        XCTAssertFalse(started.trainingEligible)
        XCTAssertEqual(vm.currentItemPosition, 1)

        await vm.selectOption("B")
        await vm.toggleFlagForReview()
        await vm.toggleEliminatedOption("D")
        await vm.nextItem()
        XCTAssertEqual(vm.currentItemPosition, 2)
        await vm.previousItem()
        XCTAssertEqual(vm.currentItemPosition, 1)

        XCTAssertEqual(vm.currentItem?.selectedOptionKey, "B")
        XCTAssertEqual(vm.currentItem?.flaggedForReview, true)
        XCTAssertEqual(vm.currentItem?.eliminatedOptionKeys, ["D"])
        XCTAssertEqual(vm.session?.answeredItemCount, 1)
        XCTAssertEqual(vm.session?.flaggedItemCount, 1)

        await vm.attachScratchpadPage(
            pageNumber: 1,
            captureSource: .paperDenemeBooklet,
            bookletPublisher: "3D Yayınları",
            payloadData: Data("sample-jpeg-payload".utf8)
        )

        let expectedPrefix = "users/\(userA.uuidString.lowercased())/exam-sessions/\(started.sessionId.uuidString.lowercased())/scratchpad/"
        XCTAssertEqual(vm.currentItem?.scratchpadAssets.count, 1)
        XCTAssertTrue(vm.currentItem?.scratchpadAssets.first?.storageKey.hasPrefix(expectedPrefix) == true)
        XCTAssertEqual(vm.currentItem?.scratchpadAssets.first?.f28VerificationStatus, "QUEUED_FOR_POST_SUBMIT_BATCH")
        XCTAssertEqual(vm.session?.scratchpadAttachmentCount, 1)

        await vm.attemptHintAccess(requestedSurface: "SOCRATIC_HINT_DRAWER")
        XCTAssertTrue(vm.noHintAlertVisible)
        XCTAssertEqual(vm.lastNoHintExplanationKey, "exam.session.no_hint.notice")

        await vm.submitSession(reason: .userInitiated)
        XCTAssertEqual(vm.submissionReceipt?.status, .submitted)
        XCTAssertEqual(vm.submissionReceipt?.f28VerificationBatchManifest.totalAssets, 1)
        XCTAssertEqual(vm.submissionReceipt?.f28VerificationBatchManifest.llmTokensUsed, 0)
        XCTAssertEqual(vm.submissionReceipt?.f28VerificationBatchManifest.trainingEligible, false)

        await vm.attemptHintAccess(requestedSurface: "POST_EXAM_REVIEW")
        XCTAssertFalse(vm.noHintAlertVisible)
    }

    func testOfflineAutosaveOutboxAndScratchpadQueueingAndReconnectReplay() async throws {
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let service = FixtureExamAPIService()
        let userA = UUID(uuidString: "91000000-0000-4000-8000-000000000a01")!
        let assemblyId = UUID(uuidString: "94100000-0000-4000-8000-000000000002")!

        let vmOnline = ExamSessionViewModel(
            apiService: service,
            offlineStore: store,
            assemblyId: assemblyId,
            userId: userA
        )
        await vmOnline.startOrResumeSession()
        guard let startedSession = vmOnline.session else {
            XCTFail("Expected online started session")
            return
        }

        // Go offline and capture answer + deneme scratchpad photo
        service.simulateOffline = true
        let vmOffline = ExamSessionViewModel(
            apiService: service,
            offlineStore: store,
            assemblyId: assemblyId,
            userId: userA,
            initialSession: startedSession
        )
        await vmOffline.selectOption("C")
        await vmOffline.attachScratchpadPage(
            pageNumber: 1,
            captureSource: .paperDenemeBooklet,
            bookletPublisher: "Özdebir",
            payloadData: Data("offline-deneme-photo".utf8)
        )

        XCTAssertTrue(vmOffline.isOfflineGraceActive)
        XCTAssertEqual(vmOffline.outboxPendingCount, 1)
        XCTAssertEqual(vmOffline.localScratchpadDraftCount, 1)

        // Come back online with a fresh ViewModel sharing SQLite store and backend fixture
        service.simulateOffline = false
        let vmReconnect = ExamSessionViewModel(
            apiService: service,
            offlineStore: store,
            assemblyId: assemblyId,
            userId: userA
        )
        await vmReconnect.startOrResumeSession()

        XCTAssertEqual(vmReconnect.outboxPendingCount, 0)
        XCTAssertEqual(vmReconnect.localScratchpadDraftCount, 0)
        XCTAssertEqual(vmReconnect.session?.answeredItemCount, 1)
        XCTAssertEqual(vmReconnect.session?.scratchpadAttachmentCount, 1)
        XCTAssertEqual(vmReconnect.session?.items.first?.selectedOptionKey, "C")
    }
}
