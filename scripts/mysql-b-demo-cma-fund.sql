-- ============================================================================
-- [데모 시드 04] demo 유저 1-10의 CMA를 KRW 10,000,000 + USD 5,000 으로 펀딩
-- 03-persona-seed.sql 직후 실행. 기본 시드에 CMA가 없는 유저(6·7)는 계좌 생성 후,
-- demo 1-10 전원의 잔액을 동일 금액으로 재설정한다. (데모 전용 — 운영 시드 아님)
-- ============================================================================
START TRANSACTION;

-- CMA 계좌가 없는 demo 유저(6·7) 생성 — user_id UNIQUE라 이미 있으면 무시
INSERT IGNORE INTO cma_accounts (user_id, status, opened_at)
VALUES (6, 'ACTIVE', NOW()), (7, 'ACTIVE', NOW());

-- demo 1-10 기존 잔액 초기화
DELETE b FROM cma_balances b
JOIN cma_accounts a ON a.id = b.cma_account_id
WHERE a.user_id BETWEEN 1 AND 10;

-- KRW 10,000,000 @3.5% + USD 5,000 @4.2% 재삽입
INSERT INTO cma_balances (cma_account_id, currency, balance, interest_rate)
SELECT id, 'KRW', 10000000.0000, 0.0350 FROM cma_accounts WHERE user_id BETWEEN 1 AND 10;
INSERT INTO cma_balances (cma_account_id, currency, balance, interest_rate)
SELECT id, 'USD', 5000.0000, 0.0420 FROM cma_accounts WHERE user_id BETWEEN 1 AND 10;

COMMIT;
