
# PocketStock API 명세

> 협업용 단일 문서. 각자 **자기 담당 행만** 수정하고 feature 브랜치 → `dev` PR. `.md`라 git이 줄단위 병합합니다.

## 👥 담당자

| 담당 | 영역 | API 수 |
|---|---|---|
| **A·우정인** | 회원·인증 · 증권계좌 개설 · 알림 · 퍼즐/보상 | 30 |
| **B·김준형** | 소수점 매매엔진 · 정기적립 · 시세/실시간시세 | 27 |
| **C·강문군** | 자산연동 · CMA · 환전 · 매수/매도 탭 | 33 |
| **D·김서현** | 소비분석 · 종목추천 · 가계부 · 캘린더 · 리밸런싱 | 24 |
| | **합계** | **114** |

## 📌 범례

- **TR코드 = 시세 데이터 출처(실전 실연동)**: 국내 `[LS 실전]`, 해외 `[KIS 실전]`.
- **계좌·예수금·주문·체결·잔고·소수점 원장은 전부 포켓스톡 자체 시뮬**(`[자체 시뮬]`) — 브로커 주문/계좌 API 미사용. 표기된 주문 TR은 설계 참조용.

## User

| 대분류 | Description | Method | URI | LS TR코드 | 담당 | 완료 |
|---|---|---|---|---|---|---|
| 회원·인증 | 아이디 중복 확인 | GET | `/api/users/check-username` |  | A·우정인 | ✅ |
| 회원·인증 | 회원가입(이름·주민번호 앞6+뒷1·아이디·비번·휴대폰) | POST | `/api/users/signup` |  | A·우정인 | ✅ |
| 회원·인증 | 비밀번호 보안규칙 실시간 검증 | POST | `/api/users/validate-password` |  | A·우정인 | ✅ |
| 회원·인증 | SMS 인증번호 발송 | POST | `/api/auth/sms/send` |  | A·우정인 |  |
| 회원·인증 | SMS 인증번호 확인 | POST | `/api/auth/sms/verify` |  | A·우정인 |  |
| 회원·인증 | 신한인증서 난수문자 인증요청 | POST | `/api/auth/shinhan-cert/request` |  | A·우정인 |  |
| 회원·인증 | 신한인증서 인증확인 | POST | `/api/auth/shinhan-cert/verify` |  | A·우정인 |  |
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
| 자산연동 | 연동 자산 전체 조회 | GET | `/api/assets` |  | C·강문군 |  |
| 자산연동 | 연동 자산 새로고침(최신화) | POST | `/api/assets/refresh` |  | C·강문군 |  |
| 자산연동 | 잠자는 잔돈 스캔 | GET | `/api/assets/scan` |  | C·강문군 |  |
| 자산연동 | 휴면계좌 조회 | GET | `/api/assets/dormant` |  | C·강문군 |  |
| 자산연동 | 휴면계좌 해지·잔액 이체 | POST | `/api/assets/dormant/close` |  | C·강문군 |  |
| 소비패턴분석 | 소비패턴 분석 결과 조회 | GET | `/api/assets/spending` |  | D·김서현 |  |
| 소비패턴분석 | 소비분석 리포트 | GET | `/api/assets/spending/report` |  | D·김서현 |  |
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
| 잔돈수집 | 적립 소스 설정(카드/계좌별 ON/OFF) | PUT | `/api/cma/collect/settings` |  | C·강문군 | ✅ |
| 잔돈수집 | 적립 이력 조회 | GET | `/api/cma/collect/history` |  | C·강문군 | ✅ |
| CMA | CMA 잔액·성과율(원화RP/외화RP) 조회 | GET | `/api/cma/balance` |  | C·강문군 | ✅ |
| CMA | CMA 계좌내역(입금·출금·이자) 조회 | GET | `/api/cma/transactions` |  | C·강문군 | ✅ |
| 자금이체 | 자금 이동 이력 조회 | GET | `/api/cma/transfers` |  | C·강문군 | ✅ |
| 자동충전 | 부족금액 자동충전 설정 조회 | GET | `/api/cma/auto-charge-settings` |  | C·강문군 |  |
| 자동충전 | 부족금액 자동충전 설정(ON/OFF·1회한도·대상계좌) | PUT | `/api/cma/auto-charge-settings` |  | C·강문군 |  |

## Trading

| 대분류 | Description | Method | URI | LS TR코드 | 담당 | 완료 |
|---|---|---|---|---|---|---|
| 증권계좌 | 종합계좌 개설(국내·해외 위탁) — CMA는 `/api/cma/account` 별도 | POST | `/api/trading/accounts` |  | B·김준형 | ✅ |
| 증권계좌 | 계좌 상태 조회 | GET | `/api/trading/accounts` |  | B·김준형 | ✅ |
| 증권계좌 | 예수금/출금가능금액 조회 | GET | `/api/trading/deposit` |  | B·김준형 | ✅ |
| 시세 | 종목 카테고리 탐색(40대 여성 상위 등) | GET | `/api/trading/stocks/categories` |  | B·김준형 |  |
| 시세 | 종목 검색(자체 종목마스터) | GET | `/api/trading/stocks/search` |  | B·김준형 | ✅ |
| 시세 | 종목 상세(마스터+현재가 합성) | GET | `/api/trading/stocks/{stockCode}` |  | B·김준형 | ✅ |
| 시세 | [국내] 현재가 조회 | GET | `/api/trading/stocks/{stockCode}/price?market=domestic` | t1102 [LS 실전] | B·김준형 | ✅ |
| 시세 | [해외] 현재가 조회 | GET | `/api/trading/stocks/{stockCode}/price?market=overseas` | HHDFS76200200 (해외 현재가상세) [KIS 실전] | B·김준형 | ✅ |
| 시세 | [국내] 호가 조회(온주 전용) | GET | `/api/trading/stocks/{stockCode}/orderbook?market=domestic` | t8450 (현재가호가) [LS 실전] | B·김준형 | ✅ |
| 시세 | [해외] 현재가·호가 조회(온주 전용) | GET | `/api/trading/stocks/{stockCode}/orderbook?market=overseas` | HHDFS76200100 (해외 현재가호가) [KIS 실전] | B·김준형 | ✅ |
| 시세 | [국내] 종목 기업정보 | GET | `/api/trading/stocks/{stockCode}/info?market=domestic` | t3320 (FNG요약) [LS 실전] | B·김준형 |  |
| 시세 | [해외] 종목 기업정보 | GET | `/api/trading/stocks/{stockCode}/info?market=overseas` | HHDFS76200200 (해외 현재가상세) [KIS 실전] | B·김준형 |  |
| 실시간시세 | [국내] 실시간 체결가(소수점·온주) | WS | `/topic/stock/trade/{stockCode}` | US3 (통합 체결) [LS 실전] | B·김준형 | ✅ |
| 실시간시세 | [국내] 실시간 호가(온주) | WS | `/topic/asking/{stockCode}` | UH1 (통합 호가잔량) [LS 실전] | B·김준형 | ✅ |
| 실시간시세 | [해외] 실시간 체결가 | WS | `/topic/foreign/transaction/{symbol}` | HDFSCNT0 (해외 실시간지연체결가) [KIS 실전] | B·김준형 | ✅ |
| 실시간시세 | [해외] 실시간 호가(온주) | WS | `/topic/foreign/quote/{symbol}` | HDFSASP0 (해외 실시간호가) [KIS 실전] | B·김준형 | ✅ |
| 실시간시세 | 실시간 체결통보(주문 결과) | WS | `/topic/order-notification` | SC1(국내)·AS1(해외) [자체 시뮬] | B·김준형 |  |
| 소수점투자 | 소수점 매수(금액/수량) → LS 합산 온주주문 | POST | `/api/trading/orders/buy` | CSPAT00601(국내)·COSAT00301(해외) [자체 시뮬] | B·김준형 |  |
| 소수점투자 | 소수점 매도(금액/전량) → LS 합산 온주주문 | POST | `/api/trading/orders/sell` | CSPAT00601(국내)·COSMT00300(해외) [자체 시뮬] | B·김준형 |  |
| 소수점투자 | 온주 매수/매도(호가 기반) | POST | `/api/trading/orders/whole` | CSPAT00601(국내)·COSAT00301(해외) [자체 시뮬] | B·김준형 | ✅ |
| 소수점투자 | 주문 취소(배치 전송 전) | DELETE | `/api/trading/orders/{orderId}` | CSPAT00801(국내)·COSAT00311(해외) [자체 시뮬] | B·김준형 |  |
| 소수점투자 | 거래내역 조회(매수·매도·달성) | GET | `/api/trading/orders` |  | B·김준형 | ✅ |
| 소수점투자 | 미체결 주문 조회 | GET | `/api/trading/orders/pending` |  | B·김준형 |  |
| 소수점투자 | 보유종목·잔고(평가·수익률) 조회 | GET | `/api/trading/holdings` |  | B·김준형 | ✅ |
| 소수점투자 | 온주 전환내역 조회 | GET | `/api/trading/whole-shares` |  | B·김준형 |  |
| 정기적립식 | 자동모으기 설정 등록(주기/조건) | POST | `/api/trading/auto-invest` |  | B·김준형 |  |
| 정기적립식 | 자동모으기 설정 수정 | PUT | `/api/trading/auto-invest/{id}` |  | B·김준형 |  |
| 정기적립식 | 자동모으기 일시중지/재개/해제 | PATCH | `/api/trading/auto-invest/{id}/status` |  | B·김준형 |  |
| 정기적립식 | 자동모으기 종목 추가 | POST | `/api/trading/auto-invest/stocks` |  | B·김준형 |  |
| 정기적립식 | 자동모으기 종합 설정 조회 | GET | `/api/trading/auto-invest` |  | B·김준형 |  |
| 퍼즐 | 퍼즐 진행률 조회(조각/완성) | GET | `/api/trading/puzzle/{stockCode}` |  | B·김준형 |  |
| 보상 | 가입보상 종목 선택·지급 | POST | `/api/trading/rewards/signup` |  | B·김준형 |  |
| 보상 | 보상 지급 내역 조회 | GET | `/api/trading/rewards` |  | B·김준형 |  |

> 참고: 해외 `현재가 조회`·`종목 기업정보`는 같은 KIS TR(HHDFS76200200) 응답을 시세/지표로 나눠 쓴 것. 한 화면에서 둘 다 호출 시 KIS 응답을 짧게 캐시해 중복 호출 줄일 것.

## Portfolio

| 대분류 | Description | Method | URI | LS TR코드 | 담당 | 완료 |
|---|---|---|---|---|---|---|
| 종목추천 | 추천 포트폴리오 조회(또래2+우량주2) | GET | `/api/portfolio/recommendations` |  | D·김서현 |  |
| 종목추천 | 추천 종목 새로고침 | POST | `/api/portfolio/recommendations/refresh` |  | D·김서현 |  |
| 종목추천 | 보유 포트폴리오 현황(비중·수익률) | GET | `/api/portfolio/holdings` |  | D·김서현 |  |
| 자산리밸런싱 | 종합 자산 분석(자산구성) | GET | `/api/portfolio/rebalancing/analysis` |  | D·김서현 |  |
| 자산리밸런싱 | 순자산(자산-부채) 조회 | GET | `/api/portfolio/rebalancing/networth` |  | D·김서현 |  |
| 자산리밸런싱 | 또래(연령·성별) 비중 비교 | GET | `/api/portfolio/rebalancing/peer` |  | D·김서현 |  |
| 자산리밸런싱 | 원클릭 리밸런싱 실행 | POST | `/api/portfolio/rebalancing/execute` |  | D·김서현 |  |
| 자산리밸런싱 | 예/적금 갈아타기 추천 | GET | `/api/portfolio/rebalancing/products` |  | D·김서현 |  |
| 자산리밸런싱 | ISA 계좌 가입 안내 | GET | `/api/portfolio/rebalancing/isa` |  | D·김서현 |  |
| 증권캘린더 | 증권 캘린더(월별 일정) 조회 | GET | `/api/portfolio/calendar` |  | D·김서현 |  |
| 증권캘린더 | 종목 주요일정(배당·실적) 조회 | GET | `/api/portfolio/calendar/events` |  | D·김서현 |  |
| 증권캘린더 | 캘린더 추천 종목 | GET | `/api/portfolio/calendar/recommendations` |  | D·김서현 |  |
| 카드추천 | 소비 기반 맞춤 카드 추천 | GET | `/api/portfolio/cards/recommendations` |  | D·김서현 |  |

## Budget

| 대분류 | Description | Method | URI | LS TR코드 | 담당 | 완료 |
|---|---|---|---|---|---|---|
| 가계부 | 소비분석 기반 목표 자동설정 | POST | `/api/budget/goals/auto` |  | D·김서현 |  |
| 가계부 | 가계부 목표 설정(생활비/카테고리) | POST | `/api/budget/goals` |  | D·김서현 |  |
| 가계부 | 가계부 목표 조회 | GET | `/api/budget/goals` |  | D·김서현 |  |
| 가계부 | 일별/월별 소비내역 조회 | GET | `/api/budget/transactions` |  | D·김서현 |  |
| 가계부 | 파도 캘린더(일별 예산) 조회 | GET | `/api/budget/calendar` |  | D·김서현 |  |
| 가계부 | 카테고리별 목표대비 절약 현황 | GET | `/api/budget/savings/by-category` |  | D·김서현 |  |
| 가계부 | 소비 섹터별 전월 비교 | GET | `/api/budget/comparison` |  | D·김서현 |  |
| 가계부 | 절약금 현황 조회 | GET | `/api/budget/savings` |  | D·김서현 |  |
| 가계부 | 절약금 모으기 동의 | POST | `/api/budget/savings/agree` |  | D·김서현 |  |

## Exchange

| 대분류 | Description | Method | URI | LS TR코드                 | 담당 | 완료 |
|---|---|---|---|-------------------------|---|---|
| 환전 | 환율 조회(USD/KRW, 예상 환전금액) | GET | `/api/exchange/rate` | CUR 기반(시세 캐시) [LS 실전]   | B·김준형 | ✅ |
| 환전 | 실시간 환율(USD/KRW) | WS | `/topic/currency/usd-krw` | CUR (현물USD 실시간) [LS 실전] | B·김준형 | ✅ |
| 환전 | 환전 가능여부·가능금액 검증 | GET | `/api/exchange/validate` |                         | B·김준형 |  |
| 환전 | 원화→달러 환전 | POST | `/api/exchange/krw-to-usd` |                         | B·김준형 | ✅ |
| 환전 | 달러→원화 환전 | POST | `/api/exchange/usd-to-krw` |                         | B·김준형 | ✅ |
| 환전 | 자동환전 설정(달러우선·한도·잔돈) | PUT | `/api/exchange/auto-settings` |                         | B·김준형 | ✅ |
| 환전 | 환전 이력 조회 | GET | `/api/exchange/history` |                         | B·김준형 | ✅ |

## Notification

| 대분류 | Description | Method | URI | LS TR코드 | 담당 | 완료 |
|---|---|---|---|---|---|---|
| 알림 | 알림 목록(알림센터) 조회 | GET | `/api/notifications` |  | A·우정인 |  |
| 알림 | 알림 읽음 처리 | PATCH | `/api/notifications/{id}/read` |  | A·우정인 |  |
| 알림 | 알림 전체 읽음 | PATCH | `/api/notifications/read-all` |  | A·우정인 |  |
| 알림 | 푸시 토큰 등록 | POST | `/api/notifications/token` |  | A·우정인 |  |
| 알림 | 알림 수신 설정 | PUT | `/api/notifications/settings` |  | A·우정인 |  |
