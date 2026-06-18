package com.pocketstock.ledger.kis;

/**
 * KIS 실시간 데이터 프레임을 tr_id 단위로 받는 도메인 핸들러.
 * KIS 데이터 프레임은 JSON이 아니라 캐럿(^) 구분 위치값 배열이라, 파싱된 필드 배열을 넘긴다.
 * 구현체는 {@link #trId()}로 처리할 거래ID를 알리고(예: HDFSASP0 해외호가),
 * {@link KisRealtimeClient}가 인바운드 프레임을 해당 핸들러로 라우팅한다.
 */
public interface KisRealtimeListener {

    /** 이 핸들러가 처리할 KIS 거래ID (예: "HDFSASP0"). */
    String trId();

    /** 캐럿(^)으로 분리된 데이터 필드 배열. 구현체가 위치로 읽어 매핑 후 STOMP push. */
    void onData(String[] fields);
}
