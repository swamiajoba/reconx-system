# TrainersGuide — Day 4: Spring Boot Enterprise Setup

> **Student-facing equivalent:** [student-guides/day4/README.md](../../student-guides/day4/README.md)
> **Exercises:** Day 4 · TICKET-ADV048 – TICKET-ADV062 (15 hands-on exercises across three workshop blocks)
> **Theme:** Spring Boot Modules 1 & 2 — Enterprise Setup (multi-module Spring Boot 3 project, JPA + Envers, MapStruct, Swagger, structured logging, Problem-Detail errors).

---

## Day at a glance

| #    | Block | Exercises | What students produce |
|------|-------|-----------|----------------------|
| 1 | Standup + Day-3 holdover unblock | — | Everyone on green; Testcontainers still passing |
| 2 | **AM Module 1 lecture — Spring Boot Enterprise Setup** | — | Whiteboard: IoC + bean lifecycle + multi-module map |
| 3 | **Workshop 4A — Layered Architecture, JPA, MapStruct, Swagger (Skeleton + profiles + entities)** | TICKET-ADV048 – TICKET-ADV052 | Reactor builds; `dev`/`uat`/`prod` profiles; `Trade`/`Instrument`/`Counterparty` JPA + Envers |
| 4 | **PM Module 2 lecture — Configuration** | — | Autoconfig internals, `@Conditional`, profile precedence |
| 5 | **Workshop 4B — Layered Architecture, JPA, MapStruct, Swagger (DTOs, mappers, repos, pagination)** | TICKET-ADV053 – TICKET-ADV057 | TradeMapper compiles to `target/generated-sources`; paged `GET /api/trades` returns 200 |
| 6 | **Workshop 4C — Layered Architecture, JPA, MapStruct, Swagger (Swagger, health, logging, errors)** | TICKET-ADV058 – TICKET-ADV062 | Swagger UI live; `/actuator/health` shows custom indicators; JSON logs; RFC 7807 errors |
| 7 | End-of-day debrief | — | Tomorrow's preview (REST + JWT) |

**Day-4 is the day the project finally "looks like a Spring Boot service."** Two
things students will under-estimate: (1) how long the multi-module reactor takes
to *first* build (5–10 min cold), and (2) how easy it is to break the Liquibase
+ JPA boot order. Pre-empt both in the morning.

---

## Pre-day instructor prep

The evening before Day 4:

- [ ] Run `./mvnw -pl backend clean install` from a **fresh clone** of the trainer copy. First build downloads ~120 MB. If your wifi is fast you won't notice; on conference wifi a team will be stuck for 15 minutes. Mirror the Maven cache to a shared folder if you can.
- [ ] Open the trainer copy's `backend/pom.xml` + `backend/api/pom.xml` side-by-side. You'll be asked "why does the child only have `<parent>` and no `<groupId>`?" — know the answer cold (it's inherited).
- [ ] **Pre-empt the three classic Spring Boot starter bugs:**
    - **Liquibase circular dependency.** If JPA's `EntityManagerFactory` bean depends on `liquibase`, but `liquibase` depends on `dataSource`, and your custom `@Configuration` re-declares the `DataSource`, you get a cycle. Reference fix: let Spring Boot's autoconfig provide both; only override what you actually need.
    - **`spring.liquibase.change-log` missing the `classpath:` prefix.** The property accepts a Spring Resource string. Without `classpath:`, Liquibase resolves it against the file system, can't find it, and **silently skips migrations** (no error in DEBUG until you turn on `logging.level.liquibase=DEBUG`). Always write `classpath:db/changelog/db.changelog-master.xml`.
    - **`data.sql` running on top of Liquibase.** Spring Boot's `spring.sql.init.mode=always` will run `data.sql` *after* Liquibase, which then re-inserts the seed data Liquibase already inserted → unique constraint violations. Either delete `data.sql` or set `spring.sql.init.mode=never` when Liquibase is on.
- [ ] Have `./mvnw -pl backend/api spring-boot:run -Dspring-boot.run.profiles=dev` in your shell history. You'll demo it 5+ times.
- [ ] Open Swagger UI once (`http://localhost:8080/swagger-ui.html`) so the browser tab is pre-warmed.
- [ ] Verify the **multi-module reactor** order: `common` → `domain` → `repository` → `service` → `api`. Reactor builds in declared order; if a student rearranges, they will hit "cannot find symbol" first.

---

## AM Module 1 — Spring Boot Enterprise Setup (lecture, 30 min)

Five whiteboard points, in this order:

1. **Spring Boot philosophy.** Convention over configuration, opinionated starters, autoconfiguration. Contrast with classic Spring XML — students who saw `applicationContext.xml` in a textbook will ask why we don't have one. ("We do. It's `META-INF/spring.factories` and `@AutoConfiguration` classes inside the starter JARs.")
2. **Multi-module Maven layout.** Draw the reactor: parent `pom.xml` of packaging `pom`, with `<modules>` declaring 5 children. Each child inherits `<parent>` and adds its own dependencies. Show the dependency arrows: `api → service → repository → domain`, with `common` underneath everything.
3. **IoC + DI.** Two slides: (a) without DI — `new` everywhere, untestable; (b) with DI — Spring wires constructor parameters. **Push constructor injection** over field injection (`@Autowired` on a field). Constructor injection makes mandatory dependencies explicit and supports `final` fields.
4. **Bean lifecycle + scopes.** Singleton by default, `prototype` for stateful helpers, `request` / `session` only in web context. Lifecycle hooks: `@PostConstruct` / `@PreDestroy`. Don't go deeper than this on Day 4.
5. **Spring Data JPA.** Repository interface → Spring generates an implementation at runtime via JDK proxy. `findByXxxAndYyy` is parsed from the method name. Custom queries get `@Query`. **Pageable** is built in.

---

## Workshop 4A — Layered Architecture, JPA, MapStruct, Swagger: Skeleton + profiles + entities (TICKET-ADV048 – TICKET-ADV052)

### TICKET-ADV048 — Create multi-module Maven project

**Common student blockers:**
- They run `mvn` from a child module and get *"Could not find artifact com.dbtraining.reconx:reconx-parent"* — they haven't installed the parent into the local repo. Run from the parent or `./mvnw install -N` first.
- They duplicate `<groupId>` and `<version>` in every child — works, but defeats the point of inheritance.
- They forget `<packaging>pom</packaging>` on the parent → Maven tries to compile the parent and fails.

**Unblocking ladder:**
1. **Nudge:** "From which directory did you run mvn?"
2. **Hint:** "Reactor builds need the parent to know about its children. Where does the `<modules>` list live?"
3. **Reveal:** Run `./mvnw -pl :reconx-api -am compile` to build only the api module *and* its dependencies upstream.

<details>
<summary>▶ Reference parent `pom.xml`</summary>

```xml
<!-- File: backend/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>

    <groupId>com.dbtraining.reconx</groupId>
    <artifactId>reconx-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <properties>
        <java.version>25</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <mapstruct.version>1.6.2</mapstruct.version>
        <lombok.version>1.18.34</lombok.version>
    </properties>

    <modules>
        <module>common</module>
        <module>domain</module>
        <module>repository</module>
        <module>service</module>
        <module>api</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct</artifactId>
                <version>${mapstruct.version}</version>
            </dependency>
            <dependency>
                <groupId>net.logstash.logback</groupId>
                <artifactId>logstash-logback-encoder</artifactId>
                <version>8.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

</details>

<details>
<summary>▶ Reference child `pom.xml` (api module)</summary>

```xml
<!-- File: backend/api/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.dbtraining.reconx</groupId>
        <artifactId>reconx-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>reconx-api</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.dbtraining.reconx</groupId>
            <artifactId>reconx-service</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.6.0</version>
        </dependency>
    </dependencies>
</project>
```

</details>

**Talking point:** "Multi-module for a single service — really worth it?" Answer: at this size it's marginal, **but** the day we ship a `recon-batch` JAR or a `recon-cli` JAR, the split pays for itself. More importantly, it forces clean layering — the `domain` module can't depend on Spring annotations, which keeps your entities portable. See the Q&A for more.

**▶ Run the project — verify TICKET-ADV048 end-to-end**

Confirm the reactor (or single-module pom) compiles cleanly before wiring anything else on top of it.

```bash
./mvnw clean install
```

**Observe:**

- Build succeeds with `BUILD SUCCESS` and exit code 0.
- If you stayed single-module: one reactor build for `reconx-api` (or whatever you named the artifactId).
- If you split into a multi-module reactor: modules build in dependency order — common, domain, repository, service, api.
- Failure signal: a `[ERROR] Failed to execute goal` line, or `Non-resolvable parent POM` if `<relativePath/>` is wrong on a child.

---

### TICKET-ADV049 — Spring profiles: dev / uat / prod

**Common student blockers:**
- They put the active profile in `application.yml` (`spring.profiles.active: dev`) and then can't override it from `SPRING_PROFILES_ACTIVE=prod` env var. **It does work** — env wins — but only because of property source precedence. Worth explaining.
- They commit `application-prod.yml` with a real-looking password. Fail the PR.
- They forget `application-uat.yml` is "UAT" not "user acceptance" and configure it like prod. UAT is dev-with-postgres for us.

<details>
<summary>▶ Reference `application.yml` + per-profile files</summary>

```yaml
# File: backend/api/src/main/resources/application.yml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  application:
    name: reconx-api
  jpa:
    open-in-view: false                   # default true — turn off in services
    properties:
      hibernate:
        jdbc.time_zone: UTC
        format_sql: true
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml  # MUST have classpath: prefix
  sql:
    init:
      mode: never                         # Liquibase owns the schema, not data.sql

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true

server:
  port: 8080
  servlet:
    context-path: /api
```

```yaml
# File: backend/api/src/main/resources/application-dev.yml
spring:
  datasource:
    url: jdbc:h2:mem:reconx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: validate                  # Liquibase does the create — JPA only validates
  h2:
    console:
      enabled: true
      path: /h2-console
logging:
  level:
    com.dbtraining.reconx: DEBUG
    org.hibernate.SQL: DEBUG
```

```yaml
# File: backend/api/src/main/resources/application-uat.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/reconx_uat
    username: reconx_uat
    password: reconx_uat
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate
logging:
  level:
    com.dbtraining.reconx: INFO
```

```yaml
# File: backend/api/src/main/resources/application-prod.yml
spring:
  datasource:
    url: ${RECONX_DB_URL}                 # no defaults — fail fast if unset
    username: ${RECONX_DB_USER}
    password: ${RECONX_DB_PASSWORD}
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate
logging:
  level:
    root: WARN
    com.dbtraining.reconx: INFO
```

</details>

**Talking point:** "Profiles vs env-vars-only — why both?" Profiles select a *bundle* of related settings (DB driver, dialect, log level, feature flags). Env vars override *individual* values. Use profiles for shape; use env vars for secrets and per-env values inside a shape.

**▶ Run the project — verify TICKET-ADV049 end-to-end**

Boot the app under each profile in turn and confirm the right datasource shape is wired up.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# then in another terminal, stop and retry:
SPRING_PROFILES_ACTIVE=uat ./mvnw spring-boot:run
```

**Observe:**

- Boot log line `The following 1 profile is active: "dev"` (or `"uat"`).
- Under `dev` the datasource URL in the startup banner is `jdbc:h2:mem:reconx;MODE=PostgreSQL;...`; under `uat` it switches to `jdbc:postgresql://...`.
- Failure signal: under `prod` with no `RECONX_DB_URL` env var set, startup fails fast with `Could not resolve placeholder 'RECONX_DB_URL'` — that is the fail-fast behaviour you wanted.

---

### TICKET-ADV050 — `@Entity Trade` with `@ManyToOne` + auditing

**Common student blockers:**
- They use `@Data` from Lombok on a JPA entity — equals/hashCode includes every field including lazy collections, and now a single `equals` triggers N+1. Use `@Getter @Setter @ToString(exclude=…)` and manual `equals`/`hashCode` on the ID.
- They forget `@EntityListeners(AuditingEntityListener.class)`. Then `@CreatedDate` is always null. They blame Spring.
- They miss `@EnableJpaAuditing` on a `@Configuration` somewhere. Same symptom.
- Default fetch on `@ManyToOne` is **EAGER**. They leave it EAGER and every trade query joins counterparty + instrument silently. Force `LAZY`.

<details>
<summary>▶ Reference `Trade` entity</summary>

```java
// File: backend/domain/src/main/java/com/dbtraining/reconx/domain/Trade.java
package com.dbtraining.reconx.domain;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "trades", indexes = {
    @Index(name = "idx_trades_trade_date", columnList = "trade_date"),
    @Index(name = "idx_trades_status",     columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Audited
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_ref", nullable = false, unique = true, length = 30)
    private String tradeRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counterparty_id", nullable = false)
    private Counterparty counterparty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradeStatus status = TradeStatus.PENDING;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "modified_at")
    private Instant modifiedAt;

    // --- getters / setters omitted for brevity ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trade other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override public int hashCode() { return Objects.hash(id); }
}
```

```java
// File: backend/api/src/main/java/com/dbtraining/reconx/config/JpaConfig.java
package com.dbtraining.reconx.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
```

</details>

**Talking point:** Why `EnumType.STRING`? Default is `ORDINAL` (an int). If anyone reorders the enum constants, every row in the DB silently re-points to the wrong status. STRING costs ~10 bytes per row and saves your career.

**▶ Run the project — verify TICKET-ADV050 end-to-end**

Boot under Postgres so you can inspect the actual `trades` schema generated by Liquibase + JPA validate.

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=uat ./mvnw spring-boot:run
# in another terminal:
psql -h localhost -p 5432 -U reconx_uat -d reconx_uat -c "\d trades"
```

**Observe:**

- `\d trades` lists columns matching your `@Column` annotations: `id`, `trade_ref`, `counterparty_id`, `instrument_id`, `quantity`, `price`, `trade_date`, `status`, `created_at`, `modified_at`.
- Indexes `idx_trades_trade_date` and `idx_trades_status` are listed.
- Failure signal: a `Schema-validation: missing column [created_at]` startup error means `@EnableJpaAuditing` is missing from `JpaConfig` or the Liquibase changelog has not been updated to add the audit columns.

---

### TICKET-ADV051 — `Instrument` entity with JSONB metadata

**Common student blockers:**
- They try to map a `Map<String,Object>` to JSONB without Hibernate types-json on classpath — error: "no Dialect mapping for JDBC type: 1111".
- They reach for `@ElementCollection` because the name sounds right — that gives you a separate join table, not JSONB. Both are valid; the JSONB route is the modern one.
- On H2 they discover JSONB doesn't exist. Solve by enabling H2's PostgreSQL mode (`MODE=PostgreSQL`) **and** using `jsonb` as the columnDefinition — H2 stores it as a CLOB but the dialect maps it sensibly.

<details>
<summary>▶ Reference `Instrument` entity (JSONB metadata)</summary>

```java
// File: backend/domain/src/main/java/com/dbtraining/reconx/domain/Instrument.java
package com.dbtraining.reconx.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "instruments")
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String symbol;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false, length = 20)
    private AssetClass assetClass;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * JSONB metadata: tick size, lot size, exchange code, etc.
     * On H2 (dev profile) this stores as a CLOB; on Postgres it's true JSONB
     * and is queryable via the @> operator.
     */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    // --- getters / setters omitted for brevity ---
}
```

</details>

**Talking point:** JSONB is *not* a synonym for "schemaless". The agreement with the consuming team should be: the keys we promise to ship, the keys we may ship, and the keys that are private. Write that down in the README of the domain module.

**▶ Run the project — verify TICKET-ADV051 end-to-end**

Boot under Postgres so the `jsonb` column type is honoured, then inspect the schema.

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=uat ./mvnw spring-boot:run
# in another terminal:
psql -h localhost -p 5432 -U reconx_uat -d reconx_uat -c "\d instruments"
```

**Observe:**

- The `metadata` column is listed with type `jsonb` (not `text` or `clob`).
- Columns `symbol`, `name`, `asset_class`, `currency` match the entity definition.
- Failure signal: `column "metadata" is of type bytea` means the `@Type(JsonBinaryType.class)` annotation is missing — Hibernate fell back to its default serialiser.

---

### TICKET-ADV052 — Hibernate Envers on the `Trade` entity

**Common student blockers:**
- `@Audited` is on the entity but the audit table is empty. **Almost always** because they forgot `@EntityListeners(AuditingEntityListener.class)` *or* `@EnableJpaAuditing` (those two are for Spring Data auditing, not Envers — but Envers uses the same Hibernate event listeners, which Spring wires for you when `hibernate-envers` is on the classpath).
- They add `hibernate-envers` to `domain` but the listener registration runs in `api`. Confirm by checking the Hibernate startup log for "Envers integration enabled".
- They expect Envers to also audit `@ManyToOne` cascades. By default it does, but for `@OneToMany` they must also `@Audited` the child.

<details>
<summary>▶ Querying the audit table via `AuditReader`</summary>

```java
// File: backend/service/src/main/java/com/dbtraining/reconx/service/TradeHistoryService.java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.domain.Trade;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TradeHistoryService {

    private final EntityManager em;

    public TradeHistoryService(EntityManager em) { this.em = em; }

    @Transactional(readOnly = true)
    public List<Number> revisionsFor(Long tradeId) {
        AuditReader reader = AuditReaderFactory.get(em);
        return reader.getRevisions(Trade.class, tradeId);
    }

    @Transactional(readOnly = true)
    public Trade snapshotAt(Long tradeId, Number revision) {
        AuditReader reader = AuditReaderFactory.get(em);
        return reader.find(Trade.class, tradeId, revision);
    }
}
```

Envers creates two tables behind the scenes:
- `trades_aud` — one row per revision of a trade, with every audited column plus `REV` + `REVTYPE` (0=insert, 1=update, 2=delete).
- `revinfo` — one row per revision, with timestamp.

</details>

**Talking point:** "What does the rev table actually capture?" One row per *committed change*, with the entity's field state at that revision. It is **not** a change-log of who clicked which button — it's a snapshot history. Tie audit-of-action separately at the controller layer (Day 5).

**▶ Run the project — verify TICKET-ADV052 end-to-end**

Boot the app and confirm Envers auto-created its audit tables, then modify a trade and watch a revision row appear.

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=uat ./mvnw spring-boot:run
# in another terminal:
psql -h localhost -p 5432 -U reconx_uat -d reconx_uat -c "\dt *_aud"
psql -h localhost -p 5432 -U reconx_uat -d reconx_uat -c "\dt revinfo"
```

**Observe:**

- `trades_aud` and `revinfo` tables are listed.
- After updating a trade three times (via your tests or curl), `SELECT id, rev, revtype FROM trades_aud WHERE id = <yourTradeId>` returns four rows: one with `revtype=0` (insert) and three with `revtype=1` (update).
- Failure signal: empty `trades_aud` means `@Audited` is missing from `Trade`, or `hibernate-envers` is not on the classpath.

---

## Workshop 4B — Layered Architecture, JPA, MapStruct, Swagger: DTOs, mappers, repos, pagination (TICKET-ADV053 – TICKET-ADV057)

### TICKET-ADV053 — DTO layer + `PagedResponse<T>`

**Common student blockers:**
- They return `Trade` (the entity) directly from the controller. Works in tests; in MVC the open-in-view filter is off (`spring.jpa.open-in-view=false` in our config), so serialising `counterparty` lazily throws `LazyInitializationException`. **Push DTOs hard** today.
- They wrap a Spring `Page<Trade>` and serialise it directly — JSON shape is a Spring-Data-specific blob with `pageable` nested object. UI teams hate it. Wrap in `PagedResponse<T>` for a stable contract.

<details>
<summary>▶ Reference DTOs</summary>

```java
// File: backend/api/src/main/java/com/dbtraining/reconx/dto/TradeRequest.java
package com.dbtraining.reconx.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeRequest(
    @NotBlank String tradeRef,
    @NotNull Long counterpartyId,
    @NotNull Long instrumentId,
    @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
    @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
    @NotNull @PastOrPresent LocalDate tradeDate
) {}
```

```java
// File: backend/api/src/main/java/com/dbtraining/reconx/dto/TradeResponse.java
package com.dbtraining.reconx.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TradeResponse(
    Long id,
    String tradeRef,
    Long counterpartyId,
    String counterpartyName,
    Long instrumentId,
    String instrumentSymbol,
    BigDecimal quantity,
    BigDecimal price,
    LocalDate tradeDate,
    String status,
    Instant createdAt,
    Instant modifiedAt
) {}
```

```java
// File: backend/api/src/main/java/com/dbtraining/reconx/dto/PagedResponse.java
package com.dbtraining.reconx.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PagedResponse<T>(
    List<T> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static <E, T> PagedResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(
            page.getContent().stream().map(mapper).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
```

</details>

**Talking point:** Records over classes for DTOs in 2026. They give you immutability, compact `equals`/`hashCode`, and clean JSON serialisation via Jackson out of the box.

**▶ Run the project — verify TICKET-ADV053 end-to-end**

Records are compile-time constructs — a `./mvnw compile` is enough to prove they are well-formed.

```bash
./mvnw clean compile
```

**Observe:**

- Compilation succeeds. The generated class files for each record contain auto-generated `equals`, `hashCode`, `toString`, and accessor methods (one per component).
- `PagedResponse.of(...)` resolves against `java.util.function.Function` and `org.springframework.data.domain.Page` — no entity types appear in any record signature.
- Failure signal: a `cannot find symbol: class Trade` compile error inside a DTO means an entity leaked into a record — DTOs must reference only primitives, JDK types, and other DTOs.

---

### TICKET-ADV054 — MapStruct mapper

**Common student blockers:**
- Their mapper interface compiles but Spring can't find the bean — they wrote `@Mapper` without `componentModel = "spring"`. MapStruct then generates a plain class with no `@Component`.
- IDE shows red on the mapper interface because the implementation hasn't been generated yet. Solution: run `./mvnw compile`. IntelliJ users may need to enable annotation processing.
- They write `@Mapping(target = "id", ignore = true)` on the entity-to-response direction. That's the wrong direction — id needs to be *populated* in the response. Ignore on the request-to-entity direction instead.

<details>
<summary>▶ Reference `TradeMapper`</summary>

```java
// File: backend/api/src/main/java/com/dbtraining/reconx/dto/TradeMapper.java
package com.dbtraining.reconx.dto;

import com.dbtraining.reconx.domain.Trade;
import org.mapstruct.*;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TradeMapper {

    @Mapping(source = "counterparty.id",       target = "counterpartyId")
    @Mapping(source = "counterparty.name",     target = "counterpartyName")
    @Mapping(source = "instrument.id",         target = "instrumentId")
    @Mapping(source = "instrument.symbol",     target = "instrumentSymbol")
    @Mapping(source = "status",                target = "status",
             qualifiedByName = "statusToString")
    TradeResponse toResponse(Trade trade);

    @Mapping(target = "id",            ignore = true)
    @Mapping(target = "counterparty",  ignore = true)   // wired by service from id
    @Mapping(target = "instrument",    ignore = true)
    @Mapping(target = "status",        ignore = true)   // defaulted to PENDING
    @Mapping(target = "createdAt",     ignore = true)
    @Mapping(target = "modifiedAt",    ignore = true)
    Trade toEntity(TradeRequest req);

    @Named("statusToString")
    static String statusToString(Enum<?> status) {
        return status == null ? null : status.name();
    }
}
```

Add MapStruct's annotation processor in the `api` module:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <annotationProcessorPaths>
          <path>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct-processor</artifactId>
            <version>${mapstruct.version}</version>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
  </plugins>
</build>
```

</details>

**Talking point:** "MapStruct vs manual mapping vs ModelMapper?" MapStruct generates *boring, debuggable code at compile time*. Manual mapping is fine for 1–2 DTOs; at 20+ DTOs your reviewers stop reading the mappers. ModelMapper uses reflection — opaque, slow, and silently maps fields you didn't mean to. MapStruct is the production default in 2026.

**▶ Run the project — verify TICKET-ADV054 end-to-end**

The MapStruct annotation processor runs at compile time — confirm it produced a generated implementation class.

```bash
./mvnw clean compile
find target/generated-sources/annotations -name "TradeMapperImpl.java"
```

**Observe:**

- `target/generated-sources/annotations/com/dbtraining/reconx/dto/TradeMapperImpl.java` exists.
- Opening that file shows the mapping logic: `target.setCounterpartyId(trade.getCounterparty().getId())` and similar lines, plus a `@Component` annotation so Spring picks it up.
- Failure signal: a compile error `Unmapped target property: "xxx"` means a field in `TradeResponse` is not covered by any `@Mapping` — the `unmappedTargetPolicy = ERROR` is doing its job; add the missing mapping.

---

### TICKET-ADV055 — `TradeRepository` with `@Query`

<details>
<summary>▶ Reference `TradeRepository`</summary>

```java
// File: backend/repository/src/main/java/com/dbtraining/reconx/repository/TradeRepository.java
package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.domain.Trade;
import com.dbtraining.reconx.domain.TradeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface TradeRepository
        extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade> {

    Optional<Trade> findByTradeRef(String tradeRef);

    @Query("""
        SELECT t FROM Trade t
        WHERE t.tradeDate BETWEEN :from AND :to
          AND (:status IS NULL OR t.status = :status)
          AND (:counterpartyId IS NULL OR t.counterparty.id = :counterpartyId)
        """)
    Page<Trade> findByFilters(
        @Param("from")           LocalDate from,
        @Param("to")             LocalDate to,
        @Param("status")         TradeStatus status,
        @Param("counterpartyId") Long counterpartyId,
        Pageable pageable
    );
}
```

</details>

**Talking point:** Text-block JPQL (Java 25 triple-quoted strings) makes long queries readable. The `(:param IS NULL OR ...)` idiom is the canonical "optional filter" trick — it works but degrades to a full scan on large tables. For 5+ optional filters move to Specifications (next exercise).

**▶ Run the project — verify TICKET-ADV055 end-to-end**

Boot the app and hit the list endpoint with a filter so you can see the JPQL render and the SQL it produces in the logs.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# in another terminal:
curl "http://localhost:8080/api/v1/trades?counterpartyId=1&status=PENDING"
```

**Observe:**

- The SQL log line (DEBUG level under `dev`) shows a `WHERE` clause referencing `trade_date BETWEEN ? AND ? AND (? IS NULL OR status = ?) AND (? IS NULL OR counterparty_id = ?)`.
- The response payload contains only trades matching `counterpartyId=1` and `status=PENDING`.
- Failure signal: `Parameter with that name [from] did not exist` means a `@Param` annotation is missing on a method parameter, or the name does not match the placeholder in the JPQL.

---

### TICKET-ADV056 — Specification-based dynamic queries

<details>
<summary>▶ Reference `TradeSpecification`</summary>

```java
// File: backend/repository/src/main/java/com/dbtraining/reconx/repository/TradeSpecification.java
package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.domain.Trade;
import com.dbtraining.reconx.domain.TradeStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public final class TradeSpecification {

    private TradeSpecification() {}

    public static Specification<Trade> tradeDateBetween(LocalDate from, LocalDate to) {
        return (root, q, cb) -> {
            if (from == null && to == null) return cb.conjunction();
            if (from == null)               return cb.lessThanOrEqualTo(root.get("tradeDate"), to);
            if (to == null)                 return cb.greaterThanOrEqualTo(root.get("tradeDate"), from);
            return cb.between(root.get("tradeDate"), from, to);
        };
    }

    public static Specification<Trade> hasStatus(TradeStatus status) {
        return (root, q, cb) -> status == null
            ? cb.conjunction()
            : cb.equal(root.get("status"), status);
    }

    public static Specification<Trade> forCounterparty(Long counterpartyId) {
        return (root, q, cb) -> counterpartyId == null
            ? cb.conjunction()
            : cb.equal(root.get("counterparty").get("id"), counterpartyId);
    }

    public static Specification<Trade> refLike(String pattern) {
        return (root, q, cb) -> pattern == null || pattern.isBlank()
            ? cb.conjunction()
            : cb.like(root.get("tradeRef"), pattern + "%");
    }
}
```

Usage in service:

```java
Specification<Trade> spec = Specification
    .where(TradeSpecification.tradeDateBetween(from, to))
    .and(TradeSpecification.hasStatus(status))
    .and(TradeSpecification.forCounterparty(cpId))
    .and(TradeSpecification.refLike(refPattern));

Page<Trade> page = tradeRepository.findAll(spec, pageable);
```

</details>

**Talking point:** Specs are verbose but composable. Pull a `forUser(currentUser)` spec for multi-tenancy and chain it everywhere — single place to fix bugs in your authorisation logic.

**▶ Run the project — verify TICKET-ADV056 end-to-end**

Boot the app and hit the search endpoint with different filter combinations — the generated SQL should change shape based on which params are supplied.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# in another terminal:
curl "http://localhost:8080/api/v1/trades?from=2026-01-01&to=2026-12-31"
curl "http://localhost:8080/api/v1/trades?from=2026-01-01&to=2026-12-31&status=SETTLED&counterpartyId=1"
```

**Observe:**

- First call's SQL log shows only `trade_date BETWEEN ? AND ?` in the WHERE — no spurious `status` or `counterparty_id` clauses.
- Second call's SQL log shows all three predicates joined with `AND`.
- Failure signal: every query produces the same SQL regardless of supplied filters — your specs are not short-circuiting to `cb.conjunction()` for null arguments.

---

### TICKET-ADV057 — Pagination on list endpoints

<details>
<summary>▶ Reference `TradeController` paged endpoint</summary>

```java
// File: backend/api/src/main/java/com/dbtraining/reconx/controller/TradeController.java
package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.domain.TradeStatus;
import com.dbtraining.reconx.dto.PagedResponse;
import com.dbtraining.reconx.dto.TradeMapper;
import com.dbtraining.reconx.dto.TradeResponse;
import com.dbtraining.reconx.service.TradeQueryService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/trades")
public class TradeController {

    private final TradeQueryService queryService;
    private final TradeMapper mapper;

    public TradeController(TradeQueryService queryService, TradeMapper mapper) {
        this.queryService = queryService;
        this.mapper       = mapper;
    }

    @GetMapping
    public PagedResponse<TradeResponse> list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) TradeStatus status,
        @RequestParam(required = false) Long counterpartyId,
        @PageableDefault(size = 20, sort = "tradeDate", direction = Sort.Direction.DESC)
        Pageable pageable
    ) {
        var page = queryService.search(from, to, status, counterpartyId, pageable);
        return PagedResponse.of(page, mapper::toResponse);
    }
}
```

</details>

**Talking point:** Pin `size` defaults. Without `@PageableDefault` a caller can request `?size=10000` and your DB will hate you. Some teams cap with a Spring config: `spring.data.web.pageable.max-page-size=100`.

**▶ Run the project — verify TICKET-ADV057 end-to-end**

Boot the app and hit the paged list endpoint — the JSON envelope shape is the contract Day 5+ tickets rely on.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# in another terminal:
curl -s "http://localhost:8080/api/v1/trades?page=0&size=5" | jq
```

**Observe:**

- Response is HTTP 200 with a JSON object containing exactly `items`, `page`, `size`, `totalElements`, `totalPages` — no Spring `pageable` nested object.
- `items` is an array of `TradeResponse` records (flat counterparty/instrument fields, status as a String).
- Failure signal: response contains a `pageable: { ... }` block — the controller is returning `Page<Trade>` directly instead of `PagedResponse.of(page, mapper::toResponse)`.

---

## Workshop 4C — Layered Architecture, JPA, MapStruct, Swagger: Swagger, health, logging, errors (TICKET-ADV058 – TICKET-ADV062)

### TICKET-ADV058 — Swagger / OpenAPI

<details>
<summary>▶ Reference `OpenApiConfig`</summary>

```java
// File: backend/api/src/main/java/com/dbtraining/reconx/config/OpenApiConfig.java
package com.dbtraining.reconx.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI reconxOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("ReconX API")
                .description("Trade reconciliation platform — DB TDI 2026")
                .version("v1.0.0")
                .contact(new Contact()
                    .name("ReconX Team")
                    .email("reconx-team@dbtraining.com")))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
            .group("public")
            .pathsToMatch("/v1/trades/**", "/v1/recon/**")
            .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
            .group("admin")
            .pathsToMatch("/v1/admin/**", "/actuator/**")
            .build();
    }
}
```

</details>

**Talking point:** Group your APIs by audience. `public` is for the UI; `admin` is for ops. Auditors love this because they can show two distinct OpenAPI specs in their compliance review.

**▶ Run the project — verify TICKET-ADV058 end-to-end**

Boot the app and open Swagger UI in a browser — flip the group dropdown between `public` and `admin`.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# then in a browser:
open http://localhost:8080/api/swagger-ui.html
# or via curl:
curl -s "http://localhost:8080/api/v1/api-docs/public" | jq '.info.title'
```

**Observe:**

- Swagger UI loads, the dropdown shows two groups (`public` and `admin`), and the `Authorize` button shows a `bearerAuth` (HTTP/bearer/JWT) scheme.
- `public` lists `/v1/trades/**` paths; `admin` lists `/v1/admin/**` and `/actuator/**` paths.
- Failure signal: 404 on `/api/swagger-ui.html` means the `springdoc-openapi-starter-webmvc-ui` dependency is missing from the api pom.

---

### TICKET-ADV059 — `DatabaseHealthIndicator`

<details>
<summary>▶ Reference</summary>

```java
// File: backend/api/src/main/java/com/dbtraining/reconx/observability/DatabaseHealthIndicator.java
package com.dbtraining.reconx.observability;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;

@Component("reconxDatabase")
public class DatabaseHealthIndicator extends AbstractHealthIndicator {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        super("ReconX database health check failed");
        this.dataSource = dataSource;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        long start = System.nanoTime();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout((int) TIMEOUT.toSeconds());
            try (ResultSet rs = stmt.executeQuery("SELECT 1")) {
                rs.next();
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            builder.up()
                   .withDetail("query", "SELECT 1")
                   .withDetail("elapsedMs", elapsedMs);
        } catch (SQLException e) {
            builder.down(e).withDetail("query", "SELECT 1");
        }
    }
}
```

</details>

**Talking point:** The default `DataSourceHealthIndicator` does almost the same thing, but you don't own the message format or the timeout. Custom indicators are how you make `/actuator/health` say what *your* ops team needs to see.

**▶ Run the project — verify TICKET-ADV059 end-to-end**

Boot the app and hit the actuator health endpoint — your custom indicator should appear under the `components` map.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# in another terminal:
curl -s "http://localhost:8080/api/actuator/health" | jq '.components.reconxDatabase'
```

**Observe:**

- `reconxDatabase` is listed with `"status": "UP"` and a `details` map containing `"query": "SELECT 1"` plus an `elapsedMs` number.
- The overall `status` is `UP`.
- Failure signal: `reconxDatabase` is missing — `@Component("reconxDatabase")` is missing or the class is not in a package scanned by the Spring Boot application class.

---

### TICKET-ADV060 — `KafkaHealthIndicator`

<details>
<summary>▶ Reference</summary>

```java
// File: backend/api/src/main/java/com/dbtraining/reconx/observability/KafkaHealthIndicator.java
package com.dbtraining.reconx.observability;

import org.apache.kafka.clients.admin.*;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component("reconxKafka")
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaHealthIndicator extends AbstractHealthIndicator {

    private final String bootstrapServers;

    public KafkaHealthIndicator(
        @org.springframework.beans.factory.annotation.Value("${spring.kafka.bootstrap-servers}")
        String bootstrapServers
    ) {
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        Map<String, Object> cfg = Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,    bootstrapServers,
            AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG,   2_000,
            AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3_000
        );
        try (AdminClient admin = AdminClient.create(cfg)) {
            DescribeClusterResult cluster = admin.describeCluster();
            String clusterId = cluster.clusterId().get(2, TimeUnit.SECONDS);
            int nodeCount    = cluster.nodes().get(2, TimeUnit.SECONDS).size();
            builder.up()
                   .withDetail("clusterId", clusterId)
                   .withDetail("nodeCount", nodeCount);
        } catch (Exception e) {
            builder.down(e);
        }
    }
}
```

</details>

**Talking point:** The `@ConditionalOnProperty` guard means the indicator simply does not register on the dev profile (where Kafka is off). Avoids permanent yellow status in dev.

**▶ Run the project — verify TICKET-ADV060 end-to-end**

The Kafka indicator should appear only when `spring.kafka.bootstrap-servers` is set — boot once without Kafka, then once with.

```bash
# 1) dev profile, no Kafka:
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
curl -s "http://localhost:8080/api/actuator/health" | jq '.components | keys'

# 2) bring up Kafka and boot under uat:
docker compose up -d kafka
SPRING_PROFILES_ACTIVE=uat ./mvnw spring-boot:run
curl -s "http://localhost:8080/api/actuator/health" | jq '.components.reconxKafka'
```

**Observe:**

- Under `dev`, the components keys list does NOT include `reconxKafka` — `@ConditionalOnProperty` removed the bean.
- Under `uat` with Kafka up, `reconxKafka.status` is `UP` with `clusterId` and `nodeCount` details.
- Failure signal: stopping Kafka and re-hitting the endpoint should show `reconxKafka.status: DOWN` with the exception detail. If it stays UP, the AdminClient timeouts are too generous.

---

### TICKET-ADV061 — Structured logging

<details>
<summary>▶ Reference `logback-spring.xml`</summary>

```xml
<!-- File: backend/api/src/main/resources/logback-spring.xml -->
<configuration>

    <springProfile name="dev">
        <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %X{correlationId:-} %X{tradeRef:-} %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
    </springProfile>

    <springProfile name="uat,prod">
        <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdc>true</includeMdc>
                <customFields>{"service":"reconx-api"}</customFields>
            </encoder>
        </appender>
    </springProfile>

    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>

</configuration>
```

```java
// File: backend/api/src/main/java/com/dbtraining/reconx/observability/MdcFilter.java
package com.dbtraining.reconx.observability;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)
public class MdcFilter implements Filter {

    static final String HDR_CORRELATION = "X-Correlation-Id";
    static final String HDR_TRADE_REF   = "X-Trade-Ref";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        String correlationId = header(http, HDR_CORRELATION, UUID.randomUUID().toString());
        String tradeRef      = header(http, HDR_TRADE_REF, null);
        try {
            MDC.put("correlationId", correlationId);
            if (tradeRef != null) MDC.put("tradeRef", tradeRef);
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }

    private static String header(HttpServletRequest r, String name, String fallback) {
        String v = r.getHeader(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
```

</details>

**Talking point:** Two reasons to switch logs to JSON in non-dev: (1) the log collector parses fields cheaply, (2) you can index `correlationId` and pivot across services in 1 minute instead of 30. Dev keeps the human-readable pattern so terminals stay readable.

**▶ Run the project — verify TICKET-ADV061 end-to-end**

Boot the app, send a request with an explicit correlation id, and confirm it propagates through every log line for that request.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# in another terminal:
curl -H "X-Correlation-Id: foo-123" "http://localhost:8080/api/v1/trades?page=0&size=1"
```

**Observe:**

- The dev console log lines for that request contain `foo-123` in the correlationId slot of the pattern.
- Every log line produced during the request — controller, service, repository, SQL log — carries the same `foo-123` id.
- Failure signal: log lines show an empty correlationId slot (just the `-` from `%X{correlationId:-}`) — the `MdcFilter` is not picking up the request, usually because `@Component @Order(1)` is missing or the package is outside component-scan.
- Switching to `SPRING_PROFILES_ACTIVE=uat`, the log lines become JSON objects with `mdc.correlationId` as a field.

---

### TICKET-ADV062 — Global `@RestControllerAdvice` with RFC 7807

<details>
<summary>▶ Reference `GlobalExceptionHandler`</summary>

```java
// File: backend/api/src/main/java/com/dbtraining/reconx/exception/GlobalExceptionHandler.java
package com.dbtraining.reconx.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TradeNotFoundException.class)
    public ProblemDetail handleNotFound(TradeNotFoundException ex, WebRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://reconx.dbtraining.com/errors/trade-not-found"));
        pd.setTitle("Trade not found");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(DuplicateTradeRefException.class)
    public ProblemDetail handleDuplicate(DuplicateTradeRefException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://reconx.dbtraining.com/errors/duplicate-trade-ref"));
        pd.setTitle("Duplicate trade reference");
        return pd;
    }

    @ExceptionHandler(ReconException.class)
    public ProblemDetail handleReconConflict(ReconException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create("https://reconx.dbtraining.com/errors/recon-failure"));
        pd.setTitle("Reconciliation failure");
        pd.setProperty("reconBreakId", ex.getReconBreakId());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .collect(Collectors.joining("; "));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, msg);
        pd.setTitle("Validation failed");
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraint(ConstraintViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleAny(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred — please contact support with the correlationId");
        pd.setTitle("Internal server error");
        return pd;
    }
}
```

</details>

**Talking point:** RFC 7807 (`application/problem+json`) is the modern interop standard for HTTP errors. UI teams parse a single shape; ops teams can route on `type` URIs. Stop inventing per-service error envelopes.

**▶ Run the project — verify TICKET-ADV062 end-to-end**

Boot the app and POST an invalid trade body so the validation handler kicks in — confirm the response is a proper RFC 7807 ProblemDetail.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# in another terminal — empty body, fails @NotBlank/@NotNull:
curl -i -X POST "http://localhost:8080/api/v1/trades" \
  -H "Content-Type: application/json" -d '{}'
```

**Observe:**

- Response status is HTTP 400 with header `Content-Type: application/problem+json`.
- Body is a JSON object containing `type`, `title` (`Validation failed`), `status: 400`, `detail` (semicolon-joined field errors like `tradeRef: must not be blank; counterpartyId: must not be null`), and `instance`.
- Hitting a non-existent trade id (e.g. `GET /api/v1/trades/999999`) returns HTTP 404 with `type` ending in `/trade-not-found` and a `timestamp` property.
- Failure signal: response body is Spring's default `{"timestamp": ..., "status": 400, "error": ...}` and `Content-Type: application/json` — the `@RestControllerAdvice` is not wired (wrong package, missing annotation, or component scan exclusion).

---

<details>
<summary><b>Q&A bank</b></summary>


Use these to drive the post-lunch lecture or the end-of-day debrief.

1. **Multi-module Maven — really worth it for one service?** Today, marginally. The minute you add a CLI or a batch worker that shares `domain` + `repository`, it pays back. It also stops `api`-only concerns leaking into `domain` (Spring web annotations on entities, etc.).
2. **JPA `@Entity` vs DTO — why both?** Entities are persistence shapes (lazy collections, JPA constraints, audit columns). DTOs are wire shapes (validation, JSON-friendly, API-versionable). Coupling them is convenient on Monday and a nightmare by Friday.
3. **Spring profiles vs env-vars-only?** Profiles select a *bundle* of related settings; env vars override individual values inside the bundle. Use profiles for shape (driver + dialect + log level) and env vars for secrets.
4. **Envers — what does the `_aud` table actually capture?** One row per committed change, with the entity state at that revision. It's a *historical snapshot store*, not an audit log of user actions. Use both together — Envers for "what did the data look like at 14:03", a separate `action_log` for "who clicked what at 14:03".
5. **MapStruct vs manual mapping vs ModelMapper?** MapStruct generates explicit, debuggable code at compile time. Manual mapping is fine for 1–2 DTOs. ModelMapper uses reflection — opaque, slow, silently maps fields you didn't intend. MapStruct wins for production.
6. **Specification API vs QueryDSL?** Specifications are zero extra deps and ship with Spring Data JPA. QueryDSL is more readable for complex joins but adds a code-gen step and another dependency tree. We choose Specifications for ReconX; QueryDSL is fine for systems with 50+ search permutations.
7. **Why RFC 7807 Problem Detail?** Standard error shape across services, well-known media type, type URIs that can be referenced in docs, structured `properties` extension point. Stop inventing your own envelope.
8. **MDC — does it survive across `@Async`?** No, not without help. `MDC` is `ThreadLocal`. Use `TaskDecorator` on the thread-pool executor to copy MDC into the worker thread, or use Micrometer's `ContextSnapshot`. Either way, demonstrate it failing first.
9. **Why a custom `HealthIndicator` — isn't Actuator's default enough?** The default `DataSourceHealthIndicator` runs a validation query but you don't control the timeout, the SQL, or the message. For Kafka there is *no* default. Custom indicators are how you make `/actuator/health` say what your ops runbook expects.
10. **`@Cacheable` on a repo method — supported?** Yes, but Spring Data wraps repos in a JDK proxy, and `@Cacheable` is a different proxy. Both stack OK in practice (Spring composes them), but the cache key needs care because repo method args may include `Pageable` which has equality semantics that surprise people. Prefer `@Cacheable` at the service layer.
11. **Where does `application-prod.yml` get its secrets from?** Environment variables — never committed defaults. The `${RECONX_DB_PASSWORD}` syntax errors at boot if unset, which is exactly what we want in prod.
12. **`open-in-view: false` — what breaks?** Lazy collections accessed *after* the service method returns will throw. Either eager-fetch, project into a DTO inside the service (recommended), or use a `@EntityGraph`.
13. **`ddl-auto: validate` — what is JPA doing on startup?** Reading every entity's mapped columns and asserting they exist with compatible types in the DB. If Liquibase missed a column, you find out at boot, not at first query.
14. **Why `@Enumerated(EnumType.STRING)` not `ORDINAL`?** ORDINAL stores the integer position of the constant. Reorder the enum, every row silently points to a different status. STRING costs ~10 bytes per row.
15. **`spring.jpa.properties.hibernate.format_sql=true` — for prod?** Dev only. In prod it adds 30%+ to log volume for marginal value; rely on structured logging + correlation IDs to debug instead.

---

</details>

<details>
<summary><b>End-of-day debrief prompts</b></summary>


At 16:45:

1. "Draw the reactor build order and explain why `domain` has zero Spring dependencies."
2. "A trade is updated three times. Show me what's in `trades`, `trades_aud`, and `revinfo` after each commit."
3. "I add `?size=10000` to your list endpoint. What stops the database from melting?"
4. "Walk me through one request from `MdcFilter` to the `GlobalExceptionHandler` — name every log line that should carry the `correlationId`."

If any team can't answer #1 confidently, schedule a 10-min refresh tomorrow before Day 5 starts — they'll be lost when REST + JWT land in the same `api` module.

---

</details>

<details>
<summary><b>Things that have gone wrong before</b></summary>


- **Liquibase tried to run before JPA dialect was set — circular dep at startup.**

  Symptom: `BeanCurrentlyInCreationException` involving `liquibase`, `entityManagerFactory`, `dataSource`. Cause: a custom `@Configuration` re-declares the `DataSource` bean *and* injects `EntityManager` in the same class.

  **Fix:** let Spring Boot's autoconfig provide both; do not re-declare unless you're adding a custom property.

- **`spring.liquibase.change-log` missing the `classpath:` prefix — silent skip.**

  Symptom: app boots green, but the `databasechangelog` table is empty and no tables exist (or only the seed `data.sql` runs). Cause: Liquibase resolves the path as a file-system path, can't find it, and skips.

  **Fix:** always write `classpath:db/changelog/db.changelog-master.xml`. Turn on `logging.level.liquibase=DEBUG` to see the skip warning.

- **`data.sql` ran on top of Liquibase — duplicate-key blowups.**

  Symptom: boot crashes on unique constraint violation, but only on the *second* run. Cause: Spring Boot's `spring.sql.init.mode=always` runs `data.sql` after Liquibase, re-inserting seed data that Liquibase already inserted.

  **Fix:** delete `data.sql` (let Liquibase own all schema + reference data) **or** set `spring.sql.init.mode=never`.

- **DTOs returned the JPA entity directly — `LazyInitializationException` on serialise.**

  Symptom: GET endpoint returns 500, stack trace includes `LazyInitializationException: could not initialize proxy - no Session`. Cause: returning a `Trade` with a lazy `counterparty`, with `spring.jpa.open-in-view=false`.

  **Fix:** map to `TradeResponse` *inside* the `@Transactional` service method.

- **Envers `@Audited` without `@EntityListeners(AuditingEntityListener.class)` — empty rev tables.**

  Symptom: `trades_aud` exists but stays empty.

  **Fix:** add `@EntityListeners(AuditingEntityListener.class)` on the entity and `@EnableJpaAuditing` on a `@Configuration`. Restart and re-update a row.

- **MapStruct mapper not picked up by Spring — `NoSuchBeanDefinitionException: TradeMapper`.**

  Symptom: at startup. Cause: missing `componentModel = "spring"` on `@Mapper`, *or* the annotation processor never ran (build was cached, IntelliJ annotation processing disabled).

  **Fix:** ensure `@Mapper(componentModel = "spring")`; `./mvnw clean compile`; in IntelliJ enable Settings → Build → Compiler → Annotation Processors.

- **Structured logging emitted plaintext to console — log collector couldn't parse JSON.**

  Symptom: prod logs look fine on `stdout`, but the ELK stack shows them as a single `message` field with the whole JSON-ish blob inside. Cause: `logback-spring.xml` had the JSON encoder only on a file appender, but the container only ships stdout.

  **Fix:** put `LogstashEncoder` on the `STDOUT` appender for non-dev profiles (as the reference does).

- **Multi-module reactor build broke on first try.**

  Symptom: `cannot find symbol` referring to a class in a sibling module. Cause: a student ran `mvn` from a child directory without `-am`.

  **Fix:** always run from the parent; or `./mvnw -pl :reconx-api -am compile`. ---</details> <details> <summary><b>Hand-off to Day 5</b></summary>


By end-of-day each team should have:

- [ ] Multi-module project builds cleanly: `./mvnw -pl backend clean install` exits 0.
- [ ] `dev` profile starts; `http://localhost:8080/api/swagger-ui.html` lists `/v1/trades`.
- [ ] `GET /api/v1/trades?page=0&size=5` returns a `PagedResponse<TradeResponse>` against seed data.
- [ ] `POST /api/v1/trades` with a bad body returns `application/problem+json` and HTTP 400.
- [ ] `/actuator/health` shows `reconxDatabase: UP` (and `reconxKafka` UP or absent under `dev`).
- [ ] Logs in `dev` show `correlationId` in the pattern; in `uat` they should be JSON when the profile flips.

**Next:** [TrainersGuide/day5/](../day5/README.md) — REST API design, JWT, RBAC, and Testcontainers integration tests built on top of today's controller skeleton.

</details>
