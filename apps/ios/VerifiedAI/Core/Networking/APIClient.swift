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

    func sendConditional<Response: Decodable & Sendable>(_ request: HTTPRequest<Response>) async throws -> ConditionalHTTPResponse<Response> {
        guard request.endpoint.method == .get else { throw NetworkError.invalidResponse }
        let (data, response) = try await preExamTransport(request)
        let etag = response.value(forHTTPHeaderField: "ETag")
        if response.statusCode == 304 { return .notModified(etag) }
        guard response.statusCode == 200 else { throw NetworkError.httpStatus(response.statusCode) }
        do {
            let body = try request.decodesISO8601Dates ? Self.decodeISO8601Response(Response.self, from: data) : decoder.decode(Response.self, from: data)
            return .modified(body, etag)
        } catch { throw NetworkError.decoding(error.localizedDescription) }
    }

    func sendNoContent(_ request: HTTPRequest<EmptyHTTPBody>) async throws {
        let (_, response) = try await preExamTransport(request)
        guard response.statusCode == 204 else { throw NetworkError.httpStatus(response.statusCode) }
    }

    private func preExamTransport<Response: Decodable>(_ request: HTTPRequest<Response>, retry: Bool = true) async throws -> (Data, HTTPURLResponse) {
        var url = try await makeURLRequest(from: request)
        url.cachePolicy = .reloadIgnoringLocalCacheData
        let result: (Data, URLResponse)
        do { result = try await session.data(for: url) }
        catch is CancellationError { throw CancellationError() }
        catch {
            if (error as? URLError)?.code == .cancelled { throw CancellationError() }
            throw NetworkError.transport(error.localizedDescription)
        }
        guard let response = result.1 as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        if (200...299).contains(response.statusCode) || response.statusCode == 304 { return (result.0, response) }
        let problem = try? decoder.decode(ProblemDetails.self, from: result.0)
        if response.statusCode == 401, problem?.code == "AUTH_TOKEN_EXPIRED", retry, request.allowsAuthRefreshRetry {
            do {
                guard try await interceptor.refreshAccessToken() != nil else { throw NetworkError.httpStatus(401) }
                return try await preExamTransport(request, retry: false)
            } catch is CancellationError { throw CancellationError() }
            catch { throw NetworkError.httpStatus(401) }
        }
        // Preserve actual HTTP status so malformed error bodies cannot enable offline fallback.
        throw NetworkError.httpStatus(response.statusCode)
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
        if let tag = request.ifNoneMatch {
            guard request.endpoint.method == .get, tag.utf8.count <= 8192,
                  !tag.contains("\r"), !tag.contains("\n") else { throw NetworkError.invalidResponse }
            urlRequest.setValue(tag, forHTTPHeaderField: "If-None-Match")
        }
        try await interceptor.intercept(&urlRequest, idempotencyKey: request.idempotencyKey)
        return urlRequest
    }
}
