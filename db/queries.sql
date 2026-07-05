-- ============================================================================
-- VWAP per instrument per day (window function)
-- ============================================================================
SELECT DISTINCT
    t.instrument_id,
    t.trade_date,
    SUM(t.price * t.quantity) OVER (PARTITION BY t.instrument_id, t.trade_date)
        / NULLIF(SUM(t.quantity) OVER (PARTITION BY t.instrument_id, t.trade_date), 0)
            AS vwap
FROM trades t
WHERE t.deleted_at IS NULL
  AND t.asset_class = 'EQUITY'
ORDER BY t.trade_date DESC, t.instrument_id;


-- ============================================================================
-- Recursive CTE: trade lifecycle (execution -> settlement
--                -> recon_break -> resolution)
-- ============================================================================
WITH RECURSIVE trade_lifecycle AS (
    -- anchor: every trade in its execution state
    SELECT
        t.id           AS trade_id,
        t.trade_ref,
        1              AS step,
        'EXECUTED'     AS state,
        t.created_at   AS at_ts,
        NULL::text     AS detail
    FROM trades t
    WHERE t.deleted_at IS NULL

    UNION ALL

    -- recursive: each subsequent state derived from the previous step
    SELECT
        tl.trade_id,
        tl.trade_ref,
        tl.step + 1,
        CASE tl.step
            WHEN 1 THEN 'CONFIRMED'
            WHEN 2 THEN 'SETTLED'
            WHEN 3 THEN 'RECONCILED'
        END                                          AS state,
        s.settlement_date::timestamp                  AS at_ts,
        s.status                                      AS detail
    FROM trade_lifecycle tl
    JOIN settlements s ON s.trade_id = tl.trade_id
    WHERE tl.step < 4
)
SELECT * FROM trade_lifecycle
ORDER BY trade_id, step;

-- materalized view

-- simple materalized view

CREATE MATERIALIZED VIEW mv_daily_recon_summary AS
SELECT
    trade_date,
    COUNT(*)                                          AS total_trades,
    COUNT(*) FILTER (WHERE status = 'MATCHED')        AS matched_trades,
    COUNT(*) FILTER (WHERE status IN ('UNMATCHED','DISPUTED')) AS break_trades,
    COALESCE(SUM(quantity * price), 0)                AS total_notional
FROM trades
WHERE deleted_at IS NULL
GROUP BY trade_date;

CREATE UNIQUE INDEX uq_mv_daily_recon_summary_trade_date
    ON mv_daily_recon_summary (trade_date);


-- Initial population
REFRESH MATERIALIZED VIEW mv_daily_recon_summary;

-- ============================================================================
-- TICKET-ADV008 — Materialised view: daily reconciliation summary
-- Refreshed nightly by a scheduled job or via API trigger after each recon run.
-- ============================================================================
--
-- CREATE MATERIALIZED VIEW mv_daily_recon_summary AS
-- SELECT
--     t.trade_date,
--     cp.region,
--     i.asset_class,
--     COUNT(*)                                                  AS total_trades,
--     COUNT(*) FILTER (WHERE t.status = 'MATCHED')              AS matched_trades,
--     COUNT(*) FILTER (WHERE t.status = 'UNMATCHED')            AS unmatched_trades,
--     COUNT(*) FILTER (WHERE t.status = 'DISPUTED')             AS disputed_trades,
--     COUNT(rb.id) FILTER (WHERE rb.status = 'OPEN')            AS open_breaks,
--     COUNT(rb.id) FILTER (WHERE rb.status = 'RESOLVED')        AS resolved_breaks,
--     ROUND(SUM(t.quantity * t.price)::NUMERIC, 2)              AS gross_notional,
--     ROUND(
--             100.0 * COUNT(*) FILTER (WHERE t.status = 'MATCHED')
--               / NULLIF(COUNT(*), 0)
--         , 2)                                                      AS match_rate_pct
-- FROM trades t
--          JOIN counterparties cp  ON cp.id = t.counterparty_id
--          JOIN instruments    i   ON i.id  = t.instrument_id
--          LEFT JOIN recon_breaks rb ON rb.trade_id = t.id
-- GROUP BY t.trade_date, cp.region, i.asset_class
--     WITH NO DATA;   -- create empty; populate on demand with REFRESH
--
-- -- REQUIRED for REFRESH MATERIALIZED VIEW CONCURRENTLY
-- CREATE UNIQUE INDEX uq_mv_daily_recon_summary
--     ON mv_daily_recon_summary (trade_date, region, asset_class);
--
-- -- Additional indexes for common dashboard filters
-- CREATE INDEX idx_mv_daily_recon_summary_date   ON mv_daily_recon_summary (trade_date);
-- CREATE INDEX idx_mv_daily_recon_summary_region ON mv_daily_recon_summary (region);
--
-- -- Initial population
-- REFRESH MATERIALIZED VIEW mv_daily_recon_summary;

-- Nightly (or post-recon-run) refresh, no read lock:
-- REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_recon_summary;



-- ============================================================================
-- TICKET-ADV008 — REFRESH the daily-summary materialised view (concurrent so it can
--         run while the dashboard is reading it)
-- ============================================================================
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_recon_summary;


-- ============================================================================
-- TICKET-ADV009 — JSONB lookup: which instruments have sector = 'Banking'?
-- ============================================================================
SELECT id, symbol, metadata
FROM instruments
WHERE metadata @> '{"sector":"Banking"}'::jsonb;
