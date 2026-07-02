# TrainersGuide — Day 9: React Testing + Apache Kafka Deep Dive

> **Student-facing equivalent:** [student-guides/day9/README.md](../../student-guides/day9/README.md)
> **Exercises:** Day 9 · TICKET-ADV128 – TICKET-ADV145 (18 hands-on exercises across the workshop blocks)
> **Theme:** React Testing + ★ Apache Kafka Deep Dive — RTL + Jest labs in the AM, plus an event-driven backbone built on Apache Kafka with retry, DLQ, replay, and event sourcing in the PM.

---

## Day at a glance

| #    | Block | Exercises | What students produce |
|------|-------|-----------|----------------------|
| 1 | Standup + Day-8 holdover unblock | — | React forms + table from Day 8 green for everyone |
| 2 | AM React Testing mini-lecture — RTL + Jest fundamentals | — | Live recap: render/screen/userEvent, query priority, async findBy, mocking fetch/MSW, snapshot vs behavioural tests |
| 3 | **Workshop 9 — Kafka Multi-Topic + DLQ + Retry + Event Sourcing (Part A: Topics + producer + payload)** | TICKET-ADV128 – TICKET-ADV130 | 3 Kafka topics created, `TradeEventProducer` publishing, `TradeEvent` record |
| 4 | **★ Apache Kafka Deep Dive (2 hrs)** — brokers, partitions, consumer groups; error handling, DLQ, retry patterns; event sourcing for audit trail; Kafka + Prometheus metrics | — | Whiteboard notes: partition/consumer-group math, DLQ flow, event-sourced rebuild, lag/throughput dashboards |
| 5 | **Workshop 9 (Part B: Consumers — recon, audit, alert)** | TICKET-ADV131 – TICKET-ADV133 | 3 `@KafkaListener` consumers wired |
| 6 | **Workshop 9 (Part C: DLQ + retry + replay + event sourcing)** | TICKET-ADV134 – TICKET-ADV138 | DLQ topics live, exponential backoff configured, `GET /audit/trades/{ref}/events` returns rebuilt history |
| 7 | **Workshop 9 (Part D: Metrics + Grafana + integration tests + AI-assisted config review)** | TICKET-ADV139 – TICKET-ADV145 | Consumer lag panel + DLQ alert in Grafana, Testcontainers Kafka green, Claude-reviewed config |
| 8 | End-of-day debrief | — | Tomorrow's preview (Docker, CI, demo) |

**Day-9 follows the TOC, not the delivery-plan doc.** If a student waves the
delivery-plan doc and asks about Kafka Streams, KSQL, Schema Registry with
Avro, or Confluent Cloud — those are **deliberately out of scope** for Day 9.
The whole point of today is "a small number of topics, used correctly, with
retry/DLQ/observability that would survive a production incident review".
Don't get pulled into KStreams. Don't get pulled into Avro. We're using JSON
and `StringSerializer`/`JsonSerializer` deliberately.

---

## Pre-day instructor prep

The evening before Day 9:

- [ ] Bring up the full local stack and confirm Kafka is healthy:
      `docker compose up -d zookeeper kafka kafdrop` then visit Kafdrop at
      `http://localhost:9000`. You should see the broker listed and zero topics.
      You will demo Kafdrop **a lot** today — keep the tab pinned.
- [ ] Decide upfront whether topics are **created by `KafkaAdmin` on startup**
      (the path the TICKET-ADV128 reference solution takes — `@Bean NewTopic`) or
      **auto-created** by the broker on first produce. The starter assumes the
      `@Bean NewTopic` path. Stick with it unless a team has a strong reason.
      If you let teams diverge, you will spend the afternoon debugging
      "UNKNOWN_TOPIC_OR_PARTITION" on three laptops at once.
- [ ] Run the Testcontainers Kafka integration test from this repo once on
      your machine: `cd backend && ./mvnw -pl . -Dtest=KafkaPipelineIT verify`.
      First run pulls `confluentinc/cp-kafka:7.6.0` (~600 MB). Pre-pulling it
      saves 10 minutes per team on Workshop 9 Part D. Pre-pull on student laptops at
      the morning standup: `docker pull confluentinc/cp-kafka:7.6.0`.
- [ ] Verify Micrometer Kafka client metrics are wired. In
      `backend/src/main/resources/application.yml` confirm
      `management.metrics.binders.kafka.enabled: true` AND check
      `curl -s http://localhost:8080/actuator/prometheus | grep kafka_consumer`.
      If you see no `kafka_consumer_*` metrics, the binder isn't picking up
      the consumer factory — fix before Workshop 9 Part D or TICKET-ADV140 – TICKET-ADV142 panels will be
      empty.
- [ ] Pre-load a sample `TradeEvent` JSON payload in Postman / Bruno so you
      can fire a manual `POST /api/v1/trades` and watch the event flow
      through Kafdrop → recon consumer → audit table live. This is the
      single best demo of the day; have it ready.
- [ ] Re-read the student Day-9 README side-by-side with this one.
      Acceptance criteria live there; the answers live here.
- [ ] Have a **partition-vs-consumer-group whiteboard sketch** ready. You
      will draw it twice (once in the AM, once when TICKET-ADV131 confusion peaks).
      One box per partition, one consumer per group per partition, arrows
      showing "key → hash → partition".
- [ ] Decide your stance on **synchronous vs async producer**. The reference
      uses `KafkaTemplate.send(...)` fire-and-forget. If a team asks "should
      we `.get()` on the future", the answer is "not in the hot path, yes in
      `@Transactional` outbox patterns — out of scope today".

---

## Workshop 9 — Kafka Multi-Topic + DLQ + Retry + Event Sourcing — Part A: Topics + producer + payload (TICKET-ADV128 – TICKET-ADV130)

The shape: declare the three topics as Spring beans so `KafkaAdmin` creates
them on boot, build a producer service that publishes `TradeEvent`s keyed by
`tradeRef`, and define the event payload as an immutable Java record.

### TICKET-ADV128 — Kafka topics (trade-events, recon-results, system-alerts)

**What good looks like:** three `NewTopic` beans in `KafkaTopicsConfig`. On
boot, Kafdrop shows three topics with the right partition counts. No manual
`kafka-topics.sh` invocations.

**Common student blockers:**
- They create topics via `kafka-topics --create` in a shell script. Works
  locally, breaks in CI and on the demo laptop. Make them use `@Bean NewTopic`.
- They set replicas to 3. The local broker is single-node — produce will fail
  with `NOT_ENOUGH_REPLICAS`. **Use `replicas(1)`** and call out that prod
  would be 3.
- They forget the partition count differences (3 / 2 / 1) and use 1 everywhere.
  Then TICKET-ADV131 consumer-group rebalancing demo is boring because there's nothing
  to rebalance.

**Unblocking ladder:**
1. **Nudge:** "How does Kafka know the topic exists when the producer first sends to it?"
2. **Hint:** "Spring has a `KafkaAdmin` bean that picks up `NewTopic` beans on startup. What does the bean look like?"
3. **Reveal:** Show the config below; restart the app; show Kafdrop now has 3 topics.

<details>
<summary>Full reference solution — KafkaTopicsConfig.java (TICKET-ADV128)</summary>

```java
package com.dbtraining.reconx.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares Kafka topics as Spring beans. KafkaAdmin (auto-configured by
 * spring-kafka) picks these up at startup and creates them on the broker
 * if they do not yet exist. This makes the topic layout reproducible
 * across dev / CI / demo machines without shell scripts.
 *
 * Partition count rationale:
 *   trade-events    : 3 — moderate volume, parallelism for the recon consumer group
 *   recon-results   : 2 — lower volume, but still parallel for downstream consumers
 *   system-alerts   : 1 — strictly ordered global event log
 *
 * Replicas = 1 because this is a single-broker dev / training cluster.
 * In production you would set replicas(3) and min.insync.replicas=2.
 */
@Configuration
public class KafkaTopicsConfig {

    public static final String TRADE_EVENTS         = "trade-events";
    public static final String TRADE_EVENTS_DLQ     = "trade-events-dlq";
    public static final String RECON_RESULTS        = "recon-results";
    public static final String SYSTEM_ALERTS        = "system-alerts";

    @Bean
    public NewTopic tradeEventsTopic() {
        return TopicBuilder.name(TRADE_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic tradeEventsDlqTopic() {
        return TopicBuilder.name(TRADE_EVENTS_DLQ)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic reconResultsTopic() {
        return TopicBuilder.name(RECON_RESULTS)
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic systemAlertsTopic() {
        return TopicBuilder.name(SYSTEM_ALERTS)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
```

</details>

**Talking point:** "Why three topics, not one with an `eventType` field?"
Each topic is a *contract* with a different consumer audience. The recon
consumer doesn't care about alerts and vice versa. Coupling them into one
topic means every consumer has to subscribe to every event and filter.
Separation also lets you set different retention, partition counts, and
ACLs per topic. Topics are cheap; the wrong topic boundary is expensive.

**▶ Run the project — verify TICKET-ADV128 end-to-end**

Boot the broker + the app, then confirm all four topics exist with the right partition counts.

```bash
docker compose up -d kafka zookeeper kafdrop
./mvnw spring-boot:run
# Kafdrop UI: http://localhost:9000
```

**Observe:**

- `trade-events` listed with 3 partitions, replication factor 1.
- `trade-events-dlq` listed with 3 partitions (must match the main topic so the DLQ recoverer preserves partition numbers).
- `recon-results` with 2 partitions; `system-alerts` with 1 partition.
- Failure signal: a topic missing or showing the wrong partition count means `KafkaAdmin` did not pick up the `@Bean` — re-check `@Profile("!dev & !test")` and that the class is on the classpath.

### TICKET-ADV129 — TradeEventProducer

**What good looks like:** a `@Component` that takes a `KafkaTemplate<String, TradeEvent>`
and exposes `publish(TradeEvent)`. It sets the message key to `event.tradeRef()`
so all events for the same trade go to the same partition (preserving order
**per trade**, which is the only ordering guarantee we need).

**Common student blockers:**
- They omit the key, so events for the same trade scatter across partitions.
  Then the recon consumer sees `TRADE_UPDATED` before `TRADE_CREATED`. Ugly.
- They wire `KafkaTemplate<String, Object>` and lose type safety. Push for the
  generic `<String, TradeEvent>` form so the serialiser is unambiguous.
- They call `.get()` on the `CompletableFuture` returned by `send()` in the
  REST controller path, adding a 50–200 ms blocking wait per request.

<details>
<summary>Full reference solution — TradeEventProducer.java (TICKET-ADV129)</summary>

```java
package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import static com.dbtraining.reconx.config.KafkaTopicsConfig.TRADE_EVENTS;

@Component
public class TradeEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TradeEventProducer.class);

    private final KafkaTemplate<String, TradeEvent> template;

    public TradeEventProducer(KafkaTemplate<String, TradeEvent> template) {
        this.template = template;
    }

    /**
     * Publishes a TradeEvent to the trade-events topic.
     * Key = tradeRef so all events for one trade land on the same partition.
     * Returns immediately; failure handling is via the producer listener
     * configured on the KafkaTemplate (see KafkaProducerConfig).
     */
    public void publish(TradeEvent event) {
        template.send(TRADE_EVENTS, event.tradeRef(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish TradeEvent {} for trade {}",
                                event.eventId(), event.tradeRef(), ex);
                    } else {
                        log.debug("Published {} for trade {} to partition {} offset {}",
                                event.eventType(),
                                event.tradeRef(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
```

</details>

**Talking point:** "Why is the key `tradeRef` and not `eventId`?" Partition
assignment is `hash(key) % partitionCount`. Same key → same partition →
preserved order. We want per-trade ordering (so `UPDATED` follows `CREATED`),
not global ordering (which would require a single-partition topic and kill
throughput). `eventId` as key would scatter every event uniformly — fine if
you don't care about per-aggregate ordering, fatal if you do.

**▶ Run the project — verify TICKET-ADV129 end-to-end**

Trigger the producer by posting a new trade, then inspect the message in Kafdrop.

```bash
curl -X POST http://localhost:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeRef":"T-ADV129-A","instrument":"USD/EUR","notional":1000000,"side":"BUY"}'
# Kafdrop → topics → trade-events → Messages
```

**Observe:**

- A new event lands on a partition determined by the `tradeRef` hash (rerun with a different `tradeRef` and verify the partition can change).
- Sending another event with the same `tradeRef` lands on the same partition (per-trade ordering preserved).
- Failure signal: a `whenComplete` log line with an exception means the producer template never reached the broker — check `spring.kafka.bootstrap-servers`.

### TICKET-ADV130 — TradeEvent payload

**What good looks like:** an immutable Java record with the event metadata
(`eventId`, `tradeRef`, `eventType`, `timestamp`) and the trade snapshot
(`before`, `after`) as `JsonNode` blobs. The record is `Serializable` via
Jackson out of the box; no custom `Serializer` needed (spring-kafka uses
`JsonSerializer`).

**Common student blockers:**
- They include the full JPA `Trade` entity in `before`/`after` and serialise
  with `@OneToMany` lazy collections still attached. Result: Jackson invokes
  the proxy, hits the closed session, blows up with `LazyInitializationException`.
- They use `LocalDateTime` instead of `Instant`. Now the timezone-of-publisher
  matters and the consumer guesses. Use `Instant` (UTC) for event timestamps.
- They make the record mutable by adding setters. Drop them — events are
  immutable by definition.

<details>
<summary>Full reference solution — TradeEvent.java (TICKET-ADV130)</summary>

```java
package com.dbtraining.reconx.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable Kafka event payload for the trade-events topic.
 *
 * - eventId    : unique per emission; survives retries/replays so consumers can dedupe.
 * - tradeRef   : the aggregate id; used as the Kafka message key.
 * - eventType  : enum, drives consumer-side switching.
 * - timestamp  : UTC instant when the event was emitted, NOT when the trade booked.
 * - before     : trade snapshot prior to the change (null on TRADE_CREATED).
 * - after      : trade snapshot after the change (null on TRADE_CANCELLED).
 *
 * `before`/`after` are JsonNode rather than `Trade` so we can evolve the trade
 * schema without breaking the audit log; the event history is the immutable
 * source of truth.
 */
public record TradeEvent(
        UUID eventId,
        String tradeRef,
        EventType eventType,
        Instant timestamp,
        JsonNode before,
        JsonNode after
) {
    public enum EventType {
        TRADE_CREATED,
        TRADE_UPDATED,
        TRADE_CANCELLED
    }

    public static TradeEvent created(String tradeRef, JsonNode after) {
        return new TradeEvent(UUID.randomUUID(), tradeRef,
                EventType.TRADE_CREATED, Instant.now(), null, after);
    }

    public static TradeEvent updated(String tradeRef, JsonNode before, JsonNode after) {
        return new TradeEvent(UUID.randomUUID(), tradeRef,
                EventType.TRADE_UPDATED, Instant.now(), before, after);
    }

    public static TradeEvent cancelled(String tradeRef, JsonNode before) {
        return new TradeEvent(UUID.randomUUID(), tradeRef,
                EventType.TRADE_CANCELLED, Instant.now(), before, null);
    }
}
```

</details>

**Talking point:** "Why both `before` and `after`?" `after` alone is enough to
rebuild current state. `before` lets a downstream consumer (and the audit UI)
*describe the change* — "price changed from 245.50 to 246.00" — without
having to fetch the prior event itself. It costs ~1 KB per event; the audit
UX win is worth it.

**▶ Run the project — verify TICKET-ADV130 end-to-end**

Post a trade and read the raw payload off the wire to confirm the JSON contract.

```bash
curl -X POST http://localhost:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeRef":"T-ADV130-B","instrument":"GBP/USD","notional":500000,"side":"SELL"}'
# Kafdrop → trade-events → open the latest message
```

**Observe:**

- Payload is JSON containing `tradeRef`, `eventType`, `before`, `after`, `actor`, `timestamp`.
- `before` is `null` on create; `after` carries the full trade snapshot.
- Failure signal: payload printed as `[B@...` (byte array toString) means the JSON serialiser is not configured — re-check `value.serializer = JsonSerializer.class` on the `ProducerFactory`.

---

## Workshop 9 — Part B: Consumers — recon, audit, alert (TICKET-ADV131 – TICKET-ADV133)

Three `@KafkaListener` consumers, each in its own consumer group. Different
groups means each consumer reads **every** event independently — exactly the
fan-out we want.

### TICKET-ADV131 — ReconciliationConsumer

**What good looks like:** a `@KafkaListener(topics="trade-events", groupId="recon-service")`
method on a `@Component`. The method calls `reconEngine.scheduleRecon(tradeRef)`
for every event. Boots cleanly; consumer registers with the broker; Kafdrop
shows it under "Consumers".

**Common student blockers:**
- They throw a `RuntimeException` from the listener for the first failure.
  Without a `DefaultErrorHandler` (TICKET-ADV134), the container retries the message
  *forever*. The same record loops. Logs explode.
- They mark the listener method `@Async`. The container is *already* running
  on a dedicated poll thread; `@Async` adds confusion without help.
- They use `groupId="default"` (or omit it). Every consumer ends up in the
  same group → only one consumer instance gets each message → fan-out broken.

**Unblocking ladder:**
1. **Nudge:** "Two different consumers, two different groups, same topic — what does each consumer see?"
2. **Hint:** "Consumer groups in Kafka are the unit of parallelism *and* of fan-out. Each group gets a full copy of the stream."
3. **Reveal:** Draw on the whiteboard — Topic `trade-events` → arrows to `recon-service` group + `audit-service` group + `alert-service` group. Each arrow is the full stream.

<details>
<summary>Full reference solution — ReconciliationConsumer.java (TICKET-ADV131)</summary>

```java
package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.service.ReconciliationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationConsumer.class);

    private final ReconciliationEngine reconEngine;

    public ReconciliationConsumer(ReconciliationEngine reconEngine) {
        this.reconEngine = reconEngine;
    }

    @KafkaListener(
            topics = "trade-events",
            groupId = "recon-service",
            containerFactory = "tradeEventListenerContainerFactory"
    )
    public void onTradeEvent(TradeEvent event) {
        log.debug("Recon: handling {} for {}", event.eventType(), event.tradeRef());

        switch (event.eventType()) {
            case TRADE_CREATED, TRADE_UPDATED -> reconEngine.scheduleRecon(event.tradeRef());
            case TRADE_CANCELLED              -> reconEngine.cancelPendingRecon(event.tradeRef());
        }
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV131 end-to-end**

POST a trade and watch the recon consumer group catch up.

```bash
curl -X POST http://localhost:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeRef":"T-ADV131-C","instrument":"USD/JPY","notional":750000,"side":"BUY"}'
# Kafdrop → Consumer Groups → recon-service
```

**Observe:**

- Backend log line "ReconciliationConsumer received TradeEvent..." with the matching `tradeRef`.
- Kafdrop shows the `recon-service` consumer group with **lag = 0** across all 3 partitions of `trade-events`.
- Failure signal: lag stays > 0 — consumer thread is blocked or `containerFactory` is misconfigured. Check the `tradeEventListenerContainerFactory` bean and that `groupId = "recon-service"`.

### TICKET-ADV132 — AuditEventConsumer

Persists every `TradeEvent` to the `audit_log` table built on Day 1. This is
the foundation of the event-sourcing rebuild in TICKET-ADV137.

<details>
<summary>Full reference solution — AuditEventConsumer.java (TICKET-ADV132)</summary>

```java
package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.AuditLogRepository;
import com.dbtraining.reconx.model.AuditLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditLogRepository auditRepo;

    public AuditEventConsumer(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    @KafkaListener(
            topics = "trade-events",
            groupId = "audit-service",
            containerFactory = "tradeEventListenerContainerFactory"
    )
    @Transactional
    public void onTradeEvent(TradeEvent event) {
        AuditLogEntry entry = AuditLogEntry.builder()
                .eventId(event.eventId())
                .tradeRef(event.tradeRef())
                .operation(event.eventType().name())
                .beforeData(event.before())
                .afterData(event.after())
                .occurredAt(event.timestamp())
                .build();
        auditRepo.save(entry);
        log.debug("Audit: persisted event {} for trade {}",
                event.eventId(), event.tradeRef());
    }
}
```

</details>

**Talking point:** `@Transactional` on the listener method ties the DB write
to the Kafka offset commit (when `AckMode.RECORD` and a `ChainedKafkaTransactionManager`
are configured). Without that, you can persist the row, crash before the offset
commits, and reprocess the same event → duplicate audit rows. Mention this is
"out of scope today, ask me on Day 10 if you want the full outbox-pattern story".

**▶ Run the project — verify TICKET-ADV132 end-to-end**

POST a trade, then read the `audit_log_entries` table to confirm the audit consumer ran independently of recon.

```bash
curl -X POST http://localhost:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeRef":"T-ADV132-D","instrument":"EUR/CHF","notional":250000,"side":"SELL"}'
curl -s 'http://localhost:8080/actuator/health'
# Inspect DB: psql or H2 console — select count(*) from audit_log_entries;
```

**Observe:**

- AuditEventConsumer log line fires once per event.
- A new row appears in `audit_log_entries` with the event payload.
- Kafdrop → Consumer Groups shows **both** `recon-service` and `audit-service` at offset N (different groups, each saw the same event).
- Failure signal: only one group visible — both consumers are sharing a `groupId`. Distinct `groupId` strings are mandatory for fan-out.

### TICKET-ADV133 — AlertConsumer

Listens to `system-alerts`, logs at WARN, and optionally posts to a Slack webhook.
The starter ships with a `NoopAlertSink` that just logs; teams can wire a real
sink if they want.

<details>
<summary>Full reference solution — AlertConsumer.java (TICKET-ADV133)</summary>

```java
package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.SystemAlert;
import com.dbtraining.reconx.service.AlertSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertConsumer.class);

    private final AlertSink sink;

    public AlertConsumer(AlertSink sink) {
        this.sink = sink;
    }

    @KafkaListener(
            topics = "system-alerts",
            groupId = "alert-service",
            containerFactory = "systemAlertListenerContainerFactory"
    )
    public void onAlert(SystemAlert alert) {
        log.warn("ALERT [{}] {} — {}", alert.severity(), alert.code(), alert.message());
        sink.notify(alert);
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV133 end-to-end**

POST a trade and confirm three distinct consumer groups are now visible.

```bash
curl -X POST http://localhost:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeRef":"T-ADV133-E","instrument":"AUD/USD","notional":600000,"side":"BUY"}'
# Kafdrop → Consumer Groups
```

**Observe:**

- AlertConsumer log line `ALERT: ...` fires.
- Kafdrop → Consumer Groups lists **three** groups: `recon-service`, `audit-service`, `alert-service`, each at offset N with lag 0.
- Failure signal: only two groups visible — the alert consumer never ran. Verify the `@KafkaListener` annotation lives on a Spring-managed `@Component` and the class is component-scanned.

---

## Workshop 9 — Part C: DLQ + retry + replay + event sourcing (TICKET-ADV134 – TICKET-ADV138)

This is the meatiest block of the day. By the end of it, a deliberately-thrown
exception in `ReconciliationConsumer` should retry 3 times with exponential
backoff and then land in `trade-events-dlq`, the DLQ consumer should log it,
and an admin REST endpoint should be able to *replay* it. Plus the
event-sourcing path: rebuild any trade's state from its event history.

### TICKET-ADV134 — Dead-Letter Queue wiring

**What good looks like:** a single `DefaultErrorHandler` bean built around
`DeadLetterPublishingRecoverer`. Wired into the listener container factory so
every `@KafkaListener` using that factory gets DLQ behaviour for free.

**Common student blockers:**
- They write a `try/catch` *inside* the listener method and manually publish
  to the DLQ. Works but bypasses the retry/backoff machinery and is brittle.
  Push for the framework path.
- They forget to create the DLQ *topic* (with the same partition count) — Kafka
  rejects the recoverer's produce with `UNKNOWN_TOPIC_OR_PARTITION`. Confirm
  TICKET-ADV128 declared `trade-events-dlq` as a `NewTopic`.
- They configure the recoverer to write to `topic + "-dlq"` but the partition
  resolver maps to a partition that doesn't exist (DLQ has fewer partitions).
  Use the same partition count; the reference solution does.

<details>
<summary>Full reference solution — KafkaErrorHandlerConfig.java (TICKET-ADV134 + TICKET-ADV135)</summary>

```java
package com.dbtraining.reconx.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaErrorHandlerConfig {

    /**
     * DefaultErrorHandler is the spring-kafka 3.x recommendation
     * (replaces SeekToCurrentErrorHandler).
     *
     * Behaviour:
     *  1. Listener throws.
     *  2. Container catches, calls handler.
     *  3. Handler waits (ExponentialBackOff) and seeks back the failed record.
     *  4. After backoff intervals are exhausted, the recoverer publishes to
     *     <originalTopic>-dlq on the same partition number.
     *  5. The offset of the *original* record is then committed so the
     *     consumer moves on.
     *
     * ExponentialBackOff(1000, 2.0):
     *   attempt 1: wait 1s
     *   attempt 2: wait 2s
     *   attempt 3: wait 4s    — then DLQ
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (record, exception) -> new TopicPartition(
                        record.topic() + "-dlq",
                        record.partition()
                )
        );

        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxElapsedTime(8_000L); // ~3 retries: 1s + 2s + 4s

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // Don't retry deserialisation failures — they will never succeed.
        handler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class,
                IllegalArgumentException.class
        );

        return handler;
    }
}
```

</details>

**Talking point:** "What's the difference between a *DLQ topic* and a
*retry topic*?" A DLQ is a terminal queue — messages there require human or
automated intervention to come back. A retry topic (think "retry-5s",
"retry-30s") is a staged re-attempt model where you re-publish to a delayed
topic and re-consume later; useful when retries should *not* block the main
partition. Spring's `RetryableTopic` annotation builds this for you. We're
using the simpler in-memory backoff because today our retries are sub-10s
and we don't need to free the partition; in production with 1-hour retries,
you'd use `RetryableTopic`.

**▶ Run the project — verify TICKET-ADV134 end-to-end**

Produce a deliberately malformed event via Kafdrop's "Produce" tab (or a curl against a debug endpoint) and watch the DLQ catch it.

```bash
# Kafdrop → trade-events → Produce → send raw text "this-is-not-json"
# Then watch backend logs and trade-events-dlq
```

**Observe:**

- Backend log shows 3 retry attempts on the same offset.
- After retries exhaust, the message lands on `trade-events-dlq` on the **same partition** it arrived on in `trade-events`.
- Kafdrop → `trade-events-dlq` shows 1 new message; the headers (`kafka_dlt-*`) carry the original topic/partition/offset.
- Failure signal: message vanishes silently — DLQ topic missing or partition counts mismatched. Re-check TICKET-ADV128.

### TICKET-ADV135 — Retry strategy (3 retries + exponential backoff)

Covered in the TICKET-ADV134 reference above. Spend the time here on the talking
point, not the code.

**Talking point:** Pin down with the room what *backoff* actually does:
- **Fixed backoff**: wait N seconds between every retry — predictable but
  can hammer a slow downstream.
- **Exponential backoff**: 1s, 2s, 4s, 8s… — gives slow downstreams time to
  recover.
- **Exponential with jitter**: same but randomised — prevents thundering
  herd when 1000 consumers all retry at second 4. Spring Kafka doesn't add
  jitter by default; that's a known production gap. Call this out.

**▶ Run the project — verify TICKET-ADV135 end-to-end**

Produce a malformed event again and time the retry log lines.

```bash
# Kafdrop → trade-events → Produce → "this-is-not-json"
# Then: tail backend logs, note timestamps on each retry log line
```

**Observe:**

- Retry 1 fires at ~t+1s, retry 2 at ~t+3s (cumulative), retry 3 at ~t+7s.
- DLQ publish happens after ~7s total elapsed.
- Failure signal: retries fire back-to-back with no delay — the `ExponentialBackOff` instance is not being used. Confirm `new ExponentialBackOff(1000L, 2.0)` and that `setMaxElapsedTime(8_000L)` is set.

### TICKET-ADV136 — DLQ consumer + replay endpoint

**What good looks like:** a `@KafkaListener` on `trade-events-dlq` that logs
each DLQ message at ERROR with full context, plus a `POST /api/v1/admin/dlq/replay`
endpoint that re-publishes the DLQ message back to the main topic.

**Common student blockers:**
- The replay endpoint accepts no parameters and replays *all* DLQ messages.
  Dangerous — if the underlying bug isn't fixed, they'll re-DLQ. Require an
  `eventId` query parameter to replay a single message, or `dryRun=true` to
  list what would be replayed.
- They expose the endpoint unauthenticated. RBAC matters — restrict to `ADMIN`.

<details>
<summary>Full reference solution — DlqConsumer + DlqAdminController (TICKET-ADV136)</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/kafka/DlqConsumer.java
package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.DlqMessageRepository;
import com.dbtraining.reconx.model.DlqMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    private final DlqMessageRepository repo;

    public DlqConsumer(DlqMessageRepository repo) {
        this.repo = repo;
    }

    @KafkaListener(
            topics = "trade-events-dlq",
            groupId = "dlq-monitor",
            containerFactory = "tradeEventListenerContainerFactory"
    )
    public void onDlqMessage(ConsumerRecord<String, TradeEvent> record,
                             @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exMsg) {
        TradeEvent event = record.value();
        log.error("DLQ: trade={} eventId={} reason={}",
                event.tradeRef(), event.eventId(), exMsg);

        repo.save(DlqMessage.builder()
                .eventId(event.eventId())
                .tradeRef(event.tradeRef())
                .originalTopic(record.topic().replace("-dlq", ""))
                .partition(record.partition())
                .offset(record.offset())
                .payload(event)
                .reason(exMsg)
                .firstSeen(Instant.now())
                .build());
    }
}
```

```java
// File: backend/src/main/java/com/dbtraining/reconx/controller/DlqAdminController.java
package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.kafka.TradeEventProducer;
import com.dbtraining.reconx.repository.DlqMessageRepository;
import com.dbtraining.reconx.model.DlqMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/dlq")
@PreAuthorize("hasRole('ADMIN')")
public class DlqAdminController {

    private final DlqMessageRepository repo;
    private final TradeEventProducer producer;

    public DlqAdminController(DlqMessageRepository repo, TradeEventProducer producer) {
        this.repo = repo;
        this.producer = producer;
    }

    @PostMapping("/replay")
    public ResponseEntity<Map<String, Object>> replay(
            @RequestParam UUID eventId,
            @RequestParam(defaultValue = "false") boolean dryRun) {

        DlqMessage msg = repo.findByEventId(eventId)
                .orElseThrow(() -> new IllegalArgumentException("No DLQ message: " + eventId));

        if (dryRun) {
            return ResponseEntity.ok(Map.of(
                    "dryRun", true,
                    "wouldReplayTo", msg.getOriginalTopic(),
                    "tradeRef", msg.getTradeRef()
            ));
        }

        producer.publish(msg.getPayload());
        repo.delete(msg);

        return ResponseEntity.ok(Map.of(
                "replayed", true,
                "eventId", eventId,
                "topic", msg.getOriginalTopic()
        ));
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV136 end-to-end**

After a message lands on the DLQ (from ADV134/ADV135), call the replay endpoint and confirm it round-trips back through recon successfully.

```bash
# First find a DLQ message
curl -s 'http://localhost:8080/api/v1/admin/dlq' | jq
# Replay it
curl -X POST http://localhost:8080/api/v1/dlq/replay/0/0
```

**Observe:**

- Replay endpoint returns 200 with `{"replayed":true, ...}`.
- The original DLQ row is marked replayed in the admin listing.
- The recon consumer picks up the republished event on `trade-events` and processes it cleanly (log line, no further retries).
- Failure signal: replay returns 200 but recon never logs — the producer is targeting the wrong topic; check the `KafkaTemplate.send(TRADE_EVENTS, ...)` call.

### TICKET-ADV137 — Event sourcing rebuild

**What good looks like:** a `TradeAggregator` service with a `rebuild(String tradeRef)`
method that reads every event from `audit_log` for that trade ordered by
timestamp and folds them into the current trade state.

**Common student blockers:**
- They rebuild on every read. Fine for 100-event histories, catastrophic for
  100k-event ones. Mention **snapshotting** (every N events, persist the
  rebuilt state) as the production pattern, but don't make them implement it.
- They naively use `eventId` to order, not `timestamp`. UUIDs aren't
  monotonic. Use the event timestamp (or, in production, Kafka offset within
  partition).
- They forget that `TRADE_CANCELLED` has `after = null` and NPE.

<details>
<summary>Full reference solution — TradeAggregator.java (TICKET-ADV137)</summary>

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.model.AuditLogEntry;
import com.dbtraining.reconx.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TradeAggregator {

    private final AuditLogRepository auditRepo;

    public TradeAggregator(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    /**
     * Rebuilds the *current* state of a trade by folding its event history.
     *
     * Each event's `after` JSON overrides the running state.
     * TRADE_CANCELLED produces an empty Optional.
     *
     * O(N) read per call — fine for our scale. In production:
     *   - snapshot the rebuilt state every K events
     *   - read snapshot + tail-events only
     */
    public Optional<JsonNode> rebuild(String tradeRef) {
        List<AuditLogEntry> events = auditRepo.findByTradeRefOrderByOccurredAtAsc(tradeRef);
        if (events.isEmpty()) {
            return Optional.empty();
        }

        JsonNode state = null;
        for (AuditLogEntry e : events) {
            switch (TradeEvent.EventType.valueOf(e.getOperation())) {
                case TRADE_CREATED, TRADE_UPDATED -> state = e.getAfterData();
                case TRADE_CANCELLED              -> state = null;
            }
        }
        return Optional.ofNullable(state);
    }
}
```

</details>

**Talking point:** "Why would I event-source if I already have the current
state in the `trades` table?" Three reasons: (1) audit — the regulator wants
to know what the trade looked like at 14:32 on Tuesday, (2) debugging — if
the trade is in a "wrong" state, you can replay history to see when it went
wrong, (3) projections — you can rebuild *new* read models from the same
events without touching the producer. Trade table = state; event log = truth.

**▶ Run the project — verify TICKET-ADV137 end-to-end**

Wipe the trades table, then rebuild from the event log and confirm every row comes back.

```bash
# Drop trades (psql)
psql -h localhost -U reconx -d reconx -c 'TRUNCATE trades CASCADE;'
# Trigger rebuild
curl -X POST http://localhost:8080/api/v1/admin/rebuild
psql -h localhost -U reconx -d reconx -c 'SELECT count(*) FROM trades;'
```

**Observe:**

- `trades` row count after rebuild matches the count before truncate.
- Backend log shows the aggregator folding events per `tradeRef`.
- Cancelled trades are absent (last event wins).
- Failure signal: count mismatch — the aggregator is reading events out of order. Confirm the repo call is `findByTradeRefOrderByEventTimestampAsc`.

### TICKET-ADV138 — Admin audit endpoint

`GET /api/v1/audit/trades/{tradeRef}/events` returns the event history,
oldest first.

<details>
<summary>Full reference solution — AuditController.java (TICKET-ADV138)</summary>

```java
package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.service.AuditQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit/trades")
@PreAuthorize("hasAnyRole('ADMIN','RECON_ANALYST')")
public class AuditController {

    private final AuditQueryService queryService;

    public AuditController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{tradeRef}/events")
    public List<TradeEvent> getEvents(@PathVariable String tradeRef) {
        return queryService.eventsForTrade(tradeRef);
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV138 end-to-end**

Read the audit log via the new endpoint.

```bash
curl -s http://localhost:8080/v1/audit | jq '.[0:5]'
curl -s http://localhost:8080/v1/audit/T-ADV132-D | jq
```

**Observe:**

- Response is a JSON array of audit rows ordered by `eventTimestamp` ascending.
- Each row shows `eventType`, `actor`, and a `before`/`after` snippet.
- Per-trade endpoint returns only rows for that `tradeRef`.
- Failure signal: 403 — the endpoint is missing an `@PreAuthorize` exemption for the admin role; check the Day 8 security config.

---

## Workshop 9 — Part D: Metrics + Grafana + integration tests + AI review (TICKET-ADV139 – TICKET-ADV145)

The shortest block by minutes, the highest leverage by impact. By the end,
Grafana has a "Kafka health" row, integration tests cover the happy + DLQ
paths against a real broker in a container, and Claude has reviewed the
consumer config.

### TICKET-ADV139 — Kafka metrics via Micrometer

**What good looks like:** `kafka_consumer_records_lag`, `kafka_consumer_records_consumed_total`,
`kafka_producer_record_send_total` all visible at `/actuator/prometheus`.
No custom code required; the Spring Kafka client metrics binder does it.

<details>
<summary>Full reference snippet — application.yml (TICKET-ADV139)</summary>

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: recon-service
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.dbtraining.reconx.dto"
        # Surfaces client-level metrics into Micrometer
        metric.reporters: io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  metrics:
    binders:
      kafka:
        enabled: true
    tags:
      application: ${spring.application.name}
```

Quick verification:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep -E '^kafka_(consumer|producer)' | head -20
```

You should see lines like:

```
kafka_consumer_records_lag{client_id="consumer-recon-service-1",topic="trade-events",partition="0",} 0.0
kafka_consumer_records_consumed_total{client_id="consumer-recon-service-1",topic="trade-events",} 42.0
```

</details>

**▶ Run the project — verify TICKET-ADV139 end-to-end**

Boot the app and scrape the Prometheus endpoint for Kafka metrics.

```bash
./mvnw spring-boot:run
curl -s http://localhost:8080/actuator/prometheus | grep -E 'kafka_(consumer|producer)' | head -20
```

**Observe:**

- `kafka_consumer_records_consumed_total` present for each of the three groups.
- `kafka_producer_record_send_total` present for the producer client id.
- `kafka_consumer_records_lag` or `kafka_consumer_fetch_manager_records_lag` present per partition.
- All series carry `application="reconx"` tag.
- Failure signal: no `kafka_*` metrics returned — Micrometer Kafka binder is missing. Verify `micrometer-registry-prometheus` plus the Spring Kafka auto-config are on the classpath.

### TICKET-ADV140 — Grafana panel: consumer lag by topic

PromQL:

```promql
sum by (topic) (kafka_consumer_records_lag)
```

Panel type: **Time series**. Threshold red at 1000, yellow at 100.

**▶ Run the project — verify TICKET-ADV140 end-to-end**

Open Grafana and confirm the consumer-lag panel renders.

```bash
docker compose up -d prometheus grafana
# Grafana: http://localhost:3000 (admin / admin)
```

**Observe:**

- "Kafka health" row contains a **Consumer Lag** time-series panel.
- Per-topic lag series visible (`trade-events`, `recon-results`, `system-alerts`) — typically 0 in a healthy state.
- Push 100 events with the consumer paused and watch lag climb visibly.
- Failure signal: "No data" — PromQL query likely refers to a metric name your binder doesn't emit. Cross-check against the ADV139 scrape output.

### TICKET-ADV141 — Grafana panel: messages/sec produced vs consumed

PromQL (two queries, one panel):

```promql
# Consumed per second across all topics
sum(rate(kafka_consumer_records_consumed_total[1m]))

# Produced per second across all topics
sum(rate(kafka_producer_record_send_total[1m]))
```

Panel type: **Time series**, two series labelled `consumed` and `produced`.
If `produced` is way above `consumed` for more than a minute, you have a
slow consumer — which the TICKET-ADV140 lag panel will confirm.

**▶ Run the project — verify TICKET-ADV141 end-to-end**

Drive a steady event rate against the app and watch the produced/consumed series track each other.

```bash
# Light load loop (run for ~1 min):
for i in $(seq 1 60); do
  curl -s -X POST http://localhost:8080/api/v1/trades \
    -H 'Content-Type: application/json' \
    -d "{\"tradeRef\":\"T-LOAD-$i\",\"instrument\":\"USD/EUR\",\"notional\":1000,\"side\":\"BUY\"}" > /dev/null
  sleep 1
done
```

**Observe:**

- The Messages/sec panel shows two line series: `produced` and `consumed`, hovering around 1 msg/s.
- In steady state the lines overlap — produce rate matches consume rate.
- Failure signal: `consumed` line lags far behind `produced` — a consumer is slow or down. Cross-reference with the ADV140 lag panel.

### TICKET-ADV142 — Grafana panel: DLQ message count + alert

PromQL:

```promql
sum(kafka_consumer_records_consumed_total{topic="trade-events-dlq"})
```

Panel type: **Stat**. Alert rule:

```yaml
- alert: KafkaDlqMessages
  expr: sum(kafka_consumer_records_consumed_total{topic="trade-events-dlq"}) > 0
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "Messages in trade-events DLQ"
    description: "At least one message has been DLQ'd in the last minute. Inspect /api/v1/admin/dlq."
```

**Talking point:** Why `consumed_total` on the DLQ rather than `lag`? Because
*we want to alert on the existence of messages*, not on consumer lag. If the
DLQ consumer is keeping up, lag will be zero — but the messages still
happened. `consumed_total > 0` (or `increase(... [5m]) > 0`) is the right
shape.

**▶ Run the project — verify TICKET-ADV142 end-to-end**

Push a handful of malformed events to drive the DLQ panel and fire the alert.

```bash
# Produce 6 garbage messages via Kafdrop's Produce tab to trade-events
# Then check Prometheus + Grafana
open http://localhost:9090/alerts
open http://localhost:3000
```

**Observe:**

- "DLQ depth" panel in Grafana climbs from 0 to >0 within ~30s.
- Prometheus → Alerts page shows `TradeEventsDLQNonEmpty` (or your alert name) transition from `inactive` → `pending` → `firing`.
- Failure signal: panel stays at 0 — DLQ depth query is using `count` instead of `sum` over per-partition series; recheck the PromQL.

### TICKET-ADV143 — Integration test: end-to-end happy path

**What good looks like:** a `@SpringBootTest` that spins up a Testcontainers
Kafka broker, publishes 100 events, awaits all 100 audit rows in the DB.
Uses `Awaitility` for the wait — never `Thread.sleep`.

<details>
<summary>Full reference solution — KafkaPipelineIT.java (TICKET-ADV143)</summary>

```java
package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class KafkaPipelineIT {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    );

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired TradeEventProducer producer;
    @Autowired AuditLogRepository auditRepo;

    @Test
    void publishesAndConsumes100Events() {
        long before = auditRepo.count();

        IntStream.range(0, 100).forEach(i ->
                producer.publish(TradeEvent.created(
                        "TRD-IT-" + i,
                        JsonNodeFactory.instance.objectNode().put("price", i)
                ))
        );

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() ->
                        assertThat(auditRepo.count()).isEqualTo(before + 100)
                );
    }
}
```

</details>

**Talking point:** Testcontainers vs `@EmbeddedKafka`:
- `@EmbeddedKafka` runs an in-JVM broker. Fast (~3s start), no Docker needed.
  Caveat: it's a *different* broker version from prod and a different
  network stack — some bugs hide.
- Testcontainers spins up the real `confluentinc/cp-kafka` image in Docker.
  Slow (~15s start), needs Docker, but is *the* broker.

Pick `@EmbeddedKafka` for unit-y tests, Testcontainers for the integration
suite. The reference uses Testcontainers because today's tests *are* the
integration suite.

**▶ Run the project — verify TICKET-ADV143 end-to-end**

Run the Testcontainers integration test against a real broker.

```bash
./mvnw -pl backend verify -Dtest=KafkaPipelineIT
```

**Observe:**

- Test log shows Testcontainers pulling/starting `confluentinc/cp-kafka:7.6.0`.
- Producer publishes 100 events; assertion confirms `audit_log_entries` grew by 100 within the Awaitility window.
- Build ends green: `Tests run: 1, Failures: 0, Errors: 0`.
- Failure signal: timeout on Awaitility — the consumer never woke up; verify the test's `spring.kafka.bootstrap-servers` is overridden to the Testcontainers broker address.

### TICKET-ADV144 — Integration test: DLQ on consumer failure

<details>
<summary>Full reference solution — DlqRoutingIT.java (TICKET-ADV144)</summary>

```java
package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.service.ReconciliationEngine;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static org.apache.kafka.clients.consumer.ConsumerConfig.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class DlqRoutingIT {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    );

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired TradeEventProducer producer;

    @MockBean ReconciliationEngine reconEngine;

    @Test
    void failingConsumerRoutesToDlq() {
        Mockito.doThrow(new RuntimeException("boom"))
                .when(reconEngine).scheduleRecon(Mockito.anyString());

        TradeEvent event = TradeEvent.created(
                "TRD-DLQ-1",
                JsonNodeFactory.instance.objectNode().put("price", 100)
        );
        producer.publish(event);

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() ->
                        assertThat(dlqHas("TRD-DLQ-1")).isTrue()
                );
    }

    private boolean dlqHas(String tradeRef) {
        Properties p = new Properties();
        p.put(BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        p.put(GROUP_ID_CONFIG, "dlq-assert-" + System.nanoTime());
        p.put(AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        p.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, TradeEvent>(p)) {
            consumer.subscribe(java.util.List.of("trade-events-dlq"));
            var records = consumer.poll(Duration.ofSeconds(5));
            for (ConsumerRecord<String, TradeEvent> r : records) {
                if (tradeRef.equals(r.value().tradeRef())) return true;
            }
        }
        return false;
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV144 end-to-end**

Run the DLQ-routing integration test.

```bash
./mvnw -pl backend verify -Dtest=DlqRoutingIT
```

**Observe:**

- Testcontainers Kafka starts; the test publishes a poison event.
- The intentionally-failing consumer retries 3 times then quarantines the message.
- Assertion confirms exactly 1 message on `trade-events-dlq`; build green.
- Failure signal: DLQ assertion sees 0 — the `DefaultErrorHandler` wiring is not active under the test profile. Check the test imports `KafkaErrorHandlerConfig` (or that `@SpringBootTest` is wide enough).

### TICKET-ADV145 — AI-assisted Kafka consumer config review

**What good looks like:** the student opens `application.yml` and
`KafkaErrorHandlerConfig.java`, pastes them into Claude with a focused
review prompt, then files a PR that lists each finding + the team's
decision (accept / reject / defer).

**Reference prompt for students** (they should adapt, not copy verbatim):

> "Review the following Spring Kafka consumer configuration for production
> readiness. Flag any missing or risky settings in these areas:
> (1) backpressure & poll tuning, (2) error handling, retry & DLQ,
> (3) idempotence and exactly-once semantics,
> (4) observability — metrics, logging, traces,
> (5) security — TLS, SASL, ACLs.
> For each finding, give the concrete config key, the recommended value,
> and a one-line justification. Do NOT rewrite the whole file — just list
> findings. The application is a trade reconciliation service handling
> ~500 events/sec with strict audit requirements."

**What Claude will *probably* flag** (use this list to grade the PR):

| Area | Likely finding | How to score it |
|------|----------------|-----------------|
| Backpressure | `max.poll.records` defaults to 500; for slow consumers this risks `max.poll.interval.ms` blowing | Accept — set to 100 |
| Error handling | `DefaultErrorHandler` is set but `BackOff` has no jitter | Accept as known gap — add to backlog |
| Idempotence | Producer has `enable.idempotence` default; confirm it's true on producer side | Accept — verify with the team |
| Observability | No `spring.application.name` tag → metrics from different services collide | Reject (we set it elsewhere) OR accept |
| Security | `bootstrap-servers` is `PLAINTEXT` | Accept as known dev gap — note for Day 10 |

**Talking point:** "Claude flagged 12 things. We accepted 5 and deferred 7.
The PR description has to **explain why** we deferred the others." This is
the AI-policy expectation — never blindly take or blindly reject AI output.

**▶ Run the project — verify TICKET-ADV145 end-to-end**

Paste your `KafkaConsumerConfig` (and `application.yml` Kafka block) into Claude with the prompt from `docs/reviews/TICKET-ADV145-prompt.md`, capture the findings, then file a PR.

```bash
# After applying accepted fixes locally:
git checkout -b ticket-ih145-kafka-config-review
git add backend/src/main/java/com/dbtraining/reconx/kafka/ backend/src/main/resources/application.yml docs/reviews/
git commit -m "TICKET-ADV145 Kafka consumer config review"
gh pr create --title "TICKET-ADV145 Kafka consumer config review" --body-file docs/reviews/TICKET-ADV145-decisions.md
```

**Observe:**

- Claude returns 3–5 distinct, actionable findings (not generic advice).
- The PR description includes a per-finding accept/reject/defer table with rationale.
- At least one of each decision type (accept, reject, defer) is recorded.
- Failure signal: all 5 findings are accepted with no rationale — re-run the review with a sharper "challenge each suggestion" follow-up prompt.

---

<details>
<summary><b>Q&A bank</b></summary>


Expect these questions. Have answers cached.

1. **"Why three topics instead of one with an `eventType` field?"**
   Each topic is a contract with a distinct consumer audience. Separate
   topics let you set per-topic retention, partition counts, ACLs, and
   replication. They also let consumers subscribe only to what they care
   about — no filtering overhead. Topics are cheap; the wrong topic
   boundary is expensive.

2. **"How many partitions should I use?"**
   The shortest answer: at least as many as you want consumer parallelism.
   The fuller answer: think about peak throughput, key cardinality, and
   future scale. We use 3 for `trade-events` because we want up to 3
   recon-consumer instances; 1 for `system-alerts` because we want strict
   global ordering. Over-partitioning has costs (controller load, file
   handles); under-partitioning caps parallelism.

3. **"What triggers a consumer group rebalance?"**
   Four common triggers: (1) a new consumer joins the group, (2) a
   consumer leaves cleanly, (3) a consumer's session times out
   (`session.timeout.ms`, default 45s), (4) a consumer fails to call
   `poll()` within `max.poll.interval.ms` (default 5 min). The last one
   bites slow-processing consumers and is the most painful to debug —
   logs say "consumer left the group" with no obvious reason.

4. **"DLQ vs retry topic — what's the difference?"**
   DLQ = terminal queue, requires intervention to leave. Retry topic =
   staged delayed re-attempt (think `retry-5s`, `retry-30s`), used when
   retries should not block the original partition. Use a retry topic
   when retries are slow (hours); use in-memory backoff + DLQ when
   retries are fast (seconds). We chose the second.

5. **"Exactly-once delivery — is it actually possible?"**
   Within the Kafka transactional API and a single consumer group writing
   back to Kafka, yes — that's the EOS feature set
   (`transactional.id` + `isolation.level=read_committed`). Across an
   external system like Postgres, you fall back to "effectively-once" via
   the transactional outbox pattern. We don't enable EOS today because
   the audit consumer's idempotency (eventId UNIQUE) makes at-least-once
   safe enough.

6. **"Event sourcing — do I really replay events on every read?"**
   No. Production systems snapshot. Persist the rebuilt aggregate every
   K events, then on read load the snapshot and replay only the tail.
   We don't snapshot today because the event volumes are small and the
   teaching point is "the events are the truth", but the production
   pattern is snapshot + tail.

7. **"Do we need a schema registry with Avro or Protobuf?"**
   We're not using one. JSON + Jackson is fine for in-house services
   where producer and consumer are the same team and deployments are
   coordinated. Schema registry + Avro/Protobuf matters when (a)
   consumers belong to different teams, (b) you want schema evolution
   enforced at the broker level, (c) on-wire size matters and you want
   binary. ReconX is none of those today.

8. **"How does Kafka handle backpressure when a consumer is slow?"**
   It doesn't, really — Kafka is pull-based. A slow consumer's lag grows
   on the broker but doesn't slow the producer. That's a feature (the
   producer is decoupled) and a risk (the broker stores the backlog and
   the consumer never catches up). You manage this with alerting
   (consumer lag), auto-scaling consumers, or — in extremis — a retry
   topic to free the main partition.

9. **"`@EmbeddedKafka` vs Testcontainers Kafka — which?"**
   `@EmbeddedKafka` for fast unit-style tests where you don't need the
   real broker (~3s start, no Docker). Testcontainers for integration
   tests where you want the real `confluentinc/cp-kafka` image
   (~15s start, needs Docker). We use Testcontainers for TICKET-ADV143/TICKET-ADV144
   because today is *integration* tests.

10. **"Why include the `before` state in the event payload?"**
    `after` alone is enough to rebuild current state. `before` lets
    consumers and UIs *describe the change* — "price went from 245.50 to
    246.00" — without fetching the prior event. Cost: ~1 KB per event.
    Benefit: dramatically better audit UX. Worth it.

11. **"Is Spring Kafka faster than the raw Kafka client?"**
    No — Spring Kafka wraps the raw client. There's a small overhead per
    message (reflection, listener dispatch). What Spring gives you is
    ergonomics: `@KafkaListener`, error handler abstractions, DLQ
    publishing, transaction integration. For 99% of services that's the
    right trade. If you're at million-msg/sec scale, drop to the raw
    client for the hot loop.

12. **"What's `auto.offset.reset=earliest` vs `latest`?"**
    `earliest` = if a new consumer group has no committed offset, start
    from the beginning of the topic. `latest` = start from now. Use
    `earliest` for new audit consumers (you want history),
    `latest` for live-only consumers (you don't care about the past).
    Our recon consumer uses `earliest` so a redeployed consumer picks
    up missed events.

13. **"What does `acks=all` do on the producer side?"**
    The producer waits for the leader *and* all in-sync replicas to
    acknowledge the write before considering the send complete. Strongest
    durability guarantee. Default is `acks=all` since Kafka 3.0 — older
    blogs say `acks=1` is the default; that's outdated.

14. **"Do consumers ever lose messages?"**
    Yes, if they auto-commit offsets *before* successfully processing.
    Default `enable.auto.commit=true` with `auto.commit.interval.ms=5000`
    means a crash mid-processing loses up to 5s of work. Spring Kafka
    defaults to `enable.auto.commit=false` and commits after the
    listener method returns — safer. Confirm your config.

15. **"Can I have more consumer instances than partitions?"**
    Yes, but the extras are idle. A partition is assigned to exactly one
    consumer in a group. 3 partitions + 5 consumers in a group = 3 active,
    2 idle. The idle ones are warm standbys for rebalance.

---

</details>

<details>
<summary><b>End-of-day debrief prompts</b></summary>


At 17:30:

1. "Draw the event flow on the whiteboard: REST POST → producer → topic
   → which consumers? Cover the keys, partition counts, and consumer
   groups. If you can't explain why `tradeRef` is the message key,
   re-read TICKET-ADV129."
2. "A message is in the DLQ. Walk me through every place you'd look to
   diagnose why — logs, Kafdrop, the DB, Grafana. Order them by where
   you'd look first."
3. "I want to rebuild a trade's state as it was 30 minutes ago. Which
   pieces of today's code make that possible, and which extra piece
   would we need that we *didn't* build today?" (Answer: we built the
   event log; we'd need a `rebuildAt(Instant)` overload on
   `TradeAggregator` and an index on `occurred_at`.)

If anyone can't answer #1 confidently, they will lurch on the Day-10 demo
when the recon flow is the centrepiece.

---

</details>

<details>
<summary><b>Things that have gone wrong before</b></summary>


- **"Consumer never rebalanced. Logs said `session timeout`."**

  The listener method took 6 minutes to process a single message; the default `max.poll.interval.ms` is 5 minutes. Kafka decided the consumer was dead and revoked its partitions.

  **Fix:** either shorten the work (move it to an async executor and ack fast) or extend `max.poll.interval.ms`. Diagnose with `consumer.coordinator` logs at DEBUG.

- **"DLQ topic was never created. Produce failed with `UNKNOWN_TOPIC_OR_PARTITION`."**

  The team only declared `trade-events` as a `NewTopic`, not the DLQ. `DeadLetterPublishingRecoverer` tried to publish to `trade-events-dlq`, broker had no such topic, auto-create was off, message vanished into the error logs.

  **Fix:** every main topic gets a matching `*-dlq` `NewTopic` bean. TICKET-ADV128 reference includes this.

- **"`@KafkaListener` threw, message wasn't acked, looped forever."**

  No `DefaultErrorHandler` configured. spring-kafka's default behaviour for spring-kafka 3.x is to retry the same record 10 times then stop; older versions retried forever. Either way, an unconfigured listener that throws is a CPU-melting log-spammer.

  **Fix:** wire the `DefaultErrorHandler` bean from TICKET-ADV134 to the listener container factory.

- **"Retry topic had the same name as the main topic. Infinite loop."**

  A clever student tried to build a custom retry pattern by re-publishing failed messages back to `trade-events`. Every retry produced the same error and re-republished. Within minutes the partition was 100k records deep.

  **Fix:** retry topics MUST be separate. Use Spring's `@RetryableTopic` if you want delayed retries; never republish to the main topic.

- **"Event-sourcing rebuild was O(N) on every read, slow."**

  A team called `TradeAggregator.rebuild(ref)` from a hot endpoint that fired on every page render. For trades with 200 events the page took 4 seconds.

  **Fix:** either cache the rebuilt state per `tradeRef` with a TTL, or snapshot every K events and read snapshot + tail. We didn't build either today; that's deliberate (teaching focus), but call it out.

- **"`TradeEvent` payload included the full JPA entity with `@Lazy` collections. Serialiser blew up."**

  A team made `before`/`after` of type `Trade` (the entity), not `JsonNode`. The entity had a `@OneToMany` lazy `breaks` collection. Jackson tried to serialise, invoked the proxy, hit a closed session, threw `LazyInitializationException` inside the producer's `send()`.

  **Fix:** never put JPA entities on the wire. Map to a DTO or `JsonNode` snapshot before publishing.

- **"Consumer lag spiked but no alert because Prometheus scraped only every 30 seconds."**

  The lag was 5000 for 25 seconds and back to 0 before the next scrape. The alert never fired.

  **Fix:** alert on the consumed-rate-versus-produced-rate ratio (which is smoother), or drop the scrape interval to 10s for Kafka metrics specifically.

- **"Testcontainers Kafka container died with `OOMKilled`."**

  Docker Desktop was on its default 2 GB RAM allocation. Kafka + Zookeeper + Postgres + the app together OOMed the daemon.

  **Fix:** make sure Docker Desktop is set to 6 GB minimum (the README says so but students miss it). Check `docker stats` while the test runs.

- **"`@KafkaListener(topics=\"trade_events\")` pointed at a topic that didn't exist. Container silently waited."**

  Typo — underscore instead of hyphen. The container subscribed, the broker auto-created the wrong topic (or didn't if auto-create was off), and the listener sat with zero records forever. No exception.

  **Fix:** add `missingTopicsFatal=true` on the listener container factory in dev — fails fast on typos.

- **"JSON deserialiser couldn't find `TradeEvent` because trusted packages weren't set."**

  spring-kafka's `JsonDeserializer` requires explicit trusted packages (or `*` in dev). Default is empty; deserialise throws `IllegalArgumentException: The class is not in the trusted packages`. Wraps as a `DeserializationException` which the error handler (correctly) doesn't retry — straight to DLQ.

  **Fix:** set `spring.json.trusted.packages: "com.dbtraining.reconx.dto"` (as the reference YAML does).

- **"Producer `send()` returned but no message hit the broker."**

  A team used `KafkaTemplate.send(...)` in a `@PostConstruct` method, app crashed in startup before the async send flushed, producer was closed without a `.flush()`.

  **Fix:** call `template.flush()` if you must send during shutdown, or accept fire-and-forget semantics and rely on the producer callback to log failures. ---</details>

<details>
<summary><b>Hand-off to Day 10</b></summary>


By end-of-day each team should have:

- [ ] Three Kafka topics created via `KafkaTopicsConfig` (`trade-events`,
      `recon-results`, `system-alerts`) plus the `trade-events-dlq`.
- [ ] `TradeEventProducer` publishing on `POST /api/v1/trades` (wired
      into the existing trade controller from Day 5).
- [ ] Three consumers (`ReconciliationConsumer`, `AuditEventConsumer`,
      `AlertConsumer`) in three distinct consumer groups.
- [ ] `DefaultErrorHandler` with `ExponentialBackOff(1000, 2.0)`, max
      3 retries, DLQ on partition-preserving topic mapping.
- [ ] DLQ consumer + `POST /api/v1/admin/dlq/replay` endpoint, RBAC-protected.
- [ ] `GET /api/v1/audit/trades/{tradeRef}/events` returns the event
      history and `TradeAggregator.rebuild(ref)` works.
- [ ] Grafana row with 3 panels: consumer lag by topic,
      produced/consumed per second, DLQ stat + alert.
- [ ] Testcontainers Kafka integration tests green: `KafkaPipelineIT`
      (happy path) and `DlqRoutingIT` (failure routing).
- [ ] PR on TICKET-ADV145 with the Claude review prompt + per-finding decision.

**Next:** [TrainersGuide/day10/](../day10/README.md) — Docker compose
7-service stack, GitHub Actions CI, k6 load test, and the final demo.

</details>
