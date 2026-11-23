import Foundation

enum RemoteConfigFetchOutcome: Sendable, Equatable {
    case updated(RemoteConfigResponseDTO, etag: String?)
    case notModified(etag: String?)
}

protocol RemoteConfigServicing: Sendable {
    func fetchRemoteConfig(ifNoneMatch: String?) async throws -> RemoteConfigFetchOutcome
}

final class HTTPRemoteConfigAPI: RemoteConfigServicing, @unchecked Sendable {
    private let apiClient: APIClient

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func fetchRemoteConfig(ifNoneMatch: String?) async throws -> RemoteConfigFetchOutcome {
        let request = HTTPRequest<RemoteConfigResponseDTO>(
            endpoint: Endpoint(path: "/api/v1/config/remote", method: .get),
            decodesISO8601Dates: true,
            ifNoneMatch: ifNoneMatch
        )
        let response = try await apiClient.sendConditional(request)
        switch response {
        case .modified(let dto, let headerEtag):
            return .updated(dto, etag: headerEtag ?? dto.etag)
        case .notModified(let headerEtag):
            return .notModified(etag: headerEtag ?? ifNoneMatch)
        }
    }
}

#if DEBUG
struct FixtureRemoteConfigAPI: RemoteConfigServicing, Sendable {
    let killPdfUpload: Bool

    init(killPdfUpload: Bool = false) {
        self.killPdfUpload = killPdfUpload
    }

    func fetchRemoteConfig(ifNoneMatch: String?) async throws -> RemoteConfigFetchOutcome {
        let etag = killPdfUpload ? "\"fixture-kill-pdf-v1\"" : "\"fixture-default-v1\""
        if let ifNoneMatch, ifNoneMatch == etag {
            return .notModified(etag: etag)
        }
        let dto = RemoteConfigResponseDTO(
            schemaVersion: "remote-config-v1",
            etag: etag,
            maxAgeSeconds: 300,
            flags: [
                RemoteFlagEntryDTO(
                    flagKey: "client_mistake_moment_paywall_ui",
                    category: "EXPERIMENT_FLAG",
                    valueType: "BOOLEAN",
                    enabled: false,
                    killSwitchActive: false,
                    reason: "FLAG_DISABLED"
                ),
                RemoteFlagEntryDTO(
                    flagKey: "client_platform_health_screen",
                    category: "RELEASE_FLAG",
                    valueType: "BOOLEAN",
                    enabled: false,
                    killSwitchActive: false,
                    reason: "FLAG_DISABLED"
                ),
                RemoteFlagEntryDTO(
                    flagKey: "pdf_upload_enabled",
                    category: "KILL_SWITCH",
                    valueType: "BOOLEAN",
                    enabled: !killPdfUpload,
                    killSwitchActive: killPdfUpload,
                    reason: killPdfUpload ? "KILL_SWITCH_ACTIVE" : "BUCKET_MATCH"
                )
            ],
            experiments: [
                RemoteExperimentEntryDTO(
                    experimentKey: "EXP-LOS-01",
                    experimentVersion: 1,
                    variantKey: "control",
                    status: "DRAFT",
                    bucketValue: 0,
                    reason: "FALLBACK_CONTROL"
                ),
                RemoteExperimentEntryDTO(
                    experimentKey: "EXP-LOS-08",
                    experimentVersion: 1,
                    variantKey: "control_quota_wall",
                    status: "DRAFT",
                    bucketValue: 0,
                    reason: "FALLBACK_CONTROL"
                ),
                RemoteExperimentEntryDTO(
                    experimentKey: "EXP-LOS-09",
                    experimentVersion: 1,
                    variantKey: "control_two_plan",
                    status: "DRAFT",
                    bucketValue: 0,
                    reason: "FALLBACK_CONTROL"
                )
            ],
            killSwitches: killPdfUpload ? ["pdf_upload_enabled"] : [],
            evaluatedAt: Date(),
            llmTokensUsed: 0,
            trainingEligible: false
        )
        return .updated(dto, etag: etag)
    }
}
#endif
