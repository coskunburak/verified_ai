import CryptoKit
import Foundation
#if canImport(StoreKit) && canImport(UIKit)
import StoreKit
import UIKit
#endif

struct StoreKitReviewPrompter: StoreReviewPrompting {
    @MainActor
    func requestReview() {
        #if canImport(StoreKit) && canImport(UIKit)
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive }) else {
            return
        }
        AppStore.requestReview(in: scene)
        #endif
    }
}

final class StubStoreReviewPrompter: StoreReviewPrompting, @unchecked Sendable {
    private let lock = NSLock()
    private var _requestCount = 0

    var requestCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return _requestCount
    }

    @MainActor
    func requestReview() {
        lock.lock()
        _requestCount += 1
        lock.unlock()
    }
}

/// Protected, backup-excluded installation salt hashed with SHA-256 (never IDFV/IDFA/IMEI).
actor GrowthDeviceFingerprintStore {
    private let fileURL: URL

    init(baseDirectory: URL? = nil) {
        let root = baseDirectory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        self.fileURL = root
            .appendingPathComponent("Growth", isDirectory: true)
            .appendingPathComponent("installation_salt.txt", isDirectory: false)
    }

    func hashedFingerprint() -> String {
        let salt = loadOrCreateSalt()
        let digest = SHA256.hash(data: Data(salt.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    func purge() {
        try? FileManager.default.removeItem(at: fileURL.deletingLastPathComponent())
    }

    private func loadOrCreateSalt() -> String {
        if let existing = try? String(contentsOf: fileURL, encoding: .utf8), !existing.isEmpty {
            return existing
        }
        let fresh = UUID().uuidString.lowercased()
        do {
            var dir = fileURL.deletingLastPathComponent()
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            var dirValues = URLResourceValues()
            dirValues.isExcludedFromBackup = true
            try? dir.setResourceValues(dirValues)

            var target = fileURL
            try Data(fresh.utf8).write(to: target, options: [.atomic, .completeFileProtection])
            var fileValues = URLResourceValues()
            fileValues.isExcludedFromBackup = true
            try? target.setResourceValues(fileValues)
        } catch {
            // Non-blocking: return ephemeral salt if file storage is temporarily unavailable.
        }
        return fresh
    }
}
