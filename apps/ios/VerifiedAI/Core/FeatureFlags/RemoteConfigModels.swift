import Foundation

struct RemoteFlagEntryDTO: Codable, Sendable, Equatable {
    let flagKey: String
    let category: String
    let valueType: String
    let enabled: Bool
    let killSwitchActive: Bool
    let reason: String
}

struct RemoteExperimentEntryDTO: Codable, Sendable, Equatable {
    let experimentKey: String
    let experimentVersion: Int
    let variantKey: String
    let status: String
    let bucketValue: Int
    let reason: String
}

struct RemoteConfigResponseDTO: Codable, Sendable, Equatable {
    let schemaVersion: String
    let etag: String
    let maxAgeSeconds: Int
    let flags: [RemoteFlagEntryDTO]
    let experiments: [RemoteExperimentEntryDTO]
    let killSwitches: [String]
    let evaluatedAt: Date
    let llmTokensUsed: Int
    let trainingEligible: Bool
}

enum RemoteConfigDegradedReason: String, Codable, Sendable, Equatable {
    case offline
    case serviceUnavailable
    case unauthorized
    case transportFailure
    case decodingFailure
}

struct RemoteConfigSnapshot: Codable, Sendable, Equatable {
    let userId: UUID
    let etag: String?
    let flagsByKey: [String: RemoteFlagEntryDTO]
    let experimentsByKey: [String: RemoteExperimentEntryDTO]
    let killSwitches: Set<String>
    let evaluatedAt: Date
    let cachedAt: Date

    func isEnabled(_ flag: FeatureFlag) -> Bool {
        if killSwitches.contains(flag.remoteKey) {
            return false
        }
        if let entry = flagsByKey[flag.remoteKey] {
            if entry.killSwitchActive {
                return false
            }
            return entry.enabled
        }
        return flag.defaultEnabled
    }

    func isKillSwitchActive(_ flag: FeatureFlag) -> Bool {
        killSwitches.contains(flag.remoteKey) || (flagsByKey[flag.remoteKey]?.killSwitchActive == true)
    }

    func experimentVariant(for experimentKey: String) -> String? {
        experimentsByKey[experimentKey]?.variantKey
    }

    static func from(
        response: RemoteConfigResponseDTO,
        etag: String?,
        userId: UUID,
        cachedAt: Date = Date()
    ) -> RemoteConfigSnapshot {
        var flagsMap: [String: RemoteFlagEntryDTO] = [:]
        for item in response.flags {
            flagsMap[item.flagKey] = item
        }
        var expMap: [String: RemoteExperimentEntryDTO] = [:]
        for item in response.experiments {
            expMap[item.experimentKey] = item
        }
        return RemoteConfigSnapshot(
            userId: userId,
            etag: etag ?? response.etag,
            flagsByKey: flagsMap,
            experimentsByKey: expMap,
            killSwitches: Set(response.killSwitches),
            evaluatedAt: response.evaluatedAt,
            cachedAt: cachedAt
        )
    }
}
