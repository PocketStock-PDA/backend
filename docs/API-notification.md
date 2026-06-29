# PocketStock API 명세 — Notification

> 공통 헤더: `Authorization: Bearer {accessToken}` | `Content-Type: application/json`

## 알림

### GET `/api/notifications` ✅ 구현완료

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
  "tag": "order-1",
  "url": null,
  "occurredAt": "2026-06-29T02:08:00Z",
  "data": {
    "side": "BUY",
    "stockCode": "005930",
    "stockName": "삼성전자",
    "quantity": 1,
    "currency": "KRW",
    "orderId": 1
  },
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

> `tag`, `url`, `occurredAt`, `data`는 구조화 푸시가 있는 알림에서만 채워진다. 기존 폴백 알림은 null일 수 있다.

---

## Web Push payload — 자동매매

자동매매(정기매수·물타기·익절)는 주문 체결 타입과 분리된 전용 type을 사용한다.

- 성공/접수: `AUTO_INVEST_EXECUTED`
- 실패: `AUTO_INVEST_FAILED`
- `trigger`: `PERIODIC | DIP_BUY | TAKE_PROFIT`
- `occurredAt`: ISO-8601 UTC `Z`
- `url`: `/portfolio/detail?stockCode={stockCode}&view=collect`
- 금액/수량 숫자는 raw 값이며 포맷은 FE 담당

```json
{
  "type": "AUTO_INVEST_EXECUTED",
  "title": "물타기 접수",
  "body": "에코프로비엠 10000원 물타기 접수되었어요",
  "tag": "autoinvest-12-3",
  "url": "/portfolio/detail?stockCode=086520&view=collect",
  "occurredAt": "2026-06-29T02:08:00Z",
  "data": {
    "trigger": "DIP_BUY",
    "side": "BUY",
    "stockCode": "086520",
    "stockName": "에코프로비엠",
    "amount": 10000,
    "quantity": null,
    "currency": "KRW",
    "status": "ACCEPTED",
    "reason": null,
    "settingId": 12,
    "roundNo": 3
  }
}
```

```json
{
  "type": "AUTO_INVEST_FAILED",
  "title": "자동모으기 실패",
  "body": "에코프로비엠 자동모으기 실패 (잔액 부족)",
  "tag": "autoinvest-12-4",
  "url": "/portfolio/detail?stockCode=086520&view=collect",
  "occurredAt": "2026-06-29T02:08:00Z",
  "data": {
    "trigger": "PERIODIC",
    "side": "BUY",
    "stockCode": "086520",
    "stockName": "에코프로비엠",
    "amount": null,
    "quantity": null,
    "currency": "KRW",
    "status": "FAILED",
    "reason": "잔액 부족",
    "settingId": 12,
    "roundNo": 4
  }
}
```

### 운영 푸시 검증용 샘플

운영 서버의 `/api/notifications/test`는 요청 본문을 가공 없이 본인 WEB 구독으로 전송한다. `$API`, `$TOKEN`을 운영 값으로 준비한 뒤 한 줄씩 발송한다.

```bash
curl -i -s -X POST "$API/api/notifications/test" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"type":"AUTO_INVEST_EXECUTED","title":"자동모으기 접수","body":"삼성전자 10000원 자동모으기 접수되었어요","tag":"autoinvest-12-1","url":"/portfolio/detail?stockCode=005930&view=collect","occurredAt":"2026-06-29T02:08:00Z","data":{"trigger":"PERIODIC","side":"BUY","stockCode":"005930","stockName":"삼성전자","amount":10000,"quantity":null,"currency":"KRW","status":"ACCEPTED","settingId":12,"roundNo":1}}' | head -8

curl -i -s -X POST "$API/api/notifications/test" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"type":"AUTO_INVEST_EXECUTED","title":"자동모으기 접수","body":"테슬라 $13.2 자동모으기 접수되었어요","tag":"autoinvest-13-1","url":"/portfolio/detail?stockCode=TSLA&view=collect","occurredAt":"2026-06-29T02:09:00Z","data":{"trigger":"PERIODIC","side":"BUY","stockCode":"TSLA","stockName":"테슬라","amount":13.2,"quantity":null,"currency":"USD","status":"ACCEPTED","settingId":13,"roundNo":1}}' | head -8

curl -i -s -X POST "$API/api/notifications/test" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"type":"AUTO_INVEST_EXECUTED","title":"자동모으기 접수","body":"엔비디아 0.025주 자동모으기 접수되었어요","tag":"autoinvest-14-1","url":"/portfolio/detail?stockCode=NVDA&view=collect","occurredAt":"2026-06-29T02:10:00Z","data":{"trigger":"PERIODIC","side":"BUY","stockCode":"NVDA","stockName":"엔비디아","amount":null,"quantity":0.025,"currency":"USD","status":"ACCEPTED","settingId":14,"roundNo":1}}' | head -8

curl -i -s -X POST "$API/api/notifications/test" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"type":"AUTO_INVEST_FAILED","title":"자동모으기 실패","body":"에코프로비엠 자동모으기 실패 (잔액 부족)","tag":"autoinvest-15-2","url":"/portfolio/detail?stockCode=086520&view=collect","occurredAt":"2026-06-29T02:11:00Z","data":{"trigger":"PERIODIC","side":"BUY","stockCode":"086520","stockName":"에코프로비엠","amount":null,"quantity":null,"currency":"KRW","status":"FAILED","reason":"잔액 부족","settingId":15,"roundNo":2}}' | head -8

curl -i -s -X POST "$API/api/notifications/test" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"type":"AUTO_INVEST_EXECUTED","title":"물타기 접수","body":"에코프로비엠 10000원 물타기 접수되었어요","tag":"autoinvest-16-3","url":"/portfolio/detail?stockCode=086520&view=collect","occurredAt":"2026-06-29T02:12:00Z","data":{"trigger":"DIP_BUY","side":"BUY","stockCode":"086520","stockName":"에코프로비엠","amount":10000,"quantity":null,"currency":"KRW","status":"ACCEPTED","settingId":16,"roundNo":3}}' | head -8

curl -i -s -X POST "$API/api/notifications/test" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"type":"AUTO_INVEST_EXECUTED","title":"익절 접수","body":"테슬라 0.01주 익절 접수되었어요","tag":"autoinvest-17-4","url":"/portfolio/detail?stockCode=TSLA&view=collect","occurredAt":"2026-06-29T02:13:00Z","data":{"trigger":"TAKE_PROFIT","side":"SELL","stockCode":"TSLA","stockName":"테슬라","amount":null,"quantity":0.01,"currency":"USD","status":"ACCEPTED","settingId":17,"roundNo":4}}' | head -8

curl -i -s -X POST "$API/api/notifications/test" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"type":"AUTO_INVEST_EXECUTED","title":"자동모으기 접수","body":"005930 10000원 자동모으기 접수되었어요","tag":"autoinvest-18-1","url":"/portfolio/detail?stockCode=005930&view=collect","occurredAt":"2026-06-29T02:14:00Z","data":{"trigger":"PERIODIC","side":"BUY","stockCode":"005930","amount":10000,"quantity":null,"currency":"KRW","status":"ACCEPTED","settingId":18,"roundNo":1}}' | head -8
```

---

### PATCH `/api/notifications/{id}/read` ✅ 구현완료

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

### PATCH `/api/notifications/read-all` ✅ 구현완료

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

### POST `/api/notifications/token` ✅ 구현완료

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

### PUT `/api/notifications/settings` ✅ 구현완료

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
