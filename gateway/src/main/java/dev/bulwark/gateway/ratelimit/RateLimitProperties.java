package dev.bulwark.gateway.ratelimit;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token bucket settings per client tier ({@code clients.tier}). The {@code free} tier is required
 * and doubles as the fallback, so a tier added to the database before the config is updated gets
 * the most restrictive limit rather than none.
 */
@ConfigurationProperties("bulwark.rate-limit")
public record RateLimitProperties(Map<String, Tier> tiers) {

    public static final String DEFAULT_TIER = "free";

    public record Tier(int capacity, double refillPerSecond) {

        public Tier {
            if (capacity < 1 || refillPerSecond <= 0) {
                throw new IllegalArgumentException(
                        "tier needs capacity >= 1 and refill-per-second > 0, got " + capacity + ", " + refillPerSecond);
            }
        }
    }

    public RateLimitProperties {
        if (tiers == null || !tiers.containsKey(DEFAULT_TIER)) {
            throw new IllegalArgumentException("bulwark.rate-limit.tiers must define the '" + DEFAULT_TIER + "' tier");
        }
        tiers = Map.copyOf(tiers);
    }

    public Tier forTier(String tier) {
        return tiers.getOrDefault(tier, tiers.get(DEFAULT_TIER));
    }
}
