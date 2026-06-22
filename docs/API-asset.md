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
  "category": "BANK",
  "companyCode": "SHINHAN_BANK",
  "companyName": "신한은행",
  "logoUrl": null,
  "linkStatus": "LINKED"
  },
  {
  "category": "BANK",
  "companyCode": "WOORI_BANK",
  "companyName": "우리은행",
  "logoUrl": null,
  "linkStatus": "AVAILABLE"
  }
 ]
 }
```

> 외부 식별자 = `companyCode`(숫자 아님). `linkStatus`: 해당 유저가 이미 연동했으면 `LINKED`, 선택 가능하면 `AVAILABLE`. 활성 카탈로그(`is_active=true`)를 `sort_order` 순으로 반환.

---

## 연동 실행(links) 공통 규칙

> 아래 `links/auth` · `links` · `links/{bank|card|point|fx|securities}` · `refresh`에 공통 적용된다(2026-06-22 확정).
>
> - **데이터 출처 = 전부 목데이터.** 마이데이터/은행/카드 외부 API를 실제로 호출하지 않는다. 연동 시 생성되는 자산(잔액·거래·포인트·외화)은 **고정 시나리오 시드/템플릿을 해당 사용자로 복제 적재**한다(임의 값 생성 아님).
> - **기관 식별자 = `companyCode`**(`GET /institutions` 응답과 동일, 예: `SHINHAN_BANK`·`SHINHAN_CARD`). 아래 예시에 남아 있는 `"001"/"088"`·기관 한글명은 플레이스홀더이며, **실제 요청 본문은 `companyCode`를 쓴다.**
> - **통합인증 토큰(`authToken`)은 무상태(stateless) mock**: `/links/auth`는 토큰 문자열만 발급하고 **어디에도 저장하지 않으며**, `POST /links`는 이 토큰을 **검증하지 않는다**(Redis 미사용). "인증 → 연동" 순서는 화면 흐름으로만 유지한다.
> - **멱등**: 이미 연동된(`LINKED`) 기관을 다시 연동하면 중복 생성 없이 skip하고 기존 상태를 반환한다.
> - **계좌번호 비요구·비노출**: 마이데이터 흐름상 사용자가 계좌번호를 입력하지 않는다(인증 후 템플릿 적재). 응답에도 `account_no_enc`(암호화 컬럼)를 노출하지 않는다.
> - **일괄 vs 개별**: `POST /links`는 온보딩 때 선택 기관을 한 번에 연동하고, `POST /links/{type}`는 연동 후 계좌·카드 등을 한 건씩 추가한다.

---

### POST `/api/assets/links/auth`

마이데이터 통합인증

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body** — 인증할 기관(`companyCode` 목록).

```json
{
  "institutions": ["SHINHAN_BANK", "SHINHAN_CARD", "SHINHAN_POINT"]
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

> `authToken`은 무상태 mock(공통 규칙 참조) — 형식만 갖춘 토큰을 발급하며 검증·저장하지 않는다.

---

### POST `/api/assets/links`

최초 자산 연동 (선택 기관 일괄)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body** — 통합인증 토큰 + 연동할 기관(`companyCode` 목록).

```json
{
  "authToken": "MYD-AUTH-TOKEN",
  "institutions": ["SHINHAN_BANK", "SHINHAN_CARD"]
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

> 선택 기관을 한 번에 연동(온보딩). 이미 연동된 기관은 멱등 skip하며 `linkedCount`는 **이번에 새로 연동된 수**만 센다. 각 기관의 자산은 고정 시나리오 템플릿으로 적재(공통 규칙 참조).

---

### POST `/api/assets/links/bank`

은행 계좌 연동 (개별 — 연동 후 한 건씩 추가)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body** — 연동할 은행(`companyCode`). 계좌번호는 입력하지 않는다(마이데이터 흐름, 템플릿 적재 — 공통 규칙 참조).

```json
{
  "companyCode": "KB_BANK"
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

카드 연동 (개별)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body** — 연동할 카드사(`companyCode`).

```json
{
  "companyCode": "SHINHAN_CARD"
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

포인트 연동 (개별)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body** — 연동할 포인트사(`companyCode`).

```json
{
  "companyCode": "OKCASHBAG_POINT"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "포인트 연동 성공",
  "data": {
  "companyCode": "OKCASHBAG_POINT",
  "balance": 25000
 }
 }
```

---

### POST `/api/assets/links/fx`

SOL트래블 외화잔액 연동 (개별)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body** — SOL트래블 외화지갑 고정이라 본문 없음(빈 객체).

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

타 증권사 연동 (개별)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body** — 연동할 증권사(`companyCode`). 계좌번호는 입력하지 않는다(템플릿 적재 — 공통 규칙 참조).

```json
{
  "companyCode": "KIWOOM_SEC"
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

> 시드가 고정이라 **잔액 재계산 없는 no-op** — 유저의 연동 기관(`linked_institutions`) `last_synced_at`만 현재 시각으로 갱신하고, 그 값(DB가 찍은 시각)을 그대로 `syncedAt`으로 반환한다. 연동된 기관이 없으면 갱신 대상이 없다. 연동 자산 화면의 "새로고침" 버튼을 위해 명세를 유지한다.

---

### GET `/api/assets/scan`

잠자는 잔돈 스캔 (연동 직후 발견 화면)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body** — 잠자는 잔돈을 **소스별로 묶어** 표시. 4개 소스(끝전 / 라운드업 / 포인트 / 외화 환전 잔돈) + 총액. 모두 KRW.

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "잠자는 잔돈 스캔 성공",
  "data": {
  "totalAmount": 37201,
  "sources": [
  {"sourceType": "ACCOUNT", "name": "신한은행 끝전",        "amount": 2300},
  {"sourceType": "CARD",    "name": "신한카드 잔돈",         "amount": 700},
  {"sourceType": "POINT",   "name": "마이신한포인트 잔돈",   "amount": 28000},
  {"sourceType": "FX",      "name": "SOL트래블 환전 잔돈",   "amount": 6201}
  ]
 }
 }
```

- `sourceType` 의미: `ACCOUNT`=연동 계좌 끝전(`balance % threshold`) / `CARD`=카드 라운드업 잔돈 / `POINT`=포인트 잔액 / `FX`=외화 지갑(`currency='USD'`) 잔액의 KRW 환산.
- `amount`는 모두 KRW 정수. FX는 USD 잔액 × **매매기준율**(CMA 홈 `totalKrwEquivalent`과 동일 환산), HALF_UP. USD 미보유면 환율 미조회·`amount=0`.
- 비활성/미설정 소스도 `amount=0`으로 항상 4개를 반환한다(화면 고정 레이아웃).

> **F-E=B 확정**: ACCOUNT·CARD·POINT는 CMA 홈 `collectSources`(상시 홈)와 **동일 계산을 단일 소스로 공유**한다 — scan(core)은 ledger `collection_settings`(끝전 임계값·활성 소스)만 내부 Feign read하고, 잔액 원천은 core 자체 DB로 로컬 계산한다(라운드업은 `InternalAssetService.getCardRoundup` 재사용). ledger 전체 수집 계산(`getHome`)을 다시 호출하지 않는다(core→ledger→core 순환 방지).
>
> **FX 입금(수집 실행)은 별도 후속(EXC-006)**: scan은 발견(표시) 전용이다. 실제 CMA 입금 시 통화 분기 — **달러 잔액→CMA USD 지갑 / 원화 잔액→CMA KRW 지갑 / 그 외 통화→달러로 환전 후 CMA USD 지갑** 입금 — 은 외화 잔돈 수집 실행(EXC-006)에서 구현한다. (그 외 통화 환전은 USD/KRW 외 환율 소스가 갖춰진 뒤.)

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
  "accountId": 4,
  "bankName": "국민은행",
  "accountName": "KB 정기예금",
  "balance": 2000000.0000,
  "currency": "KRW"
  }
 ]
 }
```

> `dormantSince`(휴면 시작일) 제거 — 와이어프레임에 시작일 표기 없음 → `linked_bank_accounts`에 휴면시작일 컬럼 추가 불필요. 해지 요청용 식별자로 `accountId` 노출. **계좌번호(`account_no_enc`)는 AES 암호화 컬럼(시드 NULL)이라 노출하지 않고**, 기존 `bank-accounts` 조회 관례대로 은행명 + 상품명(`accountName`)으로 표시. 정렬: 잔액 큰 순. 소프트 해지된 계좌(`closed_at IS NOT NULL`)는 휴면 목록에서 제외한다.

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
    "transferredAmount": 130000,
    "allCompleted": true,
    "results": [
      {
        "accountId": 12,
        "amount": 30000,
        "currency": "KRW",
        "status": "COMPLETED"
      },
      {
        "accountId": 15,
        "amount": 100000,
        "currency": "KRW",
        "status": "COMPLETED"
      }
    ]
  }
 }
```

> 화면(와이어프레임 14·17·20번) = 다중 체크 → 일괄 소프트 해지 + CMA 이체. 해지 잔액은 CMA에 `txType=DORMANT` 입금(core→ledger Feign, ledger가 멱등키 `DORMANT:{accountId}` 파생) — **F-D**(`DECISIONS.md` F-D / `ASSET_DEVELOPMENT.md` §6) 참조.
>
> `accountIds`는 비어 있거나 중복될 수 없고, 각 ID는 요청 사용자 소유의 미해지 휴면계좌 또는 과거 이 요청으로 이미 소프트 해지된 계좌여야 한다. 타인·미존재·비휴면 계좌는 원장 호출 전 400으로 거절한다. 원장 입금 뒤 DB A 계좌는 `balance=0`, `closed_at`, `closed_amount`로 소프트 해지하고, 현재 계좌/휴면 조회에서는 제외한다.
>
> `results[].status`는 `COMPLETED`(이번 호출 완료), `ALREADY_CLOSED`(과거 호출로 이미 완료되어 원장 재기록 없음), `FAILED`(정상 검증 후 실행 중 실패) 중 하나다. `closedCount`·`transferredAmount`는 이번 호출에서 새로 완료된 건만 합산한다. `allCompleted=false`인 경우에도 각 계좌 상태를 사용해 UI가 결과를 표시한다.

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
