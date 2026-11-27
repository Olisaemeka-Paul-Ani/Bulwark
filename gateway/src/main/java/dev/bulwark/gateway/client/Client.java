package dev.bulwark.gateway.client;

/**
 * An API client as the gateway needs it: who it is, which rate limit tier applies, and whether it
 * may call the admin API. Deliberately excludes {@code is_attacker}, which is evaluation-only.
 */
public record Client(String clientId, String tier, boolean admin) {
}
