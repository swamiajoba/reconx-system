package com.dbtraining.reconx.observability;

import com.dbtraining.reconx.repository.ReconBreakRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * trade_created_total Counter
 * recon_break_count Gauge (polled — wraps repo.countByStatus)
 * trade_value_total DistributionSummary
 *
 * The TIMER for reconciliation duration lives as @Timed on
 * ReconciliationEngine.reconcile() (TICKET-ADV084).
 * ============================================================================
 */
@Component
public class TradeMetrics {

    private final Counter tradeCreated;
    private final DistributionSummary tradeValue;

    public TradeMetrics(MeterRegistry registry, ReconBreakRepository breakRepo) {
        this.tradeCreated = Counter.builder("trade_created_total")
                .description("Total trades created")
                .register(registry);

        this.tradeValue = DistributionSummary.builder("trade_value_total")
                .description("Distribution of trade notional values")
                .baseUnit("USD")
                .publishPercentileHistogram()
                .register(registry);

        // polled gauge wrapping a repository count.
        Gauge.builder("recon_break_count", breakRepo, r -> r.countByStatus("OPEN"))
                .description("Open recon breaks")
                .register(registry);
    }

    public void incrementTradeCreated() { tradeCreated.increment(); }
    public void recordTradeValue(double value) { tradeValue.record(value); }
}
