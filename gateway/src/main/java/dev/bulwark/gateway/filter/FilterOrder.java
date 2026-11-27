package dev.bulwark.gateway.filter;

/**
 * Order of Bulwark's global filters, kept in one place so the chain reads top to bottom:
 * identity → quarantine → rate limit → route. All run before Spring Cloud Gateway's routing
 * filters (which have order 0 and above).
 */
public final class FilterOrder {

    public static final int CLIENT_IDENTITY = -300;
    public static final int RATE_LIMIT = -100;

    private FilterOrder() {
    }
}
