package com.dbtraining.reconx.service;

import com.dbtraining.reconx.exception.InvalidTradeException;
import com.dbtraining.reconx.repository.InstrumentRepository;
import com.dbtraining.reconx.repository.entity.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * @Cacheable on findBySymbol (cache name "instruments").
 * TTL configured in application.yml (caffeine spec).
 *
 * Symbol lookup is hot — most requests touch the cache, not the DB.
 */
@Service
public class InstrumentService {

    // Added for Day6
    private static final Logger log = LoggerFactory.getLogger(InstrumentService.class);

    private final InstrumentRepository repo;

    public InstrumentService(InstrumentRepository repo) { this.repo = repo; }

//    @Cacheable("instruments")
    @Cacheable(value = "instruments", key = "#symbol")
    public Instrument findBySymbol(String symbol) {
        log.info("DB hit for {}", symbol);
        return repo.findBySymbol(symbol)
                .orElseThrow(() -> new InvalidTradeException("Unknown instrument symbol: " + symbol));
    }
}
