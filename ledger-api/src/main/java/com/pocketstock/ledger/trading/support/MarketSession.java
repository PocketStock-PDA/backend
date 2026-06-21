package com.pocketstock.ledger.trading.support;

/**
 * 미국 시장 세션 상태(vendor 무관 도메인 개념).
 * 주문 접수 가부·실시간 구독·매칭·화면 표시 등에서 공용으로 쓴다.
 *
 * <ul>
 *   <li>{@link #REGULAR} 미국 정규장(한국 밤) — ET 09:30~16:00</li>
 *   <li>{@link #DAY}     미국 주간거래(KIS 데이마켓) — KST 10:00~16:00</li>
 *   <li>{@link #CLOSED}  그 외(장 사이 시간·주말). 휴장일 캘린더는 후속.</li>
 * </ul>
 */
public enum MarketSession {
    REGULAR,
    DAY,
    CLOSED
}
