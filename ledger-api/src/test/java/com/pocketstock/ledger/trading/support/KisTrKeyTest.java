package com.pocketstock.ledger.trading.support;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.ledger.trading.domain.TradableStock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KIS tr_key 조립 검증 — (세션, exchange, 심볼)에서 파생. */
class KisTrKeyTest {

    @Test
    @DisplayName("정규장: D + 정규시장코드 + 심볼 (DNASAAPL)")
    void regular() {
        assertThat(KisTrKey.of(MarketSession.REGULAR, "NASDAQ", "AAPL")).isEqualTo("DNASAAPL");
    }

    @Test
    @DisplayName("주간거래: R + 주간시장코드 + 심볼 (NAS→BAQ, NYS→BAY, AMS→BAA)")
    void day() {
        assertThat(KisTrKey.of(MarketSession.DAY, "NASDAQ", "AAPL")).isEqualTo("RBAQAAPL");
        assertThat(KisTrKey.of(MarketSession.DAY, "NYSE", "BRK")).isEqualTo("RBAYBRK");
        assertThat(KisTrKey.of(MarketSession.DAY, "AMEX", "XYZ")).isEqualTo("RBAAXYZ");
    }

    @Test
    @DisplayName("마감: null 반환(등록 스킵)")
    void closed() {
        assertThat(KisTrKey.of(MarketSession.CLOSED, "NASDAQ", "AAPL")).isNull();
    }

    @Test
    @DisplayName("심볼 없음: 예외")
    void invalidSymbol() {
        assertThatThrownBy(() -> KisTrKey.of(MarketSession.REGULAR, "NASDAQ", null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> KisTrKey.of(MarketSession.REGULAR, "NASDAQ", " "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("매핑 없는 거래소: 예외")
    void unmappedExchange() {
        assertThatThrownBy(() -> KisTrKey.of(MarketSession.DAY, "HKEX", "00003"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("null 입력(세션·종목): NPE 아닌 BusinessException")
    void nullInputs() {
        assertThatThrownBy(() -> KisTrKey.of(null, "NASDAQ", "AAPL"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> KisTrKey.of(MarketSession.REGULAR, (TradableStock) null))
                .isInstanceOf(BusinessException.class);
    }
}
