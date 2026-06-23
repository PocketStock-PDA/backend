package com.pocketstock.ledger.trading.dto;

/** 온주 전환 요청 — 해당 종목의 소수점 보유 정수부를 온주(직접소유)로 전환. */
public record WholeShareConvertRequest(String stockCode) {
}
