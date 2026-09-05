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
    CONFIG_ID      BIGINT       NOT NULL AUTO_INCREMENT,
    CLIENT_ID      BIGINT       NOT NULL,
    CLIENT_USERNAME VARCHAR(50) NOT NULL,
    FLOWER_ID      VARCHAR(60)  NOT NULL,
    FLOWER_NAME    VARCHAR(100) NOT NULL,
    SALES_DATE     DATE         NOT NULL,
    BAG_COUNT      INT          NOT NULL DEFAULT 0,
    BAG_CHECK      CHAR(1)      NOT NULL DEFAULT 'D',
    PRIMARY KEY (CONFIG_ID),
    UNIQUE KEY uk_bag_count_config (CLIENT_ID, FLOWER_ID, SALES_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
