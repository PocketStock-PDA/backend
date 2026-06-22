# PocketStock API 명세 — Exchange

> 공통 헤더: `Authorization: Bearer {accessToken}` | `Content-Type: application/json`

## 환전

### GET `/api/exchange/rate` ✅ 구현완료

환율 조회 (USD/KRW, 매수·매도 적용환율) | LS TR: CUR

> **구현 메모**: LS CUR 실시간 틱을 Redis 캐시(SSOT)에서 읽어 반환. `baseRate`=매매기준율, `buyRate`/`sellRate`=스프레드·우대 내재 **적용환율**(= 기준율 × (1 ± s×(1−p)), s=전신환 0.96%·p=우대 90%). 양방향 UI라 추정금액은 클라가 환산(`KRW→USD = krw÷buyRate`, `USD→KRW = usd×sellRate`). 별도 수수료 없음(비용 환율 내재, backend#54). 콜드스타트(첫 틱 미수신) 시 **502**(EXTERNAL_API_ERROR). `updatedAt`으로 staleness 판단.

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 401 Unauthorized / 502 Bad Gateway

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "환율 조회 성공",
  "data": {
  "baseCurrency": "USD",
  "targetCurrency": "KRW",
  "baseRate": 1535.40,
  "buyRate": 1536.87,
  "sellRate": 1533.93,
  "preferentialRate": 0.90,
  "change": -2.30,
  "updatedAt": "2026-06-18T23:38:06.123"
 }
 }
```

---

### WS `/topic/currency/usd-krw`

실시간 환율 (USD/KRW)<br> LS TR: CUR (현물USD 실시간)

- **HTTP Status Code**: 101 Switching Protocols

**Response Body**

```json
{
  "baseCurrency": "USD",
  "targetCurrency": "KRW",
  "exchangeRate": 1382.50,
  "change": -2.30,
  "updatedAt": "2025-06-15T10:30:00.123"
 }
```

---

### GET `/api/exchange/validate`

환전 가능여부·가능금액 검증<br> Query: amount (number, 필수) - 환전할 KRW 금액

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "환전 가능여부 검증 성공",
  "data": {
  "available": true,
  "requestedAmount": 100000,
  "maxAmount": 5000000,
  "estimatedUsd": 72.33
 }
 }
```

---

### POST `/api/exchange/krw-to-usd`

원화 → 달러 환전

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized / 409 Conflict
- **거래 인증**: 본문 비밀번호 대신 사전 거래 세션(txn-auth)으로 처리. 호출 전 `POST /api/users/account-password/verify`로 인증해야 하며, 미인증 시 401 `TXN_AUTH_REQUIRED`.
- **멱등(#96)**: `idempotencyKey` **필수**(클라 발급, 빈 값 400). 따닥 탭·재전송으로 같은 키 재요청 시 신규 체결 없이 **기존 결과를 반환**(잔액은 현재 풀 조회값). 다른 유저가 쓴 키면 409 `IDEMPOTENCY_CONFLICT`.

**Request Body**

```json
{
  "krwAmount": 100000,
  "idempotencyKey": "a1b2c3d4-..."
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "원화 → 달러 환전 성공",
  "data": {
  "exchangedUsd": 72.33,
  "appliedRate": 1382.50,
  "fee": 0,
  "trigger_type": "MANUAL",
  "remainKrw": 4900000
 }
 }
```

---

### POST `/api/exchange/usd-to-krw`

달러 → 원화 환전

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized / 409 Conflict
- **거래 인증**: 본문 비밀번호 대신 사전 거래 세션(txn-auth)으로 처리. 호출 전 `POST /api/users/account-password/verify`로 인증해야 하며, 미인증 시 401 `TXN_AUTH_REQUIRED`.
- **멱등(#96)**: `idempotencyKey` **필수**(클라 발급, 빈 값 400). 따닥 탭·재전송으로 같은 키 재요청 시 신규 체결 없이 **기존 결과를 반환**(잔액은 현재 풀 조회값). 다른 유저가 쓴 키면 409 `IDEMPOTENCY_CONFLICT`.

**Request Body**

```json
{
  "usdAmount": 50.00,
  "idempotencyKey": "a1b2c3d4-..."
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "달러 → 원화 환전 성공",
  "data": {
  "exchangedKrw": 69125,
  "appliedRate": 1382.50,
  "fee": 0,
  "trigger_type": "MANUAL",
  "remainUsd": 200.00
 }
 }
```

---

### GET `/api/exchange/auto-settings` ✅ 구현완료

자동환전 설정 조회 (1인 1행, 미설정 시 기본값)

> **구현 메모**: 미설정 사용자는 기본값(`autoEnabled=false, useDollarFirst=true`) 반환.

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 401 Unauthorized

**Response Body**: 아래 PUT 응답과 동일 구조

---

### PUT `/api/exchange/auto-settings` ✅ 구현완료

자동환전 설정 (달러우선·1회한도·외화잔돈)

> **구현 메모**: `fx_auto_settings` 1인 1행 upsert. null 필드는 기본값(autoEnabled=false, useDollarFirst=true)으로 정규화. `maxAmountPerTx`=1회 환전 한도(원, null=무제한), `residualHandling`=외화잔돈 처리(TO_KRW/KEEP_USD).

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "autoEnabled": true,
  "useDollarFirst": true,
  "maxAmountPerTx": 100000,
  "residualHandling": "TO_KRW"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "자동환전 설정 완료",
  "data": {
  "autoEnabled": true,
  "useDollarFirst": true,
  "maxAmountPerTx": 100000,
  "residualHandling": "TO_KRW"
 }
 }
```

---

### GET `/api/exchange/history` ✅ 구현완료

환전 이력 조회<br> Query: page (number, 선택, 기본 0), size (number, 선택, 기본 20·최대 100)

> **구현 메모**: `fx_transactions` 최신순 페이징. KRW/USD 금액은 방향과 무관하게 통화 기준으로 정규화(`krwAmount`/`usdAmount`). `type`=`{from}_TO_{to}`, `rate`=적용환율, `triggerType`=MANUAL/AUTO/RESIDUAL.

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "환전 이력 조회 성공",
  "data": {
  "history": [
  {
  "type": "KRW_TO_USD",
  "krwAmount": 100000,
  "usdAmount": 65.07,
  "triggerType": "MANUAL",
  "rate": 1536.87,
  "status": "DONE",
  "exchangedAt": "2026-06-18T10:30:00"
  }
  ],
  "page": 0,
  "totalElements": 15
 }
 }
```
