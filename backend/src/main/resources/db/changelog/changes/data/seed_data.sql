-- ============================================================================
-- TICKET-ADV017 — Seed data: 10 counterparties, 50 instruments, 500 trades
-- ============================================================================

-- 10 counterparties — explicit, named, spread across all 4 regions
INSERT INTO counterparties (name, lei_code, region) VALUES
                                                        ('Apex Brokers Inc',           '5493001ABCDE12345001', 'NAMR'),
                                                        ('Vertex Securities LLC',      '5493001ABCDE12345002', 'NAMR'),
                                                        ('Helix Capital Markets',      '5493001ABCDE12345003', 'APAC'),
                                                        ('Aurora Markets SA',          '5493001ABCDE12345004', 'LATAM'),
                                                        ('Borealis Trading GmbH',      '5493001ABCDE12345005', 'EMEA'),
                                                        ('Cascadia Investments PLC',   '5493001ABCDE12345006', 'EMEA'),
                                                        ('Delphi Asset Management',    '5493001ABCDE12345007', 'EMEA'),
                                                        ('Equinox Securities Pty',     '5493001ABCDE12345008', 'APAC'),
                                                        ('Fjord Capital Partners',     '5493001ABCDE12345009', 'EMEA'),
                                                        ('Granite Hill Brokers',       '5493001ABCDE12345010', 'NAMR');

-- 50 instruments — 5 explicit + 45 generated
INSERT INTO instruments (symbol, name, asset_class, currency, isin, metadata) VALUES
                                                                                  ('SAP.DE', 'SAP SE',                'EQUITY',       'EUR', 'DE0007164600',
                                                                                   '{"sector":"Technology","exchange":"XETR"}'::JSONB),
                                                                                  ('US10Y',  'US 10-Year Treasury',   'FIXED_INCOME', 'USD', 'US912828F622',
                                                                                   '{"tenor":"10Y","issuer":"US Treasury"}'::JSONB),
                                                                                  ('EURUSD', 'Euro / US Dollar',      'FX',           'USD', NULL,
                                                                                   '{"pair":["EUR","USD"]}'::JSONB),
                                                                                  ('XAU',    'Spot Gold',             'COMMODITY',    'USD', NULL,
                                                                                   '{"unit":"troy ounce"}'::JSONB),
                                                                                  ('CL_FUT', 'WTI Crude Oil Futures', 'DERIVATIVE',   'USD', NULL,
                                                                                   '{"underlying":"WTI","contractSize":1000}'::JSONB);

INSERT INTO instruments (symbol, name, asset_class, currency, isin, metadata)
SELECT
    'GEN' || LPAD(g::TEXT, 4, '0'),
    'Generated Instrument ' || g,
    (ARRAY['EQUITY','FIXED_INCOME','FX','COMMODITY','DERIVATIVE'])[1 + (g % 5)],
    (ARRAY['USD','EUR','GBP','JPY','CHF'])[1 + (g % 5)],
    NULL,
    jsonb_build_object('seq', g, 'auto', true)
FROM generate_series(1, 45) AS g;

-- 500 trades spread across 4 months (April–July 2026)
INSERT INTO trades (trade_ref, instrument_id, counterparty_id, asset_class, side, quantity, price, trade_date, status)
SELECT
    'TRD-2026-' || LPAD(n::TEXT, 6, '0'),
    1 + (n % 50),
    1 + (n % 10),
    (ARRAY['EQUITY','FIXED_INCOME','FX','COMMODITY','DERIVATIVE'])[1 + (n % 5)],
    (ARRAY['BUY','SELL'])[1 + (n % 2)],
    ROUND((random() * 10000 + 1)::NUMERIC, 4),
    ROUND((random() * 500 + 1)::NUMERIC, 4),
    DATE '2026-04-01' + (n % 120) * INTERVAL '1 day',
    (ARRAY['PENDING','MATCHED','UNMATCHED','DISPUTED','MATCHED','MATCHED'])[1 + (n % 6)]
FROM generate_series(1, 500) AS n;

-- A handful of breaks against the unmatched/disputed trades
INSERT INTO recon_breaks (trade_id, discrepancy_type, status)
SELECT id,
       (ARRAY['PRICE_MISMATCH','QUANTITY_MISMATCH','DATE_MISMATCH'])[1 + (id % 3)],
       'OPEN'
FROM trades
WHERE status IN ('UNMATCHED','DISPUTED')
    LIMIT 30;

-- Sanity checks
-- SELECT COUNT(*) AS counterparties_total FROM counterparties;   -- expect 10
-- SELECT COUNT(*) AS instruments_total   FROM instruments;       -- expect 50
-- SELECT COUNT(*) AS trades_total        FROM trades;            -- expect 500
-- SELECT COUNT(*) AS open_breaks         FROM recon_breaks WHERE status = 'OPEN';

-- Confirm partition spread:
-- SELECT
--     DATE_TRUNC('month', trade_date)::DATE AS month,
--     COUNT(*) AS n
-- FROM trades
-- GROUP BY 1
-- ORDER BY 1;
-- Expect ~125 rows per month across the 4 active partitions.