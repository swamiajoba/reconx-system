//package com.dbtraining.reconx.service;
//
//import com.dbtraining.reconx.dto.TradeEvent;
//import com.dbtraining.reconx.repository.AuditLogRepository;
//import com.dbtraining.reconx.repository.entity.AuditLogEntry;
//import com.fasterxml.jackson.databind.JsonNode;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.Optional;
//
///**
// * ============================================================================
// * TICKET-ADV137 — Event Sourcing Rebuild
// *
// * WHAT:    Folds every audit event for a trade (stored by AuditEventConsumer)
// *          into the current state, recreating the trade's snapshot without
// *          touching the `trades` table.
// * HOW:     Reads AuditLogEntry rows ordered by occurredAt ASC and applies
// *          events sequentially:
// *            TRADE_CREATED  → state = afterData
// *            TRADE_UPDATED  → state = afterData
// *            TRADE_CANCELLED → state = null
// *          Returns Optional.empty() if no events exist OR the last event
// *          was a cancellation.
// * WHY:     This is the payoff of the immutable event log: if the `trades`
// *          table is ever corrupted, dropped, or mis-migrated you can replay
// *          from offset 0 and reconstruct every trade's full history.
// *          Auditors accept this because the events ARE the source of truth;
// *          the table is just a cached projection.
// * OBSERVE: POST a trade, update it, call rebuild("tradeRef") — returns the
// *          updated snapshot. Cancel the trade, call rebuild again — returns
// *          Optional.empty(). The `trades` table was never consulted.
// *
// * GOTCHA:  Order by occurredAt (the Kafka event timestamp), NOT by eventId.
// *          UUIDs (v4) are random — ordering by them gives a random sequence
// *          and the fold will produce garbage state.
// * ============================================================================
// */
//@Service
//public class TradeAggregator {
//
//    private final AuditLogRepository auditRepo;
//
//    public TradeAggregator(AuditLogRepository auditRepo) {
//        this.auditRepo = auditRepo;
//    }
//
//    /**
//     * Rebuild the current state of a trade by folding its event log.
//     *
//     * @param tradeRef the trade reference (e.g. "EQU-20260603-0001")
//     * @return the folded JsonNode snapshot, or Optional.empty() if no events
//     *         exist or the trade was cancelled
//     */
//    public Optional<JsonNode> rebuild(String tradeRef) {
//        List<AuditLogEntry> events = auditRepo.findByTradeRefOrderByOccurredAtAsc(tradeRef);
//
//        if (events.isEmpty()) {
//            return Optional.empty();
//        }
//
//        JsonNode state = null;
//
//        for (AuditLogEntry e : events) {
//            switch (TradeEvent.EventType.valueOf(e.getOperation())) {
//                case TRADE_CREATED, TRADE_UPDATED -> state = e.getAfterData();
//                case TRADE_CANCELLED              -> state = null;
//            }
//        }
//
//        return Optional.ofNullable(state);
//    }
//}
