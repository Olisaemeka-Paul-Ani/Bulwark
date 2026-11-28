package dev.bulwark.gateway.quarantine;

import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Fills the registry before the node starts taking traffic, so a freshly started node doesn't
 * briefly let quarantined clients through.
 */
@Component
public class QuarantineStartupLoader implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(QuarantineStartupLoader.class);

    private final QuarantineStore store;
    private final QuarantineRegistry registry;

    public QuarantineStartupLoader(QuarantineStore store, QuarantineRegistry registry) {
        this.store = store;
        this.registry = registry;
    }

    @Override
    public void afterPropertiesSet() {
        try {
            List<QuarantineEntry> active = store.loadActive();
            registry.replaceAll(active);
            log.info("loaded {} active quarantine entries", active.size());
        } catch (SQLException e) {
            // Start anyway: the listener resyncs as soon as it can reach Postgres.
            log.warn("could not load quarantine set at startup: {}", e.toString());
        }
    }
}
