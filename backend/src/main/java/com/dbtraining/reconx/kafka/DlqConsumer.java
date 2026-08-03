//package com.dbtraining.reconx.kafka;
//
//import com.dbtraining.reconx.dto.TradeEvent;
//import com.dbtraining.reconx.model.DlqMessage;
//import com.dbtraining.reconx.repository.DlqMessageRepository;
//import org.apache.kafka.clients.consumer.ConsumerRecord;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.kafka.support.KafkaHeaders;
//import org.springframework.messaging.handler.annotation.Header;
//import org.springframework.stereotype.Component;
//
//import java.time.Instant;
//
///**
// * ============================================================================
// * TICKET-ADV136 — DLQ Consumer (part 1 of 2)
// *
// * WHAT:    Listens on `trade-events-dlq` and persists every quarantined
// *          message as a DlqMessage row so operators can inspect and replay
// *          individual failures via DlqAdminController.
// * HOW:     @KafkaListener on `trade-events-dlq`, groupId `dlq-monitor`.
// *          Receives the full ConsumerRecord (to get partition/offset) plus
// *          the EXCEPTION_MESSAGE header that DeadLetterPublishingRecoverer
// *          (TICKET-ADV134) writes onto every DLQ record.
// * WHY:     Without a DLQ consumer the quarantined record is invisible — it
// *          sits on the topic but no one gets paged and no operator knows
// *          which trade failed or why. Persisting to a DB table gives the
// *          replay endpoint (DlqAdminController) a structured record to
// *          query and a place to mark each message replayed.
// * OBSERVE: After a poison message hits the DLQ, query
// *          `SELECT * FROM dlq_messages` — one row per failure, carrying
// *          eventId, tradeRef, originalTopic, partition, offset and reason.
// *
// * GOTCHA:  The containerFactory must match the one used for trade-events
// *          listeners so the same JsonDeserializer config is applied.
// *          If you omit it, the consumer inherits a different factory and
// *          gets ClassCastException on the TradeEvent payload.
// * ============================================================================
// */
//@Component
//public class DlqConsumer {
//
//    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);
//
//    private final DlqMessageRepository repo;
//
//    public DlqConsumer(DlqMessageRepository repo) {
//        this.repo = repo;
//    }
//
//    @KafkaListener(
//            topics = "trade-events-dlq",
//            groupId = "dlq-monitor",
//            containerFactory = "tradeEventListenerContainerFactory"
//    )
//    public void onDlqMessage(
//            ConsumerRecord<String, TradeEvent> record,
//            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exMsg) {
//
//        TradeEvent event = record.value();
//        log.error("DLQ: trade={} eventId={} partition={} offset={} reason={}",
//                event.tradeRef(), event.eventId(), record.partition(), record.offset(), exMsg);
//
//        repo.save(DlqMessage.builder()
//                .eventId(event.eventId())
//                .tradeRef(event.tradeRef())
//                .originalTopic(record.topic().replace("-dlq", ""))
//                .partition(record.partition())
//                .offset(record.offset())
//                .payload(event)
//                .reason(exMsg)
//                .firstSeen(Instant.now())
//                .build());
//    }
//}
