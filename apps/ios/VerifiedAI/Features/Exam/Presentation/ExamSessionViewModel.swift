import CryptoKit
import Foundation
import SwiftUI

@MainActor
public final class ExamSessionViewModel: ObservableObject {
    public enum NavigatorFilter: String, CaseIterable, Identifiable, Sendable {
        case all
        case flagged
        case omitted

        public var id: String { rawValue }
    }

    @Published public private(set) var session: ExamSessionDTO?
    @Published public var currentItemPosition: Int = 1
    @Published public private(set) var remainingSeconds: Int = 0
    @Published public private(set) var graceRemainingSeconds: Int = 0
    @Published public private(set) var isOfflineGraceActive: Bool = false
    @Published public private(set) var clockSkewAnomaly: Bool = false
    @Published public var navigatorFilter: NavigatorFilter = .all

    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var isSaving: Bool = false
    @Published public private(set) var isSubmitting: Bool = false
    @Published public private(set) var isOfflineOutboxPending: Bool = false
    @Published public private(set) var outboxPendingCount: Int = 0
    @Published public private(set) var localScratchpadDraftCount: Int = 0
    @Published public private(set) var submissionReceipt: ExamSessionSubmitResponseDTO?
    @Published public private(set) var pendingSubmitIntent: ExamPendingSubmitIntent?
    @Published public var showSubmitConfirmation: Bool = false
    @Published public var showScoreSummary: Bool = false
    @Published public var noHintAlertVisible: Bool = false
    @Published public private(set) var lastNoHintExplanationKey: String?
    @Published public private(set) var errorLocalizationKey: String?
    @Published public private(set) var errorMessage: String?

    @Published public var selectedScratchpadSource: ExamScratchpadCaptureSource = .paperDenemeBooklet
    @Published public var bookletPublisherText: String = ""
    @Published public var numericInputText: String = ""

    private let apiService: ExamAPIServicing
    private let offlineStore: ExamOfflineSQLiteStore?
    private let proofKeyStore: ExamProofKeyStoring
    private let monotonicNowProvider: @Sendable () -> UInt64
    private let assemblyId: UUID
    private var activeUserId: UUID
    private var clientAutosaveSeq: Int64 = 0
    private var monotonicAnchorUptimeNs: UInt64?
    private var anchorRemainingSeconds: Int = 0
    private var anchorGraceRemainingSeconds: Int = 0

    public init(
        apiService: ExamAPIServicing,
        offlineStore: ExamOfflineSQLiteStore?,
        assemblyId: UUID,
        userId: UUID = ExamBlueprintAssemblyViewModel.defaultLearnerId,
        initialSession: ExamSessionDTO? = nil,
        proofKeyStore: ExamProofKeyStoring = KeychainExamProofKeyStore(),
        monotonicNowProvider: @escaping @Sendable () -> UInt64 = { DispatchTime.now().uptimeNanoseconds }
    ) {
        self.apiService = apiService
        self.offlineStore = offlineStore
        self.proofKeyStore = proofKeyStore
        self.monotonicNowProvider = monotonicNowProvider
        self.assemblyId = assemblyId
        self.activeUserId = userId
        if let initialSession {
            applySessionSnapshot(initialSession)
        }
    }

    public var currentUserId: UUID {
        activeUserId
    }

    public func makeScoreSummaryViewModel() -> ExamScoreSummaryViewModel {
        let resolvedSessionId = session?.sessionId ?? submissionReceipt?.sessionId ?? UUID()
        return ExamScoreSummaryViewModel(
            sessionId: resolvedSessionId,
            userId: activeUserId,
            highContrastMode: highContrastMode,
            apiService: apiService,
            offlineStore: offlineStore
        )
    }

    public var highContrastMode: Bool {
        session?.accommodationsSnapshot.highContrastMode ?? false
    }

    public var currentItem: ExamSessionItemDTO? {
        guard let session else { return nil }
        return session.items.first(where: { $0.itemPosition == currentItemPosition }) ?? session.items.first
    }

    public var filteredNavigatorItems: [ExamSessionItemDTO] {
        guard let items = session?.items else { return [] }
        switch navigatorFilter {
        case .all:
            return items
        case .flagged:
            return items.filter(\.flaggedForReview)
        case .omitted:
            return items.filter { item in
                let hasOption = item.selectedOptionKey != nil && !(item.selectedOptionKey?.isEmpty ?? true)
                return !hasOption && item.numericResponseValue == nil
            }
        }
    }

    public var formattedRemainingTime: String {
        let total = max(0, remainingSeconds)
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        let seconds = total % 60
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        }
        return String(format: "%02d:%02d", minutes, seconds)
    }

    public func startOrResumeSession() async {
        isLoading = true
        errorLocalizationKey = nil
        errorMessage = nil
        defer { isLoading = false }

        let nowString = ISO8601DateFormatter().string(from: Date())
        let request = ExamSessionStartRequestDTO(
            userId: activeUserId,
            assemblyId: assemblyId,
            offlineGraceBufferSeconds: 120,
            clientTimestamp: nowString,
            trainingEligible: false
        )

        do {
            let started = try await apiService.startOrResumeSession(request: request)
            applySessionSnapshot(started)
            await offlineStore?.saveExamSession(started)
            await prefetchIntegrityProofIfAvailable(for: started)
            await restorePendingSubmitIntent(for: started.sessionId)
            await reloadLocalPendingCounts(for: started.sessionId)
            await flushOfflineOutbox()
        } catch {
            if let cached = await offlineStore?.loadActiveExamSession(userId: activeUserId, assemblyId: assemblyId) {
                applySessionSnapshot(cached)
                isOfflineGraceActive = true
                await restorePendingSubmitIntent(for: cached.sessionId)
                await reloadLocalPendingCounts(for: cached.sessionId)
            } else {
                mapError(error)
            }
        }
    }

    public func refreshHeartbeat() async {
        guard let session else { return }
        let nowString = ISO8601DateFormatter().string(from: Date())
        do {
            let updated = try await apiService.fetchSessionState(sessionId: session.sessionId, clientTimestamp: nowString)
            applySessionSnapshot(updated)
            await offlineStore?.saveExamSession(updated)
            await flushOfflineOutbox()
        } catch let error as ExamServiceError where error == .offline {
            isOfflineGraceActive = true
        } catch {
            mapError(error)
        }
    }

    public func tickCountdown() {
        reconcileElapsedMonotonicTime()
    }

    public func reconcileElapsedMonotonicTime(currentUptimeNanoseconds: UInt64? = nil) {
        guard let session, session.status.isActive else { return }
        let nowNs = currentUptimeNanoseconds ?? monotonicNowProvider()
        guard let anchorNs = monotonicAnchorUptimeNs else {
            monotonicAnchorUptimeNs = nowNs
            anchorRemainingSeconds = remainingSeconds
            anchorGraceRemainingSeconds = graceRemainingSeconds
            return
        }
        // Monotonic elapsed seconds never go negative even if wall-clock Date is rolled back
        let elapsedSeconds = nowNs >= anchorNs ? Int((nowNs - anchorNs) / 1_000_000_000) : 0
        if elapsedSeconds == 0 {
            if remainingSeconds > 0 {
                remainingSeconds -= 1
            } else if isOfflineGraceActive && graceRemainingSeconds > 0 {
                graceRemainingSeconds -= 1
            }
            return
        }
        let nextRemaining = max(0, anchorRemainingSeconds - elapsedSeconds)
        remainingSeconds = nextRemaining
        if isOfflineGraceActive || nextRemaining == 0 {
            graceRemainingSeconds = max(0, anchorGraceRemainingSeconds - elapsedSeconds)
        }
    }

    public func selectItem(position: Int) {
        guard let session,
              let target = session.items.first(where: { $0.itemPosition == position }) else {
            return
        }
        currentItemPosition = target.itemPosition
        syncNumericInput(for: target)
    }

    public func nextItem() async {
        guard let session else { return }
        let sortedPositions = session.items.map(\.itemPosition).sorted()
        guard let idx = sortedPositions.firstIndex(of: currentItemPosition),
              idx + 1 < sortedPositions.count else {
            return
        }
        let nextPos = sortedPositions[idx + 1]
        selectItem(position: nextPos)
        await markCurrentItemViewedIfUnvisited()
    }

    public func previousItem() async {
        guard let session else { return }
        let sortedPositions = session.items.map(\.itemPosition).sorted()
        guard let idx = sortedPositions.firstIndex(of: currentItemPosition),
              idx > 0 else {
            return
        }
        let prevPos = sortedPositions[idx - 1]
        selectItem(position: prevPos)
        await markCurrentItemViewedIfUnvisited()
    }

    public func selectOption(_ optionKey: String) async {
        await performAutosave(
            action: .selectOption,
            selectedOptionKey: optionKey,
            numericResponseValue: nil,
            flaggedForReview: nil,
            eliminatedOptionKeys: nil
        )
    }

    public func setNumericResponse(_ value: Double) async {
        await performAutosave(
            action: .selectOption,
            selectedOptionKey: nil,
            numericResponseValue: value,
            flaggedForReview: nil,
            eliminatedOptionKeys: nil
        )
    }

    public func clearAnswer() async {
        numericInputText = ""
        await performAutosave(
            action: .clearAnswer,
            selectedOptionKey: nil,
            numericResponseValue: nil,
            flaggedForReview: nil,
            eliminatedOptionKeys: nil
        )
    }

    public func toggleFlagForReview() async {
        guard let item = currentItem else { return }
        await performAutosave(
            action: .toggleFlag,
            selectedOptionKey: nil,
            numericResponseValue: nil,
            flaggedForReview: !item.flaggedForReview,
            eliminatedOptionKeys: nil
        )
    }

    public func toggleEliminatedOption(_ optionKey: String) async {
        guard let item = currentItem else { return }
        var eliminated = item.eliminatedOptionKeys
        if let existingIdx = eliminated.firstIndex(of: optionKey) {
            eliminated.remove(at: existingIdx)
        } else {
            eliminated.append(optionKey)
        }
        await performAutosave(
            action: .setEliminatedOptions,
            selectedOptionKey: nil,
            numericResponseValue: nil,
            flaggedForReview: nil,
            eliminatedOptionKeys: eliminated
        )
    }

    public func attachScratchpadPage(
        pageNumber: Int,
        captureSource: ExamScratchpadCaptureSource? = nil,
        bookletPublisher: String? = nil,
        payloadData: Data? = nil,
        mimeType: String = "image/jpeg"
    ) async {
        guard var currentSession = session,
              let itemIndex = currentSession.items.firstIndex(where: { $0.itemPosition == currentItemPosition }) else {
            return
        }
        let item = currentSession.items[itemIndex]
        guard item.scratchpadAssets.count < 4 else {
            errorLocalizationKey = "exam.error.unexpected"
            return
        }

        let effectiveSource = captureSource ?? selectedScratchpadSource
        let publisherRaw = (bookletPublisher ?? bookletPublisherText).trimmingCharacters(in: .whitespacesAndNewlines)
        let effectivePublisher = publisherRaw.isEmpty ? nil : publisherRaw
        let bytes = payloadData ?? Data("deneme-scratchpad-\(currentSession.sessionId.uuidString)-p\(item.itemPosition)-\(pageNumber)".utf8)
        let digest = SHA256.hash(data: bytes)
        let sha256Hex = digest.map { String(format: "%02x", $0) }.joined()
        let storageKey = "users/\(activeUserId.uuidString.lowercased())/exam-sessions/\(currentSession.sessionId.uuidString.lowercased())/scratchpad/item-\(item.itemPosition)-page-\(pageNumber).jpg"
        let nowString = ISO8601DateFormatter().string(from: Date())
        let idempotencyKey = "scratch-\(currentSession.sessionId.uuidString.lowercased())-\(item.itemPosition)-\(pageNumber)-\(UUID().uuidString.lowercased())"

        let draft = ExamLocalScratchpadDraft(
            sessionId: currentSession.sessionId,
            userId: activeUserId,
            itemPosition: item.itemPosition,
            pageNumber: pageNumber,
            captureSource: effectiveSource,
            bookletPublisher: effectivePublisher,
            storageKey: storageKey,
            mimeType: mimeType,
            byteSize: Int64(max(1, bytes.count)),
            sha256Checksum: sha256Hex,
            uploadState: "QUEUED_LOCAL",
            idempotencyKey: idempotencyKey,
            createdAt: nowString
        )

        let request = ExamScratchpadUploadRequestDTO(
            pageNumber: pageNumber,
            storageKey: storageKey,
            mimeType: mimeType,
            byteSize: Int64(max(1, bytes.count)),
            sha256Checksum: sha256Hex,
            widthPx: 1080,
            heightPx: 1440,
            captureSource: effectiveSource,
            bookletPublisher: effectivePublisher,
            capturedAt: nowString,
            trainingEligible: false
        )

        isSaving = true
        defer { isSaving = false }

        do {
            let uploaded = try await apiService.attachScratchpadPhoto(
                sessionId: currentSession.sessionId,
                itemPosition: item.itemPosition,
                idempotencyKey: idempotencyKey,
                request: request
            )
            currentSession.items[itemIndex].scratchpadAssets.append(uploaded.asset)
            currentSession.scratchpadAttachmentCount = uploaded.scratchpadAttachmentCount
            currentSession.clockSync = uploaded.clockSync
            applySessionSnapshot(currentSession)
            await offlineStore?.saveExamSession(currentSession)
            await offlineStore?.removeLocalScratchpadDraft(userId: activeUserId, id: draft.id)
        } catch let error as ExamServiceError where error == .offline {
            await offlineStore?.saveLocalScratchpadDraft(draft)
            isOfflineGraceActive = true
            isOfflineOutboxPending = true
            await reloadLocalPendingCounts(for: currentSession.sessionId)
        } catch {
            mapError(error)
        }
    }

    public func flushOfflineOutbox() async {
        guard let session, let offlineStore else { return }
        let entries = await offlineStore.loadAutosaveOutbox(userId: activeUserId, sessionId: session.sessionId)
        for entry in entries {
            do {
                var replayReq = entry.request
                if let latestItem = self.session?.items.first(where: { $0.itemPosition == entry.itemPosition }) {
                    replayReq = ExamSessionItemAutosaveRequestDTO(
                        action: entry.request.action,
                        selectedOptionKey: entry.request.selectedOptionKey,
                        numericResponseValue: entry.request.numericResponseValue,
                        flaggedForReview: entry.request.flaggedForReview,
                        eliminatedOptionKeys: entry.request.eliminatedOptionKeys,
                        deltaTimeSpentSeconds: entry.request.deltaTimeSpentSeconds,
                        expectedAutosaveVersion: latestItem.autosaveVersion,
                        clientAutosaveSeq: entry.request.clientAutosaveSeq,
                        offlineReplay: true,
                        clientTimestamp: entry.request.clientTimestamp,
                        trainingEligible: false
                    )
                }
                let response = try await apiService.autosaveSessionItem(
                    sessionId: entry.sessionId,
                    itemPosition: entry.itemPosition,
                    idempotencyKey: entry.idempotencyKey,
                    request: replayReq
                )
                applyAutosaveResponse(response)
                await offlineStore.removeAutosaveOutboxEntry(userId: activeUserId, id: entry.id)
            } catch let error as ExamServiceError where error == .offline {
                isOfflineGraceActive = true
                break
            } catch let error as ExamServiceError where error == .sessionAlreadySubmitted || error == .deadlineExpired {
                await offlineStore.removeAutosaveOutboxEntry(userId: activeUserId, id: entry.id)
            } catch {
                // Retain transient failures in durable SQLite outbox for retry rather than dropping student answers
                break
            }
        }

        let drafts = await offlineStore.loadLocalScratchpadDrafts(userId: activeUserId, sessionId: session.sessionId)
        for draft in drafts {
            let req = ExamScratchpadUploadRequestDTO(
                pageNumber: draft.pageNumber,
                storageKey: draft.storageKey,
                mimeType: draft.mimeType,
                byteSize: draft.byteSize,
                sha256Checksum: draft.sha256Checksum,
                widthPx: 1080,
                heightPx: 1440,
                captureSource: draft.captureSource,
                bookletPublisher: draft.bookletPublisher,
                capturedAt: draft.createdAt,
                trainingEligible: false
            )
            do {
                let uploaded = try await apiService.attachScratchpadPhoto(
                    sessionId: draft.sessionId,
                    itemPosition: draft.itemPosition,
                    idempotencyKey: draft.idempotencyKey,
                    request: req
                )
                if var updatedSession = self.session,
                   let idx = updatedSession.items.firstIndex(where: { $0.itemPosition == draft.itemPosition }) {
                    updatedSession.items[idx].scratchpadAssets.append(uploaded.asset)
                    updatedSession.scratchpadAttachmentCount = uploaded.scratchpadAttachmentCount
                    updatedSession.clockSync = uploaded.clockSync
                    applySessionSnapshot(updatedSession)
                }
                await offlineStore.removeLocalScratchpadDraft(userId: activeUserId, id: draft.id)
            } catch let error as ExamServiceError where error == .offline {
                isOfflineGraceActive = true
                break
            } catch let error as ExamServiceError where error == .invalidScratchpadAsset || error == .sessionAlreadySubmitted || error == .deadlineExpired {
                await offlineStore.removeLocalScratchpadDraft(userId: activeUserId, id: draft.id)
            } catch {
                // Retain draft on transient 5xx / timeout
                break
            }
        }

        await reloadLocalPendingCounts(for: session.sessionId)
    }

    public func submitSession(reason: ExamSessionSubmissionReason = .userInitiated) async {
        guard var currentSession = session else { return }
        isSubmitting = true
        showSubmitConfirmation = false
        errorLocalizationKey = nil
        errorMessage = nil
        defer { isSubmitting = false }

        // 1. Reconcile any existing durable submit intent first without minting duplicate timestamps/nonces
        var intent: ExamPendingSubmitIntent? = pendingSubmitIntent
        if intent == nil {
            intent = await offlineStore?.loadPendingSubmitIntent(userId: activeUserId, sessionId: currentSession.sessionId)
        }
        if let existing = intent, existing.state == .inFlight || existing.state == .pendingReconciliation {
            if let serverState = try? await apiService.fetchSessionState(
                sessionId: currentSession.sessionId,
                clientTimestamp: ExamIntegrityCanonicalizer.formatCanonicalInstant(Date())
            ), !serverState.status.isActive {
                await finalizeReceiptFromServerState(serverState, idempotencyKey: existing.originalIdempotencyKey)
                return
            }
        }

        // 2. Build & persist immutable submit intent before network I/O
        let activeIntent: ExamPendingSubmitIntent
        if var reused = intent, reused.state != .rejected && reused.state != .expired && reused.state != .acknowledged {
            reused.retryCount += 1
            reused.lastAttemptAt = ExamIntegrityCanonicalizer.formatCanonicalInstant(Date())
            activeIntent = reused
        } else {
            let now = Date()
            let canonicalTs = ExamIntegrityCanonicalizer.formatCanonicalInstant(now)
            let idempotencyKey = "submit-\(currentSession.sessionId.uuidString.lowercased())-\(reason.rawValue.lowercased())"
            let nonce = ExamIntegrityCanonicalizer.generateNonce()
            let canonicalBody = ExamIntegrityCanonicalizer.buildCanonicalSubmitBody(
                submissionReason: reason,
                canonicalTimestamp: canonicalTs
            )
            let signingMaterial = await resolveSigningMaterial(
                session: currentSession,
                reason: reason,
                idempotencyKey: idempotencyKey,
                canonicalTimestamp: canonicalTs,
                nonce: nonce
            )
            activeIntent = ExamPendingSubmitIntent(
                userId: activeUserId,
                sessionId: currentSession.sessionId,
                assemblyId: currentSession.assemblyId,
                originalIdempotencyKey: idempotencyKey,
                submissionReason: reason,
                canonicalPayloadVersion: ExamIntegrityCanonicalizer.protocolV1,
                immutableCanonicalBody: canonicalBody,
                canonicalTimestamp: canonicalTs,
                nonce: nonce,
                keyId: signingMaterial.keyId,
                signature: signingMaterial.signature,
                createdAt: canonicalTs,
                lastAttemptAt: canonicalTs,
                retryCount: 0,
                state: .pendingUpload
            )
        }
        pendingSubmitIntent = activeIntent
        await offlineStore?.savePendingSubmitIntent(activeIntent)

        // 3. Flush offline outbox; block optimistic completion if unsent answers/scratchpads remain
        await flushOfflineOutbox()
        if isOfflineOutboxPending {
            var deferred = activeIntent
            deferred.state = .retryableFailure
            pendingSubmitIntent = deferred
            await offlineStore?.savePendingSubmitIntent(deferred)
            isOfflineGraceActive = true
            errorLocalizationKey = "exam.integrity.status.pending_submit_recovery"
            return
        }

        // 4. Transition intent to IN_FLIGHT and transmit the immutable signed request
        var inFlight = activeIntent
        inFlight.state = .inFlight
        inFlight.lastAttemptAt = ExamIntegrityCanonicalizer.formatCanonicalInstant(Date())
        pendingSubmitIntent = inFlight
        await offlineStore?.savePendingSubmitIntent(inFlight)

        let request = ExamSessionSubmitRequestDTO(
            submissionReason: inFlight.submissionReason,
            clientTimestamp: inFlight.canonicalTimestamp,
            submissionSignature: inFlight.signature,
            integrityKeyId: inFlight.keyId,
            submissionNonce: inFlight.nonce,
            integrityProtocolVersion: inFlight.signature != nil ? inFlight.canonicalPayloadVersion : nil,
            trainingEligible: false
        )

        do {
            let receipt = try await apiService.submitSession(
                sessionId: currentSession.sessionId,
                idempotencyKey: inFlight.originalIdempotencyKey,
                request: request
            )
            submissionReceipt = receipt
            currentSession.status = receipt.status
            currentSession.submittedAt = receipt.submittedAt
            currentSession.submissionReason = receipt.submissionReason
            currentSession.answeredItemCount = receipt.answeredItemCount
            currentSession.flaggedItemCount = receipt.flaggedItemCount
            currentSession.omittedItemCount = receipt.omittedItemCount
            currentSession.scratchpadAttachmentCount = receipt.scratchpadAttachmentCount
            currentSession.f28VerificationBatchManifest = receipt.f28VerificationBatchManifest
            currentSession.clockSync = receipt.clockSync
            applySessionSnapshot(currentSession)
            await offlineStore?.saveExamSession(currentSession)
            await offlineStore?.removePendingSubmitIntent(userId: activeUserId, sessionId: currentSession.sessionId)
            pendingSubmitIntent = nil
            try? proofKeyStore.removeProofKey(userId: activeUserId, sessionId: currentSession.sessionId)
        } catch let error as ExamServiceError where error == .sessionAlreadySubmitted {
            if let serverState = try? await apiService.fetchSessionState(
                sessionId: currentSession.sessionId,
                clientTimestamp: ExamIntegrityCanonicalizer.formatCanonicalInstant(Date())
            ), !serverState.status.isActive {
                await finalizeReceiptFromServerState(serverState, idempotencyKey: inFlight.originalIdempotencyKey)
            } else {
                var failed = inFlight
                failed.state = .rejected
                pendingSubmitIntent = failed
                await offlineStore?.savePendingSubmitIntent(failed)
                mapError(error)
            }
        } catch {
            var retryable = inFlight
            if let examError = error as? ExamServiceError {
                switch examError {
                case .deadlineExpired:
                    retryable.state = .expired
                case .integrityFailed, .retryConflict, .forbidden:
                    retryable.state = .rejected
                default:
                    retryable.state = .retryableFailure
                }
            } else {
                retryable.state = .retryableFailure
            }
            pendingSubmitIntent = retryable
            await offlineStore?.savePendingSubmitIntent(retryable)
            mapError(error)
        }
    }

    public func attemptHintAccess(requestedSurface: String = "SOCRATIC_HINT_DRAWER") async {
        guard let session else { return }
        if session.status.isActive && session.noHintPolicyEnforced {
            lastNoHintExplanationKey = "exam.session.no_hint.notice"
            noHintAlertVisible = true
            do {
                let guardResult = try await apiService.checkNoHintGuard(
                    sessionId: session.sessionId,
                    requestedSurface: requestedSurface
                )
                if !guardResult.hintAllowed {
                    lastNoHintExplanationKey = "exam.session.no_hint.notice"
                    noHintAlertVisible = true
                }
            } catch {
                lastNoHintExplanationKey = "exam.session.no_hint.notice"
                noHintAlertVisible = true
            }
            return
        }
        noHintAlertVisible = false
    }

    public func purgeUserDataAndReset() async {
        await offlineStore?.clearUserData(userId: activeUserId)
        try? proofKeyStore.removeAllProofKeys(userId: activeUserId)
        pendingSubmitIntent = nil
        session = nil
        submissionReceipt = nil
        currentItemPosition = 1
        remainingSeconds = 0
        graceRemainingSeconds = 0
        isOfflineGraceActive = false
        clockSkewAnomaly = false
        isOfflineOutboxPending = false
        outboxPendingCount = 0
        localScratchpadDraftCount = 0
        noHintAlertVisible = false
        errorLocalizationKey = nil
        errorMessage = nil
    }

    // MARK: - Private Helpers

    private func markCurrentItemViewedIfUnvisited() async {
        guard let item = currentItem, item.responseState == .unvisited else { return }
        await performAutosave(
            action: .viewOnly,
            selectedOptionKey: nil,
            numericResponseValue: nil,
            flaggedForReview: nil,
            eliminatedOptionKeys: nil
        )
    }

    private func performAutosave(
        action: ExamSessionItemAction,
        selectedOptionKey: String?,
        numericResponseValue: Double?,
        flaggedForReview: Bool?,
        eliminatedOptionKeys: [String]?
    ) async {
        guard var currentSession = session,
              let idx = currentSession.items.firstIndex(where: { $0.itemPosition == currentItemPosition }) else {
            return
        }

        let currentItem = currentSession.items[idx]
        clientAutosaveSeq += 1
        let nextSeq = clientAutosaveSeq
        let expectedVersion = currentItem.autosaveVersion
        let nowString = ISO8601DateFormatter().string(from: Date())
        let idempotencyKey = "auto-\(currentSession.sessionId.uuidString.lowercased())-\(currentItem.itemPosition)-\(nextSeq)"

        let request = ExamSessionItemAutosaveRequestDTO(
            action: action,
            selectedOptionKey: selectedOptionKey,
            numericResponseValue: numericResponseValue,
            flaggedForReview: flaggedForReview,
            eliminatedOptionKeys: eliminatedOptionKeys,
            deltaTimeSpentSeconds: 2,
            expectedAutosaveVersion: expectedVersion,
            clientAutosaveSeq: nextSeq,
            offlineReplay: false,
            clientTimestamp: nowString,
            trainingEligible: false
        )

        isSaving = true
        defer { isSaving = false }

        do {
            let response = try await apiService.autosaveSessionItem(
                sessionId: currentSession.sessionId,
                itemPosition: currentItem.itemPosition,
                idempotencyKey: idempotencyKey,
                request: request
            )
            applyAutosaveResponse(response)
            if let updated = self.session {
                await offlineStore?.saveExamSession(updated)
            }
        } catch let error as ExamServiceError where error == .offline {
            var optimisticItem = currentItem
            switch action {
            case .selectOption:
                optimisticItem.selectedOptionKey = selectedOptionKey
                optimisticItem.numericResponseValue = numericResponseValue
                optimisticItem.responseState = optimisticItem.flaggedForReview ? .flaggedAnswered : .answered
            case .clearAnswer:
                optimisticItem.selectedOptionKey = nil
                optimisticItem.numericResponseValue = nil
                optimisticItem.responseState = optimisticItem.flaggedForReview ? .flaggedOmitted : .viewedOmitted
            case .toggleFlag:
                let nextFlag = flaggedForReview ?? !optimisticItem.flaggedForReview
                optimisticItem.flaggedForReview = nextFlag
                let hasAnswer = optimisticItem.selectedOptionKey != nil || optimisticItem.numericResponseValue != nil
                optimisticItem.responseState = nextFlag ? (hasAnswer ? .flaggedAnswered : .flaggedOmitted) : (hasAnswer ? .answered : .viewedOmitted)
            case .setEliminatedOptions:
                optimisticItem.eliminatedOptionKeys = eliminatedOptionKeys ?? []
            case .viewOnly:
                if optimisticItem.responseState == .unvisited {
                    optimisticItem.responseState = .viewedOmitted
                }
            case .batchPatch:
                break
            }
            optimisticItem.clientAutosaveSeq = nextSeq
            currentSession.items[idx] = optimisticItem
            recomputeSessionCounters(&currentSession)
            applySessionSnapshot(currentSession)
            await offlineStore?.saveExamSession(currentSession)

            let outboxEntry = ExamAutosaveOutboxEntry(
                sessionId: currentSession.sessionId,
                userId: activeUserId,
                itemPosition: currentItem.itemPosition,
                idempotencyKey: idempotencyKey,
                request: request,
                enqueuedAt: nowString
            )
            await offlineStore?.enqueueAutosaveOutbox(outboxEntry)
            isOfflineGraceActive = true
            await reloadLocalPendingCounts(for: currentSession.sessionId)
        } catch {
            mapError(error)
        }
    }

    private func applyAutosaveResponse(_ response: ExamSessionItemAutosaveResponseDTO) {
        guard var currentSession = session,
              let idx = currentSession.items.firstIndex(where: { $0.itemPosition == response.itemPosition }) else {
            return
        }
        currentSession.items[idx].responseState = response.responseState
        currentSession.items[idx].selectedOptionKey = response.selectedOptionKey
        currentSession.items[idx].numericResponseValue = response.numericResponseValue
        currentSession.items[idx].flaggedForReview = response.flaggedForReview
        currentSession.items[idx].eliminatedOptionKeys = response.eliminatedOptionKeys
        currentSession.items[idx].timeSpentSeconds = response.timeSpentSeconds
        currentSession.items[idx].autosaveVersion = response.autosaveVersion
        currentSession.items[idx].clientAutosaveSeq = response.clientAutosaveSeq
        currentSession.answeredItemCount = response.answeredItemCount
        currentSession.flaggedItemCount = response.flaggedItemCount
        currentSession.omittedItemCount = response.omittedItemCount
        currentSession.status = response.status
        currentSession.clockSync = response.clockSync
        applySessionSnapshot(currentSession)
    }

    private func applySessionSnapshot(_ snapshot: ExamSessionDTO) {
        session = snapshot
        remainingSeconds = snapshot.clockSync.remainingSeconds
        graceRemainingSeconds = snapshot.clockSync.graceRemainingSeconds
        monotonicAnchorUptimeNs = monotonicNowProvider()
        anchorRemainingSeconds = snapshot.clockSync.remainingSeconds
        anchorGraceRemainingSeconds = snapshot.clockSync.graceRemainingSeconds
        isOfflineGraceActive = snapshot.clockSync.offlineGraceActive || snapshot.status == .offlineGraceBuffer
        clockSkewAnomaly = snapshot.clockSync.clockSkewAnomaly
        if let current = snapshot.items.first(where: { $0.itemPosition == currentItemPosition }) {
            syncNumericInput(for: current)
        } else if let first = snapshot.items.first {
            currentItemPosition = first.itemPosition
            syncNumericInput(for: first)
        }
    }

    private func syncNumericInput(for item: ExamSessionItemDTO) {
        if let val = item.numericResponseValue {
            numericInputText = String(val)
        } else {
            numericInputText = ""
        }
    }

    private func recomputeSessionCounters(_ snapshot: inout ExamSessionDTO) {
        let answered = snapshot.items.filter {
            $0.responseState == .answered || $0.responseState == .flaggedAnswered
        }.count
        let flagged = snapshot.items.filter(\.flaggedForReview).count
        snapshot.answeredItemCount = answered
        snapshot.flaggedItemCount = flagged
        snapshot.omittedItemCount = max(0, snapshot.totalItemCount - answered)
    }

    private func reloadLocalPendingCounts(for sessionId: UUID) async {
        guard let offlineStore else { return }
        let outbox = await offlineStore.loadAutosaveOutbox(userId: activeUserId, sessionId: sessionId)
        let drafts = await offlineStore.loadLocalScratchpadDrafts(userId: activeUserId, sessionId: sessionId)
        outboxPendingCount = outbox.count
        localScratchpadDraftCount = drafts.count
        isOfflineOutboxPending = !outbox.isEmpty || !drafts.isEmpty
    }

    private func prefetchIntegrityProofIfAvailable(for session: ExamSessionDTO) async {
        guard session.status.isActive else { return }
        if (try? proofKeyStore.proofKeyHex(userId: activeUserId, sessionId: session.sessionId)) != nil {
            return
        }
        guard let proof = try? await apiService.fetchSessionIntegrityProof(sessionId: session.sessionId),
              proof.integrityGuardEnabled,
              let keyHex = proof.sessionProofKeyHex else {
            return
        }
        try? proofKeyStore.saveProofKeyHex(keyHex, userId: activeUserId, sessionId: session.sessionId)
    }

    private func restorePendingSubmitIntent(for sessionId: UUID) async {
        guard var intent = await offlineStore?.loadPendingSubmitIntent(userId: activeUserId, sessionId: sessionId) else {
            return
        }
        if intent.state == .inFlight {
            intent.state = .pendingReconciliation
            await offlineStore?.savePendingSubmitIntent(intent)
        }
        pendingSubmitIntent = intent
    }

    private func resolveSigningMaterial(
        session: ExamSessionDTO,
        reason: ExamSessionSubmissionReason,
        idempotencyKey: String,
        canonicalTimestamp: String,
        nonce: String
    ) async -> (keyId: String?, signature: String?) {
        var proofKeyHex = try? proofKeyStore.proofKeyHex(userId: activeUserId, sessionId: session.sessionId)
        var resolvedKeyId = "exam-k1"
        if proofKeyHex == nil,
           let proof = try? await apiService.fetchSessionIntegrityProof(sessionId: session.sessionId) {
            resolvedKeyId = proof.keyId
            if proof.integrityGuardEnabled, let hex = proof.sessionProofKeyHex {
                proofKeyHex = hex
                try? proofKeyStore.saveProofKeyHex(hex, userId: activeUserId, sessionId: session.sessionId)
            }
        }
        guard let hex = proofKeyHex,
              let canonical = try? ExamIntegrityCanonicalizer.buildCanonicalSigningString(
                  userId: session.userId,
                  sessionId: session.sessionId,
                  submissionReason: reason,
                  idempotencyKey: idempotencyKey,
                  canonicalTimestamp: canonicalTimestamp,
                  integrityKeyId: resolvedKeyId,
                  submissionNonce: nonce
              ),
              let sig = try? ExamIntegrityCanonicalizer.signCanonicalString(canonical, proofKeyHex: hex) else {
            return (nil, nil)
        }
        return (resolvedKeyId, sig)
    }

    private func finalizeReceiptFromServerState(_ serverState: ExamSessionDTO, idempotencyKey: String) async {
        if let manifest = serverState.f28VerificationBatchManifest,
           let submittedAt = serverState.submittedAt {
            submissionReceipt = ExamSessionSubmitResponseDTO(
                sessionId: serverState.sessionId,
                assemblyId: serverState.assemblyId,
                blueprintId: serverState.blueprintId,
                examCode: serverState.examCode,
                status: serverState.status,
                submissionReason: serverState.submissionReason ?? "USER_INITIATED",
                submittedAt: submittedAt,
                submitIdempotencyKey: idempotencyKey,
                totalItemCount: serverState.totalItemCount,
                answeredItemCount: serverState.answeredItemCount,
                flaggedItemCount: serverState.flaggedItemCount,
                omittedItemCount: serverState.omittedItemCount,
                scratchpadAttachmentCount: serverState.scratchpadAttachmentCount,
                f28VerificationBatchManifest: manifest,
                clockSync: serverState.clockSync,
                llmTokensUsed: 0,
                trainingEligible: false
            )
        }
        applySessionSnapshot(serverState)
        await offlineStore?.saveExamSession(serverState)
        await offlineStore?.removePendingSubmitIntent(userId: activeUserId, sessionId: serverState.sessionId)
        pendingSubmitIntent = nil
        try? proofKeyStore.removeProofKey(userId: activeUserId, sessionId: serverState.sessionId)
    }

    private func mapError(_ error: Error) {
        if let examError = error as? ExamServiceError {
            switch examError {
            case .noHintModeActive:
                noHintAlertVisible = true
                lastNoHintExplanationKey = "exam.session.no_hint.notice"
            case .deadlineExpired, .sessionAlreadySubmitted:
                errorLocalizationKey = "exam.session.submit.receipt_title"
            case .autosaveVersionConflict:
                errorLocalizationKey = "exam.error.version_conflict"
            case .signatureRequired:
                errorLocalizationKey = "exam.integrity.error.signature_required"
            case .integrityFailed:
                errorLocalizationKey = "exam.integrity.error.verification_needed"
            case .clockResyncRequired:
                errorLocalizationKey = "exam.integrity.error.clock_resync"
            case .retryConflict:
                errorLocalizationKey = "exam.integrity.error.retry_conflict"
            case .proofExpired:
                errorLocalizationKey = "exam.integrity.error.proof_expired"
            case .integrityConfigUnavailable:
                errorLocalizationKey = "exam.error.unexpected"
            case .invalidScratchpadAsset:
                errorLocalizationKey = "exam.error.unexpected"
            case .featureDisabled:
                errorLocalizationKey = "exam.catalog.feature_disabled"
            case .forbidden:
                errorLocalizationKey = "exam.error.forbidden"
            case .notFound:
                errorLocalizationKey = "exam.error.not_found"
            case .offline:
                isOfflineGraceActive = true
            default:
                errorLocalizationKey = "exam.error.unexpected"
            }
        } else {
            errorMessage = error.localizedDescription
        }
    }
}
