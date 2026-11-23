import Foundation
import XCTest
@testable import VerifiedAI

private actor MockRemoteConfigAPI: RemoteConfigServicing {
    enum PlannedResult: Sendable {
        case outcome(RemoteConfigFetchOutcome)
        case failure(NetworkError)
    }

    private var queue: [PlannedResult]
    private(set) var recordedIfNoneMatches: [String?] = []

    init(planned: [PlannedResult]) {
        self.queue = planned
    }

    func enqueue(_ result: PlannedResult) {
        queue.append(result)
    }

    func fetchRemoteConfig(ifNoneMatch: String?) async throws -> RemoteConfigFetchOutcome {
        recordedIfNoneMatches.append(ifNoneMatch)
        let next = queue.isEmpty
            ? PlannedResult.failure(.httpStatus(503))
            : queue.removeFirst()
        switch next {
        case .outcome(let outcome):
            return outcome
        case .failure(let error):
            throw error
        }
    }
}

private final class OnlineFlagBox: @unchecked Sendable {
    private let lock = NSLock()
    private var online: Bool

    init(_ initial: Bool) {
        self.online = initial
    }

    func get() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return online
    }

    func set(_ value: Bool) {
        lock.lock()
        defer { lock.unlock() }
        online = value
    }
}

final class RemoteFeatureFlagClientTests: XCTestCase {
    private var tempDirectories: [URL] = []

    override func tearDown() {
        for url in tempDirectories {
            try? FileManager.default.removeItem(at: url)
        }
        tempDirectories.removeAll()
        super.tearDown()
    }

    private func makeTempDirectory() throws -> URL {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("RemoteFeatureFlagClientTests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        tempDirectories.append(dir)
        return dir
    }

    func testFailClosedDefaultsMatchSafeBaseline() async throws {
        XCTAssertFalse(FeatureFlag.platformHealthScreen.defaultEnabled)
        XCTAssertTrue(FeatureFlag.pdfUploadEnabled.defaultEnabled)
        XCTAssertFalse(FeatureFlag.mistakeMomentPaywallUI.defaultEnabled)

        XCTAssertEqual(FeatureFlag.platformHealthScreen.remoteKey, "client_platform_health_screen")
        XCTAssertEqual(FeatureFlag.pdfUploadEnabled.remoteKey, "pdf_upload_enabled")
        XCTAssertEqual(FeatureFlag.mistakeMomentPaywallUI.remoteKey, "client_mistake_moment_paywall_ui")

        let tempDir = try makeTempDirectory()
        let api = MockRemoteConfigAPI(planned: [])
        let store = await MainActor.run {
            RemoteFeatureFlagStore(
                service: api,
                cacheStore: RemoteFeatureFlagCacheStore(baseDirectory: tempDir)
            )
        }

        XCTAssertFalse(store.isEnabled(.platformHealthScreen))
        XCTAssertTrue(store.isEnabled(.pdfUploadEnabled))
        XCTAssertFalse(store.isEnabled(.mistakeMomentPaywallUI))
        XCTAssertFalse(store.isKillSwitchActive(.pdfUploadEnabled))
        XCTAssertNil(store.experimentVariant(for: "EXP-LOS-01"))

        let fixtureKilled = FixtureFeatureFlagClient(arguments: ["--kill-switch-pdf"])
        XCTAssertFalse(fixtureKilled.isEnabled(.pdfUploadEnabled))
        XCTAssertTrue(fixtureKilled.isKillSwitchActive(.pdfUploadEnabled))
    }

    func testDecodeCanonicalRemoteConfigFixtureAndApplyToStore() async throws {
        let fixtureJSON = """
        {
          "schemaVersion": "remote-config-v1",
          "etag": "\\"1f8a2b3c4d5e6f7a\\"",
          "maxAgeSeconds": 300,
          "flags": [
            {
              "flagKey": "client_mistake_moment_paywall_ui",
              "category": "EXPERIMENT_FLAG",
              "valueType": "BOOLEAN",
              "enabled": true,
              "killSwitchActive": false,
              "reason": "BUCKET_MATCH"
            },
            {
              "flagKey": "client_platform_health_screen",
              "category": "RELEASE_FLAG",
              "valueType": "BOOLEAN",
              "enabled": false,
              "killSwitchActive": false,
              "reason": "FLAG_DISABLED"
            },
            {
              "flagKey": "pdf_upload_enabled",
              "category": "KILL_SWITCH",
              "valueType": "BOOLEAN",
              "enabled": true,
              "killSwitchActive": false,
              "reason": "BUCKET_MATCH"
            }
          ],
          "experiments": [
            {
              "experimentKey": "EXP-LOS-01",
              "experimentVersion": 2,
              "variantKey": "treatment",
              "status": "RUNNING",
              "bucketValue": 1420,
              "reason": "BUCKET_MATCH"
            },
            {
              "experimentKey": "EXP-LOS-08",
              "experimentVersion": 1,
              "variantKey": "control_quota_wall",
              "status": "DRAFT",
              "bucketValue": 0,
              "reason": "FALLBACK_CONTROL"
            }
          ],
          "killSwitches": [],
          "evaluatedAt": "2026-11-22T18:30:00Z",
          "llmTokensUsed": 0,
          "trainingEligible": false
        }
        """

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let dto = try decoder.decode(RemoteConfigResponseDTO.self, from: Data(fixtureJSON.utf8))
        XCTAssertEqual(dto.schemaVersion, "remote-config-v1")
        XCTAssertEqual(dto.llmTokensUsed, 0)
        XCTAssertFalse(dto.trainingEligible)

        let tempDir = try makeTempDirectory()
        let userId = UUID()
        let sessionId = UUID()
        let api = MockRemoteConfigAPI(planned: [.outcome(.updated(dto, etag: dto.etag))])
        let store = await MainActor.run {
            RemoteFeatureFlagStore(
                service: api,
                cacheStore: RemoteFeatureFlagCacheStore(baseDirectory: tempDir)
            )
        }

        await store.bind(userId: userId, sessionId: sessionId)
        await store.refresh()

        let degraded = await MainActor.run { store.degradedReason }
        XCTAssertNil(degraded)
        XCTAssertTrue(store.isEnabled(.mistakeMomentPaywallUI))
        XCTAssertFalse(store.isEnabled(.platformHealthScreen))
        XCTAssertTrue(store.isEnabled(.pdfUploadEnabled))
        XCTAssertEqual(store.experimentVariant(for: "EXP-LOS-01"), "treatment")
        XCTAssertEqual(store.experimentVariant(for: "EXP-LOS-08"), "control_quota_wall")
    }

    func testConditionalEtagNotModifiedPreservesCachedSnapshot() async throws {
        let initialDTO = RemoteConfigResponseDTO(
            schemaVersion: "remote-config-v1",
            etag: "\"etag-v1\"",
            maxAgeSeconds: 300,
            flags: [
                RemoteFlagEntryDTO(
                    flagKey: "client_platform_health_screen",
                    category: "RELEASE_FLAG",
                    valueType: "BOOLEAN",
                    enabled: true,
                    killSwitchActive: false,
                    reason: "BUCKET_MATCH"
                )
            ],
            experiments: [
                RemoteExperimentEntryDTO(
                    experimentKey: "EXP-LOS-01",
                    experimentVersion: 1,
                    variantKey: "treatment",
                    status: "RUNNING",
                    bucketValue: 1200,
                    reason: "BUCKET_MATCH"
                )
            ],
            killSwitches: [],
            evaluatedAt: Date(timeIntervalSince1970: 1_795_000_000),
            llmTokensUsed: 0,
            trainingEligible: false
        )

        let tempDir = try makeTempDirectory()
        let userId = UUID()
        let sessionId = UUID()
        let api = MockRemoteConfigAPI(planned: [
            .outcome(.updated(initialDTO, etag: "\"etag-v1\"")),
            .outcome(.notModified(etag: "\"etag-v1\""))
        ])
        let store = await MainActor.run {
            RemoteFeatureFlagStore(
                service: api,
                cacheStore: RemoteFeatureFlagCacheStore(baseDirectory: tempDir)
            )
        }

        await store.bind(userId: userId, sessionId: sessionId)
        await store.refresh()
        XCTAssertTrue(store.isEnabled(.platformHealthScreen))
        XCTAssertEqual(store.experimentVariant(for: "EXP-LOS-01"), "treatment")

        await store.refresh()
        let sentEtags = await api.recordedIfNoneMatches
        XCTAssertEqual(sentEtags, [nil, "\"etag-v1\""])
        XCTAssertTrue(store.isEnabled(.platformHealthScreen))
        XCTAssertEqual(store.experimentVariant(for: "EXP-LOS-01"), "treatment")
        let degraded = await MainActor.run { store.degradedReason }
        XCTAssertNil(degraded)
    }

    func testKillSwitchOverridesEnabledFlagAndPersistsAcrossReload() async throws {
        // Even if the raw flag entry still carried enabled=true, killSwitches must force-disable it.
        let killSwitchDTO = RemoteConfigResponseDTO(
            schemaVersion: "remote-config-v1",
            etag: "\"etag-kill-pdf\"",
            maxAgeSeconds: 300,
            flags: [
                RemoteFlagEntryDTO(
                    flagKey: "pdf_upload_enabled",
                    category: "KILL_SWITCH",
                    valueType: "BOOLEAN",
                    enabled: true,
                    killSwitchActive: false,
                    reason: "KILL_SWITCH_ACTIVE"
                )
            ],
            experiments: [],
            killSwitches: ["pdf_upload_enabled"],
            evaluatedAt: Date(timeIntervalSince1970: 1_795_000_100),
            llmTokensUsed: 0,
            trainingEligible: false
        )

        let tempDir = try makeTempDirectory()
        let userId = UUID()
        let api1 = MockRemoteConfigAPI(planned: [.outcome(.updated(killSwitchDTO, etag: "\"etag-kill-pdf\""))])
        let store1 = await MainActor.run {
            RemoteFeatureFlagStore(
                service: api1,
                cacheStore: RemoteFeatureFlagCacheStore(baseDirectory: tempDir)
            )
        }

        await store1.bind(userId: userId, sessionId: UUID())
        await store1.refresh()

        XCTAssertFalse(store1.isEnabled(.pdfUploadEnabled))
        XCTAssertTrue(store1.isKillSwitchActive(.pdfUploadEnabled))

        // Simulate app relaunch while offline: persisted snapshot preserves kill switch immediately on bind.
        let api2 = MockRemoteConfigAPI(planned: [.failure(.httpStatus(503))])
        let store2 = await MainActor.run {
            RemoteFeatureFlagStore(
                service: api2,
                cacheStore: RemoteFeatureFlagCacheStore(baseDirectory: tempDir),
                isOnline: { false }
            )
        }
        await store2.bind(userId: userId, sessionId: UUID())
        XCTAssertFalse(store2.isEnabled(.pdfUploadEnabled))
        XCTAssertTrue(store2.isKillSwitchActive(.pdfUploadEnabled))
    }

    func testOfflineServiceUnavailableAndInvariantMismatchPreserveCachedConfig() async throws {
        let validDTO = RemoteConfigResponseDTO(
            schemaVersion: "remote-config-v1",
            etag: "\"etag-good\"",
            maxAgeSeconds: 300,
            flags: [
                RemoteFlagEntryDTO(
                    flagKey: "client_mistake_moment_paywall_ui",
                    category: "EXPERIMENT_FLAG",
                    valueType: "BOOLEAN",
                    enabled: true,
                    killSwitchActive: false,
                    reason: "BUCKET_MATCH"
                )
            ],
            experiments: [
                RemoteExperimentEntryDTO(
                    experimentKey: "EXP-LOS-09",
                    experimentVersion: 1,
                    variantKey: "treatment_three_plan",
                    status: "RUNNING",
                    bucketValue: 4100,
                    reason: "BUCKET_MATCH"
                )
            ],
            killSwitches: [],
            evaluatedAt: Date(timeIntervalSince1970: 1_795_000_200),
            llmTokensUsed: 0,
            trainingEligible: false
        )

        let unsafeDTO = RemoteConfigResponseDTO(
            schemaVersion: "remote-config-v1",
            etag: "\"etag-unsafe\"",
            maxAgeSeconds: 300,
            flags: [],
            experiments: [],
            killSwitches: ["pdf_upload_enabled"],
            evaluatedAt: Date(timeIntervalSince1970: 1_795_000_300),
            llmTokensUsed: 5,
            trainingEligible: true
        )

        let tempDir = try makeTempDirectory()
        let userId = UUID()
        let sessionId = UUID()
        let onlineBox = OnlineFlagBox(true)
        let api = MockRemoteConfigAPI(planned: [
            .outcome(.updated(validDTO, etag: "\"etag-good\"")),
            .failure(.httpStatus(503)),
            .outcome(.updated(unsafeDTO, etag: "\"etag-unsafe\""))
        ])
        let store = await MainActor.run {
            RemoteFeatureFlagStore(
                service: api,
                cacheStore: RemoteFeatureFlagCacheStore(baseDirectory: tempDir),
                isOnline: { onlineBox.get() }
            )
        }

        await store.bind(userId: userId, sessionId: sessionId)
        await store.refresh()
        XCTAssertTrue(store.isEnabled(.mistakeMomentPaywallUI))
        XCTAssertEqual(store.experimentVariant(for: "EXP-LOS-09"), "treatment_three_plan")

        // Offline preserves cached values cleanly without error crash.
        onlineBox.set(false)
        await store.refresh()
        let offlineReason = await MainActor.run { store.degradedReason }
        XCTAssertEqual(offlineReason, .offline)
        XCTAssertTrue(store.isEnabled(.mistakeMomentPaywallUI))

        // HTTP 503 preserves cached values and surfaces serviceUnavailable.
        onlineBox.set(true)
        await store.refresh()
        let unavailableReason = await MainActor.run { store.degradedReason }
        XCTAssertEqual(unavailableReason, .serviceUnavailable)
        XCTAssertTrue(store.isEnabled(.mistakeMomentPaywallUI))
        XCTAssertEqual(store.experimentVariant(for: "EXP-LOS-09"), "treatment_three_plan")

        // Safety invariant mismatch (trainingEligible=true / llmTokensUsed>0) is rejected without overwriting safe cache.
        await store.refresh()
        let invariantReason = await MainActor.run { store.degradedReason }
        XCTAssertEqual(invariantReason, .decodingFailure)
        XCTAssertTrue(store.isEnabled(.mistakeMomentPaywallUI))
        XCTAssertFalse(store.isKillSwitchActive(.pdfUploadEnabled))
    }

    func testCacheStoreSetsBackupExclusionProtectionAndResetPurges() async throws {
        let tempDir = try makeTempDirectory()
        let cacheStore = RemoteFeatureFlagCacheStore(baseDirectory: tempDir)
        let userId = UUID()

        let dto = RemoteConfigResponseDTO(
            schemaVersion: "remote-config-v1",
            etag: "\"etag-file\"",
            maxAgeSeconds: 300,
            flags: [
                RemoteFlagEntryDTO(
                    flagKey: "client_platform_health_screen",
                    category: "RELEASE_FLAG",
                    valueType: "BOOLEAN",
                    enabled: true,
                    killSwitchActive: false,
                    reason: "BUCKET_MATCH"
                )
            ],
            experiments: [],
            killSwitches: [],
            evaluatedAt: Date(timeIntervalSince1970: 1_795_000_400),
            llmTokensUsed: 0,
            trainingEligible: false
        )

        let api = MockRemoteConfigAPI(planned: [.outcome(.updated(dto, etag: "\"etag-file\""))])
        let store = await MainActor.run {
            RemoteFeatureFlagStore(service: api, cacheStore: cacheStore)
        }

        await store.bind(userId: userId, sessionId: UUID())
        await store.refresh()

        let fileURL = await cacheStore.cacheFileURL(for: userId)
        XCTAssertTrue(FileManager.default.fileExists(atPath: fileURL.path))
        let resourceValues = try fileURL.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(resourceValues.isExcludedFromBackup, true)
        let reloaded = await cacheStore.load(userId: userId)
        XCTAssertEqual(reloaded?.isEnabled(.platformHealthScreen), true)

        await store.reset(purgeAll: true)
        XCTAssertFalse(FileManager.default.fileExists(atPath: fileURL.path))
        XCTAssertFalse(store.isEnabled(.platformHealthScreen))
        let postResetSnapshot = await MainActor.run { store.currentSnapshot }
        XCTAssertNil(postResetSnapshot)
    }
}
