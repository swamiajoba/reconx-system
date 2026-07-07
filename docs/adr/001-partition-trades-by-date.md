# ADR-0012: Partition the `trades` Table by `trade_date`

**Status:** Accepted | **Date:** 2026-07-07

## Context
The `trades` table ingests ~50,000 rows/day and must be retained for 5 years (~90M rows at steady state). Reconciliation analysts (10 concurrent) query predominantly by recent trade date ranges, while older data is accessed rarely — mostly for audit and regulatory lookback. A single monolithic table degrades index performance over time, complicates purge/archive of aged data, and forces full-table vacuum/maintenance windows that risk contention with live recon jobs on Kafka-fed inserts.

## Decision
Partition `trades` by **range on `trade_date`**, using monthly partitions managed via native PostgreSQL 16 declarative partitioning. New partitions are pre-created via a scheduled job; partitions older than the 5-year retention window are detached and archived rather than deleted in place.

## Consequences
**Positive:**
- Query planner prunes irrelevant partitions for date-scoped recon queries, keeping index size and scan cost bounded regardless of total row count.
- Retention/archival becomes a metadata operation (`DETACH PARTITION`) instead of a slow bulk `DELETE`, avoiding vacuum bloat and long-held locks.
- Maintenance (reindex, vacuum) can run per-partition, reducing blocking on live inserts from Kafka consumers.

**Negative / Tradeoffs:**
- Unique constraints and foreign keys referencing `trades` must include `trade_date` in their key, requiring schema and application-layer adjustments.
- Cross-partition queries (e.g., ad-hoc reports spanning years) lose some planner efficiency versus a single well-indexed table.
- Adds operational overhead: partition creation must be automated and monitored to avoid insert failures from a missing future partition.

## Alternatives Considered
- **Single unpartitioned table with aggressive indexing** — rejected; index bloat and vacuum times would worsen unacceptably over 5-year retention.
- **Time-series extension (e.g., TimescaleDB hypertables)** — rejected to avoid introducing a new extension/dependency onto a near-prod stack already standardized on vanilla PostgreSQL 16.
- **Partition by counterparty instead of date** — rejected; access patterns are date-driven, not counterparty-driven, and would not prune effectively for recon's typical queries.