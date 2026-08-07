import SwiftUI

struct AppLifecycleHandler {
    private let logger: AppLogger

    init(logger: AppLogger) {
        self.logger = logger
    }

    func handle(scenePhase: ScenePhase) {
        switch scenePhase {
        case .active:
            logger.info("app lifecycle active")
        case .inactive:
            logger.info("app lifecycle inactive")
        case .background:
            logger.info("app lifecycle background")
        @unknown default:
            logger.warning("app lifecycle unknown")
        }
    }
}

