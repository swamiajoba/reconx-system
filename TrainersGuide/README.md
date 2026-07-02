# TrainersGuide — ReconX Advanced (Intermediate-Hybrid) Track

> Deutsche Bank — TDI 2026 Graduate Technical Training Programme
> **Instructor-facing companion** to the student-facing case study.

This directory is the **trainer's playbook** for the ReconX 10-day case study.
It exists alongside the student guides — not in place of them.

---

## How this repo relates to the student copy

| What | Where |
|---|---|
| Student starter (stubs, TODOs, minimal scaffold) | `../reconx-studentscopy/` (sibling repo grads work in) |
| **Trainer copy (this repo) — same structure, full solutions** | `../reconx-trainerCopy/` (where you are now) |
| Student-facing day-by-day guides | `./student-guides/dayN/README.md` — **identical to the student copy**; do not edit |
| **Trainer-facing day-by-day playbook (this folder)** | `./TrainersGuide/dayN/README.md` |
| Working solution code (this is the answer key) | `./backend/`, `./frontend/`, `./db/`, etc. — **filled in vs. student copy's stubs** |

**Rule of thumb when teaching:** show students the student copy, work from
this trainer copy. Never link the trainer copy to a student team — it's the
answer key.

---

## What you'll find in each `TrainersGuide/dayN/README.md`

Every day's instructor README is structured the same way:

1. **Day at a glance** — AM/PM time-blocked schedule, what students will produce.
2. **Pre-day instructor prep** — what to set up the evening before / morning of.
3. **Module-by-module walkthrough** (Module 1 AM, Module 2 PM, etc.) with:
   - **Common student blockers** — the top 3-5 things students get stuck on.
   - **Unblocking ladder** — what to say first (nudge), second (hint), third (reveal).
   - **Full reference solution** — inline code in `<details>` blocks so you can collapse-and-skim or expand to copy/paste a fix.
   - **Talking points** — the 30-second framing each exercise deserves.
4. **Q&A bank** — the 8-15 questions students ask every cohort, with model answers.
5. **End-of-day debrief prompts** — what to ask the team in the closing sync.
6. **Things that have gone wrong before** — historical pitfalls + war stories.

---

## Day index

| Day | Module | Trainer guide |
|----:|--------|---------------|
| 0   | Welcome & Onboarding                                         | [day0/](./day0/README.md)  |
| 1   | PostgreSQL Modules 1 & 2 + Liquibase                         | [day1/](./day1/README.md)  |
| 2   | Java Modules 1 & 2 — OOP Mastery + SOLID                     | [day2/](./day2/README.md)  |
| 3   | Java Modules 3 & 4 — Functional + Testing                    | [day3/](./day3/README.md)  |
| 4   | Spring Boot Modules 1 & 2 — Enterprise Setup                 | [day4/](./day4/README.md)  |
| 5   | Spring Boot Modules 3 & 4 — REST + JWT Security              | [day5/](./day5/README.md)  |
| 6   | Spring Boot Modules 5 & 6 — Performance + Observability      | [day6/](./day6/README.md)  |
| 7   | HTML/CSS Module 2 + JS Advanced + React Module 1             | [day7/](./day7/README.md)  |
| 8   | React Modules 2 & 3 — Advanced Patterns                      | [day8/](./day8/README.md)  |
| 9   | React Testing + ★ Kafka Deep Dive                            | [day9/](./day9/README.md)  |
| 10  | Docker & CI/CD — Enterprise Deployment                       | [day10/](./day10/README.md) |

Exercises within each day are numbered **Day N · Ex N.M** (e.g. *Day 2 · TICKET-ADV021*).
Each exercise has acceptance criteria, hints, and (for trainers only) a
reference solution in the matching day's README.

---

## Ground rules for trainers

1. **Don't pre-solve in class.** Even though you have the answer key, let
   students struggle for ~20 minutes before unblocking. Stuck-then-shown
   sticks 10× better than shown-then-tried.
2. **Escalate hints, don't dump.** Use the ladder in each exercise:
   nudge → narrow hint → reveal. Most teams need only nudge or hint.
3. **The student copy is the source of truth for what students see.** If an
   exercise description or acceptance criterion drifts between the two
   repos, **the student copy wins** and the trainer copy gets re-synced.
4. **Demo from this trainer copy on Day 10 only**, when you're showing the
   "what the finished product looks like" reference. The other 9 days you
   work in the student copy alongside the team.
5. **AI policy mirrors the students'**. If you use Claude/Copilot to
   generate hints or rephrase explanations, that's fine — but never paste
   AI-generated code into the trainer solutions without reading and
   testing it first. (Same policy you're teaching them.)
6. **Two reviewers per PR.** The Advanced Track doubled the Intermediate
   review requirement — make sure students enforce it on day 1 with
   branch-protection rules, or it'll quietly slip by Day 4.

---

## What's "Advanced" vs Intermediate?

If you've trained the Intermediate (TradeFlow) track before, the Advanced
(ReconX) track ratchets up in these ways:

| Dimension | Intermediate | Advanced |
|---|---|---|
| Java target | 17 | **21** (sealed, records, virtual threads) |
| Domain model | one `Trade` class | **sealed hierarchy** (Equity/FX/Bond/Derivative) |
| Auth | basic Spring Security | **JWT + refresh tokens + 4 RBAC roles** |
| Observability | Actuator only | **Custom Micrometer metrics + Grafana panels + alerts** |
| Kafka | single topic | **3 topics + DLQ + retry + event sourcing** |
| Frontend | basic React | **HOCs + compound components + RHF + SSE feed** |
| Testing | JUnit + MockMvc | **+ Testcontainers + coverage gate ≥ 85%** |
| Deploy | Docker compose locally | **+ GHCR + load test + Liquibase-in-CI** |
| Final demo | 15 min | **20 min** |

If a student says "we did this in the Intermediate track" — they were on a
different (or earlier) cohort. ReconX is the harder track; don't soften it.

---

## How to navigate

- **New trainer joining mid-programme?** Read [day0/README.md](./day0/README.md) first.
- **Prepping for tomorrow's session?** Open `dayN/README.md` for tomorrow's number.
- **Stuck on what to say when a team's blocked?** `TrainersGuide/dayN/README.md`
  → "Common student blockers" + "Unblocking ladder".
- **Want to see the answer?** Code lives under `../backend/`, `../frontend/`, etc.
  in this trainer copy. Look for the comment-banner headers above each
  implementation (WHAT/HOW/WHY/OBSERVE/HINT block).
- **Q&A bank** — every day's README ends with a Q&A section. If a question
  keeps recurring across cohorts, add it there with the model answer.

---

## Versioning

This trainer copy is versioned in lockstep with the student copy. If you
discover an issue (typo, broken sample, incorrect solution), open a PR on
**both** copies in the same branch — they ship together or they don't ship.

Current version: **1.0** (cohort: TDI 2026).
