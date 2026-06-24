
# PocketStock API 명세

> 협업용 단일 문서. 각자 **자기 담당 행만** 수정하고 feature 브랜치 → `dev` PR. `.md`라 git이 줄단위 병합합니다.

## 👥 담당자

| 담당 | 영역 | API 수 |
|---|---|---|
| **A·우정인** | 회원·인증 · 증권계좌 개설 · 알림 · 퍼즐/보상 | 30 |
| **B·김준형** | 소수점 매매엔진 · 정기적립 · 시세/실시간시세 | 33 |
| **C·강문군** | 자산연동 · CMA · 환전 · 매수/매도 탭 | 35 |
| **D·김서현** | 소비분석 · 종목추천 · 가계부 · 캘린더 · 리밸런싱 | 24 |
| | **합계** | **122** |

## 📌 범례

- **TR코드 = 시세 데이터 출처(실전 실연동)**: 국내 `[LS 실전]`, 해외 `[KIS 실전]`.
- **계좌·예수금·주문·체결·잔고·소수점 원장은 전부 포켓스톡 자체 시뮬**(`[자체 시뮬]`) — 브로커 주문/계좌 API 미사용. 표기된 주문 TR은 설계 참조용.

## User

| 대분류 | Description | Method | URI | LS TR코드 | 담당 | 완료 |
|---|---|---|---|---|---|---|
| 회원·인증 | 아이디 중복 확인 | GET | `/api/users/check-username` |  | A·우정인 | ✅ |
| 회원·인증 | 회원가입(이름·주민번호 앞6+뒷1·아이디·비번·휴대폰) | POST | `/api/users/signup` |  | A·우정인 | ✅ |
| 회원·인증 | 비밀번호 보안규칙 실시간 검증 | POST | `/api/users/validate-password` |  | A·우정인 | ✅ |
| 회원·인증 | SMS 인증번호 발송 | POST | `/api/auth/sms/send` |  | A·우정인 | ✅ |
| 회원·인증 | SMS 인증번호 확인 | POST | `/api/auth/sms/verify` |  | A·우정인 | ✅ |
| 회원·인증 | 난수문자 인증요청(휴대폰 본인확인 mock) | POST | `/api/auth/shinhan-cert/request` |  | A·우정인 | ✅ |
| 회원·인증 | 난수문자 대조 확인(echo) | POST | `/api/auth/shinhan-cert/verify` |  | A·우정인 | ✅ |
| 회원·인증 | ID/PW 로그인(JWT 발급) | POST | `/api/auth/login` |  | A·우정인 | ✅ |
| 회원·인증 | PIN/패턴 간편 로그인 | POST | `/api/auth/login/pin` |  | A·우정인 | ✅ |
| 회원·인증 | 토큰 재발급(Refresh) | POST | `/api/auth/refresh` |  | A·우정인 | ✅ |
| 회원·인증 | 로그아웃 | POST | `/api/auth/logout` |  | A·우정인 | ✅ |
| 회원·인증 | 아이디 찾기 | POST | `/api/users/find-username` |  | A·우정인 | ✅ |
| 회원·인증 | 비밀번호 찾기/재설정 | POST | `/api/users/reset-password` |  | A·우정인 | ✅ |
| 회원·인증 | 약관 동의 등록 | POST | `/api/users/terms` |  | A·우정인 | ✅ |
| 회원·인증 | PIN/패턴 설정 | POST | `/api/users/auth-method` |  | A·우정인 | ✅ |
| 회원·인증 | 계좌 비밀번호 설정 | POST | `/api/users/account-password` |  | A·우정인 | ✅ |
| 회원·인증 | 거래 인증(계좌비번 검증, 30분 유지) | POST | `/api/users/account-password/verify` |  | A·우정인 | ✅ |
| 회원·인증 | 회원정보(비밀번호) 수정 | PUT | `/api/users/me` |  | A·우정인 | ✅ |

## Asset

| 대분류 | Description | Method | URI | LS TR코드 | 담당 | 완료 |
|---|---|---|---|---|---|---|
| 자산연동 | 연동 가능 기관 목록 | GET | `/api/assets/institutions` |  | C·강문군 |  |
| 자산연동 | 마이데이터 통합인증 | POST | `/api/assets/links/auth` |  | C·강문군 |  |
| 자산연동 | 최초 자산 연동(선택 기관 일괄) | POST | `/api/assets/links` |  | C·강문군 |  |
| 자산연동 | 은행 계좌 연동 | POST | `/api/assets/links/bank` |  | C·강문군 |  |
| 자산연동 | 카드 연동 | POST | `/api/assets/links/card` |  | C·강문군 |  |
| 자산연동 | 포인트 연동 | POST | `/api/assets/links/point` |  | C·강문군 |  |
| 자산연동 | SOL트래블 외화잔액 연동 | POST | `/api/assets/links/fx` |  | C·강문군 |  |
| 자산연동 | 타 증권사 연동 | POST | `/api/assets/links/securities` |  | C·강문군 |  |
| 자산연동 | 보유 은행 계좌 목록 조회(1원 인증·재원 계좌 선택 공용) | GET | `/api/assets/bank-accounts` |  | C·강문군 | ✅ |
| 자산연동 | 계좌 1원 인증 송금요청(코드 푸시 발송) | POST | `/api/assets/bank-accounts/{accountId}/verification` |  | C·강문군 | ✅ |
| 자산연동 | 계좌 1원 인증 확인(코드 검증) | POST | `/api/assets/bank-accounts/{accountId}/verification/confirm` |  | C·강문군 | ✅ |
| 자산연동 | 가입단계 계좌 1원 인증 송금요청(공개·mock, 응답에 코드) | POST | `/api/auth/account-verify/request` |  | C·강문군 | ✅ |
| 자산연동 | 가입단계 계좌 1원 인증 확인(공개·코드 대조) | POST | `/api/auth/account-verify/confirm` |  | C·강문군 | ✅ |
| 자산연동 | 연동 자산 새로고침(최신화) | POST | `/api/assets/refresh` |  | C·강문군 |  |
| 자산연동 | 잠자는 잔돈 스캔 | GET | `/api/assets/scan` |  | C·강문군 |  |
| 자산연동 | 휴면계좌 조회 | GET | `/api/assets/dormant` |  | C·강문군 |  |
| 자산연동 | 휴면계좌 해지·잔액 이체 | POST | `/api/assets/dormant/close` |  | C·강문군 |  |
| 소비패턴분석 | 소비패턴 분석 결과 조회 | GET | `/api/assets/spending` |  | D·김서현 | ✅ |
| 소비패턴분석 | 소비분석 리포트 | GET | `/api/assets/spending/report` |  | D·김서현 | ✅ |
| 자산분석 | 자산 분석 탭 요약 (순자산·포트폴리오·고정비/변동비 합산) | GET | `/api/assets/summary` |  | D·김서현 | ✅ |
| 타사소수점 | 타사 보유 소수점 통합 조회 | GET | `/api/assets/external-holdings` |  | C·강문군 |  |

## CMA

| 대분류 | Description | Method | URI | LS TR코드 | 담당 | 완료 |
|---|---|---|---|---|---|---|
| 계좌 | CMA 계좌 개설(서비스 진입 게이트, 멱등) | POST | `/api/cma/account` |  | C·강문군 | ✅ |
| 홈 | 홈 대시보드(CMA잔액+수집가능 잔돈) | GET | `/api/cma/home` |  | C·강문군 | ✅ |
| 잔돈수집 | 잔돈 모으기 실행(통합) | POST | `/api/cma/collect` |  | C·강문군 |  |
| 잔돈수집 | 계좌 끝전 적립 | POST | `/api/cma/collect/account` |  | C·강문군 |  |
| 잔돈수집 | 카드 라운드업 적립 | POST | `/api/cma/collect/card` |  | C·강문군 |  |
| 잔돈수집 | 포인트 전환 적립 | POST | `/api/cma/collect/point` |  | C·강문군 |  |
| 잔돈수집 | 외화 잔돈 적립(연동 USD 지갑 → CMA 달러풀, 환전 없음) | POST | `/api/cma/collect/fx` |  | C·강문군 | ✅ |
| 잔돈수집 | 적립 소스 설정(카드/계좌별 ON/OFF) | PUT | `/api/cma/collect/settings` |  | C·강문군 | ✅ |
| 잔돈수집 | 적립 이력 조회 | GET | `/api/cma/collect/history` |  | C·강문군 | ✅ |
| CMA | CMA 잔액·성과율(원화RP/외화RP) 조회 | GET | `/api/cma/balance` |  | C·강문군 | ✅ |
| CMA | CMA 계좌내역(입금·출금·이자) 조회 | GET | `/api/cma/transactions` |  | C·강문군 | ✅ |
| 자금이체 | CMA 풀 → 위탁 예수금 이체(매수용 충전, BUY_TRANSFER) | POST | `/api/cma/transfer` |  | C·강문군 | ✅ |
| 자금이체 | 자금 이동 이력 조회 | GET | `/api/cma/transfers` |  | C·강문군 | ✅ |
| 충전 | 은행계좌 → CMA 원화풀 부족분 충전(DEPOSIT) | POST | `/api/cma/deposit` |  | C·강문군 | ✅ |
| 자동충전 | 부족금액 자동충전 설정 조회 | GET | `/api/cma/auto-charge-settings` |  | C·강문군 |  |
| 자동충전 | 부족금액 자동충전 설정(ON/OFF·1회한도·대상계좌) | PUT | `/api/cma/auto-charge-settings` |  | C·강문군 |  |

## Trading

| 대분류 | Description | Method | URI | LS TR코드 | 담당 | 완료 |
|---|---|---|---|---|---|---|
| 증권계좌 | 종합계좌 개설(국내·해외 위탁) — CMA는 `/api/cma/account` 별도 | POST | `/api/trading/accounts` |  | B·김준형 | ✅ |
| 증권계좌 | 계좌 상태 조회 | GET | `/api/trading/accounts` |  | B·김준형 | ✅ |
| 증권계좌 | 예수금/출금가능금액 조회(국내 KRW+해외 USD) | GET | `/api/trading/deposit` |  | B·김준형 | ✅ balances 시장별 분해 #137 |
| 시세 | 종목 카테고리 탐색(40대 여성 상위 등) | GET | `/api/trading/stocks/categories` |  | B·김준형 |  |
| 시세 | 국내 종목 순위(sort=tradevalue/marketcap, 상위 30) | GET | `/api/trading/stocks/rankings/domestic` | t1463 (거래대금상위) [LS 실전] | B·김준형 | ✅ LS t1463 거래대금·시총 동시·ETF제외→유니버스 필터·재랭킹 |
| 시세 | 해외 종목 순위(sort=tradevalue/marketcap, 상위 30) | GET | `/api/trading/stocks/rankings/overseas` | HHDFS76320010(거래대금)·HHDFS76350100(시총) [KIS 실전] | B·김준형 | ✅ KIS NAS/NYS 머지·개별주만→유니버스 필터·재랭킹. 정렬 지표만 채움(반대쪽 null)·USD |
| 시세 | 종목 검색(자체 종목마스터) | GET | `/api/trading/stocks/search` |  | B·김준형 | ✅ |
| 시세 | 종목 상세(마스터+현재가 합성) | GET | `/api/trading/stocks/{stockCode}` |  | B·김준형 | ✅ |
| 시세 | [국내] 현재가 조회 | GET | `/api/trading/stocks/{stockCode}/price?market=domestic` | t1102 [LS 실전] | B·김준형 | ✅ |
| 시세 | [해외] 현재가 조회 | GET | `/api/trading/stocks/{stockCode}/price?market=overseas` | HHDFS76200200 (해외 현재가상세) [KIS 실전] | B·김준형 | ✅ |
| 시세 | [국내] 호가 조회(온주 전용) | GET | `/api/trading/stocks/{stockCode}/orderbook?market=domestic` | t8450 (현재가호가) [LS 실전] | B·김준형 | ✅ |
| 시세 | [해외] 현재가·호가 조회(온주 전용) | GET | `/api/trading/stocks/{stockCode}/orderbook?market=overseas` | HHDFS76200100 (해외 현재가호가) [KIS 실전] | B·김준형 | ✅ |
| 실시간시세 | [국내] 실시간 체결가(소수점·온주) | WS | `/topic/stock/trade/{stockCode}` | US3 (통합 체결) [LS 실전] | B·김준형 | ✅ |
| 실시간시세 | [국내] 실시간 호가(온주) | WS | `/topic/asking/{stockCode}` | UH1 (통합 호가잔량) [LS 실전] | B·김준형 | ✅ |
| 실시간시세 | [해외] 실시간 체결가 | WS | `/topic/foreign/transaction/{symbol}` | HDFSCNT0 (해외 실시간지연체결가) [KIS 실전] | B·김준형 | ✅ |
| 실시간시세 | [해외] 실시간 호가(온주) | WS | `/topic/foreign/quote/{symbol}` | HDFSASP0 (해외 실시간호가) [KIS 실전] | B·김준형 | ✅ |
| 실시간시세 | 실시간 체결통보(주문 결과) | WS | `/topic/order-notification` | SC1(국내)·AS1(해외) [자체 시뮬] | B·김준형 |  |
| 소수점투자 | 소수점 매수/매도(side로 구분) → 차수 합산 온주주문 | POST | `/api/trading/orders/fractional` | CSPAT00601(국내)·COSAT00301/COSMT00300(해외) [자체 시뮬] | B·김준형 | ✅ side=BUY/SELL(body)·접수=즉시 QUEUED(비동기)·국내먼저(D4) #151 |
| 소수점투자 | 온주 매수/매도(호가 기반) | POST | `/api/trading/orders/whole` | CSPAT00601(국내)·COSAT00301(해외) [자체 시뮬] | B·김준형 | ✅ |
| 소수점투자 | 주문 취소(소수점 QUEUED·온주 PENDING 공용) | DELETE | `/api/trading/orders/{orderId}` | CSPAT00801(국내)·COSAT00311(해외) [자체 시뮬] | B·김준형 | ✅ |
| 소수점투자 | 거래내역 조회(매수·매도·달성) | GET | `/api/trading/orders` |  | B·김준형 | ✅ |
| 소수점투자 | 미체결 주문 조회(온주 PENDING + 소수점 QUEUED) | GET | `/api/trading/orders/pending` |  | B·김준형 | ✅ |
| 소수점투자 | 보유종목·잔고(평가·수익률) 조회 | GET | `/api/trading/holdings` |  | B·김준형 | ✅ |
| 소수점투자 | 온주 전환 실행(소수→온주, 사용자 버튼) | POST | `/api/trading/whole-shares` |  | B·김준형 | ✅ 소수 정수부를 온주(직접소유)로 굳힘 #157 |
| 소수점투자 | 온주 전환내역 조회 | GET | `/api/trading/whole-shares` |  | B·김준형 | ✅ #157 |
| 정기적립식 | 자동모으기 설정 등록(주기 base) | POST | `/api/trading/auto-invest` |  | B·김준형 | ✅ |
| 정기적립식 | 자동모으기 설정 수정 | PUT | `/api/trading/auto-invest/{id}` |  | B·김준형 | ✅ |
| 정기적립식 | 자동모으기 일시중지/재개(PAUSE/RESUME) | PATCH | `/api/trading/auto-invest/{id}/status` |  | B·김준형 | ✅ |
| 정기적립식 | 자동모으기 해제(완전 삭제, 트리거·회차로그 CASCADE) | DELETE | `/api/trading/auto-invest/{id}` |  | B·김준형 | ✅ |
| 정기적립식 | 자동모으기 종목 추가(독립형이라 `POST /auto-invest`와 중복 → 추후 여러 종목 일괄등록 용도면 부활) | POST | `/api/trading/auto-invest/stocks` |  | B·김준형 | 잠정폐지 |
| 정기적립식 | 자동모으기 종합 설정 조회 | GET | `/api/trading/auto-invest` |  | B·김준형 | ✅ |
| 정기적립식 | 자동모으기 단건 상세 조회 | GET | `/api/trading/auto-invest/{id}` |  | B·김준형 | ✅ |
| 정기적립식 | 종목별 모으기 실행 내역(회차별 체결/실패) 조회 | GET | `/api/trading/auto-invest/{id}/executions` |  | B·김준형 | ✅ #195 |
| 정기적립식 | 수익률 트리거 등록(물타기 ADD_ON_LOSS·익절 TAKE_PROFIT, 옵션 부가기능) | POST | `/api/trading/auto-invest/{id}/triggers` |  | B·김준형 |  |
| 정기적립식 | 수익률 트리거 목록 조회 | GET | `/api/trading/auto-invest/{id}/triggers` |  | B·김준형 |  |
| 정기적립식 | 수익률 트리거 해제 | DELETE | `/api/trading/auto-invest/{id}/triggers/{triggerId}` |  | B·김준형 |  |
| 퍼즐 | 퍼즐 진행률 조회(조각/완성) | GET | `/api/trading/puzzle/{stockCode}` |  | B·김준형 |  |
| 웰컴보상 | 웰컴보상 후보 종목 조회(국내 거래대금 1·2위 + 해외 1·2위) | GET | `/api/trading/rewards/welcome/candidates` |  | B·김준형 | ✅ |
| 웰컴보상 | 웰컴보상 종목 선택·지급(1,000원어치 소수점) | POST | `/api/trading/rewards/welcome` |  | B·김준형 | ✅ |
| 웰컴보상 | 보상 지급 내역 조회 | GET | `/api/trading/rewards` |  | B·김준형 | ✅ |
| 증권캘린더 | 보유 종목 증권 캘린더(월별 일정) 조회 | GET | `/api/trading/calendar` |  | D·김서현 | ✅ |
| 증권캘린더 | 보유 종목 주요일정(배당·실적) 조회 | GET | `/api/trading/calendar/events` |  | D·김서현 | ✅ |
| 시세 | [국내] 종목 기업정보 | GET | `/api/trading/stocks/{stockCode}/info?market=domestic` | t3320 (FNG요약) [LS 실전] | B·김준형 | 잠정폐지 |
| 시세 | [해외] 종목 기업정보 | GET | `/api/trading/stocks/{stockCode}/info?market=overseas` | HHDFS76200200 (해외 현재가상세) [KIS 실전] | B·김준형 | 잠정폐지 |

> 참고: 해외 `현재가 조회`·`종목 기업정보`는 같은 KIS TR(HHDFS76200200) 응답을 시세/지표로 나눠 쓴 것. 한 화면에서 둘 다 호출 시 KIS 응답을 짧게 캐시해 중복 호출 줄일 것.
>
> 참고: 웰컴보상 후보(`rewards/welcome/candidates`)는 국내 거래대금순위 1·2위 + 해외 거래대금순위 1·2위 = 4종목. 둘 다 KIS(모의 미지원 → 실전 토큰 필요). 온보딩(계좌개설+연동) 완료 후 1인 1회, 선택 종목에 1,000원어치 소수점 무상 지급(예수금 차감 없음, 해외는 매매기준율로 KRW→USD 환산).
> - 국내: LS `t1463`(거래대금상위, `/stock/high-item`) — 거래대금 desc·ETF 제외(jc_num2=1), 거래대금값 `value`(백만원→원 환산). 순위 API(`/stocks/rankings`)와 동일 소스(`LsRankingClient`) 공유. (구 KIS `국내주식-047` FHPST01710000에서 교체)
> - 해외: KIS `해외주식-044`(HHDFS76320010, `/uapi/overseas-stock/v1/ranking/trade-pbmn`), 거래대금값 `tamt`.
> - 선택 시 `POST rewards/welcome`으로 1종목 1,000원어치 소수점 지급.

## Recommendations

| 대분류 | Description | Method | URI | LS TR코드 | 담당 | 완료 |
|---|---|---|---|---|---|---|
| 종목추천 | 추천 종목 조회(또래·소비섹터·만기, type 쿼리로 필터) | GET | `/api/recommendations` |  | D·김서현 |  |
| 종목추천 | 예적금 만기 도래 → 배당주 추천 | GET | `/api/recommendations/maturity` |  | D·김서현 | ✅ |
| 카드추천 | 소비 기반 맞춤 카드 추천 | GET | `/api/recommendations/cards` |  | D·김서현 | ✅ |

## Budget

| 대분류 | Description | Method | URI | LS TR코드 | 담당 | 완료 |
|---|---|---|---|---|---|---|
| 가계부 | 소비분석 기반 목표 자동설정 | POST | `/api/budget/goals/auto` |  | D·김서현 | ✅ |
| 가계부 | 가계부 목표 설정(생활비/카테고리) | POST | `/api/budget/goals` |  | D·김서현 | ✅ |
| 가계부 | 가계부 목표 조회 | GET | `/api/budget/goals` |  | D·김서현 | ✅ |
| 가계부 | 일별/월별 소비내역 조회 | GET | `/api/budget/transactions` |  | D·김서현 | ✅ |
| 가계부 | 파도 캘린더(일별 예산) 조회 | GET | `/api/budget/calendar` |  | D·김서현 | ✅ |
| 가계부 | 카테고리별 목표대비 절약 현황 | GET | `/api/budget/savings/by-category` |  | D·김서현 | ✅ |
| 가계부 | 소비 섹터별 전월 비교 | GET | `/api/budget/comparison` |  | D·김서현 | ✅ |
| 가계부 | 절약금 현황 조회 | GET | `/api/budget/savings` |  | D·김서현 | ✅ |
| 가계부 | 절약금 모으기 동의 | POST | `/api/budget/savings/agree` |  | D·김서현 | ✅ |

## Exchange

| 대분류 | Description | Method | URI | LS TR코드                 | 담당 | 완료 |
|---|---|---|---|-------------------------|---|---|
| 환전 | 환율 조회(USD/KRW, 예상 환전금액) | GET | `/api/exchange/rate` | CUR 기반(시세 캐시) [LS 실전]   | B·김준형 | ✅ |
| 환전 | 실시간 환율(USD/KRW) | WS | `/topic/currency/usd-krw` | CUR (현물USD 실시간) [LS 실전] | B·김준형 | ✅ |
| 환전 | 환전 가능여부·가능금액 검증 | GET | `/api/exchange/validate` |                         | B·김준형 | ✅ direction(KRW_TO_USD/USD_TO_KRW)+amount(선택), dry-run |
| 환전 | 원화→달러 환전 | POST | `/api/exchange/krw-to-usd` |                         | B·김준형 | ✅ |
| 환전 | 달러→원화 환전 | POST | `/api/exchange/usd-to-krw` |                         | B·김준형 | ✅ |
| 환전 | 자동환전 설정 조회 | GET | `/api/exchange/auto-settings` |                         | B·김준형 | ✅ |
| 환전 | 자동환전 설정(달러우선·한도·잔돈) | PUT | `/api/exchange/auto-settings` |                         | B·김준형 | ✅ |
| 환전 | 환전 이력 조회 | GET | `/api/exchange/history` |                         | B·김준형 | ✅ |

## Notification

| 대분류 | Description | Method | URI | LS TR코드 | 담당 | 완료 |
|---|---|---|---|---|---|---|
| 알림 | 알림 목록(알림센터) 조회 | GET | `/api/notifications` |  | A·우정인 | ✅ |
| 알림 | 알림 읽음 처리 | PATCH | `/api/notifications/{id}/read` |  | A·우정인 | ✅ |
| 알림 | 알림 전체 읽음 | PATCH | `/api/notifications/read-all` |  | A·우정인 | ✅ |
| 알림 | 푸시 토큰 등록 | POST | `/api/notifications/token` |  | A·우정인 | ✅ |
| 알림 | 알림 수신 설정 | PUT | `/api/notifications/settings` |  | A·우정인 | ✅ |

## Recon

> 원장 복식부기 정합성 검산용. 전사 감사라 특정 유저가 아닌 전역 집계 — 실서비스는 관리자 권한 게이팅 필요(현재 인증만, TODO).

| 대분류 | Description | Method | URI | LS TR코드 | 담당 | 완료 |
|---|---|---|---|---|---|---|
| 정합성 | 원장 복식부기 불변식 검산(balanced·깨진 통화/종목) | GET | `/api/recon/ledger` |  | B·김준형 | ✅ #96 |

## Internal (MSA 내부 전용)

> 서비스 간 내부 호출 전용(core↔ledger). 클라이언트 비공개 — `@CurrentUserId` 대신 본문/파라미터 userId 인증.

| 대분류 | Description | Method | URI | LS TR코드 | 담당 | 완료 |
|---|---|---|---|---|---|---|
| CMA | 휴면계좌 해지 잔액 CMA 적립(DORMANT, 멱등) | POST | `/internal/cma/credit` |  | C·강문군 | ✅ |
| CMA | CMA 총 평가액(KRW 환산) 조회 — 자산 요약용 | GET | `/internal/cma/krw-total` |  | C·강문군 | ✅ |
| CMA | 끝전 임계값·활성 적립 소스 조회 — 잔돈 스캔용 | GET | `/internal/cma/collection-settings` |  | C·강문군 | ✅ |
| 환전 | 매매기준율 조회 — 외화 잔돈 KRW 환산용 | GET | `/internal/exchange/usd-krw-rate` |  | B·김준형 | ✅ |
| 자산 | 연동 계좌(끝전 적립 대상) 잔액 요약 조회 | GET | `/internal/assets/accounts` |  | C·강문군 | ✅ |
| 자산 | 연동 USD 지갑 목록 조회 | GET | `/internal/assets/fx-wallets` |  | C·강문군 | ✅ |
| 자산 | 카드 라운드업 대상·금액 조회 | GET | `/internal/assets/card-roundup` |  | C·강문군 | ✅ |
| 자산 | 카드 라운드업 수집 완료 마킹 | PATCH | `/internal/assets/card-roundup/mark-collected` |  | C·강문군 | ✅ |
| 자산 | 사용 가능 포인트 조회 | GET | `/internal/assets/points` |  | C·강문군 | ✅ |
| 자산 | 계좌 끝전 차감 | PATCH | `/internal/assets/accounts/deduct` |  | C·강문군 | ✅ |
| 자산 | 포인트 차감 | PATCH | `/internal/assets/points/deduct` |  | C·강문군 | ✅ |
| 캘린더 | 종목 캘린더 이벤트(배당·실적) 일괄 upsert | POST | `/internal/calendar/stock-events` |  | D·김서현 | ✅ |
