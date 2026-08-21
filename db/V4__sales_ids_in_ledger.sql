-- V4: Add SALES_IDS column to both ledger tables
-- Stores comma-separated BLOOMBUDDY_SALES.SALES_ID values for each ledger entry.
-- Enables precise View Purchase fetch without amount-matching heuristics.

-- ============================================================
-- 1. ADD COLUMNS
-- ============================================================

ALTER TABLE BLOOMBUDDY_FARMER_LEDGER
    ADD COLUMN SALES_IDS VARCHAR(1024) NULL;

ALTER TABLE BLOOMBUDDY_BUYER_LEDGER
    ADD COLUMN SALES_IDS VARCHAR(1024) NULL;

-- ============================================================
-- 2. BACKFILL FARMER
--    Rule: all sales rows for (farmer, date) belong to the
--    active Y entry if it exists, otherwise the N entry.
-- ============================================================

UPDATE BLOOMBUDDY_FARMER_LEDGER fl
JOIN (
    SELECT CLIENT_ID, FARMER_ID, SALES_DATE,
           GROUP_CONCAT(SALES_ID ORDER BY SALES_ID SEPARATOR ',') AS IDS
    FROM   BLOOMBUDDY_SALES
    GROUP  BY CLIENT_ID, FARMER_ID, SALES_DATE
) g ON  g.CLIENT_ID  = fl.CLIENT_ID
    AND g.FARMER_ID  = fl.FARMER_ID
    AND g.SALES_DATE = fl.SALES_DATE
SET fl.SALES_IDS = g.IDS
WHERE fl.LEDGER_ACTIVE = 'Y'
   OR (   fl.LEDGER_ACTIVE = 'N'
      AND NOT EXISTS (
          SELECT 1 FROM BLOOMBUDDY_FARMER_LEDGER y2
          WHERE  y2.CLIENT_ID  = fl.CLIENT_ID
            AND  y2.FARMER_ID  = fl.FARMER_ID
            AND  y2.SALES_DATE = fl.SALES_DATE
            AND  y2.LEDGER_ACTIVE = 'Y'
      )
  );

-- ============================================================
-- 3. BACKFILL BUYER — active Y entries
--    Rule: Y gets the newest trailing sales rows whose
--    cumulative sum equals Y.DEBIT_AMT + Y.CREDIT_AMT
--    (i.e. the gross purchase for the current generation).
--    Handles mixed-date (Y+N) and Y-only cases.
-- ============================================================

WITH sales_cum AS (
    SELECT s.SALES_ID, s.CLIENT_ID, s.BUYER_ID, s.SALES_DATE, s.PRICE,
           SUM(s.PRICE) OVER (
               PARTITION BY s.CLIENT_ID, s.BUYER_ID, s.SALES_DATE
               ORDER BY s.SALES_ID DESC
               ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
           ) AS cum_from_newest
    FROM BLOOMBUDDY_SALES s
),
y_entries AS (
    SELECT bl.CLIENT_ID, bl.BUYER_ID, bl.SALES_DATE,
           COALESCE(bl.DEBIT_AMT, 0) + COALESCE(bl.CREDIT_AMT, 0) AS y_target
    FROM BLOOMBUDDY_BUYER_LEDGER bl
    WHERE bl.LEDGER_ACTIVE = 'Y'
),
y_sales AS (
    SELECT sc.CLIENT_ID, sc.BUYER_ID, sc.SALES_DATE,
           GROUP_CONCAT(sc.SALES_ID ORDER BY sc.SALES_ID SEPARATOR ',') AS ids
    FROM   sales_cum sc
    JOIN   y_entries ye ON  ye.CLIENT_ID  = sc.CLIENT_ID
                       AND ye.BUYER_ID   = sc.BUYER_ID
                       AND ye.SALES_DATE = sc.SALES_DATE
    -- include row if cumulative sum BEFORE this row < target
    -- (this row and all newer rows together reach or exceed target)
    WHERE  (sc.cum_from_newest - sc.PRICE) < ye.y_target
    GROUP  BY sc.CLIENT_ID, sc.BUYER_ID, sc.SALES_DATE
)
UPDATE BLOOMBUDDY_BUYER_LEDGER bl
JOIN y_sales ys ON  ys.CLIENT_ID  = bl.CLIENT_ID
                AND ys.BUYER_ID   = bl.BUYER_ID
                AND ys.SALES_DATE = bl.SALES_DATE
SET bl.SALES_IDS = ys.ids
WHERE bl.LEDGER_ACTIVE = 'Y';

-- ============================================================
-- 4. BACKFILL BUYER — N-only entries (no Y row exists)
--    Rule: all sales rows for the date belong to the N entry.
-- ============================================================

UPDATE BLOOMBUDDY_BUYER_LEDGER bl
JOIN (
    SELECT CLIENT_ID, BUYER_ID, SALES_DATE,
           GROUP_CONCAT(SALES_ID ORDER BY SALES_ID SEPARATOR ',') AS IDS
    FROM   BLOOMBUDDY_SALES
    GROUP  BY CLIENT_ID, BUYER_ID, SALES_DATE
) g ON  g.CLIENT_ID  = bl.CLIENT_ID
    AND g.BUYER_ID   = bl.BUYER_ID
    AND g.SALES_DATE = bl.SALES_DATE
SET bl.SALES_IDS = g.IDS
WHERE bl.LEDGER_ACTIVE = 'N'
  AND NOT EXISTS (
      SELECT 1 FROM BLOOMBUDDY_BUYER_LEDGER y2
      WHERE  y2.CLIENT_ID  = bl.CLIENT_ID
        AND  y2.BUYER_ID   = bl.BUYER_ID
        AND  y2.SALES_DATE = bl.SALES_DATE
        AND  y2.LEDGER_ACTIVE = 'Y'
  );
