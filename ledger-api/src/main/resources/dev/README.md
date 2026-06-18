# DEV 테스트 페이지

백엔드 개발자용 **수동 테스트 하니스**. 프론트 없이 시세/실시간 API를 눈으로 확인하려고 둔 임시 페이지다.
**`local` 프로파일에서만** 등록된다(운영 미노출).

## 실행

```bash
# bootRun은 local 프로파일이 기본 — LS 키 등은 application-local.yml(gitignore)에서 주입된다
./gradlew :ledger-api:bootRun
```

접속: <http://localhost:8082/dev>

> `local`이 아니면 `/dev`·`/dev/token`은 404다.

## 사용법

1. **[토큰 발급]** 클릭 — 로그인(member) 도메인 없이 테스트용 JWT를 발급해 `localStorage`에 저장한다.
2. **종목코드** 입력(기본 `005930`) → **[현재가 조회]** — t1102 결과를 표 + raw JSON으로 보여준다.
   - 종목을 바꿔(`000660`, `005380` 등) 실제 LS 값이 바뀌는지 확인.
   - 토큰이 없으면 조회 시 자동으로 먼저 발급한다.

## 엔드포인트

| URL | 설명 |
|---|---|
| `GET /dev` | 테스트 페이지(HTML) |
| `GET /dev/token?userId=1` | 테스트용 JWT 발급(`JwtProvider`). 보호 API 호출용 |

## 메모

- 인증: `/api/trading/**`는 `Authorization: Bearer {JWT}`를 요구 → 그래서 `/dev/token`으로 토큰을 만든다.
- 확장 예정: 실시간시세(STOMP) 붙으면 `/topic/stock/trade/{code}` 구독 테스트 영역을 이 페이지에 추가한다.
- 정의: `dev/dev.html`(정적 페이지) + `com.pocketstock.ledger.dev.DevController`(`@Profile("local")`).
