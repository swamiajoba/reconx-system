package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.sse.TradeStreamBroadcaster;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * GET /api/v1/trades/stream — SSE endpoint consumed by the frontend's
 * useTradeStream() hook via EventSource.
 */
@RestController
@RequestMapping("/v1/trades")
@Tag(name = "trades")
public class TradeStreamController {

    private final TradeStreamBroadcaster broadcaster;

    public TradeStreamController(TradeStreamBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping("/stream")
    @Operation(summary = "Live trade stream (SSE)")
    public SseEmitter stream() {
        return broadcaster.subscribe();
    }
}
