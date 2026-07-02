# TrainersGuide — Day 8: React Modules 2 & 3 — Advanced Patterns (ReconX, Advanced Track)

> **Student-facing equivalent:** [../../student-guides/day8/README.md](../../student-guides/day8/README.md)
> **Exercises:** Day 8 · TICKET-ADV111 – TICKET-ADV125 (15 hands-on exercises across the AM + PM workshop blocks). **Fast-finisher (optional):** TICKET-ADV127 — React DevTools Profiler walkthrough.
> **Theme:** React Modules 2 & 3 — Advanced Patterns. AM (Module 2): lifting state, event handling, Context API vs prop drilling, effects & hooks (useEffect lifecycle, custom hooks), render optimisation, error boundaries. PM (Module 3): HOCs, render props, compound components, code splitting, lazy loading.
> **Output location:** Everything ships into `../frontend/` (Vite + React 19 project).

Day 7 closed with React Module 1 (components, props, JSX, basic state). Day 8
is where the **real** UI for ReconX gets built. By 17:00 the grads should
have a Vite app that demonstrates Module 2 (lifted state, context, effects,
custom hooks, memoisation, error boundaries) in the AM, and Module 3 (HOCs,
render props, compound components, code splitting + lazy loading) plus RHF +
Yup forms and RTL tests in the PM. That's a lot. **Pace ruthlessly.**

---

## Day at a glance

| #    | Block | Exercises | What students produce |
|------|-------|-----------|----------------------|
| 1 | Standup + Day-7 unblock (React Module 1 leftovers) | — | Everyone on green |
| 2 | **AM Module 2 — React State, Context, Effects, Optimisation** (theory + live demo) | — | 45-min whiteboard + live-code on lifting state, Context API, useEffect lifecycle, render optimisation, error boundaries |
| 3 | **Workshop 8A — Vite setup + HOCs + Compound DataTable** | TICKET-ADV111 – TICKET-ADV114 | Vite app boots, `withAuth`, `withErrorBoundary`, `<DataTable>` |
| 4 | Coffee | — | — |
| 5 | **Workshop 8B — Custom hooks (the meat of the day)** | TICKET-ADV115 – TICKET-ADV118 | `useWebSocket`, `useTradeStream`, `useDebouncedSearch`, `useInfiniteScroll` |
| 6 | Lunch | — | — |
| 7 | **PM Module 3 — Advanced Patterns + Code Splitting** (theory + live demo) | — | Notes on HOC vs render props vs compound components, when to reach for each, `React.lazy` + `Suspense` rules |
| 8 | **Workshop 8C — Memoisation + code splitting + RHF + theme** | TICKET-ADV119 – TICKET-ADV124 | `React.memo`, `useMemo`, `useCallback`, `React.lazy` + `Suspense`, RHF + Yup trade form, `ThemeProvider` |
| 9 | **Workshop 8D — RTL test (dashboard)** | TICKET-ADV125 | One RTL test on `<Dashboard />` with `renderWithProviders` |
| 10 | Buffer / unblock / fast-finisher (TICKET-ADV127 Profiler walkthrough) | — | Teams who finished ADV125 early open React DevTools Profiler |
| 11 | End-of-day debrief | — | Day-9 preview (Context + Kafka) |

**Workshop 8 — HOC + Compound Components + Custom Hooks + RHF + Performance.**
**Total: 15 exercises in ~6.5 hours of coding** (+ 1 optional fast-finisher TICKET-ADV127). Median ~22 minutes per
exercise. Some (TICKET-ADV111, TICKET-ADV121, TICKET-ADV125) are 10-minute jobs; some (TICKET-ADV115,
TICKET-ADV123) genuinely take 40+. Watch the clock and **collapse 8D into
a paired demo** if 8B/8C overran — the RTL coverage conversation is still
worth having even if grads don't finish the test on their own.

---

## Pre-day instructor prep

The evening before Day 8:

- [ ] On a clean clone, `cd frontend && npm ci && npm run dev` — the Vite
      dev server should be on `http://localhost:5173` in under 5 seconds.
      If it isn't, fix it now: stale `node_modules/.vite` is the usual
      culprit (`rm -rf node_modules/.vite && npm run dev`).
- [ ] Install **React Developer Tools** in your demo browser (Chrome
      and Firefox both have the extension). Open the **Profiler** tab
      once on the running app — the optional fast-finisher TICKET-ADV127
      lives or dies by your fluency here.
- [ ] Have a **sample JWT** ready to paste into the auth context for the
      TICKET-ADV112 demo. Any of the dev users from the top-level README will do
      — generate one against the running backend:
      ```bash
      curl -s -X POST http://localhost:8080/api/auth/login \
        -H "Content-Type: application/json" \
        -d '{"email":"trader@db.com","password":"trader123"}' | jq -r .token
      ```
      Paste it into localStorage as `reconx.jwt` before opening the
      frontend — saves you 90 seconds during the live demo.
- [ ] Re-skim Day 7's `static-dashboard/css/style.css` — the `data-theme`
      light/dark approach there is the *same* mechanism TICKET-ADV124's
      `ThemeProvider` flips at the React layer. Tell grads explicitly:
      "the CSS already supports two themes; today we wire React to
      toggle it." Otherwise they assume TICKET-ADV124 means re-inventing the
      stylesheet.
- [ ] Have the **React 19 docs** for `useMemo`, `useCallback`, `React.memo`,
      `React.lazy`, and `Profiler` pinned in browser tabs. You will be
      asked "when *exactly* does this re-render" 10 times today.
- [ ] Open this trainer README + the student Day-8 README side-by-side.
      Acceptance criteria are in the student copy; the solutions are here.

---

## AM concept block — React Module 2 (45 min)

A 45-minute whiteboard + live-code session before any exercises open.
Don't skip this even if grads "did some React at uni" — most of them have
never lifted state, written a custom hook, or wrapped an `ErrorBoundary`
around a feature in anger.

| Topic | Demo / talking point | Time |
|---|---|---|
| State management — lifting state up, when to colocate vs lift | Show two sibling components needing the same `selectedTradeId`. Walk through pushing state to the common parent and passing it down. Contrast with the temptation to use a global store too early. | 8 min |
| Event handling — synthetic events, bubbling, delegation | Write an `onClick` on the table row vs the `<tbody>`. Show `e.target.closest('tr')`. Why this matters once we have 10k trades in TICKET-ADV118. | 7 min |
| Context API vs prop drilling | Live-code a 3-level prop chain, then refactor to `createContext` + `useContext`. Tell them: context is a *cure for prop drilling*, not a substitute for state management. TICKET-ADV124's `ThemeProvider` is the canonical case. | 8 min |
| Effects & hooks — `useEffect` lifecycle, deps array, cleanup, custom hooks | Show the three phases (mount, update, unmount). Demo a stale-closure bug from a missing dep. Tell them: every custom hook in Workshop 8B (TICKET-ADV115–TICKET-ADV118) is just `useEffect` + `useState` with a name. | 8 min |
| Render optimisation & error boundaries | When does React re-render? (state change, parent re-render, context change). Walk through `React.memo`, `useMemo`, `useCallback` at the conceptual level — Workshop 8C (TICKET-ADV119–TICKET-ADV121) is the hands-on. Then introduce class `ErrorBoundary` as the only place in modern React you still write `class` — TICKET-ADV113 will implement one. | 7 min |
| Quick framing for PM | Tell them: AM = "the building blocks of any React feature". PM = "the patterns that make those features reusable across the whole app" (HOC, render props, compound components, lazy loading). | 4 min |

**At 10:00, Workshop 8A opens.** Keep the React Module 2 material on the
whiteboard all day — you'll point back at it during every exercise.

---

## Workshop 8A — Vite setup + HOCs + Compound DataTable (75 min)

### TICKET-ADV111 — Vite setup with path aliases

**What students produce:** A working `vite.config.js` with `@`,
`@components`, `@hooks`, `@services` aliases so imports stop looking like
`../../../components/StatCard`.

**Common student blockers:**
- They edit `vite.config.js` but the IDE still shows red squigglies on
  `@/components/...`. They need to add a matching `jsconfig.json` for
  VS Code path resolution.
- They forget `fileURLToPath` and try to use `__dirname` — it's `undefined`
  in ESM-mode Vite configs.
- They alias `@` to `./src` but also leave `baseUrl: "."` somewhere and
  get double-resolved paths.

**Unblocking ladder:**
1. **Nudge:** "What does the error message say is undefined?"
2. **Hint:** "Vite config runs as ES Modules. What's the ESM equivalent of `__dirname`?"
3. **Reveal:** Show `fileURLToPath(new URL('./src', import.meta.url))`.
   Then add the matching `jsconfig.json`.

<details>
<summary>▶ Reference solution — TICKET-ADV111 vite.config.js + jsconfig.json</summary>

```js
// frontend/vite.config.js
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@':            fileURLToPath(new URL('./src',            import.meta.url)),
      '@components':  fileURLToPath(new URL('./src/components', import.meta.url)),
      '@hooks':       fileURLToPath(new URL('./src/hooks',      import.meta.url)),
      '@services':    fileURLToPath(new URL('./src/services',   import.meta.url)),
      '@context':     fileURLToPath(new URL('./src/context',    import.meta.url)),
      '@pages':       fileURLToPath(new URL('./src/pages',      import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api':    'http://localhost:8080',
      '/stream': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
  },
});
```

```json
// frontend/jsconfig.json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*":           ["src/*"],
      "@components/*": ["src/components/*"],
      "@hooks/*":      ["src/hooks/*"],
      "@services/*":   ["src/services/*"],
      "@context/*":    ["src/context/*"],
      "@pages/*":      ["src/pages/*"]
    }
  },
  "include": ["src/**/*"]
}
```

</details>

**Talking point:** path aliases aren't "nice to have" — once a team has
20 components in 4 folders, `../../..` imports lie about the dependency
graph. The alias forces an explicit module boundary.

**▶ Run the project — verify TICKET-ADV111 end-to-end**

Boot the Vite dev server and confirm aliases resolve in both runtime and the IDE.

```bash
cd frontend && npm install
npm run dev
```

**Observe:**

- Terminal logs `VITE vX.Y.Z  ready in <N>ms` and `Local: http://localhost:5173/`.
- Opening `http://localhost:5173` loads the app shell without console errors.
- Failure signal: `Failed to resolve import "@components/..."` means an alias is wired in `vite.config.js` but missing from `jsconfig.json` (or vice versa).

---

### TICKET-ADV112 — HOC: `withAuth(Component)`

**What students produce:** A higher-order component that wraps any page
component and redirects to `/login` if the user isn't authenticated.

**Common student blockers:**
- They write `withAuth` as a hook (`useAuth`) and don't understand why
  the exercise says HOC. Both *work*; the point is to teach the HOC pattern.
- They put the redirect inside `useEffect` and the wrapped component
  still renders for one paint before the redirect fires.
- They forget to forward `props` to the wrapped component.

**Unblocking ladder:**
1. **Nudge:** "What does the user see for the split-second before
   `useEffect` runs?"
2. **Hint:** "Can you do the redirect *during* render instead of after?"
3. **Reveal:** `if (!user) return <Navigate to="/login" replace />;`
   placed before the wrapped component renders. No effect needed.

<details>
<summary>▶ Reference solution — TICKET-ADV112 withAuth HOC</summary>

```jsx
// frontend/src/hocs/withAuth.jsx
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@context/AuthContext';

export function withAuth(Component) {
  function WithAuth(props) {
    const { user, isLoading } = useAuth();
    const location = useLocation();

    if (isLoading) return <div className="page-spinner">Loading…</div>;

    if (!user) {
      // `replace` so back-button doesn't put us back on the protected page
      return <Navigate to="/login" replace state={{ from: location.pathname }} />;
    }

    return <Component {...props} />;
  }

  // Helpful in React DevTools — otherwise everything shows as <WithAuth />
  WithAuth.displayName = `withAuth(${Component.displayName || Component.name || 'Component'})`;
  return WithAuth;
}
```

Usage:
```jsx
// frontend/src/pages/Dashboard.jsx
import { withAuth } from '@/hocs/withAuth';
function Dashboard() { /* ... */ }
export default withAuth(Dashboard);
```

</details>

**Talking point:** the `displayName` line is the small detail that
separates a junior HOC from a senior one. Without it, the React DevTools
component tree is all `<WithAuth>` — debugging hell.

**▶ Run the project — verify TICKET-ADV112 end-to-end**

Dev server already running; clear any stored JWT and visit a `withAuth`-wrapped route.

```bash
# In the browser DevTools console:
localStorage.removeItem('jwt')
# then navigate to a protected route, e.g. /dashboard
```

**Observe:**

- URL flips to `/login` immediately; the protected page never paints.
- After logging in, navigating back to the original route renders normally.
- React DevTools tree shows `withAuth(Dashboard)` as the display name (not `WithAuth`).

---

### TICKET-ADV113 — HOC: `withErrorBoundary(Component)`

**What students produce:** A class-based ErrorBoundary wrapped as an HOC,
so any page can be made crash-resilient by re-exporting it.

**Common student blockers:**
- They try to write the ErrorBoundary as a functional component. **They
  can't.** Error boundaries *must* be class components (no hook
  equivalent in React 19; `react-error-boundary` exists but the exercise
  is about teaching the primitive).
- They catch the error but the same error fires every render — they
  forgot to gate the children behind `state.hasError`.
- They put `console.error` in `componentDidCatch` but never any UI —
  Sentry/logging hook left as TODO is fine, but the fallback must render.

**Unblocking ladder:**
1. **Nudge:** "What lifecycle method does React give *only* class
   components for error capture?"
2. **Hint:** "You need two: one to *set* state when an error happens,
   and one to *render* the fallback when state says so."
3. **Reveal:** `static getDerivedStateFromError(error)` returns the
   next state; `componentDidCatch(error, info)` is for side-effects
   (logging). Render switches on `state.hasError`.

<details>
<summary>▶ Reference solution — TICKET-ADV113 withErrorBoundary HOC</summary>

```jsx
// frontend/src/hocs/withErrorBoundary.jsx
import React from 'react';

class ErrorBoundary extends React.Component {
  state = { hasError: false, error: null };

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, info) {
    // In real ReconX, ship to your error tracker here.
    // eslint-disable-next-line no-console
    console.error('[ErrorBoundary]', error, info?.componentStack);
  }

  handleReset = () => this.setState({ hasError: false, error: null });

  render() {
    if (this.state.hasError) {
      const Fallback = this.props.fallback;
      if (Fallback) {
        return <Fallback error={this.state.error} onReset={this.handleReset} />;
      }
      return (
        <div role="alert" className="error-boundary">
          <h2>Something broke.</h2>
          <pre>{this.state.error?.message}</pre>
          <button onClick={this.handleReset}>Try again</button>
        </div>
      );
    }
    return this.props.children;
  }
}

export function withErrorBoundary(Component, Fallback) {
  function WithErrorBoundary(props) {
    return (
      <ErrorBoundary fallback={Fallback}>
        <Component {...props} />
      </ErrorBoundary>
    );
  }
  WithErrorBoundary.displayName = `withErrorBoundary(${Component.displayName || Component.name || 'Component'})`;
  return WithErrorBoundary;
}
```

</details>

**Talking point:** the order matters when you compose HOCs. The
ErrorBoundary should sit **above** any other HOC that might throw —
typically `withErrorBoundary(withAuth(Dashboard))`. Reversed, an auth
failure that throws won't be caught.

**▶ Run the project — verify TICKET-ADV113 end-to-end**

Force a render-time error inside a `withErrorBoundary`-wrapped child and watch the fallback take over.

```bash
# In a wrapped child component, temporarily add:  throw new Error('boom');
npm run dev
```

**Observe:**

- The fallback UI (`role="alert"` with "Try again") renders instead of a white screen.
- Browser console logs `ErrorBoundary caught Error: boom ...` from `componentDidCatch`.
- Clicking "Try again" remounts the children; a second thrown error is caught again (boundary is reusable, not single-shot).

---

### TICKET-ADV114 — Compound Component: `<DataTable>`

**What students produce:** A `<DataTable>` that exposes `<DataTable.Header>`,
`<DataTable.Body>`, `<DataTable.Pagination>` as sub-components with shared
state (sort key, current page) via internal Context.

**Common student blockers:**
- They make `DataTable` accept `header`, `body`, `pagination` as props
  instead of children. Works, but isn't the compound pattern — push back.
- They use prop-drilling between Header / Body / Pagination instead of
  Context. Code review: "what happens when grandchild needs `sortKey`?"
- They forget to attach the sub-components — `DataTable.Header` is
  undefined.

**Unblocking ladder:**
1. **Nudge:** "How does `<select>` share state with its `<option>`
   children? You don't pass `value` to every option."
2. **Hint:** "Internal Context, scoped to one component, is fine —
   you're not making it globally available."
3. **Reveal:** show the `DataTableContext.Provider` wrapping
   `{children}` in the parent, and `useContext(DataTableContext)` in
   each child.

<details>
<summary>▶ Reference solution — TICKET-ADV114 compound DataTable</summary>

```jsx
// frontend/src/components/DataTable/DataTable.jsx
import { createContext, useContext, useMemo, useState } from 'react';

const DataTableContext = createContext(null);

function useDataTable() {
  const ctx = useContext(DataTableContext);
  if (!ctx) {
    throw new Error('DataTable.* must be used inside <DataTable>');
  }
  return ctx;
}

export function DataTable({ data, pageSize = 10, children }) {
  const [page, setPage] = useState(0);
  const [sortKey, setSortKey] = useState(null);
  const [sortDir, setSortDir] = useState('asc');

  const sorted = useMemo(() => {
    if (!sortKey) return data;
    const copy = [...data];
    copy.sort((a, b) => {
      const av = a[sortKey], bv = b[sortKey];
      if (av === bv) return 0;
      const cmp = av > bv ? 1 : -1;
      return sortDir === 'asc' ? cmp : -cmp;
    });
    return copy;
  }, [data, sortKey, sortDir]);

  const paged = useMemo(() => {
    const start = page * pageSize;
    return sorted.slice(start, start + pageSize);
  }, [sorted, page, pageSize]);

  const value = useMemo(() => ({
    rows: paged, totalRows: data.length,
    page, pageSize, setPage,
    sortKey, sortDir, setSortKey, setSortDir,
  }), [paged, data.length, page, pageSize, sortKey, sortDir]);

  return (
    <DataTableContext.Provider value={value}>
      <table className="data-table">{children}</table>
    </DataTableContext.Provider>
  );
}

function Header({ columns }) {
  const { sortKey, sortDir, setSortKey, setSortDir } = useDataTable();
  const toggle = (key) => {
    if (sortKey === key) setSortDir(sortDir === 'asc' ? 'desc' : 'asc');
    else { setSortKey(key); setSortDir('asc'); }
  };
  return (
    <thead>
      <tr>
        {columns.map((c) => (
          <th key={c.key} onClick={() => toggle(c.key)}>
            {c.label}{sortKey === c.key ? (sortDir === 'asc' ? ' ▲' : ' ▼') : ''}
          </th>
        ))}
      </tr>
    </thead>
  );
}

function Body({ renderRow }) {
  const { rows } = useDataTable();
  return <tbody>{rows.map(renderRow)}</tbody>;
}

function Pagination() {
  const { page, pageSize, totalRows, setPage } = useDataTable();
  const lastPage = Math.max(0, Math.ceil(totalRows / pageSize) - 1);
  return (
    <tfoot>
      <tr><td colSpan={99}>
        <button disabled={page === 0} onClick={() => setPage(page - 1)}>Prev</button>
        <span> Page {page + 1} of {lastPage + 1} </span>
        <button disabled={page >= lastPage} onClick={() => setPage(page + 1)}>Next</button>
      </td></tr>
    </tfoot>
  );
}

DataTable.Header     = Header;
DataTable.Body       = Body;
DataTable.Pagination = Pagination;
```

Usage:
```jsx
<DataTable data={trades} pageSize={20}>
  <DataTable.Header columns={[
    { key: 'tradeRef',    label: 'Ref' },
    { key: 'instrument',  label: 'Instrument' },
    { key: 'quantity',    label: 'Qty' },
    { key: 'price',       label: 'Price' },
  ]} />
  <DataTable.Body renderRow={(t) => <TradeRow key={t.id} trade={t} />} />
  <DataTable.Pagination />
</DataTable>
```

</details>

**Talking point:** compound components win when the *parent* owns state
the children need, but the children need to be *composable* in any order
or omitted. You can drop `<DataTable.Pagination />` and the rest still
works. That's the test.

**▶ Run the project — verify TICKET-ADV114 end-to-end**

Render the compound `<DataTable>` on a trades page and exercise sort + pagination.

```bash
npm run dev
# navigate to the page using <DataTable data={trades}>...</DataTable>
```

**Observe:**

- Clicking a header cell reorders the body rows; clicking again flips ascending/descending.
- The `aria-sort` (or active-class) on the clicked column flips to match the direction.
- Removing `<DataTable.Pagination />` still leaves a working table — sub-components are optional.
- Failure signal: a console error from `useDataTable()` ("used outside `<DataTable>`") means a sub-component is rendered outside the provider.

---

## Workshop 8B — Custom hooks (90 min)

The hardest workshop of the day. Pair-program if a team is stuck on TICKET-ADV115
— don't let one grad debug WebSocket reconnection alone.

### TICKET-ADV115 — `useWebSocket(url, options)`

**What students produce:** A hook that opens a WebSocket, exposes
`{ data, status, send }`, and auto-reconnects with exponential backoff
on close.

**Common student blockers:**
- The classic: WebSocket recreated every render because they listed
  it as a dep, infinite reconnect loop. Open the Network tab and
  count — it'll be 60+ connections per second.
- They put `new WebSocket(...)` outside `useEffect` — runs on the
  server during SSR (not an issue here yet but bad habit).
- They forget to clean up — open the page twice, leak two sockets.
- Backoff implemented as `setTimeout(reconnect, 1000)` always — no
  growth, hammers the server when it's down.

**Unblocking ladder:**
1. **Nudge:** "Count the open sockets in the Network tab. What's
   triggering each one?"
2. **Hint:** "What's in your `useEffect` deps array? Does that value
   change on every render?"
3. **Reveal:** Walk through: `url` is the only stable dep; the socket
   itself must live in a `useRef`, not a dep. Backoff is
   `Math.min(maxDelay, baseDelay * 2 ** retries)`.

<details>
<summary>▶ Reference solution — TICKET-ADV115 useWebSocket</summary>

```jsx
// frontend/src/hooks/useWebSocket.js
import { useEffect, useRef, useState, useCallback } from 'react';

const DEFAULTS = { reconnect: true, maxRetries: 5, baseDelay: 1000, maxDelay: 30_000 };

export function useWebSocket(url, options = {}) {
  const opts = { ...DEFAULTS, ...options };
  const [data,   setData]   = useState(null);
  const [status, setStatus] = useState('idle'); // idle | connecting | open | closing | closed | error
  const wsRef       = useRef(null);
  const retriesRef  = useRef(0);
  const timerRef    = useRef(null);
  const shouldStop  = useRef(false);

  const connect = useCallback(() => {
    setStatus('connecting');
    const ws = new WebSocket(url);
    wsRef.current = ws;

    ws.onopen = () => {
      retriesRef.current = 0;
      setStatus('open');
    };
    ws.onmessage = (e) => {
      try { setData(JSON.parse(e.data)); }
      catch { setData(e.data); }
    };
    ws.onerror = () => setStatus('error');
    ws.onclose = () => {
      setStatus('closed');
      if (shouldStop.current || !opts.reconnect) return;
      if (retriesRef.current >= opts.maxRetries) return;
      const delay = Math.min(opts.maxDelay, opts.baseDelay * 2 ** retriesRef.current);
      retriesRef.current += 1;
      timerRef.current = setTimeout(connect, delay);
    };
  }, [url, opts.reconnect, opts.maxRetries, opts.baseDelay, opts.maxDelay]);

  useEffect(() => {
    shouldStop.current = false;
    connect();
    return () => {
      shouldStop.current = true;
      if (timerRef.current) clearTimeout(timerRef.current);
      if (wsRef.current && wsRef.current.readyState <= 1) wsRef.current.close();
    };
  }, [connect]);

  const send = useCallback((payload) => {
    const ws = wsRef.current;
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(typeof payload === 'string' ? payload : JSON.stringify(payload));
    }
  }, []);

  return { data, status, send };
}
```

</details>

**Talking point:** every grad will get the deps array wrong at least
once today. That's *fine* — it's the lesson. Run the broken version
with the Network tab visible; count the sockets; fix; count again.

**▶ Run the project — verify TICKET-ADV115 end-to-end**

Backend must be running so the WebSocket endpoint exists; then mount a `useWebSocket` consumer.

```bash
# Terminal 1 — backend
./mvnw spring-boot:run
# Terminal 2 — frontend
cd frontend && npm run dev
```

**Observe:**

- DevTools Network → WS tab shows exactly one socket open on mount; status flips to `open`.
- Navigating away (unmount) closes the socket — no growing connection count.
- Forcing the backend to drop the socket triggers a reconnect after ~500ms, then ~1s, then ~2s (exponential backoff), capped by `maxRetries`.
- Failure signal: dozens of sockets opening per second means the deps array or refs are wrong — close the tab before your laptop melts.

---

### TICKET-ADV116 — `useTradeStream()` with SSE

**What students produce:** A hook that subscribes to the existing Day-7
SSE endpoint, returns `{ trades, isConnected }`, and accumulates events
into a local array.

**Common student blockers:**
- They use `fetch` with a ReadableStream and reinvent SSE — don't let
  them. The browser has `EventSource`.
- They append to `trades` array directly (mutating) and React never
  re-renders.
- They unbounded-accumulate — array grows to 100,000 entries by lunch.
  Cap it.

**Unblocking ladder:**
1. **Nudge:** "Browser has a built-in for SSE — what is it?"
2. **Hint:** "When you do `trades.push(x)` then `setTrades(trades)`,
   what's the reference identity of the argument? Is it new?"
3. **Reveal:** `setTrades(prev => [event, ...prev].slice(0, 200))` —
   immutable update, capped at 200.

<details>
<summary>▶ Reference solution — TICKET-ADV116 useTradeStream</summary>

```jsx
// frontend/src/hooks/useTradeStream.js
import { useEffect, useState } from 'react';

const MAX_BUFFER = 200;

export function useTradeStream(endpoint = '/stream/trades') {
  const [trades, setTrades] = useState([]);
  const [isConnected, setIsConnected] = useState(false);

  useEffect(() => {
    const es = new EventSource(endpoint);

    es.onopen    = () => setIsConnected(true);
    es.onerror   = () => setIsConnected(false);
    es.onmessage = (e) => {
      try {
        const trade = JSON.parse(e.data);
        setTrades((prev) => [trade, ...prev].slice(0, MAX_BUFFER));
      } catch { /* ignore malformed frames */ }
    };

    // Server may also send named events
    es.addEventListener('trade-matched', (e) => {
      const t = JSON.parse(e.data);
      setTrades((prev) => prev.map((x) => x.id === t.id ? { ...x, status: 'MATCHED' } : x));
    });

    return () => es.close();
  }, [endpoint]);

  return { trades, isConnected };
}
```

</details>

**Talking point:** SSE vs WebSocket — one-way (server → client) vs
two-way. ReconX uses SSE for the trade feed (only server pushes), and
will use WebSocket on Day 9 for the chat-style break-resolution
collaboration. Picking the cheaper primitive matters.

**▶ Run the project — verify TICKET-ADV116 end-to-end**

Backend must be emitting SSE on `/api/v1/trades/stream`; then mount the Dashboard.

```bash
# Terminal 1 — backend
./mvnw spring-boot:run
# Terminal 2 — frontend
cd frontend && npm run dev
```

**Observe:**

- DevTools Network → EventStream tab shows an open `EventSource` against `/api/v1/trades/stream` with `text/event-stream` content-type.
- New trades stream in and prepend to the array; `isConnected` flips to `true` after `onopen`.
- The buffer never exceeds ~200 entries — oldest trades fall off as new ones arrive.
- Failure signal: connection thrash (open/close in a loop) means the effect deps are unstable.

---

### TICKET-ADV117 — `useDebouncedSearch(query, delay)`

**What students produce:** A hook that returns the *debounced* version
of a query string — only updates `delay` ms after the user stops typing.

**Common student blockers:**
- They use `useState` + `setTimeout` but forget to `clearTimeout` on
  re-render. Search fires for *every* keystroke after the delay.
- They debounce the API call instead of the value — fine, but the
  exercise asks for a *reusable* debounced value.

**Unblocking ladder:**
1. **Nudge:** "What does `useEffect` return, and when does that return
   value run?"
2. **Hint:** "If you `clearTimeout` in the cleanup, what does that do
   when `query` changes mid-flight?"
3. **Reveal:** the cleanup cancels the previous timer; only the latest
   keystroke survives the debounce window.

<details>
<summary>▶ Reference solution — TICKET-ADV117 useDebouncedSearch</summary>

```jsx
// frontend/src/hooks/useDebouncedSearch.js
import { useEffect, useState } from 'react';

export function useDebouncedSearch(query, delay = 300) {
  const [debounced, setDebounced] = useState(query);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(query), delay);
    return () => clearTimeout(timer);  // cancels superseded keystrokes
  }, [query, delay]);

  return debounced;
}
```

Usage:
```jsx
const [q, setQ] = useState('');
const debouncedQ = useDebouncedSearch(q, 300);
useEffect(() => { fetch(`/api/trades/search?q=${debouncedQ}`); }, [debouncedQ]);
```

</details>

**Talking point:** debouncing in the *component* is a lie — every
component now reinvents it. Extracting it into a hook is *the* hook
use-case. Compare LOC of the hook (10) vs the inline equivalent (also
10) — the hook wins on *reuse*, not on initial LOC.

**▶ Run the project — verify TICKET-ADV117 end-to-end**

Mount a search input that calls the hook, then type rapidly while watching the Network tab.

```bash
cd frontend && npm run dev
# focus the search input and type "AAPL" quickly, then pause
```

**Observe:**

- Exactly one API call fires ~300ms after the last keystroke — not one per character.
- Changing `delay` at runtime adjusts the window without leaking the previous timer.
- Failure signal: a request per keystroke means the cleanup `clearTimeout` is missing or the effect deps are wrong.

---

### TICKET-ADV118 — `useInfiniteScroll(loadMore)`

**What students produce:** A hook that returns a `sentinelRef`; when
that ref scrolls into view, `loadMore()` is called.

**Common student blockers:**
- They use the scroll event (`window.onscroll`) and tank performance.
  IntersectionObserver is the modern primitive.
- They call `loadMore` from the observer callback on every fire —
  including when *leaving* the viewport. Guard on `entry.isIntersecting`.
- They forget to disconnect the observer on unmount.

**Unblocking ladder:**
1. **Nudge:** "Is `onscroll` how you'd build this for production with
   10k rows?"
2. **Hint:** "What did we cover in the AM block about browser
   primitives for visibility?"
3. **Reveal:** `IntersectionObserver` watches a sentinel `<div ref={...}>`
   placed at the bottom of the list.

<details>
<summary>▶ Reference solution — TICKET-ADV118 useInfiniteScroll</summary>

```jsx
// frontend/src/hooks/useInfiniteScroll.js
import { useEffect, useRef } from 'react';

export function useInfiniteScroll(loadMore, { rootMargin = '200px' } = {}) {
  const sentinelRef = useRef(null);
  const loadMoreRef = useRef(loadMore);

  // Keep latest callback without re-creating the observer
  useEffect(() => { loadMoreRef.current = loadMore; }, [loadMore]);

  useEffect(() => {
    const node = sentinelRef.current;
    if (!node) return;

    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) loadMoreRef.current();
    }, { rootMargin });

    observer.observe(node);
    return () => observer.disconnect();
  }, [rootMargin]);

  return sentinelRef;
}
```

Usage:
```jsx
const sentinelRef = useInfiniteScroll(() => fetchNextPage());
return (
  <>
    {trades.map((t) => <TradeRow key={t.id} trade={t} />)}
    <div ref={sentinelRef} style={{ height: 1 }} />
  </>
);
```

</details>

**Talking point:** the `loadMoreRef` indirection is a common pattern —
the observer should be created *once*, but the callback might change.
This is the same shape as the WebSocket hook's `wsRef` trick.

**▶ Run the project — verify TICKET-ADV118 end-to-end**

Render a paginated list that uses the hook, then scroll to the bottom.

```bash
cd frontend && npm run dev
# scroll a paginated list down until the sentinel enters the viewport
```

**Observe:**

- A new page fetches automatically when the sentinel intersects the viewport — exactly once per page, not on every scroll event.
- A loading spinner is visible during each fetch; new rows append to the list.
- Navigating away tears the observer down (no memory leak across page changes).
- Failure signal: `loadMore` firing repeatedly while the sentinel is visible means the `isIntersecting` guard is missing.

---

## Workshop 8C — Memoisation + code splitting + RHF + theme (90 min)

### TICKET-ADV119 — `React.memo` on `<TradeRow />`

**What students produce:** A memoised trade row that only re-renders
when the trade's `id` or `status` changes.

**Common student blockers:**
- They wrap the row in `React.memo()` without thinking and a parent
  passes a new `onClick` every render — memo is bypassed because the
  prop changed by reference. Lead-in to TICKET-ADV121.
- They write a custom equality function that returns the wrong sign
  (`true` means "props are equal — skip render"; some assume it means
  "re-render").

**Unblocking ladder:**
1. **Nudge:** "What does your Profiler show? Is the row re-rendering?"
2. **Hint:** "Check the props in DevTools — is `onClick` the same
   function reference both renders?"
3. **Reveal:** memo + custom equality on the *stable* fields; then
   `useCallback` on the parent's handler (TICKET-ADV121).

<details>
<summary>▶ Reference solution — TICKET-ADV119 memoised TradeRow</summary>

```jsx
// frontend/src/components/TradeRow.jsx
import React from 'react';

function TradeRowImpl({ trade, onClick }) {
  return (
    <tr onClick={() => onClick(trade.id)}>
      <td>{trade.tradeRef}</td>
      <td>{trade.instrument}</td>
      <td>{trade.quantity}</td>
      <td>{trade.price}</td>
      <td><span className={`status-pill ${trade.status.toLowerCase()}`}>{trade.status}</span></td>
    </tr>
  );
}

// Custom equality — only the fields we actually render
function areEqual(prev, next) {
  return prev.trade.id      === next.trade.id
      && prev.trade.status  === next.trade.status
      && prev.trade.price   === next.trade.price
      && prev.onClick       === next.onClick;
}

export const TradeRow = React.memo(TradeRowImpl, areEqual);
```

</details>

**Talking point:** the second argument to `React.memo` is the *bail-out
predicate*. Default is shallow equality. Override only when shallow is
wrong — which is *most* trade-list rows, because some props (a `tags`
array) are reference-unstable.

**▶ Run the project — verify TICKET-ADV119 end-to-end**

Render a Trades page populated with `<TradeRow />` and trigger an unrelated parent state change while watching the Profiler.

```bash
cd frontend && npm run dev
# open React DevTools → Profiler, record, toggle some unrelated state (e.g. a filter dropdown), stop
```

**Observe:**

- Existing `<TradeRow>` instances do **not** appear in the commit chart for the unrelated update — `areEqual` returned true.
- Mutating a single trade's `status` or `price` re-renders only that row.
- Until TICKET-ADV121 wraps the parent `onClick` in `useCallback`, the row will still re-render with reason `props changed: onClick` — that is the expected pre-fix state.

---

### TICKET-ADV120 — `useMemo` for portfolio value + P&L

**What students produce:** Expensive aggregations (portfolio value, P&L
summary) wrapped in `useMemo` keyed on the trades array.

**Common student blockers:**
- They wrap *every* derived value in `useMemo`, including
  `const total = a + b`. Pointless — overhead > savings.
- They forget the deps array and the value is computed once and never
  again — looks fine until trades update.
- They include unstable deps (e.g. a fresh object every render) and
  memo never hits.

**Unblocking ladder:**
1. **Nudge:** "How long does the calculation take? Use `console.time`."
2. **Hint:** "useMemo is a cache. What's the cache key? When does it
   miss?"
3. **Reveal:** show the React docs' rule: "memoise when the calc is
   expensive *or* when the result identity matters for downstream
   memo."

<details>
<summary>▶ Reference solution — TICKET-ADV120 useMemo aggregations</summary>

```jsx
// frontend/src/pages/Dashboard.jsx
import { useMemo } from 'react';

function Dashboard({ trades }) {
  const portfolioValue = useMemo(
    () => trades.reduce((sum, t) => sum + t.quantity * t.price, 0),
    [trades],
  );

  const pnlSummary = useMemo(() => {
    const matched   = trades.filter((t) => t.status === 'MATCHED');
    const unmatched = trades.filter((t) => t.status === 'UNMATCHED');
    const disputed  = trades.filter((t) => t.status === 'DISPUTED');
    return {
      matchedCount:   matched.length,
      unmatchedCount: unmatched.length,
      disputedCount:  disputed.length,
      matchedValue:   matched.reduce((s, t) => s + t.quantity * t.price, 0),
    };
  }, [trades]);

  return (
    <>
      <StatCard label="Portfolio value"   value={portfolioValue} />
      <StatCard label="Matched trades"    value={pnlSummary.matchedCount} />
      <StatCard label="Unmatched trades"  value={pnlSummary.unmatchedCount} />
      <StatCard label="Disputed trades"   value={pnlSummary.disputedCount} />
    </>
  );
}
```

</details>

**Talking point:** `useMemo` is a *hint*. React reserves the right to
throw away the cache (e.g. under memory pressure). Don't rely on it
for correctness — only for performance.

**▶ Run the project — verify TICKET-ADV120 end-to-end**

Backend must be emitting SSE for the live trade stream; then visit the Dashboard while authenticated.

```bash
# Terminal 1 — backend
./mvnw spring-boot:run
# Terminal 2 — frontend
cd frontend && npm run dev
# Log in, then navigate to /dashboard
```

**Observe:**

- Live trades stream into the StatCard values; portfolio value updates as new trades arrive.
- React DevTools Profiler shows neither `useMemo` recomputing on unrelated state changes (e.g. opening a side panel).
- The page is `withAuth`-gated — a logged-out visit redirects to `/login`.
- Failure signal: memos recomputing on every render means an unstable dep (a new array literal) sneaked into the deps array.

---

### TICKET-ADV121 — `useCallback` on event handlers passed to memoised children

**What students produce:** Handlers passed to `<TradeRow />` wrapped in
`useCallback` so the memo in TICKET-ADV119 actually fires.

**Common student blockers:**
- They use `useCallback` everywhere, including handlers that aren't
  passed to memoised children. Adds noise, no gain.
- Empty deps array on a callback that closes over `state` — handler
  uses stale state forever.

**Unblocking ladder:**
1. **Nudge:** "Open Profiler with 'Record why each component rendered'
   on. What does it say for `<TradeRow>`?"
2. **Hint:** "The reason is `props changed: onClick`. Why did `onClick`
   change?"
3. **Reveal:** `useCallback(fn, deps)` returns the same function
   reference as long as deps don't change.

<details>
<summary>▶ Reference solution — TICKET-ADV121 useCallback handler</summary>

```jsx
// frontend/src/pages/Trades.jsx
import { useCallback, useState } from 'react';

function Trades({ trades }) {
  const [selectedId, setSelectedId] = useState(null);

  // Reference-stable across renders — onClick prop on <TradeRow> won't change
  const handleSelect = useCallback((id) => setSelectedId(id), []);

  return (
    <DataTable data={trades}>
      <DataTable.Header columns={cols} />
      <DataTable.Body
        renderRow={(t) => <TradeRow key={t.id} trade={t} onClick={handleSelect} />}
      />
    </DataTable>
  );
}
```

</details>

**Talking point:** `useCallback` and `useMemo` are *the same primitive*
under the hood — `useCallback(fn, d)` is `useMemo(() => fn, d)`. Show
the React docs page that says exactly this.

**▶ Run the project — verify TICKET-ADV121 end-to-end**

Open the React DevTools Profiler with "Record why each component rendered" enabled, then trigger an unrelated state change.

```bash
cd frontend && npm run dev
# React DevTools → Profiler → Settings cog → "Record why each component rendered" ON
# Record → flip an unrelated piece of state → Stop
```

**Observe:**

- `<TradeRow>` no longer appears in the commit chart with reason `props changed: onClick` — the memo from TICKET-ADV119 now actually holds.
- Total render count for rows drops to zero for the unrelated update.
- Failure signal: rows still re-rendering means an inline arrow somewhere in the parent slipped past — re-grep for `onClick={(` in the Trades page.

---

### TICKET-ADV122 — `React.lazy` + `Suspense` for route-based code splitting

**What students produce:** Each page lazily imported; Suspense fallback
in the router shows a skeleton during load.

**Common student blockers:**
- No Suspense boundary — error: "A React component suspended while
  responding to synchronous input."
- They lazy-load the route component *and* the layout — layout flashes
  on every navigation.
- They put the fallback as `null` — white screen for 200ms.

**Unblocking ladder:**
1. **Nudge:** "React's error message names the missing piece — read
   it."
2. **Hint:** "Suspense is a *boundary*; everything below it suspends.
   Where should the boundary be?"
3. **Reveal:** put `<Suspense>` *inside* the layout, *around* the
   `<Routes>` — layout stays mounted, only the page suspends.

<details>
<summary>▶ Reference solution — TICKET-ADV122 lazy + Suspense</summary>

```jsx
// frontend/src/App.jsx
import { lazy, Suspense } from 'react';
import { Routes, Route } from 'react-router-dom';
import { Layout } from '@components/Layout';
import { PageSkeleton } from '@components/PageSkeleton';

const Dashboard = lazy(() => import('@pages/Dashboard'));
const Trades    = lazy(() => import('@pages/Trades'));
const Recon     = lazy(() => import('@pages/Recon'));
const AddTrade  = lazy(() => import('@pages/AddTrade'));
const Audit     = lazy(() => import('@pages/Audit'));

export default function App() {
  return (
    <Layout>
      <Suspense fallback={<PageSkeleton />}>
        <Routes>
          <Route path="/"          element={<Dashboard />} />
          <Route path="/trades"    element={<Trades />} />
          <Route path="/recon"     element={<Recon />} />
          <Route path="/trades/new" element={<AddTrade />} />
          <Route path="/audit"     element={<Audit />} />
        </Routes>
      </Suspense>
    </Layout>
  );
}
```

</details>

**Talking point:** open the Network tab; navigate between routes; show
the per-route `chunk-XXXX.js` downloading on first visit and cached
thereafter. That's the win.

**▶ Run the project — verify TICKET-ADV122 end-to-end**

With the dev server running, open the Network tab and navigate between routes.

```bash
cd frontend && npm run dev
# Network tab → JS filter → click between /, /trades, /trades/new, /login
```

**Observe:**

- First visit to each route downloads a per-route `chunk-*.js` (or `assets/*.js` in build); subsequent visits are served from cache (HTTP `(disk cache)` / `(memory cache)`).
- Layout (nav, header) stays mounted between navigations — only the inner page area shows the Suspense fallback.
- Failure signal: `Error: A React component suspended while rendering, but no fallback UI was specified` means `<Suspense>` is missing or placed below where it's needed.

---

### TICKET-ADV123 — Trade entry form: RHF + Yup

**What students produce:** A trade entry form using `react-hook-form` +
`@hookform/resolvers/yup` + `yup`, with schema validation for trade ref,
quantity, price, and date.

**Common student blockers:**
- `register('field')` called inside a render callback — re-registers
  every render and inputs reset constantly. Hoist the destructure.
- They use `defaultValues: { quantity: '' }` then validate as a number —
  `''` is not a number, validation messages weird.
- They forget the `mode: 'onBlur'` and the form screams red on every
  keystroke.

**Unblocking ladder:**
1. **Nudge:** "Open the input element in DevTools. Does the `name`
   attribute keep changing?"
2. **Hint:** "What's the difference between calling `register('q')` in
   the JSX directly vs in a `.map` callback?"
3. **Reveal:** destructure `register` once, spread into the JSX:
   `<input {...register('quantity')} />`.

<details>
<summary>▶ Reference solution — TICKET-ADV123 RHF + Yup trade form</summary>

```jsx
// frontend/src/pages/AddTrade.jsx
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { apiService } from '@services/apiService';

const TRADE_REF_PATTERN = /^[A-Z]{3}-\d{8}-\d{4}$/;

const schema = yup.object({
  tradeRef:  yup.string()
                .matches(TRADE_REF_PATTERN, 'Format: XXX-YYYYMMDD-NNNN')
                .required('Trade ref is required'),
  instrument: yup.string().required('Instrument is required'),
  quantity:  yup.number().typeError('Quantity must be a number')
                .positive('Quantity must be positive').required(),
  price:     yup.number().typeError('Price must be a number')
                .positive('Price must be positive').required(),
  tradeDate: yup.date().typeError('Pick a date')
                .max(new Date(), 'Trade date cannot be in the future').required(),
});

export default function AddTrade() {
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm({
    resolver: yupResolver(schema),
    mode: 'onBlur',
    defaultValues: { tradeRef: '', instrument: '', quantity: '', price: '', tradeDate: '' },
  });

  const onSubmit = async (data) => {
    await apiService.createTrade(data);
    reset();
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <label> Trade ref
        <input {...register('tradeRef')} aria-invalid={!!errors.tradeRef} />
        {errors.tradeRef && <span role="alert">{errors.tradeRef.message}</span>}
      </label>

      <label> Instrument
        <input {...register('instrument')} aria-invalid={!!errors.instrument} />
        {errors.instrument && <span role="alert">{errors.instrument.message}</span>}
      </label>

      <label> Quantity
        <input type="number" step="0.0001" {...register('quantity')} />
        {errors.quantity && <span role="alert">{errors.quantity.message}</span>}
      </label>

      <label> Price
        <input type="number" step="0.0001" {...register('price')} />
        {errors.price && <span role="alert">{errors.price.message}</span>}
      </label>

      <label> Trade date
        <input type="date" {...register('tradeDate')} />
        {errors.tradeDate && <span role="alert">{errors.tradeDate.message}</span>}
      </label>

      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? 'Saving…' : 'Create trade'}
      </button>
    </form>
  );
}
```

</details>

**Talking point:** RHF wins because it's *uncontrolled by default* — no
re-render on every keystroke. Watch the Profiler with a controlled
Formik form vs RHF; the diff is dramatic.

**▶ Run the project — verify TICKET-ADV123 end-to-end**

Backend must be running so the POST is accepted; then submit one invalid and one valid form.

```bash
# Terminal 1 — backend
./mvnw spring-boot:run
# Terminal 2 — frontend
cd frontend && npm run dev
# log in, navigate to /trades/new
```

**Observe:**

- Submitting an empty/invalid form does **not** fire a network request; each invalid field renders its specific message in a `role="alert"` element.
- A valid submission POSTs to `/api/v1/trades` with parsed numbers (`quantity: 1000`, not `"1000"`) and returns `201`.
- React DevTools Profiler shows the form does **not** re-render per keystroke (RHF stays uncontrolled).
- Failure signal: numeric fields arriving at the API as strings means `yup.number().typeError(...)` is missing on the schema.

---

### TICKET-ADV124 — Theme context (light/dark)

**What students produce:** A `ThemeProvider` that sets
`document.documentElement.dataset.theme`, persists to localStorage,
and a `useTheme()` hook to toggle.

**Common student blockers:**
- They put the provider *inside* the ErrorBoundary HOC — the fallback
  UI can't read theme. Lift the provider to the root.
- They write to `document.body.className` instead of
  `documentElement.dataset.theme` — Day 7's CSS uses `[data-theme]`,
  so nothing changes visually.
- They forget the system-preference initial value
  (`prefers-color-scheme: dark`).

**Unblocking ladder:**
1. **Nudge:** "Open Elements tab. Is the `data-theme` attribute on
   `<html>` changing when you toggle?"
2. **Hint:** "Where in `index.html` does our Day-7 CSS look for the
   theme attribute?"
3. **Reveal:** `document.documentElement.dataset.theme = theme;` — the
   `<html>` element, not `<body>`.

<details>
<summary>▶ Reference solution — TICKET-ADV124 ThemeProvider</summary>

```jsx
// frontend/src/context/ThemeContext.jsx
import { createContext, useContext, useEffect, useState, useCallback } from 'react';

const ThemeContext = createContext(null);
const STORAGE_KEY = 'reconx.theme';

function initialTheme() {
  if (typeof window === 'undefined') return 'light';
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === 'light' || stored === 'dark') return stored;
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(initialTheme);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem(STORAGE_KEY, theme);
  }, [theme]);

  const toggle = useCallback(() => setTheme((t) => (t === 'light' ? 'dark' : 'light')), []);

  return (
    <ThemeContext.Provider value={{ theme, setTheme, toggle }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within <ThemeProvider>');
  return ctx;
}
```

Wire it in `main.jsx`:
```jsx
import { ThemeProvider } from '@context/ThemeContext';
createRoot(document.getElementById('root')).render(
  <ThemeProvider>
    <BrowserRouter><App /></BrowserRouter>
  </ThemeProvider>
);
```

</details>

**Talking point:** Day 7's CSS already uses CSS custom properties keyed
on `[data-theme="dark"]`. React doesn't own theme *values* — CSS does.
React only owns the *flip*. Don't duplicate colour tokens into JS.

**▶ Run the project — verify TICKET-ADV124 end-to-end**

Boot the app, click the theme toggle, then reload to check persistence.

```bash
cd frontend && npm run dev
# click the theme toggle, then reload the page
```

**Observe:**

- Clicking the toggle flips `<html data-theme="dark">` ↔ `<html data-theme="light">`; the existing CSS restyles the page instantly.
- `localStorage.getItem('reconx-theme')` matches the selected value; reloading preserves the theme.
- First-time visitors with `prefers-color-scheme: dark` (DevTools → Rendering → Emulate CSS media feature) start in dark mode.
- Failure signal: a thrown error from `useTheme()` saying "outside provider" means `<ThemeProvider>` isn't lifted high enough in `main.jsx`.

---

## Workshop 8D — RTL test (45 min)

One RTL test, one talking point about query priority. If a team finishes
TICKET-ADV125 with time on the clock, point them at the optional
**Fast Finishers / Stretch Goals** section below — the React DevTools
Profiler walkthrough (TICKET-ADV127) is the natural next step for anyone
who still has appetite at the end of the day.

### TICKET-ADV125 — RTL test: render `<TradeDashboard />`, assert summary cards

**What students produce:** A Vitest + RTL test that renders the dashboard
with mocked trade data and asserts the summary cards are present.

**Common student blockers:**
- They use `getByText('Portfolio value')` and the test breaks the
  moment design changes the label. Use `getByRole('heading', { name: /…/i })`.
- They forget to wrap in providers (Router, Theme, Auth) — render
  throws.
- They `import.meta.env.VITE_API_URL` in the component and the test
  blows up — needs mocking in `vitest.config` or `setupFiles`.

**Unblocking ladder:**
1. **Nudge:** "Why does the test fail with `useNavigate must be used
   within a Router`?"
2. **Hint:** "Tests render in isolation — none of your providers exist."
3. **Reveal:** factor out a `renderWithProviders(ui)` helper.

<details>
<summary>▶ Reference solution — TICKET-ADV125 RTL summary-cards test</summary>

```jsx
// frontend/src/pages/Dashboard.test.jsx
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider } from '@context/ThemeContext';
import { AuthContext }   from '@context/AuthContext';
import Dashboard         from './Dashboard';

const trades = [
  { id: 1, tradeRef: 'TRD-2026-0001', instrument: 'SAP.DE', quantity: 100, price: 250, status: 'MATCHED'   },
  { id: 2, tradeRef: 'TRD-2026-0002', instrument: 'SAP.DE', quantity: 50,  price: 251, status: 'UNMATCHED' },
];

function renderWithProviders(ui) {
  const user = { email: 'trader@db.com', role: 'TRADER' };
  return render(
    <AuthContext.Provider value={{ user, isLoading: false }}>
      <ThemeProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </ThemeProvider>
    </AuthContext.Provider>
  );
}

describe('<Dashboard />', () => {
  it('shows summary cards', () => {
    renderWithProviders(<Dashboard trades={trades} />);

    expect(screen.getByRole('heading', { name: /portfolio value/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /matched trades/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /unmatched trades/i })).toBeInTheDocument();
    // 100 * 250 + 50 * 251 = 37550
    expect(screen.getByText(/37,550/)).toBeInTheDocument();
  });
});
```

</details>

**Talking point:** query by **role** > **label** > **placeholder** >
**text** > **testId**. The further down that list you go, the more
brittle the test. Push grads to climb back up the list whenever they
reach for `getByTestId`.

**▶ Run the project — verify TICKET-ADV125 end-to-end**

Run the Vitest test in one-shot mode (Vitest watches by default).

```bash
cd frontend && npm test -- --run Dashboard
```

**Observe:**

- The `<Dashboard />` test passes green; output reports `1 passed`.
- The run completes without spinning up a live backend (providers are stubbed via `renderWithProviders`).
- Queries use roles (`getByRole('heading', ...)`) — confirmed by the test output not warning about `getByText` fallbacks.
- Failure signal: `useNavigate() may be used only in the context of a <Router>` means `MemoryRouter` is missing from the helper.

---

## Fast Finishers / Stretch Goals (Optional)

These exercises are **not** part of the main Day 8 sprint. Offer them to
teams who finish TICKET-ADV125 with time on the clock, or carry them into
the Day-9 buffer if interest is high. Nothing downstream of Day 8
depends on these — the demo and the end-state codebase work without them.

### TICKET-ADV127 — Profile with React DevTools, fix unnecessary re-renders *(fast-finisher)*

**What students produce:** A Profiler trace showing `<TradeDashboard />`
re-rendering excessively, the root cause identified, and a fix that
brings render count down.

**Common student blockers:**
- They open Profiler, hit record, do *nothing*, hit stop — empty
  flame chart. They need to *interact* with the app while recording.
- They identify "Dashboard re-renders on every Trades update" — true
  but not actionable. They need to find *which prop* changed.
- They "fix" by adding `useMemo` to a primitive value (`useMemo(() => count + 1, [count])`).
  Net-zero. Walk back through TICKET-ADV120's talking point.

**Unblocking ladder:**
1. **Nudge:** "What's the largest yellow bar in the flame chart?
   What component is it?"
2. **Hint:** "Click the component. The right panel says *why* it
   rendered. Read it out loud."
3. **Reveal:** typical culprit is a parent passing `style={{...}}` or
   `onClick={() => ...}` inline — fix with `useCallback` / hoisted
   const.

<details>
<summary>▶ Reference solution — TICKET-ADV127 Profiler trace + fix</summary>

```jsx
// frontend/src/pages/Dashboard.jsx — wrap with <Profiler> for one debugging session
import { Profiler } from 'react';

function onRender(id, phase, actualDuration, baseDuration) {
  // eslint-disable-next-line no-console
  console.log(`[Profiler] ${id} ${phase}  actual=${actualDuration.toFixed(2)}ms  base=${baseDuration.toFixed(2)}ms`);
}

export default function Dashboard({ trades }) {
  return (
    <Profiler id="TradeDashboard" onRender={onRender}>
      <DashboardContents trades={trades} />
    </Profiler>
  );
}
```

The actual fix typically looks like:
```diff
- <TradeRow trade={t} onClick={(id) => setSelected(id)} />
+ const handleSelect = useCallback((id) => setSelected(id), []);
+ // ...
+ <TradeRow trade={t} onClick={handleSelect} />
```

Demo flow during stand-up:
1. Open DevTools → Components → cog → toggle "Highlight updates when components render."
2. Click around the unfixed dashboard — rows flash on every parent update.
3. Apply the `useCallback` + `React.memo` fix.
4. Click around again — rows no longer flash. *That's* the deliverable.

</details>

**Talking point:** Profiler shows *actual* vs *base* time. `actual` is
what happened; `base` is what it *could* take from scratch. The gap is
what your memoisation saves you. If `actual ≈ base`, your memo isn't
hitting.

**▶ Run the project — verify TICKET-ADV127 end-to-end**

Wrap a slow component in `<Profiler>`, then record before/after traces in React DevTools.

```bash
cd frontend && npm run dev
# React DevTools → Profiler → Record → interact → Stop  (before)
# apply the useCallback / useMemo fix
# Record the same interactions again                    (after)
```

**Observe:**

- The console logs `[Profiler] TradeDashboard ... actual=X.XXms base=Y.YYms` for each commit while the wrapper is in place.
- The *after* trace shows materially fewer renders for the named culprit; the highlight stops flashing on unrelated updates.
- You can name in one sentence what changed (e.g. "hoisted the inline `onClick` arrow into a `useCallback`, so the memoised `<TradeRow>` now skips re-renders on parent state changes").
- Failure signal: identical render counts before and after means the fix targeted the wrong prop — re-read the Profiler's "Why did this render?" panel before changing anything else.

---

<details>
<summary><b>Q&A bank</b></summary>


Pre-canned answers to expect today. Grads who've used React before will
want to debate; grads who haven't will want recipes. Match the answer
to the audience.

1. **"HOC vs custom hook — when do I use which?"**
   Hooks are for *behaviour* (`useWebSocket`); HOCs are for *cross-cutting
   wrapping* (`withAuth`, `withErrorBoundary`). HOCs change the
   component tree (they add a wrapper); hooks don't. If you'd describe
   the thing with a verb ("subscribe to…", "debounce…"), it's a hook.
   If you'd describe it with a guard ("require login", "catch errors"),
   it's an HOC.

2. **"Why compound components? `<DataTable>` could just take props."**
   It can — but compound wins when (a) the slots are optional
   (drop `<Pagination>` and the rest works), (b) consumers want to
   re-arrange (header below pagination?), (c) sub-components carry their
   own props you don't want to flatten into the parent (`columns` on
   `Header` vs `renderRow` on `Body`). Compare to `<select>` /
   `<option>` — same shape.

3. **"`useMemo` — does it always help?"**
   No. It helps when (a) the calc is expensive (>1ms), or (b) the
   *result identity* feeds into another memo / `React.memo`. Memoising
   `a + b` *costs* more than it saves — the comparison + cache lookup
   beats the addition.

4. **"`useCallback` — same?"**
   Exactly the same. `useCallback(fn, d)` is literally `useMemo(() => fn, d)`.
   Use it when the callback is passed as a prop to a memoised child,
   or to another hook's deps array. Otherwise it's noise.

5. **"Why React Hook Form, not Formik?"**
   Formik is controlled — every keystroke re-renders the form root. RHF
   is uncontrolled by default — re-renders only on validation. On a form
   with 20 fields, RHF wins by 10x in render count. Also smaller bundle
   (8kB vs ~17kB).

6. **"Yup vs Zod?"**
   Both fine. Yup is mature and the ReconX stack standardised on it
   because of `@hookform/resolvers/yup` symmetry with backend
   validators. Zod gives you TS inference (`z.infer<typeof schema>`)
   — irrelevant on this project because we're JS, not TS.

7. **"Context API vs Redux vs Zustand?"**
   - Context for *low-frequency* state (theme, auth user, locale).
   - Zustand (or Redux) for *high-frequency* state with many subscribers
     (the trade store on Day 9).
   - Don't put fast-changing values in Context — *every* consumer
     re-renders on every change. That's the Day-9 lead-in.

8. **"Theme in Context *and* CSS custom properties — isn't that
   duplication?"**
   No. CSS owns the *values* (the colour tokens); React owns the
   *flip* (which token set is active). The Context never stores
   `#1a1a1a` — only the string `'light'` or `'dark'`, which becomes a
   `[data-theme]` attribute. CSS does the rest. One source of truth
   for colours, one for state.

9. **"RTL vs Enzyme?"**
   RTL tests the **DOM the user sees**; Enzyme tests **component
   internals**. RTL tests don't break when you refactor a class to a
   function or split a component in two. Enzyme tests do. Industry has
   moved to RTL — Enzyme isn't maintained for React 19.

10. **"MSW vs `vi.fn(fetch)` / `jest.mock('axios')`?"**
    MSW intercepts at the *network* layer; mocks intercept at the *code*
    layer. The MSW handler runs the same in dev, in tests, and in
    Storybook — one definition, three uses. Jest-style fetch mocks
    duplicate the contract in every test file.

11. **"When should we lazy-load?"**
    Route-level boundaries (per page). Don't lazy-load individual
    components unless they're huge (rich-text editor, chart library
    >100kB). Each lazy boundary is one Suspense + one HTTP request — the
    overhead adds up below ~30kB.

12. **"Suspense fallback — what should it look like?"**
    A *skeleton* of the page being loaded — not a spinner. Skeleton
    keeps layout stable (no CLS), tells the user what's coming, and
    "feels" faster than a centred spinner. Show the existing
    `<PageSkeleton />` component.

13. **"How is `useEffect` different from `componentDidMount`?"**
    Effects run *after* paint, not before. They also re-run on dep
    change, where `componentDidMount` only runs once and you needed
    `componentDidUpdate` for the rest. And the cleanup function is on
    the *same* hook — no `componentWillUnmount` to pair up.

14. **"Why is `<ErrorBoundary>` still a class?"**
    Because `componentDidCatch` and `static getDerivedStateFromError`
    don't have hook equivalents in React 19. The React team has said
    a hook version is coming; for now, classes are the only API.
    `react-error-boundary` is a library wrapper around the same class.

15. **"`useReducer` vs `useState`?"**
    `useState` for one or two related values; `useReducer` once the
    state machine has more than 3 actions or coupled fields. The trade
    entry form's draft state would be a candidate; the search box
    isn't.

---

</details>

<details>
<summary><b>End-of-day debrief prompts</b></summary>


At 16:45, pick 3 grads at random and ask:

1. *"Walk me through your `useWebSocket`. What happens if the server
   restarts mid-session?"* — listen for: backoff, retry cap, the
   `shouldStop` flag in cleanup.
2. *"Your `<TradeRow />` is memoised but still re-renders. Where would
   you look first?"* — listen for: Profiler → component → 'why
   rendered' → 'props changed' → `useCallback` on the parent.
3. *"What's the difference between Suspense fallback being a spinner
   vs a skeleton?"* — listen for: layout stability (CLS), perceived
   performance, retention of page structure.

If anyone can't answer #1 confidently, they will trip on Day 9's
multi-topic Kafka subscriptions — pair them tomorrow.

---

</details>

<details>
<summary><b>Things that have gone wrong before</b></summary>


A bestiary from past cohorts. Print this; tape it to the wall.

- **`useMemo` on a primitive.**

  Grad wraps `total = a + b` in `useMemo` to "make it faster." Cache comparison + closure allocation > the addition itself.

  **Fix:** show the React docs page that says "do not memoise unless profiling shows a problem."

- **`useCallback` without deps array.**

  `useCallback(fn)` (no second arg) is *not* the same as `useCallback(fn, [])`. React warns but grads ignore it. The function is recomputed every render, defeating the point.

  **Fix:** lint rule `react-hooks/exhaustive-deps` enabled in `eslint.config.js`.

- **WebSocket hook infinite reconnect.**

  Grad listed `wsRef.current` in the `useEffect` deps. Every render → new ref value → new effect → new socket → old socket closes → triggers reconnect → loop. Network tab showed 60+ sockets/sec.

  **Fix:** the socket lives in a `useRef`, *never* in deps; only the `url` belongs in deps.

- **RHF `register()` called inside a callback.**

  Grad wrote `{fields.map((f) => <input {...register(f.name)} />)}` inline. Each render re-registered every field with new internal IDs; cursor jumped on every keystroke.

  **Fix:** call `register` *outside* the render loop, or use `useFieldArray` for dynamic fields.

- **Theme provider above the ErrorBoundary.**

  Crash in `<Dashboard>` triggers the boundary fallback — but the fallback called `useTheme()` and there was no provider above it. White screen with a console error.

  **Fix:** lift `ThemeProvider` to `<root>`, *above* the ErrorBoundary HOC.

- **Lazy-loaded route, no Suspense.**

  Grad added `const Trades = lazy(() => import(...))` but forgot the `<Suspense>` boundary. React error: "A React component suspended while responding to synchronous input."

  **Fix:** Suspense in the layout, around `<Routes>`.

- **RTL test was `getByText('Match')` — design changed to 'Matched'.**

  All 6 dashboard tests went red on a copy change.

  **Fix:** use `getByRole('cell', { name: /match/i })` — regex + role + accessible name survives copywriting.

- **DevTools Profiler showed `<Dashboard>` re-rendering at 100ms.**

  Root cause wasn't the dashboard itself — a `useEffect` inside the layout was calling `setState` every render (no deps array), kicking off another render. The dashboard was *innocent*.

  **Fix:** read the whole flame chart, not just the slowest single bar.

- **MSW handler in `setupServer` but no `mockServiceWorker.js` in `public/`.**

  Test passes because Node-side `setupServer` works; dev mode silently does nothing because the worker isn't registered. Grad thinks the mock is live.

  **Fix:** `npx msw init public/` once, commit the worker file.

- **`React.lazy` chunk hash changed mid-Day-10 deploy.**

  User's open tab tried to fetch `chunk-abc123.js`, got 404 because deploy replaced it with `chunk-def456.js`.

  **Fix:** Day 10 problem — needs a "reload to get the new version" banner on chunk-load error. Mention but don't solve today. ---</details> <details> <summary><b>Hand-off to Day 9</b></summary>


By end-of-day each team should have:

- [ ] Vite dev server on `http://localhost:5173` with path aliases resolving.
- [ ] `withAuth` + `withErrorBoundary` HOCs wrapping at least the Dashboard.
- [ ] `<DataTable>` compound component rendering the trade list with
      sortable headers + pagination.
- [ ] Four custom hooks (`useWebSocket`, `useTradeStream`,
      `useDebouncedSearch`, `useInfiniteScroll`) in `src/hooks/`,
      each used by at least one page.
- [ ] `<TradeRow />` wrapped in `React.memo` with `useCallback`
      handlers feeding it.
- [ ] Lazy-loaded routes with a `<PageSkeleton />` fallback.
- [ ] Trade entry form on `/trades/new` with RHF + Yup validation;
      the form POSTs to `/api/trades` (real or mocked).
- [ ] `ThemeProvider` flipping `[data-theme]` on `<html>`,
      persisted to localStorage.
- [ ] At least one passing RTL test for the dashboard summary cards.
- [ ] *(Optional fast-finisher)* A Profiler trace, before *and* after, showing the TICKET-ADV127 fix.

**Day 9 picks up where this leaves off:** `AuthContext` graduates from
"hold a JWT" to "drive the whole multi-page session"; the Kafka layer
on the backend grows from one topic to three plus DLQs; and the
frontend learns to consume `recon-results` and `system-alerts` live.
The hooks built today are reused — `useWebSocket` powers tomorrow's
alert toast stream; `<DataTable>` becomes the breaks-board.

**Next:** [TrainersGuide/day9/](../day9/README.md)

</details>
