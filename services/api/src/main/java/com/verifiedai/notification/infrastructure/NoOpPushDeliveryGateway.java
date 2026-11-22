package com.verifiedai.notification.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deterministic Sprint 10.4 push gateway adapter executing zero external network calls.
 */
@Component
public class NoOpPushDeliveryGateway implements PushDeliveryGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoOpPushDeliveryGateway.class);

    @Override
    public PushDispatchOutcome send(PushDispatchRequest request) {
        LOGGER.info(
            "notification.push.noop_dispatch deliveryId={} category={} intentType={} tokenLast4={} correlationId={}",
            request.deliveryId(),
            request.category(),
            request.intentType(),
            request.tokenLast4(),
            request.correlationId()
        );
        return PushDispatchOutcome.sent("noop-" + request.deliveryId());
    }
}
