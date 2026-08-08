import Foundation

struct HTTPResponse<Body>: @unchecked Sendable where Body: Sendable {
    let statusCode: Int
    let headers: [AnyHashable: Any]
    let body: Body
}
