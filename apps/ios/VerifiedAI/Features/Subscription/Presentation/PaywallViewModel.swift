import Foundation
import Observation

@MainActor
@Observable
final class PaywallViewModel {
    private let billingAPI: AppleBillingServicing
    private let storeRepository: StoreProductRepository
    private let entitlementAPI: EntitlementServicing
    private let networkMonitor: NetworkMonitoring
    private let logger: AppLogger

    private var configuration: AppleBillingConfiguration?

    private(set) var state: PaywallState = .idle
    private(set) var products: [StoreProduct] = []
    private(set) var entitlement: Entitlement?
    private(set) var refreshedLearningAccess: LearningOsEntitlementV2?
    private(set) var message: String?
    var onServerEntitlementVerified: (@MainActor () async -> Void)?

    init(
        billingAPI: AppleBillingServicing,
        storeRepository: StoreProductRepository,
        entitlementAPI: EntitlementServicing,
        networkMonitor: NetworkMonitoring,
        logger: AppLogger
    ) {
        self.billingAPI = billingAPI
        self.storeRepository = storeRepository
        self.entitlementAPI = entitlementAPI
        self.networkMonitor = networkMonitor
        self.logger = logger
    }

    private var contextualLocale: String?

    func contextualAvailable(locale: String) async -> Bool {
        guard networkMonitor.isReachable else { return false }
        return (try? await eligibleOffers(locale: locale))?.isEmpty == false
    }

    func prepareContextual(locale: String) async -> Bool {
        guard !state.isBusy, networkMonitor.isReachable else { return false }
        state = .loadingProducts
        products = []
        contextualLocale = locale
        message = nil
        do {
            let offers = try await eligibleOffers(locale: locale)
            guard !offers.isEmpty else { state = .empty; return false }
            let config = try await billingAPI.configuration()
            guard config.purchaseAvailable else { state = .empty; return false }
            let productsFromStore = try await storeRepository.products(for: offers.map(\.productId))
            guard !Task.isCancelled, networkMonitor.isReachable else { state = .offline; return false }
            configuration = config
            products = productsFromStore.compactMap { product in
                let mappings = config.products.filter { $0.appStoreProductId == product.id }
                guard productsFromStore.filter({ $0.id == product.id }).count == 1,
                      mappings.count == 1, !product.displayPrice.isEmpty, product.supportedTerms,
                      let offer = offers.first(where: { offer in
                        offer.productId == product.id && offer.billingPeriod == product.periodCode
                            && mappings.first?.billingPeriod == offer.billingPeriod
                            && mappings.first?.entitlementTier == (offer.family == "PRO" ? .pro : .proPlus)
                    }) else { return nil }
                var presentation = product
                presentation.copyKey = offer.copyKey
                return presentation
            }
            if products.count < offers.count { message = "billing.priceUnavailable" }
            state = products.isEmpty ? .empty : .ready
            return true
        } catch {
            state = .offline
            return false
        }
    }

    private func eligibleOffers(locale: String) async throws -> [BillingOffers.Offer] {
        let feed = try await billingAPI.offers(locale: locale)
        guard feed.valid(for: locale) else { return [] }
        switch try await entitlementAPI.currentPresentationTier() {
        case "FREE": return feed.offers
        case "PRO": return feed.offers.filter { $0.family != "PRO" }
        default: return []
        }
    }

    func recordInteraction(_ kind: String) async {
        try? await billingAPI.interaction(kind)
    }

    func load(force: Bool = false) async {
        contextualLocale = nil
        if !force, case .ready = state {
            return
        }
        guard networkMonitor.isReachable else {
            state = .offline
            message = "Purchases are unavailable while offline."
            return
        }

        products = []
        state = .loadingProducts
        message = nil

        do {
            let configuration = try await billingAPI.configuration()
            self.configuration = configuration
            guard configuration.purchaseAvailable, !configuration.products.isEmpty else {
                products = []
                state = .empty
                return
            }

            let legacy = configuration.products.filter { $0.internalPlanId != "yks_season" }
            let storeProducts = try await storeRepository.products(for: legacy.map(\.appStoreProductId))
            products = storeProducts
            state = storeProducts.isEmpty ? .empty : .ready
            logger.info("paywall.products.ready")
        } catch {
            state = .failed("Subscriptions could not be loaded.")
            message = "Subscriptions could not be loaded."
            logger.warning("paywall.products.failed")
        }
    }

    func purchase(_ product: StoreProduct, allowed: @MainActor () -> Bool = { true }) async {
        guard networkMonitor.isReachable else {
            state = .offline
            message = "Purchases are unavailable while offline."
            return
        }

        switch state {
        case .ready, .cancelled, .failed: break
        default: return
        }
        guard allowed(), products.contains(product) else { return }
        state = .purchasing(product.id)
        do {
            if let locale = contextualLocale {
                let offers = try await eligibleOffers(locale: locale)
                guard offers.contains(where: {
                    $0.productId == product.id && $0.billingPeriod == product.periodCode
                }) else { products = []; state = .empty; return }
            }
            let configuration = try await activeConfiguration()
            guard allowed(), !Task.isCancelled else { state = .ready; return }
            let outcome = try await storeRepository.purchase(product, appAccountToken: configuration.appAccountToken)
            switch outcome {
            case .success(let transaction):
                try await submitVerifiedTransaction(transaction)
            case .pending:
                state = .pending
                message = "Purchase is pending approval."
            case .cancelled:
                state = .cancelled
                message = nil
            }
        } catch {
            if case StoreCommerceError.unverifiedTransaction = error {
                message = "billing.unverified"
            } else if !networkMonitor.isReachable {
                state = .offline
                message = "Purchases are unavailable while offline."
                return
            } else {
                message = "Purchase could not be completed."
            }
            state = .failed(message ?? "Purchase could not be completed.")
            logger.warning("paywall.purchase.failed")
        }
    }

    func restore() async {
        guard !state.isBusy else { return }
        guard networkMonitor.isReachable else {
            state = .offline
            message = "Restore is unavailable while offline."
            return
        }

        do {
            state = .verifyingDeviceTransactions
            Task { await recordInteraction("restore_purchases_tapped") }
            try await storeRepository.restorePurchases()
            let transactions = try await restoredTransactions()
            if transactions.isEmpty {
                state = .refreshingEntitlement
                entitlement = try await entitlementAPI.currentEntitlement()
                refreshedLearningAccess = try? await entitlementAPI.learningAccess()
                await onServerEntitlementVerified?()
                state = .ready
                message = "billing.noRestorable"
                return
            }

            for transaction in transactions {
                try await submitVerifiedTransaction(transaction)
            }
        } catch {
            state = .failed("Purchases could not be restored.")
            message = "Purchases could not be restored."
            logger.warning("paywall.restore.failed")
        }
    }

    func reset() {
        contextualLocale = nil
        configuration = nil
        products = []
        entitlement = nil
        refreshedLearningAccess = nil
        message = nil
        state = .idle
    }

    private func activeConfiguration() async throws -> AppleBillingConfiguration {
        if let configuration {
            return configuration
        }
        let configuration = try await billingAPI.configuration()
        self.configuration = configuration
        return configuration
    }

    private func submitVerifiedTransaction(_ transaction: StoreTransaction) async throws {
        state = .submittingToBackend
        let submission = try await billingAPI.submit(transaction: transaction)
        await storeRepository.finish(transaction)
        state = .refreshingEntitlement
        refreshedLearningAccess = try? await entitlementAPI.learningAccess()
        await onServerEntitlementVerified?()
        entitlement = submission.entitlement
        state = .completed(submission.entitlement)
        message = "Access updated."
    }

    private func restoredTransactions() async throws -> [StoreTransaction] {
        let current = try await storeRepository.currentEntitlementTransactions()
        let unfinished = try await storeRepository.unfinishedTransactions()
        var seen = Set<String>()
        return (current + unfinished).filter { transaction in
            seen.insert(transaction.transactionId).inserted
        }
    }
}

enum PaywallState: Equatable {
    case idle
    case loadingProducts
    case ready
    case purchasing(String)
    case pending
    case verifyingDeviceTransactions
    case submittingToBackend
    case refreshingEntitlement
    case completed(Entitlement)
    case cancelled
    case offline
    case empty
    case failed(String)

    var isBusy: Bool {
        switch self {
        case .loadingProducts, .purchasing, .verifyingDeviceTransactions, .submittingToBackend, .refreshingEntitlement:
            true
        default:
            false
        }
    }
}
