package com.pocketstock.ledger.recon;

import java.math.BigDecimal;

/**
 * 정합성 검산 1단위(통화 1개 또는 종목 1개)의 결과.
 *
 * @param key      검산 키(통화 KRW/USD 또는 종목코드)
 * @param left     좌변 합(예: 고객 측 변동, journal 합)
 * @param right    우변 합(예: 회사 측 변동, projection 잔액)
 * @param diff     좌·우의 차(보존=left+right, 정합=left−right). 0이어야 정상
 * @param balanced diff == 0
 */
public record ReconLine(String key, BigDecimal left, BigDecimal right, BigDecimal diff, boolean balanced) {
}
