# 대표 페르소나 데이터셋 — 임혜진

기능 개발용 목(Mock) 데이터. 로그인 후 자산 연동 ~ 포트폴리오까지 전 플로우를 커버하는 1인 페르소나.

---

## 페르소나 개요

| 항목 | 값 |
|---|---|
| 이름 | 임혜진 |
| username | hyejin90 |
| 생년월일 | 1990-07-22 (만 35세) |
| 성별 | 여성 |
| 직업 | 전업주부 |
| 주 거래 | 신한은행 / 신한카드 |

> 전업주부를 주 타겟층으로 설정. 가계 지출 관리 니즈가 명확하고, 직접 소득 없이 예산을 관리하는 사용자 흐름을 검증하기 위한 페르소나.

---

## DB A — pocketstock_main

### db-a-users.csv
앱 가입일 2026-01-15 기준. password_hash는 bcrypt 더미값(실제 인증 불필요한 목 환경용).

---

### db-a-institution-master.csv
| 기관 | 카테고리 | 선택 이유 |
|---|---|---|
| 신한은행 | BANK | 주 거래 은행 |
| 신한카드 | CARD | 주 거래 카드사 |
| 마이신한포인트 | POINT | 신한 계열 포인트 |
| 국민은행 | BANK | 결혼 전부터 쓰던 구 계좌 (현재 휴면) |
| 카카오뱅크 | BANK | 소액 보관용 보조 통장 |
| KB국민카드 | CARD | 남편 명의 추가 카드 (완전 휴면) |
| 토스증권 | SECURITIES | 과거 소수점 주식 이벤트로 받은 주식 보유 중 |

> 신한 주거래 + 타 은행/카드 혼용 + 완전 휴면 계좌 패턴을 재현해 연동 화면 다양성 확보.

---

### db-a-linked-bank-accounts.csv
| 계좌 | 잔액 | 상태 | 근거 |
|---|---|---|---|
| 신한 입출금통장 | 1,855,600원 | 활성 | 월 생활비 200~300만원 구간 기준 월초 잔액 |
| 신한 쏠편한 적금 | 8,400,000원 | 활성 | 2025-07 가입, 2년 만기, 금리 3.5% |
| 국민 일반 입출금 | 12,300원 | 휴면 | 잔돈만 남은 미사용 계좌 |
| KB 정기예금 | 2,000,000원 | 휴면 | 만기(2024-03) 지난 미해지 예금 |
| 카카오뱅크 입출금 | 85,000원 | 활성 | 간헐적 사용 소액 보조 통장 |

---

### db-a-linked-cards.csv
| 카드 | 종류 | 사용 여부 | 근거 |
|---|---|---|---|
| 신한 Deep Dream 체크카드 | CHECK | 활성 | 마트·편의점·약국 등 일상 소액 결제 |
| 신한 Air One 신용카드 | CREDIT | 활성 | 외식·관리비·주유·통신비 등 고정·중액 지출 |
| KB직장인보너스카드 | CREDIT | 휴면 | 결제 연동 계좌(국민 입출금)도 휴면 → 거래 0건 |

---

### db-a-linked-points.csv
마이신한포인트 28,000P. 신한카드 실적 적립 기준 3개월 누적 추정치.

---

### db-a-linked-securities.csv / db-a-external-holdings.csv
토스증권에서 이벤트 등으로 소수점 주식 취득. 앱을 통해 직접 매수한 것이 아닌 외부 보유분.

| 종목 | 수량 | 취득 배경 |
|---|---|---|
| 삼성전자 (005930) | 0.5주 | 이벤트/프로모션으로 취득 추정 |
| SK하이닉스 (000660) | 0.2주 | 동일 |

---

### db-a-card-transactions.csv (53건)

**기간 및 수집 상태**
| 월 | 건수 | is_roundup_collected | 체크카드 라운드업 합계 |
|---|---|---|---|
| 2026-04 | 20건 | 체크카드 TRUE (결제 즉시) / 신용카드 FALSE | 4,000원 |
| 2026-05 | 20건 | 체크카드 TRUE (결제 즉시) / 신용카드 FALSE | 4,500원 |
| 2026-06 | 13건 | 체크카드 TRUE (결제 즉시) / 신용카드 FALSE | 3,300원 |

> 라운드업은 체크카드 결제 시점에 즉시 CMA로 수집. `roundup_collected_at = paid_at` (동일 타임스탬프).  
> 신용카드(card_id=2)는 라운드업 서비스 미등록 → `is_roundup_collected=FALSE`, `roundup_amount=NULL`.  
> 교통카드충전(30,000원 딱떨어지는 금액)은 roundup_amount=0이므로 CMA 입금 없음.

**카드별 지출 패턴**
- **신한 체크카드**: 이마트·마켓컬리(장보기 분할), 편의점(GS25·CU), 교통카드 충전, 약국, 카페, 올리브영, 다이소 — 소액 다건
- **신한 신용카드**: 배달·외식, 아파트관리비, 주유(실측 금액), 병원, 통신비, 구독서비스, 쇼핑, 학원비 — 고정·중액
- **KB카드**: 거래 없음 (완전 휴면)

**금액 근거**: 통계청 가계동향조사 2인 이상 가구 월 소비 200~300만원 구간 카테고리 비율 적용.

---

### db-a-budget-goals.csv
2026-06 한 달치. 전체 예산 250만원 + 10개 카테고리 세부 배분.

| 카테고리 | 예산 | 비중 |
|---|---|---|
| 전체 | 2,500,000원 | — |
| 식료품·비주류음료 | 450,000원 | 18% |
| 음식·숙박 | 400,000원 | 16% |
| 주거·수도·광열 | 500,000원 | 20% |
| 교통 | 250,000원 | 10% |
| 보건 | 200,000원 | 8% |
| 정보통신 | 150,000원 | 6% |
| 오락·문화 | 80,000원 | 3.2% |
| 의류·신발 | 150,000원 | 6% |
| 가정용품·가사서비스 | 100,000원 | 4% |
| 교육 | 150,000원 | 6% |

---

### db-a-budget-savings.csv
- 절약 목표 30만원 / 6월 현재 지출 733,300원 (카드거래 41~53번 합산과 일치, 6/19 기준)
- transfer_status=PENDING: 월말 정산 전 상태 재현

---

### db-a-holdings-replica.csv
DB B holdings의 core-api 조회용 복제본. 삼성전자 5주, 평균 매수가 70,500원.
- 현재가 71,800원 기준 평가액 359,000원, 수익률 +1.84%

---

### db-a-peer-benchmarks.csv (52행)
또래 비교 기능용 통계 기준값. 통계청 가계금융복지조사 자산 구성 비율 근사값 적용.

| 구성 | 내용 |
|---|---|
| 연령대 | 20대 / 30대 / 40대 / 50대 |
| 성별 | MALE / FEMALE |
| 자산구간 | 1억미만 / 1~3억 / 3~5억 / 5억이상 |
| 자산항목 | STOCK / SAVINGS / FUND / ETC |

임혜진(30대, FEMALE, 1억미만) 기준 매칭 벤치마크: `id 9~12`

---

### db-a-stock-events.csv
임혜진이 보유하거나 주목할 만한 종목 이벤트. 캘린더 기능 검증용.

| 종목 | 이벤트 | 날짜 |
|---|---|---|
| 삼성전자 | 배당락일 (주당 361원) | 2026-06-27 |
| LG전자 | 배당락일 (주당 500원) | 2026-06-28 |
| SK하이닉스 | 2Q26 실적발표 | 2026-07-24 |

---

### db-a-category-sector-map.csv
소비 카테고리 → 투자 섹터 매핑 테이블. 소비 패턴 기반 종목 추천 로직 연결용.

---

### db-a-notification-settings.csv
거래/목표/미체결 알림 ON, 마케팅 알림 OFF. 일반적인 초기 동의 패턴.

---

## DB B — pocketstock_ledger

### db-b-tradable-stocks.csv
삼성전자(005930) FK 충족을 위한 최소 마스터. 삼성전자 포함 대형주 5개 등록.

---

### db-b-cma-accounts.csv / db-b-cma-balances.csv
앱 가입 5일 후(2026-01-20) CMA 개설. 현재 잔액 405,490원, 연 3.5% 이자 적용.

---

### db-b-cma-transactions.csv (34건)
CMA 잔액 405,490원의 누적 내역. 4~6월 체크카드 라운드업은 결제 즉시 건별 수집.

| 구분 | 내용 |
|---|---|
| 초기 입금 | 2026-01-20 수동 입금 340,000원 |
| 끝전 수집 (카드) — 2~3월 | 월 집계 항목. card_transactions 데이터 없는 기간 |
| 끝전 수집 (카드) — 4~6월 | **건별 실시간 수집** (결제 시각 = roundup_collected_at). 4월 9건(4,000원)·5월 9건(4,500원)·6월 6건(3,300원) |
| 끝전 수집 (계좌) | 3월, 5월 신한 입출금 잔돈 수집 |
| 포인트 전환 | 4월 마이신한포인트 → CMA 15,000원 |
| 이자 | 2~5월 월 이자 (3.5% 연율 기준) |

> 교통카드충전(30,000원)은 roundup_amount=0이라 CMA 항목 없음 (4월 id=5, 5월 id=26, 6월 id=45 제외).  
> 6월 이자는 월말 미도래 → 미반영.

> 합계 검증: 340,000 + 8,340 + 1,250 + 5,600 + 11,200 + 2,100 + 4,000 + 15,000 + 2,800 + 4,500 + 4,300 + 3,100 + 3,300 = **405,490원** ✓

---

### db-b-collection-settings.csv
| source_type | source_ref_id | 대상 |
|---|---|---|
| ACCOUNT | 1 | 신한 입출금통장 |
| CARD | 1 | 신한 체크카드 |
| POINT | 1 | 마이신한포인트 |

> 라운드업 등록은 신한 체크카드 1개만 가능. 신용카드·타사 카드 미등록.

---

### db-b-cma-auto-charge-settings.csv
자동충전 OFF. CMA 잔액 부족 시 수동으로 판단하는 성향 반영.

---

### db-b-securities-accounts.csv
pocketstock 내부 국내 증권계좌. 2026-02-10 개설. 소수점 거래 활성화.

---

### db-b-deposit-transactions.csv
| 구분 | 금액 | 잔액 |
|---|---|---|
| DEPOSIT (신한 입출금 → 증권계좌 이체) | +500,000원 | 500,000원 |
| BUY (삼성전자 5주 × 70,500) | -352,500원 | 147,500원 |

---

### db-b-holdings.csv
삼성전자(005930) 5주, 평균 매수가 70,500원. 2026-02-10 매수.

---

### db-b-auto-invest-settings.csv
자동투자 OFF. CMA에 잔돈을 모으되 투자 시점은 직접 결정하는 패턴. (`keep_collecting_on_pause=TRUE` — 수집은 계속)

---

## INSERT 순서 (FK 의존성)

**DB A**
```
institution_master → users → linked_institutions
→ linked_bank_accounts → linked_cards → linked_points → linked_securities
→ external_holdings → card_transactions
→ budget_goals → budget_savings
→ holdings_replica → peer_benchmarks → stock_events
→ category_sector_map → notification_settings
```

**DB B**
```
tradable_stocks → securities_accounts → cma_accounts
→ cma_balances → cma_transactions → collection_settings → cma_auto_charge_settings
→ deposit_transactions → holdings → auto_invest_settings
```
