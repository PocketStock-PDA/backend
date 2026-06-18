# PocketStock API 명세 — Portfolio

> 공통 헤더: `Authorization: Bearer {accessToken}` | `Content-Type: application/json`

## 종목추천

### GET `/api/portfolio/recommendations`

추천 포트폴리오 조회 (또래2 + 우량주2)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "추천 포트폴리오 조회 성공",
  "data": [
  {
  "type": "PEER",
  "stockCode": "005930",
  "stockName": "삼성전자",
  "currentPrice": 73500,
  "changeRate": 1.66,
  "reason": "또래 투자자 선호 종목"
  },
  {
  "type": "QUALITY",
  "stockCode": "000660",
  "stockName": "SK하이닉스",
  "currentPrice": 185000,
  "changeRate": 0.54,
  "reason": "우량주 추천"
  }
 ]
 }
```

---

### POST `/api/portfolio/recommendations/refresh`

추천 종목 새로고침

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
  "message": "추천 종목 새로고침 성공",
  "data": null
 }
```

---

### GET `/api/portfolio/holdings`

보유 포트폴리오 현황 (비중·수익률)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "보유 포트폴리오 현황 조회 성공",
  "data": {
  "totalEvaluated": 3250000,
  "totalReturn": 250000,
  "returnRate": 8.33,
  "holdings": [
  {
  "stockCode": "005930",
  "stockName": "삼성전자",
  "quantity": 0.5,
  "weight": 22.6,
  "returnRate": 5.0
  }
  ]
 }
 }
```

---

## 자산리밸런싱

### GET `/api/portfolio/rebalancing/analysis`

종합 자산 분석 (자산구성)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "종합 자산 분석 조회 성공",
  "data": {
  "totalAsset": 50000000,
  "assets": [
  {"type": "STOCK", "label": "주식", "amount": 15000000, "ratio": 30.0},
  {"type": "SAVINGS", "label": "예적금", "amount": 20000000, "ratio": 40.0},
  {"type": "FUND", "label": "펀드", "amount": 10000000, "ratio": 20.0},
  {"type": "ETC", "label": "기타", "amount": 5000000, "ratio": 10.0}
  ]
 }
 }
```

---

### GET `/api/portfolio/rebalancing/networth`

순자산 (자산 - 부채) 조회

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "순자산 조회 성공",
  "data": {
  "totalAsset": 50000000,
  "totalDebt": 12000000,
  "networth": 38000000
 }
 }
```

---

### GET `/api/portfolio/rebalancing/peer`

또래 (연령·성별) 비중 비교

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "또래 비중 비교 조회 성공",
  "data": {
  "ageGroup": "20대",
  "gender": "여성",
  "myAssets": [
  {"type": "STOCK", "ratio": 30.0},
  {"type": "SAVINGS", "ratio": 40.0}
  ],
  "peerAssets": [
  {"type": "STOCK", "ratio": 45.0},
  {"type": "SAVINGS", "ratio": 35.0}
  ]
 }
 }
```

---

### POST `/api/portfolio/rebalancing/execute`

원클릭 리밸런싱 실행

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "targetRatios": [
  {"type": "STOCK", "ratio": 40.0},
  {"type": "SAVINGS", "ratio": 40.0},
  {"type": "ETC", "ratio": 20.0}
  ]
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "원클릭 리밸런싱 실행 성공",
  "data": null
 }
```

---

### GET `/api/portfolio/rebalancing/products`

예/적금 갈아타기 추천

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "예/적금 갈아타기 추천 조회 성공",
  "data": [
  {
  "productType": "SAVINGS",
  "productName": "쏠편한 정기예금",
  "bank": "신한은행",
  "rate": 3.85,
  "period": 12
  }
 ]
 }
```

---

### GET `/api/portfolio/rebalancing/isa`

ISA 계좌 가입 안내

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "ISA 계좌 가입 안내 조회 성공",
  "data": {
  "eligible": true,
  "message": "절세 혜택을 받을 수 있는 ISA 계좌를 개설해보세요.",
  "benefits": ["비과세 혜택", "분리과세 적용"],
  "guideUrl": "https://..."
 }
 }
```

---

## 증권캘린더

### GET `/api/portfolio/calendar`

증권 캘린더 (월별 일정) 조회<br> Query: year (number, 필수), month (number, 필수)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "증권 캘린더 조회 성공",
  "data": {
  "year": 2025,
  "month": 6,
  "events": [
  {
  "date": "2025-06-15",
  "type": "DIVIDEND",
  "title": "삼성전자 배당락일",
  "stockCode": "005930"
  }
  ]
 }
 }
```

---

### GET `/api/portfolio/calendar/events`

종목 주요일정 (배당·실적) 조회<br> Query: date (string, 선택) - 예: 2025-06-15

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "종목 주요일정 조회 성공",
  "data": [
  {
  "stockCode": "005930",
  "stockName": "삼성전자",
  "date": "2025-06-15",
  "eventType": "DIVIDEND",
  "description": "주당 배당금 361원"
  }
 ]
 }
```

---

### GET `/api/portfolio/calendar/recommendations`

캘린더 추천 종목

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "캘린더 추천 종목 조회 성공",
  "data": [
  {
  "stockCode": "005930",
  "stockName": "삼성전자",
  "eventDate": "2025-06-20",
  "reason": "배당 지급일 예정 종목"
  }
 ]
 }
```

---

## 카드추천

### △ GET `/api/portfolio/cards/recommendations`

소비 기반 맞춤 카드 추천

> ⚠️ TODO: 사용자가 이미 보유한 카드 제외 필터 미구현 (`linked_accounts.card_id` 연결 필요)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "맞춤 카드 추천 조회 성공",
  "data": [
  {
  "cardName": "신한 Deep Dream 카드",
  "cardCompany": "신한카드",
  "annualFee": 15000,
  "benefits": ["편의점 10% 할인", "OTT 30% 할인"],
  "matchRate": 92
  }
 ]
 }
```
