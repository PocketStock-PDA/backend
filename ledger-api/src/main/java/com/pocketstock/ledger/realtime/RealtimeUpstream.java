package com.pocketstock.ledger.realtime;

/**
 * 실시간 시세 상류(브로커) 공통 인터페이스 — 구독 매니저가 브로커 종류와 무관하게
 * 종목 등록/해제를 호출한다. 구현체: LS(국내 UH1 등), KIS(해외 HDFSASP0 등).
 * trCode는 브로커별 거래코드(LS tr_cd / KIS tr_id), trKey는 등록 키(종목 식별).
 */
public interface RealtimeUpstream {

    /** 로깅·참조계수 식별용 브로커명 (예: "LS", "KIS"). */
    String name();

    /** 종목 실시간 등록(구독자 0→1일 때만 호출됨). */
    void register(String trCode, String trKey);

    /** 종목 실시간 해제(구독자 1→0일 때만 호출됨). */
    void unregister(String trCode, String trKey);
}
