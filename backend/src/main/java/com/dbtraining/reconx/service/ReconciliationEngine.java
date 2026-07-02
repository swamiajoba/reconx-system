package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.ReconResult;
import com.dbtraining.reconx.model.ReconciliationRule;
import com.dbtraining.reconx.model.TradeType;
import io.micrometer.core.annotation.Timed;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * ReconciliationEngine using Streams (parallel matching)
 * CompletableFuture: parallel recon by counterparty
 * Edge cases: empty/single/all-mismatched inputs handled
 * @Timed exports reconciliation_duration_seconds histogram
 *
 * WHAT:    Compares internal trades against external (counterparty) trades and
 *          returns a ReconResult per internal trade (MATCHED or BREAK).
 * HOW:     Index externals by tradeRef, then stream internals and look each
 *          up. CompletableFuture variant batches by counterparty for
 *          throughput on large books.
 * WHY:     This is the spine of the product. Everything else (REST API,
 *          Kafka consumers, dashboard) ultimately calls into here.
 * OBSERVE: Histogram appears at /actuator/prometheus under
 *          reconciliation_duration_seconds.
 * ============================================================================
 */
@Service
public class ReconciliationEngine {

    @Timed(value = "reconciliation.duration", description = "Wall time of reconcile()",
           percentiles = {0.5, 0.95, 0.99}, histogram = true)
    public List<ReconResult> reconcile(List<TradeType> internal,
                                       List<TradeType> external,
                                       ReconciliationRule rule) {
        if (internal == null || internal.isEmpty()) return List.of();

        Map<String, TradeType> externalByRef = (external == null ? List.<TradeType>of() : external)
                .stream()
                .collect(Collectors.toMap(t -> t.tradeRef().value(), Function.identity(), (a, b) -> a));

        return internal.parallelStream()
                .map(in -> matchOne(in, externalByRef.get(in.tradeRef().value()), rule))
                .toList();
    }

    /**
     * split by counterparty, reconcile each batch concurrently,
     * combine into a single result list. Caller passes one external feed per
     * counterparty (typical real-world shape).
     */
    public CompletableFuture<List<ReconResult>> reconcileByCounterparty(
            Map<Long, List<TradeType>> internalByCp,
            Map<Long, List<TradeType>> externalByCp,
            ReconciliationRule rule) {

        List<CompletableFuture<List<ReconResult>>> futures = internalByCp.entrySet().stream()
                .map(e -> CompletableFuture.supplyAsync(() ->
                        reconcile(e.getValue(), externalByCp.getOrDefault(e.getKey(), List.of()), rule)))
                .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(v -> futures.stream().flatMap(f -> f.join().stream()).toList());
    }

    private ReconResult matchOne(TradeType internal, TradeType external, ReconciliationRule rule) {
        String ref = internal.tradeRef().value();
        if (external == null) {
            return ReconResult.breakResult(ref, "MISSING_EXTERNAL",
                    "No external trade found for " + ref);
        }
        BigDecimal[] iPair = priceQty(internal);
        BigDecimal[] ePair = priceQty(external);
        if (rule.matches(iPair[0], iPair[1], ePair[0], ePair[1])) {
            return ReconResult.matched(ref);
        }
        return ReconResult.breakResult(ref, "VALUE_MISMATCH",
                "internal=%s/%s external=%s/%s".formatted(iPair[0], iPair[1], ePair[0], ePair[1]));
    }

    /** exhaustive switch over the sealed hierarchy. */
    private BigDecimal[] priceQty(TradeType t) {
        return switch (t) {
            case com.dbtraining.reconx.model.EquityTrade e     -> new BigDecimal[]{e.price(),  e.quantity()};
            case com.dbtraining.reconx.model.FXTrade fx        -> new BigDecimal[]{fx.fxRate(), fx.notionalCcy1()};
            case com.dbtraining.reconx.model.BondTrade b       -> new BigDecimal[]{b.couponRate(), b.faceValue()};
            case com.dbtraining.reconx.model.DerivativeTrade d -> new BigDecimal[]{d.strike(), d.quantity()};
        };
    }
}
