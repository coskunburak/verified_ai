import CryptoKit
import Foundation
import Security

public struct ExamIntegrityProofResponseDTO: Sendable, Codable, Equatable {
    public let schemaVersion: String
    public let sessionId: UUID
    public let userId: UUID
    public let integrityProtocolVersion: String
    public let keyId: String
    public let sessionProofKeyHex: String?
    public let issuedAt: String
    public let expiresAt: String
    public let integrityGuardEnabled: Bool
    public let llmTokensUsed: Int
    public let trainingEligible: Bool

    public init(
        schemaVersion: String = "exam-integrity-proof-response-v1",
        sessionId: UUID,
        userId: UUID,
        integrityProtocolVersion: String = "exam-submit-hmac-v1",
        keyId: String,
        sessionProofKeyHex: String?,
        issuedAt: String,
        expiresAt: String,
        integrityGuardEnabled: Bool,
        llmTokensUsed: Int = 0,
        trainingEligible: Bool = false
    ) {
        self.schemaVersion = schemaVersion
        self.sessionId = sessionId
        self.userId = userId
        self.integrityProtocolVersion = integrityProtocolVersion
        self.keyId = keyId
        self.sessionProofKeyHex = sessionProofKeyHex
        self.issuedAt = issuedAt
        self.expiresAt = expiresAt
        self.integrityGuardEnabled = integrityGuardEnabled
        self.llmTokensUsed = llmTokensUsed
        self.trainingEligible = trainingEligible
    }
}

public enum ExamSubmitIntentState: String, Sendable, Codable, Equatable {
    case draft = "DRAFT"
    case pendingUpload = "PENDING_UPLOAD"
    case inFlight = "IN_FLIGHT"
    case pendingReconciliation = "PENDING_RECONCILIATION"
    case retryableFailure = "RETRYABLE_FAILURE"
    case acknowledged = "ACKNOWLEDGED"
    case rejected = "REJECTED"
    case expired = "EXPIRED"
}

public struct ExamPendingSubmitIntent: Sendable, Codable, Equatable, Identifiable {
    public let id: UUID
    public let userId: UUID
    public let sessionId: UUID
    public let assemblyId: UUID
    public let originalIdempotencyKey: String
    public let submissionReason: ExamSessionSubmissionReason
    public let canonicalPayloadVersion: String
    public let immutableCanonicalBody: String
    public let canonicalTimestamp: String
    public let nonce: String
    public var keyId: String?
    public var signature: String?
    public let createdAt: String
    public var lastAttemptAt: String
    public var retryCount: Int
    public var state: ExamSubmitIntentState

    public init(
        id: UUID = UUID(),
        userId: UUID,
        sessionId: UUID,
        assemblyId: UUID,
        originalIdempotencyKey: String,
        submissionReason: ExamSessionSubmissionReason,
        canonicalPayloadVersion: String = ExamIntegrityCanonicalizer.protocolV1,
        immutableCanonicalBody: String,
        canonicalTimestamp: String,
        nonce: String,
        keyId: String? = nil,
        signature: String? = nil,
        createdAt: String,
        lastAttemptAt: String,
        retryCount: Int = 0,
        state: ExamSubmitIntentState = .pendingUpload
    ) {
        self.id = id
        self.userId = userId
        self.sessionId = sessionId
        self.assemblyId = assemblyId
        self.originalIdempotencyKey = originalIdempotencyKey
        self.submissionReason = submissionReason
        self.canonicalPayloadVersion = canonicalPayloadVersion
        self.immutableCanonicalBody = immutableCanonicalBody
        self.canonicalTimestamp = canonicalTimestamp
        self.nonce = nonce
        self.keyId = keyId
        self.signature = signature
        self.createdAt = createdAt
        self.lastAttemptAt = lastAttemptAt
        self.retryCount = retryCount
        self.state = state
    }
}

public protocol ExamProofKeyStoring: Sendable {
    func proofKeyHex(userId: UUID, sessionId: UUID) throws -> String?
    func saveProofKeyHex(_ hex: String, userId: UUID, sessionId: UUID) throws
    func removeProofKey(userId: UUID, sessionId: UUID) throws
    func removeAllProofKeys(userId: UUID) throws
}

public final class KeychainExamProofKeyStore: ExamProofKeyStoring, @unchecked Sendable {
    private let keychain: KeychainStore
    private let lock = NSLock()

    public init(service: String = "com.verifiedai.exam.integrity") {
        self.keychain = KeychainStore(service: service)
    }

    private static func proofKeyIdentifier(userId: UUID, sessionId: UUID) -> String {
        "exam.integrity.proof.\(userId.uuidString.lowercased()).\(sessionId.uuidString.lowercased())"
    }

    private static func userIndexKey(userId: UUID) -> String {
        "exam.integrity.index.\(userId.uuidString.lowercased())"
    }

    public func proofKeyHex(userId: UUID, sessionId: UUID) throws -> String? {
        lock.lock()
        defer { lock.unlock() }
        return try keychain.string(forKey: Self.proofKeyIdentifier(userId: userId, sessionId: sessionId))
    }

    public func saveProofKeyHex(_ hex: String, userId: UUID, sessionId: UUID) throws {
        lock.lock()
        defer { lock.unlock() }
        let key = Self.proofKeyIdentifier(userId: userId, sessionId: sessionId)
        try keychain.setString(hex, forKey: key)

        let indexKey = Self.userIndexKey(userId: userId)
        let sessionLower = sessionId.uuidString.lowercased()
        let existing = (try keychain.string(forKey: indexKey))?
            .split(separator: ",")
            .map(String.init) ?? []
        if !existing.contains(sessionLower) {
            let updated = (existing + [sessionLower]).joined(separator: ",")
            try keychain.setString(updated, forKey: indexKey)
        }
    }

    public func removeProofKey(userId: UUID, sessionId: UUID) throws {
        lock.lock()
        defer { lock.unlock() }
        try keychain.removeValue(forKey: Self.proofKeyIdentifier(userId: userId, sessionId: sessionId))
    }

    public func removeAllProofKeys(userId: UUID) throws {
        lock.lock()
        defer { lock.unlock() }
        let indexKey = Self.userIndexKey(userId: userId)
        if let raw = try keychain.string(forKey: indexKey) {
            for sidStr in raw.split(separator: ",") {
                if let sid = UUID(uuidString: String(sidStr)) {
                    try keychain.removeValue(forKey: Self.proofKeyIdentifier(userId: userId, sessionId: sid))
                }
            }
        }
        try keychain.removeValue(forKey: indexKey)
    }
}

public final class InMemoryExamProofKeyStore: ExamProofKeyStoring, @unchecked Sendable {
    private let lock = NSLock()
    private var entries: [String: String] = [:]

    public init() {}

    public func proofKeyHex(userId: UUID, sessionId: UUID) throws -> String? {
        lock.lock()
        defer { lock.unlock() }
        return entries[Self.storageKey(userId: userId, sessionId: sessionId)]
    }

    public func saveProofKeyHex(_ hex: String, userId: UUID, sessionId: UUID) throws {
        lock.lock()
        defer { lock.unlock() }
        entries[Self.storageKey(userId: userId, sessionId: sessionId)] = hex
    }

    public func removeProofKey(userId: UUID, sessionId: UUID) throws {
        lock.lock()
        defer { lock.unlock() }
        entries.removeValue(forKey: Self.storageKey(userId: userId, sessionId: sessionId))
    }

    public func removeAllProofKeys(userId: UUID) throws {
        lock.lock()
        defer { lock.unlock() }
        let prefix = "\(userId.uuidString.lowercased())::"
        entries.keys.filter { $0.hasPrefix(prefix) }.forEach { entries.removeValue(forKey: $0) }
    }

    private static func storageKey(userId: UUID, sessionId: UUID) -> String {
        "\(userId.uuidString.lowercased())::\(sessionId.uuidString.lowercased())"
    }
}

public enum ExamIntegritySigningError: Error, Equatable, Sendable {
    case invalidKeyHex
    case invalidInput(String)
}

public enum ExamIntegrityCanonicalizer {
    public static let protocolV1 = "exam-submit-hmac-v1"
    private static let hkdfSalt = Data("verifiedai-exam-integrity-salt-v1".utf8)

    public static func formatCanonicalInstant(_ date: Date) -> String {
        let totalMillis = Int64((date.timeIntervalSince1970 * 1000.0).rounded(.towardZero))
        let flooredSeconds = floor(Double(totalMillis) / 1000.0)
        let millis = ((totalMillis % 1000) + 1000) % 1000
        let wholeDate = Date(timeIntervalSince1970: flooredSeconds)

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let comps = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: wholeDate)
        return String(
            format: "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ",
            comps.year ?? 1970,
            comps.month ?? 1,
            comps.day ?? 1,
            comps.hour ?? 0,
            comps.minute ?? 0,
            comps.second ?? 0,
            Int(millis)
        )
    }

    public static func normalizeSubmitPath(sessionId: UUID) -> String {
        "/v1/exams/sessions/\(sessionId.uuidString.lowercased())/submit"
    }

    public static func buildCanonicalSubmitBody(
        submissionReason: ExamSessionSubmissionReason,
        canonicalTimestamp: String
    ) -> String {
        "{\"clientTimestamp\":\"\(canonicalTimestamp)\",\"submissionReason\":\"\(submissionReason.rawValue)\",\"trainingEligible\":false}"
    }

    public static func computeCanonicalBodySha256Hex(
        submissionReason: ExamSessionSubmissionReason,
        canonicalTimestamp: String
    ) -> String {
        let body = buildCanonicalSubmitBody(
            submissionReason: submissionReason,
            canonicalTimestamp: canonicalTimestamp
        )
        return sha256Hex(Data(body.utf8))
    }

    public static func buildCanonicalSigningString(
        userId: UUID,
        sessionId: UUID,
        submissionReason: ExamSessionSubmissionReason,
        idempotencyKey: String,
        canonicalTimestamp: String,
        integrityKeyId: String,
        submissionNonce: String
    ) throws -> String {
        let cleanIdemp = idempotencyKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanKeyId = integrityKeyId.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanNonce = submissionNonce.trimmingCharacters(in: .whitespacesAndNewlines)

        guard cleanIdemp.count >= 3 && cleanIdemp.count <= 128 else {
            throw ExamIntegritySigningError.invalidInput("idempotencyKey length out of bounds")
        }
        guard !cleanKeyId.isEmpty && cleanKeyId.count <= 64 else {
            throw ExamIntegritySigningError.invalidInput("integrityKeyId length out of bounds")
        }
        guard cleanNonce.count >= 16 && cleanNonce.count <= 128 else {
            throw ExamIntegritySigningError.invalidInput("submissionNonce length out of bounds")
        }

        let bodyHash = computeCanonicalBodySha256Hex(
            submissionReason: submissionReason,
            canonicalTimestamp: canonicalTimestamp
        )

        return [
            protocolV1,
            "POST",
            normalizeSubmitPath(sessionId: sessionId),
            userId.uuidString.lowercased(),
            sessionId.uuidString.lowercased(),
            submissionReason.rawValue,
            cleanIdemp,
            canonicalTimestamp,
            cleanKeyId,
            cleanNonce,
            bodyHash
        ].joined(separator: "\n") + "\n"
    }

    public static func deriveSessionProofKeyHex(
        masterKeyData: Data,
        keyId: String,
        userId: UUID,
        sessionId: UUID,
        startedAtTimestamp: String
    ) -> String {
        let info = "exam-integrity-v1|\(keyId.trimmingCharacters(in: .whitespacesAndNewlines))|\(userId.uuidString.lowercased())|\(sessionId.uuidString.lowercased())|\(startedAtTimestamp)"
        let derived = HKDF<SHA256>.deriveKey(
            inputKeyMaterial: SymmetricKey(data: masterKeyData),
            salt: hkdfSalt,
            info: Data(info.utf8),
            outputByteCount: 32
        )
        return derived.withUnsafeBytes { bytes in
            bytes.map { String(format: "%02x", $0) }.joined()
        }
    }

    public static func signCanonicalString(_ canonical: String, proofKeyHex: String) throws -> String {
        guard let keyData = decodeHex(proofKeyHex), keyData.count == 32 else {
            throw ExamIntegritySigningError.invalidKeyHex
        }
        let mac = HMAC<SHA256>.authenticationCode(
            for: Data(canonical.utf8),
            using: SymmetricKey(data: keyData)
        )
        return mac.map { String(format: "%02x", $0) }.joined()
    }

    public static func generateNonce(byteCount: Int = 16) -> String {
        var bytes = [UInt8](repeating: 0, count: max(16, byteCount))
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        if status != errSecSuccess {
            return UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
        }
        return Data(bytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    public static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    public static func decodeHex(_ hex: String) -> Data? {
        let clean = hex.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard clean.count % 2 == 0, !clean.isEmpty else { return nil }
        var data = Data(capacity: clean.count / 2)
        var idx = clean.startIndex
        while idx < clean.endIndex {
            let nextIdx = clean.index(idx, offsetBy: 2)
            guard let byte = UInt8(clean[idx..<nextIdx], radix: 16) else {
                return nil
            }
            data.append(byte)
            idx = nextIdx
        }
        return data
    }
}
