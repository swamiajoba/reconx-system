package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.repository.entity.Instrument;
import com.dbtraining.reconx.service.InstrumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/instruments")
@Tag(name = "instruments", description = "Instrument operations")
@SecurityRequirement(name = "bearerAuth")
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @GetMapping("/{symbol}")
    @Operation(summary = "Fetch instruments by symbol")
    public ResponseEntity<Instrument> getInstrumentBySymbol(@PathVariable String symbol){
        Instrument instrument = instrumentService.findBySymbol(symbol);
        return ResponseEntity.ok(instrument);
    }
}
