package com.verifiedai.problem.application.asset;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
class ProblemAssetUploadMetrics {
    private final MeterRegistry meterRegistry;

    ProblemAssetUploadMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void reservationSuccess(String assetKind) {
        counter("problem.asset.reservation.success.total", "asset_kind", assetKind).increment();
    }

    void reservationFailure(String assetKind, String errorCode) {
        counter("problem.asset.reservation.failure.total", "asset_kind", assetKind, "error_class", errorCode).increment();
    }

    void completeSuccess(String assetKind) {
        counter("problem.asset.upload.complete.success.total", "asset_kind", assetKind).increment();
    }

    void completeFailure(String assetKind, String errorCode) {
        counter("problem.asset.upload.complete.failure.total", "asset_kind", assetKind, "error_class", errorCode).increment();
    }

    void checksumMismatch(String assetKind) {
        counter("problem.asset.checksum_mismatch.total", "asset_kind", assetKind).increment();
    }

    void sizeMismatch(String assetKind) {
        counter("problem.asset.size_mismatch.total", "asset_kind", assetKind).increment();
    }

    void pendingExpired() {
        counter("problem.asset.pending_expired.total").increment();
    }

    void presignLatency(long nanos) {
        timer("problem.asset.presign.latency").record(nanos, TimeUnit.NANOSECONDS);
    }

    void completionVerificationLatency(long nanos) {
        timer("problem.asset.completion.verification.latency").record(nanos, TimeUnit.NANOSECONDS);
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }

    private Timer timer(String name) {
        return Timer.builder(name).register(meterRegistry);
    }
}
