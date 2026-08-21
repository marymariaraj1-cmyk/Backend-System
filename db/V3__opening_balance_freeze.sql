-- Freeze opening balance on inactive (settled) ledger rows.
-- Purpose: the archived/closed ledger view must preserve the opening balance that was in effect
-- at settlement time (including any configured opening-balance value). The OB config is cleared
-- at settlement, so the value is frozen here instead. Active rows keep computing opening on the fly
-- and leave this column NULL; it is stamped only when a row is deactivated by settlement.
-- Run manually (no Flyway configured). Safe to re-run: ALTER is guarded, and the backfill only
-- writes rows whose OPENING_BALANCE is still NULL.
-- Note: the DB user must have CREATE ROUTINE privilege for the stored procedure.

-- 1) Add the OPENING_BALANCE column to both ledger tables (no-op if already present).
SET @ddl_buyer := IF(
    EXISTS(
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'BLOOMBUDDY_BUYER_LEDGER'
          AND COLUMN_NAME = 'OPENING_BALANCE'
    ),
    'SELECT 1',
    'ALTER TABLE BLOOMBUDDY_BUYER_LEDGER ADD COLUMN OPENING_BALANCE DECIMAL(12,2) NULL'
);
PREPARE stmt_buyer FROM @ddl_buyer;
EXECUTE stmt_buyer;
DEALLOCATE PREPARE stmt_buyer;

SET @ddl_farmer := IF(
    EXISTS(
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'BLOOMBUDDY_FARMER_LEDGER'
          AND COLUMN_NAME = 'OPENING_BALANCE'
    ),
    'SELECT 1',
    'ALTER TABLE BLOOMBUDDY_FARMER_LEDGER ADD COLUMN OPENING_BALANCE DECIMAL(12,2) NULL'
);
PREPARE stmt_farmer FROM @ddl_farmer;
EXECUTE stmt_farmer;
DEALLOCATE PREPARE stmt_farmer;

-- 2) One-time backfill: freeze the running opening balance onto existing INACTIVE ('N') rows.
-- Walks each party's ledger in (SALES_DATE, LEDGER_ID) order, replicating the ledger walk without
-- applying the current OB config (historical OB values for already-settled periods are unrecoverable).
-- Only rows with OPENING_BALANCE IS NULL are written, so re-running is safe.
DELIMITER //

DROP PROCEDURE IF EXISTS backfill_ledger_opening_balance//

CREATE PROCEDURE backfill_ledger_opening_balance()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_client_id BIGINT;
    DECLARE v_party_id VARCHAR(60);
    DECLARE v_sales_date DATE;
    DECLARE v_debit DECIMAL(12,2);
    DECLARE v_credit DECIMAL(12,2);
    DECLARE v_active CHAR(1);
    DECLARE v_prev_client BIGINT DEFAULT -1;
    DECLARE v_prev_party VARCHAR(60) DEFAULT '';
    DECLARE v_running DECIMAL(12,2) DEFAULT 0;
    DECLARE v_active_running DECIMAL(12,2) DEFAULT 0;
    DECLARE v_have_active TINYINT DEFAULT 0;
    DECLARE v_opening DECIMAL(12,2) DEFAULT 0;
    DECLARE v_closing DECIMAL(12,2) DEFAULT 0;
    DECLARE cur_farmer CURSOR FOR
        SELECT CLIENT_ID, FARMER_ID, SALES_DATE, DEBIT_AMT, CREDIT_AMT, LEDGER_ACTIVE
        FROM BLOOMBUDDY_FARMER_LEDGER
        ORDER BY CLIENT_ID, FARMER_ID, SALES_DATE, FARMER_LEDGER_ID;
    DECLARE cur_buyer CURSOR FOR
        SELECT CLIENT_ID, BUYER_ID, SALES_DATE, DEBIT_AMT, CREDIT_AMT, LEDGER_ACTIVE
        FROM BLOOMBUDDY_BUYER_LEDGER
        ORDER BY CLIENT_ID, BUYER_ID, SALES_DATE, BUYER_LEDGER_ID;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN cur_farmer;
    SET done = 0;
    farmer_loop: LOOP
        FETCH cur_farmer INTO v_client_id, v_party_id, v_sales_date, v_debit, v_credit, v_active;
        IF done THEN
            LEAVE farmer_loop;
        END IF;
        IF v_client_id <> v_prev_client OR NOT (v_party_id <=> v_prev_party) THEN
            SET v_running = 0;
            SET v_active_running = 0;
            SET v_have_active = 0;
            SET v_prev_client = v_client_id;
            SET v_prev_party = v_party_id;
        END IF;
        SET v_opening = IF(v_active = 'Y', IF(v_have_active = 1, v_active_running, 0), v_running);
        SET v_closing = v_opening + COALESCE(v_credit, 0) - COALESCE(v_debit, 0);
        IF v_active = 'N' THEN
            UPDATE BLOOMBUDDY_FARMER_LEDGER
               SET OPENING_BALANCE = v_opening
             WHERE CLIENT_ID = v_client_id AND FARMER_ID = v_party_id
               AND SALES_DATE = v_sales_date AND LEDGER_ACTIVE = 'N'
               AND OPENING_BALANCE IS NULL;
        END IF;
        SET v_running = v_closing;
        IF v_active = 'Y' THEN
            SET v_active_running = v_closing;
            SET v_have_active = 1;
        END IF;
    END LOOP;
    CLOSE cur_farmer;

    OPEN cur_buyer;
    SET done = 0;
    buyer_loop: LOOP
        FETCH cur_buyer INTO v_client_id, v_party_id, v_sales_date, v_debit, v_credit, v_active;
        IF done THEN
            LEAVE buyer_loop;
        END IF;
        IF v_client_id <> v_prev_client OR NOT (v_party_id <=> v_prev_party) THEN
            SET v_running = 0;
            SET v_active_running = 0;
            SET v_have_active = 0;
            SET v_prev_client = v_client_id;
            SET v_prev_party = v_party_id;
        END IF;
        SET v_opening = IF(v_active = 'Y', IF(v_have_active = 1, v_active_running, 0), v_running);
        SET v_closing = v_opening + COALESCE(v_debit, 0) - COALESCE(v_credit, 0);
        IF v_active = 'N' THEN
            UPDATE BLOOMBUDDY_BUYER_LEDGER
               SET OPENING_BALANCE = v_opening
             WHERE CLIENT_ID = v_client_id AND BUYER_ID = v_party_id
               AND SALES_DATE = v_sales_date AND LEDGER_ACTIVE = 'N'
               AND OPENING_BALANCE IS NULL;
        END IF;
        SET v_running = v_closing;
        IF v_active = 'Y' THEN
            SET v_active_running = v_closing;
            SET v_have_active = 1;
        END IF;
    END LOOP;
    CLOSE cur_buyer;
END//

DELIMITER ;

CALL backfill_ledger_opening_balance();
