import Foundation

final class NotificationAPI: NotificationServicing, @unchecked Sendable {
    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func fetchPreferences() async throws -> NotificationPreferences {
        let response: HTTPResponse<NotificationPreferences> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/notifications/preferences", method: .get),
                decodesISO8601Dates: true
            )
        )
        return response.body
    }

    func updatePreferences(_ patch: NotificationPreferencePatch) async throws -> NotificationPreferences {
        let body = try encoder.encode(patch)
        let response: HTTPResponse<NotificationPreferences> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/notifications/preferences", method: .patch),
                body: body,
                decodesISO8601Dates: true
            )
        )
        return response.body
    }

    func registerDevice(_ request: NotificationDeviceRegisterRequest) async throws -> RegisteredNotificationDevice {
        let body = try encoder.encode(request)
        let response: HTTPResponse<RegisteredNotificationDevice> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/notifications/devices", method: .post),
                body: body,
                decodesISO8601Dates: true
            )
        )
        return response.body
    }

    func listActiveDevices() async throws -> [RegisteredNotificationDevice] {
        let response: HTTPResponse<NotificationDeviceListResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/notifications/devices", method: .get),
                decodesISO8601Dates: true
            )
        )
        return response.body.items
    }

    func revokeDevice(deviceId: UUID) async throws -> RegisteredNotificationDevice {
        let response: HTTPResponse<RegisteredNotificationDevice> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/notifications/devices/\(deviceId.uuidString)", method: .delete),
                decodesISO8601Dates: true
            )
        )
        return response.body
    }

    func listDeliveries(limit: Int) async throws -> [NotificationDeliveryItem] {
        let response: HTTPResponse<NotificationDeliveryListResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(
                    path: "/api/v1/notifications/deliveries",
                    method: .get,
                    queryItems: [URLQueryItem(name: "limit", value: String(limit))]
                ),
                decodesISO8601Dates: true
            )
        )
        return response.body.items
    }

    func cancelDelivery(deliveryId: UUID) async throws -> NotificationDeliveryCancelResponse {
        let response: HTTPResponse<NotificationDeliveryCancelResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/notifications/deliveries/\(deliveryId.uuidString)/cancel", method: .post),
                decodesISO8601Dates: true
            )
        )
        return response.body
    }

    func fetchLifecycleSummary(limit: Int) async throws -> LifecycleSummaryResponse {
        let clampedLimit = max(1, min(limit, 50))
        let response: HTTPResponse<LifecycleSummaryResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(
                    path: "/api/v1/notifications/lifecycle-summary",
                    method: .get,
                    queryItems: [URLQueryItem(name: "limit", value: String(clampedLimit))]
                ),
                decodesISO8601Dates: true
            )
        )
        return response.body
    }
}
