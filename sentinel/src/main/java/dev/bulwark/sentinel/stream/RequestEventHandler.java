package dev.bulwark.sentinel.stream;

import dev.bulwark.common.RequestEvent;

/** Receives each request event read from the stream, on the consumer thread. */
public interface RequestEventHandler {

    void onEvent(RequestEvent event);
}
