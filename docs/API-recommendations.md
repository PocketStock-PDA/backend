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
      "stockCode": "030000",
      "stockName": "제일기획",
      "dividendYield": 6.44,
      "reason": "현재 예금 이율보다 높은 배당 수익률"
    }
  ]
}
```

---

### GET `/api/recommendations/maturity`

예적금 만기 30일 이내 감지 시 현재 이율보다 배당수익률이 높은 배당주를 추천한다.

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
      "accountName": "신한 단기 정기예금",
      "maturityDate": "2026-07-10",
      "principalAmount": 3000000,
      "daysUntilMaturity": 20,
      "interestRate": 2.5
    },
    "recommendations": [
      {
        "stockCode": "017800",
        "stockName": "현대엘리베이터",
        "category": "건설장비",
        "dividendYield": 18.08,
        "tags": ["고배당주", "이익 초과 배당"],
        "exDividendDate": null,
        "reason": "현재 예금 이율(2.5%)보다 높은 배당 수익률"
      },
      {
        "stockCode": "030000",
        "stockName": "제일기획",
        "category": "광고",
        "dividendYield": 6.44,
        "tags": ["배당이 쏠쏠해요", "번 돈 절반 나눠요"],
        "exDividendDate": null,
        "reason": "현재 예금 이율(2.5%)보다 높은 배당 수익률"
      }
    ]
  }
}
```

> 만기 30일 이내 예적금이 없으면 `data: null` 반환.

---

## 카드 추천

### GET `/api/recommendations/cards`

소비 패턴 기반 맞춤 카드 추천 (보유 카드 자동 제외)

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
