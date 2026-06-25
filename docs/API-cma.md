# PocketStock API 명세 — CMA

> 공통 헤더: `Authorization: Bearer {accessToken}` | `Content-Type: application/json`

## 계좌

### POST `/api/cma/account`

CMA 계좌 개설 (서비스 진입 게이트). 온보딩 마지막 단계에서 호출하며, 본인인증·약관·계좌비밀번호(회원 도메인)는 선행 단계에서 처리된다. **멱등** — 이미 있으면 기존 계좌를 반환한다(오류 아님). 종합계좌(`/api/trading/accounts`)와는 별개 계좌다.

- **Request Headers**: Authorization: Bearer {accessToken}
- **Request Body**: 없음
- **HTTP Status Code**: 200 OK / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "CMA 계좌 개설 성공",
  "data": {
    "cmaAccountNo": "98765-90",
    "openedAt": "2026-06-19T01:23:00",
    "balances": [
      { "currency": "KRW", "balance": 0, "interestRate": 0.0350 }
    ]
  }
}
```

> 달러(USD) 지갑은 개설 시 만들지 않고 첫 환전 때 생성된다.

---

## 홈

### GET `/api/cma/home`

홈 대시보드 (CMA잔액 + 수집가능 잔돈)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 401 Unauthorized / 404 Not Found
- `cmaBalance`: 통화별 잔액 Map(KRW·USD 모두 보유 시 둘 다 내려감).
- `collectedSources`: "수집한 잔돈" 내역 — **이번 달 카드 라운드업 수집액만** 노출(`CARD` 단건, 수집액>0일 때만). 계좌 끝전/포인트 전환은 노출하지 않는다. 이번 달 카드 수집이 없으면 빈 배열.
- `collectSources`: "**수집 가능한** 잔돈"(아직 안 모은 끝전/라운드업/포인트). `collectedSources`(이미 모은 내역)와 구분.

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "홈 대시보드 조회 성공",
  "data": {
    "cmaBalance": { "KRW": 37840.0, "USD": 45.67 },
    "interestRate": 0.0350,
    "todayInterest": 3,
    "collectedSources": [
      { "sourceType": "CARD", "name": "카드 사용 잔돈", "amount": 3400.0 }
    ],
    "collectSources": [
      { "sourceType": "ACCOUNT", "name": "신한은행",       "amount": 320.0  },
      { "sourceType": "CARD",    "name": "SOL트래블",      "amount": 870.0  },
      { "sourceType": "POINT",   "name": "마이신한포인트",  "amount": 1240.0 }
    ],
    "totalCollectable": 2430.0
  }
}
```

---

## 잔돈수집

> **수집 대상·금액은 요청 바디로 받지 않는다.** 어떤 소스를 얼마나 모을지는 `PUT /collect/settings`로 저장된 활성 소스(ON)와 자산 도메인(core-api) 데이터에서 서버가 계산한다. 단건 API도 바디가 없다.
> **멱등키(선택)**: `X-Idempotency-Key` 헤더를 보내면 동일 키 재요청 시 중복 적립이 방지된다(없으면 서버가 1회용 키 자동 생성).
> **적립 금액 산정**: 끝전 = 연동 계좌 잔액 % 설정 threshold(1000/5000/10000), 라운드업 = 카드 결제액 천원 올림 차액, 포인트 = 전환 가능 포인트(1P=1원).

### POST `/api/cma/collect`

잔돈 모으기 실행 (통합) — 활성 소스(ACCOUNT/CARD/POINT) 전체를 **독립 실행**한다. 한 소스가 실패해도 나머지는 진행되는 **부분 성공** 모델이라, 응답은 소스별 결과 배열이다.

- **Request Headers**: Authorization: Bearer {accessToken}
- **Request Body**: 없음
- **HTTP Status Code**: 200 OK / 401 Unauthorized / 404 Not Found(CMA 계좌 없음)

**Response Body** — `status`: `SUCCESS`(적립됨) / `SKIPPED`(소스 비활성·수집 잔돈 없음) / `FAILED`(예기치 못한 오류)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "잔돈 모으기 실행 완료",
  "data": [
    { "sourceType": "ACCOUNT", "status": "SUCCESS", "amount": 7500, "balanceAfter": 412990, "errorMessage": null },
    { "sourceType": "CARD",    "status": "SUCCESS", "amount": 280,  "balanceAfter": 413270, "errorMessage": null },
    { "sourceType": "POINT",   "status": "SKIPPED", "amount": 0,    "balanceAfter": null,   "errorMessage": "수집 가능한 잔돈이 없습니다." }
  ]
}
```

---

### POST `/api/cma/collect/account`

계좌 끝전 적립 — 활성 ACCOUNT 계좌들의 (잔액 % threshold) 합산을 적립한다.

- **Request Headers**: Authorization: Bearer {accessToken} · (선택) X-Idempotency-Key: {uuid}
- **Request Body**: 없음
- **HTTP Status Code**: 200 OK / 400 Bad Request(수집 잔돈 없음·소스 비활성) / 401 Unauthorized / 404 Not Found

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "계좌 끝전 적립 완료",
  "data": { "sourceType": "ACCOUNT", "status": "SUCCESS", "amount": 7500, "balanceAfter": 412990, "errorMessage": null }
}
```

---

### POST `/api/cma/collect/card`

카드 라운드업 적립 — 미수집 카드 거래의 천원 올림 차액 합산. 적립 후 해당 카드 거래를 수집 완료로 표시한다.

- **Request Headers**: Authorization: Bearer {accessToken} · (선택) X-Idempotency-Key: {uuid}
- **Request Body**: 없음
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized / 404 Not Found

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "카드 라운드업 적립 완료",
  "data": { "sourceType": "CARD", "status": "SUCCESS", "amount": 280, "balanceAfter": 413270, "errorMessage": null }
}
```

---

### POST `/api/cma/collect/point`

포인트 전환 적립 — 전환 가능 포인트를 CMA 원화 풀로 입금한다.

- **Request Headers**: Authorization: Bearer {accessToken} · (선택) X-Idempotency-Key: {uuid}
- **Request Body**: 없음
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized / 404 Not Found

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "포인트 전환 적립 완료",
  "data": { "sourceType": "POINT", "status": "SUCCESS", "amount": 5000, "balanceAfter": 418270, "errorMessage": null }
}
```

---

### GET `/api/cma/collect/settings`

적립 소스 설정 조회 — 사용자가 등록한 수집 소스(카드/계좌/포인트) 설정 목록을 반환한다. 설정한 적이 없는 소스는 포함되지 않는다(빈 배열 반환).

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "수집 소스 설정 조회 성공",
  "data": [
    {
      "sourceType": "CARD",
      "sourceRefId": 1,
      "enabled": true,
      "threshold": null
    },
    {
      "sourceType": "ACCOUNT",
      "sourceRefId": 3,
      "enabled": true,
      "threshold": 5000
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `sourceType` | string | `ACCOUNT` / `CARD` / `POINT` / `FX` |
| `sourceRefId` | number | 소스 참조 ID (카드: `linked_cards.id`, 계좌: `linked_bank_accounts.id` 등) |
| `enabled` | boolean | 수집 활성화 여부 |
| `threshold` | number\|null | 끝전 커팅 기준. `ACCOUNT` 타입에만 적용(1000 / 5000 / 10000). 나머지는 null |

---

### PUT `/api/cma/collect/settings`

적립 소스 설정 (카드/계좌별 ON/OFF)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

소스(sourceType)별 적립 ON/OFF 및 끝전 커팅 기준(threshold) 설정. `sourceRefId`는 필수. `threshold`는 `ACCOUNT` 타입에만 적용되며 1000 / 5000 / 10000 중 하나, null이면 기존값 유지(신규 등록 시 기본값 10000).

```json
{
  "settings": [
  {"sourceType": "ACCOUNT", "sourceRefId": 1, "enabled": true, "threshold": 5000},
  {"sourceType": "CARD", "sourceRefId": 1, "enabled": true, "threshold": null},
  {"sourceType": "POINT", "sourceRefId": 1, "enabled": false, "threshold": null}
  ]
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "적립 소스 설정 완료",
  "data": null
 }
```

---

### GET `/api/cma/collect/history`

적립 이력 조회 (txType=COLLECT 거래만 필터링)<br> Query: page (number, 선택, 기본값 0), size (number, 선택, 기본값 20, 최대 100)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "적립 이력 조회 성공",
  "data": [
    {
      "id": 2001,
      "txType": "COLLECT",
      "sourceType": "CARD",
      "currency": "KRW",
      "amount": 450,
      "balanceAfter": 1258430,
      "createdAt": "2025-06-10T09:00:00"
    }
  ]
}
```

---

## CMA

### GET `/api/cma/balance`

CMA 잔액·성과율 (원화RP/외화RP) 조회

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "CMA 잔액 조회 성공",
  "data": {
  "krwBalance": 1250000,
  "krwReturnRate": 3.5,
  "usdBalance": 250.00,
  "usdReturnRate": 4.2,
  "totalKrwEquivalent": 1594500
 }
 }
```

> ℹ️ **`totalKrwEquivalent` 환산 규칙**: `KRW 잔액 + (USD 잔액 × 매매기준율)`, 원화 정수 반올림(HALF_UP). 환율은 환전 도메인 SSOT(`ExchangeRateService.getUsdKrwRate().baseRate`, LS CUR 실시간 매매기준율)를 사용한다. USD 미보유(행 없음/0)면 환율을 조회하지 않아 KRW 전용 계좌는 항상 성공한다. USD>0 인데 환율 캐시가 비어 있으면(콜드스타트) 과소 총액을 노출하지 않도록 환전 API와 동일하게 `502 EXTERNAL_API_ERROR`가 전파된다.

---

### GET `/api/cma/transactions`

CMA 계좌내역 (입금·출금·이자) 조회<br> Query: txType (COLLECT | DEPOSIT | BANK_IN | SAVINGS | DORMANT | SELL_RETURN | INTEREST | FX_IN | FX_OUT | BUY_TRANSFER | REVERT, 선택), from (date, 선택), to (date, 선택), page (number, 선택, 기본값 0), size (number, 선택, 기본값 20, 최대 100)

> **거래종류(`txType`) — 자금 성격.** 입금(+): `DEPOSIT`(사용자 수동 입금·초기 충전), `COLLECT`(잔돈 수집), `BANK_IN`(연동 은행계좌발 자동 입금), `SAVINGS`(적금 수집), `DORMANT`(휴면계좌 수집), `SELL_RETURN`(매도대금 환원), `INTEREST`(이자), `FX_IN`(환전 인입) / 출금(−): `BUY_TRANSFER`(매수 이체), `FX_OUT`(환전 출금) / 정정: `REVERT`
> **출처(`sourceType`) — 거래 출처(모든 행).** 수집: `ACCOUNT`(끝전)·`CARD`(라운드업)·`POINT` / 그 외: `MANUAL`(수동)·`SYSTEM`(이자 등 시스템).
> **참조(`ref_type`/`ref_id`) — 출처 레코드 포인터(내부용).** `LINKED_BANK_ACCOUNT`→`linked_bank_accounts.id`, `LINKED_CARD`→`linked_cards.id`, `LINKED_POINT`→`linked_points.id`, `FX_TX`→`fx_transactions.id`(환전), `REVERT`→`cma_transactions.id`(정정 대상 원거래, 자기참조), 그 외(`DEPOSIT`/`INTEREST` 등)는 `NULL`. 한 행이 같은 타입의 출처 여러 건을 합산하면 `ref_id=NULL`.
> `txType`은 자금 성격, `sourceType`은 출처를 뜻한다. 예) 초기 수동 충전 = `txType=DEPOSIT, sourceType=MANUAL`, 은행 자동 입금 = `txType=BANK_IN, sourceType=ACCOUNT`.

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "계좌내역 조회 성공",
  "data": [
    {
      "id": 1001,
      "txType": "COLLECT",
      "sourceType": "CARD",
      "currency": "KRW",
      "amount": 8430,
      "balanceAfter": 1258430,
      "createdAt": "2025-06-10T09:00:00"
    }
  ]
}
```

---

## 자금이체

### GET `/api/cma/transfers`

자금 이동 이력 조회<br> Query: page (number, 선택), size (number, 선택)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "자금 이동 이력 조회 성공",
  "data": [
    {
      "id": 3001,
      "txType": "BUY_TRANSFER",
      "sourceType": null,
      "currency": "KRW",
      "amount": 100000,
      "balanceAfter": 1158430,
      "createdAt": "2025-06-12T14:00:00"
    }
  ]
}
```

---

## 충전

### POST `/api/cma/deposit`

연동 은행계좌에서 CMA 원화풀로 **부족분만** 충전한다. 매수 화면에서 사려는 금액(`targetAmount`)을 보내면 서버가 현재 CMA 원화풀 잔액을 빼 **부족분(`targetAmount − CMA 원화잔액`)만** 은행계좌에서 끌어와 입금한다(차액은 서버가 계산 — 클라가 들고 있던 잔액이 낡아도 안전). CMA 잔액이 이미 목표 이상이면 이체하지 않는다(`sufficient=true`). **KRW 전용** — 해외(USD) 충전은 매수 시점 자동환전이 담당한다.

- **거래 인증 필수(txn-auth)**: 사전 거래 세션이 없으면 `401`(거래 인증 필요). 본문에 비밀번호를 담지 않는다.
- **멱등**: `idempotencyKey`(필수)로 따닥 탭·재전송 시 중복 충전이 방지된다. 단 `sufficient=true`(이체 없음)는 원장에 남지 않으므로 재요청 시 그때의 잔액으로 다시 판단한다.
- 출처 은행계좌가 본인 소유·미해지여야 하며(아니면 `400`), 부족분보다 잔액이 작으면 `400`(잔액 부족)으로 거부한다.
- `409 Conflict`: 거의 동시에 같은 `idempotencyKey`로 두 건이 들어온 경합, 또는 그 키가 이미 다른 사용자/다른 용도 거래에 사용된 경우. 같은 요청을 잠시 후 재시도하거나 새 멱등키로 보낸다.

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized / 404 Not Found / 409 Conflict

**Request Body**

```json
{
  "targetAmount": 50000,
  "sourceAccountId": 7,
  "idempotencyKey": "c0ffee-1234"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `targetAmount` | number | 사려는 금액(KRW). 충전 목표. 양수 필수 |
| `sourceAccountId` | number | 충전 재원이 될 연동 은행계좌 ID |
| `idempotencyKey` | string | 클라 발급 멱등키(필수) |

**Response Body** (부족분 충전)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "CMA 충전 성공",
  "data": {
    "currency": "KRW",
    "targetAmount": 50000,
    "depositAmount": 20000,
    "cmaBalanceAfter": 50000,
    "sufficient": false
  }
}
```

**Response Body** (이미 충분 — 이체 없음)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "CMA 충전 성공",
  "data": {
    "currency": "KRW",
    "targetAmount": 50000,
    "depositAmount": 0,
    "cmaBalanceAfter": 80000,
    "sufficient": true
  }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `currency` | string | `KRW` 고정 |
| `targetAmount` | number | 사려는 금액(요청 echo) |
| `depositAmount` | number | 실제 충전된 부족분(이미 충분하면 0) |
| `cmaBalanceAfter` | number | 충전 후 CMA 원화풀 잔액 |
| `sufficient` | boolean | `true`=이미 충분(이체 없음) / `false`=부족분 충전함 |

원장에는 `txType=DEPOSIT`, `sourceType=MANUAL`, `currency=KRW`, `refType=LINKED_BANK_ACCOUNT`, `refId=sourceAccountId`로 입금 1줄이 남는다.

---

## 자동충전

### GET `/api/cma/auto-charge-settings`

부족금액 자동충전 설정 조회 (SETTLE-006)

설정 행이 없는 신규 사용자는 OFF 기본값 `{ "enabled": false, "sourceAccountId": null, "maxChargePerTx": null }`을 200으로 반환한다(404 아님).

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 401 Unauthorized

**Response Body** — 설정 보유 사용자(ON 예시)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "자동충전 설정 조회 성공",
  "data": {
  "enabled": true,
  "sourceAccountId": 12,
  "maxChargePerTx": 100000
 }
 }
```

**Response Body** — 설정 행이 없는 신규 사용자(OFF 기본값)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "자동충전 설정 조회 성공",
  "data": {
  "enabled": false,
  "sourceAccountId": null,
  "maxChargePerTx": null
 }
 }
```

---

### PUT `/api/cma/auto-charge-settings`

부족금액 자동충전 설정 (ON/OFF·1회 한도·충전 재원 계좌). 매수·정기적립 시 CMA 원화풀 부족분만 연동 은행계좌에서 자동 이체(on-demand). '매일 자동충전' 정기 선충전은 미지원.

- `enabled=true`: `sourceAccountId` 필수 + `maxChargePerTx > 0`. `sourceAccountId`는 본인 명의 연동 은행계좌여야 한다(아니면 400).
- `enabled=false`: `sourceAccountId`·`maxChargePerTx`는 null 허용(끄기). 검증·소유확인 생략.
- 이번 범위는 **설정 저장만**이며, 실행(부족분 감지 → 자동 이체)은 후속 작업이다.

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "enabled": true,
  "sourceAccountId": 12,
  "maxChargePerTx": 100000
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "자동충전 설정 완료",
  "data": null
 }
```
