package com.dbtraining.reconx.observability;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * DatabaseHealthIndicator (timed SELECT 1)
 *
 * Custom indicator instead of Spring's default — gives us a per-statement
 * timeout we control and a name that appears under /actuator/health/database.
 */
@Component("database")
public class DatabaseHealthIndicator extends AbstractHealthIndicator {

    private final DataSource ds;

    public DatabaseHealthIndicator(DataSource ds) { this.ds = ds; }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        long start = System.nanoTime();
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.setQueryTimeout(2);
            s.execute("SELECT 1");
            builder.up().withDetail("latencyMs", (System.nanoTime() - start) / 1_000_000);
        }
    }
}
