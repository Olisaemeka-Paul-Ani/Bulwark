package dev.bulwark.sentinel.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.bulwark.common.RequestEvent;

/** Debug aid: logs every consumed event when {@code dev.bulwark.sentinel.stream} is at DEBUG. */
@Component
class LoggingEventHandler implements RequestEventHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventHandler.class);

    @Override
    public void onEvent(RequestEvent event) {
        log.debug("event {}", event);
    }
}
