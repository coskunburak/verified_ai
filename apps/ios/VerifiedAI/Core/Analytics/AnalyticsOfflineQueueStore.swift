import Foundation

/// Bounded, file-protected, backup-excluded per-user offline analytics queue actor.
/// Never throws unhandled disk failures back into UI or learning flows.
public actor AnalyticsOfflineQueueStore {

    public static let maxQueuedEventsPerUser = 200

    private let rootDirectory: URL

    public init(baseDirectory: URL? = nil) {
        if let baseDirectory {
            self.rootDirectory = baseDirectory.appendingPathComponent("AnalyticsQueue", isDirectory: true)
        } else {
            let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            self.rootDirectory = appSupport.appendingPathComponent("AnalyticsQueue", isDirectory: true)
        }
    }

    public func queueFileURL(for userId: UUID) -> URL {
        userDirectoryURL(for: userId).appendingPathComponent("queue.json", isDirectory: false)
    }

    @discardableResult
    public func enqueue(_ event: AnalyticsEventIngestRequestDTO, for userId: UUID) -> Int {
        var existing = loadEvents(for: userId)
        if !existing.contains(where: { $0.clientEventId == event.clientEventId }) {
            existing.append(event)
        }
        if existing.count > Self.maxQueuedEventsPerUser {
            existing.removeFirst(existing.count - Self.maxQueuedEventsPerUser)
        }
        saveEvents(existing, for: userId)
        return existing.count
    }

    public func peekBatch(for userId: UUID, limit: Int = 50) -> [AnalyticsEventIngestRequestDTO] {
        let bounded = max(1, min(limit, 50))
        return Array(loadEvents(for: userId).prefix(bounded))
    }

    public func remove(clientEventIds: Set<String>, for userId: UUID) {
        guard !clientEventIds.isEmpty else { return }
        let existing = loadEvents(for: userId)
        let remaining = existing.filter { !clientEventIds.contains($0.clientEventId) }
        if remaining.isEmpty {
            try? FileManager.default.removeItem(at: queueFileURL(for: userId))
        } else {
            saveEvents(remaining, for: userId)
        }
    }

    public func count(for userId: UUID) -> Int {
        loadEvents(for: userId).count
    }

    public func clear(userId: UUID) {
        try? FileManager.default.removeItem(at: userDirectoryURL(for: userId))
    }

    public func clearAll() {
        try? FileManager.default.removeItem(at: rootDirectory)
    }

    private func userDirectoryURL(for userId: UUID) -> URL {
        rootDirectory.appendingPathComponent(userId.uuidString.lowercased(), isDirectory: true)
    }

    private func loadEvents(for userId: UUID) -> [AnalyticsEventIngestRequestDTO] {
        let fileURL = queueFileURL(for: userId)
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            return []
        }
        do {
            let data = try Data(contentsOf: fileURL)
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            return try decoder.decode([AnalyticsEventIngestRequestDTO].self, from: data)
        } catch {
            return []
        }
    }

    private func saveEvents(_ events: [AnalyticsEventIngestRequestDTO], for userId: UUID) {
        do {
            var dirURL = userDirectoryURL(for: userId)
            try FileManager.default.createDirectory(at: dirURL, withIntermediateDirectories: true)
            var dirResourceValues = URLResourceValues()
            dirResourceValues.isExcludedFromBackup = true
            try? dirURL.setResourceValues(dirResourceValues)

            let encoder = JSONEncoder()
            encoder.dateEncodingStrategy = .iso8601
            let data = try encoder.encode(events)

            var fileURL = queueFileURL(for: userId)
            try data.write(to: fileURL, options: [.atomic, .completeFileProtection])
            var fileResourceValues = URLResourceValues()
            fileResourceValues.isExcludedFromBackup = true
            try? fileURL.setResourceValues(fileResourceValues)
        } catch {
            // Non-blocking: disk write failures must never interrupt learning or app flows.
        }
    }
}
