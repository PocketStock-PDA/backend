# PocketStock API 명세 — Notification

> 공통 헤더: `Authorization: Bearer {accessToken}` | `Content-Type: application/json`

## 알림

### GET `/api/notifications`

알림 목록 (알림센터) 조회<br> Query: read (boolean, 선택), page (number, 선택), size (number, 선택)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "알림 목록 조회 성공",
  "data": {
  "notifications": [
  {
  "id": 1,
  "type": "TRADE_FILLED",
  "title": "주문 체결",
  "body": "삼성전자 매수 주문이 체결되었습니다.",
  "isRead": false,
  "createdAt": "2025-06-15T10:30:05"
  }
  ],
  "unreadCount": 3,
  "page": 0,
  "totalElements": 20
 }
 }
```

---

### PATCH `/api/notifications/{id}/read`

알림 읽음 처리<br> Path: {id} - 읽음 처리할 알림 ID

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "알림 읽음 처리 성공",
  "data": {
  "id": 1,
  "isRead": true
 }
 }
```

---

### PATCH `/api/notifications/read-all`

알림 전체 읽음

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "알림 전체 읽음 처리 성공",
  "data": {
  "updatedCount": 3
 }
 }
```

---

### POST `/api/notifications/token`

푸시 토큰 등록 (user_id UNIQUE 기준 upsert — 최초 호출 시 설정 row 생성)

> `token`: 모바일은 FCM 토큰, 웹(PWA)은 Web Push(VAPID) 구독을 `JSON.stringify` 한 문자열.
> `deviceType`: `ANDROID` / `IOS` / `WEB` (platform 컬럼에 저장)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "token": "FCM-TOKEN-xyz | {\"endpoint\":\"...\",\"keys\":{\"p256dh\":\"...\",\"auth\":\"...\"}}",
  "deviceType": "ANDROID"
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "푸시 토큰 등록 성공",
  "data": null
 }
```

---

### PUT `/api/notifications/settings`

알림 수신 설정 (`priceAlert` ↔ `notify_unfilled`(미체결) 컬럼 매핑)

- **Request Headers**: Authorization: Bearer {accessToken}
- **HTTP Status Code**: 200 OK / 400 Bad Request / 401 Unauthorized

**Request Body**

```json
{
  "tradeFilled": true,
  "priceAlert": true,
  "goalNudge": false,
  "marketing": false
 }
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "알림 수신 설정 완료",
  "data": {
  "tradeFilled": true,
  "priceAlert": true,
  "goalNudge": false,
  "marketing": false
 }
 }
```
