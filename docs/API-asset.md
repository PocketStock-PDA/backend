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

### GET `/api/assets/bank-accounts`

보유(연동) 은행 계좌 목록 조회

계좌 1원 인증 시 인증할 계좌 선택, 출금/충전 재원 계좌 선택 등 여러 화면에서 공용으로 쓰는 조회 API다.
연동된 은행 계좌(`linked_bank_accounts`)만 반환하며, 계좌번호(`account_no_enc`, 암호화)는 노출하지 않는다.
식별은 `bankName` + `accountName`으로 한다. 정렬은 은행 표시순(`sort_order`) → 계좌 id 순.

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 401 Unauthorized
- **데이터 출처**: 목데이터(은행 계좌 연동 영역)

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "보유 은행 계좌 목록 조회 성공",
  "data": [
    {
      "accountId": 1,
      "bankCode": "SHINHAN_BANK",
      "bankName": "신한은행",
      "accountName": "신한 입출금통장",
      "accountType": "DEMAND",
      "balance": 1855600.0000,
      "currency": "KRW",
      "isDormant": false,
      "isVerified": false
    },
    {
      "accountId": 3,
      "bankCode": "KB_BANK",
      "bankName": "국민은행",
      "accountName": "국민 일반 입출금",
      "accountType": "DEMAND",
      "balance": 12300.0000,
      "currency": "KRW",
      "isDormant": true,
      "isVerified": false
    }
  ]
 }
```

> `accountType`: `DEMAND`(입출금) / `SAVINGS`(적금) / `DEPOSIT`(예금). 호출하는 화면이 필요에 따라 필터링한다(예: 1원 인증은 입출금·비휴면만).
> `isVerified`: 계좌 1원 인증(소유권 확인) 완료 여부.

---

### POST `/api/assets/bank-accounts/{accountId}/verification`

계좌 1원 인증 — 송금요청

대상 연동 은행 계좌로 **1원을 입금하는 시뮬레이션**이다(은행 계좌 연동은 목데이터 영역 → 실제 자금 이동 없음).
3자리 인증 코드를 생성해 Redis 챌린지로 저장(TTL 5분)하고, **웹푸시로 "1원 입금" 목 알림**을 보낸다.
입금자명에 코드가 실려 오며(은행 거래내역 시뮬), 사용자는 그 코드를 확인 API로 제출한다.
**코드는 응답에 포함되지 않는다**(푸시로만 전달). 재요청 시 기존 코드·시도수는 초기화된다.

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 401 Unauthorized / 404 Not Found(미소유 계좌) / 409 Conflict(이미 인증됨)
- **데이터 출처**: 목데이터 + 자체 시뮬(Redis 챌린지). CMA 원장과 무관.

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "인증 코드를 발송했습니다.",
  "data": {
    "accountId": 1,
    "senderName": "포켓스톡",
    "expiresInSeconds": 300,
    "maxAttempts": 5
  }
 }
```

> **⚠️ 푸시 수신 선행조건 (시연 시 필수).** 코드는 웹푸시로만 전달되며, 발송은 현재 **WEB(VAPID) 구독에 한해** 동작한다.
> 사용자가 `platform != WEB`이거나 미구독(`push_token` 없음)이면 발송은 조용히 no-op 된다(앱 알림 정책과 동일, 발송 실패는 요청을 막지 않음).
> 따라서 폰으로 목 푸시를 받으려면 프론트에서 **`POST /api/notifications/token`으로 WEB 구독을 먼저 등록**해야 한다.
> 로컬 개발에선 구독 없이도 알림함(`GET /api/notifications`) 본문으로 코드를 확인해 흐름을 검증할 수 있다(목 데이터 전용). 코드 자체는 로그에 남기지 않는다.
>
> **보안 주의(현재 목/시연 전제).** 인증 코드가 알림함 본문에 평문으로 남는다. 운영 전환 시 재검토 대상.

---

### POST `/api/assets/bank-accounts/{accountId}/verification/confirm`

계좌 1원 인증 — 확인

푸시로 받은 코드를 검증한다. 성공 시 `linked_bank_accounts.is_verified`를 마킹한다.

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request(코드 형식·불일치·만료) / 401 Unauthorized / 404 Not Found(미소유) / 409 Conflict(이미 인증됨) / 429 Too Many Requests(시도 초과)

**Request Body**

```json
{
  "code": "482"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "계좌 인증이 완료되었습니다.",
  "data": {
    "accountId": 1,
    "verified": true
  }
 }
```

**에러 코드**

| code | 상황 |
|---|---|
| `VERIFICATION_EXPIRED` | 인증 요청이 없거나 5분 만료 |
| `VERIFICATION_CODE_MISMATCH` | 코드 불일치(시도수 +1) |
| `VERIFICATION_ATTEMPTS_EXCEEDED` | 5회 초과 → 챌린지 폐기, 재요청 필요 |
| `ACCOUNT_ALREADY_VERIFIED` | 이미 인증된 계좌 |
| `NOT_FOUND` | 본인 소유가 아니거나 없는 계좌 |

---

## 가입단계 계좌 1원 인증 (공개)

회원가입 단계(로그인 전)의 계좌 1원 인증. 위 `/api/assets/bank-accounts/...`(로그인 후, 연동계좌 `is_verified` 마킹·푸시)와는 **별개 흐름**이다.

- **공개 API**(인증 토큰 불필요). 로그인 전이라 userId·연동계좌·DB에 묶이지 않는 **순수 휘발성 mock**이다.
- 코드 전달 채널(알림함·푸시)이 없어 **응답에 `depositorName`·`code`를 그대로 노출**한다(`depositorName`=데모 화면용, `code`=테스트 보조). 인증 코드는 입금자명 끝 **3자리**.
- 세션은 인메모리 저장, TTL **180초**, 성공 시 1회 소비. DB 변경 없음.
- ⚠️ 운영(실 펌뱅킹) 전환 시 응답에서 `code`·`depositorName` 제거 필요.

### POST `/api/auth/account-verify/request`

가입단계 1원 인증 — 송금요청(mock). 입금자명 `포켓스톡###`(랜덤 3자리)을 구성해 응답으로 내려준다.

- **HTTP Status Code**: 200 OK / 400 Bad Request(accountId 누락)
- **데이터 출처**: 순수 mock(인메모리). 실제 송금·DB 없음.

**Request Body**

```json
{
  "accountId": 1
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "1원을 입금했습니다. 입금자명을 확인해 주세요.",
  "data": {
    "verificationId": "b1f2c3d4-...",
    "depositorName": "포켓스톡482",
    "code": "482",
    "expiresIn": 180
  }
 }
```

---

### POST `/api/auth/account-verify/confirm`

가입단계 1원 인증 — 확인. 입금자명 끝 3자리를 대조한다. 만료·불일치는 `verified=false`로 응답(별도 에러코드 없음).

- **HTTP Status Code**: 200 OK / 400 Bad Request(형식 오류)

**Request Body**

```json
{
  "verificationId": "b1f2c3d4-...",
  "code": "482"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "계좌 인증 확인",
  "data": {
    "verified": true
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
  "data": {
  "syncedAt": "2026-06-22T14:30:00"
 }
 }
```

> 시드가 고정이라 잔액 재계산 변화는 없음 → 동작은 **no-op + `linked_institutions.last_synced_at` 갱신**(F-G). 연동 자산 화면에서 "새로고침" 버튼이 붙을 여지를 위해 명세 유지(de-scope 대상이던 GET /assets와 달리 보존).

---

### GET `/api/assets/scan`

잠자는 잔돈 스캔 (연동 직후 발견 화면)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body** — 잠자는 잔돈을 **소스별로 묶어** 표시(와이어프레임: SOL트래블 환전 잔돈 / 신한카드 잔돈 / 신한은행 끝전 + 총액).

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "잠자는 잔돈 스캔 성공",
  "data": {
  "totalAmount": 12450,
  "sources": [
  {"sourceType": "ACCOUNT", "name": "신한은행 끝전",      "amount": 7450},
  {"sourceType": "FX",      "name": "SOL트래블 환전 잔돈", "amount": 5000},
  {"sourceType": "CARD",    "name": "신한카드 잔돈",       "amount": 0}
  ]
 }
 }
```

> scan(연동 직후 발견 화면)과 CMA 홈 `collectSources`(상시 홈)는 **동일 계산을 단일 소스로 공유**한다. 필드 형태는 CMA `collectSources`(`sourceType`/`name`/`amount`)와 정렬. **정확한 소스 분류·끝전 계산 통일은 F-E**(`ASSET_DEVELOPMENT.md` §6, Phase 1 직전 확정)에서 확정 — 위 `sourceType`(ACCOUNT 끝전 / CARD 라운드업 잔돈 / FX 환전 잔돈)은 와이어프레임 기준 잠정안.

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
  "accountId": 12,
  "accountNo": "110-***-111222",
  "bankName": "신한은행",
  "balance": 50000
  }
 ]
 }
```

> `dormantSince`(휴면 시작일) 제거 — 와이어프레임에 시작일 표기 없음 → `linked_bank_accounts`에 휴면시작일 컬럼 추가 불필요. 해지 요청용 식별자로 `accountId` 노출(`accountNo`는 마스킹 표시용).

---

### POST `/api/assets/dormant/close`

휴면계좌 일괄 해지 → CMA 이체 (다중 선택)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body** — 휴면계좌 **다중 체크 선택**(`accountIds`). 목적지는 사용자 CMA로 **고정**(요청에 `targetAccount` 없음).

```json
{
  "accountIds": [12, 15]
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "휴면계좌 해지 및 CMA 이체 성공",
  "data": {
  "closedCount": 2,
  "transferredAmount": 130000
 }
 }
```

> 화면(와이어프레임 14·17번 "휴면계좌 정리하고 남은 돈 옮겨드릴게요 — 포켓스톡 CMA로 모을 수 있어요") = 다중 체크 → 일괄 해지 + CMA 이체. 해지 잔액은 CMA에 `txType=DORMANT` 입금(core→ledger Feign, 멱등키 `DORMANT:{accountId}`) — **F-D**(`DECISIONS.md` F-D / `ASSET_DEVELOPMENT.md` §6) 참조. 부분 성공 가능성(일부 계좌 실패) 대비 멱등 처리.

---

## 소비패턴분석

### ✅ GET `/api/assets/spending`

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
