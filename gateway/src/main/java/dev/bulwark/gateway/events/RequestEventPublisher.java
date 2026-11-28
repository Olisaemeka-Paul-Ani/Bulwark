package dev.bulwark.gateway.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.bulwark.common.RequestEvent;

/** Hands finished request events to sentinel. */
@Component
public class RequestEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RequestEventPublisher.class);

    public void publish(RequestEvent event) {
        log.debug("request event {}", event);
    }
}
