# PocketStock API 명세 — Asset

> 공통 헤더: `Authorization: Bearer {accessToken}` | `Content-Type: application/json`

## 자산연동

### GET `/api/assets/institutions`

연동 가능 기관 목록

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "연동 가능 기관 목록 조회 성공",
  "data": [
  {
  "institutionId": "001",
  "name": "신한은행",
  "type": "BANK",
  "logoUrl": "https://..."
  }
 ]
 }
```

---

### POST `/api/assets/links/auth`

마이데이터 통합인증

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "institutions": ["001", "002", "003"]
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "마이데이터 통합인증 성공",
  "data": {
  "authToken": "MYD-AUTH-TOKEN"
 }
 }
```

---

### POST `/api/assets/links`

최초 자산 연동 (선택 기관 일괄)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "authToken": "MYD-AUTH-TOKEN",
  "institutions": ["001", "002"]
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "자산 연동 성공",
  "data": {
  "linkedCount": 2
 }
 }
```

---

### POST `/api/assets/links/bank`

은행 계좌 연동

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "bankCode": "088",
  "accountNumber": "110-123-456789"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "은행 계좌 연동 성공",
  "data": {
  "accountId": 101,
  "balance": 1500000
 }
 }
```

---

### POST `/api/assets/links/card`

카드 연동

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "cardCompany": "신한카드"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "카드 연동 성공",
  "data": {
  "linked": true,
  "cardCount": 2
 }
 }
```

---

### POST `/api/assets/links/point`

포인트 연동

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "provider": "OK캐쉬백"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "포인트 연동 성공",
  "data": {
  "provider": "OK캐쉬백",
  "balance": 25000
 }
 }
```

---

### POST `/api/assets/links/fx`

SOL트래블 외화잔액 연동

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
  "message": "SOL트래블 외화잔액 연동 성공",
  "data": {
  "usdBalance": 250.00,
  "krwEquivalent": 345000
 }
 }
```

---

### POST `/api/assets/links/securities`

타 증권사 연동

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "company": "키움증권",
  "accountNo": "123-456789"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "타 증권사 연동 성공",
  "data": {
  "linked": true,
  "holdingsCount": 5
 }
 }
```

---

### GET `/api/assets`

연동 자산 전체 조회

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "연동 자산 전체 조회 성공",
  "data": {
  "totalAsset": 52000000,
  "categories": [
  {"type": "BANK", "label": "은행", "amount": 20000000},
  {"type": "SECURITIES", "label": "증권", "amount": 15000000},
  {"type": "CARD", "label": "카드", "amount": 3000000},
  {"type": "POINT", "label": "포인트", "amount": 25000}
  ]
 }
 }
```

---

### POST `/api/assets/refresh`

연동 자산 새로고침 (최신화)

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
  "message": "자산 새로고침 성공",
  "data": null
 }
```

---

### GET `/api/assets/scan`

잠자는 잔돈 스캔

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "잠자는 잔돈 스캔 성공",
  "data": {
  "totalAmount": 12450,
  "accounts": [
  {"bankName": "신한은행", "accountNo": "110-***-456789", "amount": 7450},
  {"bankName": "국민은행", "accountNo": "012-***-123456", "amount": 5000}
  ]
 }
 }
```

---

### GET `/api/assets/dormant`

휴면계좌 조회

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "휴면계좌 조회 성공",
  "data": [
  {
  "accountNo": "110-***-111222",
  "bankName": "신한은행",
  "balance": 50000,
  "dormantSince": "2023-01-01"
  }
 ]
 }
```

---

### POST `/api/assets/dormant/close`

휴면계좌 해지·잔액 이체

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "accountNo": "110-***-111222",
  "targetAccount": "110-456-789012"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "휴면계좌 해지 및 이체 성공",
  "data": {
  "transferredAmount": 50000
 }
 }
```

---

## 소비패턴분석

### GET `/api/assets/spending`

소비패턴 분석 결과 조회<br> Query: year (number, 선택), month (number, 선택)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "소비패턴 분석 결과 조회 성공",
  "data": {
  "totalSpending": 1850000,
  "categories": [
  {"category": "식비", "amount": 450000, "ratio": 24.3},
  {"category": "교통", "amount": 120000, "ratio": 6.5},
  {"category": "쇼핑", "amount": 380000, "ratio": 20.5}
  ]
 }
 }
```

---

### GET `/api/assets/spending/report`

소비분석 리포트<br> Query: year (number, 선택), month (number, 선택)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "소비분석 리포트 조회 성공",
  "data": {
  "period": "2025-06",
  "insight": "이번 달 식비가 전월 대비 12% 증가했습니다.",
  "topCategory": "식비",
  "savingTip": "외식 횟수를 주 1회 줄이면 월 3만원 절약 가능"
 }
 }
```

---

## 타사소수점

### GET `/api/assets/external-holdings`

타사 보유 소수점 통합 조회

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "타사 보유 소수점 조회 성공",
  "data": [
  {
  "company": "키움증권",
  "stocks": [
  {"stockCode": "005930", "stockName": "삼성전자", "quantity": 0.5, "evaluated": 36750}
  ]
  }
 ]
 }
```
