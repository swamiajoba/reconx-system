//package com.dbtraining.reconx.observability;
//
//import io.micrometer.core.instrument.MeterRegistry;
//import io.micrometer.core.instrument.Timer;
//import org.springframework.stereotype.Component;
//
//@Component
//public class ReconMetrics {
//
//    private final Timer reconciliationTimer;
//
//    public ReconMetrics(MeterRegistry registry) {
//        this.reconciliationTimer = Timer.builder("reconciliation_duration_seconds")
//                .description("Time spent in ReconciliationEngine.reconcile()")
//                .publishPercentileHistogram()
//                .publishPercentiles(0.5, 0.95, 0.99)
//                .register(registry);
//    }
//
//    public Timer reconciliationTimer() {
//        return reconciliationTimer;
//    }
//}