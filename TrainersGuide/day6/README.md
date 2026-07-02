# TrainersGuide — Day 6: Caching + Prometheus + Grafana (Observability Deep Dive)

> **Student-facing equivalent:** [student-guides/day6/README.md](../../student-guides/day6/README.md)
> **Exercises:** Day 6 · TICKET-ADV081 – TICKET-ADV097 (17 hands-on exercises across the Workshop 6 block)
> **Theme:** Spring Boot Modules 5 & 6 — Performance + Observability

---

## Day at a glance

| #    | Block                                                                                  | Exercises         | What students produce                                                  |
|---------------|----------------------------------------------------------------------------------------|-------------------|------------------------------------------------------------------------|
| 1 | Standup + Day-5 holdover unblock                                                       | —                 | Everyone on green, JWT still working                                    |
| 2 | **Workshop 6 — Caching + Custom Metrics + Grafana Dashboards · A — Caching (Caffeine)**| TICKET-ADV081 – TICKET-ADV082   | `@Cacheable` on `findBySymbol`, TTL config in `application.yml`         |
| 3 | **Workshop 6 — Caching + Custom Metrics + Grafana Dashboards · B — Custom Micrometer metrics** | TICKET-ADV083 – TICKET-ADV086   | 4 custom metrics: Counter, Timer, Gauge, Summary                        |
| 4 | **★ Workshop 6 — Caching + Custom Metrics + Grafana Dashboards · C — Grafana panels + alerts (Observability Deep Dive — 2 hrs)** | TICKET-ADV087 – TICKET-ADV094  | 6 dashboard panels + 2 Prometheus alert rules                           |
| 5 | **Workshop 6 — Caching + Custom Metrics + Grafana Dashboards · D — Custom Spring Boot Starter + JMX + perf test** | TICKET-ADV095 – TICKET-ADV097 | `recon-audit-starter` module, JMX MBean, `ab`/`k6` load test result     |
| 6 | End-of-day debrief                                                                     | —                 | Day 7 preview (HTML5 + SSE)                                             |

**The Observability Deep Dive (Workshop 6C) is the headline session of the day.** Block out a full 2 hours, do not try to merge it with the metrics block. Students need to see the **Actuator → Prometheus → Grafana → alert** pipeline end-to-end with their own metrics before they can build panels confidently.

---

## Pre-day instructor prep

The evening before Day 6:

- [ ] `docker compose up -d prometheus grafana` — both running, Grafana at `http://localhost:3000` (admin/admin). Visit it once so the first-login password prompt is out of the way.
- [ ] `curl http://localhost:8080/actuator/prometheus | head -50` — confirm Spring Boot is exposing metrics. If `/actuator/prometheus` 404s, the `micrometer-registry-prometheus` dep is missing in `backend/pom.xml` — fix it tonight, not in class.
- [ ] Open `monitoring/prometheus/prometheus.yml` and verify the `recon-service` job target is `host.docker.internal:8080` (macOS) or `172.17.0.1:8080` (Linux). Wrong host = empty Grafana panels = a confused room for an hour.
- [ ] Confirm Grafana datasource is provisioned: open `monitoring/grafana/provisioning/datasources/datasource.yml` and the file should list a Prometheus datasource with `uid: prometheus-ds` (or whatever the dashboards reference). The UID **must** match what your sample dashboard JSON uses.
- [ ] Have a **sample dashboard JSON** ready to import as a fallback if a team's panels won't render. `monitoring/grafana/provisioning/dashboards/reconx-overview.json` is the canonical one.
- [ ] Test the Caffeine config locally — `mvn spring-boot:run`, hit `GET /api/v1/instruments/SAP.DE` twice, confirm second call is sub-millisecond. If you skip this, the first team to ask "is the cache even working?" derails the room.
- [ ] **Open JConsole** on your machine. Verify `jconsole` is on your PATH (`brew install --cask zulu21` or use the JDK's bundled one). You'll demo JMX live in Workshop 6D — fumbling with a missing tool kills momentum.
- [ ] Pre-write the `ab` (Apache Bench) command for TICKET-ADV097 in a terminal tab. If `ab` isn't installed (`brew install httpd` on macOS), have the k6 fallback script ready.
- [ ] Pre-open this trainer README + the student Day-6 README side-by-side.

---

## Workshop 6A — Caching with Caffeine (~1 hr)

### TICKET-ADV081 — `@Cacheable` on `InstrumentService.findBySymbol()`

**Common student blockers:**

- They annotate a `private` method. Spring AOP proxies only public methods on Spring-managed beans — the cache silently does nothing.
- They call `findBySymbol` from another method in the same class (`this.findBySymbol(...)`). Self-invocation bypasses the proxy. No cache hit.
- They forget `@EnableCaching` on a config class. The annotation is parsed but never wired.

**Unblocking ladder:**
1. **Nudge:** "Add a `log.info(...)` at the top of `findBySymbol`. Call the endpoint 3 times. How many log lines?"
2. **Hint:** "Spring uses CGLIB proxies. What does that say about method visibility and self-calls?"
3. **Reveal:** Make the method `public`, ensure callers go through `instrumentService.findBySymbol(...)` not `this.findBySymbol(...)`, and confirm `@EnableCaching` exists on a `@Configuration` class.

<details>
<summary>▶ Reference solution — TICKET-ADV081</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/service/InstrumentService.java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.Instrument;
import com.dbtraining.reconx.repository.InstrumentRepository;
import com.dbtraining.reconx.exception.NotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class InstrumentService {

    private final InstrumentRepository repo;

    public InstrumentService(InstrumentRepository repo) {
        this.repo = repo;
    }

    @Cacheable(value = "instruments", key = "#symbol")
    public Instrument findBySymbol(String symbol) {
        return repo.findBySymbol(symbol)
                   .orElseThrow(() -> new NotFoundException("Instrument not found: " + symbol));
    }
}
```

```java
// File: backend/src/main/java/com/dbtraining/reconx/config/CacheConfig.java
package com.dbtraining.reconx.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig { }
```

</details>

**Talking point:** the default cache key is built from method args via `SimpleKeyGenerator` — for a single-arg method that's the arg itself, for multi-arg it's a tuple. Explicit `key = "#symbol"` is best practice anyway: it documents intent and prevents silent breakage when someone adds a second parameter.

**▶ Run the project — verify TICKET-ADV081 end-to-end**

Confirm the `@Cacheable` annotation actually proxies the instrument lookup so the second call skips the DB.

```bash
./mvnw -pl backend spring-boot:run
# in another terminal:
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .accessToken)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/instruments/1
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/instruments/1
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/actuator/caches
```

**Observe:**

- First request logs a `DB hit for ...` line in the Spring console; the second request does not.
- `/actuator/caches` lists an `instruments` cache.
- If both calls log `DB hit`, `@EnableCaching` is missing or the method is being self-called inside the same class (proxy bypassed).

---

### TICKET-ADV082 — Cache eviction: TTL 5 min instruments, 1 min counterparties

**Common student blockers:**

- They write **one** Caffeine spec under `spring.cache.caffeine.spec` and apply it to both caches. Then the counterparty TTL is wrong.
- They put TTL in milliseconds (`expireAfterWrite=300000`) — Caffeine's spec parser expects `5m`, `30s`, etc. and silently fails on integer input.
- They forget to add `caffeine` to the dependencies and Spring falls back to a `ConcurrentMapCacheManager` that ignores all of the TTL config.

**Unblocking ladder:**
1. **Nudge:** "Run `./mvnw dependency:tree | grep caffeine`. Anything?"
2. **Hint:** "`spring.cache.caffeine.spec` is one global spec. How do you give two caches different TTLs?"
3. **Reveal:** Define a `CacheManager` bean programmatically and register two named `CaffeineCache` instances with different specs.

<details>
<summary>▶ Reference solution — TICKET-ADV082</summary>

```xml
<!-- backend/pom.xml -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

```yaml
# backend/src/main/resources/application.yml
spring:
  cache:
    type: caffeine
    cache-names: instruments,counterparties
```

```java
// File: backend/src/main/java/com/dbtraining/reconx/config/CacheConfig.java
package com.dbtraining.reconx.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCache instruments = new CaffeineCache("instruments",
            Caffeine.newBuilder()
                    .maximumSize(500)
                    .expireAfterWrite(5, TimeUnit.MINUTES)
                    .recordStats()
                    .build());

        CaffeineCache counterparties = new CaffeineCache("counterparties",
            Caffeine.newBuilder()
                    .maximumSize(200)
                    .expireAfterWrite(1, TimeUnit.MINUTES)
                    .recordStats()
                    .build());

        SimpleCacheManager mgr = new SimpleCacheManager();
        mgr.setCaches(List.of(instruments, counterparties));
        return mgr;
    }
}
```

</details>

**Talking point:** `recordStats()` is what makes Caffeine emit `cache_gets_total{result="hit"}` and `cache_gets_total{result="miss"}` to Micrometer. Without it, you have a cache and no way to *prove* it's working. Always-on in dev, configurable in prod.

**Caffeine vs Redis discussion (slot in here):** Caffeine is in-process — zero network hop, perfect for read-mostly reference data (instruments, counterparties). Redis is out-of-process — shared across instances, needed once you scale beyond one JVM. ReconX uses Caffeine because the data fits in one heap and we only run one instance in the case study. **Production would graduate this to Redis** once we add a second pod.

**▶ Run the project — verify TICKET-ADV082 end-to-end**

Confirm both named caches load with the configured TTL/maxSize specs and that Caffeine stats are wired into Prometheus.

```bash
./mvnw -pl backend spring-boot:run
curl -s http://localhost:8080/actuator/caches | jq
curl -s http://localhost:8080/actuator/caches/instruments | jq
curl -s http://localhost:8080/actuator/prometheus | grep '^cache_'
```

**Observe:**

- `/actuator/caches` lists both `instruments` and `counterparties`.
- The configured spec on each cache matches the `application.yml` (or `CacheConfig`) values for `expireAfterWrite` and `maximumSize`.
- After a warm read, `cache_gets_total{cache="instruments",result="hit"}` appears in the Prometheus scrape; if the series is missing, `recordStats()` was not chained on the `Caffeine` builder.

---

## Workshop 6B — Custom Micrometer metrics (~2 hrs)

This is where students go from "Actuator gave me metrics for free" to "I designed a metric for a business event." Spend time on **which meter type for which question** before they write any code.

### Meter type cheat sheet (put this on the whiteboard)

| Question | Meter | Example |
|---|---|---|
| "How many?" (monotonically increasing) | **Counter** | `trade_created_total` |
| "How long did each take?" (latency dist) | **Timer** | `reconciliation_duration_seconds` |
| "What's the current value?" (sampled) | **Gauge** | `recon_break_count` |
| "What's the distribution of a value?" (non-time) | **DistributionSummary** | `trade_value_total` (USD notional) |

### TICKET-ADV083 — Counter: `trade_created_total`

**Common student blockers:**

- They construct the `Counter` inside the method body — `Counter c = Counter.builder(...).register(reg);` runs every call, registering a fresh meter each time. Memory leak + metric ID conflict.
- They use `MeterRegistry.counter("trade_created_total").increment()` (the convenience form) and then can't add description/tags later because the meter is already registered.
- They name the metric `tradeCreatedTotal` (camelCase). Prometheus convention is snake_case ending in `_total` for counters.

<details>
<summary>▶ Reference solution — TICKET-ADV083</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/observability/TradeMetrics.java
package com.dbtraining.reconx.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TradeMetrics {

    private final Counter tradesCreated;

    public TradeMetrics(MeterRegistry registry) {
        this.tradesCreated = Counter.builder("trade_created_total")
                .description("Total number of trades created via the API")
                .tag("instance", "reconx")
                .register(registry);
    }

    public void incrementCreated() {
        tradesCreated.increment();
    }
}
```

```java
// In TradeService.create(...)
public Trade create(CreateTradeRequest req) {
    Trade saved = repo.save(map(req));
    tradeMetrics.incrementCreated();
    return saved;
}
```

</details>

**Talking point:** Counters are append-only. They never decrement. If you find yourself wanting a counter to go down, you want a `Gauge` instead — see TICKET-ADV085.

**▶ Run the project — verify TICKET-ADV083 end-to-end**

Confirm the custom Counter is registered once at bean creation and increments exactly once per successful trade save.

```bash
./mvnw -pl backend spring-boot:run
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .accessToken)
curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -X POST http://localhost:8080/api/v1/trades \
  -d '{"tradeRef":"T-ADV083","instrumentSymbol":"SAP.DE","counterpartyId":1,"quantity":100,"price":245.5,"tradeDate":"2026-06-02"}'
curl -s http://localhost:8080/actuator/prometheus | grep trade_created
```

**Observe:**

- `trade_created_total` appears with a `# HELP` line describing what it counts.
- The counter rises by exactly 1 per successful POST.
- If the metric is missing entirely, the `TradeMetrics` component was not picked up (check `@Component`); if it appears but never moves, the `incrementTradeCreated()` call is on a path that did not commit (e.g., before `repo.save`).

---

### TICKET-ADV084 — Timer: `reconciliation_duration_seconds`

**Common student blockers:**

- They call `timer.start()` and forget `timer.stop(sample)` in a finally block — timer never records and the metric reads as zero in Prometheus.
- They wrap a `void` method but want the return value — `Timer.record(Runnable)` returns `void`. Use `Timer.record(Supplier<T>)` for value-returning calls.
- They forget `publishPercentileHistogram()` and then can't compute `histogram_quantile()` in PromQL because no buckets are exported.

<details>
<summary>▶ Reference solution — TICKET-ADV084</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/observability/ReconMetrics.java
package com.dbtraining.reconx.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class ReconMetrics {

    private final Timer reconciliationTimer;

    public ReconMetrics(MeterRegistry registry) {
        this.reconciliationTimer = Timer.builder("reconciliation_duration_seconds")
                .description("Time spent in ReconciliationEngine.reconcile()")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public Timer reconciliationTimer() {
        return reconciliationTimer;
    }
}
```

```java
// In a service that owns the recon call:
public ReconResult runRecon(Long batchId) {
    return reconMetrics.reconciliationTimer().record(() -> engine.reconcile(batchId));
}
```

</details>

**Talking point:** `publishPercentileHistogram()` exports the `_bucket` series Prometheus needs for *server-side* quantile estimation via `histogram_quantile()`. `publishPercentiles(0.5,0.95,0.99)` exports *client-side* pre-computed percentiles as `_quantile` time series. They are **different things** and a Prometheus shop wants the histogram form. See Q&A.

**▶ Run the project — verify TICKET-ADV084 end-to-end**

Confirm the reconcile method is timed with a histogram so server-side `histogram_quantile` queries succeed.

```bash
./mvnw -pl backend spring-boot:run
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .accessToken)
for i in 1 2 3 4 5; do
  curl -s -H "Authorization: Bearer $TOKEN" -X POST http://localhost:8080/api/v1/recon/run
done
curl -s http://localhost:8080/actuator/prometheus | grep reconciliation
```

**Observe:**

- `reconciliation_duration_seconds_count`, `_sum`, and many `_bucket{le="..."}` series are present.
- Querying `histogram_quantile(0.95, sum(rate(reconciliation_duration_seconds_bucket[5m])) by (le))` in Prometheus returns a non-zero value.
- If you see only `_count` and `_sum` but no `_bucket` series, the `@Timed(histogram = true)` flag (or `.publishPercentileHistogram()` on the programmatic timer) is missing.

---

### TICKET-ADV085 — Gauge: `recon_break_count`

**Common student blockers:**

- They try to manually `gauge.set(value)` — Micrometer's `Gauge` is a **pull** abstraction. You give it a supplier; Micrometer polls.
- They register the Gauge against an object that gets garbage-collected (weak reference) and the metric mysteriously stops updating.
- The supplier hits the database on every Prometheus scrape (every 15s). With 20 panels referencing this metric, that's an unnecessary DB load.

**Unblocking ladder:**
1. **Nudge:** "Open `/actuator/prometheus` — is `recon_break_count` listed at all? What value?"
2. **Hint:** "If you call `.set(...)`, you've made it a stateful gauge variant. What does the builder API actually want?"
3. **Reveal:** Pass `repo::countOpenBreaks` as the `ToDoubleFunction`. Hold a strong reference to the repository.

<details>
<summary>▶ Reference solution — TICKET-ADV085</summary>

```java
// In ReconMetrics (or a dedicated GaugeRegistrar):
import io.micrometer.core.instrument.Gauge;

@Component
public class BreakCountGauge {
    public BreakCountGauge(MeterRegistry registry, ReconBreakRepository repo) {
        Gauge.builder("recon_break_count", repo, ReconBreakRepository::countOpenBreaks)
             .description("Current number of OPEN reconciliation breaks")
             .register(registry);
    }
}
```

```java
// File: backend/src/main/java/com/dbtraining/reconx/repository/ReconBreakRepository.java
public interface ReconBreakRepository extends JpaRepository<ReconBreak, Long> {
    @Query("SELECT COUNT(b) FROM ReconBreak b WHERE b.status = 'OPEN'")
    long countOpenBreaks();
}
```

</details>

**Talking point:** if the DB query is expensive, *cache* the result with a short TTL and have the Gauge supplier read the cached value. The Gauge polls; you decide how fresh the data needs to be.

**▶ Run the project — verify TICKET-ADV085 end-to-end**

Confirm the polled Gauge reflects the live DB count of OPEN breaks.

```bash
./mvnw -pl backend spring-boot:run
curl -s http://localhost:8080/actuator/prometheus | grep recon_break_count
# in psql or H2 console:
#   SELECT COUNT(*) FROM recon_breaks WHERE status='OPEN';
```

**Observe:**

- `recon_break_count` value in Prometheus matches `SELECT COUNT(*) FROM recon_breaks WHERE status='OPEN'`.
- Closing a break via `PUT /api/v1/breaks/{id}/resolve` and re-scraping within ~15s drops the value.
- If the value is always `0` (despite breaks existing), the gauge's `ToDoubleFunction` is likely referencing a stale reference or the wrong status string ("Open" vs "OPEN").

---

### TICKET-ADV086 — DistributionSummary: `trade_value_total`

**Common student blockers:**

- They use a `Counter` because the metric name ends in `_total`. The `_total` suffix is a Prometheus *convention* for counters, but `DistributionSummary` also produces `_count`/`_sum`/`_bucket` series. Naming nuance — let the meter type drive the name.
- They forget `.baseUnit("usd")` — the unit doesn't appear in PromQL output and someone in Workshop 6C asks "is this in dollars or cents?"
- They record a `BigDecimal` via `.doubleValue()` and don't think about precision. For business reporting that matters; for a metric it's fine.

<details>
<summary>▶ Reference solution — TICKET-ADV086</summary>

```java
// In TradeMetrics:
import io.micrometer.core.instrument.DistributionSummary;

@Component
public class TradeMetrics {
    private final Counter tradesCreated;
    private final DistributionSummary tradeValue;

    public TradeMetrics(MeterRegistry registry) {
        this.tradesCreated = Counter.builder("trade_created_total")
            .description("Total number of trades created").register(registry);

        this.tradeValue = DistributionSummary.builder("trade_value_total")
            .description("Distribution of trade notional values")
            .baseUnit("usd")
            .publishPercentileHistogram()
            .register(registry);
    }

    public void recordTrade(Trade t) {
        tradesCreated.increment();
        tradeValue.record(t.notional().doubleValue());
    }
}
```

</details>

**Talking point:** the difference between a Timer and a DistributionSummary is **time vs anything else**. Timer measures how long; Summary measures how big/wide/heavy/expensive. The Prometheus output looks similar (`_count`, `_sum`, `_bucket`) — the semantic difference is yours to preserve.

**▶ Run the project — verify TICKET-ADV086 end-to-end**

Confirm all three TradeMetrics members (Counter + DistributionSummary + Gauge) appear together in one Prometheus scrape.

```bash
./mvnw -pl backend spring-boot:run
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .accessToken)
for i in 1 2 3; do
  curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -X POST http://localhost:8080/api/v1/trades \
    -d "{\"tradeRef\":\"T-ADV086-$i\",\"instrumentSymbol\":\"SAP.DE\",\"counterpartyId\":1,\"quantity\":100,\"price\":245.5,\"tradeDate\":\"2026-06-02\"}"
done
curl -s http://localhost:8080/actuator/prometheus | grep -E 'trade_created_total|trade_value_total|recon_break_count'
```

**Observe:**

- All three series — `trade_created_total`, `trade_value_total_{count,sum,bucket}`, and `recon_break_count` — appear together.
- `trade_value_total_sum` grows by `quantity * price` per POST; `_count` grows by 1.
- If `_bucket` series for `trade_value_total` are missing, `publishPercentileHistogram()` was not chained on the `DistributionSummary` builder.

---

## ★ Workshop 6C — Grafana panels + alerts (Observability Deep Dive — 2 hrs)

This is the highlight of Day 6. Open with a **5-minute architecture sketch** on the whiteboard:

```
Spring Boot app  ──[/actuator/prometheus]──►  Prometheus  ──[PromQL]──►  Grafana
   |                       (HTTP pull, 15s)        |                       |
   └─ Micrometer                                   └─ alert rules ──► Alertmanager
                                                                          │
                                                                          ▼
                                                                 Slack / email / webhook
```

Then demo the pipeline live: open `http://localhost:8080/actuator/prometheus`, then `http://localhost:9090/graph` and query `up`, then `http://localhost:3000` and add a panel for `http_server_requests_seconds_count`. Students see the data flow before they build anything.

### TICKET-ADV087 — Grafana panel: API request rate by endpoint

**Common student blockers:**

- They use `rate(http_server_requests_seconds_count[1m])` without `sum() by (uri)` and get one line per `(uri, method, status, instance)` combination. The legend is unreadable.
- They forget the time-window — `rate(metric)` without `[duration]` is a syntax error. The error message is unhelpful.
- The panel renders "No data" because their Prometheus job name is wrong (e.g., scrapes `recon-svc` instead of `recon-service`).

<details>
<summary>▶ Reference PromQL + panel JSON — TICKET-ADV087</summary>

PromQL:
```promql
sum(rate(http_server_requests_seconds_count[1m])) by (uri)
```

Panel JSON (drop into the dashboard's `panels` array):
```json
{
  "id": 1,
  "type": "timeseries",
  "title": "API request rate by endpoint",
  "datasource": { "type": "prometheus", "uid": "prometheus-ds" },
  "targets": [
    {
      "refId": "A",
      "expr": "sum(rate(http_server_requests_seconds_count[1m])) by (uri)",
      "legendFormat": "{{uri}}"
    }
  ],
  "fieldConfig": {
    "defaults": { "unit": "reqps" }
  },
  "gridPos": { "h": 8, "w": 12, "x": 0, "y": 0 }
}
```

</details>

**▶ Run the project — verify TICKET-ADV087 end-to-end**

Boot the observability stack and confirm the request-rate panel renders one line per URI.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
# in another terminal — exercise a few endpoints to get non-zero traffic:
for i in 1 2 3 4 5; do curl -s http://localhost:8080/actuator/health > /dev/null; done
# open http://localhost:3000  (admin/admin)
```

**Observe:**

- The "ReconX Overview" dashboard loads and the "API request rate by endpoint" panel renders one line per `uri`.
- Y-axis unit is `reqps` and the legend reads as a URI (not a `{instance=...,job=...}` blob).
- If the panel shows "No data," check `up{job="recon-service"}` in Prometheus first — the break is almost always at the scrape, not the panel.

---

### TICKET-ADV088 — Grafana panel: API response time P50/P95/P99

**Common student blockers:**

- They use `http_server_requests_seconds` (no suffix) — that's the Micrometer base name, not a Prometheus series. PromQL wants `_bucket` / `_count` / `_sum`.
- They forget the `le` label in the `by` clause. `histogram_quantile()` *needs* `le` to interpolate buckets.
- The panel shows percentiles in seconds and they expect milliseconds. Set the unit on the Grafana axis.

<details>
<summary>▶ Reference PromQL — TICKET-ADV088</summary>

```promql
# P50
histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))
# P95
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))
# P99
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))
```

Three queries on the same panel, legends `P50 {{uri}}`, `P95 {{uri}}`, `P99 {{uri}}`. Unit: `s` (Grafana renders ms/µs automatically).

</details>

**Talking point:** P95 = 95% of requests complete *under* this value. The right SLO to put on your status page. P50 = median (most users' experience). P99 = the tail — where the angry-customer escalations come from.

**▶ Run the project — verify TICKET-ADV088 end-to-end**

Confirm the P50/P95/P99 latency panel renders three labelled lines once the bucket series exist.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
for i in $(seq 1 20); do curl -s http://localhost:8080/actuator/health > /dev/null; done
# open http://localhost:3000 → ReconX Overview → "API response time P50/P95/P99"
```

**Observe:**

- Three labelled lines (P50, P95, P99) render with non-zero values after a bit of traffic.
- Panel unit is `s` and Grafana auto-formats values into ms or µs.
- If a percentile line is flat at zero, the `_bucket` series is missing — confirm `management.metrics.distribution.percentiles-histogram.http.server.requests: true` is set.

---

### TICKET-ADV089 — Grafana panel: `trade_created_total` over time

<details>
<summary>▶ Reference PromQL — TICKET-ADV089</summary>

```promql
sum(rate(trade_created_total[1m]))
```

Time series panel. Unit: `trades/s` (custom unit string). Legend: `Trades created /s`.

If a team wants a **cumulative** count instead of a rate, use:
```promql
trade_created_total
```
…and pick the "increase" calculation in panel options. But the rate form is what an SRE will want to alert on.

</details>

**▶ Run the project — verify TICKET-ADV089 end-to-end**

Confirm the trades-created-per-second panel moves when you POST trades.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .accessToken)
for i in $(seq 1 10); do
  curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -X POST http://localhost:8080/api/v1/trades \
    -d "{\"tradeRef\":\"T-ADV089-$i\",\"instrumentSymbol\":\"SAP.DE\",\"counterpartyId\":1,\"quantity\":100,\"price\":245.5,\"tradeDate\":\"2026-06-02\"}"
done
# open http://localhost:3000 → ReconX Overview → "Trades created / sec"
```

**Observe:**

- The "Trades created / sec" panel shows a non-flat line during/after the POST burst.
- Legend reads "Trades created /s" and the unit is `trades/s`.
- If the line is flat at zero but `trade_created_total` is visibly rising on `/actuator/prometheus`, the PromQL is wrapping the wrong series — confirm you used `rate(...[1m])` over the `_total` counter.

---

### TICKET-ADV090 — Grafana histogram panel for `reconciliation_duration_seconds`

<details>
<summary>▶ Reference PromQL — TICKET-ADV090</summary>

```promql
# Heatmap data source
sum(rate(reconciliation_duration_seconds_bucket[5m])) by (le)
```

Panel type: **Heatmap**. X-axis: time. Y-axis: bucket. Cell value: rate. Set "Format" to `Heatmap` and Grafana renders the density.

For a simpler alternative — a multi-percentile line panel:
```promql
histogram_quantile(0.50, sum(rate(reconciliation_duration_seconds_bucket[5m])) by (le))
histogram_quantile(0.95, sum(rate(reconciliation_duration_seconds_bucket[5m])) by (le))
histogram_quantile(0.99, sum(rate(reconciliation_duration_seconds_bucket[5m])) by (le))
```

</details>

**▶ Run the project — verify TICKET-ADV090 end-to-end**

Confirm the reconciliation-duration heatmap (or percentile lines) renders after triggering a dozen recon runs.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .accessToken)
for i in $(seq 1 12); do
  curl -s -H "Authorization: Bearer $TOKEN" -X POST http://localhost:8080/api/v1/recon/run
done
# open http://localhost:3000 → ReconX Overview → "Reconciliation duration histogram"
```

**Observe:**

- Heatmap cells (or three percentile lines) appear with non-empty data; Y-axis shows `le` bucket boundaries.
- The PromQL preview in the panel editor shows one series per `le` value.
- If you see only a single dark row at the top of the heatmap, the engine is faster than the smallest bucket — trigger a heavier workload or accept "always fast" as a valid observation.

---

### TICKET-ADV091 — Grafana stat panel: `recon_break_count`

<details>
<summary>▶ Reference PromQL + panel — TICKET-ADV091</summary>

```promql
recon_break_count
```

Panel type: **Stat**. Thresholds:
- Green: 0 – 10
- Yellow: 10 – 50
- Red: > 50

Color mode: "Value". This gives operators an at-a-glance "are we drowning in breaks?" tile.

</details>

**▶ Run the project — verify TICKET-ADV091 end-to-end**

Confirm the stat tile renders the current open-break count and changes colour by threshold.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
# open http://localhost:3000 → ReconX Overview → "Open recon breaks"
# in psql:  INSERT INTO recon_breaks (...) VALUES (..., 'OPEN'); -- bump count past 10 then past 50
```

**Observe:**

- Stat panel shows a single large number matching the `recon_break_count` gauge.
- Inserting OPEN breaks crosses the 10 and 50 thresholds and the tile turns yellow then red.
- Resolving breaks drops the value within one scrape interval (~15s); if the colour does not change at all, the Stat panel's Color mode is set to `Background` instead of `Value`.

---

### TICKET-ADV092 — Grafana pie chart: trades by status

**Common student blocker:** they try to derive this from `trade_created_total` (which has no `status` label). Need a Gauge with a `status` tag emitting from the app.

<details>
<summary>▶ Reference solution — TICKET-ADV092</summary>

App side — register one Gauge per status, sourced from a repository count:
```java
@Component
public class TradesByStatusGauge {
    public TradesByStatusGauge(MeterRegistry registry, TradeRepository repo) {
        for (String status : List.of("PENDING","MATCHED","UNMATCHED","DISPUTED","CANCELLED")) {
            Gauge.builder("trades_by_status", repo, r -> r.countByStatus(status))
                 .tag("status", status)
                 .description("Trades currently in a given status")
                 .register(registry);
        }
    }
}
```

PromQL for the pie chart:
```promql
sum by (status) (trades_by_status)
```

Grafana panel type: **Pie chart**. Legend: `{{status}}`.

</details>

**▶ Run the project — verify TICKET-ADV092 end-to-end**

Confirm one tagged gauge series appears per trade status and the Grafana pie chart shows slices for each.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
curl -s http://localhost:8080/actuator/prometheus | grep trades_by_status
# open http://localhost:3000 → ReconX Overview → "Trades by status" pie chart
```

**Observe:**

- `/actuator/prometheus` lists five `trades_by_status{status="..."}` series, one per status value.
- The pie chart in Grafana renders one slice per non-zero status with `{{status}}` as the legend.
- If only some slices render, that is expected — statuses with a zero count emit `0.0` and Grafana hides the slice; not a bug.

---

### TICKET-ADV093 — Alert: `recon_break_count > 50` for 5 min

<details>
<summary>▶ Reference Prometheus alert rule — TICKET-ADV093</summary>

```yaml
# File: monitoring/prometheus/alert.rules.yml
groups:
  - name: reconx-alerts
    interval: 30s
    rules:
      - alert: TooManyReconBreaks
        expr: recon_break_count > 50
        for: 5m
        labels:
          severity: warning
          team: recon-ops
        annotations:
          summary: "More than 50 open recon breaks"
          description: "recon_break_count has been above 50 for 5 minutes (current value: {{ $value }}). Investigate ReconciliationEngine output and counterparty feed health."
```

Reference from `prometheus.yml`:
```yaml
rule_files:
  - "alert.rules.yml"
```

</details>

**Talking point:** `for: 5m` means "stay above the threshold for 5 continuous minutes before firing." Without it, a single transient scrape spike would alert. Critical guard against pager fatigue.

**▶ Run the project — verify TICKET-ADV093 end-to-end**

Confirm the alert rule loads in Prometheus, fires when breaches persist for the `for:` window, and carries severity labels.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
# trigger the condition: inject enough OPEN breaks (e.g. via SQL or by ingesting bad reconciliation data)
# then open http://localhost:9090/rules and http://localhost:9090/alerts
```

**Observe:**

- `TooManyReconBreaks` is listed at `http://localhost:9090/rules` with the configured `expr`, `for`, labels, and annotations.
- After breaching `> 50` for 5 minutes, the alert moves to PENDING then FIRING on `/alerts`; severity label reads `warning`.
- If the rule never appears in `/rules`, `rule_files:` in `prometheus.yml` is misconfigured or Prometheus did not reload — check the Prometheus container logs for YAML parse errors.

---

### TICKET-ADV094 — Alert: API P95 latency > 500ms for 3 min

<details>
<summary>▶ Reference Prometheus alert rule — TICKET-ADV094</summary>

```yaml
- alert: HighApiLatency
  expr: histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le)) > 0.5
  for: 3m
  labels:
    severity: warning
    team: backend
  annotations:
    summary: "API P95 latency above 500ms"
    description: "P95 latency over the last 5m has been above 500ms for 3 continuous minutes. Current: {{ $value }}s."
```

</details>

**Talking point on Prometheus alerts vs Grafana alerts:** Prometheus alerts live in the Prometheus config and survive Grafana being down. Grafana alerts are easier to author and you get the dashboard preview, but they couple your alerting to Grafana's uptime. **Production rule:** alert in Prometheus, *visualise* in Grafana.

**▶ Run the project — verify TICKET-ADV094 end-to-end**

Confirm the latency alert appears alongside the break-count rule and fires when P95 stays high for the `for:` window.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
# slow a service method (e.g. Thread.sleep in a handler) or hammer a heavy endpoint
ab -n 500 -c 20 http://localhost:8080/api/v1/instruments/1
# then open http://localhost:9090/rules and http://localhost:9090/alerts
```

**Observe:**

- Both `TooManyReconBreaks` and `HighApiLatencyP95` appear under `http://localhost:9090/rules`.
- After 3 minutes of P95 above 500ms, `HighApiLatencyP95` moves to FIRING with the offending `uri` interpolated in the annotation.
- If the alert never moves to PENDING, the underlying `histogram_quantile` query is returning `NaN` — confirm the HTTP histogram exposure flag is on and the bucket series exist.

---

## Workshop 6D — Custom Spring Boot Starter + JMX + perf test (~1 hr)

### TICKET-ADV095 — Custom Spring Boot Starter: `recon-audit-starter`

This is one of the most-misunderstood Spring features and worth slowing down for. The starter is a **separate Maven module** that other projects can depend on; it auto-wires its beans when present on the classpath.

**Common student blockers:**

- They build it as a sub-package of the main app — defeats the purpose. It must be a sibling Maven module so it can be packaged and reused.
- They put `spring.factories` in `src/main/resources/META-INF/` — **Boot 3 uses a different file** (`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`). Older tutorials lead them astray.
- They forget `@ConditionalOnMissingBean` and the starter's bean overrides the user's. Or they forget `@ConditionalOnClass` and it autoconfigures even when the consumer doesn't have the necessary dependency on the classpath.

<details>
<summary>▶ Reference solution — TICKET-ADV095 (full module structure)</summary>

```
recon-audit-starter/
├── pom.xml
└── src/main/
    ├── java/com/dbtraining/reconx/audit/
    │   ├── AuditAutoConfiguration.java
    │   ├── AuditEventPublisher.java
    │   └── AuditProperties.java
    └── resources/META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

`pom.xml` (starter module):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.dbtraining.reconx</groupId>
    <artifactId>recon-audit-starter</artifactId>
    <version>1.0.0</version>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

`AuditAutoConfiguration.java`:
```java
package com.dbtraining.reconx.audit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(ApplicationEventPublisher.class)
@ConditionalOnProperty(prefix = "reconx.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AuditProperties.class)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditEventPublisher auditEventPublisher(ApplicationEventPublisher publisher,
                                                    AuditProperties props) {
        return new AuditEventPublisher(publisher, props);
    }
}
```

`AuditProperties.java`:
```java
package com.dbtraining.reconx.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("reconx.audit")
public class AuditProperties {
    private boolean enabled = true;
    private String topic = "audit-events";
    // getters + setters
}
```

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
com.dbtraining.reconx.audit.AuditAutoConfiguration
```

Consumer side (the main `recon-service`) just adds:
```xml
<dependency>
    <groupId>com.dbtraining.reconx</groupId>
    <artifactId>recon-audit-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

…and `AuditEventPublisher` becomes injectable with zero configuration.

</details>

**Talking point:** the *value* of a custom starter is **convention + zero ceremony for the consumer**. When ReconX grows to 5 microservices each needing audit publishing, one dependency line replaces 50 lines of copy-paste config per service. That's the win.

**▶ Run the project — verify TICKET-ADV095 end-to-end**

Confirm the starter module builds standalone, installs to your local Maven repo, and the imports file ships inside the JAR.

```bash
cd recon-audit-starter
../mvnw clean install
jar tf target/recon-audit-starter-1.0.0.jar | grep AutoConfiguration.imports
ls -la ~/.m2/repository/com/dbtraining/reconx/recon-audit-starter/1.0.0/
# then in the consumer:
cd ../backend && ../mvnw spring-boot:run
# toggle off and confirm the bean disappears:
SPRING_APPLICATION_JSON='{"reconx":{"audit":{"enabled":false}}}' ../mvnw spring-boot:run
```

**Observe:**

- The starter's JAR appears in `~/.m2/repository/com/dbtraining/reconx/recon-audit-starter/1.0.0/`.
- `jar tf` shows `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` inside the JAR.
- With the starter on the classpath the `AuditEventPublisher` bean is autowired; setting `reconx.audit.enabled=false` makes it disappear cleanly. If the bean never appears, the imports file is at the wrong path (e.g., still under the Boot-2-era `META-INF/spring.factories`).

---

### TICKET-ADV096 — JMX beans: toggle caching, adjust recon tolerance at runtime

**Common student blockers:**

- They expect to see the MBean in JConsole but Spring's JMX exposure is off by default in Boot 3 — needs `spring.jmx.enabled=true`.
- They use `@ManagedAttribute` on a getter and want the setter exposed too — both the getter *and* the setter need `@ManagedAttribute` annotations for it to become a writable JMX attribute.
- They name the bean's `objectName` without a `:type=` segment. JConsole won't display it under any folder.

<details>
<summary>▶ Reference solution — TICKET-ADV096</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/observability/ReconConfigMBean.java
package com.dbtraining.reconx.observability;

import org.springframework.cache.CacheManager;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

@Component
@ManagedResource(
    objectName = "reconx:type=ReconConfig",
    description = "Runtime tuning for the reconciliation engine"
)
public class ReconConfigMBean {

    private volatile double priceTolerance = 0.01;
    private volatile boolean cachingEnabled = true;
    private final CacheManager cacheManager;

    public ReconConfigMBean(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @ManagedAttribute(description = "Price tolerance for break detection (0.0 - 1.0)")
    public double getPriceTolerance() {
        return priceTolerance;
    }

    @ManagedAttribute
    public void setPriceTolerance(double v) {
        if (v < 0 || v > 1) throw new IllegalArgumentException("tolerance must be 0..1");
        this.priceTolerance = v;
    }

    @ManagedAttribute
    public boolean isCachingEnabled() { return cachingEnabled; }

    @ManagedAttribute
    public void setCachingEnabled(boolean enabled) { this.cachingEnabled = enabled; }

    @ManagedOperation(description = "Evict all entries from the instruments cache")
    public void clearCache() {
        cacheManager.getCacheNames().forEach(n -> cacheManager.getCache(n).clear());
    }
}
```

`application.yml`:
```yaml
spring:
  jmx:
    enabled: true
```

</details>

**Demo:** open JConsole, connect to the running JVM (`localhost:8080` process), navigate `MBeans → reconx → ReconConfig → Attributes`. Change `PriceTolerance` to `0.05`. Trigger a recon. Show the break count drop. **This is observability that lets you tune live.**

**▶ Run the project — verify TICKET-ADV096 end-to-end**

Boot with JMX enabled and confirm JConsole can read, write, and invoke operations on the `ReconConfig` MBean.

```bash
./mvnw -pl backend spring-boot:run
# in another terminal:
jconsole
# in JConsole: Local Process → reconx-service PID → MBeans tab → reconx → ReconConfig
```

**Observe:**

- `reconx:type=ReconConfig` is visible under MBeans → reconx; attributes `PriceTolerance`, `CachingEnabled` are listed, and operation `clearCache` is invocable.
- Editing `PriceTolerance` from JConsole changes the value that subsequent reconcile runs use; invoking `clearCache()` empties the Caffeine caches without a restart.
- If the MBean is missing entirely, `spring.jmx.enabled: true` is not set or the bean is missing `@ManagedResource`; if JConsole rejects a `setPriceTolerance` call, the validation throw is doing its job (try a value in 0..1).

---

### TICKET-ADV097 — Performance test: 100 concurrent requests

<details>
<summary>▶ Reference command — TICKET-ADV097</summary>

```bash
# Get a token first
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .accessToken)

# Apache Bench: 100 total requests, 10 concurrent
ab -n 100 -c 10 \
   -H "Authorization: Bearer $TOKEN" \
   -T application/json \
   -p trade.json \
   http://localhost:8080/api/v1/trades

# trade.json:
# {"tradeRef":"PERF-001","instrumentSymbol":"SAP.DE","counterpartyId":1,
#  "quantity":100,"price":245.5,"tradeDate":"2026-06-02"}
```

k6 fallback (if `ab` isn't installed):
```javascript
// perf.js
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 10,
  iterations: 100,
};

const TOKEN = __ENV.TOKEN;

export default function () {
  const res = http.post('http://localhost:8080/api/v1/trades',
    JSON.stringify({tradeRef: `K6-${__VU}-${__ITER}`, instrumentSymbol: 'SAP.DE',
                    counterpartyId: 1, quantity: 100, price: 245.5, tradeDate: '2026-06-02'}),
    { headers: { 'Authorization': `Bearer ${TOKEN}`, 'Content-Type': 'application/json' }});
  check(res, { 'status is 201': r => r.status === 201 });
}

// run: TOKEN=$TOKEN k6 run perf.js
```

</details>

**What good looks like:** P95 < 200ms on a laptop with the cache warm, throughput > 100 req/s. Show the live Grafana panel from TICKET-ADV088 ticking up as the test runs — that's the payoff for the whole day.

**▶ Run the project — verify TICKET-ADV097 end-to-end**

Drive 100 concurrent trade creations and watch the Grafana dashboards spike live.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .accessToken)
ab -n 100 -c 10 \
   -H "Authorization: Bearer $TOKEN" \
   -T application/json \
   -p trade.json \
   http://localhost:8080/api/v1/trades
# open http://localhost:3000 — watch the ADV087 + ADV088 panels live
```

**Observe:**

- `ab` reports 100 successful (201) responses with a `Requests per second` and a percentile table; throughput stays above the agreed SLO.
- The TICKET-ADV087 request-rate panel spikes during the run and the TICKET-ADV088 P95 latency panel stays under ~200ms.
- No DLQ events fire on the Kafka topics; if `ab` errors mid-run on duplicate `tradeRef`, switch to the k6 fallback which templates the ref per iteration.

---

<details>
<summary><b>Q&A bank</b></summary>


**1. `@Cacheable` — what's the cache key by default?** The `SimpleKeyGenerator` builds a key from the method's arguments: `SimpleKey.EMPTY` for no args, the arg itself for one arg, a `SimpleKey(args...)` tuple for multiple. Best practice: specify `key = "#paramName"` explicitly so the key is documented and won't silently change when a parameter is added.

**2. Caffeine vs Redis — when does each win?** Caffeine = in-process, zero network hop, sub-microsecond reads, perfect for read-mostly reference data that fits in one heap. Redis = shared across JVMs, survives instance restarts, needed once you scale horizontally. ReconX is single-instance so Caffeine fits; production would graduate to Redis.

**3. Counter vs Gauge vs Summary vs Timer — when?** Counter for monotonically increasing event counts ("how many trades created"). Gauge for current-value samples ("how many open breaks right now"). Timer for latency distributions. DistributionSummary for non-time-value distributions (USD notional). Wrong meter type = wrong PromQL = wrong answer.

**4. PromQL `rate()` vs `irate()` — what's the difference?** `rate(metric[5m])` averages over the whole window. `irate(metric[5m])` uses only the last two samples. `irate` is more reactive (spiky graphs), `rate` is smoother (dashboards and alerts). **Alert on `rate`, eyeball with `irate` for debugging.**

**5. Prometheus Histogram vs Summary — pick one?** Histogram exports `_bucket` series and lets you compute *server-side* percentiles via `histogram_quantile()`, aggregatable across instances. Summary pre-computes `_quantile` series in the app — cheap to render but NOT aggregatable (can't average two P95s). **Always Histogram in a Prometheus shop.**

**6. Grafana variables — how, and why?** Define in `Settings → Variables → New`, e.g. `instance` from `label_values(up, instance)`. Reference in PromQL as `$instance`. Lets one dashboard serve many environments. Biggest dashboard-maintenance win — use from day one or you'll have 30 copy-pasted dashboards by Day 10.

**7. Alert in Prometheus vs alert in Grafana — which?** Prometheus alerts live in Prometheus config, evaluate during scrape, fire to Alertmanager, and survive Grafana being down. Grafana alerts are easier to author but couple alerting uptime to Grafana uptime. **Production rule: alert in Prometheus, visualise in Grafana.**

**8. Why JMX in 2026 — isn't it legacy?** Still the lowest-friction way to expose *runtime-mutable* config attributes from a JVM. JConsole, VisualVM, Mission Control — built-in tooling ops teams already use. Actuator's `/actuator/env` is the modern HTTP equivalent but JMX is what runs in a locked-down trading-floor environment where you can't open an extra port. Legacy in UI land, current in infra.

**9. Should JMX be enabled in prod?** Read-only attributes (metrics, current state) — yes. Writable attributes (`set*`) and operations (`clearCache()`) — only behind strong access control (JMX auth, RMI port firewalled, SRE jumphost). The danger isn't JMX itself; it's an unsecured `setPriceTolerance(0)` that wipes break detection. Treat write JMX like sudo.

**10. Should we put the load test in CI?** A *correctness* load test (does it 200 OK under 100 concurrent? do we leak connections?) — yes, deterministic, catches regressions. A *performance* load test (P95 < 200ms) — no, CI runners have unpredictable noisy-neighbour CPU and you'll get flaky failures. Run perf benchmarks on dedicated hardware nightly.

**11. Why `_total` suffix on counters?** Prometheus convention. Client libraries, alert templates and recording rules often assume it. Stick to the convention.

**12. What does `publishPercentileHistogram()` actually do?** Tells Micrometer to export pre-configured `_bucket` series along with `_count` and `_sum`. Bucket boundaries (`le="0.001", "0.01", "0.1"...`) are exponentially spaced and tuned for latencies. Without it, Prometheus only sees count+sum and can't compute server-side quantiles.

**13. How do I see Caffeine cache hit ratio?** With `recordStats()` set on the cache, Micrometer auto-exports `cache_gets_total{cache="instruments",result="hit"|"miss"}`. PromQL: `sum(rate(cache_gets_total{result="hit"}[5m])) / sum(rate(cache_gets_total[5m]))`. Stat panel with thresholds — green > 90%, yellow > 70%, red below.

**14. My recon timer reports P95 = 0 — what?** Two causes: (a) you didn't call `publishPercentileHistogram()` so no buckets are exported, (b) you wrapped the wrong method and no time is being recorded. Grep `/actuator/prometheus` for `reconciliation_duration_seconds_bucket` — missing = (a); present with `le=+Inf` count = 0 = (b).

**15. Can the custom starter ship its own metrics?** Yes, and that's good practice. The auto-configuration class can register Micrometer meters as `@Bean`s — picked up by the consumer's `MeterRegistry`. That's how `spring-boot-starter-actuator` does it. Remember `@ConditionalOnClass(MeterRegistry.class)` so the starter doesn't crash a non-Actuator consumer.

---

</details>

<details>
<summary><b>End-of-day debrief prompts</b></summary>


At 16:45, ask each team:

1. "Show me your Grafana dashboard. Talk through what each panel tells an SRE in 60 seconds."
2. "Your `recon_break_count` alert fires. What's the first PromQL query you'd run in Prometheus to triage?"
3. "Argue for keeping `@Cacheable` on `findBySymbol` in production. Argue *against*. Which side wins?"
4. "If the recon engine starts taking 10x longer overnight, name three metrics you'd correlate to find the cause."
5. "Sketch the path of a single byte from `Counter.increment()` in TradeService to a pixel on a Grafana panel. Name every hop."

If anyone can't answer #5 confidently, they don't yet have the mental model — schedule a 10-minute 1:1 tomorrow morning before Day 7 starts.

---

</details>

<details>
<summary><b>Things that have gone wrong before</b></summary>


- **`@Cacheable` on a private method.**

  Spring AOP couldn't proxy it, the cache silently did nothing, the team spent 90 minutes "tuning" a cache that never ran.

  **Fix:** during PR review, grep for `@Cacheable` and verify every annotated method is public AND called from outside the bean.

- **Cache key was the object reference, not a meaningful key.**

  Team had `@Cacheable("trades") public Trade enrich(Trade t)` — every call had a different `Trade` instance so every call missed.

  **Fix:** key must be a value-equal type (String, Long). Add `key = "#t.tradeRef"`.

- **Counter created in a method body, not as a field.**

  Every call constructed a fresh `Counter`, Micrometer threw `IllegalArgumentException: meter with same name already registered` after the first request.

  **Fix:** Counters/Timers/Gauges go in the constructor, hold them as `private final` fields.

- **Timer registered but never recorded.**

  Team used `Timer.start(registry)` returning a `Sample` and forgot to call `sample.stop(timer)`. Timer's `_count` series stayed at 0 forever.

  **Fix:** prefer `timer.record(() -> ...)` (impossible to leak) over the start/stop pair.

- **Grafana datasource UID mismatched after provisioning.**

  Provisioning file said `uid: prometheus`, dashboard JSON referenced `uid: prometheus-ds`. Every panel showed "N/A" with no error.

  **Fix:** standardise the UID in `monitoring/grafana/provisioning/datasources/datasource.yml` and grep all dashboard JSONs for the same string before committing.

- **PromQL `recon_break_count > 50` alert fired on every scrape because the metric never decreased.**

  Team's Gauge was sourced from a stale in-memory counter that they never decremented when a break resolved. Alert paged 4 people overnight.

  **Fix:** Gauge supplier must read live state — the DB count, not a manually-maintained counter. (This is exactly what TICKET-ADV085's reference solution avoids.)

- **Custom starter's `spring.factories` wasn't at the right path.**

  Boot 3 ignores `META-INF/spring.factories` for autoconfig — it wants `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. The starter's beans never registered, the consumer's `@Autowired AuditEventPublisher` failed at startup with "no bean".

  **Fix:** the path is the path. Verify with `jar tf recon-audit-starter-1.0.0.jar | grep AutoConfiguration.imports` after building.

- **Perf test ran against a single thread.**

  Team did `for i in 1..100; do curl ...; done` — that measured serial throughput (~1 RPS), they reported "API is slow."

  **Fix:** use `ab -c 10` or `k6 --vus 10`. Concurrency, not iteration, is what stresses the system.

- **JMX MBean object name had no `type=` segment.**

  Bean registered but didn't show up in JConsole's tree view (it was hidden in the root). Team thought it wasn't registered.

  **Fix:** always use the `domain:type=Foo,name=Bar` convention.

- **Caffeine without `recordStats()`.**

  Cache worked but `cache_gets_total` series were missing from Prometheus, so the hit-rate dashboard panel was blank. Team concluded the cache wasn't doing anything.

  **Fix:** `recordStats()` on the builder, every time.

- **Alert rules file referenced but not loaded.**

  Team added `alert.rules.yml` but didn't list it in `prometheus.yml` under `rule_files:`. Prometheus started clean, no alerts.

  **Fix:** check `http://localhost:9090/rules` — if your rule isn't listed, Prometheus didn't see the file. ---</details> <details> <summary><b>Hand-off to Day 7</b></summary>


By end-of-day each team should have:

- [ ] `@Cacheable` working on `InstrumentService.findBySymbol` — second call demonstrably faster.
- [ ] Caffeine cache config with two named caches at different TTLs.
- [ ] Four custom Micrometer metrics live on `/actuator/prometheus`: `trade_created_total`, `reconciliation_duration_seconds`, `recon_break_count`, `trade_value_total`.
- [ ] Grafana dashboard with 6 panels (request rate, latency percentiles, trade-create rate, recon-duration histogram, break-count stat, trades-by-status pie).
- [ ] Two firing-capable Prometheus alert rules.
- [ ] `recon-audit-starter` Maven module published locally, consumed by `recon-service`.
- [ ] `ReconConfig` JMX MBean visible in JConsole with attributes and one operation.
- [ ] Apache Bench / k6 run logged with throughput + P95 numbers.

**Day 7 picks up the frontend story:** semantic HTML5 + CSS Grid + SSE feed + ARIA accessibility. The metrics you built today will reappear as a live operational dashboard students can wire to via SSE.

**Next:** [TrainersGuide/day7/](../day7/README.md)

</details>
