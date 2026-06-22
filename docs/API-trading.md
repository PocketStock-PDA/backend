# PocketStock API 명세 — Trading

> 공통 헤더: `Authorization: Bearer {accessToken}` | `Content-Type: application/json`

## 증권계좌

### POST `/api/trading/accounts` ✅ 구현완료

증권계좌 개설 (국내·해외 위탁)

> **구현 메모**: `accountTypes ⊆ {DOMESTIC, OVERSEAS}`. 이미 개설된 시장은 건너뛰는 **멱등** 동작(기존 베이스 계좌번호 재사용). 계좌번호 형식 `{베이스5자리}-{01 국내 | 02 해외}`, DB엔 AES-256-GCM 암호문(`account_no_enc`) 저장. CMA 계좌 동시 생성은 추후(이벤트 연계). 미인증 401 / 잘못된 유형 400.

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

### GET `/api/trading/accounts` ✅ 구현완료

계좌 상태 조회

> **구현 메모**: 개설된 위탁계좌를 시장별로 반환(`type` = DOMESTIC | OVERSEAS). 계좌번호는 복호화해 응답. 미인증 401.

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

### GET `/api/trading/deposit` ✅ 구현완료

예수금/출금가능금액 조회 — **국내(KRW)+해외(USD) 시장별(#137)**.

> **구현 메모**: 최상위 3필드(`deposit`/`withdrawable`/`orderable`)는 **국내 KRW**(기존 단일 화면 하위호환). `balances`는 위탁계좌 시장별 분해 — 계좌가 있는 시장만 포함(국내 KRW·해외 USD). 출금가능·주문가능 = `balance − held`(미체결 매수 hold 제외, M2). 미결제 출금보류는 후속이라 현재 `withdrawable`=`orderable`. 거래 없으면 0. 미인증 401.

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
    "orderable": 500000,
    "balances": [
      { "market": "DOMESTIC", "currency": "KRW", "deposit": 500000, "withdrawable": 480000, "orderable": 480000 },
      { "market": "OVERSEAS", "currency": "USD", "deposit": 120.50, "withdrawable": 120.50, "orderable": 120.50 }
    ]
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

### GET `/api/trading/stocks/{stockCode}/price?market=domestic` ✅ 구현완료

[국내] 현재가 조회<br> Path: {stockCode} - 종목코드 (예: 005930) | LS TR: t1102

> **구현 메모**: LS `t1102`(`/stock/market-data`, KRX) 실연동. `sign`(4하한·5하락)으로 `changePrice`·`changeRate` 부호 적용. 금액 필드는 BigDecimal(정수는 그대로 직렬화). LS 토큰은 `LsTokenProvider`(Redis 캐싱) 공유, 401 시 1회 재발급 재시도.
> **에러**: 무효 종목코드 → **404**(LS가 빈 블록 반환 → `hname` 없으면 미존재 처리) / LS 호출 장애(타임아웃·5xx) → **502**(`EXTERNAL_API_ERROR`) / `market=overseas` → 현재 **400**(g3101 추후) / 미인증 401.

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

[해외] 종목 기업정보<br> Path: {stockCode} - 종목코드 (예: AAPL) | KIS TR: HHDFS76200200

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

> **구현 메모**: LS US3(통합 체결, KRX+NXT) 실시간을 `TradeRealtimeListener`가 매핑해 push. 현재가/등락/시고저/거래량 필드명을 REST 현재가(t1102 `StockPriceResponse`)와 맞춰 진입 시 REST 스냅샷 → WS 틱 갱신으로 재사용. `changePrice`·`changeRate`는 sign(4하한·5하락) 적용 부호 포함. 구독 시에만 LS에 종목 등록(온디맨드), tr_key = `U`+단축코드 7자리+공백 3자리(10자리).

- **HTTP Status Code**: 101 Switching Protocols

**Response Body**

```json
{
  "stockCode": "005930",
  "tradeTime": "162202",
  "currentPrice": 56100,
  "changePrice": 900,
  "changeRate": 1.63,
  "openPrice": 55500,
  "highPrice": 56700,
  "lowPrice": 55500,
  "volume": 16495969,
  "lastTradeVolume": 40,
  "tradeType": "-",
  "tradeStrength": 107.51
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

[해외] 실시간 체결가<br> Path: {symbol} - 해외 종목코드 (예: AAPL) | KIS TR: HDFSCNT0

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

[해외] 실시간 호가 (온주)<br> Path: {symbol} - 해외 종목코드 (예: AAPL) | KIS TR: HDFSASP0

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

### POST `/api/trading/orders/fractional/buy`

소수점 매수 — **접수 즉시 1분 차수에 `QUEUED` 편입(비동기)**. 체결은 차수 집행기가 수행(상계·회사 선부담 ceil·시뮬·비례배분). 응답의 `estQuantity`는 접수 시점 예상수량(참고치) — 확정 수량·체결가는 거래내역/배분에서 확인.

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized
- **D4(국내 먼저)**: 현재 국내(KRW)만 접수. 해외는 후속(#155 해외 확장).
- **자금 hold(D1)**: `AMOUNT`=주문금액 그대로(버퍼X) / `QUANTITY`=예상금액×(1+버퍼 1%) 잠금(체결 후 미사용분 환원). 실제 잠근 금액은 `heldAmount`.
- **market은 받지 않음** — `stockCode`→exchange에서 파생(온주와 동일). `clientOrderId`(멱등키) 필수.

**Request Body** (`orderType`: AMOUNT 금액 / QUANTITY 수량)

```json
{
  "clientOrderId": "frac-buy-20260623-001",
  "stockCode": "005930",
  "orderType": "AMOUNT",
  "amount": 10000
 }
```

```json
{
  "clientOrderId": "frac-buy-20260623-002",
  "stockCode": "005930",
  "orderType": "QUANTITY",
  "quantity": 0.5
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "소수점 매수 접수 성공",
  "data": {
    "orderId": 1024,
    "roundId": 88,
    "stockCode": "005930",
    "side": "BUY",
    "orderType": "AMOUNT",
    "estQuantity": 0.140845,
    "heldAmount": 10000,
    "status": "QUEUED",
    "orderable": 90000
  }
 }
```

---

### POST `/api/trading/orders/fractional/sell`

소수점 매도 — **접수 즉시 `QUEUED` 편입(비동기)**. 접수 시 매도가능 보유수량을 hold(`holdings.held_quantity`). `ALL`=매도가능 전량, `AMOUNT`=예상가로 환산한 주수(상한=매도가능 전량).

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized
- **market은 받지 않음**(stockCode→exchange 파생). `clientOrderId`(멱등키) 필수.

**Request Body** (`orderType`: AMOUNT 금액 / ALL 전량)

```json
{
  "clientOrderId": "frac-sell-20260623-001",
  "stockCode": "005930",
  "orderType": "ALL"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "소수점 매도 접수 성공",
  "data": {
    "orderId": 1025,
    "roundId": 88,
    "stockCode": "005930",
    "side": "SELL",
    "orderType": "ALL",
    "estQuantity": 0.5,
    "heldAmount": null,
    "status": "QUEUED",
    "orderable": null
  }
 }
```

---

### POST `/api/trading/orders/whole`

온주 매수/매도 (호가 기반)<br> LS TR: CSPAT00601 (국내) · COSAT00301 (해외)

- **응답 status**: 즉시체결 `FILLED` / 지정가 미체결 `PENDING`(H4 매칭 대기) / 검증·체결 실패 `REJECTED`.
  - **REJECTED는 주문으로 기록**(감사 추적) — 본 트랜잭션 롤백과 별개로 별도 트랜잭션(`REQUIRES_NEW`)에 `REJECTED` 행을 남기고 차감분이 있으면 환원.
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

주문 취소 — **소수점·온주 공용**.<br> Path: {orderId} - 취소할 주문 ID | LS TR: CSPAT00801 (국내) · COSAT00311 (해외)

- **취소 가능 상태**: 소수점 `QUEUED`(배치 전송 전) / 온주 `PENDING`(지정가 미체결) → `CANCELLED`로 전이.
- **취소 불가(종결) 상태**: `SENT`·`FILLED`·`CANCELLED`·`REJECTED` → `409 Conflict`.
- **전이 가드**: 상태 전이는 DB 조건부 UPDATE(`SET status='CANCELLED' WHERE id=? AND user_id=? AND status IN ('QUEUED','PENDING')`)로 원자 처리 — 동시 체결/이중취소 경합 차단(0행이면 실패 응답).
- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 401 Unauthorized / 404 Not Found(없는 주문·타인 주문) / 409 Conflict(종결 상태라 취소 불가)

**Response Body (성공)**

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

**Response Body (취소 불가 — 이미 체결/종결, 409)**

```json
{
  "success": false,
  "code": "ORDER_NOT_CANCELLABLE",
  "message": "이미 체결·종결된 주문은 취소할 수 없습니다.",
  "data": null
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

### POST `/api/trading/whole-shares`

온주 전환 실행(소수→온주) — **사용자가 전환 버튼을 누르면** 그 종목의 소수점(신탁) 보유 **정수부를 온주(직접소유)로 굳힌다**(FRAC-010 #157). 전환분은 이후 **정수 매도만**, 남은 소수만 소수 매도 가능(온주→소수 역전환 불가). `holdings.quantity`는 불변(온주=quantity−fractional_qty가 +전환수량). **소수 보유가 1주 미만이면 거부.** 미체결 매도분(held)은 전환 대상에서 제외.

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{ "stockCode": "005930" }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "온주 전환 성공",
  "data": {
    "stockCode": "005930",
    "convertedWholeQty": 1,
    "remainingFractional": 0.2,
    "wholeQty": 1.0,
    "totalQuantity": 1.2
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
      "wholeQty": 1,
      "convertedAt": "2026-06-23T10:00:00"
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

---

## 증권 캘린더

> 사용자가 **보유한 종목** 기준으로 필터된 이벤트. 전체 시장 캘린더가 아님.  
> `eventType`: `DIVIDEND`(배당락일) · `EARNINGS`(실적발표) · `RECOMMEND`(추천)  
> **데이터 흐름**: ledger-api 배치 수집 → `CalendarFeignClient` → core-api `stock_events` 테이블 upsert → 조회 시 `holdings_replica` JOIN으로 보유 종목 필터링

---

### GET `/api/trading/calendar` ✅ 구현완료

보유 종목 증권 캘린더 (월별 일정) 조회<br> Query: `year` (number, 선택), `month` (number, 선택) — 미지정 시 현재 월

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "증권 캘린더 조회 성공",
  "data": {
    "year": 2026,
    "month": 6,
    "days": [
      {
        "date": "2026-06-27",
        "events": [
          { "stockCode": "005930", "eventType": "DIVIDEND", "title": "삼성전자 배당락일" }
        ]
      },
      {
        "date": "2026-06-30",
        "events": [
          { "stockCode": "005930", "eventType": "EARNINGS", "title": "삼성전자 2Q 실적발표" }
        ]
      }
    ]
  }
}
```

---

### GET `/api/trading/calendar/events` ✅ 구현완료

보유 종목 주요 일정 목록 조회 (지정 월 전체)<br> Query: `year` (number, 선택), `month` (number, 선택) — 미지정 시 현재 월 / year 범위: 2000~2100

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "보유 종목 주요일정 조회 성공",
  "data": {
    "events": [
      {
        "stockCode": "005930",
        "eventType": "DIVIDEND",
        "eventDate": "2026-06-27",
        "title": "삼성전자 배당락일",
        "detail": "주당 배당금 361원"
      },
      {
        "stockCode": "005930",
        "eventType": "EARNINGS",
        "eventDate": "2026-06-30",
        "title": "삼성전자 2Q 실적발표",
        "detail": "2026년 2분기"
      }
    ]
  }
}
```

---

### [배치] 실적발표 일정 수집 — `EarningsBatchService` (feat/trading-calendar-ledger/#118)

> 외부 API로 보유 종목의 실적 발표 일정을 수집해 `CalendarFeignClient`로 upsert. 컨트롤러 없음.

**트리거**
- `@Scheduled` 주 1회 (월요일 새벽 2시)
- 매수 체결 이벤트 시 해당 종목 즉시 수집 (선택)

**외부 API 후보**
- KIS: 실적 발표 일정 API 지원 여부 우선 확인
- OpenDART `fnlttSinglAcntAll`: 보고서 제출일 기준 (발표 예정일 아님에 유의)

**upsert 요청 포맷** (`POST /internal/calendar/stock-events`)

```json
[
  {
    "stockCode": "005930",
    "eventType": "EARNINGS",
    "eventDate": "2026-06-30",
    "title": "삼성전자 2Q 실적발표",
    "detail": "2026년 2분기"
  }
]
```
```
