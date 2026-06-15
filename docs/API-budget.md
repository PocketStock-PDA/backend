# PocketStock API 명세 — Budget

> 공통 헤더: `Authorization: Bearer {accessToken}` | `Content-Type: application/json`

## 가계부

### POST `/api/budget/goals/auto`

소비분석 기반 목표 자동설정

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
  "message": "소비분석 기반 목표 자동설정 성공",
  "data": {
  "monthlyBudget": 1500000,
  "categories": [
  {"category": "식비", "budget": 400000},
  {"category": "교통", "budget": 150000},
  {"category": "쇼핑", "budget": 300000}
  ]
 }
 }
```

---

### POST `/api/budget/goals`

가계부 목표 설정 (생활비/카테고리)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "monthlyBudget": 1500000,
  "categories": [
  {"category": "식비", "budget": 400000},
  {"category": "교통", "budget": 150000}
  ]
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "가계부 목표 설정 성공",
  "data": {
  "goalId": 1,
  "monthlyBudget": 1500000
 }
 }
```

---

### GET `/api/budget/goals`

가계부 목표 조회

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "가계부 목표 조회 성공",
  "data": {
  "goalId": 1,
  "monthlyBudget": 1500000,
  "spentAmount": 850000,
  "remainAmount": 650000,
  "categories": [
  {"category": "식비", "budget": 400000, "spent": 280000},
  {"category": "교통", "budget": 150000, "spent": 90000}
  ]
 }
 }
```

---

### GET `/api/budget/transactions`

일별/월별 소비내역 조회<br> Query: type (DAILY | MONTHLY, 선택), year (number, 선택), month (number, 선택), day (number, 선택)  (카드 소비 내역 기반: card_transactions JOIN budget_categories)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "소비내역 조회 성공",
  "data": {
  "transactions": [
  {
  "transactionId": 1,
  "category": "식비",
  "description": "스타벅스",
  "amount": 5500,
  "transactedAt": "2025-06-15T08:30:00"
  }
  ],
  "totalAmount": 85000
 }
 }
```

---

### GET `/api/budget/calendar`

파도 캘린더 (일별 예산) 조회<br> Query: year (number, 필수), month (number, 필수)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "파도 캘린더 조회 성공",
  "data": {
  "year": 2025,
  "month": 6,
  "dailyBudget": 50000,
  "days": [
  {"date": "2025-06-01", "spent": 32000, "status": "SAFE"},
  {"date": "2025-06-02", "spent": 68000, "status": "OVER"}
  ]
 }
 }
```

---

### GET `/api/budget/savings/by-category`

카테고리별 목표대비 절약 현황

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "카테고리별 절약 현황 조회 성공",
  "data": {
  "totalSaved": 320000,
  "categories": [
  {"category": "식비", "budget": 400000, "spent": 280000, "saved": 120000},
  {"category": "쇼핑", "budget": 300000, "spent": 100000, "saved": 200000}
  ]
 }
 }
```

---

### GET `/api/budget/comparison`

소비 섹터별 전월 비교

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "소비 전월 비교 조회 성공",
  "data": {
  "currentMonth": "2025-06",
  "previousMonth": "2025-05",
  "sectors": [
  {"sector": "식비", "current": 280000, "previous": 320000, "changeRate": -12.5},
  {"sector": "쇼핑", "current": 180000, "previous": 150000, "changeRate": 20.0}
  ]
 }
 }
```

---

### GET `/api/budget/savings`

절약금 현황 조회

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "절약금 현황 조회 성공",
  "data": {
  "totalSaved": 320000,
  "accumulatedInCma": 120000,
  "remainToCollect": 200000,
  "savingRate": 21.3
 }
 }
```

---

### POST `/api/budget/savings/agree`

절약금 모으기 동의

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "agreed": true
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "절약금 모으기 동의 성공",
  "data": {
  "agreed": true,
  "agreedAt": "2025-06-15T10:00:00"
 }
 }
```
