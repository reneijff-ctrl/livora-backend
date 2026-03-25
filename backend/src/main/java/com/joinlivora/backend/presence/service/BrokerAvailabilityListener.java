package com.joinlivora.backend.presence.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.stereotype.Component;

/**
 * Listener that tracks the availability of the STOMP message broker.
 * This is crucial when using a STOMP Broker Relay (like RabbitMQ)
 * to avoid MessageDeliveryException when the broker is not yet active.
 */
@Component
@Slf4j
public class BrokerAvailabilityListener {

    private volatile boolean brokerAvailable = false;

    @EventListener
    public void handleBrokerAvailability(BrokerAvailabilityEvent event) {
        this.brokerAvailable = event.isBrokerAvailable();
        log.info("STOMP Broker availability changed: {}", this.brokerAvailable);
    }

    public boolean isBrokerAvailable() {
        return brokerAvailable;
    }

    /**
     * For testing purposes or legacy manual wiring.
     */
    public void setBrokerAvailable(boolean available) {
        this.brokerAvailable = available;
    }
}
