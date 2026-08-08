import Foundation
import StoreKit

actor StoreKitProductRepository: StoreProductRepository {
    private var productsByIdentifier: [String: Product] = [:]
    private var transactionsByIdentifier: [String: Transaction] = [:]

    func products(for productIdentifiers: [String]) async throws -> [StoreProduct] {
        let products = try await Product.products(for: productIdentifiers)
        for product in products {
            productsByIdentifier[product.id] = product
        }
        let ordering = Dictionary(uniqueKeysWithValues: productIdentifiers.enumerated().map { ($0.element, $0.offset) })
        return products
            .sorted { (ordering[$0.id] ?? Int.max) < (ordering[$1.id] ?? Int.max) }
            .map(Self.storeProduct(from:))
    }

    func purchase(_ product: StoreProduct, appAccountToken: UUID) async throws -> StorePurchaseOutcome {
        let storeKitProduct = try await productForPurchase(product.id)
        let result = try await storeKitProduct.purchase(options: [.appAccountToken(appAccountToken)])
        switch result {
        case .success(let verificationResult):
            let transaction = try storeTransaction(from: verificationResult)
            return .success(transaction)
        case .pending:
            return .pending
        case .userCancelled:
            return .cancelled
        @unknown default:
            return .pending
        }
    }

    func restorePurchases() async throws {
        try await AppStore.sync()
    }

    func currentEntitlementTransactions() async throws -> [StoreTransaction] {
        var transactions: [StoreTransaction] = []
        for await verificationResult in Transaction.currentEntitlements {
            let transaction = try storeTransaction(from: verificationResult)
            transactions.append(transaction)
        }
        return transactions
    }

    func unfinishedTransactions() async throws -> [StoreTransaction] {
        var transactions: [StoreTransaction] = []
        for await verificationResult in Transaction.unfinished {
            let transaction = try storeTransaction(from: verificationResult)
            transactions.append(transaction)
        }
        return transactions
    }

    nonisolated func transactionUpdates() -> AsyncStream<StoreTransaction> {
        AsyncStream { continuation in
            let task = Task {
                for await verificationResult in Transaction.updates {
                    do {
                        let transaction = try await self.storeTransaction(from: verificationResult)
                        continuation.yield(transaction)
                    } catch {
                        continue
                    }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in
                task.cancel()
            }
        }
    }

    func finish(_ transaction: StoreTransaction) async {
        guard let storeKitTransaction = transactionsByIdentifier.removeValue(forKey: transaction.transactionId) else {
            return
        }
        await storeKitTransaction.finish()
    }

    private func productForPurchase(_ identifier: String) async throws -> Product {
        if let product = productsByIdentifier[identifier] {
            return product
        }
        guard let product = try await Product.products(for: [identifier]).first else {
            throw StoreCommerceError.productUnavailable
        }
        productsByIdentifier[identifier] = product
        return product
    }

    private func storeTransaction(from verificationResult: VerificationResult<Transaction>) throws -> StoreTransaction {
        switch verificationResult {
        case .verified(let transaction):
            transactionsByIdentifier[String(transaction.id)] = transaction
            return StoreTransaction(
                transactionId: String(transaction.id),
                productId: transaction.productID,
                signedTransactionInfo: verificationResult.jwsRepresentation
            )
        case .unverified(_, let error):
            throw StoreCommerceError.unverifiedTransaction(error.localizedDescription)
        }
    }

    private static func storeProduct(from product: Product) -> StoreProduct {
        StoreProduct(
            id: product.id,
            displayName: product.displayName,
            description: product.description,
            displayPrice: product.displayPrice,
            subscriptionPeriod: product.subscription?.subscriptionPeriod.localizedDescription
        )
    }
}

private extension Product.SubscriptionPeriod {
    var localizedDescription: String {
        let unitText: String
        switch unit {
        case .day:
            unitText = value == 1 ? "day" : "days"
        case .week:
            unitText = value == 1 ? "week" : "weeks"
        case .month:
            unitText = value == 1 ? "month" : "months"
        case .year:
            unitText = value == 1 ? "year" : "years"
        @unknown default:
            unitText = "period"
        }
        return "\(value) \(unitText)"
    }
}
