-- 페르소나 시드 데이터 — 임혜진 (DB B / pocketstock_ledger)
-- docker-entrypoint-initdb.d/03-persona-seed.sql 로 마운트 → 볼륨 초기화 시 자동 실행
USE pocketstock_ledger;
SET NAMES utf8mb4;

-- tradable_stocks: INSERT IGNORE — 02-seed.sql(tradable_stocks_seed)과 중복 방지
INSERT IGNORE INTO tradable_stocks (id,stock_code,exchange,standard_code,stock_name,english_name,rt_symbol,currency,sec_type,is_fractional,is_active,logo_url,created_at,updated_at) VALUES
(1,'005930','KOSPI','KR7005930003','삼성전자','Samsung Electronics',NULL,'KRW','STOCK',TRUE,TRUE,NULL,'2026-01-01 00:00:00','2026-01-01 00:00:00'),
(2,'000660','KOSPI','KR7000660001','SK하이닉스','SK Hynix',NULL,'KRW','STOCK',TRUE,TRUE,NULL,'2026-01-01 00:00:00','2026-01-01 00:00:00'),
(3,'066570','KOSPI','KR7066570003','LG전자','LG Electronics',NULL,'KRW','STOCK',TRUE,TRUE,NULL,'2026-01-01 00:00:00','2026-01-01 00:00:00'),
(4,'035420','KOSPI','KR7035420009','NAVER','NAVER Corporation',NULL,'KRW','STOCK',TRUE,TRUE,NULL,'2026-01-01 00:00:00','2026-01-01 00:00:00'),
(5,'051910','KOSPI','KR7051910008','LG화학','LG Chem',NULL,'KRW','STOCK',TRUE,TRUE,NULL,'2026-01-01 00:00:00','2026-01-01 00:00:00');

INSERT INTO cma_accounts (id,user_id,account_no_enc,status,opened_at,created_at,updated_at) VALUES
(1,1,NULL,'ACTIVE','2026-01-20 10:00:00','2026-01-20 10:00:00','2026-01-20 10:00:00');

INSERT INTO cma_balances (id,cma_account_id,currency,balance,interest_rate,created_at,updated_at) VALUES
(1,1,'KRW',405490.0000,3.5000,'2026-01-20 10:00:00','2026-06-15 09:00:00');

INSERT INTO collection_settings (id,user_id,source_type,source_ref_id,is_enabled,threshold,created_at,updated_at) VALUES
(1,1,'ACCOUNT',1,TRUE,10000.0000,'2026-01-20 10:00:00','2026-01-20 10:00:00'),
(2,1,'CARD',1,TRUE,10000.0000,'2026-01-20 10:00:00','2026-01-20 10:00:00'),
(3,1,'POINT',1,TRUE,10000.0000,'2026-01-20 10:00:00','2026-01-20 10:00:00');

INSERT INTO cma_auto_charge_settings (id,user_id,is_enabled,source_account_ref,max_charge_per_tx,created_at,updated_at) VALUES
(1,1,FALSE,NULL,NULL,'2026-01-20 10:00:00','2026-01-20 10:00:00');

INSERT INTO cma_transactions (id,user_id,cma_account_id,currency,tx_type,source_type,amount,balance_after,ref_type,ref_id,idempotency_key,created_at) VALUES
(1,1,1,'KRW','DEPOSIT','MANUAL',340000.0000,340000.0000,NULL,NULL,'cma-manual-deposit-20260120','2026-01-20 10:30:00'),
(2,1,1,'KRW','COLLECT','CARD',8340.0000,348340.0000,NULL,NULL,'cma-card-roundup-202602','2026-02-28 23:59:00'),
(3,1,1,'KRW','INTEREST','SYSTEM',1250.0000,349590.0000,NULL,NULL,'cma-interest-202602','2026-02-28 23:59:30'),
(4,1,1,'KRW','COLLECT','ACCOUNT',5600.0000,355190.0000,NULL,NULL,'cma-acct-roundup-202603','2026-03-31 23:59:00'),
(5,1,1,'KRW','COLLECT','CARD',11200.0000,366390.0000,NULL,NULL,'cma-card-roundup-202603','2026-03-31 23:59:10'),
(6,1,1,'KRW','INTEREST','SYSTEM',2100.0000,368490.0000,NULL,NULL,'cma-interest-202603','2026-03-31 23:59:30'),
-- 4월 체크카드 건별 라운드업 (card_transaction id 1,2,3,4,6,7,8,9,10 — id=5 교통카드충전 0원 제외)
(7,1,1,'KRW','COLLECT','CARD',600.0000,369090.0000,NULL,NULL,'cma-card-roundup-tx1','2026-04-02 11:20:00'),
(8,1,1,'KRW','COLLECT','CARD',200.0000,369290.0000,NULL,NULL,'cma-card-roundup-tx2','2026-04-04 15:30:00'),
(9,1,1,'KRW','COLLECT','CARD',400.0000,369690.0000,NULL,NULL,'cma-card-roundup-tx3','2026-04-06 10:30:00'),
(10,1,1,'KRW','COLLECT','CARD',100.0000,369790.0000,NULL,NULL,'cma-card-roundup-tx4','2026-04-07 15:30:00'),
(11,1,1,'KRW','COLLECT','CARD',700.0000,370490.0000,NULL,NULL,'cma-card-roundup-tx6','2026-04-11 09:00:00'),
(12,1,1,'KRW','COLLECT','CARD',800.0000,371290.0000,NULL,NULL,'cma-card-roundup-tx7','2026-04-12 14:20:00'),
(13,1,1,'KRW','COLLECT','CARD',500.0000,371790.0000,NULL,NULL,'cma-card-roundup-tx8','2026-04-14 10:30:00'),
(14,1,1,'KRW','COLLECT','CARD',300.0000,372090.0000,NULL,NULL,'cma-card-roundup-tx9','2026-04-15 14:00:00'),
(15,1,1,'KRW','COLLECT','CARD',400.0000,372490.0000,NULL,NULL,'cma-card-roundup-tx10','2026-04-17 11:00:00'),
(16,1,1,'KRW','COLLECT','POINT',15000.0000,387490.0000,NULL,NULL,'cma-point-transfer-202604','2026-04-30 23:59:10'),
(17,1,1,'KRW','INTEREST','SYSTEM',2800.0000,390290.0000,NULL,NULL,'cma-interest-202604','2026-04-30 23:59:30'),
-- 5월 체크카드 건별 라운드업 (card_transaction id 21,22,23,24,25,27,28,29,30 — id=26 교통카드충전 0원 제외)
(18,1,1,'KRW','COLLECT','CARD',300.0000,390590.0000,NULL,NULL,'cma-card-roundup-tx21','2026-05-02 11:00:00'),
(19,1,1,'KRW','COLLECT','CARD',500.0000,391090.0000,NULL,NULL,'cma-card-roundup-tx22','2026-05-04 08:00:00'),
(20,1,1,'KRW','COLLECT','CARD',700.0000,391790.0000,NULL,NULL,'cma-card-roundup-tx23','2026-05-06 14:20:00'),
(21,1,1,'KRW','COLLECT','CARD',200.0000,391990.0000,NULL,NULL,'cma-card-roundup-tx24','2026-05-07 10:45:00'),
(22,1,1,'KRW','COLLECT','CARD',700.0000,392690.0000,NULL,NULL,'cma-card-roundup-tx25','2026-05-09 15:00:00'),
(23,1,1,'KRW','COLLECT','CARD',600.0000,393290.0000,NULL,NULL,'cma-card-roundup-tx27','2026-05-14 13:00:00'),
(24,1,1,'KRW','COLLECT','CARD',700.0000,393990.0000,NULL,NULL,'cma-card-roundup-tx28','2026-05-16 10:30:00'),
(25,1,1,'KRW','COLLECT','CARD',400.0000,394390.0000,NULL,NULL,'cma-card-roundup-tx29','2026-05-19 14:20:00'),
(26,1,1,'KRW','COLLECT','CARD',400.0000,394790.0000,NULL,NULL,'cma-card-roundup-tx30','2026-05-21 15:00:00'),
(27,1,1,'KRW','COLLECT','ACCOUNT',4300.0000,399090.0000,NULL,NULL,'cma-acct-roundup-202605','2026-05-31 23:59:10'),
(28,1,1,'KRW','INTEREST','SYSTEM',3100.0000,402190.0000,NULL,NULL,'cma-interest-202605','2026-05-31 23:59:30'),
-- 6월 체크카드 건별 라운드업 (card_transaction id 41,42,43,44,46,47 — id=45 교통카드충전 0원 제외)
(29,1,1,'KRW','COLLECT','CARD',700.0000,402890.0000,NULL,NULL,'cma-card-roundup-tx41','2026-06-02 11:10:00'),
(30,1,1,'KRW','COLLECT','CARD',900.0000,403790.0000,NULL,NULL,'cma-card-roundup-tx42','2026-06-04 15:20:00'),
(31,1,1,'KRW','COLLECT','CARD',500.0000,404290.0000,NULL,NULL,'cma-card-roundup-tx43','2026-06-06 10:20:00'),
(32,1,1,'KRW','COLLECT','CARD',200.0000,404490.0000,NULL,NULL,'cma-card-roundup-tx44','2026-06-09 15:30:00'),
(33,1,1,'KRW','COLLECT','CARD',600.0000,405090.0000,NULL,NULL,'cma-card-roundup-tx46','2026-06-13 13:00:00'),
(34,1,1,'KRW','COLLECT','CARD',400.0000,405490.0000,NULL,NULL,'cma-card-roundup-tx47','2026-06-15 09:00:00');

INSERT INTO securities_accounts (id,user_id,market,account_no_enc,status,is_fractional_enabled,opened_at,created_at,updated_at) VALUES
(1,1,'DOMESTIC',NULL,'ACTIVE',TRUE,'2026-02-10 09:00:00','2026-02-10 09:00:00','2026-02-10 09:00:00');

INSERT INTO deposit_transactions (id,user_id,account_id,tx_type,amount,currency,balance_after,ref_type,ref_id,idempotency_key,created_at) VALUES
(1,1,1,'DEPOSIT',500000.0000,'KRW',500000.0000,NULL,NULL,'sec-init-deposit-20260210','2026-02-10 09:05:00'),
(2,1,1,'BUY',-352500.0000,'KRW',147500.0000,NULL,NULL,'sec-buy-005930-5sh-20260210','2026-02-10 09:10:00');

-- 예수금 현재잔액 = deposit_transactions 최종 balance_after(147,500). 계좌당 1행(account_id=1, DOMESTIC/KRW).
INSERT INTO account_balances (id,account_id,currency,balance,created_at,updated_at) VALUES
(1,1,'KRW',147500.0000,'2026-02-10 09:10:00','2026-02-10 09:10:00');

INSERT INTO holdings (id,user_id,account_id,stock_code,quantity,avg_buy_price,krw_cost_basis,currency,created_at,updated_at) VALUES
(1,1,1,'005930',5.000000,70500.0000,352500.0000,'KRW','2026-02-10 09:10:00','2026-02-10 09:10:00');

INSERT INTO auto_invest_settings (id,user_id,is_enabled,is_paused,keep_collecting_on_pause,created_at,updated_at) VALUES
(1,1,FALSE,FALSE,TRUE,'2026-01-20 10:00:00','2026-01-20 10:00:00');
