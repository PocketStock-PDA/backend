# PocketStock API 명세 — Exchange

> 공통 헤더: `Authorization: Bearer {accessToken}` | `Content-Type: application/json`

## 환전

### GET `/api/exchange/rate`

환율 조회 (USD/KRW, 예상 환전금액)<br> Query: amount (number, 선택) - 환전 예상 금액 (KRW) | LS TR: CUR

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "환율 조회 성공",
  "data": {
  "baseCurrency": "USD",
  "targetCurrency": "KRW",
  "exchangeRate": 1382.50,
  "estimatedKrw": 1382500,
  "updatedAt": "2025-06-15T10:00:00"
 }
 }
```

---

### WS `/topic/currency/usd-krw`

실시간 환율 (USD/KRW)<br> LS TR: CUR (현물USD 실시간)

- **HTTP Status Code**: 101 Switching Protocols

**Response Body**

```json
{
  "baseCurrency": "USD",
  "targetCurrency": "KRW",
  "exchangeRate": 1382.50,
  "change": -2.30,
  "updatedAt": "2025-06-15T10:30:00.123"
 }
```

---

### GET `/api/exchange/validate`

환전 가능여부·가능금액 검증<br> Query: amount (number, 필수) - 환전할 KRW 금액

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "환전 가능여부 검증 성공",
  "data": {
  "available": true,
  "requestedAmount": 100000,
  "maxAmount": 5000000,
  "estimatedUsd": 72.33
 }
 }
```

---

### POST `/api/exchange/krw-to-usd`

원화 → 달러 환전

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "krwAmount": 100000,
  "accountPassword": "1234"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "원화 → 달러 환전 성공",
  "data": {
  "exchangedUsd": 72.33,
  "appliedRate": 1382.50,
  "fee": 0,
  "trigger_type": "MANUAL",
  "remainKrw": 4900000
 }
 }
```

---

### POST `/api/exchange/usd-to-krw`

달러 → 원화 환전

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "usdAmount": 50.00,
  "accountPassword": "1234"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "달러 → 원화 환전 성공",
  "data": {
  "exchangedKrw": 69125,
  "appliedRate": 1382.50,
  "fee": 0,
  "trigger_type": "MANUAL",
  "remainUsd": 200.00
 }
 }
```

---

### PUT `/api/exchange/auto-settings`

자동환전 설정 (달러우선·한도·잔돈)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "enabled": true,
  "priority": "USD_FIRST",
  "dailyLimit": 500000,
  "useRoundUp": true
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "자동환전 설정 완료",
  "data": {
  "enabled": true,
  "priority": "USD_FIRST",
  "dailyLimit": 500000
 }
 }
```

---

### GET `/api/exchange/history`

환전 이력 조회<br> Query: page (number, 선택), size (number, 선택)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "환전 이력 조회 성공",
  "data": {
  "history": [
  {
  "type": "KRW_TO_USD",
  "krwAmount": 100000,
  "usdAmount": 72.33,
  "trigger_type": "MANUAL",
    "rate": 1382.50,
  "exchangedAt": "2025-06-15T10:30:00"
  }
  ],
  "page": 0,
  "totalElements": 15
 }
 }
```
