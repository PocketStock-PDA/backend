package com.pocketstock.ledger.ls;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * LS 실시간 데이터 프레임을 tr_cd 단위로 받는 도메인 핸들러.
 * 구현체는 {@link #trCd()}로 자신이 처리할 거래코드를 알리고,
 * {@link LsRealtimeClient}가 인바운드 프레임을 해당 핸들러로 라우팅한다.
 * (예: UH1 → 호가, US3 → 체결가) — 도메인별로 한 구현체.
 */
public interface LsRealtimeListener {

    /** 이 핸들러가 처리할 LS 거래코드 (예: "UH1"). */
    String trCd();

    /** LS 실시간 프레임의 body 노드. 구현체가 매핑해 STOMP 토픽으로 push. */
    void onData(JsonNode body);
}
