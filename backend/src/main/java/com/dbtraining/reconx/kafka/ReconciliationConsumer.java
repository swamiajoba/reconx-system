package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens for trade-events and schedules reconciliation.
 *
 * In a full implementation this would push a row into recon_jobs and
 * trigger the engine. The trainer copy logs the trigger so students can
 * trace the message flow end-to-end without a full job runner.
 */
@Component
public class ReconciliationConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationConsumer.class);

    @KafkaListener(topics = "trade-events", groupId = "recon-service")
    public void onTradeEvent(TradeEvent event) {
        log.info("Recon-trigger received eventId={} ref={} type={}",
                event.eventId(), event.tradeRef(), event.eventType());
        // TICKET-ADV131 (full impl) — enqueue a recon job; the trainer guide
        // explains why we schedule rather than reconcile inline (avoid
        // blocking the consumer thread).
    }
}
