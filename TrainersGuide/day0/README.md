# TrainersGuide — Day 0: Welcome & Onboarding (ReconX, Advanced Track)

> **Student-facing equivalent:** [../../student-guides/day0/README.md](../../student-guides/day0/README.md)
> Read that **first** — it's the deck the grads are following.
> Top-level architecture + credentials lives in [../../README.md](../../README.md) — keep it open as you teach.
> **Booting the whole project (step-by-step):** [projectrun.md](./projectrun.md) — share with any grad whose setup didn't take.

ReconX is the Advanced Track. Grads arrive expecting it to be harder than the
Intermediate (TradeFlow) track — and it is: Java 25 sealed classes, JWT auth,
3 Kafka topics + DLQs, Testcontainers, two PR reviewers not one. Day 0 sets the tone. If you walk out with every grad able to (a)
draw the architecture, (b) say "trade break" in plain English, and (c) push
a green PR, Day 1 will run itself.

---

## Day at a glance

| #    | What | Owner |
|------|------|-------|
| Pre-arrival | Verify every grad submitted §7 setup-checklist screenshot | Lead trainer |
| 1 | Welcome + team-formation icebreaker | Lead trainer |
| 2 | Case-study domain walkthrough (§1 + §3 of student Day 0) | Lead trainer |
| 3 | Coffee + setup-check 1:1s for anyone whose `docker compose up -d postgres kafka` didn't work | All trainers |
| 4 | Architecture deep-dive (§4) + 10-day flow (§5) | Senior trainer |
| 5 | Lunch | — |
| 6 | GitHub & GitFlow demo + team norms (§6) | Lead trainer |
| 7 | Tooling roundtable: IntelliJ, VS Code, Postman, Docker Desktop (4 stations, 20–25 min each, grads rotate) | Each trainer takes one |
| 8 | Q&A + Day-1 preview | Lead trainer |
| 9 | End-of-day debrief (see prompts below) | Lead trainer |

**There are no code exercises today.** The day's whole job is making sure 100%
of grads can boot the starter, push to a feature branch, and explain what
"trade reconciliation" means out loud. Hands-on exercises start tomorrow (Day 1 · TICKET-ADV001 onward).

---

## Pre-day instructor prep

The evening before Day 0:

- [ ] Confirm every grad's §7 setup checklist has been submitted (screenshot
      of `java -version` showing 25.x, `docker compose ps`, and `git --version`).
      Anyone who hasn't = first 1:1 of the morning. **JDK 21 vs 25 mismatch is
      the single biggest Day-1 blocker on this track** — see "things that have
      gone wrong before."
- [ ] On a clean clone, run the §7 sanity commands yourself end-to-end:
      `docker compose up -d postgres kafka zookeeper`, then
      `cd backend && ./mvnw -DskipTests package`, then
      `cd ../frontend && npm ci`. If anything is flaky on your laptop, it
      will be flaky on theirs.
- [ ] Print the §3 terminology cheat sheet, 1-per-grad. People reference
      paper more than they reference scrolling. Mark `ISIN`, `LEI`,
      `discrepancy type`, and `DLQ` — those four come back every day.
- [ ] Have the architecture diagram from the top-level [README](../../README.md)
      ready **both** as a slide **and** as a whiteboard sketch you can draw
      live. Live > slide for "how it all connects."
- [ ] Decide team composition before they arrive — **3–4 grads per team**,
      mixed strengths, mixed degree backgrounds, mixed OS where possible.
      Don't let them self-form (cliques + skill silos).
- [ ] Confirm GitHub org access — each grad should already be invited to
      the team's repo with `write` permission. Branch protection on `main`
      and `develop` should be on (require 2 reviewers — Advanced convention).
- [ ] Glance at the Day 1 trainer README so you can give an accurate preview
      at 16:00.

---

## Conversation milestones

Day 0 has no formal exercises. Instead, every grad should hit these three
milestones by end of day. Tick them off in your head as the day runs.

### Milestone 1 — They can name the business problem in plain English

**What "passed" looks like:** Each grad, asked at random, can answer
*"what's a trade break?"* in their own words without the cheat sheet.
30 seconds, no jargon. Bonus: they can name a *type* of break (price,
quantity, missing trade).

**Common student confusion:**
- Conflating "trade" with "settlement". A trade is the agreement;
  settlement is the actual exchange of cash + ownership. Both happen,
  on different days (`T` and `T+2`).
- Assuming reconciliation is just "comparing two CSVs." It is — until you
  ask what "match" means when one side has `quantity=1000` and the other
  has `1000.0000`, or one has `SAP` and the other has `SAP.DE`.

**Unblocking ladder:**
1. **Nudge:** "Two systems wrote down the same trade. Why might they
   disagree, beyond someone making a typo?"
2. **Hint:** Run the live example from §1 (1,000 SAP shares — bank says
   €120.50, broker says €120.55 → that's a `PRICE_MISMATCH` break).
3. **Reveal:** Walk the table in §3 (Trading & operations) once, slowly,
   in your own words, with a real example for each row.

### Milestone 2 — They can sketch the architecture from memory

**What "passed" looks like:** A grad at the whiteboard can draw the
top-level diagram with boxes roughly in place — React on top, Spring Boot
in the middle, Postgres + Kafka below, Prometheus/Grafana on the side.
Don't grade arrows; grade boxes. Bonus: they can name **3 of the 4 Kafka
topics** (`trade-events`, `recon-results`, `system-alerts`, plus DLQs).

```
React (Vite) ──HTTPS+JWT──▶ Spring Boot (Java 25) ──JDBC──▶ Postgres
       │ SSE                       │  Kafka                  │  (Liquibase)
       │                           ▼                         ▼
       └─────────────── Kafka (trade-events, recon-results,  audit_log,
                              system-alerts + DLQs)          mat. views
                                   │
                                   ▼
                       Recon / Audit / Alert consumers
                                   │
                       /actuator/prometheus → Prometheus → Grafana
```

**Common student confusion:**
- *"Where does Liquibase fit?"* — it's not on the runtime diagram because
  it runs at *startup*, not at request time. Worth being explicit.
- *"Why is Kafka separate from the consumers?"* — the consumers live
  **inside the same `recon-service` JVM** in this project (we don't split
  into microservices). They're drawn separately to show the event flow,
  not the deployment topology.
- *"Where's the auth server?"* — there isn't one. JWT is issued by
  `recon-service` itself via `POST /api/auth/login`. No Keycloak, no Auth0.

**Unblocking ladder:**
1. **Nudge:** "If I `POST /api/trades`, which boxes get touched in order?
   Trace the arrow."
2. **Hint:** Draw the happy path yourself: `React → Spring Boot → DB
   commit → KafkaTemplate.send(trade-events) → ReconConsumer → DB write →
   /actuator/prometheus → Grafana panel ticks`.
3. **Reveal:** Re-draw the full diagram while narrating, then erase
   half and ask a different grad to fill it back in.

### Milestone 3 — Every team has a green PR before EOD

**What "passed" looks like:** By 16:30 every team has opened a
`feature/day0-team-setup` PR into `develop`, got **two reviews** (Advanced
Track requires two — flag this loudly), and merged. Doesn't matter what's
in it — a one-line README edit listing team members is fine. **The goal is
to prove the GitFlow loop + the 2-reviewer rule works** before hands-on
exercises start tomorrow.

**Common student blockers:**
- Branch protection blocks their first push to `main` or `develop`.
  **That's the point** — show them the error, then walk through the PR flow.
- They forget `-u` on the first push (`git push -u origin feature/day0-...`).
- They open the PR against `main` instead of `develop`.
- One grad has a typo in the org name and pushes to a personal fork.
- They get **one** review and try to merge — Advanced needs **two**. The
  merge button stays grey; they think it's broken.

**Unblocking ladder:**
1. **Nudge:** "Read the error message out loud."
2. **Hint:** "What does the PR page say under 'Reviewers'? How many
   green checks does the merge rule require?"
3. **Reveal:** Live demo from your laptop — slowly, narrate every click.

---

## Talking points for the architecture deep-dive

Use these as the spine of the 90 min session. Don't read them
verbatim — riff. The grads who care about the *why* will ask follow-ups.

- **Why PostgreSQL, not Mongo / Cassandra:** trades have a strong
  relational shape (FKs, joins, aggregations across counterparty +
  instrument + asset class). Document stores would fight the domain.
  Postgres 16 also gives us JSONB for the messy bits (raw counterparty
  payloads in `audit_log`) so we get both worlds.
- **Why Liquibase, not Flyway or hand-rolled SQL:** XML/YAML changelogs
  are easier to *review in a PR* than raw SQL diffs, and Liquibase
  validates them in CI on Day 10. Both Liquibase and Flyway are valid in
  production; we pick one to teach the *concept*.
- **Why Spring Boot 3, not plain Spring or Quarkus:** auto-config + the
  ecosystem (Data JPA, Security, Kafka, Actuator, Micrometer) means
  students spend their 10 days on domain logic, not on framework
  bootstrapping. Spring Boot 3 also forces Jakarta EE namespaces, which
  matches what they'll see at the bank.
- **Why Java 25, not 21:** four reasons, all used in this codebase:
  (1) **sealed classes** model the `Trade` hierarchy (`EquityTrade`,
  `FXTrade`, `BondTrade`) with exhaustive `switch` patterns — Day 2;
  (2) **records** kill DTO boilerplate — used everywhere from Day 4 on;
  (3) **virtual threads** let our Kafka consumers and SSE endpoints scale
  without a thread-pool config dance — Day 9; (4) Java 25 is the current
  LTS we want every cohort on the same modern baseline. If a grad asks
  "can I use 21?" the answer is *no* — pin `JAVA_HOME` to 25 before Day 1.
- **Why JWT, not session cookies:** stateless. The backend can be
  load-balanced or restarted without losing user sessions, and the same
  token works for both the React SPA and Postman/curl. We do still use
  HttpOnly cookies for the **refresh token** (7-day TTL) — best of both.
- **Why Kafka, not an HTTP webhook:** decoupling + replay. The
  `ReconConsumer` doesn't need to know who fired the event, and the
  producer doesn't wait for it. On Day 9 we use Kafka's offset semantics
  to *replay* events to rebuild state — webhooks can't do that.
- **Why 3 topics, not 1:** separation of concerns + independent scaling.
  `trade-events` is high-throughput (every trade), `recon-results` is
  lower-throughput (one per matched batch), `system-alerts` is rare but
  high-priority. Different consumer groups, different retention. Plus,
  ACLs in real Kafka are per-topic.
- **Why DLQs:** poison messages happen — a malformed JSON, a deleted
  counterparty FK, a schema mismatch. Without a DLQ, the consumer
  retries forever and blocks the partition. With one, bad messages get
  parked in `*.DLQ` for an Ops human to inspect. Day 9 covers this.
- **Why Prometheus + Grafana, not Datadog / New Relic / ELK:** standard,
  open-source, free, runs locally in Docker. Don't get drawn into a
  vendor debate — out of scope for the programme.

---

## Tooling roundtable — demo notes

Four stations, 20–25 min each, grads rotate. Each trainer takes one. Aim
for "I can do the thing now," not "look how clever this IDE is."

### IntelliJ IDEA Ultimate (backend)

- Open `backend/pom.xml` → "Open as project" → wait for Maven import.
- Show **SDK = Temurin 25** under `File → Project Structure`. This is
  the #1 thing to verify per grad.
- Show the green run gutter on `ReconxApplication.main`. Boot it.
- Show **Database tool window** → connect to `localhost:5432`,
  `db=reconx`, `user/pass=reconx/reconx`. Browse `audit_log`.
- Show **HTTP Client** (`.http` scratch file): `POST /api/auth/login`
  → copy JWT into next request's `Authorization` header.
- Show **Run Configurations** → add `-Dspring-boot.run.profiles=dev`.
- Mention: Ultimate is free for grads via JetBrains' student licence.

### VS Code (frontend)

- Open `frontend/` as a workspace.
- Recommended extensions: ESLint, Prettier, ES7+ React/Redux snippets,
  GitLens, Tailwind CSS IntelliSense (we use it lightly on Day 8).
- Show **integrated terminal** → `npm run dev` → click the
  `localhost:5173` link.
- Show **Source Control panel** — stage / commit / push from inside VS
  Code (so they don't have to context-switch to a terminal).
- Show **breakpoint debugging** in `src/components/DataTable.jsx` via
  the JS debug terminal.

### Postman (or Bruno / Insomnia)

- Import the collection from `backend/postman/ReconX.postman_collection.json`
  (ship this if it doesn't exist yet — they'll need it on Day 5).
- Walk: `POST /api/auth/login` → set `{{jwt}}` collection variable from
  the response → `GET /api/trades` → `POST /api/trades` → `GET
  /api/recon/runs/{id}`.
- Show **environments** — `local` vs `docker` vs `demo-laptop` — so
  switching base URLs is one click.
- Mention: Bruno is the open-source alternative if a grad doesn't want
  the Postman cloud account.

### Docker Desktop

- Show **resource allocation** — must be **≥ 6 GB RAM, 4 CPUs**.
  Default 2 GB will OOM Kafka. This is the #2 Day-1 blocker.
- Run `docker compose up -d postgres kafka zookeeper` from the repo
  root. Show the containers turning green in the GUI.
- Show **container logs** via the GUI — clicking `kafka` and reading
  the log is faster than `docker logs` for most grads.
- Show **volumes** — explain that `docker compose down -v` wipes the
  Postgres data; `docker compose down` keeps it.
- Demo `docker compose down && docker compose up -d` to prove the
  full lifecycle.

---

## End-of-day debrief prompts

Ask the room at **16:45** (15 min before the day ends, while energy's
still there):

1. *"What's one thing you'd want to know more about before Day 1
   starts?"* (Note answers — feed them into the Day 1 morning warm-up.)
2. *"Sketch the architecture diagram from memory on paper. Compare with
   your pair."* (90 seconds. Walk the room. Anyone whose sketch is
   missing Kafka or Prometheus = Day 1 1:1.)
3. *"Pair up. One person explains 'reconciliation' to the other in 60
   seconds. Swap."* (Confirms everyone has the domain story.)

---

<details>
<summary><b>Things that have gone wrong before</b></summary>


- **JDK 21 vs 25 mismatch on Windows laptops.**

  Grad installed JDK 21 for a uni module last year, didn't read the prerequisites, `mvn` picks up 21 from `JAVA_HOME` and the Day-2 code doesn't compile.

  **Fix:** Run `java -version` AND `mvn -v` in front of every grad at the morning 1:1. The second line of `mvn -v` shows which JDK Maven is actually using — it's often *different* from `java -version`.

- **Setup checklist not actually verified.**

  Trainers ticked "looks good" without running commands. Day 1 lost 90 min to Docker config issues across the room.

  **Fix:** Watch each grad type the §7 commands. No screenshots — live.

- **Docker Desktop default 2 GB RAM.**

  Kafka + Postgres + Prometheus + Grafana = OOM. Containers restart-loop.

  **Fix:** Bump to 6 GB **in front of the grad** at the 10:30 1:1.

- **Architecture explained only on slides.**

  Grads forgot it by lunch.

  **Fix:** Whiteboard it live, then ask one grad to re-draw it from memory before lunch. Bonus: ask a different grad after lunch.

- **Cliques in team formation.**

  Two grads from the same uni paired up and shut the other two out.

  **Fix:** Trainer assigns teams; mix uni + degree type. Don't let self-formation happen.

- **Misaligned tooling on a team.**

  One grad on Windows, three on Mac. Trainer didn't notice until WSL path issues broke a Liquibase migration on Day 4.

  **Fix:** Note OS per grad at Day 0; flag any team with mixed OS and pair-test commands.

- **PR rule confusion.**

  A team got one review on their Milestone-3 PR and tried to merge — the button was greyed. They thought GitHub was broken for an hour.

  **Fix:** Call out the **2-reviewer rule** explicitly during the 13:30 GitFlow demo.

- **`localhost:5432` already in use.**

  Grad has a local Postgres install from a previous project. `docker compose up -d postgres` fails silently or the backend connects to the wrong DB.

  **Fix:** `lsof -i :5432` at the morning 1:1; stop the host Postgres or remap the Docker port in `docker-compose.yml`. ---</details> <details> <summary><b>Q&A bank</b></summary>


Common Day-0 questions with model answers. Memorise the first three —
they come up every cohort.

**Q1. "Why ReconX, isn't TradeFlow the same thing?"**
Same business domain (trade reconciliation), different difficulty
target. TradeFlow is the Intermediate Track — Java 25, simpler model,
one Kafka topic, one PR reviewer. ReconX is the
Advanced (Intermediate-Hybrid) Track — Java 25, sealed-class
hierarchy, 3 Kafka topics + DLQs, JWT auth, two PR reviewers. The
shape of the diagram is similar on purpose; the depth of each box is
not.

**Q2. "Do we have to use Java 25?"**
Yes — Java 25 is the LTS baseline for the 2026 cohort and every grad
needs to be on the same JDK. If your `java -version` shows anything
older than 25, install Temurin 25 and update `JAVA_HOME` before Day 1.

**Q3. "Can I use Cursor / Codeium / Continue instead of Copilot?"**
Yes, any AI coding assistant is fine. The AI policy (§9 of the
student README) is tool-agnostic: use it to explain, scaffold,
review, debug — read every line before committing, be ready to
defend it in code review, note its use in the PR description. We'll
sample at random and ask you to explain lines.

**Q4. "What's the AI usage policy in detail?"**
Allowed: explain, scaffold, review, debug. Required: read +
understand every committed line, note AI use in the PR description,
be ready to defend it live. Forbidden: pasting AI output without
reading it. Exercises marked **★ AI-assisted** (Days 1, 3, 7, 9, 10)
are exercises in *prompting and reviewing* — graded on the prompt
trail, not just the code.

**Q5. "What if my team has mixed skill levels?"**
Good — that's by design. Pair the stronger Java grad with the
stronger React grad; rotate pairs daily so knowledge spreads. The
trainer will not re-balance teams after Day 0 unless a team is
visibly stuck. If you're the stronger member, your job is to
*teach*, not to solo the exercises — your grade reflects team output,
not individual.

**Q6. "How are we graded?"**
Three buckets: (1) **team output** — exercises closed, PRs merged,
Day-10 demo quality; (2) **individual contribution** — your PRs,
your code reviews, your stand-up clarity; (3) **technical depth** —
random "explain this line / draw this diagram" checks throughout
the 10 days. Final breakdown is shared in the Day 10 wrap.

**Q7. "Why GitFlow, not trunk-based?"**
Two reasons. (1) GitFlow's `feature/* → develop → main` model maps
cleanly onto the exercise → PR → release narrative we want to
*teach*. (2) Most DB engineering teams still use a GitFlow-ish
variant — you'll see it in your first team rotation. Trunk-based is
fine in production; it's not what we're teaching this fortnight.

**Q8. "Why 2 PR reviewers, not 1?"**
Advanced-track convention. The Intermediate Track uses 1. With 2 you
get a richer review (different angles), and you learn to review *at
speed* — because if you're slow, you become your team's bottleneck.
It also mimics what most DB platform teams require for prod-bound
changes.

**Q9. "What if Docker Desktop won't run on my Mac (M1/M2/M3)?"**
Check three things: (1) Docker Desktop is the Apple Silicon build,
not Intel; (2) you've granted it ≥ 6 GB RAM in
Settings → Resources; (3) you're on macOS 13+. If it still won't
start, fallback is Colima (`brew install colima && colima start
--cpu 4 --memory 6`). All the `docker compose` commands work
identically. Flag it to the trainer at the 10:30 1:1.

**Q10. "What if I'm not a Java person — I came from a Python / JS
background?"**
You'll be uncomfortable for two days, then fine. The Day 2–3
material is deliberately a from-scratch Java refresher (sealed
classes, records, streams, `CompletableFuture`). Pair with a
Java-stronger grad on those days; you'll pay it back on Day 7–8
when the React work starts.

**Q11. "What happens if I don't finish an exercise by EOD?"**
It rolls into the next day's backlog. The 10-day plan has slack —
not every team finishes every exercise. We care more about *quality*
of merged exercises than *count*. Don't merge half-done code to hit a
number — that's the worst possible outcome for the demo.

**Q12. "Can I work on this in the evenings / weekend?"**
Not required, not forbidden. If you do, **don't merge to `develop`
unreviewed** — open the PR, leave it for your team to review next
morning. Surprising your team-mates with merged code overnight is
the fastest way to break trust.

---

</details>

<details>
<summary><b>Hand-off to Day 1</b></summary>


By end of Day 0, every grad should be able to answer "yes" to:

- [ ] I can explain trade reconciliation to a non-technical friend.
- [ ] I've sketched the architecture from memory (React, Spring Boot,
      Postgres, Kafka with at least 2 topics named, Prometheus, Grafana).
- [ ] I've cloned the repo, run `docker compose up -d postgres kafka
      zookeeper`, and seen all three containers as `healthy`.
- [ ] I've run `cd backend && ./mvnw -DskipTests package` and seen
      `BUILD SUCCESS` on Java 25.
- [ ] I've opened my first PR into `develop`, got 2 approvals, merged.
- [ ] I know who's on my team and we've Slack-DM'd each other.
- [ ] I know where the day's exercises live (`student-guides/dayN/`) and
      where the trainer playbook lives (`TrainersGuide/dayN/`).

If any of those are "no" → flag for the Day 1 morning 1:1.

**Next:** [TrainersGuide/day1/](../day1/README.md) — PostgreSQL + Liquibase
deep dive, TICKET-ADV001 – TICKET-ADV017.

</details>

