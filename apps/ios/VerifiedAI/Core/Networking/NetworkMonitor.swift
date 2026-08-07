import Foundation
import Network
import Observation

@MainActor
protocol NetworkMonitoring {
    var isReachable: Bool { get }
}

@MainActor
@Observable
final class NetworkMonitor: NetworkMonitoring, @unchecked Sendable {
    private let monitor: NWPathMonitor
    private let queue = DispatchQueue(label: "com.verifiedai.learning.network-monitor")

    private(set) var isReachable: Bool = true

    init(monitor: NWPathMonitor = NWPathMonitor()) {
        self.monitor = monitor
        monitor.pathUpdateHandler = { [weak self] path in
            let isReachable = path.status == .satisfied
            Task { @MainActor [weak self] in
                self?.isReachable = isReachable
            }
        }
        monitor.start(queue: queue)
    }

    deinit {
        monitor.cancel()
    }
}
