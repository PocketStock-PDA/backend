package com.pocketstock.ledger.realtime;

/**
 * 실시간 상류 세션이 끊겼다가 다시 붙은 순간(down→up)을 알리는 이벤트. 첫 연결엔 발행하지 않는다.
 * 소비자는 끊긴 동안 놓쳤을 수 있는 일을 REST 1회 스냅샷으로 보정한다(폴링 루프 아님, 재연결 1발).
 * 예: 온주 지정가 매칭 엔진이 PENDING 종목의 호가를 다시 받아 그 사이 닿은 cross를 체결.
 */
public record RealtimeReconnectedEvent(String broker) {
}
