# TrainersGuide — Day 5: Spring Boot REST + JWT + RBAC + Integration Tests

> **Student-facing equivalent:** [student-guides/day5/README.md](../../student-guides/day5/README.md)
> **Exercises:** Day 5 · TICKET-ADV063 – TICKET-ADV080 (18 hands-on exercises across three workshop blocks)
> **Theme:** Spring Boot Modules 3 & 4 — REST + JWT Security. Ship the public HTTP surface, lock it down with JWT + RBAC, and prove it with MockMvc and Testcontainers.

---

## Day at a glance

| #    | Block | Exercises | What students produce |
|------|-------|-----------|----------------------|
| 1 | Standup + Day-4 holdover unblock | — | Multi-module build green, Envers wired |
| 2 | AM Module 3 mini-lecture — REST + Spring MVC | — | Whiteboard: request lifecycle, `@Valid`, pagination |
| 3 | **Workshop 5A — REST CRUD endpoints** | TICKET-ADV063 – TICKET-ADV071 | TradeController, ReconController, AuditController |
| 4 | PM Module 4 mini-lecture — Spring Security + JWT | — | Whiteboard: filter chain, stateless auth |
| 5 | **Workshop 5B — JWT + RBAC** | TICKET-ADV072 – TICKET-ADV074 | `/api/auth/login`, JwtAuthenticationFilter, SecurityFilterChain |
| 6 | **Workshop 5C — MockMvc + Testcontainers + versioning** | TICKET-ADV075 – TICKET-ADV080 | Slice tests + full lifecycle integration test |
| 7 | Buffer / unblock | — | Catch-up time |
| 8 | End-of-day debrief | — | Day-6 preview (caching + observability) |

**Hard rule for the day:** no controller ships without (a) `@Valid` on the
request body, (b) at least one MockMvc test, and (c) an entry in the
`SecurityFilterChain`. If a team's PR is missing any of those three, bounce
it.

---

## Pre-day instructor prep

The evening before Day 5:

- [ ] **Pre-test a JWT round-trip on [jwt.io](https://jwt.io/)** using the
      same HS256 secret you'll demo with (set in `application-dev.yml` as
      `app.jwt.secret`). Paste the encoded token, confirm the claims
      `sub`, `roles`, `iat`, `exp` decode cleanly. If you can't decode it
      live in front of grads, don't try.
- [ ] **Import the Postman collection** at
      `backend/postman/reconx-day5.postman_collection.json` (smoke covers
      login → POST trade → GET trades → PATCH status → DELETE). Run
      against your local backend; every request should go green.
- [ ] **Re-seed the 4 default users** from the top-level
      [README — Default credentials](../../README.md#default-credentials-dev-profile-only).
      Confirm `admin@db.com / admin123`, `trader@db.com / trader123`,
      `viewer@db.com / viewer123`, `recon@db.com / recon123` all log in
      and return the expected role claim.
- [ ] **Boot the dev profile cleanly** — `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.
      Watch the startup log for `Liquibase: Successfully released change log lock`
      and `Tomcat started on port 8080`. If either is missing, fix before
      class — students will copy whatever they see.
- [ ] Pre-open this trainer README + the student Day-5 README side-by-side.
- [ ] Have `curl` + `jq` history ready for live JWT demos:
      ```bash
      TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
        -H 'Content-Type: application/json' \
        -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .token)
      curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/trades | jq
      ```

---

## Workshop 5A — REST CRUD endpoints (TICKET-ADV063 – TICKET-ADV071) (~2 hr 15 min)

### Mini-lecture (15 min) — before students start

Draw the Spring MVC request lifecycle on the whiteboard:

```
Client → DispatcherServlet → HandlerMapping → HandlerAdapter
       → (Filters) → Controller → @Valid → Service → Repository
       → Response → MessageConverter (Jackson) → Client
```

Hit these talking points:
- `@RestController` = `@Controller` + `@ResponseBody` on every method.
- `@Valid` triggers JSR-380 (Bean Validation 3.0 / Jakarta Validation). The
  `MethodArgumentNotValidException` is caught by `@RestControllerAdvice`
  (built Day 4) and converted to RFC 7807 `ProblemDetail`.
- Pagination: `Pageable` is auto-bound from `?page=0&size=20&sort=createdAt,desc`.
- DTOs vs entities: never leak `@Entity` to the wire. Records make this
  one-line.

---

### TICKET-ADV063 — GET /api/v1/trades (paginated, filterable, sortable)

**Common student blockers:**
- They return `List<Trade>` instead of `Page<TradeResponse>`. Page metadata
  (total elements, current page) gets lost.
- They build the filter `WHERE` clause by string-concatenation instead of
  using a `Specification`. Works for one filter, breaks at three.
- `?sort=created_at,desc` returns a 500 — the JPA property is `createdAt`
  (camelCase), not the column name.

**Unblocking ladder:**
1. **Nudge:** "What does Postman show — `content` array but no `totalPages`? What's that telling you?"
2. **Hint:** "Spring already has a wrapper for paginated responses. What does `JpaRepository.findAll(Pageable)` return?"
3. **Reveal:** Wrap the `Page` in a small `PagedResponse` record so the JSON shape is stable.

<details>
<summary>▶ Full reference solution — TradeController.list(...)</summary>

```java
// ============================================================================
// File: backend/src/main/java/com/dbtraining/reconx/controller/TradeController.java
// TICKET-ADV063 — paginated, filterable, sortable GET /api/v1/trades
// ============================================================================
package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.PagedResponse;
import com.dbtraining.reconx.dto.TradeRequest;
import com.dbtraining.reconx.dto.TradeResponse;
import com.dbtraining.reconx.dto.StatusUpdate;
import com.dbtraining.reconx.service.TradeService;
import com.dbtraining.reconx.repository.TradeSpecifications;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/trades")
public class TradeController {

    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    // ---------- TICKET-ADV063 — list ----------
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PagedResponse<TradeResponse>> list(
            @RequestParam(required = false) Optional<String> status,
            @RequestParam(required = false) Optional<String> counterparty,
            @RequestParam(required = false) Optional<LocalDate> fromDate,
            @RequestParam(required = false) Optional<LocalDate> toDate,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<TradeResponse> page = tradeService.search(
                TradeSpecifications.filter(status, counterparty, fromDate, toDate),
                pageable);

        return ResponseEntity.ok(PagedResponse.from(page));
    }
}
```

```java
// File: backend/src/main/java/com/dbtraining/reconx/dto/PagedResponse.java
public record PagedResponse<T>(
        java.util.List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {
    public static <T> PagedResponse<T> from(org.springframework.data.domain.Page<T> p) {
        return new PagedResponse<>(
                p.getContent(),
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages(),
                p.isLast());
    }
}
```

</details>

**Talking point:** "Why is the filter optional instead of four overloaded
endpoints?" Because the alternative is `/trades`, `/trades/by-status`,
`/trades/by-counterparty`, `/trades/by-status-and-counterparty`… Specifications
collapse that combinatorial explosion to one endpoint and one DAO method.

**▶ Run the project — verify TICKET-ADV063 end-to-end**

Boot the app and confirm the paginated list endpoint returns the expected envelope shape.

```bash
docker compose up -d postgres
./mvnw -pl backend spring-boot:run &
sleep 25
curl -s "http://localhost:8080/api/v1/trades?page=0&size=20" | jq
```

**Observe:**

- JSON body contains `items` (or `content`), `page`, `size`, `totalElements`, `totalPages` fields.
- Default page size of 20 is respected when omitted; `page=0` returns the first page.
- A 500 about an unknown sort property means the sort parameter does not match a JPA field name.

---

### TICKET-ADV064 — POST /api/v1/trades (create + validation)

**Common student blockers:**
- They return the saved entity directly, leaking `@Entity` annotations and
  potentially lazy-loaded proxies that Jackson chokes on.
- They forget `@Valid` — invalid payloads sail through and crash deeper in
  the service.
- They return `ResponseEntity.ok(...)` for create. Should be `201 Created`
  with a `Location` header.

**Unblocking ladder:**
1. **Nudge:** "What status code does REST convention require for a successful create?"
2. **Hint:** "`ResponseEntity` has a static `created(URI)` for exactly this."
3. **Reveal:** Show `ResponseEntity.created(URI.create("/api/v1/trades/" + saved.id())).body(saved)`.

<details>
<summary>▶ Reference solution — POST /api/v1/trades</summary>

```java
// ---------- TICKET-ADV064 — create ----------
@PostMapping
@PreAuthorize("hasAnyRole('TRADER','ADMIN')")
public ResponseEntity<TradeResponse> create(@RequestBody @Valid TradeRequest req) {
    TradeResponse saved = tradeService.create(req);
    return ResponseEntity
            .created(URI.create("/api/v1/trades/" + saved.id()))
            .body(saved);
}
```

```java
// File: backend/src/main/java/com/dbtraining/reconx/dto/TradeRequest.java
package com.dbtraining.reconx.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeRequest(
        @NotBlank @Size(max = 30) String tradeRef,
        @NotNull Long instrumentId,
        @NotNull Long counterpartyId,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal price,
        @NotNull @PastOrPresent LocalDate tradeDate,
        @Pattern(regexp = "PENDING|MATCHED|UNMATCHED|DISPUTED|CANCELLED") String status) {}
```

</details>

**Talking point:** The `@RestControllerAdvice` built on Day 4 catches the
`MethodArgumentNotValidException` and returns a `ProblemDetail` with the
field-by-field errors. Walk through a `curl` with a deliberately bad
payload to show the 400 shape.

**▶ Run the project — verify TICKET-ADV064 end-to-end**

POST a valid trade for 201, then a malformed body for 400.

```bash
# Valid body → 201 + Location header
curl -i -X POST http://localhost:8080/api/v1/trades \
  -H "Content-Type: application/json" \
  -d '{"tradeRef":"TRD-20260315-0001","instrumentId":1,"counterpartyId":1,"assetClass":"EQUITY","side":"BUY","quantity":100.0,"price":245.50,"tradeDate":"2026-03-15"}'

# Invalid body (missing tradeRef, negative quantity) → 400 ProblemDetail
curl -i -X POST http://localhost:8080/api/v1/trades \
  -H "Content-Type: application/json" \
  -d '{"quantity":-5,"price":245.50}'
```

**Observe:**

- Valid POST returns `HTTP/1.1 201 Created` with `Location: /api/v1/trades/{id}` header.
- Invalid POST returns `HTTP/1.1 400 Bad Request` with a `ProblemDetail` body listing field errors.
- A 500 instead of 400 means `@Valid` is not on the `@RequestBody` parameter.

---

### TICKET-ADV065 — PUT /api/v1/trades/{id} (full update)

**Common student blockers:**
- They confuse PUT and PATCH semantics. PUT replaces the *whole* resource;
  the request body must include every field.
- They forget the 404 path — updating a non-existent trade returns 500 via
  `EntityNotFoundException` instead of a clean 404.

<details>
<summary>▶ Reference solution — PUT /api/v1/trades/{id}</summary>

```java
// ---------- TICKET-ADV065 — full update ----------
@PutMapping("/{id}")
@PreAuthorize("hasAnyRole('TRADER','ADMIN')")
public ResponseEntity<TradeResponse> update(
        @PathVariable Long id,
        @RequestBody @Valid TradeRequest req) {
    return ResponseEntity.ok(tradeService.update(id, req));
}
```

In `TradeService` — throw `TradeNotFoundException` (a custom
`RuntimeException`) which `@RestControllerAdvice` maps to 404.

</details>

**Talking point:** Idempotency. `PUT /trades/42 {body}` called twice with
the same body must produce the same final state. POST does not have to be
idempotent.

**▶ Run the project — verify TICKET-ADV065 end-to-end**

PUT the same body twice and confirm idempotency, then PUT to a missing id for 404.

```bash
# Replace 1 with a real trade id from the previous list call
curl -i -X PUT http://localhost:8080/api/v1/trades/1 \
  -H "Content-Type: application/json" \
  -d '{"tradeRef":"TRD-20260315-0001","instrumentId":1,"counterpartyId":1,"assetClass":"EQUITY","side":"BUY","quantity":150.0,"price":250.00,"tradeDate":"2026-03-15"}'

# Repeat the same PUT — should be idempotent
curl -i -X PUT http://localhost:8080/api/v1/trades/1 \
  -H "Content-Type: application/json" \
  -d '{"tradeRef":"TRD-20260315-0001","instrumentId":1,"counterpartyId":1,"assetClass":"EQUITY","side":"BUY","quantity":150.0,"price":250.00,"tradeDate":"2026-03-15"}'

# Missing id → 404 ProblemDetail
curl -i -X PUT http://localhost:8080/api/v1/trades/9999999 \
  -H "Content-Type: application/json" \
  -d '{"tradeRef":"TRD-20260315-0001","instrumentId":1,"counterpartyId":1,"assetClass":"EQUITY","side":"BUY","quantity":150.0,"price":250.00,"tradeDate":"2026-03-15"}'
```

**Observe:**

- Both successful PUTs return 200 with identical response bodies.
- Missing id returns `HTTP/1.1 404 Not Found` with a `ProblemDetail` body, not a 500.
- A 500 stack trace means `TradeNotFoundException` is not wired into the `@RestControllerAdvice`.

---

### TICKET-ADV066 — PATCH /api/v1/trades/{id}/status

**Common student blockers:**
- They make this a `PUT` on `/trades/{id}` and just update one field. That
  breaks PUT semantics (above).
- They allow VIEWER to call it because they forget `@PreAuthorize`.

<details>
<summary>▶ Reference solution — PATCH /status</summary>

```java
// ---------- TICKET-ADV066 — partial update (status only) ----------
@PatchMapping("/{id}/status")
@PreAuthorize("hasAnyRole('TRADER','ADMIN')")
public ResponseEntity<TradeResponse> updateStatus(
        @PathVariable Long id,
        @RequestBody @Valid StatusUpdate req) {
    return ResponseEntity.ok(tradeService.updateStatus(id, req.status()));
}
```

```java
// File: backend/src/main/java/com/dbtraining/reconx/dto/StatusUpdate.java
package com.dbtraining.reconx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StatusUpdate(
        @NotBlank
        @Pattern(regexp = "PENDING|MATCHED|UNMATCHED|DISPUTED|CANCELLED")
        String status) {}
```

</details>

**Talking point:** "Why is `/status` a sub-resource and not a query parameter?"
Because a query parameter signals filtering, not mutation. The URL should
read like a noun-phrase even on a verb-method (PATCH).

**▶ Run the project — verify TICKET-ADV066 end-to-end**

PATCH the status sub-resource, then read the trade back to confirm only `status` changed.

```bash
curl -i -X PATCH http://localhost:8080/api/v1/trades/1/status \
  -H "Content-Type: application/json" \
  -d '{"status":"MATCHED"}'

curl -s http://localhost:8080/api/v1/trades/1 | jq '.status'

# Invalid status → 400
curl -i -X PATCH http://localhost:8080/api/v1/trades/1/status \
  -H "Content-Type: application/json" \
  -d '{"status":"FOOBAR"}'
```

**Observe:**

- PATCH returns 200 and the response body's `status` field is `MATCHED`.
- The follow-up GET also shows `MATCHED`; all other fields (quantity, price, side) are unchanged.
- Invalid status returns 400 with a validation message — anything else means the `@Pattern` constraint is missing.

---

### TICKET-ADV067 — DELETE /api/v1/trades/{id} (soft delete)

**Common student blockers:**
- They `DELETE FROM trades` — hard delete. Audit history is now broken;
  Envers can't track a row that no longer exists.
- The list endpoint (TICKET-ADV063) still returns soft-deleted rows because the
  query doesn't filter `deleted_at IS NULL`.

**Unblocking ladder:**
1. **Nudge:** "Run your delete, then call GET — is the trade still there?"
2. **Hint:** "Soft-delete needs filtering on the read side too. Where would that filter live?"
3. **Reveal:** Add `@SQLRestriction("deleted_at IS NULL")` on the entity (Hibernate 6.3+) or filter in every Specification.

<details>
<summary>▶ Reference solution — DELETE (soft)</summary>

```java
// ---------- TICKET-ADV067 — soft delete ----------
@DeleteMapping("/{id}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Void> delete(@PathVariable Long id) {
    tradeService.softDelete(id);
    return ResponseEntity.noContent().build();
}
```

```java
// TradeService.softDelete
@Transactional
public void softDelete(Long id) {
    Trade t = tradeRepository.findById(id)
            .orElseThrow(() -> new TradeNotFoundException(id));
    t.setDeletedAt(java.time.OffsetDateTime.now());
    // No explicit save() needed inside @Transactional — dirty checking handles it.
}
```

```java
// Trade entity — class-level annotation so every query hides deleted rows
@Entity
@Table(name = "trades")
@SQLRestriction("deleted_at IS NULL")   // org.hibernate.annotations.SQLRestriction
public class Trade { ... }
```

</details>

**Talking point:** Why 204 No Content? Because the response body would be
empty anyway — the resource just got removed (logically). 204 communicates
"done, nothing to send back".

**▶ Run the project — verify TICKET-ADV067 end-to-end**

DELETE a trade, confirm the GET list excludes it, then prove the row physically remains.

```bash
curl -i -X DELETE http://localhost:8080/api/v1/trades/1

# Should NOT appear in the default list anymore
curl -s "http://localhost:8080/api/v1/trades" | jq '.items[] | select(.id == 1)'

# Direct SQL check — row still present with deleted_at populated
docker compose exec postgres psql -U reconx -d reconx \
  -c "SELECT id, trade_ref, deleted_at FROM trades WHERE id = 1;"
```

**Observe:**

- DELETE returns `HTTP/1.1 204 No Content` with an empty body.
- The follow-up jq filter returns nothing — the row is hidden by `@SQLRestriction`.
- The SQL query shows the row with a non-NULL `deleted_at` timestamp; if the row is gone, you issued a hard `DELETE FROM` instead of a soft delete.

---

### TICKET-ADV068 — POST /api/v1/recon/run

**Common student blockers:**
- They block synchronously and return the full result. A real recon job
  could take minutes — the HTTP request times out.
- They return 200. Should be 202 Accepted because the work is queued.

<details>
<summary>▶ Reference solution — ReconController.run</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/controller/ReconController.java
@RestController
@RequestMapping("/api/v1/recon")
public class ReconController {

    private final ReconService reconService;

    public ReconController(ReconService reconService) {
        this.reconService = reconService;
    }

    // ---------- TICKET-ADV068 — trigger ----------
    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('RECON_ANALYST','ADMIN')")
    public ResponseEntity<ReconJobAccepted> run(@RequestBody @Valid ReconRequest req) {
        String jobId = reconService.triggerAsync(req);
        return ResponseEntity
                .accepted()
                .header("Location", "/api/v1/recon/jobs/" + jobId + "/results")
                .body(new ReconJobAccepted(jobId, "QUEUED"));
    }
}

public record ReconRequest(
        @NotNull java.time.LocalDate from,
        @NotNull java.time.LocalDate to,
        java.util.Optional<Long> counterpartyId) {}

public record ReconJobAccepted(String jobId, String status) {}
```

</details>

**Talking point:** 202 Accepted + `Location` header is the standard
"long-running job" pattern. The client polls the URL in `Location` until
the job finishes. We'll wire SSE for push-based updates on Day 7.

**▶ Run the project — verify TICKET-ADV068 end-to-end**

POST a recon-run request and confirm the 202 Accepted response with a jobId.

```bash
curl -i -X POST http://localhost:8080/api/v1/recon/run \
  -H "Content-Type: application/json" \
  -d '{"from":"2026-03-01","to":"2026-03-31"}'
```

**Observe:**

- Response status is `HTTP/1.1 202 Accepted`, not 200.
- Response body contains a `jobId` (a valid UUID) and `status: QUEUED`.
- Application log shows a line like `recon job dispatched: jobId=...` — if missing, the service did not actually enqueue the work.

---

### TICKET-ADV069 — GET /api/v1/recon/jobs/{jobId}/results

<details>
<summary>▶ Reference solution</summary>

```java
@GetMapping("/jobs/{jobId}/results")
@PreAuthorize("hasAnyRole('RECON_ANALYST','VIEWER','ADMIN')")
public ResponseEntity<PagedResponse<ReconResultResponse>> results(
        @PathVariable String jobId,
        @PageableDefault(size = 50) Pageable pageable) {
    return ResponseEntity.ok(
            PagedResponse.from(reconService.findResultsByJobId(jobId, pageable)));
}
```

</details>

**Talking point:** Note that VIEWER can read recon results — read-only
roles need broad read access. Keep the matrix on the whiteboard.

**▶ Run the project — verify TICKET-ADV069 end-to-end**

GET the breaks for a specific recon job and confirm pagination metadata.

```bash
# Use a jobId from the ADV068 response, or any string while the trainer-stub returns all open breaks
curl -s "http://localhost:8080/api/v1/recon/jobs/00000000-0000-0000-0000-000000000001/results?page=0&size=50" | jq
```

**Observe:**

- Response is 200 with a JSON list (or `PagedResponse` envelope) of break rows.
- Page size of 50 is respected; each entry has `id`, `status`, and break metadata fields.
- A 403 here means the role you are calling with is not in the recon path's allow-list — switch tokens once Workshop 5B lands.

---

### TICKET-ADV070 — PUT /api/v1/recon/results/{id}/resolve

<details>
<summary>▶ Reference solution</summary>

```java
@PutMapping("/results/{id}/resolve")
@PreAuthorize("hasAnyRole('RECON_ANALYST','ADMIN')")
public ResponseEntity<ReconResultResponse> resolve(
        @PathVariable Long id,
        @RequestBody @Valid ResolutionRequest req) {
    return ResponseEntity.ok(reconService.resolve(id, req.note()));
}

public record ResolutionRequest(@NotBlank @Size(max = 500) String note) {}
```

In `ReconService.resolve`:

```java
@Transactional
public ReconResultResponse resolve(Long id, String note) {
    ReconBreak rb = reconBreakRepository.findById(id)
            .orElseThrow(() -> new ReconBreakNotFoundException(id));
    rb.setStatus("RESOLVED");
    rb.setResolvedAt(java.time.OffsetDateTime.now());
    rb.setResolutionNote(note);
    return reconMapper.toResponse(rb);
}
```

</details>

**Talking point:** Why PUT not PATCH here? Because the act of resolving
sets multiple fields atomically (status + resolvedAt + note) and the
client cannot resolve a subset. A "resolution" is a coherent operation —
PUT communicates that.

**▶ Run the project — verify TICKET-ADV070 end-to-end**

PUT a resolution note on an open break and confirm status flips to RESOLVED.

```bash
curl -i -X PUT http://localhost:8080/api/v1/recon/results/1/resolve \
  -H "Content-Type: application/json" \
  -d '{"note":"Confirmed via counterparty email on 2026-03-16."}'

# Empty note → 400
curl -i -X PUT http://localhost:8080/api/v1/recon/results/1/resolve \
  -H "Content-Type: application/json" \
  -d '{"note":""}'
```

**Observe:**

- First call returns 200; response body shows `status: RESOLVED`, a non-null `resolvedAt`, and the note echoed in `resolutionNote`.
- Second call returns `HTTP/1.1 400 Bad Request` because `@NotBlank` on the note rejected the empty string.
- A second call on the same id returning 200 again is fine — resolving an already-resolved break should be idempotent.

---

### TICKET-ADV071 — GET /api/v1/audit/trades/{tradeRef}

This endpoint surfaces the **Hibernate Envers** audit history built on
Day 4. Each revision in `_AUD` tables becomes a JSON entry.

<details>
<summary>▶ Reference solution — AuditController</summary>

```java
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/trades/{tradeRef}")
    @PreAuthorize("hasAnyRole('ADMIN','RECON_ANALYST','VIEWER')")
    public ResponseEntity<java.util.List<TradeRevision>> getHistory(
            @PathVariable String tradeRef) {
        return ResponseEntity.ok(auditService.findRevisions(tradeRef));
    }
}

public record TradeRevision(
        long revisionId,
        java.time.OffsetDateTime revisionTimestamp,
        String revisionType,        // ADD / MOD / DEL
        String changedBy,
        TradeResponse snapshot) {}
```

</details>

**Talking point:** This is the first place students see real value from
Envers. Compliance asks "who changed trade TRD-2026-0042 and when?" and
the answer is one HTTP call — not a forensic database dive.

**▶ Run the project — verify TICKET-ADV071 end-to-end**

GET the audit history for a seeded trade (note: this needs Workshop 5B's JWT working, so revisit once ADV072 lands).

```bash
# Once JWT auth is in place, log in as admin first
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@db.com","password":"admin123"}' | jq -r '.token')

# Pull audit history with the admin token
curl -s http://localhost:8080/api/v1/audit/trades/TRD-20260315-0001 \
  -H "Authorization: Bearer $TOKEN" | jq

# Same endpoint without a token (or with a TRADER token) → 403
curl -i http://localhost:8080/api/v1/audit/trades/TRD-20260315-0001
```

**Observe:**

- Admin-authenticated GET returns a JSON array of revision rows — at minimum the initial `ADD` revision.
- Non-admin or unauthenticated call returns `HTTP/1.1 403 Forbidden` (or 401 if no token at all).
- An empty array for a trade you know was edited means Envers `_AUD` tables are not populating — re-check Day 4.

---

## Workshop 5B — JWT + RBAC (TICKET-ADV072 – TICKET-ADV074) (~1 hr 15 min)

### Mini-lecture (15 min)

Whiteboard:

```
1. POST /api/auth/login (username, password)
        ↓ AuthenticationManager
2. UsernamePasswordAuthenticationToken → DaoAuthenticationProvider
        ↓ UserDetailsService + PasswordEncoder
3. Authentication{ principal, authorities, authenticated=true }
        ↓ JwtTokenProvider.generate(auth)
4. Token { sub: "trader@db.com", roles: ["ROLE_TRADER"], exp: now+60min }
        ↓ HTTP 200 { token: "eyJhbGc..." }

NEXT REQUEST:
5. GET /api/v1/trades  Authorization: Bearer eyJ...
        ↓ JwtAuthenticationFilter (OncePerRequestFilter)
6. parse + validate signature + check exp → Authentication into SecurityContext
        ↓
7. @PreAuthorize evaluated by MethodSecurityInterceptor → 200 or 403
```

Key points to land:
- **Stateless.** Server keeps zero session state. The token IS the session.
- **Signed, not encrypted.** Anyone can read the payload (it's base64).
  Don't put secrets in JWT claims.
- **HS256 vs RS256.** HS256 (symmetric) is fine for a monolith;
  RS256 (asymmetric) is the choice when the verifier is a different
  service from the issuer (we'd switch if we split recon-service from
  auth-service).

---

### TICKET-ADV072 — JWT issuance on /api/auth/login

**Common student blockers:**
- They put the JWT secret in `application.yml` and commit it. Hard fail.
- They use `Keys.secretKeyFor(SignatureAlgorithm.HS256)` — generates a
  random key on every boot, so tokens issued before a restart are rejected.
- They omit `iat` or `exp` claims. Tokens never expire (security
  vulnerability) or fail validation on jwt.io.

**Unblocking ladder:**
1. **Nudge:** "Where is your JWT secret defined? If I had your git history could I steal it?"
2. **Hint:** "12-factor: secrets live in environment variables, not in source. What's the Spring property syntax for that?"
3. **Reveal:** `${APP_JWT_SECRET:dev-only-do-not-use-in-prod-AT-LEAST-256-bits-please-xx}` — env var with a clearly-marked dev default.

<details>
<summary>▶ Reference solution — JwtTokenProvider + AuthController</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/security/JwtTokenProvider.java
package com.dbtraining.reconx.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long validityMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.validity-ms:3600000}") long validityMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityMs = validityMs;
    }

    public String generateToken(Authentication auth) {
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Date now = new Date();
        Date exp = new Date(now.getTime() + validityMs);

        return Jwts.builder()
                .subject(auth.getName())
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .clockSkewSeconds(30)         // small leeway for clock drift
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

```java
// File: backend/src/main/java/com/dbtraining/reconx/controller/AuthController.java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtTokenProvider tokenProvider;

    public AuthController(AuthenticationManager authManager, JwtTokenProvider tokenProvider) {
        this.authManager = authManager;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest req) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        SecurityContextHolder.getContext().setAuthentication(auth);
        String token = tokenProvider.generateToken(auth);
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", 3600));
    }
}

public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
public record LoginResponse(String token, String tokenType, long expiresIn) {}
```

```yaml
# backend/src/main/resources/application-dev.yml
app:
  jwt:
    secret: ${APP_JWT_SECRET:dev-only-do-not-use-in-prod-AT-LEAST-256-bits-please-xx}
    validity-ms: 3600000   # 60 minutes
```

</details>

**Talking point:** Walk through copying the token into jwt.io live. Show
the decoded `roles: ["ROLE_TRADER"]` — students light up when the abstract
becomes concrete.

**▶ Run the project — verify TICKET-ADV072 end-to-end**

POST credentials to `/api/auth/login` and decode the returned JWT.

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@db.com","password":"admin123"}' | jq

# Wrong credentials → 401
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@db.com","password":"wrong"}'
```

**Observe:**

- Valid login returns 200 with a body containing `token`, `tokenType: "Bearer"`, `expiresInSeconds`, and `role`.
- Paste the token into [jwt.io](https://jwt.io/) — the payload shows `sub` (email), `role`, `iat`, and `exp` claims, signed with HS256.
- Wrong password returns `HTTP/1.1 401 Unauthorized` — if you get 200 with a token, BCrypt verification is short-circuiting.

---

### TICKET-ADV073 — JwtAuthenticationFilter

**Common student blockers:**
- They `extends GenericFilterBean` instead of `OncePerRequestFilter`. The
  filter runs twice on async dispatches and they get a confusing
  authentication-overwrite bug.
- They write the SecurityContext but forget `chain.doFilter(req, res)`.
  Every request hangs.
- They catch `JwtException` and return 200. Should fail closed.

<details>
<summary>▶ Reference solution — JwtAuthenticationFilter</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/security/JwtAuthenticationFilter.java
package com.dbtraining.reconx.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = req.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            try {
                Claims claims = tokenProvider.parseClaims(token);
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) claims.get("roles");
                var authorities = roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                var auth = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException ex) {
                // Invalid / expired token → fail closed; downstream returns 401
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}
```

</details>

**Talking point:** "Why not throw on a bad token here?" Because the
filter's job is just to *populate* the context. The actual access decision
happens later in `AuthorizationFilter` / `@PreAuthorize`. Single
responsibility.

**▶ Run the project — verify TICKET-ADV073 end-to-end**

Hit a protected endpoint without a token (401), with a valid token (200), and with a junk token (401).

```bash
# No token → 401
curl -i http://localhost:8080/api/v1/trades

# With valid token → 200
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@db.com","password":"admin123"}' | jq -r '.token')
curl -i http://localhost:8080/api/v1/trades \
  -H "Authorization: Bearer $TOKEN"

# Bogus / expired token → 401
curl -i http://localhost:8080/api/v1/trades \
  -H "Authorization: Bearer not.a.real.token"
```

**Observe:**

- Anonymous request returns `HTTP/1.1 401 Unauthorized`.
- Valid token returns 200 with the trades list — `SecurityContextHolder` was populated by the filter.
- Bogus token also returns 401; the filter caught `JwtException`, cleared the context, and let the chain bounce the request — if you see a 500 stack trace, the filter is re-throwing instead of failing closed.

---

### TICKET-ADV074 — SecurityFilterChain + RBAC

**Common student blockers:**
- They forget `csrf(disable)`. POST returns 403 because the CSRF token is
  missing. (CSRF is irrelevant for stateless JWT APIs — it's a session
  attack.)
- They use `hasRole("ROLE_ADMIN")`. Spring auto-prefixes `ROLE_` so this
  becomes `ROLE_ROLE_ADMIN` and nothing matches.
- They put the JWT filter **after** `UsernamePasswordAuthenticationFilter`.
  By the time auth runs, the context is already empty.

<details>
<summary>▶ Reference solution — SecurityConfig</summary>

```java
// File: backend/src/main/java/com/dbtraining/reconx/config/SecurityConfig.java
package com.dbtraining.reconx.config;

import com.dbtraining.reconx.security.JwtAuthenticationFilter;
import com.dbtraining.reconx.security.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtTokenProvider tokenProvider) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(tokenProvider);

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST,   "/api/v1/trades").hasAnyRole("TRADER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/api/v1/trades/**").hasAnyRole("TRADER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH,  "/api/v1/trades/**").hasAnyRole("TRADER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/trades/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,   "/api/v1/recon/**").hasAnyRole("RECON_ANALYST", "ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/api/v1/recon/**").hasAnyRole("RECON_ANALYST", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
```

</details>

**Talking point:** "Where's the policy — in `SecurityFilterChain` or in
`@PreAuthorize`?" Both. URL-level rules go in the filter chain (path
matching is faster than method invocation). Fine-grained "is this user
the *owner* of this trade?" lives in `@PreAuthorize` with SpEL.
Defence-in-depth.

**▶ Run the project — verify TICKET-ADV074 end-to-end**

Run the RBAC matrix: log in as VIEWER, attempt a write, expect 403; switch to TRADER, expect 201.

```bash
# VIEWER attempts a POST → 403
VIEWER_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"viewer@db.com","password":"viewer123"}' | jq -r '.token')
curl -i -X POST http://localhost:8080/api/v1/trades \
  -H "Authorization: Bearer $VIEWER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tradeRef":"TRD-20260315-0002","instrumentId":1,"counterpartyId":1,"assetClass":"EQUITY","side":"BUY","quantity":100.0,"price":245.50,"tradeDate":"2026-03-15"}'

# TRADER attempts the same POST → 201
TRADER_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r '.token')
curl -i -X POST http://localhost:8080/api/v1/trades \
  -H "Authorization: Bearer $TRADER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tradeRef":"TRD-20260315-0003","instrumentId":1,"counterpartyId":1,"assetClass":"EQUITY","side":"BUY","quantity":100.0,"price":245.50,"tradeDate":"2026-03-15"}'
```

**Observe:**

- VIEWER POST returns `HTTP/1.1 403 Forbidden` — the URL-level rule blocked it before the controller.
- TRADER POST returns `HTTP/1.1 201 Created` with a `Location` header.
- A 401 instead of 403 means the JWT filter didn't authenticate the token at all — check the `Authorization` header and the filter ordering.

---

## Workshop 5C — MockMvc + Testcontainers + versioning (TICKET-ADV075 – TICKET-ADV080) (~1 hr)

### Mini-lecture (5 min)

Two tiers of tests:
- **`@WebMvcTest`** — slice test. Loads MVC infra + the named controller
  only. Service is mocked. Fast (~500 ms per test).
- **`@SpringBootTest` + Testcontainers** — full stack. Real Postgres,
  real Liquibase, real Spring Security. Slow (~5 s startup), high-fidelity.

You need both. Slice tests prove the contract; integration tests prove the
wiring.

---

### TICKET-ADV075 — MockMvc: authenticated create returns 201

<details>
<summary>▶ Reference solution — TradeControllerWebMvcTest</summary>

```java
// File: backend/src/test/java/com/dbtraining/reconx/controller/TradeControllerWebMvcTest.java
package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.TradeRequest;
import com.dbtraining.reconx.dto.TradeResponse;
import com.dbtraining.reconx.service.TradeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TradeController.class)
class TradeControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private TradeService tradeService;

    private TradeRequest validRequest() {
        return new TradeRequest(
                "TRD-2026-9999", 1L, 1L,
                new BigDecimal("100.0000"), new BigDecimal("245.50"),
                LocalDate.now(), "PENDING");
    }

    @Test
    @WithMockUser(roles = "TRADER")
    void testCreateTrade_authenticated_returns201() throws Exception {
        when(tradeService.create(any())).thenReturn(
                new TradeResponse(42L, "TRD-2026-9999", "SAP.DE", "Apex Brokers Inc",
                        new BigDecimal("100.0000"), new BigDecimal("245.50"),
                        LocalDate.now(), "PENDING"));

        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/trades/42")))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.tradeRef").value("TRD-2026-9999"));
    }
}
```

</details>

**Talking point:** `@WebMvcTest` does NOT load `JpaRepository` beans. If
the controller depends on one transitively (via service), you'll see
`UnsatisfiedDependencyException`. Solution: `@MockBean` the service so
the slice stays thin.

**▶ Run the project — verify TICKET-ADV075 end-to-end**

Run the MockMvc slice test in isolation — no Postgres or full Boot context needed.

```bash
./mvnw -pl backend test -Dtest=TradeControllerWebMvcTest#testCreateTrade_authenticated_returns201
```

**Observe:**

- Test passes green; Surefire reports `Tests run: 1, Failures: 0, Errors: 0`.
- The slice loads only `TradeController` — no `DataSource` or JPA in the context (so no Docker required for this test).
- Failure with `UnsatisfiedDependencyException` means the controller is autowiring a `JpaRepository` directly; push that dependency behind the service so `@MockBean` can stand in.

---

### TICKET-ADV076 — MockMvc: unauthenticated returns 401

<details>
<summary>▶ Reference solution</summary>

```java
@Test
void testCreateTrade_unauthenticated_returns401() throws Exception {
    mockMvc.perform(post("/api/v1/trades")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isUnauthorized());
}
```

</details>

**Talking point:** No `@WithMockUser`, no CSRF token — and the request
hits the security filter chain before validation runs. Order matters.

**▶ Run the project — verify TICKET-ADV076 end-to-end**

Run the unauthenticated MockMvc test method only.

```bash
./mvnw -pl backend test -Dtest=TradeControllerWebMvcTest#testCreateTrade_unauthenticated_returns401
```

**Observe:**

- Test passes green; assertion is `status().isUnauthorized()` (401).
- The service is never called — Mockito's default verification shows zero interactions with `tradeService`.
- A 403 here instead of 401 means anonymous CSRF rejection beat the auth check; ensure `STATELESS` + CSRF disabled in `SecurityConfig`.

---

### TICKET-ADV077 — MockMvc: VIEWER returns 403

<details>
<summary>▶ Reference solution</summary>

```java
@Test
@WithMockUser(roles = "VIEWER")
void testCreateTrade_viewerRole_returns403() throws Exception {
    mockMvc.perform(post("/api/v1/trades")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest()))
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().isForbidden());
}
```

</details>

**Talking point:** 401 vs 403 — 401 says "I don't know who you are";
403 says "I know who you are and you can't do this". HR analogy: 401 is
"badge in"; 403 is "you have a badge but not for this floor".

**▶ Run the project — verify TICKET-ADV077 end-to-end**

Run the full WebMvc test class — all three role-coverage methods should be green.

```bash
./mvnw -pl backend test -Dtest=TradeControllerWebMvcTest
```

**Observe:**

- All three methods pass: `..._authenticated_returns201`, `..._unauthenticated_returns401`, `..._viewerRole_returns403`.
- Each test asserts a distinct status code — 201, 401, 403 — covering the happy path plus two security boundaries.
- A 201 instead of 403 for the VIEWER test means someone added `VIEWER` to the POST role list in `SecurityConfig` — this test catches that regression.

---

### TICKET-ADV078 — Full lifecycle integration test with Testcontainers

<details>
<summary>▶ Reference solution — TradeLifecycleIT</summary>

```java
// File: backend/src/test/java/com/dbtraining/reconx/integration/TradeLifecycleIT.java
package com.dbtraining.reconx.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TradeLifecycleIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort int port;
    @Autowired ObjectMapper om;

    static String token;
    static Long createdId;
    static String reconJobId;
    static Long breakId;

    RestTemplate http = new RestTemplate();

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return h;
    }

    @Test @Order(1)
    void loginAsAdmin() {
        var body = """
                {"username":"admin@db.com","password":"admin123"}
                """;
        var req = new HttpEntity<>(body, new HttpHeaders() {{
            setContentType(MediaType.APPLICATION_JSON);
        }});
        var resp = http.postForEntity(
                "http://localhost:" + port + "/api/auth/login", req, JsonNode.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        token = resp.getBody().get("token").asText();
        Assertions.assertNotNull(token);
    }

    @Test @Order(2)
    void createTrade() {
        var body = """
                {"tradeRef":"IT-2026-0001","instrumentId":1,"counterpartyId":1,
                 "quantity":100.0,"price":245.50,"tradeDate":"2026-03-15","status":"PENDING"}
                """;
        var resp = http.exchange(
                "http://localhost:" + port + "/api/v1/trades",
                HttpMethod.POST, new HttpEntity<>(body, authHeaders()), JsonNode.class);
        Assertions.assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        createdId = resp.getBody().get("id").asLong();
    }

    @Test @Order(3)
    void getTradeBack() {
        var resp = http.exchange(
                "http://localhost:" + port + "/api/v1/trades?status=PENDING",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), JsonNode.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        Assertions.assertTrue(resp.getBody().get("totalElements").asLong() >= 1);
    }

    @Test @Order(4)
    void patchStatus() {
        var body = """
                {"status":"MATCHED"}
                """;
        var resp = http.exchange(
                "http://localhost:" + port + "/api/v1/trades/" + createdId + "/status",
                HttpMethod.PATCH, new HttpEntity<>(body, authHeaders()), JsonNode.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        Assertions.assertEquals("MATCHED", resp.getBody().get("status").asText());
    }

    @Test @Order(5)
    void triggerRecon() {
        var body = """
                {"from":"2026-03-01","to":"2026-03-31"}
                """;
        var resp = http.exchange(
                "http://localhost:" + port + "/api/v1/recon/run",
                HttpMethod.POST, new HttpEntity<>(body, authHeaders()), JsonNode.class);
        Assertions.assertEquals(HttpStatus.ACCEPTED, resp.getStatusCode());
        reconJobId = resp.getBody().get("jobId").asText();
    }

    @Test @Order(6)
    void resolveBreak() {
        // (Test data seeded by Liquibase guarantees at least one break with id 1.)
        breakId = 1L;
        var body = """
                {"note":"Confirmed via counterparty email on 2026-03-16."}
                """;
        var resp = http.exchange(
                "http://localhost:" + port + "/api/v1/recon/results/" + breakId + "/resolve",
                HttpMethod.PUT, new HttpEntity<>(body, authHeaders()), JsonNode.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        Assertions.assertEquals("RESOLVED", resp.getBody().get("status").asText());
    }
}
```

</details>

**Talking point:** `@ServiceConnection` (Spring Boot 3.1+) auto-wires the
Testcontainer to Spring's `DataSource` — no manual
`@DynamicPropertySource` for JDBC URL/user/password. Demo what the boot
log looks like: `HikariPool — jdbcUrl: jdbc:postgresql://localhost:53241/...`
where the port is the container's random port.

**▶ Run the project — verify TICKET-ADV078 end-to-end**

Run the Testcontainers-backed integration test (Docker must be running).

```bash
./mvnw -pl backend verify -Dit.test=TradeLifecycleIT
```

**Observe:**

- A `postgres:16-alpine` container starts (visible in `docker ps` mid-run) and is reaped at the end.
- All 6 ordered test methods pass — `loginAsAdmin`, `createTrade`, `getTradeBack`, `patchStatus`, `triggerRecon`, `resolveBreak`.
- Final test asserts `status == RESOLVED`; an `OptimisticLockingFailureException` mid-run usually means missing `@Order` annotations and a race between methods.

---

### TICKET-ADV079 — Verify Liquibase ran on fresh DB

<details>
<summary>▶ Reference solution — LiquibaseMigrationsIT</summary>

```java
// File: backend/src/test/java/com/dbtraining/reconx/integration/LiquibaseMigrationsIT.java
package com.dbtraining.reconx.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class LiquibaseMigrationsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired JdbcTemplate jdbc;

    @Test
    void liquibase_applied_all_expected_changesets() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog", Integer.class);
        // Day 1 = 9 changesets (init + schema + seed + views + audit)
        // Day 4 = +3 Envers tables. Day 5 = +1 deleted_at column. Total expected = 13.
        assertThat(applied).isGreaterThanOrEqualTo(13);

        Integer trades = jdbc.queryForObject(
                "SELECT COUNT(*) FROM trades WHERE deleted_at IS NULL", Integer.class);
        assertThat(trades).isGreaterThanOrEqualTo(10);   // seed_data.sql
    }
}
```

</details>

**Talking point:** This test catches the embarrassing case where a
developer adds a changeset on their laptop, runs the app once (which
applies it), then forgets to commit the XML. CI on a fresh container
fails — early signal.

**▶ Run the project — verify TICKET-ADV079 end-to-end**

Run the Liquibase migrations check against a fresh container.

```bash
./mvnw -pl backend test -Dtest=LiquibaseMigrationsIT
```

**Observe:**

- A fresh `postgres:16-alpine` container starts and Liquibase applies every changeset from an empty schema.
- `databasechangelog` row count is at least 13 (Day 1 = 9, Day 4 = +3, Day 5 = +1) and `trades` has at least 10 non-deleted seed rows.
- Failure with `relation "databasechangelog" does not exist` means Liquibase never ran — check `spring.liquibase.enabled` and the changelog path.

---

### TICKET-ADV080 — API versioning + deprecation

<details>
<summary>▶ Reference solution — versioned prefix + deprecation header</summary>

```java
// All v1 controllers carry the prefix:
@RequestMapping("/api/v1/trades")
public class TradeController { ... }

// Example deprecation of an old endpoint surface area:
@Deprecated(since = "v1.4.0", forRemoval = true)
@GetMapping(value = "/old-search", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<Void> oldSearch(HttpServletResponse response) {
    response.setHeader("Deprecation", "true");
    response.setHeader("Sunset", "Sat, 1 Jul 2026 00:00:00 GMT");
    response.setHeader("Link",
            "</api/v1/trades?status=...>; rel=\"successor-version\"");
    return ResponseEntity.status(HttpStatus.GONE).build();
}
```

</details>

**Talking point:** "Why `/api/v1` on every endpoint, not just `/api`?"
Because the day we ship `/api/v2/trades` (breaking change to the response
shape), `/api/v1/trades` must keep working unchanged for existing
clients. The version is part of the contract; prefixing every route makes
that contract visible in the URL.

**▶ Run the project — verify TICKET-ADV080 end-to-end**

Hit the deprecated v0 endpoint and the current v1 endpoint and compare responses.

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@db.com","password":"admin123"}' | jq -r '.token')

# Deprecated path → 410 Gone with deprecation headers
curl -i http://localhost:8080/api/v0/trades \
  -H "Authorization: Bearer $TOKEN"

# Current path → 200
curl -i http://localhost:8080/api/v1/trades \
  -H "Authorization: Bearer $TOKEN"
```

**Observe:**

- v0 response is `HTTP/1.1 410 Gone` with `Deprecation: true`, `Sunset: <HTTP-date>`, and `Link: </api/v1/...>; rel="successor-version"` headers set.
- v1 response is `HTTP/1.1 200 OK` with the normal paginated trades body.
- A 404 on `/api/v0/trades` instead of 410 means the deprecated handler is not registered — clients then think the surface area never existed, which is worse than a clear deprecation signal.

---

<details>
<summary><b>Q&A bank</b></summary>


Have these ready to fire at students or to answer when asked.

1. **Q:** Why JWT not session cookies?
   **A:** Two reasons. (1) Statelessness — no session table to replicate
   across nodes; the server scales horizontally without sticky sessions.
   (2) Mobile/SPA-friendly — the React frontend and a future Android app
   both speak Bearer tokens without cookie wrangling. The trade-off:
   revocation is harder (you can't invalidate a JWT server-side without a
   blocklist), so we keep tokens short-lived (60 min).

2. **Q:** Where do I store the JWT on the frontend — localStorage or HttpOnly cookie?
   **A:** HttpOnly cookie is safer (XSS can't read it). localStorage is
   easier (works across subdomains, no CSRF concern). For ReconX our
   refresh tokens go in an HttpOnly Secure SameSite=Strict cookie; the
   access token is held in memory only (not localStorage). That's the
   industry's emerging consensus for 2026.

3. **Q:** Refresh tokens: yes or no?
   **A:** Yes, when access tokens are short-lived (60 min) but users
   shouldn't have to re-login that often. The refresh token (7 days, HttpOnly
   cookie) is exchanged at `/api/auth/refresh` for a new access token.
   The refresh token MUST be revocable server-side — store its ID in
   Postgres with `revoked_at`.

4. **Q:** `@PreAuthorize` vs filter-based authorization — which?
   **A:** Both, layered. Filter chain handles coarse URL-level rules
   (faster, runs before controller invocation). `@PreAuthorize` handles
   fine-grained policy with SpEL — e.g. `@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.userId")`.
   Defence-in-depth.

5. **Q:** Soft delete vs hard delete?
   **A:** Soft delete in finance. Always. Trades have audit/compliance
   obligations — a deleted trade is still part of history. Hard delete
   only for genuine GDPR right-to-erasure requests against PII, never
   for transactional records.

6. **Q:** PUT vs PATCH for partial updates — should we really use PATCH?
   **A:** Yes, when the update is partial. PUT semantics say "replace the
   entire resource"; a one-field update is a lie wrapped in PUT. PATCH
   is honest. JSON Patch (RFC 6902) is the formal version; for our
   purposes, `PATCH /trades/{id}/status` with a status-only body is a
   pragmatic compromise.

7. **Q:** MockMvc vs RestAssured — which?
   **A:** MockMvc for slice tests inside the JVM (no network, no port).
   RestAssured for true black-box tests against a running server. For
   Testcontainers integration tests, either works; we use `RestTemplate`
   in this case study to keep the dependency surface smaller, but
   RestAssured's DSL is nicer if you adopt it.

8. **Q:** Testcontainers is slow — can we share the container across tests?
   **A:** Yes. Two techniques. (1) **Reuse:** mark the container `static`
   in a base class — JUnit Jupiter starts it once per test class. (2)
   **Testcontainers reuse mode:** enable `testcontainers.reuse.enable=true`
   in `~/.testcontainers.properties` and call `.withReuse(true)` — the
   same container survives across test *runs*. Caveat: data pollution.
   Use `@DirtiesContext` or a `@BeforeEach` cleanup query.

9. **Q:** API versioning — URL (`/v1/`) vs Accept header (`Accept: application/vnd.reconx.v1+json`)?
   **A:** URL versioning is visible, debuggable in browser DevTools, easy
   to route in nginx. Accept-header versioning is purist (a URL identifies
   a resource, not a representation). For internal-facing services like
   ReconX, URL versioning wins because operators have to read logs and
   curl endpoints by hand all day.

10. **Q:** Why `/api/v1` prefix on everything, not just `/api`?
    **A:** Because the prefix locks in the contract. The day we ship
    `/api/v2/trades` (breaking change), `/api/v1/trades` keeps working
    for clients on the old contract. Without the version segment, we'd
    have no place to put v2 without breaking v1.

11. **Q:** Why is the JWT signed but not encrypted?
    **A:** The signature proves the token wasn't tampered with — that's
    what we need. Encryption would hide the payload, but the payload is
    just `{sub, roles, exp}` — public knowledge the moment the user
    logs in. Don't put secrets in JWT claims and you don't need
    encryption.

12. **Q:** What's `clockSkewSeconds(30)` doing?
    **A:** Tolerating up to 30 seconds of clock drift between the
    issuing server and the validating server. Without it, a token issued
    at 12:00:00.000 with a 1-second window of clock difference may be
    rejected as "not yet valid" or "already expired".

13. **Q:** What about CORS?
    **A:** Add a `CorsConfigurationSource` bean and reference it in
    `http.cors()`. For dev: allow `http://localhost:5173`. For prod:
    only the demo-laptop's served origin. We wire this on Day 7 when
    the frontend starts hitting the API.

14. **Q:** Should `/actuator/*` be authenticated?
    **A:** Health and info, no (load balancers and monitoring need them
    unauthenticated). Metrics, prometheus, env: yes — they leak
    configuration. We open `/actuator/health` and `/actuator/info`,
    require auth (ADMIN role) for the rest. Hardened on Day 6.

15. **Q:** What if a TRADER tries to UPDATE a trade they didn't create?
    **A:** Right now, allowed — role-level only. If we needed
    ownership-based authorization, we'd add
    `@PreAuthorize("hasRole('ADMIN') or @tradeService.isOwner(#id, authentication.name)")`
    on the method. SpEL + a service-layer permission check.

---

</details>

<details>
<summary><b>End-of-day debrief prompts</b></summary>


At 16:45:

1. "Draw the request flow from `curl POST /api/v1/trades` (with a Bearer
   token) all the way to the database. Name every Spring component the
   request passes through."
2. "A grad on another team says 'JWT is insecure because anyone can decode
   it'. What's right and what's wrong about that statement?"
3. "Your MockMvc test passes but a production curl with the same payload
   gets 403. List three things that could differ."
4. "Why do we have BOTH `SecurityFilterChain` rules AND `@PreAuthorize`?"

If a grad can't talk through #1 confidently, they will struggle on Day 6
when they need to reason about which beans see which requests.

---

</details>

<details>
<summary><b>Things that have gone wrong before</b></summary>


- **JWT secret committed to git.**

  A grad set `app.jwt.secret: my-super-secret-key-123` in `application.yml` and pushed. The PR went through review without anyone catching it.

  **Fix:** Add a pre-commit hook (`git-secrets` or `detect-secrets`) and review every PR diff for hardcoded secrets. Make `${APP_JWT_SECRET}` the only way the secret enters the app. Add a unit test that fails if the effective secret matches a known-default string.

- **JWT validation passed for expired tokens — `clockSkewSeconds` misconfigured.**

  A grad set `clockSkewSeconds(3600)` to "fix" flaky tests. Tokens were honoured for an hour past expiry.

  **Fix:** Cap skew at 30-60 seconds. If tests are flaky because of clock drift, fix the test (mock `Clock`) — don't widen the production window.

- **`@PreAuthorize` on a private method — Spring AOP couldn't proxy it.**

  A grad annotated a private helper inside a service. Spring's AOP proxies the bean's public surface only — annotations on private methods are silently ignored. The "secured" call ran for everyone.

  **Fix:** Annotate the public entry point. Code review: grep for `@PreAuthorize` near `private` keywords.

- **MockMvc test passed but real Spring app rejected the same request — used `standaloneSetup` not `webAppContextSetup`.**

  `standaloneSetup` skips the security filter chain entirely. Test went green; production hit a 403.

  **Fix:** Default to `@WebMvcTest` which uses `webAppContextSetup` and loads the security config automatically. Ban `standaloneSetup` in the codebase.

- **Testcontainers test polluted the DB across tests because `@DirtiesContext` was missing.**

  Test order changed (alphabetical), earlier tests had inserted rows the later tests assumed weren't there, later tests failed mysteriously.

  **Fix:** Either annotate the class `@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)` or use a `@BeforeEach` that truncates application tables (keep `databasechangelog` intact).

- **Soft delete query didn't filter out deleted rows in list endpoint.**

  A team added `deleted_at` to the entity and to DELETE, but forgot the filter on GET. The deleted trades reappeared in the list.

  **Fix:** `@SQLRestriction("deleted_at IS NULL")` on the entity class — applies to every Hibernate-issued SELECT automatically. Or, less safely, remember to filter in every Specification.

- **RBAC roles spelt inconsistently — `ROLE_ADMIN` vs `ADMIN`.**

  Spring Security auto-prefixes `ROLE_` when you use `hasRole("ADMIN")` but NOT when you use `hasAuthority("ADMIN")`. Tokens claimed `roles: ["ADMIN"]`, filter looked for `ROLE_ADMIN`, every authenticated call returned 403.

  **Fix:** Pick one convention. We use **role-without-prefix in JWT claims** + `SimpleGrantedAuthority("ROLE_" + role)` when building the `Authentication`. Document it in the README.

- **POST `/trades` returned 200 not 201 — REST convention violation.**

  A grad just did `ResponseEntity.ok(saved)` because "it works". Slice test passed (didn't assert the status code).

  **Fix:** Add an assertion: `.andExpect(status().isCreated())` on every POST test — it forces grads to use `ResponseEntity.created(...)`.

- **CSRF disabled too late.**

  A grad enabled CSRF in dev profile but not in test. MockMvc tests passed; first real POST from Postman got 403 "Invalid CSRF token".

  **Fix:** Disable CSRF unambiguously in `SecurityConfig` for the JWT/stateless API. CSRF is a session attack; stateless APIs don't need it.

- **`@WebMvcTest` failed with `UnsatisfiedDependencyException` — slice pulled in a `JpaRepository`.**

  Controller depended on service, service depended on repository, slice tried to instantiate the repository, no JPA infra was loaded.

  **Fix:** `@MockBean` the service, never the repository. Keep slices thin. ---</details>

<details>
<summary><b>Hand-off to Day 6</b></summary>


By end-of-day each team should have:

- [ ] All nine controller endpoints (TICKET-ADV063–TICKET-ADV071) live, returning correct
      status codes, and surfaced in Swagger UI.
- [ ] `POST /api/auth/login` returns a valid JWT; jwt.io can decode it with
      the configured secret.
- [ ] `JwtAuthenticationFilter` wired into the chain BEFORE
      `UsernamePasswordAuthenticationFilter`.
- [ ] RBAC matrix enforced: VIEWER cannot POST, only ADMIN can DELETE,
      RECON_ANALYST can resolve breaks. Verified by MockMvc 401/403 tests.
- [ ] `TradeLifecycleIT` runs against a Testcontainer and passes
      end-to-end (login → POST → GET → PATCH → recon → resolve).
- [ ] `LiquibaseMigrationsIT` passes on a clean container.
- [ ] All controllers prefixed `/api/v1/`; deprecation example documented.

**Day 6 picks up with:** Caching (Caffeine + Spring `@Cacheable`),
Micrometer custom metrics, Prometheus scraping, Grafana dashboards. The
controllers shipped today are what we'll instrument and observe.

**Next:** [TrainersGuide/day6/](../day6/README.md)

</details>
