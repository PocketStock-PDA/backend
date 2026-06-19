# PocketStock API 명세 — 종목 추천

> 공통 헤더: `Authorization: Bearer {accessToken}` | `Content-Type: application/json`

## 종목 추천

### GET `/api/recommendations`

추천 종목 목록 조회<br> Query: `type` (PEER | SECTOR | MATURITY, 선택 — 미지정 시 전체 반환)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "추천 종목 조회 성공",
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
      "type": "SECTOR",
      "stockCode": "069960",
      "stockName": "현대백화점",
      "currentPrice": 58400,
      "changeRate": -0.34,
      "reason": "식료품 소비 비중 기반 소비재 섹터 추천"
    },
    {
      "type": "MATURITY",
      "stockCode": "105560",
      "stockName": "KB금융",
      "currentPrice": 82100,
      "changeRate": 0.86,
      "dividendYield": 4.2,
      "reason": "적금 만기 도래 — 배당주 투자 추천"
    }
  ]
}
```

---

### GET `/api/recommendations/maturity`

예적금 만기 도래 시 배당주 추천<br>
만기 90일 이내 예적금을 감지해 해당 금액 규모에 적합한 배당주를 추천한다.

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "만기 도래 배당주 추천 조회 성공",
  "data": {
    "triggerAccount": {
      "accountName": "신한 쏠편한 적금",
      "maturityDate": "2027-07-01",
      "maturityAmount": 8400000,
      "daysUntilMaturity": 377
    },
    "recommendations": [
      {
        "stockCode": "105560",
        "stockName": "KB금융",
        "currentPrice": 82100,
        "dividendYield": 4.2,
        "dividendPerShare": 3150,
        "exDividendDate": "2026-12-27",
        "reason": "안정적 배당 수익 — 연 4% 이상"
      },
      {
        "stockCode": "017670",
        "stockName": "SK텔레콤",
        "currentPrice": 51800,
        "dividendYield": 5.8,
        "dividendPerShare": 3380,
        "exDividendDate": "2026-12-27",
        "reason": "고배당 통신주 — 예적금 대안"
      }
    ]
  }
}
```

---

## 카드 추천

### △ GET `/api/recommendations/cards`

소비 패턴 기반 맞춤 카드 추천

> ⚠️ TODO: 사용자가 이미 보유한 카드 제외 필터 미구현 (`linked_cards` 연결 필요)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 401 Unauthorized

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
