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
    "collectedToday": 5830.0,
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

### POST `/api/cma/collect`

잔돈 모으기 실행 (통합)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{}
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "잔돈 모으기 성공",
  "data": {
  "collectedAmount": 8430,
  "newBalance": 1258430
 }
 }
```

---

### POST `/api/cma/collect/account`

계좌 끝전 적립

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "accountId": 101
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "계좌 끝전 적립 성공",
  "data": {
  "amount": 3450,
  "newBalance": 1253450
 }
 }
```

---

### POST `/api/cma/collect/card`

카드 라운드업 적립

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{}
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "카드 라운드업 적립 성공",
  "data": {
  "amount": 2980,
  "newBalance": 1252980
 }
 }
```

---

### POST `/api/cma/collect/point`

포인트 전환 적립

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "provider": "OK캐쉬백",
  "points": 5000
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "포인트 전환 적립 성공",
  "data": {
  "convertedAmount": 2000,
  "newBalance": 1252000
 }
 }
```

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

> ⚠️ **TODO (환전 도메인 의존)**: `totalKrwEquivalent`는 위 예시처럼 USD 잔액을 환율로 환산해 KRW와 합산한 값이어야 하지만, 환율 조회/환전 API(`/api/exchange/*`, `docs/API-exchange.md` 참고)가 아직 구현되지 않아 **현재 구현(`CmaQueryService.getBalance()`)은 KRW 잔액만 반환하고 USD는 합산하지 않는다.** Exchange 도메인 구현 후 환율을 곱해 USD 잔액을 KRW로 환산·합산하는 로직을 추가해야 함.

---

### GET `/api/cma/transactions`

CMA 계좌내역 (입금·출금·이자) 조회<br> Query: txType (COLLECT | DEPOSIT | BANK_IN | SAVINGS | DORMANT | SELL_RETURN | INTEREST | FX_IN | FX_OUT | BUY_TRANSFER | REVERT, 선택), from (date, 선택), to (date, 선택), page (number, 선택, 기본값 0), size (number, 선택, 기본값 20, 최대 100)

> **거래종류(`txType`) — 자금 성격.** 입금(+): `DEPOSIT`(사용자 수동 입금·초기 충전), `COLLECT`(잔돈 수집), `BANK_IN`(연동 은행계좌발 자동 입금), `SAVINGS`(적금 수집), `DORMANT`(휴면계좌 수집), `SELL_RETURN`(매도대금 환원), `INTEREST`(이자), `FX_IN`(환전 인입) / 출금(−): `BUY_TRANSFER`(매수 이체), `FX_OUT`(환전 출금) / 정정: `REVERT`
> **출처(`sourceType`) — 거래 출처(모든 행).** 수집: `ACCOUNT`(끝전)·`CARD`(라운드업)·`POINT` / 그 외: `MANUAL`(수동)·`SYSTEM`(이자 등 시스템).
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

## 자동충전

### GET `/api/cma/auto-charge-settings`

부족금액 자동충전 설정 조회 (SETTLE-006)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 401 Unauthorized

**Response Body**

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

---

### PUT `/api/cma/auto-charge-settings`

부족금액 자동충전 설정 (ON/OFF·1회 한도·충전 재원 계좌). 매수·정기적립 시 CMA 원화풀 부족분만 연동 은행계좌에서 자동 이체(on-demand). '매일 자동충전' 정기 선충전은 미지원.

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
