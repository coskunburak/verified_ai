import Foundation

struct HTTPResponse<Body> {
    let statusCode: Int
    let headers: [AnyHashable: Any]
    let body: Body
}

