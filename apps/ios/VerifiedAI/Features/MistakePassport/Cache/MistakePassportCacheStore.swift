// apps/ios/VerifiedAI/Features/MistakePassport/Cache/MistakePassportCacheStore.swift
import Foundation

public protocol MistakePassportCacheStoreProtocol: Sendable {
    func savePassportSnapshot(_ snapshot: MistakePassportResponseDto, statusFilter: TrapLifecycleStatus?) async
    func loadCachedPassportSnapshot(statusFilter: TrapLifecycleStatus?) async -> MistakePassportResponseDto?
    func purgeAllCachedPassports() async
}

public actor MistakePassportCacheStore: MistakePassportCacheStoreProtocol {
    private var inMemoryCache: [String: MistakePassportResponseDto] = [:]

    public init() {}

    private func cacheKey(for statusFilter: TrapLifecycleStatus?) -> String {
        statusFilter?.rawValue ?? "ALL"
    }

    public func savePassportSnapshot(_ snapshot: MistakePassportResponseDto, statusFilter: TrapLifecycleStatus?) async {
        let key = cacheKey(for: statusFilter)
        inMemoryCache[key] = snapshot
    }

    public func loadCachedPassportSnapshot(statusFilter: TrapLifecycleStatus?) async -> MistakePassportResponseDto? {
        let key = cacheKey(for: statusFilter)
        return inMemoryCache[key]
    }

    public func purgeAllCachedPassports() async {
        inMemoryCache.removeAll()
    }
}
