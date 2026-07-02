# TrainersGuide — Day 2: Java Modules 1 & 2 — OOP Mastery + SOLID

> **Student-facing equivalent:** [student-guides/day2/README.md](../../student-guides/day2/README.md)
> **Exercises:** Day 2 · TICKET-ADV018 – TICKET-ADV032 (15 hands-on exercises across PM workshop blocks)
> **Theme:** Java Modules 1 & 2 — OOP Mastery + SOLID. Build the sealed `TradeType` hierarchy, value objects, exception ladder, and contract methods that the rest of the platform sits on.

---

## Day at a glance

| #    | Block | Exercises | What students produce |
|------|-------|-----------|----------------------|
| 1 | Standup + Day-1 holdover unblock | — | Schema green, naming inconsistency resolved |
| 2 | **AM Module 1 — OOP Mastery (lecture/JShell)** | — | 4 pillars + sealed hierarchies live-coded |
| 3 | **Workshop 2A — Sealed hierarchy + builders** | TICKET-ADV018 – TICKET-ADV022 | `TradeType` + 4 concrete trades with builders |
| 4 | **PM Module 2 — Strings, I/O, SOLID (lecture)** | — | Access modifiers + SOLID applied to recon |
| 5 | **Workshop 2B — Factories, value objects, exceptions, enums** | TICKET-ADV023 – TICKET-ADV026 | `TradeFactory`, `Money`, `TradeRef`, exception ladder, `ReconciliationRule` |
| 6 | **Workshop 2C — Contract methods, validation, docs** | TICKET-ADV027 – TICKET-ADV032 | `Comparable`, `equals/hashCode`, JSR-380, Javadoc, PR review |
| 7 | End-of-day debrief | — | Tomorrow's preview |

**The 15 exercises are tightly coupled — they all land in `com.dbtraining.reconx.model` and `com.dbtraining.reconx.exception`.** Don't let students fork off and "do their own thing"; if one team-mate's `TradeType` signature drifts from the other's `TradeFactory` return type, you'll lose 40 minutes mid-afternoon to merge conflicts. Force PR review per sub-workshop.

---

## Pre-day instructor prep

The evening before Day 2:

- [ ] **IntelliJ live-templates ready.** Have `psvm`, `sout`, `Builder` (Generate → Builder), and the Postfix `.var`/`.nn` templates muscle-memorised. You'll demo them at least twice each.
- [ ] **JShell open in a terminal.** `jshell --enable-preview` if you want to show pattern-matching switches. Day 2 is the day grads see "Java is actually interactive". Have these snippets pre-pasted in a scratch file:
  ```java
  sealed interface Shape permits Circle, Square {}
  record Circle(double r) implements Shape {}
  record Square(double s) implements Shape {}
  Shape sh = new Circle(3.0);
  String desc = switch (sh) { case Circle c -> "circle r=" + c.r(); case Square sq -> "square s=" + sq.s(); };
  ```
  Run live during AM Module 1. It collapses 30 minutes of slides into 90 seconds.
- [ ] **Decide records-vs-classes policy and announce at standup.** The recommendation for this codebase:
  - **Records** for value objects: `Money`, `TradeRef`, request DTOs.
  - **Sealed interface + final classes** for the `TradeType` hierarchy (NOT records — concrete trades have a `Builder`, mutable build state, and shared abstract logic that records can't host cleanly).
  - **Enum** for `ReconciliationRule`, `Side` (BUY/SELL), `OptionType` (CALL/PUT).
  If a team-mate asks "why not record EquityTrade?" — the answer is "records can't have a Builder inner class with the build-then-validate pattern; you'd lose your single point of invariant enforcement". Have that one-liner ready.
- [ ] **Pre-open the student Day-2 README + this trainer README side-by-side.** Acceptance criteria are in the student copy; reference solutions are here.
- [ ] **Re-read the Day-1 hand-off note.** If yesterday's team renamed `ReconResult` → `ReconBreak`, today's `ReconciliationMismatchException` references should line up. If they didn't, add the alias as part of TICKET-ADV025.
- [ ] **Have the Java 25 cheatsheet on a tab.** Sealed-class syntax, record compact constructors, and pattern-matching switch are the three new-to-grads features today. If JEP numbers come up: JEP 409 (sealed), JEP 395 (records), JEP 441 (pattern matching for switch).

---

## AM Module 1 lecture — OOP Mastery (1 hr, 60 min)

Run as **30 min slides + 30 min JShell live-code**. Do NOT let it run over — the workshop depends on the full 2 hr block. Cover:

1. **4 pillars** (encapsulation, inheritance, polymorphism, abstraction) — 5 min recap, assume they've seen this in Percipio.
2. **Constructors, factory methods, and the builder pattern** — telescoping-constructor anti-pattern → builder. Show the smell, then the cure.
3. **Inheritance vs composition** — quick. "Favour composition" is the slogan; sealed interfaces give you typed polymorphism *without* exposing extension.
4. **Sealed classes/interfaces (Java 25+)** — the headline new feature. "Polymorphism with a closed world." Pattern-matching switch becomes exhaustive.
5. **Abstract classes vs interfaces** — when to use which. Abstract for shared state + template methods; interface for capability.
6. **Exception hierarchy** — checked vs unchecked. For a Spring service, default to **unchecked** custom exceptions extending `RuntimeException`. Reason: checked exceptions force every layer to declare/rethrow, and `@RestControllerAdvice` (Day 4) handles unchecked cleanly.

**Talking point to plant for Workshop 2A:** "Today the goal is *enforce invariants at construction*. By 12:30 it should be *impossible* to instantiate a trade that violates the rules. Not 'we'll check later in the service' — impossible at the type system level."

---

## Workshop 2A — Sealed hierarchy + builders (TICKET-ADV018 – TICKET-ADV022, ~2 hr)

This is the spine of the model layer. If 2A lands clean, 2B and 2C drop in cleanly. If 2A drifts, the rest of the day collapses.

### TICKET-ADV018 — Sealed interface `TradeType` + abstract base

**Common student blockers:**
- They write a plain `interface TradeType` (no `sealed`) and only realise at PR review.
- They put `permits` on different lines / wrong order and the compiler can't find the permitted types because they're in different files but no `public` modifier.
- They forget that sealed interfaces require either `final`, `sealed`, or `non-sealed` modifier on every implementer. Compiler error reads scary; the fix is one keyword.
- Confusion: "do we need both the interface AND an abstract base class?" Answer: yes — interface for the closed-world polymorphism, abstract base for shared state (`tradeRef`, `tradeDate`, `notional`).

**Unblocking ladder:**
1. **Nudge:** "What does the compiler error literally say? Read the line after 'sealed'."
2. **Hint:** "Sealed types need every permitted type to opt into how they extend. Three keywords are valid — which are they?"
3. **Reveal:** Walk through `final`, `sealed`, `non-sealed` and which one applies to a concrete leaf class (final).

<details>
<summary>▶ Reference solution — TICKET-ADV018</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/model/TradeType.java
package com.dbtraining.reconx.model;

import java.time.LocalDate;

/**
 * Closed-world polymorphic type for every trade in the recon platform.
 *
 * <p>Adding a new asset class is a deliberate, reviewable act: extend the
 * {@code permits} clause AND add a case to every {@code switch} on
 * {@code TradeType}. The compiler will refuse to build until both happen.</p>
 */
public sealed interface TradeType
        permits EquityTrade, FXTrade, BondTrade, DerivativeTrade {

    /** The trade's natural key — globally unique, validated at construction. */
    TradeRef tradeRef();

    /** Notional value in the trade's settlement currency. */
    Money notional();

    /** Calendar date the trade was struck (not the settlement date). */
    LocalDate tradeDate();
}
```

```java
// File: backend/src/main/java/com/dbtraining/reconx/model/Trade.java
package com.dbtraining.reconx.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Shared state + behaviour for every concrete {@link TradeType}.
 *
 * <p>This class is <strong>package-private</strong> on purpose: the only legal
 * way to obtain a {@code TradeType} outside this package is via a concrete
 * builder or {@link TradeFactory}. The sealed contract lives on the interface;
 * the shared plumbing lives here.</p>
 */
abstract sealed class Trade
        permits EquityTrade, FXTrade, BondTrade, DerivativeTrade {

    protected final TradeRef tradeRef;
    protected final Money notional;
    protected final LocalDate tradeDate;

    protected Trade(TradeRef tradeRef, Money notional, LocalDate tradeDate) {
        this.tradeRef = Objects.requireNonNull(tradeRef, "tradeRef");
        this.notional = Objects.requireNonNull(notional, "notional");
        this.tradeDate = Objects.requireNonNull(tradeDate, "tradeDate");
    }
}
```

</details>

**Talking point:** "Why both `sealed interface TradeType` AND `abstract sealed class Trade`?" The interface is the *public* contract for downstream consumers (`switch (trade) { case EquityTrade e -> ... }`). The abstract class is the *internal* shared-state base; package-private because no-one outside `model` should extend it directly. This is exactly the **Open/Closed Principle** in action — open to add an asset class (extend permits), closed to arbitrary extension.

**▶ Run the project — verify TICKET-ADV018 end-to-end**

After this ticket, the sealed `TradeType` interface and its `AssetClass` enum exist; the project does not yet compile cleanly because the four permitted leaves are not implemented (that lands in ADV019–ADV022).

```bash
# from project root
./mvnw -pl backend compile
```

**Observe:**

- `TradeType.java` is picked up by the compiler — no "cannot find symbol" on `TradeType` itself.
- Compilation fails on the `permits` clause with a message naming `EquityTrade` / `FXTrade` / `BondTrade` / `DerivativeTrade` as missing — that is the *expected* failure state until the next four tickets land.
- Adding a stub `class Rogue implements TradeType {}` outside the permits list produces a distinct compile error ("is not allowed in the sealed hierarchy").

---

### TICKET-ADV019 — `EquityTrade` with Builder pattern

**Common student blockers:**
- They write a telescoping constructor "to ship faster" and add the builder "later". Later never comes.
- The `build()` method calls `new EquityTrade(...)` and the constructor *then* validates — but they forget to validate in `build()` first, so the IllegalStateException becomes an NPE deep in the constructor.
- They make the builder generic (`Builder<T extends Trade>`). Cute, doesn't pay off, skip it.
- Side enum forgotten — they use `String side` ("BUY"/"SELL"). Push back; type the domain.

**Unblocking ladder:**
1. **Nudge:** "If I forget to call `.price(...)`, when should the error fire — at field-set time or at build time?"
2. **Hint:** "Where does the invariant live: in the constructor, or split between the constructor and the builder?"
3. **Reveal:** Show that `build()` should validate the *builder state*, then call the constructor with non-null guarantees. Two layers of defence.

<details>
<summary>▶ Reference solution — TICKET-ADV019</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/model/EquityTrade.java
package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * An equity (stock) trade — buy or sell of N units of an instrument at a price.
 *
 * <p>Construct via {@link #builder()}; direct constructor access is reserved
 * for the {@link TradeFactory}. The build-then-validate pattern means a
 * malformed {@code EquityTrade} cannot exist — once you hold a reference,
 * every field is non-null and every invariant has passed.</p>
 */
public final class EquityTrade extends Trade implements TradeType {

    public enum Side { BUY, SELL }

    private final String instrumentSymbol;
    private final BigDecimal quantity;
    private final BigDecimal price;
    private final Side side;

    private EquityTrade(Builder b) {
        super(b.tradeRef, b.notional, b.tradeDate);
        this.instrumentSymbol = b.instrumentSymbol;
        this.quantity = b.quantity;
        this.price = b.price;
        this.side = b.side;
    }

    @Override public TradeRef tradeRef()  { return tradeRef; }
    @Override public Money    notional()  { return notional; }
    @Override public LocalDate tradeDate(){ return tradeDate; }

    public String     instrumentSymbol() { return instrumentSymbol; }
    public BigDecimal quantity()         { return quantity; }
    public BigDecimal price()            { return price; }
    public Side       side()             { return side; }

    public static Builder builder() { return new Builder(); }

    /** Fluent, mutable build-state for {@link EquityTrade}. NOT thread-safe. */
    public static final class Builder {
        private TradeRef tradeRef;
        private Money notional;
        private LocalDate tradeDate;
        private String instrumentSymbol;
        private BigDecimal quantity;
        private BigDecimal price;
        private Side side;

        public Builder tradeRef(TradeRef v)         { this.tradeRef = v; return this; }
        public Builder notional(Money v)            { this.notional = v; return this; }
        public Builder tradeDate(LocalDate v)       { this.tradeDate = v; return this; }
        public Builder instrumentSymbol(String v)   { this.instrumentSymbol = v; return this; }
        public Builder quantity(BigDecimal v)       { this.quantity = v; return this; }
        public Builder price(BigDecimal v)          { this.price = v; return this; }
        public Builder side(Side v)                 { this.side = v; return this; }

        public EquityTrade build() {
            // Layer 1: required-field gate
            Objects.requireNonNull(tradeRef,          "tradeRef is required");
            Objects.requireNonNull(notional,          "notional is required");
            Objects.requireNonNull(tradeDate,         "tradeDate is required");
            Objects.requireNonNull(instrumentSymbol,  "instrumentSymbol is required");
            Objects.requireNonNull(quantity,          "quantity is required");
            Objects.requireNonNull(price,             "price is required");
            Objects.requireNonNull(side,              "side is required");
            // Layer 2: invariants
            if (quantity.signum() <= 0)
                throw new IllegalStateException("quantity must be positive: " + quantity);
            if (price.signum() < 0)
                throw new IllegalStateException("price cannot be negative: " + price);
            if (instrumentSymbol.isBlank())
                throw new IllegalStateException("instrumentSymbol cannot be blank");
            return new EquityTrade(this);
        }
    }
}
```

</details>

**Talking point:** Two layers of defence on purpose. Layer 1 catches "you forgot a field" with a clear message; Layer 2 catches "the field is wrong shape". If you collapse them, the first NPE swallows the second check. Show them what the error message looks like when they collapse: `Cannot invoke "java.math.BigDecimal.signum()" because "this.quantity" is null` vs `quantity is required`. The diagnostic difference is the whole point.

**▶ Run the project — verify TICKET-ADV019 end-to-end**

After this ticket, `Side` and `EquityTrade` compile; an `EquityTrade` can only be constructed via the Builder, and missing required fields throw a named `NullPointerException` rather than a deep NPE inside the constructor.

```bash
# from project root
./mvnw -pl backend compile
./mvnw -pl backend test -Dtest=EquityTradeTest
```

**Observe:**

- `javap -p backend/target/classes/com/dbtraining/reconx/model/EquityTrade.class` shows the class is `final` and the only constructor is `private`.
- The unit test (`EquityTradeTest`) reports a happy-path build green and a "missing field" case throws `NullPointerException` with message `tradeRef` / `instrumentSymbol` etc.
- A negative `quantity` or `price` triggers `IllegalStateException("quantity must be > 0")` — caught before the object is ever returned.

---

### TICKET-ADV020 — `FXTrade` (two currencies + fxRate)

**Common student blockers:**
- Confused about which currency is the "notional" currency. Convention used in this codebase: **`notional` is in `ccy1`** (the base currency); `fxRate` is `ccy2/ccy1`.
- They use `String` for currencies instead of `java.util.Currency`. Push back — `Currency.getInstance("EUR")` validates the ISO 4217 code; `"EURR"` becomes a build failure not a runtime mystery on Day 5.

<details>
<summary>▶ Reference solution — TICKET-ADV020</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/model/FXTrade.java
package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/**
 * A spot FX trade — exchange {@code notionalCcy1} units of {@code ccy1}
 * for {@code notionalCcy1 * fxRate} units of {@code ccy2} at trade date.
 */
public final class FXTrade extends Trade implements TradeType {

    private final Currency   ccy1;
    private final Currency   ccy2;
    private final BigDecimal notionalCcy1;
    private final BigDecimal fxRate;

    private FXTrade(Builder b) {
        super(b.tradeRef, b.notional, b.tradeDate);
        this.ccy1         = b.ccy1;
        this.ccy2         = b.ccy2;
        this.notionalCcy1 = b.notionalCcy1;
        this.fxRate       = b.fxRate;
    }

    @Override public TradeRef tradeRef()  { return tradeRef; }
    @Override public Money    notional()  { return notional; }
    @Override public LocalDate tradeDate(){ return tradeDate; }

    public Currency   ccy1()         { return ccy1; }
    public Currency   ccy2()         { return ccy2; }
    public BigDecimal notionalCcy1() { return notionalCcy1; }
    public BigDecimal fxRate()       { return fxRate; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private TradeRef tradeRef;
        private Money notional;
        private LocalDate tradeDate;
        private Currency ccy1;
        private Currency ccy2;
        private BigDecimal notionalCcy1;
        private BigDecimal fxRate;

        public Builder tradeRef(TradeRef v)        { this.tradeRef = v; return this; }
        public Builder notional(Money v)           { this.notional = v; return this; }
        public Builder tradeDate(LocalDate v)      { this.tradeDate = v; return this; }
        public Builder ccy1(Currency v)            { this.ccy1 = v; return this; }
        public Builder ccy2(Currency v)            { this.ccy2 = v; return this; }
        public Builder notionalCcy1(BigDecimal v)  { this.notionalCcy1 = v; return this; }
        public Builder fxRate(BigDecimal v)        { this.fxRate = v; return this; }

        public FXTrade build() {
            Objects.requireNonNull(tradeRef,     "tradeRef is required");
            Objects.requireNonNull(notional,     "notional is required");
            Objects.requireNonNull(tradeDate,    "tradeDate is required");
            Objects.requireNonNull(ccy1,         "ccy1 is required");
            Objects.requireNonNull(ccy2,         "ccy2 is required");
            Objects.requireNonNull(notionalCcy1, "notionalCcy1 is required");
            Objects.requireNonNull(fxRate,       "fxRate is required");
            if (ccy1.equals(ccy2))
                throw new IllegalStateException("ccy1 and ccy2 must differ: " + ccy1);
            if (fxRate.signum() <= 0)
                throw new IllegalStateException("fxRate must be positive: " + fxRate);
            if (notionalCcy1.signum() <= 0)
                throw new IllegalStateException("notionalCcy1 must be positive: " + notionalCcy1);
            return new FXTrade(this);
        }
    }
}
```

</details>

**Talking point:** Notice the FX-specific invariant — `ccy1 != ccy2`. This is the kind of thing that's a 5-line bug report from Ops if you don't enforce it. *Domain-aware invariants are the entire point of having a model layer instead of just DTOs.*

**▶ Run the project — verify TICKET-ADV020 end-to-end**

After this ticket, `FXTrade` compiles, builder rejects equal `ccy1`/`ccy2`, bad ISO codes throw at the boundary (not three layers later), and `notional()` rolls up in the quote currency.

```bash
# from project root
./mvnw -pl backend test -Dtest=FXTradeTest
```

**Observe:**

- A happy-path build with `ccy1="EUR"`, `ccy2="USD"` succeeds and `notional().currency()` is `USD`.
- Builder call `.ccy1("EURR")` throws `IllegalArgumentException` from `Currency.getInstance(...)` immediately — not at `build()`.
- `ccy1=ccy2` (both `"EUR"`) makes `build()` throw `IllegalStateException("ccy1 and ccy2 must differ")`.
- No `String` currency field appears anywhere in `FXTrade` — only `java.util.Currency`.

---

### TICKET-ADV021 — `BondTrade` (couponRate, maturityDate, faceValue, isin)

**Common student blockers:**
- `couponRate` as `double` (rounding). Use `BigDecimal`.
- `isin` not validated — use a Pattern for length-12 alphanumeric in the builder, or defer to JSR-380 in TICKET-ADV029.
- `maturityDate` before `tradeDate` — push to fail-fast in `build()`.

<details>
<summary>▶ Reference solution — TICKET-ADV021</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/model/BondTrade.java
package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A fixed-income (bond) trade. {@code couponRate} is annual, decimal
 * (0.0425 = 4.25%); {@code faceValue} is per-bond.
 */
public final class BondTrade extends Trade implements TradeType {

    private final BigDecimal couponRate;
    private final LocalDate  maturityDate;
    private final BigDecimal faceValue;
    private final String     isin;

    private BondTrade(Builder b) {
        super(b.tradeRef, b.notional, b.tradeDate);
        this.couponRate   = b.couponRate;
        this.maturityDate = b.maturityDate;
        this.faceValue    = b.faceValue;
        this.isin         = b.isin;
    }

    @Override public TradeRef tradeRef()  { return tradeRef; }
    @Override public Money    notional()  { return notional; }
    @Override public LocalDate tradeDate(){ return tradeDate; }

    public BigDecimal couponRate()   { return couponRate; }
    public LocalDate  maturityDate() { return maturityDate; }
    public BigDecimal faceValue()    { return faceValue; }
    public String     isin()         { return isin; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private TradeRef tradeRef;
        private Money notional;
        private LocalDate tradeDate;
        private BigDecimal couponRate;
        private LocalDate maturityDate;
        private BigDecimal faceValue;
        private String isin;

        public Builder tradeRef(TradeRef v)        { this.tradeRef = v; return this; }
        public Builder notional(Money v)           { this.notional = v; return this; }
        public Builder tradeDate(LocalDate v)      { this.tradeDate = v; return this; }
        public Builder couponRate(BigDecimal v)    { this.couponRate = v; return this; }
        public Builder maturityDate(LocalDate v)   { this.maturityDate = v; return this; }
        public Builder faceValue(BigDecimal v)     { this.faceValue = v; return this; }
        public Builder isin(String v)              { this.isin = v; return this; }

        public BondTrade build() {
            Objects.requireNonNull(tradeRef,     "tradeRef is required");
            Objects.requireNonNull(notional,     "notional is required");
            Objects.requireNonNull(tradeDate,    "tradeDate is required");
            Objects.requireNonNull(couponRate,   "couponRate is required");
            Objects.requireNonNull(maturityDate, "maturityDate is required");
            Objects.requireNonNull(faceValue,    "faceValue is required");
            Objects.requireNonNull(isin,         "isin is required");
            if (couponRate.signum() < 0)
                throw new IllegalStateException("couponRate cannot be negative: " + couponRate);
            if (faceValue.signum() <= 0)
                throw new IllegalStateException("faceValue must be positive: " + faceValue);
            if (!maturityDate.isAfter(tradeDate))
                throw new IllegalStateException(
                        "maturityDate (" + maturityDate + ") must be after tradeDate (" + tradeDate + ")");
            if (isin.length() != 12)
                throw new IllegalStateException("isin must be 12 chars: " + isin);
            return new BondTrade(this);
        }
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV021 end-to-end**

After this ticket, `BondTrade` compiles, the builder refuses a maturity before the trade date, and every numeric field is `BigDecimal` — no `double`/`float` leaks through.

```bash
# from project root
./mvnw -pl backend test -Dtest=BondTradeTest
```

**Observe:**

- A happy-path build with `maturityDate` strictly after `tradeDate` succeeds and `notional()` returns `Money(faceValue, currency)`.
- Setting `maturityDate` before `tradeDate` makes `build()` throw `IllegalStateException("maturityDate cannot be before tradeDate")`.
- `grep -E "\\b(double|float)\\b" backend/src/main/java/com/dbtraining/reconx/model/BondTrade.java` returns nothing.
- ISIN length other than 12 is caught (either in the builder now or deferred to JSR-380 in ADV029, per your choice — document which).

---

### TICKET-ADV022 — `DerivativeTrade` (underlying, strike, expiry, optionType)

**Common student blockers:**
- `optionType` as `String` ("CALL"/"PUT"). Use an enum.
- They model strike as `double`. `BigDecimal`. Always.
- They forget that an expired derivative is a valid historical record — don't validate `expiry > today` in the model. Validate in the *service* if you must.

<details>
<summary>▶ Reference solution — TICKET-ADV022</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/model/DerivativeTrade.java
package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A vanilla equity / index option. Constructor is private; use
 * {@link #builder()}.
 */
public final class DerivativeTrade extends Trade implements TradeType {

    public enum OptionType { CALL, PUT }

    private final String     underlying;
    private final BigDecimal strike;
    private final LocalDate  expiry;
    private final OptionType optionType;

    private DerivativeTrade(Builder b) {
        super(b.tradeRef, b.notional, b.tradeDate);
        this.underlying = b.underlying;
        this.strike     = b.strike;
        this.expiry     = b.expiry;
        this.optionType = b.optionType;
    }

    @Override public TradeRef tradeRef()  { return tradeRef; }
    @Override public Money    notional()  { return notional; }
    @Override public LocalDate tradeDate(){ return tradeDate; }

    public String     underlying() { return underlying; }
    public BigDecimal strike()     { return strike; }
    public LocalDate  expiry()     { return expiry; }
    public OptionType optionType() { return optionType; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private TradeRef tradeRef;
        private Money notional;
        private LocalDate tradeDate;
        private String underlying;
        private BigDecimal strike;
        private LocalDate expiry;
        private OptionType optionType;

        public Builder tradeRef(TradeRef v)         { this.tradeRef = v; return this; }
        public Builder notional(Money v)            { this.notional = v; return this; }
        public Builder tradeDate(LocalDate v)       { this.tradeDate = v; return this; }
        public Builder underlying(String v)         { this.underlying = v; return this; }
        public Builder strike(BigDecimal v)         { this.strike = v; return this; }
        public Builder expiry(LocalDate v)          { this.expiry = v; return this; }
        public Builder optionType(OptionType v)     { this.optionType = v; return this; }

        public DerivativeTrade build() {
            Objects.requireNonNull(tradeRef,   "tradeRef is required");
            Objects.requireNonNull(notional,   "notional is required");
            Objects.requireNonNull(tradeDate,  "tradeDate is required");
            Objects.requireNonNull(underlying, "underlying is required");
            Objects.requireNonNull(strike,     "strike is required");
            Objects.requireNonNull(expiry,     "expiry is required");
            Objects.requireNonNull(optionType, "optionType is required");
            if (strike.signum() <= 0)
                throw new IllegalStateException("strike must be positive: " + strike);
            if (!expiry.isAfter(tradeDate))
                throw new IllegalStateException(
                        "expiry (" + expiry + ") must be after tradeDate (" + tradeDate + ")");
            if (underlying.isBlank())
                throw new IllegalStateException("underlying cannot be blank");
            return new DerivativeTrade(this);
        }
    }
}
```

</details>

**Talking point for end of 2A:** "Open `EquityTrade` and `BondTrade` side-by-side. What's the boilerplate?" The four builders share ~80% code. Some grads will want to extract a generic `AbstractTradeBuilder<T>`. Push back today — keep them simple and explicit; on Day 6 we'll revisit with the Lombok/codegen discussion. Premature abstraction has cost more careers than copy-paste.

**▶ Run the project — verify TICKET-ADV022 end-to-end**

After this ticket, all four sealed leaves exist and the project compiles end-to-end for the first time today. `DerivativeTrade` rejects expiry-before-trade-date but accepts an already-expired option as a valid historical record.

```bash
# from project root
./mvnw -pl backend compile
./mvnw -pl backend test -Dtest=DerivativeTradeTest
```

**Observe:**

- Full `compile` succeeds — no sealed-hierarchy warnings, no unresolved permits.
- Happy path with `optionType=CALL`, `expiry` after `tradeDate` builds and `assetClass()` returns `DERIVATIVE`.
- An `expiry` already in the past (relative to `LocalDate.now()` but after `tradeDate`) still builds — a historical option is valid data.
- An `expiry` before `tradeDate` throws `IllegalStateException("expiry cannot be before tradeDate")`.

---

## PM Module 2 lecture — Strings, I/O, SOLID (30 min, 30 min)

Tight. Cover, no more:

1. **String pool, `==` vs `.equals()`, `String.intern()`** — 5 min. Run a `==` snippet in JShell that prints `false` and watch their faces.
2. **Access modifiers, the real difference between `protected` and package-private** — 5 min. Show how `package-private` enforces the sealed boundary.
3. **NIO.2 (`Path`, `Files`, `Files.walk`)** — 5 min. Mention it; the Day 3 file-loader exercise uses it.
4. **`Serializable` and why we mostly don't use it any more** — 3 min. JSON-via-Jackson is the modern path; reserve `Serializable` for caches that need it.
5. **Multithreading — `Thread`, `Runnable`, `ExecutorService`, virtual threads (Java 25)** — 5 min. Day 3 uses `CompletableFuture` for parallel recon; today's job is just vocabulary.
6. **SOLID, applied to recon** — 7 min. The five letters with one ReconX example each:
   - **S** — `TradeFactory` ONLY constructs; it does not persist, validate beyond shape, or publish events.
   - **O** — `TradeType` sealed → open to new asset class (extend permits + add switch case), closed to arbitrary extension.
   - **L** — every `TradeType` must satisfy the contract (non-null `tradeRef`, non-null `notional`). No subclass returns null.
   - **I** — don't merge `TradeType` with `Persistable` or `Auditable`; small, focused interfaces.
   - **D** — services depend on `TradeRepository` (interface), not the JPA impl.

---

## Workshop 2B — Factories, value objects, exceptions, enums (TICKET-ADV023 – TICKET-ADV026, ~1.5 hr)

### TICKET-ADV023 — `TradeFactory`

**Common student blockers:**
- They write a giant `if/else` chain on the asset class string. Push to `switch` expression with pattern matching.
- The `Map<String, Object>` params bag invites `ClassCastException` at runtime. That's the *point* — the factory absorbs the cost so callers don't have to. But make sure the factory casts with a clear error message.
- They want to make the factory non-static. Don't — it's pure construction logic, no state.

<details>
<summary>▶ Reference solution — TICKET-ADV023</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/model/TradeFactory.java
package com.dbtraining.reconx.model;

import com.dbtraining.reconx.exception.InvalidTradeException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;

/**
 * Single entry-point for constructing a {@link TradeType} from a loosely-typed
 * input bag (e.g. parsed JSON, CSV row, Kafka payload).
 *
 * <p>The factory absorbs the type-erasure cost: callers hand in a
 * {@code Map<String,Object>} and a discriminator string, and get back a
 * fully-validated, typed trade — or an {@link InvalidTradeException}.</p>
 */
public final class TradeFactory {

    private TradeFactory() {}

    public static TradeType create(String assetClass, Map<String, Object> params) {
        Objects.requireNonNull(assetClass, "assetClass");
        Objects.requireNonNull(params,     "params");

        try {
            return switch (assetClass.toUpperCase()) {
                case "EQUITY"     -> buildEquity(params);
                case "FX"         -> buildFx(params);
                case "BOND"       -> buildBond(params);
                case "DERIVATIVE" -> buildDerivative(params);
                default -> throw new InvalidTradeException(
                        "Unknown assetClass: " + assetClass);
            };
        } catch (ClassCastException | NullPointerException | IllegalStateException ex) {
            throw new InvalidTradeException(
                    "Failed to build " + assetClass + " trade: " + ex.getMessage(), ex);
        }
    }

    private static EquityTrade buildEquity(Map<String, Object> p) {
        return EquityTrade.builder()
                .tradeRef(        (TradeRef)   p.get("tradeRef"))
                .notional(        (Money)      p.get("notional"))
                .tradeDate(       (LocalDate)  p.get("tradeDate"))
                .instrumentSymbol((String)     p.get("instrumentSymbol"))
                .quantity(        (BigDecimal) p.get("quantity"))
                .price(           (BigDecimal) p.get("price"))
                .side(            (EquityTrade.Side) p.get("side"))
                .build();
    }

    private static FXTrade buildFx(Map<String, Object> p) {
        return FXTrade.builder()
                .tradeRef(    (TradeRef)   p.get("tradeRef"))
                .notional(    (Money)      p.get("notional"))
                .tradeDate(   (LocalDate)  p.get("tradeDate"))
                .ccy1(        (Currency)   p.get("ccy1"))
                .ccy2(        (Currency)   p.get("ccy2"))
                .notionalCcy1((BigDecimal) p.get("notionalCcy1"))
                .fxRate(      (BigDecimal) p.get("fxRate"))
                .build();
    }

    private static BondTrade buildBond(Map<String, Object> p) {
        return BondTrade.builder()
                .tradeRef(    (TradeRef)   p.get("tradeRef"))
                .notional(    (Money)      p.get("notional"))
                .tradeDate(   (LocalDate)  p.get("tradeDate"))
                .couponRate(  (BigDecimal) p.get("couponRate"))
                .maturityDate((LocalDate)  p.get("maturityDate"))
                .faceValue(   (BigDecimal) p.get("faceValue"))
                .isin(        (String)     p.get("isin"))
                .build();
    }

    private static DerivativeTrade buildDerivative(Map<String, Object> p) {
        return DerivativeTrade.builder()
                .tradeRef(  (TradeRef)   p.get("tradeRef"))
                .notional(  (Money)      p.get("notional"))
                .tradeDate( (LocalDate)  p.get("tradeDate"))
                .underlying((String)     p.get("underlying"))
                .strike(    (BigDecimal) p.get("strike"))
                .expiry(    (LocalDate)  p.get("expiry"))
                .optionType((DerivativeTrade.OptionType) p.get("optionType"))
                .build();
    }
}
```

</details>

**Talking point:** the factory is the **boundary**. Inside it: type chaos (raw Maps, casts, null checks). Outside it: the `TradeType` sealed contract holds — every value is typed and validated. This is what "build a wall around your domain" means in practice.

**▶ Run the project — verify TICKET-ADV023 end-to-end**

After this ticket, `TradeFactory.create("EQUITY", map)` returns a typed `EquityTrade` from a loosely-typed `Map<String, Object>`, and unknown discriminators / bad casts surface cleanly (either as `IllegalArgumentException` from `valueOf` or — once translation is wired in ADV025 — as `InvalidTradeException`).

```bash
# from project root
./mvnw -pl backend test -Dtest=TradeFactoryTest
```

**Observe:**

- `TradeFactory.create("EQUITY", validMap)` returns a non-null `EquityTrade`; `instanceof EquityTrade` is true.
- `TradeFactory.create("FOO", map)` throws `IllegalArgumentException` (from `AssetClass.valueOf`) before any builder is touched.
- A missing key like `"price"` triggers an `NullPointerException` inside the builder's `requireNonNull("price")` — the named message points at the right field.
- `TradeFactory` has a private no-arg constructor and exposes only `static` methods (no instance state).

---

### TICKET-ADV024 — `Money` + `TradeRef` value objects

**Common student blockers:**
- They make `Money` a class with getters and setters. Stop — `record`.
- They use `double` for `amount`. `BigDecimal`. Non-negotiable.
- They put `Currency` as `String`. Use `java.util.Currency` — ISO-4217 enforcement is free.
- `TradeRef` regex is wrong on first try. Common errors: missing `^/$` anchors, allowing lowercase, wrong digit-count groups.

<details>
<summary>▶ Reference solution — TICKET-ADV024</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/model/Money.java
package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable, currency-aware monetary value. Negative amounts are rejected —
 * a "loss" is modelled by the trade's side (sell/buy), not by the sign of the
 * money. Equality is value-based.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount,   "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0)
            throw new IllegalArgumentException("amount must be >= 0: " + amount);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency))
            throw new IllegalArgumentException(
                    "currency mismatch: " + this.currency + " vs " + other.currency);
        return new Money(this.amount.add(other.amount), this.currency);
    }
}
```

```java
// File: backend/src/main/java/com/dbtraining/reconx/model/TradeRef.java
package com.dbtraining.reconx.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Globally-unique trade reference. Format: {@code XXX-YYYYMMDD-NNNN} where
 * {@code XXX} is the 3-letter asset class code (EQU/FXS/BND/DRV),
 * {@code YYYYMMDD} is the trade date, and {@code NNNN} is a zero-padded
 * intra-day sequence.
 *
 * <p>Example: {@code EQU-20260602-0001}</p>
 */
public record TradeRef(String value) {

    private static final Pattern PATTERN =
            Pattern.compile("^[A-Z]{3}-\\d{8}-\\d{4}$");

    public TradeRef {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches())
            throw new IllegalArgumentException(
                    "Invalid trade reference: '" + value + "' — expected XXX-YYYYMMDD-NNNN");
    }

    @Override public String toString() { return value; }
}
```

</details>

**Talking point:** "If `Money` is a record, can I subclass it?" No — records are implicitly `final`. That's deliberate. Value objects derive their *identity* from their *value*; subclassing `Money` makes no semantic sense. (Side question: what would `new SpecialMoney(10, GBP).equals(new Money(10, GBP))` return? Be glad you can't find out.)

**▶ Run the project — verify TICKET-ADV024 end-to-end**

After this ticket, `Money` and `TradeRef` are records, validate in their compact constructors, and provide value-based equality plus immutable arithmetic (`Money.plus` returns a new instance).

```bash
# from project root
./mvnw -pl backend test -Dtest=MoneyTest,TradeRefTest
```

**Observe:**

- `Money.of("100","USD").equals(Money.of("100","USD"))` is true; same values, same money.
- `Money.of("100","USD").plus(Money.of("50","USD"))` returns a *new* `Money(150, USD)`; the original receivers are unchanged.
- `Money.of("100","USD").plus(Money.of("50","EUR"))` throws `IllegalArgumentException` with a clear "currency mismatch" message.
- `TradeRef.of("EQU-20260602-0001")` succeeds; `TradeRef.of("foo")` and `TradeRef.of(null)` both throw with a message naming the expected format `AAA-YYYYMMDD-NNNN`.

---

### TICKET-ADV025 — Exception hierarchy

**Common student blockers:**
- They make `ReconException` `extends Exception` (checked). For a Spring service, default to `RuntimeException` (unchecked). Justify: cleanly handled by `@RestControllerAdvice` on Day 4.
- They forget the `(message, cause)` constructor and lose the root-cause stack on chained throws.
- They put domain-specific data in messages and leak it to logs. `"Trade EQU-20260602-0001 for counterparty 'ACME LLC' (LEI 5493…) failed"` is fine internally; sanitise before it hits external logs. We address with `toString()` masking in TICKET-ADV030.

<details>
<summary>▶ Reference solution — TICKET-ADV025</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/exception/ReconException.java
package com.dbtraining.reconx.exception;

/**
 * Root of the recon platform's exception hierarchy. Unchecked: Spring's
 * {@code @RestControllerAdvice} (Day 4) maps every {@link ReconException}
 * subtype to a structured error response.
 */
public abstract class ReconException extends RuntimeException {

    protected ReconException(String message) {
        super(message);
    }

    protected ReconException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

```java
// File: backend/src/main/java/com/dbtraining/reconx/exception/InvalidTradeException.java
package com.dbtraining.reconx.exception;

/** Thrown when a trade fails construction-time validation. HTTP 400. */
public class InvalidTradeException extends ReconException {
    public InvalidTradeException(String message)                    { super(message); }
    public InvalidTradeException(String message, Throwable cause)   { super(message, cause); }
}
```

```java
// File: backend/src/main/java/com/dbtraining/reconx/exception/ReconciliationMismatchException.java
package com.dbtraining.reconx.exception;

/** Thrown when reconciliation finds an actionable break. HTTP 409. */
public class ReconciliationMismatchException extends ReconException {
    public ReconciliationMismatchException(String message)                  { super(message); }
    public ReconciliationMismatchException(String message, Throwable cause) { super(message, cause); }
}
```

```java
// File: backend/src/main/java/com/dbtraining/reconx/exception/TradeNotFoundException.java
package com.dbtraining.reconx.exception;

/** Thrown when a lookup by {@code TradeRef} returns no result. HTTP 404. */
public class TradeNotFoundException extends ReconException {
    public TradeNotFoundException(String message)                  { super(message); }
    public TradeNotFoundException(String message, Throwable cause) { super(message, cause); }
}
```

```java
// File: backend/src/main/java/com/dbtraining/reconx/exception/DuplicateTradeRefException.java
package com.dbtraining.reconx.exception;

/** Thrown when persisting a trade whose {@code tradeRef} already exists. HTTP 409. */
public class DuplicateTradeRefException extends ReconException {
    public DuplicateTradeRefException(String message)                  { super(message); }
    public DuplicateTradeRefException(String message, Throwable cause) { super(message, cause); }
}
```

</details>

**▶ Run the project — verify TICKET-ADV025 end-to-end**

After this ticket, the four-level exception ladder compiles, every subtype extends `ReconException` (which is abstract), and the message + cause are preserved through the chain.

```bash
# from project root
./mvnw -pl backend test -Dtest=ReconExceptionTest
```

**Observe:**

- Every subtype `extends ReconException` — verify with `javap -p backend/target/classes/com/dbtraining/reconx/exception/InvalidTradeException.class` (and the other three).
- `ReconException` itself cannot be instantiated directly — `new ReconException("x")` is a compile error because the class is `abstract`.
- A `new InvalidTradeException("bad trade", rootCause)` preserves the cause: `e.getCause() == rootCause` is true.
- All five classes sit in `com.dbtraining.reconx.exception` — `find backend/src/main/java/com/dbtraining/reconx/exception -name '*.java'` lists exactly five files.

---

### TICKET-ADV026 — `ReconciliationRule` enum

**Common student blockers:**
- They want a config table for the thresholds. Push back today — the *set* of rules is closed; the thresholds within them are constants. If those constants need to be tuneable later, lift them into `@ConfigurationProperties` on Day 4. Don't pre-engineer.
- They use `double` for tolerances. `BigDecimal`. Forever.

<details>
<summary>▶ Reference solution — TICKET-ADV026</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/model/ReconciliationRule.java
package com.dbtraining.reconx.model;

import java.math.BigDecimal;

/**
 * Closed set of reconciliation tolerance rules. Each enum constant carries
 * the price and quantity tolerances it applies. A {@code ReconciliationEngine}
 * (Day 3) picks the rule by name and compares against the trade pair.
 */
public enum ReconciliationRule {

    /** Strict match — no tolerance on either field. */
    EXACT(BigDecimal.ZERO, BigDecimal.ZERO),

    /** Allow up to 1% price drift, quantities must match exactly. */
    PRICE_TOLERANCE_1PCT(BigDecimal.ONE, BigDecimal.ZERO),

    /** Allow up to 5-unit quantity drift, prices must match exactly. */
    QTY_TOLERANCE_5UNITS(BigDecimal.ZERO, BigDecimal.valueOf(5)),

    /** Permissive — 1% price drift AND 5-unit quantity drift. */
    LOOSE(BigDecimal.ONE, BigDecimal.valueOf(5));

    private final BigDecimal priceTolerancePct;
    private final BigDecimal qtyToleranceAbs;

    ReconciliationRule(BigDecimal priceTolerancePct, BigDecimal qtyToleranceAbs) {
        this.priceTolerancePct = priceTolerancePct;
        this.qtyToleranceAbs   = qtyToleranceAbs;
    }

    public BigDecimal priceTolerancePct() { return priceTolerancePct; }
    public BigDecimal qtyToleranceAbs()   { return qtyToleranceAbs; }

    /** {@code true} if {@code (a, b)} is within this rule's tolerances. */
    public boolean matches(BigDecimal priceA, BigDecimal priceB,
                           BigDecimal qtyA,   BigDecimal qtyB) {
        BigDecimal priceDriftPct = priceA.subtract(priceB).abs()
                .divide(priceA, 6, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        BigDecimal qtyDrift = qtyA.subtract(qtyB).abs();
        return priceDriftPct.compareTo(priceTolerancePct) <= 0
                && qtyDrift.compareTo(qtyToleranceAbs) <= 0;
    }
}
```

</details>

**Talking point:** enum + behaviour (the `matches` method) is one of Java's underused features. Each constant is its own object; methods dispatch polymorphically. This collapses what would be a strategy-pattern hierarchy into ~30 lines.

**▶ Run the project — verify TICKET-ADV026 end-to-end**

After this ticket, `ReconciliationRule` is an enum whose constants each carry their own tolerances, and `matches(...)` correctly applies them with `BigDecimal.compareTo` — no `double`, no `==` on numerics.

```bash
# from project root
./mvnw -pl backend test -Dtest=ReconciliationRuleTest
```

**Observe:**

- `ReconciliationRule.EXACT.matches(p, q, p, q)` is true; any drift returns false.
- `PRICE_TOLERANCE_1PCT.matches(100, 10, 100.5, 10)` is true (0.5 % drift), but `.matches(100, 10, 102, 10)` is false (2 %).
- `QTY_TOLERANCE_5UNITS.matches(100, 10, 100, 14)` is true; `.matches(100, 10, 100, 16)` is false.
- `grep -E "\\b(double|float)\\b" backend/src/main/java/com/dbtraining/reconx/model/ReconciliationRule.java` returns nothing.

---

## Workshop 2C — Contract methods, validation, docs (TICKET-ADV027 – TICKET-ADV032, ~1.25 hr)

### TICKET-ADV027 — `Comparable<TradeType>` natural ordering

**Common student blockers:**
- They compare on a nullable field. NPE on the first heterogeneous list.
- They make ordering depend on `id` (database-generated), which is null pre-persist. `TreeSet<Trade>` then silently corrupts.
- They don't make ordering consistent with `equals` (see TICKET-ADV028). `TreeSet` then drops duplicates that `equals` says aren't duplicates.

<details>
<summary>▶ Reference solution — TICKET-ADV027</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/model/TradeType.java
// (Extension to the interface defined in TICKET-ADV018)
package com.dbtraining.reconx.model;

import java.time.LocalDate;
import java.util.Comparator;

public sealed interface TradeType extends Comparable<TradeType>
        permits EquityTrade, FXTrade, BondTrade, DerivativeTrade {

    TradeRef tradeRef();
    Money notional();
    LocalDate tradeDate();

    /**
     * Natural ordering: newest trade-date first, ties broken by
     * {@link TradeRef} ascending. Consistent with {@code equals} (which is
     * also keyed on {@code tradeRef}), so safe for {@code TreeSet}.
     */
    Comparator<TradeType> NATURAL = Comparator
            .comparing(TradeType::tradeDate, Comparator.reverseOrder())
            .thenComparing(t -> t.tradeRef().value());

    @Override
    default int compareTo(TradeType other) {
        return NATURAL.compare(this, other);
    }
}
```

</details>

**Talking point:** ordering and equality must agree, or `TreeSet`/`TreeMap` behave bizarrely (an element with `compareTo == 0` is "equal" to the set). We key both on `TradeRef` so they're consistent by construction.

**▶ Run the project — verify TICKET-ADV027 end-to-end**

After this ticket, `TradeType` extends `Comparable<TradeType>`, a shared `NATURAL` comparator orders newest trade date first with `tradeRef` as the tiebreaker, and a heterogeneous `TreeSet<TradeType>` sorts deterministically.

```bash
# from project root
./mvnw -pl backend test -Dtest=TradeTypeOrderingTest
```

**Observe:**

- A `TreeSet<TradeType>` built from one Equity / FX / Bond / Derivative trade iterates newest-first by `tradeDate`, no `NullPointerException`.
- Two trades sharing the same `tradeDate` are ordered by `tradeRef.value()` ascending — stable, repeatable.
- `t1.compareTo(t2) == 0` is true *only* when `t1.tradeRef().equals(t2.tradeRef())` — keeping ordering consistent with the equality you wire in ADV028.
- `javap -p backend/target/classes/com/dbtraining/reconx/model/TradeType.class` shows `Comparable` in the supertypes.

---

### TICKET-ADV028 — `equals`/`hashCode` keyed on `tradeRef`

**Common student blockers:**
- They IDE-generate on every field. Two trades with the same `tradeRef` but different `quantity` (because one's been amended) compare unequal — that's wrong; `tradeRef` is the natural key.
- They use `id` (database-generated). It's null before persist. `Set<Trade>` then has two distinct copies of the same unpersisted trade.
- They override `equals` but forget `hashCode` (or vice versa). `HashMap<Trade, X>` then misbehaves.

<details>
<summary>▶ Reference solution — TICKET-ADV028</summary>

```java
// Add to EquityTrade (and identically to FXTrade, BondTrade, DerivativeTrade)

@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TradeType other)) return false;   // pattern-match across the hierarchy
    return this.tradeRef.equals(other.tradeRef());
}

@Override
public int hashCode() {
    return tradeRef.hashCode();
}
```

</details>

**Talking point — the JPA-vs-domain distinction:** "But JPA generates equality on `@Id` for us!" Yes — and the JPA `@Id` is the **surrogate** key (auto-generated row id). The domain natural key is `tradeRef`. They're different concepts; we want **domain** equality, not row-identity equality. JPA equality bites you the moment you compare a detached entity to a freshly-constructed one. Stick with `tradeRef`.

Also note: the pattern-match `o instanceof TradeType other` means an `EquityTrade` and an `FXTrade` with the same (impossible-in-practice) `tradeRef` would compare equal. That's the correct domain behaviour — `tradeRef` is globally unique, so this is a no-op in practice but is the right contract.

**▶ Run the project — verify TICKET-ADV028 end-to-end**

After this ticket, every concrete trade overrides `equals` and `hashCode` keyed on `tradeRef`, so a `HashSet<TradeType>` of two trades that share a `tradeRef` (but differ on other fields) has size 1.

```bash
# from project root
./mvnw -pl backend test -Dtest=TradeEqualityTest
```

**Observe:**

- `new HashSet<>(List.of(t1, t2))` has size **1** when `t1.tradeRef().equals(t2.tradeRef())`, even if quantity / price differ.
- `t1.equals(t2)` ⇔ `t1.hashCode() == t2.hashCode()` — the contract holds for every concrete trade.
- `t1.equals(t2)` is consistent with `t1.compareTo(t2) == 0` from ADV027 — same key, same semantics.
- Cross-type equality is `false`: an `EquityTrade` and an `FXTrade` with the *same* `tradeRef` are not equal (each `equals` body uses concrete-class `instanceof`).

---

### TICKET-ADV029 — JSR-380 validation on the request DTO

**Common student blockers:**
- They put `@NotNull` on the domain class fields. Doesn't fire — the model is constructed via the builder, not by Spring's binding. **JSR-380 lives on the inbound DTO**, not the domain.
- They use `@NotEmpty` on a `BigDecimal` (compile error). `@NotNull` + `@Positive` is the recipe.
- They forget to add `@Validated` on the controller (covered Day 5) and wonder why validation never fires.

<details>
<summary>▶ Reference solution — TICKET-ADV029</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/dto/TradeRequest.java
package com.dbtraining.reconx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inbound HTTP body for trade creation. Validation happens at the controller
 * boundary via {@code @Valid}; the domain layer assumes the DTO has already
 * passed.
 */
public record TradeRequest(

        @NotNull
        @Pattern(regexp = "^[A-Z]{3}-\\d{8}-\\d{4}$",
                 message = "tradeRef must match XXX-YYYYMMDD-NNNN")
        String tradeRef,

        @NotBlank
        @Size(min = 1, max = 20)
        String instrumentSymbol,

        @NotNull
        @Positive
        BigDecimal quantity,

        @NotNull
        @PositiveOrZero
        BigDecimal price,

        @NotNull
        LocalDate tradeDate,

        @NotBlank
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
        String currency,

        @NotBlank
        @Pattern(regexp = "BUY|SELL", message = "side must be BUY or SELL")
        String side
) {}
```

</details>

**Talking point — why two layers of validation?** Bean Validation (JSR-380) is *declarative* and runs at the HTTP boundary; the builder validation is *imperative* and protects the domain. They cover different attack surfaces. JSR-380 catches "the JSON was malformed"; the builder catches "the assembly code skipped a field". Belt and braces.

**▶ Run the project — verify TICKET-ADV029 end-to-end**

After this ticket, `TradeRequest` compiles as a record in `com.dbtraining.reconx.dto` with JSR-380 annotations on every component; the controller side will be wired up in Day 3+.

```bash
# from project root
./mvnw -pl backend compile
./mvnw -pl backend test -Dtest=TradeRequestValidationTest
```

**Observe:**

- All imports come from `jakarta.validation.constraints.*` — `grep "javax.validation" backend/src/main/java/com/dbtraining/reconx/dto/TradeRequest.java` returns nothing.
- A `Validator.validate(...)` call against a well-formed `TradeRequest` returns zero violations; a request with `quantity = -1` returns exactly one violation on `quantity` with the `@Positive` message.
- A bad `tradeRef` like `"foo"` produces a violation tagged with the regex message `"tradeRef must match AAA-YYYYMMDD-NNNN"`.
- Annotations live only on the DTO; no `jakarta.validation` import appears in any class under `com.dbtraining.reconx.model`.

---

### TICKET-ADV030 — `toString()` for logging (no PII leak)

**Common student blockers:**
- IDE-generated `toString` includes every field — including LEI codes, internal account ids, account numbers in payment instructions. **That's a regulatory issue.**
- They include `toString()` of nested objects without checking — a `Counterparty.toString()` that includes the full LEI flows through.
- They format `BigDecimal` with `.toString()` which can use scientific notation. Use `toPlainString()` or skip.

<details>
<summary>▶ Reference solution — TICKET-ADV030</summary>

```java
// In EquityTrade.java
@Override
public String toString() {
    return "EquityTrade[ref=%s, symbol=%s, qty=%s, price=%s, side=%s]"
            .formatted(
                    tradeRef.value(),
                    instrumentSymbol,
                    quantity.toPlainString(),
                    price.toPlainString(),
                    side);
    // NOTE: notional and counterparty intentionally omitted from the default
    // toString — they include PII (counterparty LEI, settlement currency
    // amounts). Use the dedicated `toAuditString()` (Day 4) when full
    // detail is required and the log sink is approved for PII.
}
```

</details>

**Talking point:** the rule is "default `toString` = safe for application logs". Anything sensitive needs an explicit, opt-in formatter. The cost of one PII leak in Splunk far outweighs the cost of one extra method.

**▶ Run the project — verify TICKET-ADV030 end-to-end**

After this ticket, every concrete trade has a hand-written `toString` that includes commercial fields but excludes `counterpartyId` — the platform's PII line.

```bash
# from project root
./mvnw -pl backend test -Dtest=TradeToStringTest
```

**Observe:**

- For each concrete trade `t`, `t.toString().contains(String.valueOf(t.counterpartyId()))` is **false**.
- `EquityTrade.toString()` includes `ref`, `symbol`, `qty`, `price`, currency code, and `side` — no other fields.
- `BigDecimal` fields render as plain decimals (no `1E+2` scientific notation) — i.e. backed by `toPlainString()` or a format spec.
- A `// NOTE:` comment in each `toString` lists the deliberately-omitted fields so a future maintainer does not "fix" the omission.

---

### TICKET-ADV031 — Javadoc on all public domain classes

**Common student blockers:**
- They write `@param tradeRef the tradeRef` (vacuous). The bar is "the line after the field name adds information you can't get from the field name".
- They Javadoc private methods. Wasted effort; Javadoc is for the *contract*, which is the public surface.
- They skip `@throws`. The exception is part of the contract; documenting it is non-optional.

<details>
<summary>▶ Reference solution — TICKET-ADV031</summary>

```java
// In EquityTrade.Builder
/**
 * Build the immutable {@link EquityTrade}, validating that every required
 * field is set and that all invariants hold.
 *
 * @return a fully-constructed, validated {@code EquityTrade} — never {@code null}.
 * @throws NullPointerException  if any required field
 *                               ({@code tradeRef}, {@code notional},
 *                               {@code tradeDate}, {@code instrumentSymbol},
 *                               {@code quantity}, {@code price}, {@code side})
 *                               was not set.
 * @throws IllegalStateException if {@code quantity} is not strictly positive,
 *                               {@code price} is negative, or
 *                               {@code instrumentSymbol} is blank.
 */
public EquityTrade build() {
    // ... see TICKET-ADV019
}
```

</details>

**Talking point:** good Javadoc reads like a function spec a junior dev could implement against without seeing the body. If you can't do that, the doc isn't done.

**▶ Verify the artifact — TICKET-ADV031 Javadoc**

After this ticket, the Javadoc tool produces a complete site for `com.dbtraining.reconx.model` and `com.dbtraining.reconx.exception` with no warnings; the rendered HTML reads like a spec, not a code echo.

```bash
# from project root
./mvnw -pl backend javadoc:javadoc
open backend/target/site/apidocs/index.html
```

**Verify the artifact contains:**

- [ ] Every public class in `model/` and `exception/` has a class-level Javadoc block (the `WHAT / HOW / WHY` shape used in the trainer copy).
- [ ] Every public method has `@param`, `@return`, `@throws` as appropriate — including unchecked exceptions where they are part of the contract (`requireNonNull`, `IllegalStateException` in builders).
- [ ] `./mvnw -pl backend javadoc:javadoc` exits cleanly with no `[WARNING]` lines.
- [ ] No vacuous `@param tradeRef the tradeRef` style entries — the rendered HTML adds information beyond the name.
- [ ] Private helpers and self-explanatory builder setters are *not* documented — the focus is the public surface.

---

### TICKET-ADV032 — PR review (2 approvals required)

This is a **process exercise** but don't treat it as fluff. It's the moment two people on the team have to *agree* that the model contract is right. If they don't, today's wobbles become Day-4 fires.

**Trainer's PR-review checklist** (post in the team's Slack):
- [ ] Every concrete `TradeType` is `final` (sealed leaf).
- [ ] No `public` constructor on any concrete trade — only via builder.
- [ ] All `BigDecimal` fields, no `double`/`float`.
- [ ] `Currency` is `java.util.Currency`, not `String`.
- [ ] `equals`/`hashCode` keyed on `tradeRef`, not `id`.
- [ ] `Comparable.compareTo` consistent with `equals`.
- [ ] No `toString()` includes counterparty PII / LEI.
- [ ] JSR-380 on the DTO, not the domain class.
- [ ] Every public class / method has Javadoc.
- [ ] Exception hierarchy extends `ReconException` (not raw `RuntimeException`).
- [ ] `TradeFactory` only constructs — no persistence, no events, no logging beyond errors.

If a PR fails any of these, **block the merge.** A team that ships a leaky model on Day 2 spends Days 4–5 unwinding it.

**▶ Verify the artifact — TICKET-ADV032 PR**

After this ticket, the PR is open against the team integration branch with the reviewer checklist filled in inline, two approvals are recorded, and the smoke test is green on the merged branch.

```bash
# from project root
git push -u origin feature/day2-trade-model
gh pr create --fill
cd backend && ./mvnw test -Dtest=EquityTradeTest,TradeRefTest,MoneyTest
```

**Verify the artifact contains:**

- [ ] PR title is short and action-led (e.g. "Day 2 — TradeType sealed hierarchy, factory, value objects, exception ladder").
- [ ] PR body contains the scope paragraph, the new-files list, the eleven-item reviewer checklist, and the smoke-test command.
- [ ] Two distinct approvals are recorded on the PR; any unticked checklist box has a request-changes comment with a specific `file:line`.
- [ ] After merge, the smoke test command above exits 0 on the team integration branch.
- [ ] A link back to this student guide (`student-guides/day2/README.md`) appears in the PR body.

---

<details>
<summary><b>Q&A bank</b></summary>


Plant these in your back pocket; pull as needed.

1. **"Why sealed, not abstract?"** Both close the inheritance hierarchy, but `abstract` is open to *any* subclass — internal or third-party. `sealed` requires the implementer to be named in `permits`. This gives you (a) exhaustive `switch` (compiler enforces every case is handled) and (b) "no surprise subclass" — Ops can't add a `CryptoTrade` we never tested.

2. **"Records vs classes for trades?"** Records for **value objects** (`Money`, `TradeRef`, request DTOs) — small, immutable, all-fields-equal. Classes (with builders) for the **`TradeType` hierarchy** — 8+ fields, needs a build-then-validate pattern, shares abstract state, may eventually need behaviour. Records can't host a `static class Builder` cleanly, and they auto-generate `equals` over *all* components, which is wrong for trades (where natural key is `tradeRef`).

3. **"Why Builder over telescoping constructor?"** Readability (call-site reads as named fields, not positional), safety (you can't accidentally swap `quantity` and `price` — both are `BigDecimal`), and a single point to enforce invariants. Telescoping constructors collapse when fields grow past ~4.

4. **"Why immutable value objects?"** Three reasons: (a) thread safety is free — share across threads without locks; (b) cacheable / interneable safely; (c) eliminates a class of bugs where a downstream service mutates a "shared" `Money` and breaks the caller. The cost — one extra object on update — is nothing compared to the debugging cost of mutation aliasing.

5. **"Checked vs unchecked custom exceptions — which?"** In a Spring service: **unchecked** (extends `RuntimeException`). Reasons: (a) `@RestControllerAdvice` handles them uniformly; (b) layered services don't have to `throws X` everywhere, polluting signatures; (c) checked exceptions encourage `catch (X e) { /* swallow */ }` which is worse than crashing. Reserve checked for truly recoverable I/O like `IOException`.

6. **"Why override equals/hashCode — JPA does it for us?"** JPA's default keys on the surrogate `@Id`. That's null pre-persist (so `Set<Trade>` contains "all the unsaved trades as duplicates of one") and changes meaning post-detach. Our domain equality is the **natural key** (`tradeRef`) — globally unique by construction, never null, stable across the lifecycle. JPA is allowed to be wrong about this; we're not.

7. **"JSR-380 vs writing manual validation?"** JSR-380 (`@NotNull`, `@Positive`, etc.) is declarative, runs automatically at the controller boundary, and produces structured error messages. Manual validation (`if (x == null) throw ...`) lives in the builder and protects the domain even if some pathway skips the controller. **Both, not either** — different layers, different attackers.

8. **"Can we skip Javadoc if names are clear?"** Names tell you *what*; Javadoc tells you *why and what-it-promises*. `build()` is obvious; `throws IllegalStateException if quantity is not positive` is not. Public contracts have to be documented because every reader of the contract is a future maintainer. Internal methods can rely on names.

9. **"Why is `ReconciliationRule` an enum, not a config table?"** The *set* of rules is closed and changes via deploy, not via DB UPDATE. Rules aren't user-editable; they're audit-controlled. If thresholds become tuneable later (Day 6 ConfigurationProperties), we lift those constants out — but the *list* of rules stays in code where it's reviewable and version-controlled. Tables are for data; enums are for logic.

10. **"Why is `Trade` package-private but `TradeType` public?"** `TradeType` is the **public contract** for downstream code: services, controllers, tests. `Trade` is the **internal shared-state base** — only `model` package code should subclass it. Splitting them lets us expose the contract without inviting "let me just extend `Trade`" from another package.

11. **"Why not Lombok `@Builder`?"** Three reasons today: (a) the grads need to *see* the boilerplate to understand what `@Builder` generates; (b) Lombok's generated builders don't do build-time invariant validation without extra work (`@Builder.Default`, `@Singular`, etc.); (c) we add Lombok consciously on Day 4 once they've earned the right. Premature framework adoption hides the model from its authors.

12. **"What's the difference between `final` and `sealed` for a class?"** `final` = no subclasses, period. `sealed` = subclasses allowed, but only the ones named in `permits` and each must declare itself `final`, `sealed`, or `non-sealed`. `final` is a hard close; `sealed` is a closed-world contract that still allows internal extension.

13. **"Why use `Currency` over `String`?"** `Currency.getInstance("EURR")` throws `IllegalArgumentException`. `"EURR"` as a string passes through to Day-5 fail-during-settlement. Cheap upfront enforcement of ISO 4217.

14. **"What's the cost of all this immutability — does it allocate too much?"** For domain objects modified <100k/s, the allocation cost is invisible against the JIT and modern GC. If profiling shows it's a real bottleneck (it won't on Day 2), you can switch specific hot-path objects. Optimise on evidence, never on speculation.

15. **"Why is the abstract `Trade` class also sealed?"** Same closed-world guarantee inside the package: nobody can `extends Trade` from another `model`-package file unless they're in the `permits` list. Belt-and-braces with the public sealed interface; if the interface were ever made non-sealed by mistake, the abstract class still enforces the closed set.

---

</details>

<details>
<summary><b>End-of-day debrief prompts</b></summary>


At 16:45:

1. "Sketch the `TradeType` hierarchy on paper from memory. Which classes are sealed, which are final, which are abstract? Why each?"
2. "Walk me through what happens when I call `EquityTrade.builder().price(BigDecimal.TEN).build()`. Where does the failure surface, what's the message, what's the root cause?"
3. "If I wanted to add `CryptoTrade` tomorrow morning, list every file I'd touch and every `switch` I'd have to extend. The compiler will help me find some of them — which ones?"

If anyone can't answer #1 confidently, schedule a 15-minute 1:1 before tomorrow. The model is the spine of Days 3–6; weak ground here cascades.

---

</details>

<details>
<summary><b>Things that have gone wrong before</b></summary>


- **Team used `abstract class Trade` instead of `sealed interface TradeType`.**

  Anyone could extend `Trade` from any package. Day 4, a junior added an `InternalTestTrade` to a test package that *escaped into production* via a misrouted import.

  **Fix:** Sealed is non-optional on this codebase. Enforce at PR review.

- **Builder forgot to validate in `build()`.**

  Trades passed the builder with `quantity = null`, the constructor accepted them (because no `Objects.requireNonNull`), and the trade only failed at Jakarta validation in the controller on Day 5. By then there was a 200-trade test fixture committed with nulls.

  **Fix:** validate in `build()` first. Layer 1 nulls, Layer 2 invariants.

- **`equals`/`hashCode` keyed on `id` (database PK).**

  Before persist, `id` was null. `Set<Trade>` held all unsaved trades as "the same trade". The dedupe job on Day 6 silently dropped 90% of intake.

  **Fix:** key on `tradeRef` (natural key), not `id`.

- **Exception messages leaked PII into application logs.**

  `InvalidTradeException("Counterparty ACME LLC [LEI 5493…] failed validation for trade EQU-…")` went to Splunk; security audit caught it Day 9 and we burnt a day redacting.

  **Fix:** sanitise messages; PII lives in structured logs with access controls, not free-text exception strings.

- **`Comparable` inconsistent with `equals`.**

  Team ordered on `tradeDate` only; `equals` was on `tradeRef`. `TreeSet<TradeType>` silently dropped trades with the same date but different refs. The bug surfaced as "we have 1000 trades in the DB, 87 in the in-memory set".

  **Fix:** order on `tradeDate, then tradeRef` so the comparator returns 0 only when `equals` is true.

- **`Money` as a mutable class with setters.**

  A service mutated a shared `Money` instance returned from a cache; the cached entry got corrupted; downstream calculations went wrong for an hour before anyone noticed.

  **Fix:** records. Always. Immutability is not a style choice for value objects; it's correctness.

- **`Currency` as `String`.**

  Team accepted `"EURR"` from a CSV import. Failed in the Day-5 FX conversion service with a 200-line stack trace ending in `UnknownCurrencyException` deep in a third-party lib.

  **Fix:** `Currency.getInstance(...)` at the boundary; if it throws, you have a clear error pointing at the source.

- **Telescoping constructor "for speed".**

  Team shipped `new EquityTrade(ref, sym, qty, price, side, notional, tradeDate)` "to save time". On Day 5, a colleague swapped `qty` and `price` arguments (both `BigDecimal`) in one of 30 call-sites. Trade priced at 1000.00 GBP, quantity 12.5 units, instead of price 12.50, qty 1000. Caught by recon mismatch — but the customer-facing report was already wrong.

  **Fix:** builders are not optional past 4 fields.

- **`ReconciliationRule` made into a database table on Day 2.**

  "We thought it'd be more flexible". Spent the rest of Day 2 building admin CRUD endpoints for it. By Day 4, no-one had ever changed a rule via the UI — and the auditable in-code version was gone.

  **Fix:** enum first; lift to config only on evidence. ---</details> <details> <summary><b>Hand-off to Day 3</b></summary>


By end-of-day each team should have:

- [ ] `sealed interface TradeType` + abstract `Trade` base class, in `com.dbtraining.reconx.model`.
- [ ] Four concrete trade classes (`EquityTrade`, `FXTrade`, `BondTrade`, `DerivativeTrade`) — each `final`, each with a static `Builder`, each with build-then-validate.
- [ ] `TradeFactory.create(String, Map)` returns a typed `TradeType` or throws `InvalidTradeException`.
- [ ] Value objects: `Money` (record), `TradeRef` (record, regex-validated in compact constructor).
- [ ] Full exception ladder: `ReconException` → 4 concrete subtypes.
- [ ] `ReconciliationRule` enum with thresholds and a `matches(...)` method.
- [ ] `Comparable<TradeType>` consistent with `equals` (both keyed on `tradeRef`).
- [ ] `TradeRequest` DTO with JSR-380 annotations.
- [ ] PII-safe `toString()` overrides.
- [ ] Javadoc on every public class and method.
- [ ] PR merged into `develop` with **two** approvals (advanced-track convention).

**Smoke test before close-of-day** — every team should be able to run:

```bash
cd backend && ./mvnw test -Dtest=EquityTradeTest,TradeRefTest,MoneyTest
```

and get a green bar. If they can't, fix it tonight; Day 3 builds parallel recon on top of this and won't compile against a broken model.

**Next:** [TrainersGuide/day3/](../day3/README.md) — Functional Java, JUnit 5, Testcontainers, and parallel reconciliation with `CompletableFuture`.

</details>
