-- =====================================================================
-- DB B (원장, 물리 격리) — pocketstock_ledger
-- cma · exchange · trading 테이블 (같은 DB, 로컬 트랜잭션 / append-only + 멱등키)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS pocketstock_ledger CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pocketstock_ledger;
SET NAMES utf8mb4;  -- initdb는 client charset이 latin1 → 한글 이중인코딩 방지

-- ========== cma (멀티커런시 자금풀) ==========
CREATE TABLE IF NOT EXISTS cma_accounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  account_no_enc VARBINARY(255),
  status VARCHAR(20) DEFAULT 'ACTIVE',
  opened_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cma_balances (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cma_account_id BIGINT NOT NULL,
  currency VARCHAR(3) NOT NULL,
  balance DECIMAL(18,4) DEFAULT 0,
  interest_rate DECIMAL(7,4) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_cb (cma_account_id, currency)
);

CREATE TABLE IF NOT EXISTS cma_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  cma_account_id BIGINT NOT NULL,
  currency VARCHAR(3) NOT NULL,
  tx_type VARCHAR(20) NOT NULL,
  source_type VARCHAR(20) NULL,
  amount DECIMAL(18,4) NOT NULL,
  balance_after DECIMAL(18,4),
  ref_type VARCHAR(20) NULL,   -- 출처 대상 테이블: LINKED_BANK_ACCOUNT/LINKED_CARD/LINKED_POINT(collect) · FX_TX(환전) · NULL(DEPOSIT/INTEREST 등). 관례 기반(CHECK 없음)
  ref_id BIGINT NULL,          -- ref_type 테이블의 PK. 타입 내 다출처 합산 시 NULL (E-1)
  idempotency_key VARCHAR(80) UNIQUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_cmt_user (user_id), INDEX idx_cmt_acc (cma_account_id)
);

CREATE TABLE IF NOT EXISTS collection_settings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  source_type VARCHAR(20) NOT NULL,
  source_ref_id BIGINT NULL,
  is_enabled BOOLEAN DEFAULT TRUE,
  threshold DECIMAL(18,4) NOT NULL DEFAULT 10000,  -- 끝전 커팅 기준: 1000 / 5000 / 10000 (ACCOUNT 타입에만 적용)
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_cs (user_id, source_type, source_ref_id)
);

-- 부족금액 자동충전 설정 (SETTLE-006, 1인 1행, on-demand 충전만)
CREATE TABLE IF NOT EXISTS cma_auto_charge_settings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  source_account_ref BIGINT NULL,
  max_charge_per_tx DECIMAL(18,4) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ========== exchange (범용 환전) ==========
CREATE TABLE IF NOT EXISTS fx_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  from_currency VARCHAR(3) NOT NULL,
  to_currency VARCHAR(3) NOT NULL,
  from_amount DECIMAL(18,4) NOT NULL,
  to_amount DECIMAL(18,4) NOT NULL,
  exchange_rate DECIMAL(18,6) NOT NULL,
  fee DECIMAL(18,4) DEFAULT 0,
  trigger_type VARCHAR(20),
  ref_order_id BIGINT NULL,
  status VARCHAR(20) DEFAULT 'DONE',
  idempotency_key VARCHAR(80) UNIQUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_fx_user (user_id)
);

CREATE TABLE IF NOT EXISTS fx_auto_settings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  is_auto_enabled BOOLEAN DEFAULT FALSE,
  use_dollar_first BOOLEAN DEFAULT TRUE,
  max_amount_per_tx DECIMAL(18,4) NULL,
  residual_handling VARCHAR(20) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ========== trading (주문·체결·보유·자동투자·보상) ==========
CREATE TABLE IF NOT EXISTS securities_accounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  market VARCHAR(10) NOT NULL,
  account_no_enc VARBINARY(255),
  status VARCHAR(20) DEFAULT 'ACTIVE',
  is_fractional_enabled BOOLEAN DEFAULT TRUE,
  opened_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_sa (user_id, market)
);

CREATE TABLE IF NOT EXISTS deposit_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  tx_type VARCHAR(20) NOT NULL,
  amount DECIMAL(18,4) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  balance_after DECIMAL(18,4),
  ref_type VARCHAR(20) NULL,
  ref_id BIGINT NULL,
  idempotency_key VARCHAR(80) UNIQUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_dt_user (user_id), INDEX idx_dt_acc (account_id)
);

-- 예수금 현재잔액(물질화 projection, 가변). 계좌당 1행, 계좌개설 때 balance=0으로 생성.
-- 갱신은 조건부 원자 UPDATE(balance ± delta, 출금 음수 가드)로 lost update·음수 예수금 차단(#77·#78).
CREATE TABLE IF NOT EXISTS account_balances (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_id BIGINT NOT NULL,
  currency VARCHAR(3) NOT NULL,            -- DOMESTIC=KRW / OVERSEAS=USD (account가 결정하는 파생값)
  balance DECIMAL(18,4) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_acc_bal (account_id)       -- (account_id) = 잔액 그레인
);

CREATE TABLE IF NOT EXISTS holdings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  stock_code VARCHAR(20) NOT NULL,
  quantity DECIMAL(18,6) DEFAULT 0,
  avg_buy_price DECIMAL(18,4) DEFAULT 0,    -- 종목 통화 기준 평단(국내 KRW / 해외 USD)
  krw_cost_basis DECIMAL(18,4) DEFAULT 0,   -- 원화 누적 취득원가(매수 시 체결환율로 환산). 평균매입환율·환차손익 파생용. 국내는 = quantity*avg_buy_price
  currency VARCHAR(3),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_hold (account_id, stock_code)
);

-- 웰컴 보상(온보딩 완료 후 첫 주식 선물) 지급 이력. user_id UNIQUE = 1인 1회.
CREATE TABLE IF NOT EXISTS welcome_rewards (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  stock_code VARCHAR(20) NOT NULL,
  market VARCHAR(10) NOT NULL,             -- DOMESTIC | OVERSEAS
  quantity DECIMAL(18,6) NOT NULL,         -- 지급 소수점 수량
  grant_price DECIMAL(18,4) NOT NULL,      -- 지급시점 현재가(종목통화)
  budget_krw INT NOT NULL,                 -- 지급 예산(원) = 1000
  currency VARCHAR(3) NOT NULL,            -- KRW | USD
  granted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_welcome_user (user_id)
);

CREATE TABLE IF NOT EXISTS trading_rounds (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  market VARCHAR(10) NOT NULL,
  round_no VARCHAR(20) NOT NULL,
  trade_date DATE NOT NULL,
  submit_open DATETIME,
  submit_close DATETIME,
  execute_at DATETIME,
  settle_at DATETIME,
  cancel_deadline DATETIME,
  pricing_method VARCHAR(20),
  status VARCHAR(20) DEFAULT 'OPEN',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_round (market, round_no, trade_date)
);

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  client_order_id VARCHAR(80) UNIQUE,
  user_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  stock_code VARCHAR(20) NOT NULL,
  exchange VARCHAR(10) NOT NULL,
  side VARCHAR(4) NOT NULL,
  order_type VARCHAR(20),
  order_amount DECIMAL(18,4) NULL,
  order_quantity DECIMAL(18,6) NULL,
  est_quantity DECIMAL(18,6) NULL,
  price DECIMAL(18,4) NULL,
  status VARCHAR(20) DEFAULT 'RECEIVED',
  source VARCHAR(20),
  round_id BIGINT NULL,
  batch_id BIGINT NULL,
  carried_over_count INT DEFAULT 0,
  currency VARCHAR(3),
  requested_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_ord_user (user_id), INDEX idx_ord_status (status), INDEX idx_ord_round (round_id)
);

CREATE TABLE IF NOT EXISTS batch_orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  stock_code VARCHAR(20) NOT NULL,
  exchange VARCHAR(10) NOT NULL,
  side VARCHAR(4) NOT NULL,
  round_id BIGINT NULL,
  pricing_method VARCHAR(20),
  net_fractional_qty DECIMAL(18,6),
  whole_qty INT,
  ls_order_id VARCHAR(40) NULL,
  ls_exec_id VARCHAR(40) NULL,
  status VARCHAR(20) DEFAULT 'PENDING',
  fill_price DECIMAL(18,4) NULL,
  sent_at DATETIME NULL,
  filled_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_bo_round (round_id)
);

CREATE TABLE IF NOT EXISTS allocations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  batch_order_id BIGINT NOT NULL,
  allocated_qty DECIMAL(18,6) NOT NULL,
  allocated_price DECIMAL(18,4) NOT NULL,
  gross_amount DECIMAL(18,4),
  fee DECIMAL(18,4) DEFAULT 0,
  tax DECIMAL(18,4) DEFAULT 0,
  net_amount DECIMAL(18,4),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_alloc_order (order_id), INDEX idx_alloc_batch (batch_order_id)
);

CREATE TABLE IF NOT EXISTS operating_account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  stock_code VARCHAR(20) NOT NULL UNIQUE,
  whole_qty INT DEFAULT 0,
  fractional_remainder DECIMAL(18,6) DEFAULT 0,
  cash_balance DECIMAL(18,4) DEFAULT 0,
  currency VARCHAR(3),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auto_invest_settings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  is_enabled BOOLEAN DEFAULT FALSE,
  is_paused BOOLEAN DEFAULT FALSE,
  keep_collecting_on_pause BOOLEAN DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auto_invest_stocks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  account_id BIGINT NULL,
  stock_code VARCHAR(20) NOT NULL,
  market VARCHAR(10),
  trigger_type VARCHAR(20),
  period VARCHAR(20) NULL,
  period_day INT NULL,
  buy_condition_rate DECIMAL(7,4) NULL,
  amount_type VARCHAR(20),
  buy_amount DECIMAL(18,4) NULL,
  buy_quantity DECIMAL(18,6) NULL,
  currency VARCHAR(3),
  sell_condition_rate DECIMAL(7,4) NULL,
  is_active BOOLEAN DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_ais (user_id, stock_code)
);

-- 종목 마스터 (한투 .mst/.COD 파일 정제 → seed). 단축코드=LS API tr_key.
-- stock_code 전역 UNIQUE: KR 단축코드(숫자6) vs US 심볼(알파벳) 충돌 불가 → 단일 FK 대상.
-- NXT/주간/통합은 별도 행이 아니라 시세 venue 구분(LS unt_* 사용) → 마스터는 종목당 1행.
CREATE TABLE IF NOT EXISTS tradable_stocks (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  stock_code    VARCHAR(20)  NOT NULL UNIQUE,   -- 단축코드(KR 6자리)/심볼(US AAPL) → 주문·표시·LS tr_key
  exchange      VARCHAR(10)  NOT NULL,          -- 거래소: KOSPI/KOSDAQ/NASDAQ/NYSE/AMEX (국내/해외는 거래소값에서 파생)
  standard_code VARCHAR(20)  NULL,              -- 표준코드/ISIN (KR7005930003) — KR 안정 식별자
  stock_name    VARCHAR(100) NOT NULL,          -- 한글종목명
  english_name  VARCHAR(100) NULL,              -- 영문명 (US)
  rt_symbol     VARCHAR(30)  NULL,              -- 실시간 시세 구독 심볼 (US: NASAACB / KR: null이면 stock_code 사용)
  currency      CHAR(3)      NOT NULL,          -- KRW/USD
  sec_type      VARCHAR(10)  NOT NULL,          -- STOCK / ETF
  is_fractional BOOLEAN DEFAULT TRUE,
  is_active     BOOLEAN DEFAULT TRUE,           -- 거래정지/정리매매 제외
  logo_url      VARCHAR(255) NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_ts_code_exchange (stock_code, exchange),  -- orders·batch_orders composite FK 대상(종목-거래소 정합성)
  INDEX idx_ts_sectype (sec_type)
);

CREATE TABLE IF NOT EXISTS stock_categories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  stock_code VARCHAR(20) NOT NULL,
  category VARCHAR(40) NOT NULL,
  `rank` INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_sc (stock_code), INDEX idx_sc_cat (category)
);

CREATE TABLE IF NOT EXISTS whole_share_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  stock_code VARCHAR(20) NOT NULL,
  whole_qty INT NOT NULL,
  triggered_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_wse_user (user_id)
);

CREATE TABLE IF NOT EXISTS daily_valuations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  stock_code VARCHAR(20) NOT NULL,
  eval_date DATE NOT NULL,
  quantity DECIMAL(18,6),
  close_price DECIMAL(18,4),
  eval_amount DECIMAL(18,4),
  profit_amount DECIMAL(18,4),
  profit_rate DECIMAL(7,4),
  currency VARCHAR(3),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_dv (user_id, stock_code, eval_date)
);

CREATE TABLE IF NOT EXISTS rewards (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  reward_type VARCHAR(20) NOT NULL,
  stock_code VARCHAR(20) NULL,
  amount DECIMAL(18,4) NULL,
  quantity DECIMAL(18,6) NULL,
  status VARCHAR(20) DEFAULT 'PENDING',
  fail_reason VARCHAR(200) NULL,
  granted_at DATETIME NULL,
  idempotency_key VARCHAR(80) UNIQUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_rwd_user (user_id)
);

-- =====================================================================
-- 외래키(FK) — 같은 DB B 내 관계 (2026-06-15: cross-domain ref_order_id·stock_code 포함)
-- ※ user_id는 users가 DB A라 cross-DB → FK 불가(값참조).
-- ※ stock_code는 tradable_stocks(같은 DB B)가 보유 → FK 연결(컨벤션: 같은 DB면 cross-domain도 FK).
-- =====================================================================
-- cma same-domain
ALTER TABLE cma_balances         ADD CONSTRAINT fk_cb_acc    FOREIGN KEY (cma_account_id) REFERENCES cma_accounts(id);
ALTER TABLE cma_transactions     ADD CONSTRAINT fk_cmt_acc   FOREIGN KEY (cma_account_id) REFERENCES cma_accounts(id);
-- trading same-domain
ALTER TABLE deposit_transactions ADD CONSTRAINT fk_dt_acc    FOREIGN KEY (account_id) REFERENCES securities_accounts(id);
ALTER TABLE holdings             ADD CONSTRAINT fk_hold_acc  FOREIGN KEY (account_id) REFERENCES securities_accounts(id);
ALTER TABLE orders               ADD CONSTRAINT fk_ord_acc   FOREIGN KEY (account_id) REFERENCES securities_accounts(id);
ALTER TABLE orders               ADD CONSTRAINT fk_ord_round FOREIGN KEY (round_id)   REFERENCES trading_rounds(id);
ALTER TABLE orders               ADD CONSTRAINT fk_ord_batch FOREIGN KEY (batch_id)   REFERENCES batch_orders(id);
ALTER TABLE batch_orders         ADD CONSTRAINT fk_bo_round  FOREIGN KEY (round_id)   REFERENCES trading_rounds(id);
ALTER TABLE allocations          ADD CONSTRAINT fk_alloc_ord FOREIGN KEY (order_id)       REFERENCES orders(id);
ALTER TABLE allocations          ADD CONSTRAINT fk_alloc_bo  FOREIGN KEY (batch_order_id) REFERENCES batch_orders(id);
ALTER TABLE auto_invest_stocks   ADD CONSTRAINT fk_ais_acc   FOREIGN KEY (account_id) REFERENCES securities_accounts(id);
ALTER TABLE whole_share_events   ADD CONSTRAINT fk_wse_acc   FOREIGN KEY (account_id) REFERENCES securities_accounts(id);
-- cross-domain (exchange → trading, 같은 DB B)
ALTER TABLE fx_transactions      ADD CONSTRAINT fk_fx_order  FOREIGN KEY (ref_order_id) REFERENCES orders(id);
-- stock_code → tradable_stocks (trading 내부 종목 마스터, 같은 DB B, 2026-06-15)
-- FK child 컬럼 leading 인덱스 명시 (InnoDB 자동생성 대신 이름·의도 고정)
ALTER TABLE holdings             ADD INDEX idx_hold_stock (stock_code);
ALTER TABLE orders               ADD INDEX idx_ord_exchange (stock_code, exchange);
ALTER TABLE batch_orders         ADD INDEX idx_bo_exchange  (stock_code, exchange);
ALTER TABLE daily_valuations     ADD INDEX idx_dv_stock   (stock_code);
ALTER TABLE auto_invest_stocks   ADD INDEX idx_ais_stock  (stock_code);
ALTER TABLE rewards              ADD INDEX idx_rwd_stock  (stock_code);
-- 단순 FK(stock_code) — 종목 마스터 존재만 검증
ALTER TABLE holdings             ADD CONSTRAINT fk_hold_stock FOREIGN KEY (stock_code) REFERENCES tradable_stocks(stock_code);
ALTER TABLE stock_categories     ADD CONSTRAINT fk_sc_stock   FOREIGN KEY (stock_code) REFERENCES tradable_stocks(stock_code);
ALTER TABLE daily_valuations     ADD CONSTRAINT fk_dv_stock   FOREIGN KEY (stock_code) REFERENCES tradable_stocks(stock_code);
ALTER TABLE operating_account    ADD CONSTRAINT fk_oa_stock   FOREIGN KEY (stock_code) REFERENCES tradable_stocks(stock_code);
ALTER TABLE auto_invest_stocks   ADD CONSTRAINT fk_ais_stock  FOREIGN KEY (stock_code) REFERENCES tradable_stocks(stock_code);
ALTER TABLE rewards              ADD CONSTRAINT fk_rwd_stock  FOREIGN KEY (stock_code) REFERENCES tradable_stocks(stock_code);
-- composite FK(stock_code, exchange) — 종목-거래소 정합성까지 검증 (체결엔진 라우팅 무결성)
-- ※ orders·batch_orders는 exchange NOT NULL이라 composite 적용. auto_invest_stocks는 market(DOMESTIC/OVERSEAS) NULL 허용이라 단순 FK 유지
ALTER TABLE orders               ADD CONSTRAINT fk_ord_stock  FOREIGN KEY (stock_code, exchange) REFERENCES tradable_stocks(stock_code, exchange);
ALTER TABLE batch_orders         ADD CONSTRAINT fk_bo_stock   FOREIGN KEY (stock_code, exchange) REFERENCES tradable_stocks(stock_code, exchange);
