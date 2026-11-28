package dev.bulwark.gateway.admin;

/**
 * Body of {@code POST /admin/quarantine}.
 *
 * @param reason defaults to {@code manual}
 */
public record QuarantineRequest(String clientId, Integer minutes, String reason) {
}
