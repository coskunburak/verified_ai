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
    private(set) var message: String?

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

    func load(force: Bool = false) async {
        if !force, case .ready = state {
            return
        }
        guard networkMonitor.isReachable else {
            state = .offline
            message = "Purchases are unavailable while offline."
            return
        }

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

            let storeProducts = try await storeRepository.products(for: configuration.products.map(\.appStoreProductId))
            products = storeProducts
            state = storeProducts.isEmpty ? .empty : .ready
            logger.info("paywall.products.ready")
        } catch {
            state = .failed("Subscriptions could not be loaded.")
            message = "Subscriptions could not be loaded."
            logger.warning("paywall.products.failed")
        }
    }

    func purchase(_ product: StoreProduct) async {
        guard networkMonitor.isReachable else {
            state = .offline
            message = "Purchases are unavailable while offline."
            return
        }

        do {
            let configuration = try await activeConfiguration()
            state = .purchasing(product.id)
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
            state = .failed("Purchase could not be completed.")
            message = "Purchase could not be completed."
            logger.warning("paywall.purchase.failed")
        }
    }

    func restore() async {
        guard networkMonitor.isReachable else {
            state = .offline
            message = "Restore is unavailable while offline."
            return
        }

        do {
            state = .verifyingDeviceTransactions
            try await storeRepository.restorePurchases()
            let transactions = try await restoredTransactions()
            if transactions.isEmpty {
                state = .refreshingEntitlement
                entitlement = try await entitlementAPI.currentEntitlement()
                state = entitlement.map(PaywallState.completed) ?? .ready
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
        configuration = nil
        products = []
        entitlement = nil
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
