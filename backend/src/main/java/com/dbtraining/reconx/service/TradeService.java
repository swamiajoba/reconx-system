package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.TradeRequest;
import com.dbtraining.reconx.exception.DuplicateTradeRefException;
import com.dbtraining.reconx.exception.TradeNotFoundException;
import com.dbtraining.reconx.kafka.TradeEventProducer;
import com.dbtraining.reconx.observability.TradeMetrics;
import com.dbtraining.reconx.repository.CounterpartyRepository;
import com.dbtraining.reconx.repository.InstrumentRepository;
import com.dbtraining.reconx.repository.TradeRepository;
import com.dbtraining.reconx.repository.entity.Trade;
import com.dbtraining.reconx.dto.TradeEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static com.dbtraining.reconx.repository.TradeSpecifications.*;

/**
 * ============================================================================
 * TradeService.create (POST endpoint backing)
 * update
 * updateStatus (PATCH)
 * softDelete
 * increments trade_created_total Counter on create
 * publishes TradeEvent on every state change
 * TICKET-ADV055/TICKET-ADV056 — list() uses Specifications + filter query
 * ============================================================================
 */
@Service
@Transactional
public class TradeService {

    private final TradeRepository tradeRepo;
    private final CounterpartyRepository cpRepo;
    private final InstrumentRepository instRepo;
    private final TradeEventProducer events;
    private final TradeMetrics metrics;

    public TradeService(TradeRepository tradeRepo,
                        CounterpartyRepository cpRepo,
                        InstrumentRepository instRepo,
                        TradeEventProducer events,
                        TradeMetrics metrics) {
        this.tradeRepo = tradeRepo;
        this.cpRepo = cpRepo;
        this.instRepo = instRepo;
        this.events = events;
        this.metrics = metrics;
    }

    public Trade create(TradeRequest req, String actor) {
        tradeRepo.findByTradeRef(req.tradeRef())
                .ifPresent(t -> { throw new DuplicateTradeRefException(req.tradeRef()); });

        Trade t = new Trade();
        t.setTradeRef(req.tradeRef());
        t.setInstrument(instRepo.findById(req.instrumentId())
                .orElseThrow(() -> new TradeNotFoundException("instrument " + req.instrumentId())));
        t.setCounterparty(cpRepo.findById(req.counterpartyId())
                .orElseThrow(() -> new TradeNotFoundException("counterparty " + req.counterpartyId())));
        t.setAssetClass(req.assetClass());
        t.setSide(req.side());
        t.setQuantity(req.quantity());
        t.setPrice(req.price());
        t.setTradeDate(req.tradeDate());
        t.setStatus("PENDING");

        Trade saved = tradeRepo.save(t);
        metrics.incrementTradeCreated();
        metrics.recordTradeValue(saved.getQuantity().multiply(saved.getPrice()).doubleValue());
        events.publish(new TradeEvent(UUID.randomUUID(), saved.getTradeRef(),
                TradeEvent.EventType.TRADE_CREATED, Instant.now(), actor, null, "created"));
        return saved;
    }

    public Trade update(Long id, TradeRequest req, String actor) {
        Trade t = tradeRepo.findById(id)
                .orElseThrow(() -> new TradeNotFoundException("id=" + id));
        t.setQuantity(req.quantity());
        t.setPrice(req.price());
        t.setSide(req.side());
        t.setTradeDate(req.tradeDate());
        Trade saved = tradeRepo.save(t);
        events.publish(new TradeEvent(UUID.randomUUID(), saved.getTradeRef(),
                TradeEvent.EventType.TRADE_UPDATED, Instant.now(), actor, "before", "after"));
        return saved;
    }

    public Trade updateStatus(Long id, String status, String actor) {
        Trade t = tradeRepo.findById(id)
                .orElseThrow(() -> new TradeNotFoundException("id=" + id));
        t.setStatus(status);
        Trade saved = tradeRepo.save(t);
        events.publish(new TradeEvent(UUID.randomUUID(), saved.getTradeRef(),
                TradeEvent.EventType.TRADE_UPDATED, Instant.now(), actor, null, status));
        return saved;
    }

    public void softDelete(Long id, String actor) {
        Trade t = tradeRepo.findById(id)
                .orElseThrow(() -> new TradeNotFoundException("id=" + id));
        t.softDelete();
        tradeRepo.save(t);
        events.publish(new TradeEvent(UUID.randomUUID(), t.getTradeRef(),
                TradeEvent.EventType.TRADE_CANCELLED, Instant.now(), actor, null, null));
    }

    @Transactional(readOnly = true)
    public Page<Trade> list(LocalDate from, LocalDate to, String status, Long counterpartyId, Pageable pageable) {
        Specification<Trade> spec = Specification
                .where(hasStatus(status))
                .and(tradeDateBetween(from, to))
                .and(hasCounterparty(counterpartyId));
        return tradeRepo.findAll(spec, pageable);
    }
}
