package com.dbtraining.reconx.sse;

import com.dbtraining.reconx.dto.TradeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds active SSE connections for the trades stream and broadcasts
 * a TradeResponse to all of them whenever TradeService mutates a trade.
 */
@Component
public class TradeStreamBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(TradeStreamBroadcaster.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((ex) -> emitters.remove(emitter));
        emitters.add(emitter);
        return emitter;
    }

    public void broadcast(TradeResponse trade) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(trade));
            } catch (Exception ex) {
                log.debug("Dropping dead SSE emitter: {}", ex.getMessage());
                emitters.remove(emitter);
            }
        }
    }
}