package com.verifiedai.analytics.api.dto;

import com.verifiedai.analytics.application.AnalyticsEventIngestionService.CatalogEntryView;
import com.verifiedai.analytics.application.AnalyticsEventIngestionService.CatalogView;
import java.time.Instant;
import java.util.List;

public record AnalyticsEventCatalogResponse(
    String schemaVersion,
    String catalogVersion,
    int eventCount,
    List<EventItem> events,
    int llmTokensUsed,
    boolean trainingEligible,
    Instant generatedAt
) {
    public static AnalyticsEventCatalogResponse from(CatalogView view) {
        return new AnalyticsEventCatalogResponse(
            view.schemaVersion(),
            view.catalogVersion(),
            view.eventCount(),
            view.events().stream().map(EventItem::from).toList(),
            view.llmTokensUsed(),
            view.trainingEligible(),
            view.generatedAt()
        );
    }

    public record EventItem(
        String eventName,
        String eventDomain,
        boolean clientAllowed,
        boolean serverAllowed,
        List<String> allowedPropertyKeys
    ) {
        public static EventItem from(CatalogEntryView entry) {
            return new EventItem(
                entry.eventName(),
                entry.eventDomain(),
                entry.clientAllowed(),
                entry.serverAllowed(),
                entry.allowedPropertyKeys()
            );
        }
    }
}
