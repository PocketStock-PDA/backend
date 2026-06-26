# 카드 추천 로직

소비 패턴(최근 3개월 카드 지출)을 분석해, 사용자가 많이 쓰는 카테고리에 혜택이 몰린 카드를 점수화해 추천한다.

- **엔드포인트**: `GET /api/recommendations/cards`
- **구현**: `core-api` · `recommendations.card.CardRecommendationService#recommend`
- **API 스펙**: [API-recommendations.md](./API-recommendations.md) 참고

---

## 데이터 소스

| 테이블 | 용도 | 쿼리(`CardMapper`) |
|---|---|---|
| `card_transactions` | 최근 3개월 카테고리별 소비 집계 | `findCategorySpending` |
| `cards` | 추천 후보 카드(활성 + 미보유) | `findAllActiveCards` |
| `card_benefits` | 카드별 혜택(카테고리·설명) | `findAllBenefits` |
| `linked_cards` | 보유 카드 — 추천에서 제외 | `findAllActiveCards` 서브쿼리 |

- 소비 집계 윈도: **최근 3개월** (`now-3M` ~ `now+1d`, UTC), `is_cancelled = false`, `category IS NOT NULL`.
- 후보 카드: `cards.is_active = true` **AND** `linked_cards.card_master_id`에 없는 카드(이미 보유한 카드는 추천 안 함).

---

## 알고리즘 단계

1. **소비 집계** — `findCategorySpending`로 카테고리별 `SUM(amount)`을 금액 내림차순으로 가져온다.
2. **총액·비중 계산**
   - `total` = 전 카테고리 합. **`total == 0`이면 빈 응답** `{ topCategories: [], recommendations: [] }` 즉시 반환.
   - `spendRatio[category]` = `amount / total × 100` (소수 2자리).
3. **TOP 3 카테고리** — 금액 상위 3개를 `topCategories`로(정수 % 반올림). 화면 상단 "많이 쓴 곳" 표시용.
4. **후보 카드 로드** — 활성·미보유 카드 + 카드별 혜택(`benefitsByCard`).
5. **카드별 매칭 점수(`matchRate`) 계산** (아래 상세).
6. **필터·정렬·제한** — `matchRate > 0`만, `matchRate` 내림차순, **상위 5개**(`TOP_N`).

---

## 매칭 점수(matchRate) 계산

핵심 아이디어: **"내가 많이 쓰는 카테고리에 혜택을 주는 카드일수록 점수가 높다."**

각 후보 카드에 대해:

```
matchRate = Σ spendRatio[category]
            (단, 그 category의 매핑 키워드 중 하나라도
             이 카드의 혜택 카테고리 문자열에 포함되는 경우만 합산)
```

- 카드 혜택의 `benefit_category` 문자열들을 소문자 집합(`benefitKeywords`)으로 만든다.
- 소비 카테고리(`spendRatio`의 key)마다, `CATEGORY_MAP`의 키워드 목록 `k`를 보고
  **`benefitKeywords` 중 하나가 `k`를 부분문자열로 포함(`b.contains(k)`)** 하면 그 카테고리의 비중을 더한다.
- 즉 matchRate는 **이 카드가 커버하는 소비 카테고리들의 지출 비중 합**(%)이다.
- 최종값은 `min(round(matchRate), 100)`으로 0~100 정수.

> 예) 소비가 음식·숙박 50% + 교통 30% + 기타 20%인데, 카드가 음식·숙박·교통 혜택을 주면 → matchRate ≈ 80.

### 매칭된 혜택(`benefits`)

응답의 카드별 `benefits`는, 그 카드 혜택 중 **소비 카테고리 키워드와 매칭되는 것**만 골라 `(benefit_category, benefit_desc)`로, 중복 제거 후 **최대 3개**를 노출한다.

---

## 카테고리 → 혜택 키워드 매핑 (`CATEGORY_MAP`)

소비 카테고리(통계청 비목)를 카드 혜택 표기에 흔한 키워드로 잇는 사전. 혜택 카테고리 문자열이 이 키워드를 **부분 포함**하면 매칭으로 본다.

| 소비 카테고리 | 매핑 키워드(일부) |
|---|---|
| 교통 | 대중교통, 버스, 지하철, 택시, KTX, 하이패스, K-패스 … |
| 음식·숙박 | 음식점, 카페, 배달, 외식, 커피, 배민 … |
| 식료품·비주류음료 | 마트, 편의점, 장보기, CU, GS리테일 … |
| 보건 | 병원, 약국, 의료, 피트니스, 올리브영 |
| 오락·문화 | OTT, 멤버십, 영화, 테마파크, 디지털 구독 |
| 정보통신 | 통신, 알뜰폰, 공과금, 구독, 정기결제 … |
| 의류·신발 | 쇼핑, 백화점, 신세계, 온라인 … |
| 교육 | 학원, 인터넷서점, 교육업종 |
| 주거·수도·광열 | 아파트관리비, 공과금, 관리비, 주거 |
| 가정용품·가사서비스 | 생활 가맹점, 생활서비스, 일상생활비 |
| 주류·담배 | 주류, 술, 와인, 맥주, 담배 |
| 기타상품·서비스 | 전 가맹점, 국내 이용, 생활 가맹점 |

> 전체 키워드는 `CardRecommendationService.CATEGORY_MAP` 참조.

---

## 응답 구조

```jsonc
{
  "topCategories": [           // 소비 상위 3개 (정수 %)
    { "category": "음식·숙박", "percentage": 41 }
  ],
  "recommendations": [         // matchRate 상위 ≤5
    {
      "cardName": "...",
      "cardType": "CREDIT|CHECK",
      "imageUrl": "...",
      "applyUrl": "...",
      "annualFee": 15000,      // annual_fee_domestic
      "benefits": [ { "category": "...", "description": "..." } ],  // ≤3
      "matchRate": 92
    }
  ]
}
```

---

## 주의·한계

- **부분문자열 휴리스틱**: 매칭이 `benefit_category.contains(키워드)` 기반이라, 키워드/혜택 표기에 민감하다(오탐·누락 가능). 정교화하려면 카드 혜택 데이터에 정규화된 카테고리 코드를 두는 게 낫다.
- **matchRate는 "커버하는 지출 비중 합"**이지, 실제 절감액 추정이 아니다. 할인율·한도·실적조건은 반영하지 않는다.
- 연회비는 **국내 연회비(`annual_fee_domestic`)만** 노출(해외 연회비 별도 컬럼 미반영).
- 보유 카드 제외는 `linked_cards.card_master_id` 기준 — 마스터 매칭이 안 된 보유 카드는 제외되지 않을 수 있다.
- 소비 데이터가 전혀 없으면(`total == 0`) 추천·상위 카테고리 모두 빈 배열.
- ⚠️ 기존 [API-recommendations.md](./API-recommendations.md)의 카드 추천 응답 예시는 구버전(`cardCompany`, `benefits`가 문자열 배열, `topCategories` 누락)이라 **실제 DTO와 불일치** — 위 응답 구조가 정확.
