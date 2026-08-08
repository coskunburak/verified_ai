package com.verifiedai.identity.api;

import com.verifiedai.identity.application.DataExportResult;
import java.time.Instant;
import java.util.UUID;

record DataExportResponse(
    UUID exportId,
    String status,
    String schemaVersion,
    Instant requestedAt,
    Instant completedAt,
    Instant downloadedAt,
    Instant expiresAt
) {
    static DataExportResponse from(DataExportResult result) {
        return new DataExportResponse(
            result.exportId(),
            result.status(),
            result.schemaVersion(),
            result.requestedAt(),
            result.completedAt(),
            result.downloadedAt(),
            result.expiresAt()
        );
    }
}
