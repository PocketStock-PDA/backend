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

### POST `/api/auth/sms/send`

SMS 인증번호 발송

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
  "data": null
 }
```

---

### POST `/api/auth/sms/verify`

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

### POST `/api/auth/shinhan-cert/request`

신한인증서 난수문자 인증요청

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
  "message": "신한인증서 인증요청 성공",
  "data": {
  "requestId": "REQ-20250615-001",
  "randomCode": "A1B2C3",
  "expiresIn": 180
 }
 }
```

---

### POST `/api/auth/shinhan-cert/verify`

신한인증서 인증확인

- **Request Headers**: 없음 (인증 불필요)
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "requestId": "REQ-20250615-001"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "신한인증서 인증 확인 성공",
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

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "로그인 성공",
  "data": {
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
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

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "간편 로그인 성공",
  "data": {
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "expiresIn": 1800
 }
 }
```

---

### POST `/api/auth/refresh`

토큰 재발급 (Refresh)

- **Request Headers**: 없음 (인증 불필요)
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "refreshToken": "eyJhbGci..."
 }
```

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
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "refreshToken": "eyJhbGci..."
 }
```

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
- **keepAuth("비밀번호 유지" 토글)**: `true`면 검증 성공을 **30분 거래 세션**으로 기억해 이후 거래는 비번 스킵. `false`면 이번 1회만 통과하고 세션을 남기지 않음(`expiresAt == verifiedAt`).

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
