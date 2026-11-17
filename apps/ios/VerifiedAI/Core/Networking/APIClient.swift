import Foundation

protocol HTTPSession {
    func data(for request: URLRequest) async throws -> (Data, URLResponse)
}

extension URLSession: HTTPSession {}

final class APIClient {
    private let baseURL: URL
    private let session: HTTPSession
    private let decoder: JSONDecoder
    private let interceptor: RequestInterceptor

    init(
        baseURL: URL,
        session: HTTPSession,
        authTokenProvider: AuthTokenProvider,
        correlationContext: CorrelationContext,
        decoder: JSONDecoder = JSONDecoder()
    ) {
        self.baseURL = baseURL
        self.session = session
        self.interceptor = RequestInterceptor(tokenProvider: authTokenProvider, correlationContext: correlationContext)
        self.decoder = decoder
    }

    func send<Response: Decodable>(_ request: HTTPRequest<Response>) async throws -> HTTPResponse<Response> {
        do {
            return try await sendOnce(request)
        } catch NetworkError.server(let problem)
            where problem.code == "AUTH_TOKEN_EXPIRED" && request.allowsAuthRefreshRetry {
            guard try await interceptor.refreshAccessToken() != nil else {
                throw NetworkError.server(problem: problem)
            }
            return try await sendOnce(request)
        }
    }

    private func sendOnce<Response: Decodable>(_ request: HTTPRequest<Response>) async throws -> HTTPResponse<Response> {
        let urlRequest = try await makeURLRequest(from: request)

        do {
            let (data, response) = try await session.data(for: urlRequest)
            guard let httpResponse = response as? HTTPURLResponse else {
                throw NetworkError.invalidResponse
            }

            if (200...299).contains(httpResponse.statusCode) {
                do {
                    return HTTPResponse(
                        statusCode: httpResponse.statusCode,
                        headers: httpResponse.allHeaderFields,
                        body: try request.decodesISO8601Dates
                            ? Self.decodeISO8601Response(Response.self, from: data)
                            : decoder.decode(Response.self, from: data)
                    )
                } catch {
                    throw NetworkError.decoding(error.localizedDescription)
                }
            }

            if let problem = try? decoder.decode(ProblemDetails.self, from: data) {
                throw NetworkError.server(problem: problem)
            }

            throw NetworkError.httpStatus(httpResponse.statusCode)
        } catch let error as NetworkError {
            throw error
        } catch {
            throw NetworkError.transport(error.localizedDescription)
        }
    }

    /// Opt-in for native practice/micro-lesson contracts, without changing existing DTO decoding.
    static func decodeISO8601Response<Response: Decodable>(_ type: Response.Type, from data: Data) throws -> Response {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let value = try container.decode(String.self)
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            if let date = formatter.date(from: value) { return date }
            formatter.formatOptions = [.withInternetDateTime]
            if let date = formatter.date(from: value) { return date }
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Expected an ISO-8601 timestamp")
        }
        return try decoder.decode(type, from: data)
    }

    private func makeURLRequest<Response: Decodable>(from request: HTTPRequest<Response>) async throws -> URLRequest {
        var components = URLComponents(url: baseURL.appending(path: request.endpoint.path), resolvingAgainstBaseURL: false)
        components?.queryItems = request.endpoint.queryItems.isEmpty ? nil : request.endpoint.queryItems

        guard let url = components?.url else {
            throw NetworkError.invalidURL
        }

        var urlRequest = URLRequest(url: url)
        urlRequest.timeoutInterval = request.timeout
        urlRequest.httpMethod = request.endpoint.method.rawValue
        urlRequest.httpBody = request.body
        urlRequest.setValue("application/json", forHTTPHeaderField: "Accept")
        if request.body != nil {
            urlRequest.setValue(request.contentType, forHTTPHeaderField: "Content-Type")
        }
        try await interceptor.intercept(&urlRequest, idempotencyKey: request.idempotencyKey)
        return urlRequest
    }
}
