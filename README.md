# PocketStock Backend

잔돈·포인트 → 소수점 투자 플랫폼. **MSA(8 마이크로서비스)** · Gradle 멀티모듈 monorepo.

## 스택
- Java 17 · Spring Boot 3.3.x · Spring Cloud 2023.0.x
- Gradle 멀티모듈 · MyBatis · MySQL · Redis · Kafka
- API Gateway(Spring Cloud Gateway) · OpenFeign · Resilience4j
- **Eureka 미사용** — Docker Compose 내부 DNS로 서비스 디스커버리

## 서비스 구성 (8개)

> **DB는 2개로 통합** — DB-per-service의 분산 복잡도를 줄인 타협. 서비스(코드)는 8개 독립, DB만 2개. 같은 DB 내 JOIN 허용(쓰기는 자기 테이블만), 돈 원장만 물리 격리.
> - **`pocketstock_main`** (DB A, mysql-a:3306) — user·asset·budget·portfolio·notif
> - **`pocketstock_ledger`** (DB B, mysql-b:3307, 원장) — cma·exchange·trading

| 모듈 | 포트 | DB | 책임 |
|---|---|---|---|
| `api-gateway` | 8080 | — | 라우팅·JWT 1차 검증 |
| `user` | 8081 | pocketstock_main (DB A) | 인증·약관·계좌비번 |
| `asset` | 8082 | pocketstock_main (DB A) | 마이데이터 사본·소비분석 |
| `portfolio` | 8083 | pocketstock_main (DB A) | 추천·리밸런싱·캘린더·카드 |
| `budget` | 8084 | pocketstock_main (DB A) | 가계부·절약금 |
| `notification` | 8085 | pocketstock_main (DB A) | 알림 sink → FCM |
| `cma` | 8086 | **pocketstock_ledger** (DB B 원장) | 멀티커런시 자금풀·이자·이체 |
| `trading` | 8087 | **pocketstock_ledger** (DB B 원장) | 주문·체결·보유·자동투자·보상 |
| `exchange` | 8088 | **pocketstock_ledger** (DB B 원장) | 범용 환전 |

`common` = 공통 라이브러리(이벤트 DTO·응답포맷·유틸), 각 서비스가 의존.

## 구조
```
backend/
├ settings.gradle / build.gradle    # 멀티모듈 루트 (Java17·Boot/Cloud BOM)
├ docker-compose.yml                # 로컬 인프라 (MySQL×2·Redis·Kafka)
├ scripts/                          # DB 초기화 SQL
├ common/                           # 공통 라이브러리
├ api-gateway/
├ user/ asset/ portfolio/ budget/ notification/   # 일반 → DB A (pocketstock_main)
└ cma/ trading/ exchange/                          # 원장 → DB B (pocketstock_ledger)
```

## 로컬 실행
```bash
# 1) 인프라 기동 (MySQL-A:3306 / MySQL-B:3307 / Redis:6379 / Kafka:9092)
docker compose up -d

# 2) 전체 빌드
./gradlew build

# 3) 특정 서비스 실행
./gradlew :trading:bootRun

# 4) 특정 서비스만 빌드
./gradlew :trading:build
```

> DB·Redis·Kafka 접속정보 등 시크릿은 `application-local.yml`/환경변수로 주입(커밋 금지, `.gitignore` 처리됨).

## 아키텍처
- **DB 2개 통합**: `pocketstock_main`(DB A 일반) / `pocketstock_ledger`(DB B 원장). 같은 DB 내 JOIN 허용, 쓰기는 자기 테이블만(모듈러 모놀리스 규율). DB-per-service의 분산 복잡도 타협.
- **원장 격리(DB B)**: cma·exchange·trading = append-only + 멱등키 + REVERT. 물리 격리(감사·정합성).
- **통신**: 동기(OpenFeign+Resilience4j) + 비동기(Kafka 이벤트)
- **Saga(A↔B 경계만)**: 계좌개설(User A ↔ CMA·Trading B) · 절약금(Budget A → CMA B). ※환전·매수자금은 같은 DB B라 **로컬 트랜잭션**(Saga 불필요)
- 설계 문서: `../docs` (ERD·기능명세·아키텍처)
