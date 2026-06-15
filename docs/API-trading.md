# PocketStock API 명세 — Trading

> 공통 헤더: `Authorization: Bearer {accessToken}` | `Content-Type: application/json`

## 증권계좌

### POST `/api/trading/accounts`

증권계좌 개설 (CMA + 국내·해외 위탁)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "accountTypes": ["DOMESTIC", "OVERSEAS"]
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "증권계좌 개설 성공",
  "data": {
  "accountNo": "98765-01",
  "accountTypes": ["DOMESTIC", "OVERSEAS"]
 }
 }
```

---

### GET `/api/trading/accounts`

계좌 상태 조회

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "계좌 상태 조회 성공",
  "data": [
  {"type": "DOMESTIC", "accountNo": "98765-01", "status": "ACTIVE"},
  {"type": "OVERSEAS", "accountNo": "98765-02", "status": "ACTIVE"}
 ]
 }
```

---

### GET `/api/trading/deposit`

예수금/출금가능금액 조회

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "예수금 조회 성공",
  "data": {
  "deposit": 500000,
  "withdrawable": 480000,
  "orderable": 500000
 }
 }
```

---

## 시세

### GET `/api/trading/stocks/categories`

종목 카테고리 탐색<br> Query: category (string, 선택) - 예: 40대여성선호, 급등주, 배당주

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "종목 카테고리 탐색 성공",
  "data": {
  "category": "40대여성선호",
  "stocks": [
  {
  "stockCode": "005930",
  "stockName": "삼성전자",
  "currentPrice": 73500,
  "changeRate": 1.66
  }
  ]
 }
 }
```

---

### GET `/api/trading/stocks/search`

종목 검색 (자체 종목마스터)<br> Query: keyword (string, 필수), page (number, 선택), size (number, 선택)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "종목 검색 성공",
  "data": {
  "stocks": [
  {
  "stockCode": "005930",
  "stockName": "삼성전자",
  "market": "KOSPI",
  "currentPrice": 73500
  }
  ],
  "totalElements": 3
 }
 }
```

---

### GET `/api/trading/stocks/{stockCode}`

종목 상세 (마스터 + 현재가 합성)<br> Path: {stockCode} - 종목코드 (예: 005930)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "종목 상세 조회 성공",
  "data": {
  "stockCode": "005930",
  "stockName": "삼성전자",
  "market": "KOSPI",
  "sector": "전기전자",
  "currentPrice": 73500,
  "changePrice": 1200,
  "changeRate": 1.66,
  "volume": 15234567,
  "marketCap": 4389000000000
 }
 }
```

---

### GET `/api/trading/stocks/{stockCode}/price?market=domestic`

[국내] 현재가 조회<br> Path: {stockCode} - 종목코드 (예: 005930) | LS TR: t1102

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "[국내] 현재가 조회 성공",
  "data": {
  "stockCode": "005930",
  "currentPrice": 73500,
  "changePrice": 1200,
  "changeRate": 1.66,
  "highPrice": 74200,
  "lowPrice": 72800,
  "openPrice": 73000,
  "volume": 15234567
 }
 }
```

---

### GET `/api/trading/stocks/{stockCode}/price?market=overseas`

[해외] 현재가 조회<br> Path: {stockCode} - 종목코드 (예: AAPL) | LS TR: g3101

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "[해외] 현재가 조회 성공",
  "data": {
  "stockCode": "AAPL",
  "currentPrice": 213.55,
  "changePrice": 2.30,
  "changeRate": 1.09,
  "currency": "USD",
  "volume": 55234567
 }
 }
```

---

### GET `/api/trading/stocks/{stockCode}/orderbook?market=domestic`

[국내] 호가 조회 (온주 전용)<br> Path: {stockCode} - 종목코드 (예: 005930) | LS TR: t8450

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "[국내] 호가 조회 성공",
  "data": {
  "stockCode": "005930",
  "asks": [
  {"price": 73600, "quantity": 12000},
  {"price": 73700, "quantity": 8500}
  ],
  "bids": [
  {"price": 73400, "quantity": 15000},
  {"price": 73300, "quantity": 20000}
  ]
 }
 }
```

---

### GET `/api/trading/stocks/{stockCode}/orderbook?market=overseas`

[해외] 현재가·호가 조회 (온주 전용)<br> Path: {stockCode} - 종목코드 (예: AAPL) | LS TR: g3106

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "[해외] 호가 조회 성공",
  "data": {
  "stockCode": "AAPL",
  "asks": [{"price": 213.60, "quantity": 500}],
  "bids": [{"price": 213.50, "quantity": 700}],
  "currency": "USD"
 }
 }
```

---

### GET `/api/trading/stocks/{stockCode}/info?market=domestic`

[국내] 종목 기업정보<br> Path: {stockCode} - 종목코드 (예: 005930) | LS TR: t3320

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "[국내] 기업정보 조회 성공",
  "data": {
  "stockCode": "005930",
  "stockName": "삼성전자",
  "ceo": "경계현",
  "founded": "1969-01-13",
  "employees": 270000,
  "per": 14.2,
  "pbr": 1.3,
  "eps": 5174,
  "dividendYield": 2.4
 }
 }
```

---

### GET `/api/trading/stocks/{stockCode}/info?market=overseas`

[해외] 종목 기업정보<br> Path: {stockCode} - 종목코드 (예: AAPL) | LS TR: g3104

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "[해외] 기업정보 조회 성공",
  "data": {
  "stockCode": "AAPL",
  "stockName": "Apple Inc.",
  "exchange": "NASDAQ",
  "per": 32.5,
  "pbr": 48.2,
  "eps": 6.57,
  "dividendYield": 0.5,
  "marketCap": 3280000000000
 }
 }
```

---

## 실시간시세

### WS `/topic/stock/trade/{stockCode}`

[국내] 실시간 체결가 (소수점·온주)<br> Path: {stockCode} - 종목코드 (예: 005930) | LS TR: US3

- **HTTP Status Code**: 101 Switching Protocols

**Response Body**

```json
{
  "stockCode": "005930",
  "tradePrice": 73500,
  "tradeVolume": 100,
  "changeRate": 1.66,
  "tradedAt": "2025-06-15T10:30:00.123"
 }
```

---

### WS `/topic/asking/{stockCode}`

[국내] 실시간 호가 (온주)<br> Path: {stockCode} - 종목코드 (예: 005930) | LS TR: UH1

- **HTTP Status Code**: 101 Switching Protocols

**Response Body**

```json
{
  "stockCode": "005930",
  "asks": [{"price": 73600, "quantity": 12000}],
  "bids": [{"price": 73400, "quantity": 15000}],
  "updatedAt": "2025-06-15T10:30:00.123"
 }
```

---

### WS `/topic/foreign/transaction/{symbol}`

[해외] 실시간 체결가<br> Path: {symbol} - 해외 종목코드 (예: AAPL) | LS TR: GSC

- **HTTP Status Code**: 101 Switching Protocols

**Response Body**

```json
{
  "symbol": "AAPL",
  "tradePrice": 213.55,
  "tradeVolume": 500,
  "changeRate": 1.09,
  "currency": "USD",
  "tradedAt": "2025-06-15T10:30:00.123"
 }
```

---

### WS `/topic/foreign/quote/{symbol}`

[해외] 실시간 호가 (온주)<br> Path: {symbol} - 해외 종목코드 (예: AAPL) | LS TR: GSH

- **HTTP Status Code**: 101 Switching Protocols

**Response Body**

```json
{
  "symbol": "AAPL",
  "asks": [{"price": 213.60, "quantity": 500}],
  "bids": [{"price": 213.50, "quantity": 700}],
  "updatedAt": "2025-06-15T10:30:00.123"
 }
```

---

### WS `/topic/order-notification`

실시간 체결통보 (주문 결과)<br> LS TR: SC1 (국내) · AS1 (해외)

- **HTTP Status Code**: 101 Switching Protocols

**Response Body**

```json
{
  "orderId": "ORD-20250615-001",
  "stockCode": "005930",
  "orderType": "BUY",
  "status": "FILLED",
  "filledQuantity": 0.5,
  "filledPrice": 73500,
  "filledAt": "2025-06-15T10:30:05"
 }
```

---

## 소수점투자

### POST `/api/trading/orders/buy`

소수점 매수 (금액/수량)<br> LS TR: CSPAT00601 (국내) · COSAT00301 (해외)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "stockCode": "005930",
  "market": "DOMESTIC",
  "orderType": "AMOUNT",
  "amount": 10000
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "소수점 매수 주문 성공",
  "data": {
  "orderId": "ORD-20250615-001",
  "stockCode": "005930",
  "orderType": "BUY",
  "requestedAmount": 10000,
  "status": "RECEIVED"
 }
 }
```

---

### POST `/api/trading/orders/sell`

소수점 매도 (금액/전량)<br> LS TR: CSPAT00601 (국내) · COSMT00300 (해외)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "stockCode": "005930",
  "market": "DOMESTIC",
  "orderType": "ALL"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "소수점 매도 주문 성공",
  "data": {
  "orderId": "ORD-20250615-002",
  "stockCode": "005930",
  "orderType": "SELL",
  "quantity": 0.5,
  "status": "RECEIVED"
 }
 }
```

---

### POST `/api/trading/orders/whole`

온주 매수/매도 (호가 기반)<br> LS TR: CSPAT00601 (국내) · COSAT00301 (해외)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "stockCode": "005930",
  "market": "DOMESTIC",
  "orderSide": "BUY",
  "orderType": "LIMIT",
  "price": 73500,
  "quantity": 1
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "온주 주문 성공",
  "data": {
  "orderId": "ORD-20250615-003",
  "stockCode": "005930",
  "orderSide": "BUY",
  "price": 73500,
  "quantity": 1,
  "status": "RECEIVED"
 }
 }
```

---

### DELETE `/api/trading/orders/{orderId}`

주문 취소 (배치 전송 전)<br> Path: {orderId} - 취소할 주문 ID | LS TR: CSPAT00801 (국내) · COSAT00311 (해외)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "주문 취소 성공",
  "data": {
  "orderId": "ORD-20250615-001",
  "status": "CANCELLED"
 }
 }
```

---

### GET `/api/trading/orders`

거래내역 조회 (매수·매도·달성)<br> Query: type (BUY | SELL | ALL, 선택), page (number, 선택), size (number, 선택)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "거래내역 조회 성공",
  "data": {
  "orders": [
  {
  "orderId": "ORD-20250615-001",
  "stockCode": "005930",
  "stockName": "삼성전자",
  "orderType": "BUY",
  "amount": 10000,
  "filledQuantity": 0.136,
  "status": "FILLED",
  "filledAt": "2025-06-15T10:30:05"
  }
  ],
  "page": 0,
  "totalElements": 25
 }
 }
```

---

### GET `/api/trading/orders/pending`

미체결 주문 조회

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "미체결 주문 조회 성공",
  "data": [
  {
  "orderId": "ORD-20250615-003",
  "stockCode": "005930",
  "stockName": "삼성전자",
  "orderSide": "BUY",
  "price": 73500,
  "quantity": 1,
  "createdAt": "2025-06-15T10:28:00"
  }
 ]
 }
```

---

### GET `/api/trading/holdings`

보유종목·잔고 (평가·수익률) 조회

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "보유종목 조회 성공",
  "data": {
  "totalEvaluated": 3250000,
  "totalReturn": 250000,
  "returnRate": 8.33,
  "holdings": [
  {
  "stockCode": "005930",
  "stockName": "삼성전자",
  "quantity": 0.5,
  "purchasePrice": 70000,
  "currentPrice": 73500,
  "evaluated": 36750,
  "returnRate": 5.0
  }
  ]
 }
 }
```

---

### GET `/api/trading/whole-shares`

온주 전환내역 조회

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "온주 전환내역 조회 성공",
  "data": [
  {
  "stockCode": "005930",
  "stockName": "삼성전자",
  "fractionalQuantity": 1.0,
  "convertedAt": "2025-06-10T00:00:00"
  }
 ]
 }
```

---

## 정기적립식

### POST `/api/trading/auto-invest`

자동모으기 설정 등록 (주기/조건)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "trigger_type": "PERIODIC",
  "cycle": "WEEKLY",
  "dayOfWeek": "MON",
  "amount": 10000,
  "stocks": [
  {"stockCode": "005930", "ratio": 60},
  {"stockCode": "000660", "ratio": 40}
  ]
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "자동모으기 설정 등록 성공",
  "data": {
  "autoInvestId": 1,
  "trigger_type": "PERIODIC",
  "cycle": "WEEKLY",
  "nextExecuteDate": "2025-06-16"
 }
 }
```

---

### PUT `/api/trading/auto-invest/{id}`

자동모으기 설정 수정<br> Path: {id} - 자동모으기 ID

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "cycle": "MONTHLY",
  "dayOfMonth": 1,
  "amount": 20000
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "자동모으기 설정 수정 성공",
  "data": {
  "autoInvestId": 1,
  "cycle": "MONTHLY",
  "nextExecuteDate": "2025-07-01"
 }
 }
```

---

### PATCH `/api/trading/auto-invest/{id}/status`

자동모으기 일시중지/재개/해제<br> Path: {id} - 자동모으기 ID

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "status": "PAUSED"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "자동모으기 상태 변경 성공",
  "data": {
  "autoInvestId": 1,
  "status": "PAUSED"
 }
 }
```

---

### POST `/api/trading/auto-invest/stocks`

자동모으기 종목 추가

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "autoInvestId": 1,
  "stockCode": "035720",
  "ratio": 20
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "자동모으기 종목 추가 성공",
  "data": {
  "autoInvestId": 1,
  "stockCount": 3
 }
 }
```

---

### GET `/api/trading/auto-invest`

자동모으기 종합 설정 조회

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "자동모으기 설정 조회 성공",
  "data": {
  "autoInvestId": 1,
  "trigger_type": "PERIODIC",
  "cycle": "WEEKLY",
  "dayOfWeek": "MON",
  "amount": 10000,
  "status": "ACTIVE",
  "stocks": [
  {"stockCode": "005930", "stockName": "삼성전자", "ratio": 60},
  {"stockCode": "000660", "stockName": "SK하이닉스", "ratio": 40}
  ],
  "nextExecuteDate": "2025-06-16"
 }
 }
```

---

## 퍼즐

### GET `/api/trading/puzzle/{stockCode}`

퍼즐 진행률 조회 (조각/완성)<br> Path: {stockCode} - 종목코드 (예: 005930)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "퍼즐 진행률 조회 성공",
  "data": {
  "stockCode": "005930",
  "stockName": "삼성전자",
  "totalPieces": 10,
  "collectedPieces": 7,
  "progressRate": 70.0,
  "isCompleted": false
 }
 }
```

---

## 보상

### POST `/api/trading/rewards/signup`

가입보상 종목 선택·지급

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "stockCode": "005930"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "가입보상 지급 성공",
  "data": {
  "stockCode": "005930",
  "stockName": "삼성전자",
  "rewardQuantity": 0.001,
  "rewardValue": 73
 }
 }
```

---

### GET `/api/trading/rewards`

보상 지급 내역 조회

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "보상 지급 내역 조회 성공",
  "data": [
  {
  "type": "SIGNUP",
  "stockCode": "005930",
  "stockName": "삼성전자",
  "quantity": 0.001,
  "grantedAt": "2025-06-01T09:00:00"
  }
 ]
 }
```
