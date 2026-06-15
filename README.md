# PocketStock Backend

잔돈·포인트 → 소수점 투자 플랫폼. **모듈러 모놀리스** · Gradle 멀티모듈 monorepo.

## 스택
- Java 17 · Spring Boot 3.3.x · Spring Cloud 2023.0.x
- Gradle 멀티모듈 · MyBatis · MySQL · Redis · Kafka
- OpenFeign · Resilience4j
- JWT 인증(순수 Servlet Filter, spring-security 미사용)

## 구성 — 4모듈 (실행 앱 2개)

도메인 8개를 패키지로 분리하되, 배포는 DB 경계 따라 2개로 묶은 **의도된 모듈러 모놀리스**.
과한 MSA의 분산 복잡도(분산 트랜잭션·운영 부담)를 피하면서 원장 격리와 도메인 경계는 유지한다.

| 모듈 | 종류 | 포트 | DB | 책임 |
|---|---|---|---|---|
| `common` | 라이브러리 | — | — | 응답·예외·유틸 |
| `user` | 라이브러리 | — | — | 회원 도메인(member, DB A) + JWT 인증(security, 공유) |
| `core-api` | 실행(bootJar) | 8081 | pocketstock_main (DB A) | user·asset·budget·portfolio·notification |
| `ledger-api` | 실행(bootJar) | 8082 | pocketstock_ledger (DB B 원장) | cma·exchange·trading |

> **DB 2개** — `pocketstock_main`(DB A, mysql-a:3306) / `pocketstock_ledger`(DB B 원장, mysql-b:3307).
> 같은 DB 내 JOIN·FK 허용, DB A↔B 경계만 이벤트/Saga.

### user 모듈 공유 구조
- `user.member` — 회원가입·로그인·약관·계좌비번 (DB A). **core-api만** 스캔.
- `user.security` — JWT 검증(무상태, `@CurrentUserId`). **core·ledger 둘 다** 스캔.
- → ledger는 회원 도메인(member)을 스캔하지 않아 DB A 결합이 없다.

## 구조
```
backend/
├ settings.gradle / build.gradle    # 멀티모듈 루트 (Java17·Boot/Cloud BOM)
├ docker-compose.yml                # 로컬 인프라 (MySQL×2·Redis·Kafka)
├ scripts/                          # DB 초기화 SQL (테이블 + FK)
├ common/                           # 라이브러리 — 응답·예외
├ user/                             # 라이브러리 — 회원(member) + JWT(security)
│   └ src/main/java/com/pocketstock/user/{member, security}
├ core-api/                         # 실행 → DB A
│   └ src/main/java/com/pocketstock/core/{asset, budget, portfolio, notification}
└ ledger-api/                       # 실행 → DB B(원장)
    └ src/main/java/com/pocketstock/ledger/{cma, exchange, trading}
```

각 도메인 패키지 = `controller / service / domain / mapper / dto`.

## 로컬 실행
```bash
# 1) 인프라 기동 (MySQL-A:3306 / MySQL-B:3307 / Redis:6379 / Kafka:9092)
docker compose up -d

# 2) 전체 빌드
./gradlew build

# 3) 실행 앱 기동
./gradlew :core-api:bootRun     # DB A (8081)
./gradlew :ledger-api:bootRun   # DB B (8082)
```

> DB·Redis·Kafka 접속정보 등 시크릿은 환경변수/`application-local.yml`로 주입(커밋 금지).

## 아키텍처
- **모듈러 모놀리스**: 도메인은 패키지로 분리, 배포는 DB 경계 따라 2개(core-api/ledger-api). 공통 인증(JWT)은 `user.security` 라이브러리로 공유.
- **DB 2개**: `pocketstock_main`(DB A 일반) / `pocketstock_ledger`(DB B 원장). 같은 DB 내 JOIN·FK 허용, 쓰기는 자기 테이블만.
- **원장 격리(DB B)**: cma·exchange·trading = append-only + 멱등키 + REVERT. 물리 격리(감사·정합성).
- **통신**: 동기(OpenFeign+Resilience4j) + 비동기(Kafka 이벤트). DB A↔B 경계만.
- **인증**: JWT를 순수 Servlet Filter(`user.security`)로 검증 → `@CurrentUserId`로 주입. spring-security 미사용.
- 설계 문서: `../docs` (ERD·기능명세·API명세)
