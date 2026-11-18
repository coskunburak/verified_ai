import Foundation

struct HTTPResponse<Body>: @unchecked Sendable where Body: Sendable {
    let statusCode: Int
    let headers: [AnyHashable: Any]
    let body: Body
}

struct EmptyHTTPBody: Decodable, Sendable {}
enum ConditionalHTTPResponse<Body: Sendable>: Sendable {
    case modified(Body, String?)
    case notModified(String?)
}
