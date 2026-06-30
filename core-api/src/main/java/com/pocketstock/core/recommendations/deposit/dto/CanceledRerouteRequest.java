package com.pocketstock.core.recommendations.deposit.dto;

/**
 * 배당주 예약 취소분 라우팅 요청 — 취소된 매수금액이 공중분해되지 않게 '남은 자금 굴리기'로 보낸다.
 * 계좌에 활성 rollover(재예치/CMA)가 있으면 그 금액에 합산하고, 없으면 target으로 새로 만든다.
 * target: "CMA"=CMA 이체, 그 외="DEPOSIT"(같은 상품 재예치). 활성 rollover에 합산될 때는 무시된다.
 */
public record CanceledRerouteRequest(
        Long linkedBankAccountId,
        Long amount,
        String target
) {}
