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
                        body: try decoder.decode(Response.self, from: data)
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

    private func makeURLRequest<Response: Decodable>(from request: HTTPRequest<Response>) async throws -> URLRequest {
        var components = URLComponents(url: baseURL.appending(path: request.endpoint.path), resolvingAgainstBaseURL: false)
        components?.queryItems = request.endpoint.queryItems.isEmpty ? nil : request.endpoint.queryItems

        guard let url = components?.url else {
            throw NetworkError.invalidURL
        }

        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = request.endpoint.method.rawValue
        urlRequest.httpBody = request.body
        urlRequest.setValue("application/json", forHTTPHeaderField: "Accept")
        if request.body != nil {
            urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        try await interceptor.intercept(&urlRequest, idempotencyKey: request.idempotencyKey)
        return urlRequest
    }
}

