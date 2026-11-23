package com.verifiedai.analytics.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable specification for a single canonical analytics event.
 */
public record AnalyticsEventSpec(
    String eventName,
    AnalyticsEventDomain domain,
    boolean clientAllowed,
    boolean serverAllowed,
    Set<String> allowedPropertyKeys
) {
    public AnalyticsEventSpec {
        Objects.requireNonNull(eventName, "eventName must not be null");
        Objects.requireNonNull(domain, "domain must not be null");
        allowedPropertyKeys = Set.copyOf(Objects.requireNonNull(allowedPropertyKeys, "allowedPropertyKeys must not be null"));
    }

    public boolean allowsSource(AnalyticsEventSource source) {
        return switch (source) {
            case CLIENT -> clientAllowed;
            case SERVER -> serverAllowed;
        };
    }

    public List<String> allowedSources() {
        List<String> sources = new ArrayList<>(2);
        if (clientAllowed) {
            sources.add(AnalyticsEventSource.CLIENT.name());
        }
        if (serverAllowed) {
            sources.add(AnalyticsEventSource.SERVER.name());
        }
        return List.copyOf(sources);
    }
}
