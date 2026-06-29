-- DB A(pocketstock_main) notification structured payload columns.
-- Run once before deploying app code that writes tag/url/occurred_at/data_json.
CREATE DATABASE IF NOT EXISTS pocketstock_main CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pocketstock_main;
SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS add_notification_column_if_missing$$

CREATE PROCEDURE add_notification_column_if_missing(IN p_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
  IF (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND COLUMN_NAME = p_column
  ) = 0 THEN
    SET @ddl = p_ddl;
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$

CALL add_notification_column_if_missing(
  'tag',
  'ALTER TABLE notifications ADD COLUMN tag VARCHAR(80) NULL AFTER ref_id'
)$$
CALL add_notification_column_if_missing(
  'url',
  'ALTER TABLE notifications ADD COLUMN url VARCHAR(255) NULL AFTER tag'
)$$
CALL add_notification_column_if_missing(
  'occurred_at',
  'ALTER TABLE notifications ADD COLUMN occurred_at VARCHAR(40) NULL AFTER url'
)$$
CALL add_notification_column_if_missing(
  'data_json',
  'ALTER TABLE notifications ADD COLUMN data_json JSON NULL AFTER occurred_at'
)$$

DROP PROCEDURE add_notification_column_if_missing$$

DELIMITER ;
