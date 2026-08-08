import Foundation

final class URLSessionPresignedObjectUploader: PresignedObjectUploading, @unchecked Sendable {
    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func upload(
        fileURL: URL,
        to uploadURL: URL,
        requiredHeaders: [String: String],
        progress: @escaping @Sendable (Double) -> Void
    ) async throws {
        var request = URLRequest(url: uploadURL)
        request.httpMethod = "PUT"
        for (header, value) in requiredHeaders {
            request.setValue(value, forHTTPHeaderField: header)
        }
        request.setValue(nil, forHTTPHeaderField: "Authorization")

        progress(0)
        let data = try Data(contentsOf: fileURL)
        progress(0.05)
        let (_, response) = try await session.upload(for: request, from: data)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NetworkError.invalidResponse
        }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpStatus(httpResponse.statusCode)
        }
        progress(1)
    }
}
