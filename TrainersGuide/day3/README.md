# TrainersGuide — Day 3: Functional Java + JUnit 5 + Mockito + Testcontainers

> **Student-facing equivalent:** [student-guides/day3/README.md](../../student-guides/day3/README.md)
> **Exercises:** Day 3 · TICKET-ADV033 – TICKET-ADV047 (15 hands-on exercises across three workshop blocks)
> **Theme:** Java Modules 3 & 4 — Functional + Testing
> **What it covers:** Streams, Collectors, CompletableFuture — then prove it works with TDD, Mockito, and a real Postgres in a container.

---

## Day at a glance

| #    | Block | Exercises | What students produce |
|------|-------|-----------|----------------------|
| 1 | Standup + Day-2 holdover unblock | — | Sealed `Trade` hierarchy compiles for everyone |
| 2 | AM Module 3 mini-lecture — Collections, generics, Streams, `java.time` | — | JShell live-coded examples |
| 3 | **Workshop 3A — Streams + Collectors + CompletableFuture** | TICKET-ADV033 – TICKET-ADV039 | `ReconciliationEngine` + analytics + parallel recon |
| 4 | PM Module 4 mini-lecture — JDBC, JUnit 5, Mockito, TDD | — | Red-green-refactor demo on a toy method |
| 5 | **Workshop 3B — TDD with JUnit 5 + Mockito** | TICKET-ADV040 – TICKET-ADV043 | Failing-then-passing tests around the engine |
| 6 | **Workshop 3C — Testcontainers + integration + coverage** | TICKET-ADV044 – TICKET-ADV047 | Live Postgres container, JaCoCo > 85% |
| 7 | End-of-day debrief | — | Tomorrow's preview (Spring Boot setup) |

**Day-3 follows the TOC, not the delivery-plan doc.** If a student waves the
delivery-plan doc and asks about Project Reactor, virtual threads, or
Quarkus — those have been **dropped from Day 3** by design. The whole point
of today is *Streams in anger* and *tests you can trust*. Don't get pulled
into reactive land.

---

## Pre-day instructor prep

The evening before Day 3:

- [ ] Open a JShell session: `jshell --enable-preview`. Have 4-5 ready-to-paste Stream snippets (`Stream.of(1,2,3).map(...).collect(...)`, `groupingBy`, `summarizingDouble`, a custom Collector skeleton). You will use JShell during the AM lecture.
- [ ] Confirm Docker Desktop is running on **every** student laptop before lunch. Testcontainers spins up a real Postgres in 2A — if Docker isn't healthy, half the class blocks at 16:00. **Walk the room at 13:00 and check.**
- [ ] Pre-pull the image: `docker pull postgres:16-alpine`. On a flaky conference Wi-Fi this saves 10 minutes per student.
- [ ] Verify the JUnit 5 + Mockito + Testcontainers dependencies are already declared in `backend/pom.xml`. They should be from Day-2 starter; if not, add them now (paste from the TICKET-ADV044 reference below).
- [ ] Have `mvn jacoco:report` ready as a terminal-history shortcut. You will demo opening `target/site/jacoco/index.html` after TICKET-ADV046.
- [ ] Re-read the student Day-3 README side-by-side with this one. Acceptance criteria live there; the answers live here.
- [ ] **Decide your TDD demo example.** A 4-line `isPalindrome(String)` works well — red, green, refactor in 3 minutes. Have it ready; do not improvise.
- [ ] Have a printed cheat-sheet of the four Collector parts (`supplier`, `accumulator`, `combiner`, `finisher`) — students *will* forget the combiner exists.

---

## Workshop 3A — Streams + Collectors + CompletableFuture (TICKET-ADV033 – TICKET-ADV039)

This is the hardest morning of the week so far. The shape is "rewrite the
naive for-loop reconciliation from Day 2 as a Stream pipeline, then
parallelise it". Expect frustration around `Collector` internals and
`CompletableFuture` ordering.

### TICKET-ADV033 — ReconciliationEngine using Streams

**What good looks like:** a `reconcile(List<Trade> internal, List<Trade> external)` method on `com.dbtraining.reconx.service.ReconciliationEngine` that uses `parallelStream()` for the internal side and joins against an indexed `Map<String, Trade>` of the external side.

**Common student blockers:**
- They write `internal.stream().filter(...).forEach(...)` with a side-effecting lambda mutating an external list. Mutable accumulators in lambdas are a *classic* anti-pattern.
- They put `parallelStream()` *and* a non-thread-safe `HashMap` accumulator together. Boom: lost results, no exception.
- They build an O(n*m) nested-loop join inside the stream and complain it's slow on 10k trades.

**Unblocking ladder:**
1. **Nudge:** "What's the time complexity of your join? Walk me through it on the whiteboard."
2. **Hint:** "If you indexed one side into a `Map<String, Trade>` first, what would each lookup cost?"
3. **Reveal:** Show the O(n+m) version below — index external side once, stream the internal side, do `Map.get` per element.

<details>
<summary>Full reference solution — ReconciliationEngine.java (TICKET-ADV033)</summary>

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.ReconResult;
import com.dbtraining.reconx.model.Trade;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

public class ReconciliationEngine {

    private static final BigDecimal PRICE_TOLERANCE = new BigDecimal("0.01");

    /**
     * Reconciles the internal trade book against an external counterparty feed.
     * Internal side is streamed in parallel; external side is pre-indexed by tradeRef.
     * Result map preserves insertion order of internal-side refs.
     */
    public Map<String, ReconResult> reconcile(List<Trade> internal,
                                              List<Trade> external) {
        if (internal == null || internal.isEmpty()) {
            return Map.of();
        }

        Map<String, Trade> externalIndex = external.stream()
                .collect(toMap(Trade::tradeRef, t -> t, (a, b) -> a));

        return internal.parallelStream()
                .map(t -> match(t, externalIndex.get(t.tradeRef())))
                .collect(toMap(ReconResult::tradeRef,
                               r -> r,
                               (a, b) -> a,
                               java.util.LinkedHashMap::new));
    }

    private ReconResult match(Trade internalTrade, Trade externalTrade) {
        if (externalTrade == null) {
            return ReconResult.breakResult(internalTrade.tradeRef(),
                    "MISSING_COUNTERPARTY_TRADE");
        }
        BigDecimal priceDiff = internalTrade.price().subtract(externalTrade.price()).abs();
        if (priceDiff.compareTo(PRICE_TOLERANCE) > 0) {
            return ReconResult.breakResult(internalTrade.tradeRef(), "PRICE_MISMATCH");
        }
        if (!internalTrade.quantity().equals(externalTrade.quantity())) {
            return ReconResult.breakResult(internalTrade.tradeRef(), "QUANTITY_MISMATCH");
        }
        return ReconResult.matched(internalTrade.tradeRef());
    }
}
```

</details>

**Talking point:** "Why `parallelStream` here and not in the controller?" Parallelism in a CPU-bound pure function is the right place; parallelism in I/O code (database calls, REST) saturates the shared common ForkJoinPool and breaks unrelated requests. We'll see this exact mistake in 2C if anyone wires the parallel stream into a `@Transactional` repo call.

**▶ Run the project — verify TICKET-ADV033 end-to-end**

Run the engine's unit test to confirm the streamed pipeline matches correctly.

```bash
./mvnw -pl backend test -Dtest=ReconciliationEngineTest
```

**Observe:**

- `ReconciliationEngineTest` reports green — exact-match scenario passes.
- The streamed pipeline returns results without `LazyInitializationException` or NPEs on empty input.
- Console shows the test taking well under a second on a 10k-trade pair.

### TICKET-ADV034 — Trade analytics with Collectors

**Common blockers:**
- They reach for `groupingBy(..., toList())` and then loop again to summarise. The `summarizingDouble` collector does it in one pass.
- They use `summarizingDouble` on a `BigDecimal` field — silent precision loss. Discuss it.

<details>
<summary>Full reference solution — TradeAnalytics.java (TICKET-ADV034)</summary>

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.Trade;

import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summarizingDouble;

public class TradeAnalytics {

    /**
     * NOTE: summarizingDouble loses BigDecimal precision. Accept this only for
     * dashboard-grade analytics, never for settlement-grade money math.
     */
    public Map<String, DoubleSummaryStatistics> notionalByCounterparty(List<Trade> trades) {
        return trades.stream().collect(
                groupingBy(
                    Trade::counterpartyId,
                    summarizingDouble(t -> t.price()
                                            .multiply(t.quantity())
                                            .doubleValue())));
    }
}
```

</details>

**Talking point:** the `DoubleSummaryStatistics` return type carries count, sum, min, max, avg in one shot — show them `getAverage()` vs `getCount()` in JShell so they see the API surface.

**▶ Run the project — verify TICKET-ADV034 end-to-end**

Unit-test the collector grouping to confirm per-counterparty aggregates.

```bash
./mvnw -pl backend test -Dtest=TradeAnalyticsServiceTest
```

**Observe:**

- Groups by counterparty produce the correct trade counts per bucket.
- `NotionalSummary` fields are immutable (record components) — no setter compiles.
- `BigDecimal` totals are exact — no precision loss compared to a hand-calculated sum.

### TICKET-ADV035 — VWAP via a custom Collector

VWAP = Σ(price × qty) / Σ(qty). A perfect first custom Collector: needs `supplier`, `accumulator`, `combiner`, `finisher`.

**Common blockers:**
- They omit the `combiner` and the test passes serially, then fails the moment someone parallelises it.
- They use a mutable `double[]` accumulator but write the combiner as `(a, b) -> a` (silently dropping half the data in parallel).

<details>
<summary>Full reference solution — VwapCollector.java (TICKET-ADV035)</summary>

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.Trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public final class VwapCollector
        implements Collector<Trade, VwapCollector.Acc, BigDecimal> {

    static final class Acc {
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal totalQty = BigDecimal.ZERO;
    }

    @Override public Supplier<Acc> supplier() { return Acc::new; }

    @Override public BiConsumer<Acc, Trade> accumulator() {
        return (acc, t) -> {
            acc.weighted = acc.weighted.add(t.price().multiply(t.quantity()));
            acc.totalQty = acc.totalQty.add(t.quantity());
        };
    }

    @Override public BinaryOperator<Acc> combiner() {
        return (a, b) -> {
            Acc out = new Acc();
            out.weighted = a.weighted.add(b.weighted);
            out.totalQty = a.totalQty.add(b.totalQty);
            return out;
        };
    }

    @Override public Function<Acc, BigDecimal> finisher() {
        return acc -> acc.totalQty.signum() == 0
                ? BigDecimal.ZERO
                : acc.weighted.divide(acc.totalQty, 6, RoundingMode.HALF_UP);
    }

    @Override public Set<Characteristics> characteristics() {
        return EnumSet.of(Characteristics.UNORDERED);
    }
}
```

</details>

**Talking point:** mark `UNORDERED` only when truly unordered (sum/average are; ranked lists are not). Wrong characteristics silently break parallel reduction.

**▶ Run the project — verify TICKET-ADV035 end-to-end**

Unit-test the custom VWAP collector on a known fixture.

```bash
./mvnw -pl backend test -Dtest=TradeAnalyticsServiceTest#vwap*
```

**Observe:**

- VWAP per instrument matches the hand-computed value to 4 decimal places.
- Serial-stream and parallel-stream invocations return identical `BigDecimal` results.
- Empty input returns `BigDecimal.ZERO` rather than throwing `ArithmeticException`.

### TICKET-ADV036 — P&L per instrument

<details>
<summary>Full reference solution — PnlCalculator.java (TICKET-ADV036)</summary>

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.Trade;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;

public class PnlCalculator {

    public Map<String, BigDecimal> pnlByInstrument(List<Trade> trades) {
        return trades.stream().collect(
                groupingBy(
                    Trade::instrumentSymbol,
                    reducing(BigDecimal.ZERO, Trade::pnl, BigDecimal::add)));
    }
}
```

</details>

**Talking point:** `reducing` with an identity (`BigDecimal.ZERO`) and an associative operator (`add`) is parallel-safe by construction. Contrast with the `for`-loop equivalent that needs an explicit `synchronized`.

**▶ Run the project — verify TICKET-ADV036 end-to-end**

Unit-test the P&L-per-instrument reducer.

```bash
./mvnw -pl backend test -Dtest=TradeAnalyticsServiceTest#pnl*
```

**Observe:**

- Mixed BUY/SELL fixture produces the hand-calculable per-instrument totals.
- Map keys are instrument symbols; values are `BigDecimal` (no `Double` rounding).
- Parallel stream over the same input produces the same totals — `reducing` is associative.

### TICKET-ADV037 — CompletableFuture: parallel recon by counterparty

This is the most error-prone exercise of the morning. The pattern: split trades by counterparty, fan out one `CompletableFuture` per counterparty, `allOf` to wait, merge.

**Common blockers:**
- They use `CompletableFuture.supplyAsync(...)` with no `Executor` argument → everything runs on the *common* ForkJoinPool. With 200 counterparties they saturate it and block JDBC threads.
- They call `.get()` inside a stream → checked-exception hell, then `.join()` works but they forget it can wrap a `CompletionException`.
- They `allOf(...)` and then iterate over the original futures list instead of `.thenApply`.

<details>
<summary>Full reference solution — ParallelReconciliationService.java (TICKET-ADV037)</summary>

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.ReconResult;
import com.dbtraining.reconx.model.Trade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ParallelReconciliationService {

    private final ReconciliationEngine engine;
    private final ExecutorService executor;

    public ParallelReconciliationService(ReconciliationEngine engine) {
        this.engine = engine;
        // Bounded pool — never use ForkJoinPool.commonPool for I/O-adjacent recon work.
        this.executor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()));
    }

    public Map<String, ReconResult> reconcileByCounterparty(List<Trade> internal,
                                                            List<Trade> external) {
        Map<String, List<Trade>> internalByCp =
                internal.stream().collect(Collectors.groupingBy(Trade::counterpartyId));
        Map<String, List<Trade>> externalByCp =
                external.stream().collect(Collectors.groupingBy(Trade::counterpartyId));

        List<CompletableFuture<Map<String, ReconResult>>> futures = internalByCp.entrySet().stream()
                .map(e -> CompletableFuture.supplyAsync(
                        () -> engine.reconcile(e.getValue(),
                                externalByCp.getOrDefault(e.getKey(), List.of())),
                        executor))
                .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(HashMap::new, Map::putAll, Map::putAll))
                .join();
    }

    public void shutdown() {
        executor.shutdown();
    }
}
```

</details>

**Talking point:** "Why not just `parallelStream` over the counterparties?" Because the common ForkJoinPool is shared across the whole JVM — Spring's HTTP threads, async tasks, every other `parallelStream` in the app. A bounded `ExecutorService` is bounded blast radius. Tie this back to Day 9 when Kafka consumers will need the same discipline.

**▶ Run the project — verify TICKET-ADV037 end-to-end**

Run the engine test to confirm per-counterparty futures merge correctly.

```bash
./mvnw -pl backend test -Dtest=ReconciliationEngineTest
```

**Observe:**

- Merged result size equals the sum of per-counterparty input sizes — nothing is lost.
- Each `CompletableFuture` runs on the explicit executor, not the JVM-wide common pool.
- No `LazyInitializationException` and no deadlock — `allOf().thenApply` releases the caller cleanly.

### TICKET-ADV038 — Custom Collector returning a ReconSummary

This is TICKET-ADV035 generalised. Same four-method skeleton, different domain object.

<details>
<summary>Full reference solution — ReconSummaryCollector.java + ReconSummary.java (TICKET-ADV038)</summary>

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.ReconResult;
import com.dbtraining.reconx.model.ReconResult.Status;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public final class ReconSummaryCollector
        implements Collector<ReconResult, ReconSummary.Builder, ReconSummary> {

    @Override public Supplier<ReconSummary.Builder> supplier() {
        return ReconSummary.Builder::new;
    }

    @Override public BiConsumer<ReconSummary.Builder, ReconResult> accumulator() {
        return (b, r) -> {
            b.total++;
            if (r.status() == Status.MATCHED) b.matched++;
            else b.broken++;
        };
    }

    @Override public BinaryOperator<ReconSummary.Builder> combiner() {
        return (a, b) -> {
            ReconSummary.Builder out = new ReconSummary.Builder();
            out.total   = a.total   + b.total;
            out.matched = a.matched + b.matched;
            out.broken  = a.broken  + b.broken;
            return out;
        };
    }

    @Override public Function<ReconSummary.Builder, ReconSummary> finisher() {
        return b -> new ReconSummary(b.total, b.matched, b.broken);
    }

    @Override public Set<Characteristics> characteristics() {
        return EnumSet.of(Characteristics.UNORDERED);
    }
}

// ---------- ReconSummary.java ----------
package com.dbtraining.reconx.service;

public record ReconSummary(long total, long matched, long broken) {
    public static ReconSummary empty() { return new ReconSummary(0, 0, 0); }

    public static final class Builder {
        long total;
        long matched;
        long broken;
    }
}
```

</details>

**Talking point:** when do you write a custom Collector vs. just chain built-ins? Custom Collector wins when (a) you need a domain object out, not a `Map`/`List`, AND (b) you want parallel correctness — your `combiner` is the contract.

**▶ Run the project — verify TICKET-ADV038 end-to-end**

Unit-test the custom `ReconSummaryCollector` against serial and parallel streams.

```bash
./mvnw -pl backend test -Dtest=ReconSummaryCollectorTest
```

**Observe:**

- A 10k-result parallel stream produces the same `ReconSummary` as the serial run — combiner is correct.
- `ReconSummary` record fields (`total`, `matched`, `broken`) are immutable after construction.
- `empty()` factory returns a summary with all counts at zero.

### TICKET-ADV039 — Optional chaining for null-safe lookups

**Common blockers:**
- They use `Optional.get()` defensively after an `isPresent()` check — works but defeats the point.
- They chain `.map` when they want `.flatMap` (because the lambda already returns `Optional<X>`).

<details>
<summary>Full reference solution — TradeLookupService.java (TICKET-ADV039)</summary>

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.Counterparty;
import com.dbtraining.reconx.model.Trade;
import com.dbtraining.reconx.repository.CounterpartyRepository;
import com.dbtraining.reconx.repository.TradeRepository;

import java.util.NoSuchElementException;

public class TradeLookupService {

    private final TradeRepository tradeRepo;
    private final CounterpartyRepository cpRepo;

    public TradeLookupService(TradeRepository tradeRepo, CounterpartyRepository cpRepo) {
        this.tradeRepo = tradeRepo;
        this.cpRepo = cpRepo;
    }

    public Counterparty counterpartyForTradeRef(String tradeRef) {
        return tradeRepo.findByRef(tradeRef)
                .map(Trade::counterpartyId)
                .flatMap(cpRepo::findById)
                .orElseThrow(() -> new NoSuchElementException(
                        "No counterparty resolvable for trade " + tradeRef));
    }
}
```

</details>

**Talking point:** `Optional` is a *return type*, never a field type. If anyone declares `private Optional<X> x;` push back — it serialises poorly, can be null itself, defeats the purpose.

**▶ Run the project — verify TICKET-ADV039 end-to-end**

Unit-test the Optional chain on present and missing trades.

```bash
./mvnw -pl backend test -Dtest=TradeLookupServiceTest
```

**Observe:**

- A trade with a resolvable counterparty returns the expected `Counterparty`.
- A missing trade ref throws `NoSuchElementException` carrying the ref in its message.
- Source grep of the method body shows zero occurrences of `.isPresent()` or `.get()`.

---

## Workshop 3B — TDD with JUnit 5 + Mockito (TICKET-ADV040 – TICKET-ADV043)

**Set the rule out loud at 14:15:** "Write the test first. If you start writing production code, I will stop you." Repeat this rule. They will break it. Stop them.

### TICKET-ADV040 — Exact-match TDD cycle

The shape: write a *failing* test → run it red → write the minimum code → run it green → refactor. This is **the** exercise where the methodology actually clicks. Watch for them to skip the red step.

<details>
<summary>Full reference solution — ReconciliationEngineTest.java (TICKET-ADV040)</summary>

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.ReconResult;
import com.dbtraining.reconx.model.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationEngineTest {

    private final ReconciliationEngine engine = new ReconciliationEngine();

    @Test
    @DisplayName("exact match on price + qty returns MATCHED")
    void testReconcile_exactMatch_returnsMatched() {
        // given
        Trade internal = new Trade("TRD-1", "CP-1", "SAP.DE",
                new BigDecimal("100"), new BigDecimal("245.50"), LocalDate.now());
        Trade external = new Trade("TRD-1", "CP-1", "SAP.DE",
                new BigDecimal("100"), new BigDecimal("245.50"), LocalDate.now());

        // when
        Map<String, ReconResult> results = engine.reconcile(List.of(internal), List.of(external));

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get("TRD-1").status()).isEqualTo(ReconResult.Status.MATCHED);
    }
}
```

</details>

**Talking point:** the `// given / when / then` comments are *not optional*. They are the contract that says "this test reads like a sentence". Reject PRs without them.

**▶ Run the project — verify TICKET-ADV040 end-to-end**

Drive the exact-match TDD cycle through `./mvnw test`.

```bash
./mvnw -pl backend test
```

**Observe:**

- `testReconcile_exactMatch_returnsMatched` shows green in the surefire output.
- AssertJ failure messages, if any, name the actual vs expected status — not raw `assertEquals` output.
- The `@DisplayName` reads like an English sentence in the IDE/test report.

### TICKET-ADV041 — Tolerance test with `@ParameterizedTest`

<details>
<summary>Full reference solution — additional test method (TICKET-ADV041)</summary>

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ParameterizedTest(name = "price diff {0} -> MATCHED")
@ValueSource(strings = {"0.000", "0.005", "0.010"})
@DisplayName("price differences within tolerance return MATCHED")
void testReconcile_priceTolerance_withinThreshold(String diff) {
    BigDecimal basePrice = new BigDecimal("100.000");
    Trade internal = new Trade("TRD-1", "CP-1", "SAP.DE",
            new BigDecimal("10"), basePrice, LocalDate.now());
    Trade external = new Trade("TRD-1", "CP-1", "SAP.DE",
            new BigDecimal("10"), basePrice.add(new BigDecimal(diff)), LocalDate.now());

    Map<String, ReconResult> results = engine.reconcile(List.of(internal), List.of(external));

    assertThat(results.get("TRD-1").status()).isEqualTo(ReconResult.Status.MATCHED);
}
```

</details>

**Talking point:** parameterised tests reduce *code surface*, not *test surface*. The same three asserts run three times. If a value should *fail*, write a separate `@ParameterizedTest` for the failing range — don't mix passing and failing inputs in one source.

**▶ Run the project — verify TICKET-ADV041 end-to-end**

Run the test suite and watch the parametrised rows expand in the surefire report.

```bash
./mvnw -pl backend test
```

**Observe:**

- Parametrised cases show one labelled row per value (`price diff 0.10 ...`, `0.50 ...`, `0.99 ...`).
- All rows are green — every diff inside tolerance returns `MATCHED`.
- No floating-point literals appear in either source or test output — values are `BigDecimal` parsed from strings.

### TICKET-ADV042 — Missing counterparty trade

<details>
<summary>Full reference solution — break-path test (TICKET-ADV042)</summary>

```java
@Test
@DisplayName("missing external trade returns BREAK with MISSING_COUNTERPARTY_TRADE")
void testReconcile_missingCounterpartyTrade_returnsBreak() {
    Trade internal = new Trade("TRD-MISSING", "CP-1", "SAP.DE",
            new BigDecimal("100"), new BigDecimal("245.50"), LocalDate.now());

    Map<String, ReconResult> results = engine.reconcile(List.of(internal), List.of());

    ReconResult r = results.get("TRD-MISSING");
    assertThat(r.status()).isEqualTo(ReconResult.Status.BREAK);
    assertThat(r.reason()).isEqualTo("MISSING_COUNTERPARTY_TRADE");
}
```

</details>

**Talking point:** asserting on the *reason string* is the right call — it's a stable API contract. Asserting on the exception message text would not be (messages drift). Asserting on `toString()` would *definitely* not be (covered in pitfalls below).

**▶ Run the project — verify TICKET-ADV042 end-to-end**

Run the test suite to confirm the break-path scenario asserts both fields.

```bash
./mvnw -pl backend test
```

**Observe:**

- `testReconcile_missingCounterpartyTrade_returnsBreak` is green.
- Two distinct `assertThat` assertions fire — status equals `BREAK` and reason equals `MISSING_EXTERNAL` by exact equality.
- The given/when/then comment block is visible in the source; the test reads top-to-bottom as a scenario.

### TICKET-ADV043 — Mockito ArgumentCaptor

This is where the engine starts collaborating with a repository. Demonstrate `verify(times)` first, then justify *why* `ArgumentCaptor` is better when you care about the *content* of the argument.

<details>
<summary>Full reference solution — ArgumentCaptor test (TICKET-ADV043)</summary>

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.ReconResult;
import com.dbtraining.reconx.model.Trade;
import com.dbtraining.reconx.repository.ReconResultRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReconciliationServiceTest {

    @Test
    void testReconcile_savesResultWithMatchedStatus() {
        // given
        ReconResultRepository repo = mock(ReconResultRepository.class);
        ReconciliationEngine engine = new ReconciliationEngine();
        ReconciliationService svc = new ReconciliationService(engine, repo);

        Trade i = new Trade("TRD-1", "CP-1", "SAP.DE",
                new BigDecimal("10"), new BigDecimal("100"), LocalDate.now());
        Trade e = new Trade("TRD-1", "CP-1", "SAP.DE",
                new BigDecimal("10"), new BigDecimal("100"), LocalDate.now());

        // when
        svc.runRecon(List.of(i), List.of(e));

        // then
        ArgumentCaptor<ReconResult> captor = ArgumentCaptor.forClass(ReconResult.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().tradeRef()).isEqualTo("TRD-1");
        assertThat(captor.getValue().status()).isEqualTo(ReconResult.Status.MATCHED);
    }
}
```

</details>

**Talking point:** `verify(repo, times(1)).save(any())` proves *that* save was called. `ArgumentCaptor` proves *what* was passed. Use Captor when the argument is the load-bearing assertion (almost always for "did we persist the right thing?").

**▶ Run the project — verify TICKET-ADV043 end-to-end**

Run the parallel reconciliation test and confirm fan-out timing beats sequential.

```bash
./mvnw -pl backend test -Dtest=ReconciliationEngineTest#testParallel
```

**Observe:**

- Wall-clock completion time is materially less than the sequential equivalent for the same input.
- Mockito `ArgumentCaptor` reads the saved `ReconResult` — `tradeRef` and `status` assertions pass.
- A thread-pool exhaustion warning appears in the log if the pool is sized smaller than the work fan-out.

---

## Workshop 3C — Testcontainers + integration + coverage (TICKET-ADV044 – TICKET-ADV047)

This is the afternoon's payoff. Real Postgres, real JDBC, real coverage report.
If Docker isn't healthy on someone's laptop, **send them to the working-pair
station** — don't let them block on infra.

### TICKET-ADV044 — Set up Testcontainers PostgreSQL

**Common blockers:**
- They forget `@Testcontainers` on the test class → the container is never started → `Connection refused`.
- They use `private` (not `static`) field with `@Container` → a fresh container per test method. Slow and confusing.
- They use `@DynamicPropertySource` but the method isn't `static` → Spring silently ignores it.

<details>
<summary>Full reference solution — test infrastructure (TICKET-ADV044)</summary>

```java
package com.dbtraining.reconx.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ReconciliationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reconx")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void containerIsRunning() {
        // sanity: if this passes, all your wiring is correct.
        // The real assertions live in TICKET-ADV045.
    }
}
```

Required dependency in `backend/pom.xml`:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>
```

</details>

**Talking point:** `static` container + `@Container` = shared across all `@Test` methods in the class (started once). Without `static`, each test gets a fresh one. Cost matters: 2s startup × 30 tests = 1 min wasted per run.

**▶ Run the project — verify TICKET-ADV044 end-to-end**

Run the integration phase with Docker Desktop active.

```bash
./mvnw -pl backend verify
docker ps
```

**Observe:**

- A Postgres container starts during the test run (`docker ps` shows `postgres:16-alpine`).
- The `containerIsRunning` sanity test passes — Spring's datasource resolved against the container.
- The container is auto-removed at the end of the test class; `docker ps` after the build is clean.

### TICKET-ADV045 — Integration test: insert → recon → verify

<details>
<summary>Full reference solution — end-to-end DB test (TICKET-ADV045)</summary>

```java
@Test
void insertedTradesAreReconciledAndPersisted() {
    // given — two matching trades, one in each repo
    Trade internal = new Trade("TRD-INT-1", "CP-1", "SAP.DE",
            new BigDecimal("100"), new BigDecimal("245.50"), LocalDate.now());
    Trade external = new Trade("TRD-INT-1", "CP-1", "SAP.DE",
            new BigDecimal("100"), new BigDecimal("245.50"), LocalDate.now());

    internalTradeRepo.save(internal);
    externalTradeRepo.save(external);

    // when
    reconciliationService.runRecon(
            internalTradeRepo.findAll(),
            externalTradeRepo.findAll());

    // then — exactly one MATCHED row landed in recon_results
    List<ReconResult> persisted = reconResultRepo.findAll();
    assertThat(persisted).hasSize(1);
    assertThat(persisted.get(0).status()).isEqualTo(ReconResult.Status.MATCHED);
    assertThat(persisted.get(0).tradeRef()).isEqualTo("TRD-INT-1");
}
```

</details>

**Talking point:** "Why not just mock the repository?" Mocks prove your *code path*. Testcontainers proves your *SQL works*. Both are valuable — different bug classes.

**▶ Run the project — verify TICKET-ADV045 end-to-end**

Run the integration test and confirm the entity ↔ domain round-trip survives the database.

```bash
./mvnw -pl backend verify
```

**Observe:**

- A matching pair of trades inserted via the repository round-trips back through the recon service.
- JSON serialisation/deserialisation of the persisted `ReconResult` preserves every field — no nulls after fetch.
- `reconResultRepo.findAll()` returns exactly one row with the right `tradeRef` and `MATCHED` status.

### TICKET-ADV046 — JaCoCo coverage report >85%

<details>
<summary>Full reference solution — pom.xml JaCoCo plugin (TICKET-ADV046)</summary>

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <includes>
                            <include>com.dbtraining.reconx.service.*</include>
                            <include>com.dbtraining.reconx.repository.*</include>
                        </includes>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.85</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Run: `./mvnw clean verify` → open `backend/target/site/jacoco/index.html`.

</details>

**Talking point:** 85%, not 100%. Why? Because the last 15% is usually trivial getters, generated builders, or exception branches that fire once a decade — chasing 100% means writing test theatre. 85% is "you tested the things that matter".

**▶ Run the project — verify TICKET-ADV046 end-to-end**

Run a full verify and open the coverage report.

```bash
./mvnw -pl backend clean verify
open backend/target/site/jacoco/index.html
```

**Observe:**

- `backend/target/site/jacoco/index.html` opens with line coverage ≥ 85% on `service.*` and `repository.*`.
- The `check` execution succeeds — green build.
- Dropping a service test (e.g. comment one out) and re-running produces a build error from the JaCoCo gate.

### TICKET-ADV047 — Refactor for edge cases

**The bugs to surface live:**
- Empty `internal` list → engine returns empty `Map`, not an NPE. Confirm with a test.
- Single trade, no external → returns `BREAK` for that one trade (already handled).
- All mismatched → every result is `BREAK`, total == broken in the summary.

<details>
<summary>Reference — before/after refactor of reconcile() (TICKET-ADV047)</summary>

**Before (vulnerable to edge cases):**

```java
public Map<String, ReconResult> reconcile(List<Trade> internal, List<Trade> external) {
    Map<String, Trade> externalIndex = external.stream()
            .collect(toMap(Trade::tradeRef, t -> t));   // NPE if external is null
    return internal.parallelStream()                    // NPE if internal is null
            .map(t -> match(t, externalIndex.get(t.tradeRef())))
            .collect(toMap(ReconResult::tradeRef, r -> r));
}
```

**After (edge cases handled):**

```java
public Map<String, ReconResult> reconcile(List<Trade> internal, List<Trade> external) {
    if (internal == null || internal.isEmpty()) {
        log.info("reconcile called with empty internal book — returning empty result");
        return Map.of();
    }
    List<Trade> safeExternal = external == null ? List.of() : external;

    Map<String, Trade> externalIndex = safeExternal.stream()
            .collect(toMap(Trade::tradeRef, t -> t, (a, b) -> a));

    if (internal.size() == 1 && safeExternal.isEmpty()) {
        log.warn("Single internal trade with no external feed — flagging as BREAK");
    }

    return internal.parallelStream()
            .map(t -> match(t, externalIndex.get(t.tradeRef())))
            .collect(toMap(ReconResult::tradeRef, r -> r, (a, b) -> a,
                           java.util.LinkedHashMap::new));
}
```

</details>

**Talking point:** the merge-function `(a, b) -> a` in `toMap` is not paranoia — `parallelStream` can produce duplicates if the input list has duplicate `tradeRef`s. Without the merge function, you get `IllegalStateException: Duplicate key`. Write a test for it.

**▶ Run the project — verify TICKET-ADV047 end-to-end**

Run the edge-case suite and confirm all three scenarios pass.

```bash
./mvnw -pl backend test -Dtest=ReconciliationEngineEdgeCasesTest
```

**Observe:**

- Empty-internal scenario returns an empty result list with no exception.
- All-mismatched scenario produces a 100% break ratio (`broken == 3`, `matched == 0`).
- Single-trade-no-external scenario is classified as `BREAK` with reason `MISSING_EXTERNAL` — never NPE.

---

<details>
<summary><b>Q&A bank</b></summary>


Likely student questions. Have answers loaded.

1. **"Why `parallelStream` and not parallel `CompletableFuture`?"** `parallelStream` is for **CPU-bound, pure** work on a known-size collection — fork-join splits the work, the JVM picks the worker count. `CompletableFuture` is for **independent async operations**, often I/O-bound, where *you* control the executor. Recon is CPU-bound (math + map lookups) → streams. Calling 200 external APIs → CompletableFuture with a bounded pool.
2. **"When should you write a custom Collector?"** When (a) you want a domain object out (not a `Map`/`List`) AND (b) you need parallel correctness (which means an associative `combiner`). Otherwise chain `groupingBy` / `mapping` / `reducing`.
3. **"TDD really test-first? Even for trivial code?"** Yes, *while learning*. The discipline of writing the failing test is the muscle. Once you can do TDD reflexively, you can drop it for trivial getters. Day 3 is about building the muscle.
4. **"`ArgumentCaptor` vs. `verify(times)`?"** `verify(times)` answers "was it called N times?". `ArgumentCaptor` answers "with what arguments?". Use Captor whenever the argument content is the assertion.
5. **"Testcontainers vs. `@DataJpaTest` with H2?"** H2 is a *different database* — different SQL dialect, different type coercion, different transaction semantics. Tests pass on H2, prod blows up on Postgres. Testcontainers runs the actual prod DB. The 2-second startup cost is the price of truth.
6. **"Why JaCoCo 85% not 100%?"** 85% covers the load-bearing logic; the last 15% is usually defensive branches, generated code, trivial getters, or impossible paths. Chasing 100% rewards test theatre — writing tests that hit a line without asserting anything meaningful.
7. **"`Optional.orElseThrow` vs `Optional.ifPresent`?"** `orElseThrow` is for "value must exist, fail loud if not". `ifPresent` is for side effects on an optional value. Don't `ifPresent { return x }` — that's a hint to use `map` + `orElse`.
8. **"`parallelStream` gotchas?"** Three big ones: (a) it uses the shared common ForkJoinPool, so a long-running stream blocks every other parallel stream in the JVM; (b) ordered terminals (`forEachOrdered`, `toList`) lose most parallelism benefit; (c) any state captured by lambdas must be thread-safe — that includes the `Map` you `put` into. Use `Collectors.toMap`, not `forEach(map::put)`.
9. **"Should the service be stateless?"** Yes. Services are singletons in Spring (Day 4). Any mutable field is a multi-threading bug waiting to happen. State lives in the database, the request, or a thread-local — never in the service.
10. **"Can I use `var` in tests?"** Yes for local variables. No for fields, return types, or method parameters. The rule is "if the reader can infer the type in 2 seconds, `var` is fine".
11. **"Why not Mockito-inline / mockStatic by default?"** Because if you need to mock a static, the design is usually telling you to inject a collaborator. Use static mocking only as a last resort (e.g. `LocalDate.now()`).
12. **"My Testcontainers is slow."** First run downloads the image (~80 MB). Subsequent runs reuse it. If still slow, check Docker Desktop's resource allocation — 6 GB RAM minimum for this stack.
13. **"`Stream.parallel()` vs `parallelStream()`?"** Identical. `parallelStream()` is sugar over `stream().parallel()`. Pick one and stick with it for readability.

---

</details>

<details>
<summary><b>End-of-day debrief prompts</b></summary>


At 17:00:

1. "On a whiteboard, sketch the four methods of `Collector` and what each does. Why is the `combiner` the contract for parallel safety?"
2. "Your team has a recon job that takes 30 seconds on 100k trades. Walk me through three places you'd profile first, and which Day-3 tool you'd reach for to fix the bottleneck."
3. "You wrote `verify(repo, times(1)).save(any())` — why is the `ArgumentCaptor` version of that test *also* worth writing? When is `times(1)` enough?"

If anyone can't answer #1 confidently, send them back to TICKET-ADV035/TICKET-ADV038 tomorrow morning before they start Day 4.

---

</details>

<details>
<summary><b>Things that have gone wrong before</b></summary>


- **`parallelStream` used inside a `@Transactional` method.**

  The stream forked work onto the common ForkJoinPool; each fork tried to grab a JDBC connection; the pool exhausted; the *whole app* froze for 30 seconds.

  **Fix:** never `parallelStream` inside a transaction. If you need parallel DB work, use a bounded `ExecutorService` and one transaction per task.

- **`CompletableFuture.supplyAsync(...)` without an `Executor`.**

  Defaulted to the common ForkJoinPool. With 200 concurrent recon tasks, the common pool saturated, blocking every other `parallelStream` in the JVM.

  **Fix:** always pass an explicit `Executor` to `supplyAsync`/`runAsync`. Bounded pools only.

- **Testcontainers without `@Testcontainers` annotation.**

  The `@Container` field was declared but the container was never started. Tests failed with `Connection refused`, took 40 minutes to diagnose.

  **Fix:** code-review *every* Testcontainers PR for the class-level `@Testcontainers` annotation. Make it a checklist item.

- **JaCoCo report missed integration tests because pom was configured for surefire only.**

  Coverage looked artificially low. Integration tests run under failsafe, not surefire — you need both `prepare-agent` and `prepare-agent-integration` executions.

  **Fix:** check `target/site/jacoco/index.html` lists *all* tested packages; if `repository` is at 0%, the agent didn't attach to the integration run.

- **Mockito stubbed a method that the SUT called via a different reference.**

  Team mocked `tradeRepo.findAll()` but the service injected `internalTradeRepo` (a different bean). Test always returned `[]`.

  **Fix:** when a Mockito stub seems to be ignored, log the actual injected bean inside the SUT — it's almost always a wiring mismatch.

- **TDD test asserted on `toString()`.**

  A trivial Lombok upgrade reordered fields in the generated `toString`, broke 40 tests.

  **Fix:** assert on *individual fields*, never on `toString()`. AssertJ's `extracting()` is the right hammer.

- **Custom Collector omitted the `combiner`.**

  Tests passed serially. When the same code ran in `parallelStream` on Day 4, half the trades vanished.

  **Fix:** write a *parallel-stream test* for every custom Collector. `Stream.generate(...).parallel().limit(10000)` is enough to surface a missing combiner.

- **Used `summarizingDouble` on `BigDecimal` notional.**

  Off-by-pence on every aggregate. Accountants noticed.

  **Fix:** for money, never `double`. Roll your own Collector that stays in `BigDecimal` if precision matters.

- **`@ParameterizedTest` with `@ValueSource(doubles = ...)` for prices.**

  Floating-point literals lost precision. `0.1 + 0.2 != 0.3`.

  **Fix:** use `@ValueSource(strings = ...)` and parse to `BigDecimal` inside the test. ---</details> <details> <summary><b>Hand-off to Day 4</b></summary>


By end-of-day each team should have:

- [ ] `ReconciliationEngine.reconcile(...)` working with `parallelStream` and indexed external lookup.
- [ ] `TradeAnalytics`, `VwapCollector`, `PnlCalculator` compiling and tested.
- [ ] `ParallelReconciliationService` using a *bounded* `ExecutorService` (not the common pool).
- [ ] `ReconSummaryCollector` with a correct `combiner` — verified by a parallel-stream test.
- [ ] `ReconciliationEngineTest` with at least one passing test per status (`MATCHED`, `BREAK` with each reason code).
- [ ] One `ArgumentCaptor`-based test proving the right `ReconResult` was persisted.
- [ ] A green Testcontainers integration test — Postgres really starts, really gets data, really verifies.
- [ ] `./mvnw clean verify` passes, JaCoCo report shows ≥85% on `service.*`.
- [ ] Edge cases (empty list, single trade, all mismatched) handled with explicit tests.

**Next:** [TrainersGuide/day4/](../day4/README.md) — Spring Boot enterprise setup, multi-module Maven, Hibernate Envers, MapStruct. Day 3's services become Spring `@Service` beans; the JDBC code becomes Spring Data JPA.

</details>
