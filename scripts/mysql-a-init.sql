-- =====================================================================
-- DB A (일반) — pocketstock_main
-- user · asset · budget · portfolio · notification 테이블 (같은 DB, JOIN 허용)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS pocketstock_main CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pocketstock_main;

-- ========== user ==========
CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(60),
  name VARCHAR(50),
  phone VARCHAR(20),
  ci CHAR(88) UNIQUE,
  birth_date DATE,
  gender VARCHAR(10),
  status VARCHAR(20) DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME NULL
);

CREATE TABLE IF NOT EXISTS user_auth_methods (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  method_type VARCHAR(10),
  secret_hash VARCHAR(255),
  is_active BOOLEAN DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_uam_user (user_id)
);

CREATE TABLE IF NOT EXISTS account_passwords (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  password_hash VARCHAR(255),
  is_locked BOOLEAN DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS terms_agreements (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  terms_type VARCHAR(40),
  is_required BOOLEAN,
  terms_version VARCHAR(20),
  is_agreed BOOLEAN,
  agreed_at DATETIME,
  ip VARCHAR(45),
  channel VARCHAR(20),
  INDEX idx_terms_user (user_id)
);

-- ========== asset ==========
CREATE TABLE IF NOT EXISTS linked_institutions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  company VARCHAR(20),
  institution_type VARCHAR(20),
  link_status VARCHAR(20),
  linked_at DATETIME NULL,
  last_synced_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_inst (user_id, company, institution_type)
);

CREATE TABLE IF NOT EXISTS linked_accounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  institution_id BIGINT,
  account_type VARCHAR(20),
  account_no_enc VARBINARY(255),
  balance DECIMAL(18,4),
  currency VARCHAR(3),
  interest_rate DECIMAL(7,4) NULL,
  maturity_date DATE NULL,
  is_dormant BOOLEAN DEFAULT FALSE,
  last_synced_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_la_user (user_id), INDEX idx_la_inst (institution_id)
);

CREATE TABLE IF NOT EXISTS card_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  linked_account_id BIGINT,
  merchant_name VARCHAR(100),
  mcc VARCHAR(10),
  category VARCHAR(40),
  amount DECIMAL(18,4),
  paid_at DATETIME,
  is_cancelled BOOLEAN DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ct_user (user_id), INDEX idx_ct_paid (paid_at)
);

CREATE TABLE IF NOT EXISTS spending_analysis (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  period VARCHAR(7),
  category VARCHAR(40),
  amount_sum DECIMAL(18,4),
  ratio DECIMAL(7,4),
  analyzed_at DATETIME,
  INDEX idx_sa_user (user_id)
);

CREATE TABLE IF NOT EXISTS external_holdings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  broker_name VARCHAR(40),
  stock_code VARCHAR(20),
  stock_name VARCHAR(100),
  quantity DECIMAL(18,6),
  eval_amount DECIMAL(18,4),
  synced_at DATETIME,
  INDEX idx_eh_user (user_id)
);

-- ========== budget ==========
CREATE TABLE IF NOT EXISTS budget_goals (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  period VARCHAR(7),
  category VARCHAR(40) NULL,
  target_amount DECIMAL(18,4),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_bg (user_id, period, category)
);

CREATE TABLE IF NOT EXISTS budget_savings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  period VARCHAR(7),
  target_amount DECIMAL(18,4),
  spent_amount DECIMAL(18,4),
  saved_amount DECIMAL(18,4),
  is_collect_agreed BOOLEAN DEFAULT FALSE,
  transfer_status VARCHAR(20) DEFAULT 'PENDING',
  transferred_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_bs (user_id, period)
);

-- ========== portfolio ==========
CREATE TABLE IF NOT EXISTS recommended_portfolios (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  is_current BOOLEAN DEFAULT TRUE,
  generated_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_rp_user (user_id)
);

CREATE TABLE IF NOT EXISTS portfolio_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  portfolio_id BIGINT NOT NULL,
  stock_code VARCHAR(20),
  basis VARCHAR(20),
  sector VARCHAR(40),
  target_weight DECIMAL(7,4),
  rationale VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_pi_pf (portfolio_id)
);

CREATE TABLE IF NOT EXISTS holdings_replica (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  stock_code VARCHAR(20),
  quantity DECIMAL(18,6),
  avg_buy_price DECIMAL(18,4),
  eval_amount DECIMAL(18,4),
  profit_rate DECIMAL(7,4),
  currency VARCHAR(3),
  synced_at DATETIME,
  UNIQUE KEY uq_hr (user_id, stock_code)
);

CREATE TABLE IF NOT EXISTS category_sector_map (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  category VARCHAR(40),
  sector VARCHAR(40),
  weight DECIMAL(7,4)
);

CREATE TABLE IF NOT EXISTS peer_benchmarks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  age_band VARCHAR(10),
  gender_band VARCHAR(10),
  asset_band VARCHAR(20),
  asset_category VARCHAR(20),
  avg_ratio DECIMAL(7,4),
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_pb (age_band, gender_band, asset_band, asset_category)
);

CREATE TABLE IF NOT EXISTS rebalancing_products (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_type VARCHAR(20),
  product_name VARCHAR(100),
  provider VARCHAR(40),
  interest_rate DECIMAL(7,4) NULL,
  benefit_desc VARCHAR(500),
  is_active BOOLEAN DEFAULT TRUE,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stock_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  stock_code VARCHAR(20),
  event_type VARCHAR(20),
  event_date DATE,
  title VARCHAR(200),
  detail VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_se (stock_code, event_date)
);

CREATE TABLE IF NOT EXISTS calendar_recommendations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NULL,
  stock_code VARCHAR(20),
  recommend_date DATE,
  source VARCHAR(20),
  reason VARCHAR(200) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recommended_cards (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  card_name VARCHAR(100),
  provider VARCHAR(40),
  benefit_category VARCHAR(40),
  benefit_rate DECIMAL(7,4),
  benefit_desc VARCHAR(200),
  is_active BOOLEAN DEFAULT TRUE,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ========== notification ==========
CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  type VARCHAR(20),
  title VARCHAR(200),
  body VARCHAR(500),
  is_read BOOLEAN DEFAULT FALSE,
  ref_type VARCHAR(20) NULL,
  ref_id BIGINT NULL,
  sent_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_noti_user (user_id)
);

CREATE TABLE IF NOT EXISTS notification_settings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  push_token VARCHAR(255) NULL,
  platform VARCHAR(10),
  notify_trade BOOLEAN DEFAULT TRUE,
  notify_goal BOOLEAN DEFAULT TRUE,
  notify_unfilled BOOLEAN DEFAULT TRUE,
  notify_marketing BOOLEAN DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
