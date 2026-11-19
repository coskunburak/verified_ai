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

    func testSignedSubmitDurableIntentRecoveryReconciliationAndKeychainWipe() async throws {
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let service = FixtureExamAPIService()
        service.integrityGuardEnabled = true
        service.simulatedMasterKeyData = Data("synthetic-ios-master-key-minimum-32-bytes-01234567".utf8)
        let proofKeyStore = InMemoryExamProofKeyStore()
        let userA = UUID(uuidString: "91000000-0000-4000-8000-000000000a01")!
        let assemblyId = UUID(uuidString: "94100000-0000-4000-8000-000000000003")!

        final class FakeMonotonicClock: @unchecked Sendable {
            private let lock = NSLock()
            private var value: UInt64
            init(_ initial: UInt64) { self.value = initial }
            func now() -> UInt64 {
                lock.lock()
                defer { lock.unlock() }
                return value
            }
            func advance(by delta: UInt64) -> UInt64 {
                lock.lock()
                defer { lock.unlock() }
                value += delta
                return value
            }
        }

        let fakeClock = FakeMonotonicClock(10_000_000_000)
        let vm = ExamSessionViewModel(
            apiService: service,
            offlineStore: store,
            assemblyId: assemblyId,
            userId: userA,
            proofKeyStore: proofKeyStore,
            monotonicNowProvider: { fakeClock.now() }
        )
        await vm.startOrResumeSession()
        guard let session = vm.session else {
            XCTFail("Expected started session")
            return
        }

        // Proof key prefetched into Keychain-backed store
        let prefetchedKey = try proofKeyStore.proofKeyHex(userId: userA, sessionId: session.sessionId)
        XCTAssertNotNil(prefetchedKey)

        // Monotonic countdown test
        let beforeRemaining = vm.remainingSeconds
        let advancedNs = fakeClock.advance(by: 5_000_000_000)
        vm.reconcileElapsedMonotonicTime(currentUptimeNanoseconds: advancedNs)
        XCTAssertEqual(vm.remainingSeconds, max(0, beforeRemaining - 5))

        // Go offline -> queue option + attempt submit -> intent persists offline without claiming submission succeeded
        service.simulateOffline = true
        await vm.selectOption("B")
        await vm.submitSession(reason: .userInitiated)
        XCTAssertNil(vm.submissionReceipt)
        XCTAssertEqual(vm.pendingSubmitIntent?.state, .retryableFailure)
        let savedIntent = await store.loadPendingSubmitIntent(userId: userA, sessionId: session.sessionId)
        XCTAssertNotNil(savedIntent)
        XCTAssertNotNil(savedIntent?.signature)
        let originalNonce = savedIntent?.nonce
        let originalTs = savedIntent?.canonicalTimestamp
        let originalSig = savedIntent?.signature

        // Reconnect and retry submit -> must reuse exact same nonce, timestamp, signature and clean up proof key + intent
        service.simulateOffline = false
        await vm.submitSession(reason: .userInitiated)
        XCTAssertEqual(vm.submissionReceipt?.status, .submitted)
        XCTAssertNil(vm.pendingSubmitIntent)
        let clearedIntent = await store.loadPendingSubmitIntent(userId: userA, sessionId: session.sessionId)
        XCTAssertNil(clearedIntent)
        let clearedKey = try proofKeyStore.proofKeyHex(userId: userA, sessionId: session.sessionId)
        XCTAssertNil(clearedKey)
        XCTAssertEqual(savedIntent?.nonce, originalNonce)
        XCTAssertEqual(savedIntent?.canonicalTimestamp, originalTs)
        XCTAssertEqual(savedIntent?.signature, originalSig)
    }

    func testCrashRelaunchInFlightIntentReconcilesWithServerCommittedReceipt() async throws {
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let service = FixtureExamAPIService()
        service.integrityGuardEnabled = true
        service.simulatedMasterKeyData = Data("synthetic-ios-master-key-minimum-32-bytes-01234567".utf8)
        let proofKeyStore = InMemoryExamProofKeyStore()
        let userA = UUID(uuidString: "91000000-0000-4000-8000-000000000a01")!
        let assemblyId = UUID(uuidString: "94100000-0000-4000-8000-000000000004")!

        let vm1 = ExamSessionViewModel(
            apiService: service,
            offlineStore: store,
            assemblyId: assemblyId,
            userId: userA,
            proofKeyStore: proofKeyStore
        )
        await vm1.startOrResumeSession()
        guard let started = vm1.session else {
            XCTFail("Expected started session")
            return
        }

        // Server actually committed the submit while client was IN_FLIGHT (e.g. lost HTTP response before app termination)
        let proofHex = try proofKeyStore.proofKeyHex(userId: userA, sessionId: started.sessionId)!
        let idemp = "submit-\(started.sessionId.uuidString.lowercased())-user_initiated"
        let ts = ExamIntegrityCanonicalizer.formatCanonicalInstant(Date())
        let nonce = ExamIntegrityCanonicalizer.generateNonce()
        let canonical = try ExamIntegrityCanonicalizer.buildCanonicalSigningString(
            userId: userA,
            sessionId: started.sessionId,
            submissionReason: .userInitiated,
            idempotencyKey: idemp,
            canonicalTimestamp: ts,
            integrityKeyId: "exam-k1",
            submissionNonce: nonce
        )
        let sig = try ExamIntegrityCanonicalizer.signCanonicalString(canonical, proofKeyHex: proofHex)
        _ = try await service.submitSession(
            sessionId: started.sessionId,
            idempotencyKey: idemp,
            request: ExamSessionSubmitRequestDTO(
                submissionReason: .userInitiated,
                clientTimestamp: ts,
                submissionSignature: sig,
                integrityKeyId: "exam-k1",
                submissionNonce: nonce,
                integrityProtocolVersion: ExamIntegrityCanonicalizer.protocolV1,
                trainingEligible: false
            )
        )

        // Persist simulated IN_FLIGHT intent left over from interrupted client
        let leftover = ExamPendingSubmitIntent(
            userId: userA,
            sessionId: started.sessionId,
            assemblyId: assemblyId,
            originalIdempotencyKey: idemp,
            submissionReason: .userInitiated,
            immutableCanonicalBody: ExamIntegrityCanonicalizer.buildCanonicalSubmitBody(submissionReason: .userInitiated, canonicalTimestamp: ts),
            canonicalTimestamp: ts,
            nonce: nonce,
            keyId: "exam-k1",
            signature: sig,
            createdAt: ts,
            lastAttemptAt: ts,
            retryCount: 0,
            state: .inFlight
        )
        await store.savePendingSubmitIntent(leftover)

        // New ViewModel launches, restores IN_FLIGHT as PENDING_RECONCILIATION, reconciles with server GET state
        let vm2 = ExamSessionViewModel(
            apiService: service,
            offlineStore: store,
            assemblyId: assemblyId,
            userId: userA,
            initialSession: started,
            proofKeyStore: proofKeyStore
        )
        await vm2.submitSession(reason: .userInitiated)
        XCTAssertEqual(vm2.submissionReceipt?.status, .submitted)
        XCTAssertNil(vm2.pendingSubmitIntent)
        let wipedKey = try proofKeyStore.proofKeyHex(userId: userA, sessionId: started.sessionId)
        XCTAssertNil(wipedKey)
    }
}
