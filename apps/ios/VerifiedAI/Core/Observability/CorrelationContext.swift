import Foundation

final class CorrelationContext {
    func requestId() -> String {
        UUID().uuidString
    }
}

