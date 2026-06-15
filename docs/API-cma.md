# PocketStock API 명세 — CMA

> 공통 헤더: `Authorization: Bearer {accessToken}` | `Content-Type: application/json`

## 홈

### GET `/api/cma/home`

홈 대시보드 (CMA잔액 + 수집가능 잔돈)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "CMA 홈 대시보드 조회 성공",
  "data": {
  "cmaBalance": 1250000,
  "cmaReturnRate": 3.5,
  "collectableAmount": 8430,
  "lastCollectedAt": "2025-06-10T09:00:00"
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

CMA 계좌내역 (입금·출금·이자) 조회<br> Query: type (COLLECT | BANK_IN | SAVINGS | DORMANT | SELL_RETURN | INTEREST | FX_IN | FX_OUT | BUY_TRANSFER | REVERT, 선택), page (number, 선택), size (number, 선택)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "CMA 계좌내역 조회 성공",
  "data": {
  "transactions": [
  {
  "type": "COLLECT",
  "amount": 8430,
  "balance": 1258430,
  "description": "잔돈 모으기",
  "createdAt": "2025-06-10T09:00:00"
  }
  ],
  "page": 0,
  "totalElements": 120
 }
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
