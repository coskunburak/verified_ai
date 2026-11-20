import SwiftUI

/// Mounted only after a completed unassisted twin. All educational content stays in the parent.
struct ContextualPaywallSheetView: View {
    @Environment(\.billingTimerCoordinator) private var coordinator
    @Environment(\.contextualCommerce) private var commerce
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.locale) private var locale
    @State private var available = false
    @State private var preparation: Task<Void, Never>?
    @State private var counted = false
    @State private var presented = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if available, coordinator?.safeToPresent == true {
                Button("billing.seePlans") {
                    preparation?.cancel()
                    preparation = Task {
                        guard let commerce, let coordinator,
                              await commerce.prepareContextual(locale: catalogLocale),
                              !Task.isCancelled, coordinator.permits(eligible: true, enabled: true, acceptedFreeOutput: false,
                                                  explicit: false, now: Date()) else { return }
                        presented = true
                    }
                }
                .frame(minHeight: 44)
            }
        }
        .onDisappear { preparation?.cancel() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { preparation?.cancel(); presented = false; available = false }
        }
        .task(id: availabilityKey) {
            available = false
            guard let commerce, scenePhase == .active, coordinator?.safeToPresent == true else { return }
            let enabled = await commerce.contextualAvailable(locale: catalogLocale)
            if !Task.isCancelled {
                available = coordinator?.permits(eligible: enabled, enabled: enabled, acceptedFreeOutput: false,
                                                  explicit: false, now: Date()) == true
            }
        }
        .onChange(of: coordinator?.safeToPresent) { _, safe in
            if safe != true { presented = false }
        }
        .sheet(isPresented: $presented, onDismiss: { available = false }) {
            if let commerce, coordinator?.safeToPresent == true {
                PaywallView(viewModel: commerce, contextual: true, safeToPurchase: { coordinator?.safeToPresent == true })
                    .onAppear {
                        guard !counted else { return }
                        counted = true
                        coordinator?.didPresent(at: Date())
                        Task { await commerce.recordInteraction("paywall_viewed") }
                    }
            }
        }
    }

    private var availabilityKey: String {
        catalogLocale + (coordinator?.safeToPresent == true && scenePhase == .active ? ".idle" : ".blocked")
    }

    private var catalogLocale: String {
        switch locale.language.languageCode?.identifier {
        case "tr": "tr-TR"
        case "en": "en-US"
        default: "unsupported"
        }
    }
}
