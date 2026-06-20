-- =====================================================================
-- DB A (일반) — pocketstock_main
-- user · asset · budget · portfolio · notification 테이블 (같은 DB, JOIN 허용)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS pocketstock_main CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pocketstock_main;
SET NAMES utf8mb4;  -- initdb는 client charset이 latin1 → 한글 이중인코딩 방지

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
  device_id VARCHAR(255) NULL,        -- 간편(PIN) 로그인 기기 식별자(사용자당 1기기, 최근 로그인 기준)
  status VARCHAR(20) DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME NULL,
  UNIQUE KEY uq_users_device (device_id)   -- 기기 1대=계정 1명(NULL은 다중 허용)
);

CREATE TABLE IF NOT EXISTS user_auth_methods (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  method_type VARCHAR(10),
  secret_hash VARCHAR(255),
  is_active BOOLEAN DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_uam_user_type (user_id, method_type),  -- 사용자당 방식별 1건(upsert 기준)
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
-- 연동 가능 기관 카탈로그 (카테고리×회사, user 무관 공용). 연동 화면 picker 출처(LINK-001)
CREATE TABLE IF NOT EXISTS institution_master (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  category VARCHAR(20) NOT NULL,             -- BANK / CARD / SECURITIES / POINT
  company_code VARCHAR(30) NOT NULL UNIQUE,  -- SHINHAN_BANK / KB_CARD / MIRAE_SEC / NAVER_POINT
  company_name VARCHAR(40) NOT NULL,
  logo_url VARCHAR(255) NULL,
  is_active BOOLEAN DEFAULT TRUE,
  sort_order INT DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- user <-> 회사 커넥션 노드 (연동상태·새로고침·연결끊기 단위). 자식(linked_*)이 매달림
CREATE TABLE IF NOT EXISTS linked_institutions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  institution_master_id BIGINT NOT NULL,    -- 어느 회사(카탈로그 참조)
  link_status VARCHAR(20),                   -- LINKED / AVAILABLE / MAINTENANCE / FAILED
  linked_at DATETIME NULL,
  last_synced_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_inst (user_id, institution_master_id),
  INDEX idx_li_master (institution_master_id)
);

-- 은행 계좌(예금/적금/입출금) 잔액 사본. 예적금만 interest_rate/start_date/maturity_date 채움
CREATE TABLE IF NOT EXISTS linked_bank_accounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  institution_id BIGINT NOT NULL,           -- → linked_institutions
  account_type VARCHAR(20),                  -- DEPOSIT(예금) / SAVINGS(적금) / DEMAND(입출금)
  account_name VARCHAR(60),                  -- 상품 표시명 (KB Star 정기예금)
  account_no_enc VARBINARY(255),             -- AES-256 (SEC-001). 이체(잔돈수집)용
  balance DECIMAL(18,4),                      -- 원금(가입 원금 또는 입출금 잔액, 만기 이자 미포함)
  currency VARCHAR(3),                        -- KRW / USD
  interest_rate DECIMAL(7,4) NULL,           -- 예적금만 (계약 약정 금리, 가입기간별 상이)
  start_date DATE NULL,                      -- 예적금만 (가입일)
  maturity_date DATE NULL,                   -- 예적금만 (만기일)
  is_dormant BOOLEAN DEFAULT FALSE,
  last_synced_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_lba_user (user_id), INDEX idx_lba_inst (institution_id)
);

-- 보유 카드 사본(메타만, lean). 라운드업 ON/OFF는 collection_settings(DB-B)가 관리
CREATE TABLE IF NOT EXISTS linked_cards (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  institution_id BIGINT NOT NULL,           -- → linked_institutions (카드사)
  card_name VARCHAR(60),
  card_type VARCHAR(10),                     -- CREDIT(신용) / CHECK(체크)
  masked_no VARCHAR(20),                     -- 1234-****-****-5678
  payment_account_id BIGINT NULL,            -- → linked_bank_accounts (결제/출금 계좌)
  card_master_id BIGINT NULL,               -- → cards (추천 카드 마스터, NULL=마스터 미매칭)
  last_synced_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_lc_user (user_id), INDEX idx_lc_inst (institution_id)
);

-- 외부(3rd-party) 포인트 잔액 사본 (네이버·토스). 마이신한포인트(1st-party)는 별개
CREATE TABLE IF NOT EXISTS linked_points (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  institution_id BIGINT NOT NULL,           -- → linked_institutions (포인트사)
  point_name VARCHAR(40),                    -- 네이버포인트 / 토스포인트
  balance BIGINT,                            -- 포인트 잔액 (1P=1원, 정수)
  last_synced_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_lp_user (user_id), INDEX idx_lp_inst (institution_id)
);

-- 타 증권사 예수금(현금) 사본. 보유 종목은 external_holdings (2계층 패턴)
CREATE TABLE IF NOT EXISTS linked_securities (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  institution_id BIGINT NOT NULL,           -- → linked_institutions (증권사)
  deposit_cash DECIMAL(18,4),                -- 예수금(현금 잔액)
  currency VARCHAR(3),                        -- KRW / USD (통화별 row)
  last_synced_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_lsec_user (user_id), INDEX idx_lsec_inst (institution_id)
);

CREATE TABLE IF NOT EXISTS card_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  card_id BIGINT,                            -- → linked_cards (구 linked_account_id)
  merchant_name VARCHAR(100),
  mcc VARCHAR(10),
  category VARCHAR(40),
  amount DECIMAL(18,4),
  paid_at DATETIME,
  is_cancelled BOOLEAN DEFAULT FALSE,
  is_roundup_collected BOOLEAN NOT NULL DEFAULT FALSE,
  roundup_collected_at DATETIME NULL,
  roundup_amount DECIMAL(18,4) NULL,         -- 이 거래 라운드업 금액(미수확 NULL)
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
  institution_id BIGINT,                     -- → linked_institutions (증권사, 구 broker_name 정규화)
  stock_code VARCHAR(20),
  stock_name VARCHAR(100),
  quantity DECIMAL(18,6),
  eval_amount DECIMAL(18,4),
  synced_at DATETIME,
  INDEX idx_eh_user (user_id), INDEX idx_eh_inst (institution_id)
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

-- rebalancing_products 제거(2026-06-18): 갈아타기(REBAL-005·006)·대출 폐지로 삭제

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

CREATE TABLE IF NOT EXISTS dividend_stocks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  stock_code VARCHAR(20) NOT NULL,
  stock_name VARCHAR(100) NOT NULL,
  category VARCHAR(40),
  market VARCHAR(10) NOT NULL,
  dividend_yield DECIMAL(5,2) NOT NULL,
  tags VARCHAR(200),
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_ds (stock_code)
);

CREATE TABLE IF NOT EXISTS dividend_tag_criteria (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tag_name VARCHAR(40) NOT NULL,
  condition_desc VARCHAR(200),
  UNIQUE KEY uq_dtc (tag_name)
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

CREATE TABLE IF NOT EXISTS cards (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  card_name VARCHAR(100) NOT NULL,
  card_type VARCHAR(10) NOT NULL COMMENT '신용|체크',
  provider VARCHAR(40) NOT NULL,
  annual_fee_domestic INT DEFAULT 0,
  annual_fee_global INT DEFAULT 0,
  image_url VARCHAR(255),
  is_active BOOLEAN DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_card (card_name, provider)
);

CREATE TABLE IF NOT EXISTS card_benefits (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  card_id BIGINT NOT NULL,
  benefit_category VARCHAR(40) NOT NULL,
  benefit_rate DECIMAL(7,4),
  benefit_desc VARCHAR(200),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_cb (card_id, benefit_category),
  INDEX idx_cb_card (card_id),
  CONSTRAINT fk_cb_card FOREIGN KEY (card_id) REFERENCES cards (id)
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
  INDEX idx_noti_user (user_id),
  -- 알림센터 목록: WHERE user_id=? ORDER BY created_at DESC 를 인덱스로 처리(filesort 제거)
  INDEX idx_noti_user_created (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS notification_settings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  push_token TEXT NULL,               -- FCM 토큰 또는 Web Push(VAPID) 구독 JSON(stringify)
  platform VARCHAR(10),
  notify_trade BOOLEAN DEFAULT TRUE,
  notify_goal BOOLEAN DEFAULT TRUE,
  notify_unfilled BOOLEAN DEFAULT TRUE,
  notify_marketing BOOLEAN DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =====================================================================
-- 외래키(FK) — 같은 DB A 내 관계 (2026-06-15: cross-domain user_id 포함)
-- ※ DB B의 user_id는 users가 DB A라 cross-DB → FK 불가(값참조), 여기 없음
-- =====================================================================
-- same-domain
ALTER TABLE user_auth_methods    ADD CONSTRAINT fk_uam_user   FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE account_passwords    ADD CONSTRAINT fk_ap_user    FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE terms_agreements     ADD CONSTRAINT fk_terms_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE linked_institutions  ADD CONSTRAINT fk_li_master  FOREIGN KEY (institution_master_id) REFERENCES institution_master(id);
ALTER TABLE linked_bank_accounts ADD CONSTRAINT fk_lba_inst   FOREIGN KEY (institution_id)    REFERENCES linked_institutions(id);
ALTER TABLE linked_cards         ADD CONSTRAINT fk_lc_inst       FOREIGN KEY (institution_id)    REFERENCES linked_institutions(id);
ALTER TABLE linked_cards         ADD CONSTRAINT fk_lc_payacc     FOREIGN KEY (payment_account_id) REFERENCES linked_bank_accounts(id);
ALTER TABLE linked_cards         ADD CONSTRAINT fk_lc_card_master FOREIGN KEY (card_master_id)    REFERENCES cards(id);
ALTER TABLE linked_points        ADD CONSTRAINT fk_lp_inst    FOREIGN KEY (institution_id)    REFERENCES linked_institutions(id);
ALTER TABLE linked_securities    ADD CONSTRAINT fk_lsec_inst  FOREIGN KEY (institution_id)    REFERENCES linked_institutions(id);
ALTER TABLE external_holdings    ADD CONSTRAINT fk_eh_inst    FOREIGN KEY (institution_id)    REFERENCES linked_institutions(id);
ALTER TABLE card_transactions    ADD CONSTRAINT fk_ct_card    FOREIGN KEY (card_id)           REFERENCES linked_cards(id);
ALTER TABLE portfolio_items      ADD CONSTRAINT fk_pi_pf      FOREIGN KEY (portfolio_id)      REFERENCES recommended_portfolios(id);
-- cross-domain (user_id → users, 같은 DB A)
ALTER TABLE linked_institutions     ADD CONSTRAINT fk_li_user   FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE linked_bank_accounts    ADD CONSTRAINT fk_lba_user  FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE linked_cards            ADD CONSTRAINT fk_lc_user   FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE linked_points           ADD CONSTRAINT fk_lp_user   FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE linked_securities       ADD CONSTRAINT fk_lsec_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE card_transactions       ADD CONSTRAINT fk_ct_user   FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE spending_analysis       ADD CONSTRAINT fk_sa_user   FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE external_holdings       ADD CONSTRAINT fk_eh_user   FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE budget_goals            ADD CONSTRAINT fk_bg_user   FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE budget_savings          ADD CONSTRAINT fk_bs_user   FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE recommended_portfolios  ADD CONSTRAINT fk_rp_user   FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE holdings_replica        ADD CONSTRAINT fk_hr_user   FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE calendar_recommendations ADD CONSTRAINT fk_cr_user  FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE notifications           ADD CONSTRAINT fk_noti_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE notification_settings   ADD CONSTRAINT fk_ns_user   FOREIGN KEY (user_id) REFERENCES users(id);
