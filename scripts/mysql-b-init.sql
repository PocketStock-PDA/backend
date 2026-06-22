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
  ref_type VARCHAR(20) NULL,   -- 출처 대상 테이블: LINKED_BANK_ACCOUNT/LINKED_CARD/LINKED_POINT(collect) · FX_TX(환전) · REVERT(정정, ref_id=원거래 cma_transactions.id 자기참조) · NULL(DEPOSIT/INTEREST 등). 관례 기반(CHECK 없음)
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
-- 갱신은 조건부 원자 UPDATE(balance ± delta, 출금 가드)로 lost update·음수 예수금 차단(#77·#78).
CREATE TABLE IF NOT EXISTS account_balances (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_id BIGINT NOT NULL,
  currency VARCHAR(3) NOT NULL,            -- DOMESTIC=KRW / OVERSEAS=USD (account가 결정하는 파생값)
  balance DECIMAL(18,4) NOT NULL DEFAULT 0,
  -- 미체결 매수 주문에 묶인 금액(증거금 hold, M2). 주문가능/출금가능 = balance − held.
  -- 지정가 PENDING 진입 시 held += total, 체결 시 held·balance 동시 차감, 취소·미체결 시 held 환원(#80, H4).
  held DECIMAL(18,4) NOT NULL DEFAULT 0,
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
  -- 미체결 매도 주문에 묶인 수량(수량 hold, M2 대칭). 매도가능 = quantity − held_quantity.
  -- 지정가 매도 PENDING 진입 시 += qty, 체결 시 held_quantity·quantity 동시차감, 취소·미체결 시 −= qty(#80, H4).
  held_quantity DECIMAL(18,6) NOT NULL DEFAULT 0,
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
  -- 1분 단위 차수(국내·해외 동일, 매 분 1 round) / RESERVED(장외·휴장 예약). 해외는 US 정규장 중 매 분(#101)
  round_no VARCHAR(20) NOT NULL,
  trade_date DATE NOT NULL,
  submit_open DATETIME,
  submit_close DATETIME,
  execute_at DATETIME,
  settle_at DATETIME,
  cancel_deadline DATETIME,
  -- DOMESTIC_TICK(국내 금액매수=현재가+5틱) / MARKET(국내 수량·해외=실행시점 시장가). VWAP 폐기(#101)
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
  -- 경로별 상태머신(ERD-04 §08·§08b). 소수점: RECEIVED|QUEUED|SENT|FILLED|CANCELLED|REJECTED
  -- 온주: RECEIVED|PENDING|FILLED|CANCELLED|REJECTED. 부분체결(PARTIALLY_FILLED)·이월(CARRIED_OVER) 미지원(#101).
  -- 앱 OrderStatus enum이 소스 오브 트루스, 아래 CHECK는 쓰레기 값 차단용 안전망(전이 규칙은 앱+조건부 UPDATE).
  status VARCHAR(20) DEFAULT 'RECEIVED'
    CHECK (status IN ('RECEIVED','QUEUED','SENT','PENDING','FILLED','CANCELLED','REJECTED')),
  source VARCHAR(20),
  round_id BIGINT NULL,
  batch_id BIGINT NULL,
  -- carried_over_count 제거(#101): 이월 폐기 — 1주 미달분은 회사 선부담(ceil)으로 즉시 체결.
  currency VARCHAR(3),
  fail_reason VARCHAR(255) NULL,   -- REJECTED 사유(감사용, H3). 정상 주문은 NULL
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

-- 옴니버스 재고(소수점 정산용, 2차). 현금은 분리됨(operating_cash_*) — 여기는 주식 재고 전용.
CREATE TABLE IF NOT EXISTS operating_account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  stock_code VARCHAR(20) NOT NULL UNIQUE,
  whole_qty INT DEFAULT 0,
  -- firm 순재고 소수부(양방향: 흡수 +/선공급 −, #101). 회사 선부담(ceil)이라 총재고(whole_qty+이값)≥0.
  -- 갱신은 원자 조건부 UPDATE+음수가드(C1~C3 동형). 이동이력은 batch_orders+allocations가 담당.
  fractional_remainder DECIMAL(18,6) DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 회사 현금 원장(복식부기 상대계정, H1 #89). 유저쪽 deposit_transactions/account_balances와 대칭.
-- 거래 역사는 operating_cash_transactions(불변 journal), 현재잔액은 operating_cash_balances(통화당 1행, 가변).
-- ※ 음수 가드 없음: 무상주(웰컴리워드) 매도 등으로 회사 순현금이 음수가 될 수 있음(정당한 회계값).
CREATE TABLE IF NOT EXISTS operating_cash_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  currency VARCHAR(3) NOT NULL,
  tx_type VARCHAR(20) NOT NULL,            -- BUY(회사 현금 수취,+) / SELL(지급,-) / FX(환전 통화풀 leg, 부호로 방향)
  amount DECIMAL(18,4) NOT NULL,           -- +수취 / −지급 (유저 예수금 leg의 반대 부호)
  balance_after DECIMAL(18,4),
  ref_type VARCHAR(20) NULL,               -- order(온주) | allocation | batch(2차 소수점) | fx(환전 회사 통화풀 leg, H5)
  ref_id BIGINT NULL,
  idempotency_key VARCHAR(80) UNIQUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_oct_ccy (currency), INDEX idx_oct_ref (ref_type, ref_id)
);

-- 회사 현금 현재잔액(물질화 projection, 통화당 1행). 갱신은 원자 upsert(balance += delta), 음수 가드 없음.
CREATE TABLE IF NOT EXISTS operating_cash_balances (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  currency VARCHAR(3) NOT NULL,
  balance DECIMAL(18,4) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_ocb_ccy (currency)         -- (currency) = 회사현금 그레인
);

-- 회사 환차익 원장(복식부기 P&L, H5 #96). 환전 스프레드(매매기준율 vs 고객 적용환율 차)의 실현 손익.
-- 회사 통화풀(operating_cash_*)은 from풀 +/to풀 − 2-leg으로 통화별 보존(Σ=0)을 만들고,
-- 그 2-leg이 mid 기준으로 비대칭인 만큼(=회사가 가져가는 스프레드)을 base_currency(KRW)로 여기 1줄 박제.
-- append-only journal(별도 projection 없음 — 누적은 SUM으로 충분, 회사 P&L은 단방향 누적).
CREATE TABLE IF NOT EXISTS operating_fx_pnl (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  fx_transaction_id BIGINT NOT NULL,                 -- 짝지은 환전 거래(fx_transactions.id)
  from_currency VARCHAR(3) NOT NULL,
  to_currency VARCHAR(3) NOT NULL,
  base_currency VARCHAR(3) NOT NULL DEFAULT 'KRW',   -- 손익 측정 통화(기준통화)
  realized_pnl DECIMAL(18,4) NOT NULL,               -- 실현 환차익(base_currency 기준, + = 회사 이익)
  mid_rate DECIMAL(18,6) NOT NULL,                   -- 매매기준율 스냅샷(손익 측정 기준)
  applied_rate DECIMAL(18,6) NOT NULL,               -- 고객 적용환율(buyRate/sellRate)
  idempotency_key VARCHAR(80) UNIQUE,                -- fx:{id}:pnl (재적재 중복 차단)
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ofp_fx (fx_transaction_id)
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
ALTER TABLE operating_fx_pnl     ADD CONSTRAINT fk_ofp_fx    FOREIGN KEY (fx_transaction_id) REFERENCES fx_transactions(id);
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

