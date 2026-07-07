# ADR-0014: GIN Index over B-tree on `instruments.metadata`

**Status:** Accepted | **Date:** 2026-07-07

## Context
`instruments.metadata` (JSONB, per ADR-0013) is queried primarily via containment lookups — recon analysts and matching jobs filter instruments by nested attributes (e.g., "find all bonds with `coupon_frequency: quarterly`" or "instruments where `metadata @> '{"sector": "financials"}'"). B-tree indexes on JSONB columns can only support equality on the *entire* JSON value or expression indexes on specific extracted paths, neither of which scales well against an attribute set that varies by asset class and grows as new instrument types are onboarded.

## Decision
Index `instruments.metadata` with a **GIN index using the `jsonb_path_ops` operator class**, optimized for `@>` containment queries. `jsonb_path_ops` is chosen over default GIN (`jsonb_ops`) because it produces a smaller, faster index at the cost of not supporting key-existence operators (`?`, `?|`, `?&`), which recon query patterns don't require.

## Consequences
**Positive:**
- Containment queries across arbitrary, varying JSON structures stay performant without needing a separate B-tree expression index per attribute path.
- `jsonb_path_ops` keeps index size smaller than default GIN, reducing write amplification on the ~50K inserts/day from Kafka-fed trade ingestion.
- No schema changes needed as new instrument attributes are added — the index adapts automatically since it isn't tied to specific paths.

**Negative / Tradeoffs:**
- Key-existence queries (`metadata ? 'coupon_frequency'`) are not supported; any query needing "does this key exist" must be rewritten as a containment check or use a separate expression index.
- GIN indexes have higher write overhead than B-tree, though mitigated here by fast-update GIN pending-list batching in PostgreSQL 16.
- No native support for range queries on nested numeric/date fields inside the JSONB blob.

## Alternatives Considered
- **B-tree expression indexes per known JSON path** — rejected; requires foreknowledge of query patterns and a migration per new attribute path.
- **Default GIN (`jsonb_ops`)** — rejected; larger index footprint for no benefit given recon's containment-only access pattern.
- **No index (sequential scan)** — rejected; unacceptable at 50K trades/day with 5-year retention and 10 concurrent analysts running recon queries.