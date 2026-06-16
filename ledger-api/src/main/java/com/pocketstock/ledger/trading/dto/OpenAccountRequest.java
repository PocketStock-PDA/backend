package com.pocketstock.ledger.trading.dto;

import java.util.List;

/**
 * 증권계좌 개설 요청. accountTypes ⊆ {DOMESTIC, OVERSEAS}.
 */
public record OpenAccountRequest(List<String> accountTypes) {
}
