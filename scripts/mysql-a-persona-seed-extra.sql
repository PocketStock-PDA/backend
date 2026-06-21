-- 추가 페르소나(데모용) — 기존 mysql-a-persona-seed.sql(03-persona-seed)을 건드리지 않고 분리 적재.
-- initdb.d에 04-persona-extra.sql로 마운트되어 03 다음에 실행된다(빈 볼륨 첫 기동/`down -v` 후에만 1회).
-- user2(박민준): CMA 데모용 2번째 사용자. password_hash는 user1과 동일한 공개 예시 bcrypt(시크릿 아님).
USE pocketstock_main;
SET NAMES utf8mb4;

INSERT INTO users (id,username,password_hash,name,phone,ci,birth_date,gender,device_id,status,created_at,updated_at,deleted_at) VALUES
(2,'minjun88','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lM5i','박민준','010-2345-6789',NULL,'1988-03-11','MALE',NULL,'ACTIVE','2026-02-01 10:00:00','2026-02-01 10:00:00',NULL);
