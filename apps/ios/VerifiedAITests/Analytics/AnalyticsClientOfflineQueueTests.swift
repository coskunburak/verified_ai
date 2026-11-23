import Foundation
import XCTest
@testable import VerifiedAI

final class AnalyticsClientOfflineQueueTests: XCTestCase {

    private func makeTempRoot() throws -> URL {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("AnalyticsQueueTests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    func testOfflinePersistenceBackupExclusionBoundedCapAndReinitRecovery() async throws {
        let tempRoot = try makeTempRoot()
        defer { try? FileManager.default.removeItem(at: tempRoot) }

        let userId = UUID()
        let sessionId = UUID()
        let transport = StubAnalyticsBatchTransport()
        let store1 = AnalyticsOfflineQueueStore(baseDirectory: tempRoot)
        let client1 = AnalyticsClient(
            transport: transport,
            store: store1,
            isOnline: { false }
        )
        await client1.bind(userId: userId, sessionId: sessionId)

        // Enqueue 210 events offline -> capped at 200 (drops oldest 10)
        for _ in 0..<210 {
            await client1.track(.problemCaptureStarted(locale: .en))
        }
        let persistedCount = await store1.count(for: userId)
        XCTAssertEqual(persistedCount, 200)

        let queueURL = await store1.queueFileURL(for: userId)
        XCTAssertTrue(FileManager.default.fileExists(atPath: queueURL.path))
        let resourceValues = try queueURL.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(resourceValues.isExcludedFromBackup, true)

        // Re-initialize store & client -> recovers persisted events and flushes when online
        let store2 = AnalyticsOfflineQueueStore(baseDirectory: tempRoot)
        let recoveredCount = await store2.count(for: userId)
        XCTAssertEqual(recoveredCount, 200)

        let client2 = AnalyticsClient(
            transport: transport,
            store: store2,
            isOnline: { true }
        )
        await client2.bind(userId: userId, sessionId: sessionId)
        await client2.flush(ignoreBackoff: true)

        let sentCountAfterFirstFlush = await transport.totalSentEvents
        XCTAssertEqual(sentCountAfterFirstFlush, 50)
        let remainingAfterFirstFlush = await store2.count(for: userId)
        XCTAssertEqual(remainingAfterFirstFlush, 150)
    }

    func testAutoFlushAtThresholdConcurrentTrackingAndResetPurge() async throws {
        let tempRoot = try makeTempRoot()
        defer { try? FileManager.default.removeItem(at: tempRoot) }

        let userId = UUID()
        let sessionId = UUID()
        let transport = StubAnalyticsBatchTransport()
        let store = AnalyticsOfflineQueueStore(baseDirectory: tempRoot)
        let client = AnalyticsClient(
            transport: transport,
            store: store,
            isOnline: { true }
        )
        await client.bind(userId: userId, sessionId: sessionId)

        // Concurrent TaskGroup tracking 12 events -> triggers auto-flush at >= 10 without data races
        await withTaskGroup(of: Void.self) { group in
            for _ in 0..<12 {
                group.addTask {
                    await client.track(.verificationDetailsViewed(verificationStatus: .verified, entitlementTier: .pro))
                }
            }
        }
        await client.flush(ignoreBackoff: true)

        let sent = await transport.totalSentEvents
        XCTAssertEqual(sent, 12)
        let countAfterFlush = await store.count(for: userId)
        XCTAssertEqual(countAfterFlush, 0)

        // Queue offline, then reset() purges local storage completely
        let offlineClient = AnalyticsClient(
            transport: transport,
            store: store,
            isOnline: { false }
        )
        await offlineClient.bind(userId: userId, sessionId: sessionId)
        await offlineClient.track(.weeklyDigestViewed(weekIso: "2026-W47"))
        let beforeReset = await store.count(for: userId)
        XCTAssertEqual(beforeReset, 1)

        await offlineClient.reset(purgeAll: true)
        let afterReset = await store.count(for: userId)
        XCTAssertEqual(afterReset, 0)
    }

    func testPreservesQueueWithBackoffOn503AndDropsPermanentlyOn422WithoutRetryStorm() async throws {
        let tempRoot = try makeTempRoot()
        defer { try? FileManager.default.removeItem(at: tempRoot) }

        let userId = UUID()
        let sessionId = UUID()
        let clock = MutableClock(now: Date(timeIntervalSince1970: 1_795_424_000))
        let transport = StubAnalyticsBatchTransport()
        await transport.setFailure(statusCode: 503, code: "ANALYTICS_FEATURE_DISABLED")

        let store = AnalyticsOfflineQueueStore(baseDirectory: tempRoot)
        let client = AnalyticsClient(
            transport: transport,
            store: store,
            isOnline: { true },
            nowProvider: { clock.current() }
        )
        await client.bind(userId: userId, sessionId: sessionId)
        await client.track(.onboardingStarted(authMethod: .apple, locale: .en))

        await client.flush(ignoreBackoff: false)
        let attemptsAfter503 = await transport.attemptCount
        let countAfter503 = await store.count(for: userId)
        XCTAssertEqual(attemptsAfter503, 1)
        XCTAssertEqual(countAfter503, 1)

        // Immediate retry is suppressed by exponential backoff
        await client.flush(ignoreBackoff: false)
        let attemptsDuringBackoff = await transport.attemptCount
        XCTAssertEqual(attemptsDuringBackoff, 1)

        // Advance clock past 5s backoff and simulate permanent 422 rejection -> discards without retry storm
        clock.advance(by: 10)
        await transport.setFailure(statusCode: 422, code: "ANALYTICS_EVENT_VALIDATION_FAILED")
        await client.flush(ignoreBackoff: false)

        let attemptsAfter422 = await transport.attemptCount
        let countAfter422 = await store.count(for: userId)
        XCTAssertEqual(attemptsAfter422, 2)
        XCTAssertEqual(countAfter422, 0)

        // Non-blocking resilience when storage root is an unwriteable regular file
        let badFileRoot = tempRoot.appendingPathComponent("not-a-directory", isDirectory: false)
        _ = FileManager.default.createFile(atPath: badFileRoot.path, contents: Data("x".utf8))
        let resilientStore = AnalyticsOfflineQueueStore(baseDirectory: badFileRoot)
        let resilientClient = AnalyticsClient(transport: transport, store: resilientStore, isOnline: { true })
        await resilientClient.bind(userId: userId, sessionId: sessionId)
        await resilientClient.track(.problemCaptureStarted())
        await resilientClient.flush(ignoreBackoff: true)
    }
}

private final class MutableClock: @unchecked Sendable {
    private let lock = NSLock()
    private var now: Date

    init(now: Date) {
        self.now = now
    }

    func current() -> Date {
        lock.lock()
        defer { lock.unlock() }
        return now
    }

    func advance(by seconds: TimeInterval) {
        lock.lock()
        defer { lock.unlock() }
        now = now.addingTimeInterval(seconds)
    }
}

private actor StubAnalyticsBatchTransport: AnalyticsBatchTransport {
    private var forcedFailure: (Int, String)?
    private(set) var attemptCount = 0
    private(set) var totalSentEvents = 0

    func setFailure(statusCode: Int?, code: String?) {
        if let statusCode, let code {
            forcedFailure = (statusCode, code)
        } else {
            forcedFailure = nil
        }
    }

    func sendBatch(_ request: AnalyticsEventBatchRequestDTO) async throws -> AnalyticsEventIngestResponseDTO {
        attemptCount += 1
        if let (status, code) = forcedFailure {
            throw NetworkError.server(
                problem: ProblemDetails(
                    type: "https://errors.verified-ai-learning.example/test",
                    title: "Forced failure",
                    status: status,
                    code: code,
                    traceId: "trace-test",
                    details: nil
                )
            )
        }
        totalSentEvents += request.events.count
        let now = Date()
        return AnalyticsEventIngestResponseDTO(
            schemaVersion: "analytics-event-ingest-response-v1",
            acceptedCount: request.events.count,
            replayedCount: 0,
            rejectedCount: 0,
            results: request.events.map {
                AnalyticsEventIngestResultItemDTO(
                    clientEventId: $0.clientEventId,
                    eventName: $0.eventName,
                    status: "ACCEPTED",
                    eventId: UUID(),
                    errorCode: nil,
                    processedAt: now
                )
            },
            llmTokensUsed: 0,
            trainingEligible: false,
            processedAt: now
        )
    }

    func fetchCatalog() async throws -> AnalyticsEventCatalogResponseDTO {
        AnalyticsEventCatalogResponseDTO(
            schemaVersion: "analytics-event-catalog-v1",
            catalogVersion: "1.0.0",
            eventCount: 0,
            events: [],
            llmTokensUsed: 0,
            trainingEligible: false,
            generatedAt: Date()
        )
    }
}
