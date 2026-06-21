-- 추가 페르소나(데모용) CMA — 기존 mysql-b-persona-seed.sql(03-persona-seed)을 건드리지 않고 분리 적재.
-- initdb.d에 04-persona-extra.sql로 마운트되어 03 다음에 실행된다(빈 볼륨 첫 기동/`down -v` 후에만 1회).
-- user2(박민준): user1과 동형 구조(KRW + USD 달러풀). account_no_enc=NULL —
-- 시드는 암호문을 생성하지 않고(API 개설분만 채워짐), 조회 시 CmaAccountNoCipher.decrypt가 null-safe 처리.
USE pocketstock_ledger;
SET NAMES utf8mb4;

INSERT INTO cma_accounts (id,user_id,account_no_enc,status,opened_at,created_at,updated_at) VALUES
(2,2,NULL,'ACTIVE','2026-02-05 10:00:00','2026-02-05 10:00:00','2026-02-05 10:00:00');

INSERT INTO cma_balances (id,cma_account_id,currency,balance,interest_rate,created_at,updated_at) VALUES
(3,2,'KRW',1200000.0000,3.5000,'2026-02-05 10:00:00','2026-06-15 09:00:00'),
(4,2,'USD',120.0000,4.2000,'2026-04-02 10:00:00','2026-06-15 09:00:00');  -- 환전 1회 거친 사용자 가정(달러풀)
