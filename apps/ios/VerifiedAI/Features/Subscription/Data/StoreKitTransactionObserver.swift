import Foundation

final class StoreKitTransactionObserver: @unchecked Sendable {
    private let storeRepository: StoreProductRepository
    private let billingAPI: AppleBillingServicing
    private let logger: AppLogger
    private let lock = NSLock()
    private var task: Task<Void, Never>?

    init(
        storeRepository: StoreProductRepository,
        billingAPI: AppleBillingServicing,
        logger: AppLogger
    ) {
        self.storeRepository = storeRepository
        self.billingAPI = billingAPI
        self.logger = logger
    }

    func start() {
        lock.lock()
        defer { lock.unlock() }
        guard task == nil else {
            return
        }
        task = Task { [storeRepository, billingAPI, logger] in
            do {
                let unfinished = try await storeRepository.unfinishedTransactions()
                for transaction in unfinished {
                    try await Self.submitAndFinish(transaction, billingAPI: billingAPI, storeRepository: storeRepository)
                }
            } catch {
                logger.warning("storekit.unfinished_replay.failed")
            }

            for await transaction in storeRepository.transactionUpdates() {
                do {
                    try await Self.submitAndFinish(transaction, billingAPI: billingAPI, storeRepository: storeRepository)
                    logger.info("storekit.transaction_update.processed")
                } catch {
                    logger.warning("storekit.transaction_update.failed")
                }
            }
        }
    }

    func stop() {
        lock.lock()
        defer { lock.unlock() }
        task?.cancel()
        task = nil
    }

    private static func submitAndFinish(
        _ transaction: StoreTransaction,
        billingAPI: AppleBillingServicing,
        storeRepository: StoreProductRepository
    ) async throws {
        _ = try await billingAPI.submit(transaction: transaction)
        await storeRepository.finish(transaction)
    }
}
