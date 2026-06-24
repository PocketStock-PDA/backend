# PocketStock API 명세 — User

> 공통 헤더: `Authorization: Bearer {accessToken}` | `Content-Type: application/json`

## 회원·인증

### GET `/api/users/check-username`

아이디 중복 확인<br> Query: username (string, 필수) - 중복 확인할 아이디

- **Request Headers**: 없음 (인증 불필요)
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "아이디 중복 확인 성공",
  "data": {
  "available": true
 }
 }
```

---

### POST `/api/users/signup`

회원가입

- **Request Headers**: 없음 (인증 불필요)
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "username": "user1234",
  "password": "P@ssword1!",
  "name": "홍길동",
  "residentFront": "950101",
  "residentBack": "1",
  "phone": "01012345678"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "회원가입 성공",
  "data": {
  "userId": 1,
  "username": "user1234"
 }
 }
```

---

### POST `/api/users/validate-password`

비밀번호 보안규칙 실시간 검증

- **Request Headers**: 없음 (인증 불필요)
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "password": "P@ssword1!"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "비밀번호 검증 성공",
  "data": {
  "valid": true,
  "failedRules": []
 }
 }
```

---

### POST `/api/auth/sms/send` ✅ 구현완료

SMS 인증번호 발송<br>
**mock** — 실제 SMS는 발송하지 않고, 발급한 인증번호를 응답으로 내려준다(프론트가 "문자 도착"을 연출).
운영(실제 발송) 전환 시 응답의 `code` 필드는 제거한다.

- **Request Headers**: 없음 (인증 불필요)
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "phone": "01012345678"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "SMS 인증번호 발송 성공",
  "data": {
  "code": "123456",
  "expiresIn": 180
 }
 }
```

---

### POST `/api/auth/sms/verify` ✅ 구현완료

SMS 인증번호 확인

- **Request Headers**: 없음 (인증 불필요)
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "phone": "01012345678",
  "code": "123456"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "SMS 인증 확인 성공",
  "data": {
  "verified": true
 }
 }
```

---

### POST `/api/auth/shinhan-cert/request` ✅ 구현완료

휴대폰 본인확인 난수문자 요청<br>
서버가 난수문자(`randomCode`)를 발급한다. 프론트는 이를 "문자 보내기" 화면 형태로 표시하고,
사용자가 "보내기" 클릭 시 그 내용을 verify로 제출해 대조한다. **실제 SMS는 발송하지 않는 mock(echo 대조)** 이다.

- **Request Headers**: 없음 (인증 불필요)
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "username": "user1234"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "난수문자 인증요청 성공",
  "data": {
  "requestId": "REQ-20250615-001",
  "randomCode": "A1B2C3",
  "expiresIn": 180
 }
 }
```

---

### POST `/api/auth/shinhan-cert/verify` ✅ 구현완료

난수문자 대조 확인<br>
request에서 발급한 난수문자(`randomCode`)를 echo로 제출받아, 세션에 저장된 발급값과 일치하는지 대조한다.
세션이 없거나 만료(TTL)됐거나 코드가 불일치하면 실패한다.

- **Request Headers**: 없음 (인증 불필요)
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "requestId": "REQ-20250615-001",
  "randomCode": "A1B2C3"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "난수문자 대조 확인 성공",
  "data": {
  "verified": true
 }
 }
```

---

### POST `/api/auth/login`

ID/PW 로그인 (JWT 발급)

- **Request Headers**: 없음 (인증 불필요)
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "username": "user1234",
  "password": "P@ssword1!"
 }
```

**Response Headers**

```
Set-Cookie: refreshToken={token}; Max-Age=1209600; Path=/api/auth; HttpOnly; Secure; SameSite=Lax
```

> refreshToken은 바디로 내려가지 않는다. HttpOnly 쿠키로만 전달되어 JS에서 접근할 수 없다(XSS 탈취 방지).

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "로그인 성공",
  "data": {
  "accessToken": "eyJhbGci...",
  "expiresIn": 1800
 }
 }
```

---

### POST `/api/auth/login/pin`

PIN/패턴 간편 로그인

- **Request Headers**: 없음 (인증 불필요)
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "type": "PIN",
  "value": "123456"
 }
```

**Response Headers**

```
Set-Cookie: refreshToken={token}; Max-Age=1209600; Path=/api/auth; HttpOnly; Secure; SameSite=Lax
```

> refreshToken은 바디로 내려가지 않는다. HttpOnly 쿠키로만 전달된다(로그인과 동일).

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "간편 로그인 성공",
  "data": {
  "accessToken": "eyJhbGci...",
  "expiresIn": 1800
 }
 }
```

---

### POST `/api/auth/refresh`

토큰 재발급 (Refresh)

- **Request Headers**: 없음 (인증 불필요)
- **HTTP Status Code**: 200 OK / 401 Unauthorized
- **Cookie**: `refreshToken` (HttpOnly) — 로그인 시 발급된 쿠키가 자동 전송됨

**Request Body**: 없음 — refreshToken은 쿠키에서 읽는다. 쿠키가 없으면 401.

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "토큰 재발급 성공",
  "data": {
  "accessToken": "eyJhbGci...",
  "expiresIn": 1800
 }
 }
```

---

### POST `/api/auth/logout`

로그아웃

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK
- **Cookie**: `refreshToken` (HttpOnly) — 자동 전송됨. Redis의 refresh token을 폐기한다.

**Request Body**: 없음 — refreshToken은 쿠키에서 읽는다.

**Response Headers**

```
Set-Cookie: refreshToken=; Max-Age=0; Path=/api/auth; HttpOnly; Secure; SameSite=Lax
```

> refresh 쿠키를 즉시 만료시켜 브라우저에서 제거한다.

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "로그아웃 성공",
  "data": null
 }
```

---

### POST `/api/users/find-username`

아이디 찾기

- **Request Headers**: 없음 (인증 불필요)
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "name": "홍길동",
  "phone": "01012345678"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "아이디 찾기 성공",
  "data": {
  "maskedUsername": "us***234"
 }
 }
```

---

### POST `/api/users/reset-password`

비밀번호 찾기/재설정

- **Request Headers**: 없음 (인증 불필요)
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "username": "user1234",
  "phone": "01012345678",
  "newPassword": "NewP@ss1!"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "비밀번호 재설정 성공",
  "data": null
 }
```

---

### POST `/api/users/terms`

약관 동의 등록 (약관 항목·termId·본문·버전은 프론트 정적 관리 — 별도 조회 API 없음)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "terms": [
  {"termId": 1, "agreed": true},
  {"termId": 2, "agreed": true}
  ]
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "약관 동의 등록 성공",
  "data": null
 }
```

---

### POST `/api/users/auth-method`

PIN/패턴 설정

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "type": "PIN",
  "value": "123456"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "PIN/패턴 설정 성공",
  "data": null
 }
```

---

### POST `/api/users/account-password`

계좌 비밀번호 설정

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "accountPassword": "1234"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "계좌 비밀번호 설정 성공",
  "data": null
 }
```

---

### POST `/api/users/account-password/verify`

거래 인증 (계좌비번 검증). 거래(환전·CMA 등)는 이 인증으로 통과시키며, 매 거래마다 비번을 받지 않는다.

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized
- **keepAuth("비밀번호 유지" 토글)**: `true`면 검증 성공을 **30분 거래 세션**으로 기억해 이후 거래는 비번 스킵. `false`면 직후 **거래 1건만** 통과하고 소비됨(다음 거래는 재인증; `expiresAt`는 5분 유효창).

**Request Body**

```json
{
  "accountPassword": "1234",
  "keepAuth": true
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "거래 인증 성공",
  "data": {
  "verifiedAt": "2025-06-15T10:00:00",
  "expiresAt": "2025-06-15T10:30:00"
 }
 }
```

---

### PUT `/api/users/me`

회원정보 (비밀번호) 수정

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "currentPassword": "P@ssword1!",
  "newPassword": "NewP@ss1!"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "회원정보 수정 성공",
  "data": null
 }
```

---

## 마이페이지

### GET `/api/users/me/mypage`

마이페이지 집계 조회 (프로필·CMA잔액·퍼즐판 평가·연동계좌·자동모으기 토글).<br>
email은 회원 데이터에 컬럼이 없어 응답에서 제외. `username`은 이름 아래 표시용 로그인 아이디.<br>
금액은 KRW 정수. CMA 미개설 시 `cmaBalance: 0`.

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "마이페이지 조회 성공",
  "data": {
  "name": "김준형",
  "username": "user1234",
  "cmaBalance": 37840,
  "puzzleValuation": 71516,
  "linkedAccounts": [
    { "id": "shinhan-bank", "name": "신한은행", "type": "BANK", "linked": true },
    { "id": "shinhan-card", "name": "신한카드", "type": "CARD", "linked": true },
    { "id": "shinhan-point", "name": "마이신한포인트", "type": "PAY", "linked": true }
  ],
  "settings": { "cardChangeCollect": true, "monthlySavingCollect": true }
 }
 }
```

> `linkedAccounts[].type`은 기관 카테고리 매핑값: `BANK`/`CARD`/`SECURITIES`, 포인트는 `PAY`.<br>
> `puzzleValuation`은 ledger 실시간 집계(보유 × 현재가, 해외는 USD×환율 KRW 환산) — 포트폴리오 화면과 동일 소스.

---

### PATCH `/api/users/me/mypage/settings`

자동 모으기 토글 부분 변경. 변경분만 전송(생략=변경 안 함), 둘 다 생략하면 400.<br>
`cardChangeCollect`는 ledger 적립설정(sourceType=CARD)에 동기화, `monthlySavingCollect`는 현재월 절약금 동의에 반영.

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "cardChangeCollect": false
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "저장되었습니다.",
  "data": { "cardChangeCollect": false, "monthlySavingCollect": true }
 }
```
