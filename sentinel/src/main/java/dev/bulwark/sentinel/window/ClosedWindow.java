package dev.bulwark.sentinel.window;

import java.util.List;

import dev.bulwark.common.RequestEvent;

/**
 * All of one client's requests in one window, ordered by timestamp.
 */
public record ClosedWindow(String clientId, long windowStartMs, long windowSizeMs, List<RequestEvent> events) {
}
