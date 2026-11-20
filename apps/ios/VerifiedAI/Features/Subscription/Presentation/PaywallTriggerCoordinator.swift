import SwiftUI

@MainActor
@Observable
final class PaywallTriggerCoordinator {
    enum Flow: Hashable { case exam, diagnostic, twin }
    enum Timer: Equatable { case active, inactive, unknown }
    private var timers: [Flow: Timer] = [:]
    private var lastContextualPresentation: Date?

    var safeToPresent: Bool { timers.values.allSatisfy { $0 == .inactive } }

    func update(_ flow: Flow, _ timer: Timer) { timers[flow] = timer }

    func permits(eligible: Bool, enabled: Bool, acceptedFreeOutput: Bool,
                 explicit: Bool, now: Date) -> Bool {
        guard safeToPresent, eligible, enabled, !acceptedFreeOutput else { return false }
        return explicit || (lastContextualPresentation.map { now.timeIntervalSince($0) >= 86400 } ?? true)
    }

    func didPresent(at date: Date) { lastContextualPresentation = date }
}

extension EnvironmentValues {
    @Entry var billingTimerCoordinator: PaywallTriggerCoordinator? = nil
    @Entry var contextualCommerce: PaywallViewModel? = nil
}
