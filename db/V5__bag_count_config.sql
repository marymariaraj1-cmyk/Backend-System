-- Bag Count Configuration
-- Run manually (no Flyway configured). Safe to re-run: IF NOT EXISTS guards.
-- Purpose: per flower + date config for validating total bag counts entered in sales.
--   BAG_COUNT     : configured number of bags allowed for that flower on that date
--   BAG_CHECK     : 'E' = Enable validation (enforce limit), 'D' = Disable (skip limit check)
--                   Validation defaults to DISABLED ('D'); the user enables it explicitly.
--
-- NOTE: This feature also depends on BLOOMBUDDY_SALES having FLOWER_ID and BAG_COUNT
-- columns (used for saved-state bag totals and edit fallback). Those were added
-- manually to the DB (earlier work) and are NOT recreated here by design.
-- If not present, run:
--   ALTER TABLE BLOOMBUDDY_SALES ADD COLUMN FLOWER_ID VARCHAR(60) NULL AFTER DEBIT_CREDIT_FLAG;
--   ALTER TABLE BLOOMBUDDY_SALES ADD COLUMN BAG_COUNT INT NULL AFTER FLOWER_ID;
-- IMPORTANT: BLOOMBUDDY_SALES.FLOWER_ID is currently VARCHAR(20) but
-- BLOOMBUDDY_FLOWER_MASTER.FLOWER_ID (PK) is VARCHAR(60). To safely store/resolve
-- all flower ids it should be widened to VARCHAR(60):
--   ALTER TABLE BLOOMBUDDY_SALES MODIFY FLOWER_ID VARCHAR(60) NULL;

CREATE TABLE IF NOT EXISTS BLOOMBUDDY_BAG_COUNT_CONFIG (
    CONFIG_ID      INT        NOT NULL AUTO_INCREMENT,
    CLIENT_ID      INT       NOT NULL,
    CLIENT_USERNAME VARCHAR(50) NOT NULL,
    FLOWER_ID      VARCHAR(60)  NOT NULL,
    FLOWER_NAME    VARCHAR(100) NOT NULL,
    SALES_DATE     DATE         NOT NULL,
    BAG_COUNT      INT          NOT NULL DEFAULT 0,
    BAG_CHECK      CHAR(1)      NOT NULL DEFAULT 'D',
    PRIMARY KEY (CONFIG_ID),
    UNIQUE KEY uk_bag_count_config (CLIENT_ID, FLOWER_ID, SALES_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE bloombuddy_sales
    MODIFY COLUMN FARMER_ID VARCHAR(60) NULL,
    MODIFY COLUMN FARMER_NAME VARCHAR(100) NULL;


ALTER TABLE bloombuddy_bag_count_config DROP COLUMN BAG_CHECK;
ALTER TABLE bloombuddy_bag_count_config ADD COLUMN FARMER_ID VARCHAR(20) NOT NULL AFTER BAG_COUNT;
ALTER TABLE bloombuddy_bag_count_config ADD COLUMN FARMER_NAME    VARCHAR(100) NOT NULL  AFTER FARMER_ID;



CREATE TABLE bloombuddy_flower_price_config (
FLOWER_PRICE_CONFIG_ID   BIGINT AUTO_INCREMENT PRIMARY KEY,
CLIENT_ID                BIGINT NOT NULL,
CLIENT_USERNAME          VARCHAR(100) NOT NULL,
FLOWER_ID                 VARCHAR(60)  NOT NULL,
FLOWER_NAME              VARCHAR(150) NOT NULL,
PRICE_DATE               DATE NOT NULL,
PRICE                    DECIMAL(10,2) NOT NULL,
CREATED_BY               VARCHAR(100),
CREATED_DATE              DATETIME DEFAULT CURRENT_TIMESTAMP,
UPDATED_BY               VARCHAR(100),
UPDATED_DATE              DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

CONSTRAINT uq_flower_price_client_date UNIQUE (CLIENT_ID, FLOWER_ID, PRICE_DATE),
CONSTRAINT fk_flower_price_flower FOREIGN KEY (FLOWER_ID) REFERENCES bloombuddy_flower_master(FLOWER_ID)
);


CREATE TABLE bloombuddy_daily_cash_book (
                                            DAILY_CASH_BOOK_ID        BIGINT AUTO_INCREMENT PRIMARY KEY,
                                            CLIENT_ID                 BIGINT NOT NULL,
                                            CLIENT_USERNAME           VARCHAR(100) NOT NULL,
                                            BOOK_DATE                 DATE NOT NULL,

    -- LEFT SIDE (Debit / Outgoing) — MANUAL ENTRY
                                            RENT_AMT                  DECIMAL(12,2) DEFAULT 0.00,   -- வாடகை
                                            EXPENSE_AMT               DECIMAL(12,2) DEFAULT 0.00,   -- செலவு
                                            CHIT_AMT                  DECIMAL(12,2) DEFAULT 0.00,   -- சீட்டு
                                            FINANCE_AMT               DECIMAL(12,2) DEFAULT 0.00,   -- பைனான்ஸ்
                                            NOTE_AMT                  DECIMAL(12,2) DEFAULT 0.00,   -- நோட்டு
                                            SALARY_AMT                DECIMAL(12,2) DEFAULT 0.00,   -- சம்பளம்
                                            COIN_AMT                  DECIMAL(12,2) DEFAULT 0.00,   -- காயின்

    -- LEFT SIDE — SYSTEM CALCULATED
                                            BUYER_PURCHASE_TOTAL      DECIMAL(12,2) DEFAULT 0.00,   -- Block 1: பாக்கி
                                            FARMER_EXCESS_DEBIT_CASH  DECIMAL(12,2) DEFAULT 0.00,   -- Block 2: sum of cash-mode excess debits

    -- RIGHT SIDE (Credit / Incoming) — MANUAL ENTRY
                                            CASH_IN_HAND_AMT          DECIMAL(12,2) DEFAULT 0.00,   -- "YMM" row
                                            OPENING_BALANCE           DECIMAL(12,2) DEFAULT 0.00,   -- முன் இருப்பு

    -- RIGHT SIDE — SYSTEM CALCULATED
                                            BUYER_RECEIVED_TOTAL      DECIMAL(12,2) DEFAULT 0.00,   -- Block 3: பாக்கி வரவு
                                            COMMISSION_TOTAL          DECIMAL(12,2) DEFAULT 0.00,   -- Block 4: கமிஷன்
                                            INSTALLMENT_TOTAL         DECIMAL(12,2) DEFAULT 0.00,   -- Block 5: தவணை

    -- RECORD ONLY (excluded from all totals)
                                            FARMER_EXCESS_DEBIT_NONCASH DECIMAL(12,2) DEFAULT 0.00, -- Block 6

    -- TOTALS
                                            TOTAL_DEBIT_SIDE          DECIMAL(12,2) DEFAULT 0.00,
                                            TOTAL_CREDIT_SIDE         DECIMAL(12,2) DEFAULT 0.00,
                                            CLOSING_BALANCE           DECIMAL(12,2) DEFAULT 0.00,   -- மீதம் = debit - credit

                                            REMARKS                   VARCHAR(500) NULL,
                                            CREATED_BY                VARCHAR(100),
                                            CREATED_DATE              DATETIME DEFAULT CURRENT_TIMESTAMP,
                                            UPDATED_BY                VARCHAR(100),
                                            UPDATED_DATE              DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                            CONSTRAINT uq_daily_cash_book_client_date UNIQUE (CLIENT_ID, BOOK_DATE)
);

CREATE INDEX idx_daily_cash_book_client_date
    ON bloombuddy_daily_cash_book (CLIENT_ID, BOOK_DATE);


-- =====================================================
-- Daily Cash Book — Detail table
-- Snapshots the variable-length lists (Block 2, Block 6)
-- and any ad-hoc manual expense rows
-- =====================================================
CREATE TABLE bloombuddy_daily_cash_book_detail (
                                                   CASH_BOOK_DETAIL_ID       BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                   DAILY_CASH_BOOK_ID        BIGINT NOT NULL,
                                                   CLIENT_ID                 BIGINT NOT NULL,
                                                   CLIENT_USERNAME           VARCHAR(100) NOT NULL,
                                                   BOOK_DATE                 DATE NOT NULL,

                                                   ENTRY_TYPE                VARCHAR(40) NOT NULL,
    -- 'FARMER_EXCESS_DEBIT_CASH'    → Block 2 (left side, included in total)
    -- 'FARMER_EXCESS_DEBIT_NONCASH' → Block 6 (record only, excluded from total)
    -- 'MANUAL_EXPENSE'              → ad-hoc extra expense row

                                                   ENTRY_LABEL               VARCHAR(150) NOT NULL,   -- farmer name or expense label
                                                   FARMER_ID                 VARCHAR(100)  NULL,
                                                   AMOUNT                    DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                                                   DISPLAY_ORDER             INT DEFAULT 0,

                                                   CREATED_DATE              DATETIME DEFAULT CURRENT_TIMESTAMP,
                                                   UPDATED_DATE              DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                                   CONSTRAINT fk_cash_book_detail_header
                                                       FOREIGN KEY (DAILY_CASH_BOOK_ID)
                                                           REFERENCES bloombuddy_daily_cash_book(DAILY_CASH_BOOK_ID)
                                                           ON DELETE CASCADE
);

CREATE INDEX idx_cash_book_detail_header
    ON bloombuddy_daily_cash_book_detail (DAILY_CASH_BOOK_ID, ENTRY_TYPE);