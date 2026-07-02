# How to Run ReconX (Trainer Copy)

Step-by-step setup guide for the **Advanced (Intermediate-Hybrid) Track**
ReconX case study. Run these in order the **first time** you bring the
project up. Once everything is wired, daily use boils down to a single
`docker compose pull && docker compose up -d`.

The deploy model is **Option A**: GitHub Actions builds the images and
pushes them to GHCR; your laptop pulls and runs them. No local Maven /
Node build required for the full stack — only for the fast dev loop.

> All paths are relative to the project root (the directory containing
> `docker-compose.yml`, `backend/`, `frontend/`, `TrainersGuide/`).

---

## What you're booting

A 7-service stack (8 with the optional Kafdrop debug container):

| Service | Image | Port | Why |
|---|---|---|---|
| `backend`    | `ghcr.io/<your-org>/reconx-backend:latest`  | 8080 | Spring Boot REST API + Kafka producers/consumers |
| `frontend`   | `ghcr.io/<your-org>/reconx-frontend:latest` | 5173 | React 19 + Vite, served by nginx |
| `postgres`   | `postgres:16-alpine`                        | 5432 | DB (Liquibase migrates on backend boot) |
| `zookeeper`  | `confluentinc/cp-zookeeper:7.6.0`           | —    | Kafka quorum |
| `kafka`      | `confluentinc/cp-kafka:7.6.0`               | 9092 | trade-events / recon-results / system-alerts + DLQ |
| `prometheus` | `prom/prometheus:v2.54.1`                   | 9090 | Scrapes /api/actuator/prometheus + alert rules |
| `grafana`    | `grafana/grafana:11.2.0`                    | 3000 | "ReconX Overview" dashboard auto-provisioned |
| `kafdrop`    | `obsidiandynamics/kafdrop:4.0.1`            | 9000 | **Optional.** Bring up with `--profile debug` |

---

## Prerequisites

> **Terminal**

```bash
java -version          # 25.x — REQUIRED, sealed classes / pattern switch
mvn -v                 # 3.9+ (only if you skip the wrapper)
node -v                # 22.x
docker --version       # 24.x+
docker compose version # v2.x+
gh --version           # optional but handy
```

Install whatever's missing before continuing. The Advanced track is
**Java 25 only** — anything older fails the `sealed interface TradeType`
compile in `model/`.

---

## Platform notes (read once, save grief)

Most of this guide assumes a Unix-like shell (bash/zsh on macOS or Linux).
The two places where platform actually matters:

### Apple Silicon Mac (M1 / M2 / M3 / M4) — and Windows-on-ARM

The CI workflow builds **amd64** (x86_64) images by default. Pulling
those on an arm64 host returns:

```
no matching manifest for linux/arm64/v8 in the manifest list entries
```

**Quick fix — tell Docker to ask for amd64 everywhere in this shell:**

```bash
# macOS / Linux:
export DOCKER_DEFAULT_PLATFORM=linux/amd64

# Windows PowerShell:
$env:DOCKER_DEFAULT_PLATFORM = "linux/amd64"
```

…then run `docker compose pull && docker compose up -d` as normal.
Containers run via **Rosetta emulation** (or QEMU on Windows). JVM
startup is ~20-30% slower; fine for dev/demo, not for production.

**Make it permanent on macOS:** add the `export` line to `~/.zshrc`.
**On Windows:** Settings → System → About → Advanced system settings →
Environment Variables → add `DOCKER_DEFAULT_PLATFORM = linux/amd64` under
User variables.

**Permanent right fix:** have CI build multi-arch images. See
"Multi-arch builds" at the bottom of this doc.

### Windows specifics

- **Use PowerShell**, not legacy `cmd`. PowerShell has Unicode + modern syntax.
- **Install Docker Desktop with the WSL 2 backend** (Settings → General →
  Use WSL 2 based engine). The Hyper-V backend is ~10× slower for I/O.
- Java users: use `mvnw.cmd`, not `mvnw`. Both are committed.
- **PowerShell command equivalents** for the bash snippets shown below:

  | Bash (shown elsewhere) | PowerShell equivalent |
  |---|---|
  | `cp .env.example .env` | `Copy-Item .env.example .env` |
  | `export FOO=bar` | `$env:FOO = "bar"` (current session only) |
  | `SPRING_PROFILES_ACTIVE=uat ./mvnw spring-boot:run` | `$env:SPRING_PROFILES_ACTIVE = "uat"; .\mvnw.cmd spring-boot:run` |
  | `./mvnw spring-boot:run` | `.\mvnw.cmd spring-boot:run` |
  | `echo "<PAT>" \| docker login ghcr.io -u me --password-stdin` | `"<PAT>" \| docker login ghcr.io -u me --password-stdin` |
  | `grep "Reconciling tradeRef"` | `Select-String "Reconciling tradeRef"` |
  | `docker compose ps`, `docker compose up -d`, `docker pull ...` | Identical |

### Intel Mac, Linux x86_64, Windows x86_64

Nothing extra needed. Default amd64 images run natively.

---

## Step 0 — RAM check

The full 7-service stack with Kafka + Postgres + Prometheus + Grafana
needs **~6 GB free**. On Mac/Windows that's a Docker Desktop setting:

- **Docker Desktop → Settings → Resources → Memory:** at least **6 GB**.
- If you're staying on the **fast dev loop** (H2 only), 2 GB is fine.

---

## Step 1 — Find your `your-org`

`your-org` is your GitHub username (or organisation name if the repo
lives under an org). It appears in every GHCR URL.

> **Terminal** — from the project root

```bash
gh repo view --json owner --jq .owner.login
```

No `gh` CLI? Use git:

```bash
git remote get-url origin
# git@github.com:sidoncode/reconx.git
#                ^^^^^^^^^ that's your-org
```

**Apply it everywhere.** Open `.env.example` and `docker-compose.yml` and
search-and-replace `db-tdi-2026` (the placeholder shipped in this trainer
copy) with your real value.

Example: if your GitHub is `sidoncode`, every occurrence of
`ghcr.io/db-tdi-2026/...` becomes `ghcr.io/sidoncode/...`.

---

## Step 2 — Push code so CI builds the images

GHCR doesn't have any images yet. The first push to `develop`/`main`
triggers `.github/workflows/ci.yml`, which builds and publishes them.

> **Terminal**

```bash
git add .
git commit -m "Wire ReconX Option A deploy"
git push origin main      # or develop — both trigger the docker-and-push job
```

Then in your browser:

1. Open your repo → **Actions** tab.
2. Watch the latest workflow run. First run takes ~6 minutes (no cache).
3. Wait for all 3 jobs (`backend-build-and-test`, `frontend-build-and-test`,
   `docker-and-push`) to go green.

The `docker-and-push` job runs **only on `main`** or version tags
(`v*`) — pushes to feature branches build + test but don't publish.

---

## Step 3 — Verify the images landed in GHCR

After the workflow finishes:

```
https://github.com/<your-org>?tab=packages
```

You should see two packages: `reconx-backend` and `reconx-frontend`.
Click each → tag list shows at least:

- `latest`
- A 40-char SHA tag

---

## Step 4 — Get a GitHub PAT for `read:packages`

Packages are **private by default**. Your laptop needs a token to pull.

1. Open <https://github.com/settings/tokens> (**classic** tokens, NOT
   fine-grained — they don't support GHCR yet).
2. **Generate new token (classic)**.
3. Scopes: tick **only** `read:packages`.
4. Copy the token (you'll only see it once).

> **Want public images instead?** On the package page → **Package settings**
> → change visibility to public. Then no token is needed for pulls.

---

## Step 5 — Log in to GHCR (one-time on the laptop)

> **Terminal**

```bash
echo "<paste-PAT-here>" | docker login ghcr.io -u <your-github-username> --password-stdin
```

You should see `Login Succeeded`. Credentials are cached in
`~/.docker/config.json` until the token expires.

**Verify the pull works** before continuing:

```bash
docker pull ghcr.io/<your-org>/reconx-backend:latest
docker pull ghcr.io/<your-org>/reconx-frontend:latest
```

If both succeed, you're set. If you get `pull access denied`, redo Step 4
with the right scope.

---

## Step 6 — Set up `.env`

> **Terminal** — from project root

```bash
cp .env.example .env
```

Open `.env` and review:

```env
SPRING_PROFILES_ACTIVE=uat                    # Postgres-backed
POSTGRES_HOST=postgres                        # docker DNS name (NOT localhost inside compose)
POSTGRES_PORT=5432
POSTGRES_DB=reconx
POSTGRES_USER=reconx_user
POSTGRES_PASSWORD=reconx_pass

KAFKA_BOOTSTRAP=kafka:29092                   # internal listener inside the docker network

# JWT signing secret. MUST be >= 32 bytes for HS256.
# Generate a real one for prod: `openssl rand -base64 32`
JWT_SECRET=dev-secret-change-me-32-bytes-min!!
JWT_EXP_MIN=60
JWT_REFRESH_EXP_DAYS=7
```

Then add the GHCR image refs (they're not in `.env.example` by default —
docker-compose hard-codes `ghcr.io/db-tdi-2026/...` so swap that for your org):

```env
BACKEND_IMAGE=ghcr.io/<your-org>/reconx-backend:latest
FRONTEND_IMAGE=ghcr.io/<your-org>/reconx-frontend:latest
```

For **demo day**, pin to a specific SHA so a teammate's late push can't
roll the deploy mid-stage:

```bash
SHA=$(git rev-parse HEAD)
echo "SHA=$SHA"
```

Then in `.env`:

```env
BACKEND_IMAGE=ghcr.io/<your-org>/reconx-backend:8a3f9c2b1e4d5f6a7b8c9d0e1f2a3b4c5d6e7f8a
FRONTEND_IMAGE=ghcr.io/<your-org>/reconx-frontend:8a3f9c2b1e4d5f6a7b8c9d0e1f2a3b4c5d6e7f8a
```

---

## Step 7 — Pull and run

> **Terminal** — from project root

```bash
docker compose pull        # fetch the CI-tested images (skip if you'll build locally)
docker compose up -d       # bring up the 7-service stack
docker compose ps          # confirm Up / Up (healthy)
```

First boot takes **~75 s** (Kafka quorum + Liquibase migrations + Spring
Boot startup). Subsequent boots are ~25 s.

If you want Kafdrop for debugging, add the optional debug profile:

```bash
docker compose --profile debug up -d kafdrop
# Open http://localhost:9000
```

---

## Step 8 — Verify the platform is live

Open in browser:

| URL | What |
|---|---|
| <http://localhost:5173>                        | React UI |
| <http://localhost:8080/api/swagger-ui.html>    | Swagger UI |
| <http://localhost:8080/api/actuator/health>    | Health (should report UP) |
| <http://localhost:8080/api/actuator/prometheus>| Raw metrics |
| <http://localhost:9000>                        | Kafdrop (if `--profile debug`) |
| <http://localhost:9090/targets>                | Prometheus targets (`reconx-backend` UP) |
| <http://localhost:3000>                        | Grafana (`admin` / `admin`) |

**End-to-end smoke test:**

1. UI → click **Sign in** → form is pre-filled with `admin@db.com` / `admin123` → submit.
2. UI → **Add trade** → fill in a valid `tradeRef` matching `^[A-Z]{3}-\d{8}-\d{4}$` → submit.
3. Kafdrop (if running) → topic `trade-events` → your event is there.
4. Grafana → **ReconX Overview** → request-rate panel ticks up.
5. Audit row landed:
   ```bash
   docker compose exec postgres psql -U reconx_user -d reconx \
       -c "SELECT id, trade_ref, event_type, event_timestamp FROM audit_log ORDER BY id DESC LIMIT 3;"
   ```
6. Recon consumer fired:
   ```bash
   docker compose logs backend | grep "Recon-trigger received"
   ```

All six green = the platform is healthy.

---

## Login credentials (JWT auth)

Seeded by Liquibase changeset `008-seed.xml`. BCrypt hashes shipped in the
trainer copy.

| Role          | Email           | Password   | Can do                                        |
|---------------|-----------------|------------|-----------------------------------------------|
| ADMIN         | `admin@db.com`  | `admin123` | Everything, incl. DELETE /v1/trades/*          |
| TRADER        | `trader@db.com` | `trader123`| GET + POST + PUT + PATCH on /v1/trades         |
| VIEWER        | `viewer@db.com` | `viewer123`| GET on /v1/trades only                         |
| RECON_ANALYST | `recon@db.com`  | `recon123` | GET on /v1/trades, full access on /v1/recon/*  |

### Get a JWT via curl

```bash
TOKEN=$(curl -s http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@db.com","password":"admin123"}' | jq -r .token)
echo "$TOKEN"
```

### Use the token

```bash
curl -s http://localhost:8080/api/v1/trades \
  -H "Authorization: Bearer $TOKEN" | jq '.items | length'
```

### Authorize in Swagger

Swagger UI → **Authorize** (padlock top right) → paste **just the token**
(no `Bearer ` prefix — Swagger adds it) → Authorize → Close. Every "Try
it out" call now sends the header.

### Use in Postman

Import the collection at `postman/ReconX.postman_collection.json` (when
shipped). Set the `token` collection variable from the login response.

---

## Daily flow (after first-time setup)

Whenever you want the latest CI-built version:

```bash
docker compose pull
docker compose up -d
```

Two commands; one optional (`docker compose ps` to verify). That's the
whole CD loop.

---

## Teardown

```bash
docker compose down              # stop services; keep volumes
docker compose down -v           # also wipe postgres_data + grafana_data
docker compose down --rmi local  # also delete any locally-built images
```

Use `docker compose down -v` between demo rehearsals so each run starts
clean (no leftover trades, fresh Liquibase migration).

---

## Fast dev loop without Docker (Java / React code changes)

When iterating on backend or frontend code, Docker round-trips are slow.
Use the dev profile (H2 in-memory) + Vite proxy instead.

> **Terminal #1 — backend** (from `backend/`)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

This boots with:
- **H2 in-memory DB** (Liquibase migrations apply; JSONB/partitioning/
  matview steps skip via `<preConditions>`).
- **No Kafka required** — `spring.kafka.listener.missing-topics-fatal=false`
  is set in `application-dev.yml`, so the app starts cleanly and just
  logs producer warnings whenever you POST a trade. The REST API still works.
- H2 web console at <http://localhost:8080/api/h2> · JDBC URL
  `jdbc:h2:mem:reconx`, user `sa`, no password.

> **Terminal #2 — frontend** (from `frontend/`)

```bash
npm install      # one-time
npm run dev
```

Vite proxies `/api` to `http://localhost:8080` (see `vite.config.js`), so
the frontend's `apiService.js` Just Works against the dev backend.

URLs in dev mode:
- React UI:    <http://localhost:5173>
- Swagger:     <http://localhost:8080/api/swagger-ui.html>
- H2 console:  <http://localhost:8080/api/h2>

**Caveat:** Kafka isn't running, so producer logs will show
`UnknownTopicOrPartitionException` warnings whenever you POST a trade.
The trade is still persisted; only the downstream consumers don't fire.
**Run Kafka separately** if you want event flow:

```bash
docker compose up -d zookeeper kafka
```

---

## Fallback — local Docker build (offline / no GHCR access)

When you can't pull from GHCR (offline, no PAT, or CI hasn't run yet),
build locally:

```bash
# from project root
docker compose build backend frontend
docker compose up -d
```

First build takes ~6 min (Maven downloads the dep tree + npm install).
Subsequent builds reuse Docker's layer cache → ~30 s.

To skip Maven downloads inside the image build, build the JAR locally first:

```bash
cd backend && ./mvnw -DskipTests package && cd ..
docker compose build backend
```

---

## Common issues

| Symptom | Cause | Fix |
|---|---|---|
| `pull access denied for ghcr.io/...` | Not logged in, or PAT lacks `read:packages` | Redo Steps 4–5. |
| Backend logs `Caused by: org.postgresql.util.PSQLException: Connection refused` | Postgres not healthy yet; backend started before it was ready | Should not happen — `depends_on: postgres: condition: service_healthy` gates this. If it does, `docker compose logs postgres` for the real cause. |
| Backend exits with `relation "trades" does not exist` | Liquibase didn't run | `docker compose logs backend \| grep -i liquibase`. Classic cause: missing `classpath:` prefix on `spring.liquibase.change-log` (already correct in this trainer copy). |
| `401 Unauthorized` on every API call (except `/auth/login`) | JWT missing / expired / signed with a different secret | Re-login. Check token isn't past `JWT_EXP_MIN` minutes (default 60). |
| `403 Forbidden` on POST /v1/trades | Token belongs to a `VIEWER` | Log in as `trader@db.com` or `admin@db.com`. |
| Kafka warnings flood the log in dev mode | No broker reachable | Move to the full Docker stack, or `docker compose up -d zookeeper kafka` alongside dev. |
| `docker compose pull` finishes but `up` shows old code | Image tag pinned to an old SHA in `.env` | Update `.env` to the new SHA → `pull && up -d`. |
| Grafana panels say "No data" | Time range too narrow | Top-right time picker → "Last 15 minutes". Also confirm `/api/actuator/prometheus` returns data and Prometheus target is UP. |
| Frontend can't reach `/api` | Vite proxy mis-pointed, OR docker `frontend` nginx config wrong | Dev: confirm backend on 8080. Docker: `docker compose logs frontend` for proxy errors. |
| `no matching manifest for linux/arm64` | Apple Silicon / arm64 pulling amd64 image | `export DOCKER_DEFAULT_PLATFORM=linux/amd64` (see Platform notes). |
| Login form pre-filled credentials don't work | DB wiped without re-running Liquibase, OR new BCrypt mismatch | `docker compose down -v && docker compose up -d` rebuilds the seed. |
| `org.apache.kafka.common.errors.UnknownTopicOrPartitionException: trade-events-dlq` | DLQ topic not pre-declared and you triggered an error path | Already declared by `KafkaTopicsConfig.tradeEventsDlq()`. If you see this, check the bean is loaded (not behind a `@Profile` filter). |

---

## End-to-end verification — Kafka pipeline + Grafana dashboards

Once `docker compose up -d` shows all 7 services healthy, run this
checklist to confirm the entire pipeline is wired correctly:

```
React UI → Spring Boot → DB commit → Kafka producer → trade-events topic
                                                          │
                                          ┌───────────────┼────────────────┐
                                          ▼               ▼                ▼
                                   recon-service     audit-service   alert-service
                                   (schedule recon)  (persist row)   (logs only)
                                                                          │
                                                                          ▼
                                                                    audit_log row
                                                                          │
                                                                    Grafana panels
                                                                    + Prom metrics
```

Takes ~5 min. Do it once after first boot, then again as a smoke test
before any demo.

### Step 1 — Open everything in tabs

| URL | What you're watching |
|---|---|
| <http://localhost:5173>                          | React UI (where you'll post a trade) |
| <http://localhost:8080/api/swagger-ui.html>       | Swagger (or POST via curl instead) |
| <http://localhost:9000>                          | Kafdrop (needs `--profile debug`) |
| <http://localhost:3000>                          | Grafana (dashboards) |
| <http://localhost:9090/targets>                  | Prometheus targets — `reconx-backend` should be `UP` |

### Step 2 — Pin the Grafana dashboard open

In Grafana (`admin` / `admin`):

1. **Dashboards** → **ReconX** folder → open **ReconX Overview**.
2. Top-right time picker → **Last 15 minutes** + refresh interval **5s**.

What to watch:

| Panel | Metric |
|---|---|
| API request rate by endpoint (TICKET-ADV087) | `sum(rate(http_server_requests_seconds_count[1m])) by (uri)` |
| API P95 latency (TICKET-ADV088) | `histogram_quantile(0.95, …)` |
| Open recon breaks (TICKET-ADV091) | `recon_break_count` gauge |
| Trades created / sec (TICKET-ADV089) | `rate(trade_created_total[1m])` |
| Reconciliation duration histogram (TICKET-ADV090) | `reconciliation_duration_seconds_bucket` |

### Step 3 — Get a JWT and post a trade

> **curl** (use the same TOKEN throughout the test):

```bash
TOKEN=$(curl -s http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"trader@db.com","password":"trader123"}' | jq -r .token)

curl -s -X POST http://localhost:8080/api/v1/trades \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "tradeRef":       "EQU-20260603-9001",
    "instrumentId":   1,
    "counterpartyId": 1,
    "assetClass":     "EQUITY",
    "side":           "BUY",
    "quantity":       1000,
    "price":          125.50,
    "tradeDate":      "2026-06-03"
  }' | jq
```

Expect **201 Created** with a `Location` header pointing at the new trade.

### Step 4 — Verify each piece of the pipeline fired

Within ~3 seconds:

**1. Kafdrop — producer fired** (if `--profile debug`):
- <http://localhost:9000> → **Topics** → click **trade-events** → **View Messages**.
- Latest message: key = `EQU-20260603-9001`, value contains `"eventType":"TRADE_CREATED"`.

**2. Backend logs — both Kafka consumer groups fired:**

```bash
docker compose logs --tail 80 backend | grep -E "Recon-trigger received|Audit row persisted"
```

Expect two lines per trade:
```
... Recon-trigger received eventId=… ref=EQU-20260603-9001 type=TRADE_CREATED  ← recon-service group
... Audit row persisted for eventId=…                                          ← audit-service group
```

If only one appears: the consumer groups collide. Check each
`@KafkaListener(groupId = "...")` — `recon-service` and `audit-service`
must be **distinct** (otherwise only one consumer in the group gets the
message).

**3. Kafdrop — both consumer groups visible:**
Kafdrop → **Consumers** (left nav). Two groups: `recon-service` and
`audit-service`. Each `Lag` returns to `0` within a second.

**4. Audit row landed in Postgres:**

```bash
docker compose exec postgres psql -U reconx_user -d reconx -c \
  "SELECT id, trade_ref, event_type, event_timestamp, actor
   FROM audit_log ORDER BY id DESC LIMIT 3;"
```

Top row: a fresh `audit_log` row with `trade_ref=EQU-20260603-9001`,
`event_type=TRADE_CREATED`, `event_timestamp = now`.

### Step 5 — Watch Grafana light up

**ReconX Overview** dashboard:

| Panel | Expected change after the POST |
|---|---|
| API request rate by endpoint | Brief spike on `/api/v1/trades` URI |
| API P95 latency | Reading appears on `/api/v1/trades` (was blank if no traffic) |
| Trades created / sec | Visible blip |
| Reconciliation duration histogram | Visible bucket increment if recon fired |

If panels stay flat: hit the time picker → **Refresh**. Auto-refresh
sometimes misses the first data point.

### Step 6 — Stress test (optional but satisfying)

Throw 50 trades and watch Grafana react:

```bash
for i in $(seq 100 149); do
  curl -s -X POST http://localhost:8080/api/v1/trades \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"tradeRef\":       \"EQU-20260603-9$i\",
      \"instrumentId\":   1,
      \"counterpartyId\": 1,
      \"assetClass\":     \"EQUITY\",
      \"side\":           \"BUY\",
      \"quantity\":       100,
      \"price\":          250.0,
      \"tradeDate\":      \"2026-06-03\"
    }" > /dev/null
done
echo "Sent 50 trades"
```

What you'll see:
- **Grafana — API request rate** spikes to ~50 req/s for one bucket, then back to 0.
- **Trades created / sec** counter advances.
- **Kafka consumer lag** spikes briefly, returns to 0 within ~3 s.
- **Postgres `audit_log`**: 50 new rows.
- **Prometheus** `trade_created_total` counter (raw) advances by 50.

### Step 7 — Test the dead-letter queue (optional)

Send a malformed payload directly to `trade-events` and watch it land in
`trade-events-dlq` after 3 retries:

```bash
docker compose exec -T kafka kafka-console-producer \
  --bootstrap-server kafka:29092 --topic trade-events <<< 'this-is-not-json'
```

Wait ~7 seconds (3 retries × exponential back-off starting at 1s), then in Kafdrop:

- **trade-events-dlq** topic → 1 new message containing the raw bytes.
- **Backend logs** show the `DeserializationException` and the DLQ publish.
- **Prometheus alert `KafkaDlqGrowing`** fires after ~1 minute (configured
  in `monitoring/prometheus/alerts.yml`).

### What "all green" looks like

The full verification is healthy when all of these are true:

- [ ] All 7 (or 8 with kafdrop) containers `Up (healthy)` in `docker compose ps`.
- [ ] Prometheus `reconx-backend` target is `UP`.
- [ ] `POST /api/auth/login` returns a JWT.
- [ ] `POST /api/v1/trades` returns 201 with a `Location` header.
- [ ] The `EQU-20260603-9001` message appears in Kafdrop within 1 second.
- [ ] Both consumer-group log lines appear in backend logs.
- [ ] A matching `audit_log` row exists in Postgres.
- [ ] Grafana request-rate + trades-created panels advance.
- [ ] DLQ topic stays empty unless you trigger Step 7.

If any of these fail, the Step 4–5 callouts above point at the most likely
cause. Otherwise — the platform is wired and healthy.

---

## Tearing down between demos

For the cleanest rehearsal:

```bash
docker compose down -v        # wipe DB + Grafana state
docker compose pull           # take any new CI build
docker compose up -d          # boot, ~75 s
```

Run the §"End-to-end verification" smoke test (Steps 3-5) once after boot
and once again 60 seconds before the demo.

---

## Multi-arch builds (permanent fix for Apple Silicon / arm64)

The CI workflow shipped in this trainer copy builds amd64 only. To make
it build BOTH amd64 and arm64 — so any laptop pulls the native variant —
make two changes in `.github/workflows/ci.yml` inside the `docker-and-push`
job:

1. **Add QEMU setup** after `actions/checkout@v4`:

   ```yaml
   - name: Set up QEMU
     uses: docker/setup-qemu-action@v3
   ```

2. **Add `platforms` to BOTH `docker/build-push-action` steps:**

   ```yaml
   - name: Build + push backend
     uses: docker/build-push-action@v6
     with:
       context: ./backend
       push: true
       platforms: linux/amd64,linux/arm64        # ← add this
       tags: |
         ghcr.io/${{ github.repository_owner }}/reconx-backend:latest
         ghcr.io/${{ github.repository_owner }}/reconx-backend:${{ github.sha }}
   ```

CI build time goes from ~4 min → ~8 min (multi-arch builds in series),
but every laptop pulls native bytecode. No `DOCKER_DEFAULT_PLATFORM`, no
Rosetta tax, no per-shell setup.

---

## Mock reconciliation — full-stack flow walkthrough

This is the **demo-day script**. It walks one trade end-to-end through every
service so you can show a non-technical audience that the wiring is real.
Takes ~10 minutes. Run it once before any live demo.

### The flow you'll watch

```
   ┌────────────┐  HTTPS  ┌─────────────────┐  KafkaTemplate  ┌──────────────┐
   │ React UI   │ ──────► │ Spring Boot REST│ ──────────────► │ trade-events │
   │ Add Trade  │         │ TradeController │                 │   (3 part.)  │
   │  (frontend)│         │  + service      │                 └──────┬───────┘
   └────┬───────┘         └────────┬────────┘                        │
        │                          │                                 │
        │                          ▼                                 ▼
        │                  trades table              ┌──────────────────┐
        │                  audit_log table           │  @KafkaListener  │
        │                  recon_breaks table        │  recon-service   │
        │                          │                 │  audit-service   │
        │                          │                 │  alert-service   │
        │                          │                 └────────┬─────────┘
        │                          ▼                          ▼
        │              /actuator/prometheus           audit_log row written
        │                          │
        │                          ▼
        │                  ┌──────────────┐    PromQL    ┌─────────┐
        │                  │  Prometheus  │  ──────────► │ Grafana │
        │                  │   (scrape)   │              │ panels  │
        │                  └──────────────┘              └─────────┘
        │                                                     │
        │                                                     │ (you watch
        ▼                                                     │  it tick)
   refresh /api/v1/trades — see the new row in the table     │
```

> **Honest disclosure for trainers:** in this trainer copy a few pieces are
> intentionally mocked so the demo stays small. **`POST /api/v1/recon/run`
> returns a job ID but does not actually invoke the engine** (that worker
> wiring is a Day-9 student exercise). **`recon_breaks` rows are not
> auto-created** from trades — you'll inject one in Step 10 so you can show
> the `recon_break_count` gauge moving in Grafana. The SSE endpoint the
> `<Dashboard />` page subscribes to (`/api/v1/trades/stream`) is also a
> student exercise — the live-feed card will stay quiet; demo from the
> **Trades** page instead, which uses the regular `GET /api/v1/trades`
> endpoint.

---

### Pre-flight — confirm the stack is healthy

```bash
docker compose ps
```

All 7 (or 8 with Kafdrop) containers must be `Up (healthy)`. If `backend`
is `Restarting`, run §"Common issues" before continuing.

```bash
curl -fsS http://localhost:8080/api/actuator/health
# expect: {"status":"UP",...}
```

---

### Step 1 — Lay out the tabs

Open these in browser tabs (Cmd-T five times, paste each):

| # | URL                                                | What you're watching |
|---|----------------------------------------------------|-----------------------|
| 1 | <http://localhost:5173>                            | React UI (login + add trade + list) |
| 2 | <http://localhost:9000>                            | Kafdrop — topic + consumer-group view (needs `--profile debug`) |
| 3 | <http://localhost:3000>                            | Grafana → **Dashboards** → **ReconX** → **ReconX Overview** |
| 4 | <http://localhost:9090/targets>                    | Prometheus — confirm `reconx-backend` is **UP** |
| 5 | <http://localhost:8080/api/swagger-ui.html>        | Swagger fallback if the UI misbehaves |

In Grafana: time picker top-right → **Last 15 minutes**, auto-refresh **5s**.

Leave a terminal open too:

```bash
docker compose logs -f backend | grep -E "Recon-trigger received|Audit row persisted|TradeEvent"
```

This is your "events fired" tap.

---

### Step 2 — Authenticate (frontend)

In tab 1 (React UI) → click **Sign in**. Form is pre-filled:

- email: `trader@db.com`
- password: `trader123`

Click **Sign in**. You land on the Dashboard. The token is stored in
`sessionStorage` and every API call now sends `Authorization: Bearer …`.

> **What happened under the hood:** `POST /api/auth/login` →
> `AuthController` → BCrypt-verify password → `JwtTokenProvider.generate(...)`
> → HS256-signed token returned → React `AuthContext.login()` stashes it.

---

### Step 3 — Post 5 trades through the UI

Click **Add trade** in the nav. Use these five payloads in turn — they vary
across asset classes and counterparties so the dashboard panels group
interestingly:

| tradeRef            | instrId | cpId | assetClass | side | qty   | price | tradeDate  |
|---------------------|---------|------|------------|------|-------|-------|------------|
| `EQU-20260603-1001` | 1       | 1    | EQUITY     | BUY  | 1000  | 125.5 | 2026-06-03 |
| `EQU-20260603-1002` | 4       | 2    | EQUITY     | SELL | 500   | 178.2 | 2026-06-03 |
| `EQU-20260603-1003` | 5       | 3    | EQUITY     | BUY  | 250   | 412.8 | 2026-06-03 |
| `BND-20260603-1004` | 10      | 4    | BOND       | BUY  | 100   | 99.75 | 2026-06-03 |
| `FX-20260603-1005`  | 13      | 5    | FX         | SELL | 10000 | 1.085 | 2026-06-03 |

Each submit should clear the form. Watch the **Events fired** terminal tap
for two log lines per submit:

```
... Recon-trigger received eventId=… ref=EQU-20260603-1001 type=TRADE_CREATED
... Audit row persisted for eventId=…
```

> **Prefer curl for repeatability?** Run this once you have a TOKEN:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"trader@db.com","password":"trader123"}' \
  | sed 's/.*"token":"\([^"]*\)".*/\1/')

for n in 1001 1002 1003 1004 1005; do
  curl -s -X POST http://localhost:8080/api/v1/trades \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"tradeRef\":\"EQU-20260603-$n\",
      \"instrumentId\":1,\"counterpartyId\":1,
      \"assetClass\":\"EQUITY\",\"side\":\"BUY\",
      \"quantity\":1000,\"price\":125.50,
      \"tradeDate\":\"2026-06-03\"
    }" | head -c 80
  echo
done
```

---

### Step 4 — Watch the events hit Kafka (tab 2 — Kafdrop)

Kafdrop → **Topics** → click **trade-events** → **View Messages** →
partition `0`, offset `0`, count `10` → **View**.

You should see your 5 messages with:

- **Key**: the tradeRef (`EQU-20260603-1001` etc.) — this is how we
  guarantee per-trade ordering: all events for one trade hash to one
  partition.
- **Value** (JSON): `{"eventId":"…","tradeRef":"…","eventType":"TRADE_CREATED","timestamp":"…",…}`

Kafdrop → **Consumers** (left nav). You should see **two** consumer groups:

- `recon-service` — `Lag: 0`
- `audit-service` — `Lag: 0`

(`alert-service` only consumes from `system-alerts`, so it stays at lag 0
without ever advancing.)

If a group is missing → two `@KafkaListener` methods share the same
`groupId`, so only one consumed each message. Check the source.

---

### Step 5 — Verify audit rows landed (Postgres)

```bash
docker compose exec postgres psql -U reconx_user -d reconx -c \
  "SELECT id, trade_ref, event_type, event_timestamp, actor
     FROM audit_log
     ORDER BY id DESC
     LIMIT 5;"
```

Top 5 rows should match the 5 tradeRefs you just posted, each with
`event_type = TRADE_CREATED` and a recent `event_timestamp`.

---

### Step 6 — Watch the raw Prometheus counters

```bash
curl -s http://localhost:8080/api/actuator/prometheus \
  | grep -E "^(trade_created_total|trade_value_total_count|recon_break_count|http_server_requests_seconds_count.*v1/trades)" \
  | head -20
```

You should see:

- `trade_created_total` advanced by **5**
- `trade_value_total_count` advanced by **5**, with `trade_value_total_sum`
  reflecting the dollar value of those trades
- `recon_break_count` still at **0** (we'll change this in Step 10)
- `http_server_requests_seconds_count{uri="/api/v1/trades",method="POST",status="201",…}` advanced by 5

---

### Step 7 — Watch Grafana panels animate (tab 3)

On the **ReconX Overview** dashboard, the panels should reflect Step 6
within ~10 s (Prometheus scrape interval):

| Panel | Expected change |
|---|---|
| API request rate by endpoint | A visible spike on the `/api/v1/trades` line |
| API P95 latency              | First data point appears (was blank if no traffic before) |
| Trades created / sec         | A blip — short rate spike then back to 0 |
| Reconciliation duration histogram | Quiet for now — no recon actually ran yet |
| Open recon breaks            | Still 0 |

If panels stay flat: time picker → **Refresh**, or check that
`reconx-backend` Prometheus target is **UP** (tab 4).

---

### Step 8 — Trigger a (mock) recon run

`POST /api/v1/recon/run` accepts a date window and returns a job ID. In
the trainer copy this **doesn't actually execute the engine** — it shows
how the front-of-house contract looks. The worker that consumes the job
is a Day-9 student exercise.

```bash
curl -s -X POST http://localhost:8080/api/v1/recon/run \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"from":"2026-06-01","to":"2026-06-30"}'
```

Expect: `202 Accepted` with `{"jobId":"<uuid>","status":"QUEUED"}`. Stash
the jobId for the next step.

---

### Step 9 — Query recon results

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/recon/jobs/<jobId>/results"
```

Returns `[]` — no breaks exist yet. That changes in Step 10.

---

### Step 10 — Inject a mock recon_break (because the engine is mocked)

Real production would have the recon engine create rows here. For the
demo, hand-create one so we can show the `recon_break_count` gauge move
in Grafana:

```bash
docker compose exec postgres psql -U reconx_user -d reconx -c \
  "INSERT INTO recon_breaks (trade_id, discrepancy_type, status, detected_at)
     SELECT id, 'PRICE_MISMATCH', 'OPEN', NOW()
       FROM trades
      WHERE trade_ref = 'EQU-20260603-1001';"
```

Within ~10 s, in Grafana:

- **Open recon breaks** stat panel ticks from **0 → 1**.
- The Prometheus alert rule `TooManyReconBreaks` would fire at >50, so
  this single break doesn't page anyone.

Confirm the metric source:

```bash
curl -s http://localhost:8080/api/actuator/prometheus | grep recon_break_count
# recon_break_count 1.0
```

The gauge is wired in `TradeMetrics.java` and reads from
`ReconBreakRepository.countByStatus("OPEN")` on every Prometheus scrape.

---

### Step 11 — Resolve the break (round-trips the lifecycle)

Find the break's id (use the value from psql or the `/results` endpoint):

```bash
docker compose exec postgres psql -U reconx_user -d reconx -tA -c \
  "SELECT id FROM recon_breaks WHERE status='OPEN' ORDER BY id DESC LIMIT 1;"
# e.g. 1
```

Resolve it via the REST API:

```bash
curl -s -X PUT http://localhost:8080/api/v1/recon/results/1/resolve \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"note":"price was within manual-override tolerance"}'
```

Expect: 200 with the break now showing `status: "RESOLVED"`,
`resolved_at` set, and your note attached.

Within ~10 s, in Grafana: **Open recon breaks** drops back to **0**.

---

### Step 12 — Refresh the React Trades page (tab 1)

Click **Trades** in the nav. The list shows your 5 newly-posted trades
(most recent first, because the controller default sort is
`tradeDate DESC`). Try the status filter — type `PENDING` to see only
fresh trades.

Open a trade row's tradeRef in the URL bar to fetch its audit history
(if you wire a click handler — this is a Day-8 stretch exercise; for the
trainer copy, hit the endpoint directly):

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/audit/trades/EQU-20260603-1001" | head -c 300
```

You'll see the `TRADE_CREATED` event row that the AuditEventConsumer
persisted in Step 4.

---

### "All green" demo-day checklist

The mock-recon walkthrough is healthy when every box is ticked:

- [ ] React login returns a JWT and lands on Dashboard
- [ ] Posting 5 trades via the UI returns 201 each
- [ ] Backend log tap shows 2 lines per trade (`Recon-trigger received` + `Audit row persisted`)
- [ ] Kafdrop's `trade-events` topic has 5 new messages keyed by tradeRef
- [ ] Both `recon-service` and `audit-service` consumer groups show lag = 0
- [ ] `audit_log` table has 5 matching rows
- [ ] Prometheus `trade_created_total` advanced by 5
- [ ] Grafana request-rate, latency, trades-created panels show the spike
- [ ] `POST /api/v1/recon/run` returns 202 with a job ID
- [ ] Injecting a `recon_breaks` row moves the gauge 0 → 1 in Grafana
- [ ] `PUT /api/v1/recon/results/{id}/resolve` moves the gauge 1 → 0
- [ ] React Trades page lists all 5 new trades, status filter works
- [ ] `GET /api/v1/audit/trades/EQU-20260603-1001` returns the audit row

If everything passes you have a 10-minute live demo loop that touches
every layer of the stack: React → REST → JPA → Kafka producer → topic
→ two consumer groups → Postgres writes → Micrometer metrics →
Prometheus scrape → Grafana visualisation → back to the UI.

---

### Resetting between runs

Each demo run leaves rows behind. For a clean rehearsal:

```bash
docker compose exec postgres psql -U reconx_user -d reconx -c \
  "DELETE FROM audit_log;
   DELETE FROM recon_breaks;
   DELETE FROM trades WHERE trade_ref LIKE '%-2026%';"
```

Or nuke + reseed the whole DB:

```bash
docker compose down -v       # drops postgres_data volume
docker compose up -d         # Liquibase reseeds 10 counterparties, 15 instruments, 4 users
```

---

## Where to go from here

- New to the case study? Read [TrainersGuide/day0/README.md](./README.md).
- Prepping Day 1? [TrainersGuide/day1/README.md](../day1/README.md).
- Demo-day deploy walkthrough in depth: [TrainersGuide/day10/README.md](../day10/README.md).
- Top-level repo map + stack overview: [../../README.md](../../README.md).
