# PocketStock API 명세 — CMA

> 공통 헤더: `Authorization: Bearer {accessToken}` | `Content-Type: application/json`

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

```json
{
  "settings": [
  {"type": "CARD_ROUNDUP", "enabled": true},
  {"type": "ACCOUNT_ENDPENNY", "enabled": false}
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

적립 이력 조회<br> Query: page (number, 선택), size (number, 선택)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "적립 이력 조회 성공",
  "data": {
  "history": [
  {
  "type": "CARD_ROUNDUP",
  "amount": 450,
  "collectedAt": "2025-06-10T09:00:00"
  }
  ],
  "page": 0,
  "totalElements": 42
 }
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

---

### GET `/api/cma/transactions`

CMA 계좌내역 (입금·출금·이자) 조회<br> Query: txType (COLLECT | BANK_IN | SAVINGS | DORMANT | SELL_RETURN | INTEREST | FX_IN | FX_OUT | BUY_TRANSFER | REVERT, 선택), from (date, 선택), to (date, 선택), page (number, 선택, 기본값 0), size (number, 선택, 기본값 20, 최대 100)

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
      "sourceType": "CARD_ROUNDUP",
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
  "data": {
  "transfers": [
  {
  "direction": "CMA_TO_INVEST",
  "amount": 100000,
  "transferredAt": "2025-06-12T14:00:00"
  }
  ],
  "page": 0,
  "totalElements": 10
 }
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
