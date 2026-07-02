# TrainersGuide — Day 7: HTML/CSS Module 2 + JavaScript Advanced + React Module 1

> **Student-facing equivalent:** [student-guides/day7/README.md](../../student-guides/day7/README.md)
> **Exercises:** Day 7 · TICKET-ADV098 – TICKET-ADV106 (9 hands-on exercises)
> **Theme:** HTML/CSS Module 2 + JS Advanced + React Module 1 — Frontend Foundations
> **What students build:** Flexbox-based dashboard shell, design-token theming, animations, responsive layout, an SSE-driven live trade feed, an advanced data table, and their first React project scaffold.

Day 7 is the frontend pivot day. The AM block doubles up: Advanced CSS
(Flexbox, custom properties, animations, responsive) in the first half,
JavaScript Advanced (ES6+ classes, advanced DOM, Promises / async-await /
Web Workers) in the second half. The PM theory block introduces React
Module 1 — Vite/CRA project setup, JSX components & props, the hook
trio (`useMemo`, `useCallback`, `useReducer`), and composition primitives
(`React.memo`, lazy loading). The workshop is **9 exercises**, down from
the legacy 13: Semantic HTML5, ARIA, keyboard navigation, and BEM are
**no longer taught** — those skills are explicitly out of scope and
should not be coached during walk-arounds.

---

## Day at a glance

| #    | Block                                                                          | Exercises       | What students produce                                                            |
|---------------|--------------------------------------------------------------------------------|-----------------|----------------------------------------------------------------------------------|
| 1 | Standup                                                                        | —               | Backend `/api/v1/trades/stream` confirmed reachable                              |
| 2 | **AM — HTML/CSS Module 2 (Advanced CSS)** (theory + Percipio labs)             | —               | Notes: Flexbox layout, CSS custom properties, animations/transitions, responsive |
| 3 | Break                                                                          | —               | —                                                                                |
| 4 | **AM — JavaScript Advanced** (theory + Percipio labs)                          | —               | Notes: ES6+ classes, advanced DOM, Promises / async-await / Web Workers          |
| 5 | Lunch                                                                          | —               | —                                                                                |
| 6 | **PM theory — React Module 1: Advanced Foundations** (theory + Percipio labs)  | —               | Notes: Vite/CRA setup, JSX + props, useMemo/useCallback/useReducer, composition  |
| 7 | **Workshop 7 — Frontend foundations + SSE feed**                               | TICKET-ADV098 – TICKET-ADV106 | Flexbox shell, design tokens, dark mode, animations, responsive, SSE feed, table |
| 8 | End-of-day debrief                                                             | —               | Day-8 preview (React Module 2 + styling)                                         |

**Pacing notes:**

- The AM is **two back-to-back theory blocks**. Do not bleed the CSS
  half into the JS half — students need both before the workshop.
- **Flexbox is the layout primitive.** CSS Grid is intentionally out of
  the taught syllabus this year; treat it only as a stretch goal for
  fast finishers who already know Flexbox cold.
- **Do not coach Semantic HTML5, ARIA, keyboard navigation, or BEM.**
  These were dropped from the syllabus. If a student asks, point them
  at the React accessibility patterns they will meet on Day 9.
- Workshop 7 has **9 exercises in 2 hours 15 minutes** — pace ~15 minutes
  each. TICKET-ADV104–7.9 (SSE + advanced table) are the longest; if a team is
  behind by TICKET-ADV103, skip TICKET-ADV106 (table) and reveal the reference.

---

## Pre-day instructor prep

The evening before Day 7:

- [ ] **Pre-create the `static-dashboard/` skeleton** in each student starter.
  At minimum: `dashboard.html`, `trades.html`, `recon.html`, `css/style.css`,
  `js/dashboard.js`, `js/sse.js`. Empty is fine — students fill them in.
- [ ] **Confirm `/api/v1/trades/stream` is reachable** on every team's backend.
  This was scaffolded on Day 6. If it 404s, the SSE half of Workshop 7
  (TICKET-ADV104 – TICKET-ADV105) dies on the vine. Hit it with
  `curl -N http://localhost:8080/api/v1/trades/stream` before standup.
- [ ] **Browser DevTools cheat sheet ready** — you will demo Elements,
  Computed styles, the Lighthouse panel, and Application → Local Storage at
  least 10 times today. Pin those tabs.
- [ ] **Run Lighthouse against the baseline page** (an empty
  `dashboard.html` with a single `<h1>`) and screenshot the scores. You will
  re-run at end of day and the contrast is the point.
- [ ] **Install the axe DevTools extension** in your demo browser. You'll
  use it during the end-of-day a11y audit.
- [ ] Have **two browser windows** ready side-by-side: one at 1440px wide,
  one at 375px (iPhone SE). The responsive demo is more convincing with both
  visible.
- [ ] Decide whether the SSE endpoint you'll demo emits a trade every
  **2 seconds** (gentle) or **300 ms** (chaotic — better for talking about
  animation throttling). 2s is the safe default; bump to 300ms only if the
  group is keeping pace.

---

## Workshop 7 — Frontend Foundations + SSE Feed (135 min)

Workshop 7 is **9 exercises in 2 hours 15 minutes**. The goal is for
every team to leave with a Flexbox-laid dashboard, design-token theming,
animated SSE trade feed, a sortable table, and the muscle memory to
walk into Day 8's React project without bouncing off the CSS basics.
Skip nothing on TICKET-ADV098 – TICKET-ADV103; if you must drop one, drop TICKET-ADV106 last.

### TICKET-ADV098 — Flexbox layout: sidebar, header, 3-column main, footer

> **Flexbox is the chosen layout primitive for Day 7. CSS Grid is a
> stretch topic for fast finishers — do not lecture on it.**

**Common student blockers:**

- **`flex-basis` vs `width` confusion** — students set `width: 240px` on
  the sidebar and wonder why it still shrinks. In a flex row, the
  initial main size comes from `flex-basis` (or `width` if basis is
  `auto`), and `flex-shrink: 1` is the default — so it shrinks. Set
  `flex: 0 0 240px` to lock the sidebar.
- **`justify-content` vs `align-items` mix-up** — they want the header
  contents centered vertically and reach for `justify-content: center`.
  `justify-content` runs along the main axis, `align-items` along the
  cross axis. In a horizontal flex row, vertical centering is
  `align-items: center`.
- **Forgetting `flex-wrap`** for the 3-column main area — without
  `flex-wrap: wrap` the three cards push out of the container at
  narrower widths instead of dropping to the next row.
- They set `body { display: flex }` but the `<html>` element doesn't
  have `height: 100%`, so the column collapses to content height and
  the footer floats up the page.

**Unblocking ladder:**

1. **Nudge:** "Open DevTools → Elements → click `<body>` → look at the
   Flex overlay (badge says `flex`). Does the main axis match what
   you expect — column or row?"
2. **Hint:** "Your sidebar keeps shrinking. What's its `flex-shrink`?
   Default is 1 — set it to 0 (or use the shorthand `flex: 0 0 240px`)."
3. **Reveal:** Walk through the nested-flex idea on the whiteboard.
   Outer container is `flex-direction: column` (header → body row →
   footer). The middle row is itself a flex row (sidebar + main).

<details>
<summary>▶ Reference solution — TICKET-ADV098 Flexbox page shell</summary>

```html
<!-- File: static-dashboard/dashboard.html -->
<!DOCTYPE html>
<html lang="en" data-theme="light">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>ReconX — Dashboard</title>
  <link rel="stylesheet" href="css/style.css">
</head>
<body class="app-shell">
  <header class="app-header">...</header>
  <div class="app-body">
    <aside class="app-sidebar">...</aside>
    <main class="app-main">
      <section class="dashboard-grid">
        <!-- three cards -->
      </section>
    </main>
  </div>
  <footer class="app-footer">...</footer>

  <script src="js/dashboard.js" defer></script>
  <script src="js/sse.js" defer></script>
</body>
</html>
```

```css
/* File: static-dashboard/css/style.css — TICKET-ADV098 Flexbox shell */
html, body {
  height: 100%;
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

/* Outer shell: vertical stack (header, body row, footer) */
.app-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.app-header {
  flex: 0 0 64px;
  display: flex;
  align-items: center;        /* vertical centering in a row */
  padding: 0 var(--space-4);
  background: var(--color-primary);
  color: #fff;
}

/* Middle row: sidebar + main, grows to fill */
.app-body {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;              /* lets children scroll inside */
}

.app-sidebar {
  flex: 0 0 240px;            /* locked: no grow, no shrink, 240px basis */
  background: var(--color-bg-alt);
  border-right: 1px solid var(--color-border);
  padding: var(--space-4);
}

.app-main {
  flex: 1 1 auto;             /* fills remaining width */
  padding: var(--space-4);
  overflow-y: auto;
}

.app-footer {
  flex: 0 0 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-alt);
  border-top: 1px solid var(--color-border);
  font-size: 0.85rem;
  color: var(--color-text-muted);
}

/* The 3-column main content area — flex row that wraps */
.dashboard-grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
}
.dashboard-grid > * {
  flex: 1 1 calc((100% - var(--space-4) * 2) / 3);  /* 3 columns by default */
  min-width: 240px;
}
```

</details>

**Talking point:** "Why Flexbox for the page shell?" — Flexbox is the
one layout primitive every grad must internalise. The shell is two
nested 1-D flows (column outside, row inside) — perfectly inside
Flexbox's comfort zone. CSS Grid would be terser for a header /
sidebar / main / footer template, but Grid is a **stretch topic** this
year; teach Flexbox first and demand fluency. Anyone who finishes Ex
7.1 in five minutes can rewrite their solution using `grid-template-
areas` as a personal exercise.

**▶ Run the project — verify TICKET-ADV098 end-to-end**

Serve the static dashboard and check the app-shell grid + ARIA landmarks render correctly.

```bash
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

**Observe:**

- Header, sidebar, main, and footer are laid out as a grid — header spans the top, sidebar on the left, main beside it, footer pinned to the bottom.
- DevTools inspector shows `<header role="banner">`, `<main role="main">`, `<aside role="complementary">`, `<footer role="contentinfo">` — landmarks announce correctly to a screen reader.
- Run `npx @axe-core/cli http://localhost:5500/dashboard.html` — no violations.
- Failure signal: a flat single-column stack with no sidebar means the `grid-template-areas` rule isn't applying — check the `body { display: grid }` selector.

---

### Theming + animations + responsive (TICKET-ADV099 – TICKET-ADV103)

This is the **CSS heavy lifting** mid-section. Custom properties enable
theming, keyframes enable the trade-feed slide-in, and media queries
make it survive a phone screen. Students who already "know CSS" should
be pushed to use **CSS custom properties everywhere** — no hardcoded
colors in `.trade-card`, ever.

### TICKET-ADV099 — CSS custom properties (design tokens)

**Common student blockers:**

- They define variables on `.card` or some random component scope. Then
  another component can't read them. **Variables on `:root`** unless you
  have a specific reason otherwise.
- They use SCSS-style naming (`$primary`) and wonder why nothing works.
  CSS custom properties are `--primary` and accessed via `var(--primary)`.
- They forget the fallback: `var(--color-primary, #003366)` — without the
  fallback, a typo silently produces no color.

<details>
<summary>▶ Reference solution — TICKET-ADV099 design tokens</summary>

```css
/* File: static-dashboard/css/style.css — top of file */
:root {
  /* Brand colors */
  --color-primary:    #003366;   /* DB navy */
  --color-primary-2:  #0066cc;
  --color-success:    #28a745;
  --color-warning:    #ffc107;
  --color-danger:     #dc3545;

  /* Surfaces */
  --color-bg:         #ffffff;
  --color-bg-alt:     #f5f7fa;
  --color-border:     #e1e5eb;
  --color-text:       #1a1a1a;
  --color-text-muted: #6c757d;

  /* Spacing scale (4px base) */
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 24px;
  --space-6: 32px;

  /* Radius + shadow */
  --radius: 4px;
  --radius-lg: 8px;
  --shadow-sm: 0 1px 2px rgba(0,0,0,0.05);
  --shadow-md: 0 4px 12px rgba(0,0,0,0.08);

  /* Type */
  --font-size-sm: 0.875rem;
  --font-size-md: 1rem;
  --font-size-lg: 1.25rem;
}
```

</details>

**Talking point:** "Custom properties vs SCSS variables — why bother with
CSS-native?" SCSS variables are resolved at *compile time* — they cannot
respond to runtime state. CSS custom properties are *live in the
cascade* — they re-resolve when the DOM changes (e.g. when you flip
`data-theme="dark"` on `<html>`). This is the whole reason dark-mode
toggling works without a rebuild.

**▶ Run the project — verify TICKET-ADV099 end-to-end**

Serve the dashboard and inspect computed styles to confirm every value flows through `:root` tokens.

```bash
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

**Observe:**

- DevTools → Elements → select `<html>` → Computed pane shows `--color-gold`, `--color-slate`, `--color-surface`, `--space-4`, `--radius`, etc. all defined on `:root`.
- Inspect a `.trade-card` (or any styled element) — its computed `background-color` resolves from `var(--color-surface)`, not a hex literal.
- Failure signal: any component rule still showing a `#abc123` in the Styles pane means the refactor missed a hardcoded value.

---

### TICKET-ADV100 — Dark/light theme toggle

**Common student blockers:**

- **FOUC** (flash of unstyled content): the page loads with the light
  theme, then JS reads `localStorage` and flips to dark — users see a
  half-second white flash. Fix: read the theme **before** any paint,
  ideally in a blocking `<script>` in `<head>`.
- They toggle by adding a `class="dark"` to `<body>` and writing a
  duplicate stylesheet for `.dark .card { ... }`. Works but doesn't
  scale — every component is now defined twice. Use `data-theme` on
  `<html>` and override custom properties instead.
- They store the theme in `sessionStorage` instead of `localStorage`. It
  resets every tab — annoying.

<details>
<summary>▶ Reference solution — TICKET-ADV100 theme toggle</summary>

```html
<!-- File: static-dashboard/dashboard.html — head section -->
<head>
  <!-- ... -->
  <script>
    // CRITICAL: runs before <body> paints — prevents FOUC.
    (function () {
      var saved = localStorage.getItem('reconx-theme') || 'light';
      document.documentElement.setAttribute('data-theme', saved);
    })();
  </script>
  <link rel="stylesheet" href="css/style.css">
</head>
```

```css
/* File: static-dashboard/css/style.css — theme overrides */
[data-theme="dark"] {
  --color-bg:         #1a1a1a;
  --color-bg-alt:     #2a2a2a;
  --color-border:     #3a3a3a;
  --color-text:       #f5f5f5;
  --color-text-muted: #9aa1ab;
  --shadow-sm: 0 1px 2px rgba(0,0,0,0.4);
  --shadow-md: 0 4px 12px rgba(0,0,0,0.5);
}

/* Components reference tokens only — never hardcode */
.card {
  background: var(--color-bg);
  color: var(--color-text);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: var(--space-4);
}
```

```javascript
// File: static-dashboard/js/dashboard.js — theme toggle handler
const themeToggle = document.getElementById('theme-toggle');
themeToggle?.addEventListener('click', () => {
  const html = document.documentElement;
  const current = html.getAttribute('data-theme') || 'light';
  const next = current === 'light' ? 'dark' : 'light';
  html.setAttribute('data-theme', next);
  localStorage.setItem('reconx-theme', next);
  themeToggle.setAttribute('aria-pressed', String(next === 'dark'));
});
```

</details>

**Talking point:** `data-theme` attribute vs `class="dark"` — the data
attribute is the modern convention (cleaner CSS selectors, more
extensible to `data-theme="high-contrast"` etc.) and lives on `<html>`
not `<body>` so it can scope variables before the body renders.

**▶ Run the project — verify TICKET-ADV100 end-to-end**

Serve the dashboard, click the theme toggle, and confirm the attribute flip + persistence.

```bash
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

**Observe:**

- Click `#theme-toggle` — `<html data-theme="light">` flips to `data-theme="dark"`, background/text colours invert immediately.
- DevTools → Application → Local Storage → `http://localhost:5500` — key `reconx-theme` holds the current value (`dark` or `light`).
- Reload the page — the saved theme reapplies before paint, no white flash.
- Failure signal: a brief white flash on reload means the inline IIFE is loading after the stylesheet — move it above `<link rel="stylesheet">` in `<head>`.

---

### TICKET-ADV101 + TICKET-ADV102 — Trade feed area + CSS animations

**Common student blockers:**

- They animate `top`/`left` instead of `transform`. The browser cannot
  GPU-compose `top` changes; you get jank at >60fps.
- They use `animation: slide-in 0.3s` without specifying `ease-out`.
  Linear feels robotic; ease-out feels natural for content arrivals.
- They animate `width`/`height` for a "pulse" effect — same GPU problem.
  Use `transform: scale()`.

<details>
<summary>▶ Reference solution — TICKET-ADV101 + TICKET-ADV102 keyframes</summary>

```css
/* File: static-dashboard/css/style.css — animations */

@keyframes slide-in {
  from { transform: translateX(-20px); opacity: 0; }
  to   { transform: translateX(0);     opacity: 1; }
}

@keyframes fade-in {
  from { opacity: 0; }
  to   { opacity: 1; }
}

@keyframes pulse {
  0%, 100% { transform: scale(1);    box-shadow: 0 0 0 0 rgba(220,53,69,0.6); }
  50%      { transform: scale(1.02); box-shadow: 0 0 0 8px rgba(220,53,69,0); }
}

.trade-card {
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-left: 4px solid var(--color-text-muted);
  border-radius: var(--radius);
  padding: var(--space-3);
  margin-bottom: var(--space-2);
  animation: slide-in 0.3s ease-out;
}

.trade-card--new {
  /* applied for the first 500ms after insert — gives a richer entrance */
  animation: slide-in 0.4s ease-out, fade-in 0.4s ease-out;
}

.alert--danger {
  animation: pulse 2s ease-in-out infinite;
}

/* Respect prefers-reduced-motion — disable non-essential animation */
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

</details>

**Talking point:** Always pair animations with a `prefers-reduced-motion`
opt-out. About 1 in 30 users has vestibular-disorder sensitivity to
motion. It is one media query — there is no excuse to skip it.

**▶ Run the project — verify TICKET-ADV101 end-to-end**

Serve the dashboard, watch the demo trade events populate, and confirm status-coloured borders + entrance animation.

```bash
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

**Observe:**

- Three demo cards appear in `#trade-feed` on staggered 500ms ticks — each slides in from the left via the `translateX(-10%) → 0` keyframe.
- A `.trade-card--matched` card has a green (`--color-success`) left border; a `.trade-card--break` card has a red (`--color-danger`) left border.
- Manually paste `<div class="trade-card trade-card--matched">test</div>` into `#trade-feed` via DevTools — it slides in immediately.
- Failure signal: cards appear with no animation — confirm `animation: slide-in 0.3s ease-out` is on `.trade-card` (not on a parent) and the `@keyframes` block is defined.

**▶ Run the project — verify TICKET-ADV102 end-to-end**

Serve the dashboard and toggle the reduced-motion emulation in DevTools to confirm the override fires.

```bash
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

**Observe:**

- DevTools → ⋮ → More tools → Rendering → "Emulate CSS media feature prefers-reduced-motion" → `reduce`.
- Reload the page — trade cards still appear but with no slide animation; any `.alert--danger` pulse halts instantly.
- Switch emulation back to `no-preference` — animations run normally again.
- Failure signal: the `pulse` animation keeps running even with reduce enabled — confirm the `@media (prefers-reduced-motion: reduce)` block sits at the **bottom** of the stylesheet and uses `!important`.

---

### TICKET-ADV103 — Responsive breakpoints: desktop (3-col), tablet (2-col), mobile (1-col)

**Common student blockers:**

- They write breakpoints in `px` and then test on a Retina display where
  the layout breaks at a different *effective* width. Use `em` or stay
  with `px` but test in DevTools device emulation.
- They redefine the desktop `flex-basis` at tablet but forget the
  `.app-body` row needs to flip from row to column on mobile. Result:
  sidebar still claims 240px on a 375px screen.
- Mobile-first vs desktop-first: they mix the two. Pick one. Mobile-first
  (`min-width` queries that scale up) is the modern default; desktop-first
  works fine for an internal-only app.

<details>
<summary>▶ Reference solution — TICKET-ADV103 responsive Flexbox layout</summary>

```css
/* File: static-dashboard/css/style.css — responsive layout */

/* Desktop default (defined in TICKET-ADV098): row sidebar + main, 3 cards per row */

/* Tablet: drop sidebar to a top strip, cards 2 per row */
@media (max-width: 1024px) {
  .app-body { flex-direction: column; }
  .app-sidebar {
    flex: 0 0 auto;
    border-right: none;
    border-bottom: 1px solid var(--color-border);
  }
  .dashboard-grid > * {
    flex-basis: calc((100% - var(--space-4)) / 2);   /* 2 columns */
  }
}

/* Mobile: single column everything */
@media (max-width: 640px) {
  .app-header { flex-basis: 56px; padding: 0 var(--space-3); }
  .app-main   { padding: var(--space-3); }
  .dashboard-grid > * {
    flex-basis: 100%;                                /* 1 column */
  }
  .trade-card__body { font-size: var(--font-size-sm); }
}
```

</details>

**Talking point:** The dashboard-grid stays a flex row at every
breakpoint — only its children's `flex-basis` changes. That's the
Flexbox "1-D flow" model in action: one axis, parameterised by
breakpoint. If a fourth card appears, nothing changes; if the screen
narrows, the media query takes over.

**▶ Run the project — verify TICKET-ADV103 end-to-end**

Serve the dashboard and resize the viewport across the three breakpoints to confirm the layout adapts.

```bash
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

**Observe:**

- At ≥1025px: sidebar fixed at 240px (or your chosen desktop width), main beside it, footer pinned bottom.
- Resize below 1024px (DevTools device toolbar → 1024px): sidebar narrows to 180px (per the trainer rule) — grid still side-by-side.
- Resize below 720px (or 375px iPhone preset): sidebar disappears, grid collapses to a single column, no horizontal scroll appears.
- Failure signal: a horizontal scrollbar at 375px means a fixed-width child (table, card grid) is overflowing — set `min-width: 0` on the affected flex/grid item.

---

### SSE live feed + advanced table (TICKET-ADV104 – TICKET-ADV106)

This is the **payoff stretch**. By 16:45 every team should have a live
trade feed prepending in real time and a sortable + resizable trades
table. Save 5 – 10 minutes at the end for the cross-team show-and-tell.

### TICKET-ADV104 — Server-Sent Events subscription

**Common student blockers:**

- They use `fetch()` with streaming response handling, manually parsing
  SSE frames. Why? The browser ships `EventSource`. Use it.
- They forget the SSE endpoint must respond with
  `Content-Type: text/event-stream`. If the backend Day-6 wiring is
  wrong, EventSource silently retries forever.
- **The DDoS pitfall**: `onerror` is wired to "log error and re-create
  EventSource". EventSource already auto-reconnects with backoff — adding
  manual reconnect on top creates a reconnect storm that hammers the
  dev server.

<details>
<summary>▶ Reference solution — TICKET-ADV104 SSE wiring</summary>

```javascript
// File: static-dashboard/js/sse.js — TICKET-ADV104 + TICKET-ADV105
(function () {
  const FEED_EL = document.getElementById('trade-feed');
  if (!FEED_EL) return;

  const STREAM_URL = '/api/v1/trades/stream';
  let sse = null;
  let connectionStatus = 'connecting';

  function connect() {
    sse = new EventSource(STREAM_URL);

    sse.onopen = () => {
      connectionStatus = 'connected';
      updateConnectionBadge('Live', 'success');
    };

    sse.onmessage = (event) => {
      try {
        const trade = JSON.parse(event.data);
        prependTradeRow(trade);
      } catch (err) {
        console.error('Bad SSE payload', event.data, err);
      }
    };

    sse.onerror = () => {
      // EventSource auto-reconnects with exponential backoff.
      // DO NOT call connect() here — you will DDoS the dev server.
      connectionStatus = 'reconnecting';
      updateConnectionBadge('Reconnecting…', 'warning');
    };
  }

  function updateConnectionBadge(text, variant) {
    const badge = document.getElementById('sse-status');
    if (!badge) return;
    badge.textContent = text;
    badge.className = `badge badge--${variant}`;
  }

  // Clean up on page unload
  window.addEventListener('beforeunload', () => sse?.close());

  connect();
})();
```

</details>

**▶ Run the project — verify TICKET-ADV104 end-to-end**

For the demo stub, just the static server. For the real SSE wiring, also start the backend so `EventSource` can connect to `/api/v1/trades/stream`.

```bash
# Terminal 1 — backend (for real SSE wiring)
./mvnw spring-boot:run
# Terminal 2 — static dashboard
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

**Observe:**

- DevTools → Network tab → filter by "EventSource" (or "stream") — for the demo stub, three trade events arrive on the staggered 500ms `setTimeout` schedule.
- With the production wiring + backend running: a single `/api/v1/trades/stream` request stays open (status: pending, type: eventsource); `#sse-status` reads "Live".
- Stop the backend — badge flips to "Reconnecting…"; restart it — badge returns to "Live" without any code re-running `new EventSource()`.
- Failure signal: the Network tab shows repeated full requests to `/stream` — you're calling `connect()` from `onerror`; remove that and let the browser auto-reconnect.

---

### TICKET-ADV105 — Prepend new trades with animation

<details>
<summary>▶ Reference solution — TICKET-ADV105 prependTradeRow</summary>

```javascript
// File: static-dashboard/js/sse.js — TICKET-ADV105
function prependTradeRow(trade) {
  const row = document.createElement('article');
  const statusModifier = trade.status === 'MATCHED'   ? 'trade-card--matched'
                       : trade.status === 'UNMATCHED' ? 'trade-card--break'
                       :                                 '';
  row.className = `trade-card ${statusModifier} trade-card--new`;
  row.innerHTML = `
    <header class="trade-card__header">
      <span class="trade-card__ref">${escapeHtml(trade.tradeRef)}</span>
      <span class="trade-card__status">${escapeHtml(trade.status)}</span>
    </header>
    <div class="trade-card__body">
      ${escapeHtml(trade.symbol)}  ${formatQty(trade.quantity)} @ ${formatPrice(trade.price)} ${escapeHtml(trade.currency)}
    </div>
  `;
  FEED_EL.prepend(row);

  // Drop the "new" modifier after the entrance animation ends.
  setTimeout(() => row.classList.remove('trade-card--new'), 500);

  // Cap the feed to the last 50 entries — DOM grows unbounded otherwise.
  while (FEED_EL.children.length > 50) {
    FEED_EL.lastElementChild.remove();
  }
}

function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, c =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function formatQty(n)   { return new Intl.NumberFormat('en-US').format(n); }
function formatPrice(n) { return new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 4 }).format(n); }
```

</details>

**Talking point:** Notice the **50-row cap**. Without it, a long-running
dashboard with 5 trades/sec accumulates 18,000 DOM nodes per hour.
Browsers don't like that. Real-time UIs always need a windowing strategy
— this is a tiny preview of what React virtualised lists solve at scale
on Day 8.

**▶ Run the project — verify TICKET-ADV105 end-to-end**

Serve the dashboard and watch the prepend order + DOM cap in DevTools.

```bash
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

**Observe:**

- Each new trade card lands at the **top** of `#trade-feed` (newest first); previous cards shift down — confirm in DevTools → Elements that the first child of `#trade-feed` is always the most recent event.
- Each insert plays the slide-in animation; the temporary `.trade-card--new` modifier disappears ~500ms after insert (inspect classList live).
- Inject 60+ events (or push fake objects from the Console) — `feed.children.length` caps at 50; the oldest card at the bottom is removed.
- Failure signal: new cards appear at the bottom — you used `appendChild` / `append` instead of `prepend`.

---

### TICKET-ADV106 — Advanced data table (sortable, resizable, frozen header)

**Common student blockers:**

- They reach for DataTables.js or a library. **No.** The whole point of
  Day 7 is that you don't always need one.
- Frozen header `<thead>` with `position: sticky; top: 0` silently fails
  because an ancestor has `overflow: hidden`. Sticky is killed by any
  overflow-clipping ancestor.
- Resizable columns: they listen to `mousemove` on the column handle.
  The cursor leaves the handle the moment you start dragging and the
  listener fires for one frame then dies. Listen on `document`, not
  on the handle.

<details>
<summary>▶ Reference solution — TICKET-ADV106 sortable + resizable + frozen-header table</summary>

```html
<!-- File: static-dashboard/trades.html — table fragment -->
<div class="table-scroll">
  <table class="data-table" id="trades-table">
    <thead>
      <tr>
        <th data-col="tradeRef"    data-dir="asc">Trade Ref <span class="resize-handle"></span></th>
        <th data-col="symbol"      data-dir="asc">Instrument <span class="resize-handle"></span></th>
        <th data-col="quantity"    data-dir="asc" data-type="number">Quantity <span class="resize-handle"></span></th>
        <th data-col="price"       data-dir="asc" data-type="number">Price <span class="resize-handle"></span></th>
        <th data-col="status"      data-dir="asc">Status <span class="resize-handle"></span></th>
      </tr>
    </thead>
    <tbody id="trades-tbody"><!-- rows injected by JS --></tbody>
  </table>
</div>
```

```css
/* File: static-dashboard/css/style.css — table */
.table-scroll {
  /* CRITICAL: no overflow:hidden ANYWHERE up the ancestor chain, or
     sticky header breaks. overflow:auto is fine. */
  overflow: auto;
  max-height: 60vh;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-sm);
}
.data-table th,
.data-table td {
  padding: var(--space-2) var(--space-3);
  text-align: left;
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}
.data-table thead th {
  position: sticky;
  top: 0;
  background: var(--color-bg-alt);
  cursor: pointer;
  user-select: none;
  z-index: 1;
}
.data-table thead th[aria-sort="ascending"]::after  { content: " ▲"; }
.data-table thead th[aria-sort="descending"]::after { content: " ▼"; }

.resize-handle {
  display: inline-block;
  width: 4px;
  height: 16px;
  cursor: col-resize;
  background: var(--color-border);
  margin-left: var(--space-2);
  vertical-align: middle;
}
```

```javascript
// File: static-dashboard/js/trades.js — TICKET-ADV106 sort + resize
(function () {
  const table = document.getElementById('trades-table');
  const tbody = document.getElementById('trades-tbody');
  let rows = []; // canonical data — sort operates on this

  // ---------- sortable columns ----------
  table.querySelectorAll('thead th').forEach(th => {
    th.addEventListener('click', (e) => {
      if (e.target.classList.contains('resize-handle')) return; // ignore resize clicks
      const col = th.dataset.col;
      const type = th.dataset.type || 'string';
      const dir = th.getAttribute('aria-sort') === 'ascending' ? 'descending' : 'ascending';

      // clear all, set this one
      table.querySelectorAll('thead th').forEach(o => o.removeAttribute('aria-sort'));
      th.setAttribute('aria-sort', dir);

      const mult = dir === 'ascending' ? 1 : -1;
      rows.sort((a, b) => {
        const av = a[col], bv = b[col];
        if (type === 'number') return (Number(av) - Number(bv)) * mult;
        return String(av).localeCompare(String(bv)) * mult;
      });
      renderRows();
    });
  });

  // ---------- resizable columns ----------
  table.querySelectorAll('.resize-handle').forEach(handle => {
    handle.addEventListener('mousedown', (e) => {
      e.preventDefault();
      const th = handle.closest('th');
      const startX = e.clientX;
      const startWidth = th.offsetWidth;

      // Listen on DOCUMENT so the drag survives leaving the handle.
      function onMove(ev) { th.style.width = (startWidth + ev.clientX - startX) + 'px'; }
      function onUp()     { document.removeEventListener('mousemove', onMove);
                            document.removeEventListener('mouseup', onUp); }
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    });
  });

  function renderRows() {
    tbody.innerHTML = rows.map(r => `
      <tr>
        <td>${r.tradeRef}</td><td>${r.symbol}</td>
        <td>${r.quantity}</td><td>${r.price}</td>
        <td>${r.status}</td>
      </tr>`).join('');
  }

  // initial load — hits the REST API from Day 5
  fetch('/api/v1/trades?size=200')
    .then(r => r.json())
    .then(data => { rows = data.content || data; renderRows(); });
})();
```

</details>

**Talking point:** `aria-sort` on the `<th>` is the accessibility win
here — a screen-reader user navigating to a column header is *told* the
current sort state, without you adding `aria-label="sorted ascending"`
or anything custom. Native semantics first.

**▶ Run the project — verify TICKET-ADV106 end-to-end**

Serve `trades.html` (and run the backend if the table fetches from `/api/v1/trades`) and exercise sort + resize + frozen header.

```bash
# Terminal 1 — backend (so fetch('/api/v1/trades?size=200') returns data)
./mvnw spring-boot:run
# Terminal 2 — static dashboard
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/trades.html
```

**Observe:**

- Table renders with seed rows; click the `Quantity` `<th>` — rows sort numerically ascending, the `▲` indicator appears, and `aria-sort="ascending"` shows in DevTools. Click again — descends with `▼`.
- Drag a `.resize-handle` to the right — column widens smoothly; the drag continues even when the cursor leaves the handle (mouse listener is on `document`).
- Scroll the `.table-scroll` body — the `<thead>` row stays pinned at the top thanks to `position: sticky`.
- Failure signal: the header scrolls away with the body — some ancestor has `overflow: hidden`; check the `.app-shell` / `.app-main` ancestor chain and remove it.

---

<details>
<summary><b>Q&A bank</b></summary>


Use these to drill in concepts during walk-arounds, or save them for the
end-of-day debrief.

1. **Why HTML/CSS before React?** Because React renders to HTML and is
   styled with CSS. A team that doesn't understand the cascade,
   specificity, or how Flexbox distributes space ships React components
   that are unstyleable. You learn the canvas before you paint.

2. **Why Flexbox and not CSS Grid for the page shell?** Flexbox is the
   one layout primitive every grad has to internalise this year. Grid
   is terser for 2-D templates but is a **stretch topic** in 2026 —
   we cover it only as an optional extension. The trick to a Flexbox
   shell is *nesting*: a column for the outer stack, a row for the
   sidebar+main band, another row that wraps for the card grid.

3. **CSS custom properties vs SCSS variables — what's the difference?**
   SCSS resolves at compile time; custom properties live in the cascade
   and re-resolve at runtime. Dark-mode toggling depends on runtime
   resolution — SCSS can't do it without a full rebuild.

4. **`data-theme` attribute vs `class="dark"` — why the data attribute?**
   `data-theme` lives on `<html>` (so it can scope variables before
   `<body>` paints), is semantically explicit, and extends naturally
   to `data-theme="high-contrast"`. The class approach works but
   doesn't scale.

5. **SSE vs WebSocket — when do I pick which?** SSE = one-way, server
   to client, runs over plain HTTP, auto-reconnects, perfect for trade
   feeds / notifications / progress. WebSocket = bidirectional, needed
   for chat / collaborative editing / multiplayer games. SSE is
   simpler — use it unless you genuinely need duplex.

6. **Why not Tailwind?** Three reasons specific to this programme:
   (a) we teach the cascade, not utility classes; (b) DB internal apps
   tend toward custom design systems with tokens, which is custom-
   property territory; (c) Tailwind without componentisation = HTML
   with 40-class soup. After Day 8 with React, opt in if your team
   wants.

7. **Sortable table without a library — really?** Yes, and for a 5-
   column table it's 30 lines of JS. Libraries add 100 KB to your
   bundle for features (i18n, virtualization, server-side pagination)
   you don't need until you do. Reach for AG-Grid or TanStack Table
   when the requirements warrant it, not because "tables are hard".

8. **Are we really supporting IE / old browsers?** No — DB internal
   apps target the latest Chrome/Edge. That's why we use Flexbox,
   custom properties, and `:focus-visible` without polyfills. The
   constraint we DO have is: works on iPad Safari (used on the
   trading floor) and over remote-desktop sessions (low-latency CSS
   only — no fancy blur backdrops).

9. **Why `position: sticky` instead of `position: fixed` for the
   table header?** Sticky scrolls with the page until it hits the
   container edge, then sticks. Fixed is positioned relative to the
   viewport — the header would also stick when scrolling between
   cards, which is wrong.

10. **Why `transform` for animations and not `top` / `left`?**
    `transform` runs on the compositor thread (GPU). `top` / `left`
    triggers layout reflow on every frame. The difference is jank-
    free 60fps vs. a slideshow.

11. **`localStorage` vs `sessionStorage` vs cookies for the theme?**
    `localStorage` — persists across tabs and sessions, no server
    round-trip, no cookie size limits. Cookies would also work but
    add overhead to every HTTP request. `sessionStorage` resets per
    tab, which would annoy users.

12. **The Lighthouse score dropped after I added animations — why?**
    Probably the cumulative-layout-shift (CLS) metric. Animations that
    change layout (anything but `transform` and `opacity`) cause
    shifts. Use `will-change: transform` sparingly and prefer
    transforms.

13. **Promises vs async/await vs Web Workers — when do I reach for
    each?** Promises and async/await are the same machinery — `await`
    is just sugar over `.then()`. Reach for Web Workers only when a
    computation actually blocks the main thread (e.g. parsing a 50 MB
    JSON dump) — not as a "make it faster" reflex.

14. **`useMemo` vs `useCallback` vs `useReducer` — when do I pick
    which?** `useMemo` caches a *value*; `useCallback` caches a
    *function* (it's `useMemo(() => fn, deps)` under the hood);
    `useReducer` is `useState` with a single reducer for related
    state. Use the first two **only when a profiler shows a problem** —
    premature memoisation makes code harder to read.

---

</details>

<details>
<summary><b>End-of-day debrief prompts</b></summary>


At 16:45:

1. **"Show me your dashboard at 375px width."** If anything overflows
   horizontally, that's a fail. The fix usually lives in one media
   query against the Flexbox layout.
2. **"Show me the Flexbox arrow overlay on `.app-body` in DevTools."**
   They should be able to point at the main-axis arrow and tell you
   what direction it runs at desktop vs. tablet.
3. **"Disable JavaScript in DevTools and reload. What still works?"**
   The static dashboard view (cards, recon summary) should render
   fine; only the live trade feed should go silent. That's the
   correct degradation.
4. **"Walk me through one async function from TICKET-ADV104 or 7.8 — where
   does the Promise get awaited and what happens on rejection?"** This
   is the JS-Advanced check: if they can't articulate it, replay the
   `EventSource` flow with them at the whiteboard.

If a team can't pass all four, push the gaps onto their Day 8 backlog
explicitly — React will not fix layout or async-flow gaps for you.

---

</details>

<details>
<summary><b>Things that have gone wrong before</b></summary>


- **Sidebar would not stop shrinking — set `width: 240px` and watched it collapse to ~140px at narrower viewports.**

  Default `flex-shrink: 1` was eating it.

  **Fix:** `flex: 0 0 240px` on `.app-sidebar`, not bare `width`. See the TICKET-ADV098 reference.

- **EventSource `onerror` reconnected in a tight loop — DDoS'd the dev server.**

  EventSource already auto-reconnects. Adding a manual reconnect on top creates a storm. The backend logs went from 2 requests/sec to 200/sec and the laptop fan hit takeoff.

  **Fix:** Remove the manual reconnect. Update the connection-status badge on `onerror`; let EventSource do its thing.

- **Flexbox shell broke at 768px because `.app-body` stayed `flex-direction: row` on mobile.**

  The sidebar held its 240px and pushed the main area off-screen.

  **Fix:** In the tablet/mobile media query, set `.app-body { flex-direction: column }` and reset the sidebar's `flex` to `0 0 auto`. See TICKET-ADV103 reference.

- **Theme toggle stored in `localStorage` but flashed light theme on load — FOUC.**

  The toggle code ran after `<body>` paint. Users saw a half-second white flash on every page load when they preferred dark.

  **Fix:** Move the "read theme + apply to `<html>`" snippet into a blocking `<script>` in `<head>`, before the stylesheet loads. Yes, render-blocking. It's 15 lines and runs in microseconds.

- **Frozen table header drifted because `position: sticky` parent had `overflow: hidden`.**

  Sticky positioning is killed by any ancestor with `overflow: hidden | scroll | auto` that isn't the intended scroll container. The team spent 20 minutes thinking sticky was broken in Chrome.

  **Fix:** Walk up the DOM tree, find the offending `overflow`, change to `auto` on the scroll container only.

- **`prefers-reduced-motion` was ignored and a tester with vestibular disorder filed a bug.**

  All animations played at full speed regardless of OS settings.

  **Fix:** One media query at the bottom of the stylesheet — see TICKET-ADV102 reference. Non-negotiable for production.

- **Sortable table sorted the DOM in place using `appendChild` in a loop — 5,000 rows took 8 seconds.**

  Sort the underlying data array, then re-render. Or use `DocumentFragment` for batched DOM ops.

  **Fix:** See TICKET-ADV106 reference — sort `rows[]`, then one `tbody.innerHTML = ...` call.

- **Promise chain swallowed an `await` rejection — SSE handler silently stopped on the first bad payload.**

  They wrapped `JSON.parse` in an async function but didn't `try/catch`, so a malformed event killed the whole subscription.

  **Fix:** Always `try/catch` inside the `onmessage` handler (see TICKET-ADV104) and log the offending payload.

- **`useCallback` on every function in their first React component — 100-line file, zero perf win, three review comments.**

  They thought it was a best practice.

  **Fix:** Memoise only after the React DevTools profiler shows an actual re-render problem. ---</details>

<details>
<summary><b>Hand-off to Day 8</b></summary>


By end-of-day each team should have:

- [ ] `static-dashboard/dashboard.html` rendering with the Flexbox
  shell (header / sidebar / main / footer, no Grid).
- [ ] A working dark/light theme toggle with **no FOUC** on reload.
- [ ] CSS custom properties powering every color and spacing — no
  hardcoded `#xxxxxx` in component styles.
- [ ] Slide-in / fade-in / pulse animations wired to the trade feed
  and `alert--danger`, with `prefers-reduced-motion` honoured.
- [ ] Responsive layout: 3-col desktop, 2-col tablet, 1-col mobile.
- [ ] Live trade feed via SSE: trades prepend with a slide-in animation
  and the feed self-caps at 50 entries.
- [ ] Sortable + resizable + frozen-header trades table.
- [ ] At least one ES6+ class in the JS layer (e.g. a `TradeFeed`
  class wrapping the SSE state) — a tangible deliverable from the
  JS-Advanced theory block.
- [ ] A React project scaffolded with Vite (or CRA) containing one
  JSX component with props and one hook from the trio
  (`useMemo` / `useCallback` / `useReducer`).

**Day 8 picks up here:** the same dashboard.html gets rebuilt as a
React 19 app with Vite. Custom properties, Flexbox layout, the SSE
client, and the ES6+ class structure all carry forward — but the
imperative DOM manipulation (`document.createElement`, `appendChild`)
is replaced by JSX + hooks. The grads who understood the cascade and
the Promise machinery today will breeze through component styling
and data fetching tomorrow.

**Next:** [TrainersGuide/day8/](../day8/README.md)

</details>
