# ADR-0013: JSONB for `instruments.metadata`

**Status:** Accepted | **Date:** 2026-07-07

## Context
`instruments` covers heterogeneous asset classes — bonds carry coupon schedules and day-count conventions, equities carry dividend and corporate-action data, derivatives carry strike/expiry/underlying references. These attribute sets differ significantly by type and evolve as new instrument classes are onboarded, driven by trading desk and regulatory requirements. A rigid relational schema would require frequent migrations; ReconX's near-prod status means schema changes carry review and deployment overhead that slows onboarding of new instrument types.

## Decision
Store variable, asset-class-specific attributes in a single `metadata JSONB` column on `instruments`, backed by a GIN index using the `jsonb_path_ops` operator class for containment queries (`@>`). Core, universally-shared fields (ISIN, LEI, instrument type, currency) remain as typed relational columns; only the variable attribute set lives in JSONB.

## Consequences
**Positive:**
- New instrument types or attributes can be onboarded without a schema migration or downtime — critical given a small platform team supporting active trading desks.
- `jsonb_path_ops` GIN indexing keeps containment queries performant at 50K trades/day scale without needing per-attribute indexes.
- Keeps the relational core (ISIN, LEI, FK relationships) intact for referential integrity where it matters most.

**Negative / Tradeoffs:**
- No foreign key or NOT NULL enforcement within the JSONB structure; validation shifts to the application layer (Spring Boot service layer).
- Reporting/BI tooling requires JSONB-aware queries, reducing out-of-the-box compatibility with some analyst tools.
- Schema documentation must be maintained separately (e.g., JSON Schema per instrument type) since the database no longer self-documents these fields.

## Alternatives Considered
- **Table-per-asset-class (bonds, equities, derivatives as subtypes)** — rejected; multiplies migration and query-join overhead as new asset classes are added.
- **Entity-Attribute-Value (EAV) table** — rejected; poor query performance and unreadable SQL at this row volume and 5-year retention.
- **Separate metadata microservice** — rejected as premature; adds operational complexity (new service, new datastore) disproportionate to current scale (10 concurrent analysts).