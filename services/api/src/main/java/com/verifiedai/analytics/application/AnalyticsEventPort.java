package com.verifiedai.analytics.application;

/**
 * Public Spring Modulith SPI (@NamedInterface("application")) for server-authoritative
 * analytics event recording from peer modules (INV-ANL-002, INV-ANL-007).
 */
public interface AnalyticsEventPort {

    AnalyticsIngestOutcome recordServerEvent(RecordAnalyticsEventCommand command);
}
