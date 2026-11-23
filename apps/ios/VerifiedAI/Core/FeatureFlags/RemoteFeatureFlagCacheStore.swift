import Foundation

/// Per-user file-protected, backup-excluded remote configuration cache store.
/// Never throws unhandled disk failures back into UI or learning flows.
actor RemoteFeatureFlagCacheStore {
    private let rootDirectory: URL

    init(baseDirectory: URL? = nil) {
        let base = baseDirectory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        self.rootDirectory = base.appendingPathComponent("RemoteConfigCache", isDirectory: true)
    }

    func cacheFileURL(for userId: UUID) -> URL {
        rootDirectory.appendingPathComponent("\(userId.uuidString.lowercased()).json", isDirectory: false)
    }

    func load(userId: UUID) -> RemoteConfigSnapshot? {
        let fileURL = cacheFileURL(for: userId)
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            return nil
        }
        do {
            let data = try Data(contentsOf: fileURL)
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let snapshot = try decoder.decode(RemoteConfigSnapshot.self, from: data)
            guard snapshot.userId == userId else {
                return nil
            }
            return snapshot
        } catch {
            return nil
        }
    }

    func save(userId: UUID, snapshot: RemoteConfigSnapshot) {
        do {
            var dirURL = rootDirectory
            try FileManager.default.createDirectory(at: dirURL, withIntermediateDirectories: true)
            var dirResourceValues = URLResourceValues()
            dirResourceValues.isExcludedFromBackup = true
            try? dirURL.setResourceValues(dirResourceValues)

            let encoder = JSONEncoder()
            encoder.dateEncodingStrategy = .iso8601
            let data = try encoder.encode(snapshot)

            var fileURL = cacheFileURL(for: userId)
            try data.write(to: fileURL, options: [.atomic, .completeFileProtection])
            var fileResourceValues = URLResourceValues()
            fileResourceValues.isExcludedFromBackup = true
            try? fileURL.setResourceValues(fileResourceValues)
        } catch {
            // Non-blocking: cache write failures degrade safely without interrupting user flows.
        }
    }

    func purge(userId: UUID) {
        try? FileManager.default.removeItem(at: cacheFileURL(for: userId))
    }

    func purgeAll() {
        try? FileManager.default.removeItem(at: rootDirectory)
    }
}
