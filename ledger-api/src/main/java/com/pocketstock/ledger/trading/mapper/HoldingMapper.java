package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.Holding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HoldingMapper {

    /**
     * 매수/적립 — 보유 원자 upsert. 신규면 INSERT, 기존이면 수량 누적 + 평단 가중평균을 한 문장으로.
     * 읽기 없이 원자 갱신이라 동시 매수 lost update·첫 매수 경합(unique)까지 차단.
     */
    int upsertBuy(@Param("userId") Long userId,
                  @Param("accountId") Long accountId,
                  @Param("stockCode") String stockCode,
                  @Param("qty") java.math.BigDecimal qty,
                  @Param("price") java.math.BigDecimal price,
                  @Param("krwAmount") java.math.BigDecimal krwAmount,
                  @Param("currency") String currency);

    /**
     * 매도 — 보유 수량 원자 차감 + 음수 가드(매도가능 = quantity − held_quantity 기준). 평단은 유지.
     * @return 갱신 행 수(0이면 매도가능 부족 또는 보유 없음)
     */
    int reduceForSell(@Param("accountId") Long accountId,
                      @Param("stockCode") String stockCode,
                      @Param("qty") java.math.BigDecimal qty);

    /**
     * 매도 PENDING 진입 시 수량 hold(M2 대칭) — held_quantity += qty, 매도가능 가드.
     * @return 갱신 행 수(0이면 매도가능 부족 또는 보유 없음)
     */
    int reserveForSell(@Param("accountId") Long accountId,
                       @Param("stockCode") String stockCode,
                       @Param("qty") java.math.BigDecimal qty);

    /** 수량 hold 환원 — held_quantity -= qty (취소·미체결 만료). */
    int releaseSellReserve(@Param("accountId") Long accountId,
                           @Param("stockCode") String stockCode,
                           @Param("qty") java.math.BigDecimal qty);

    /** 매도가능 수량(quantity − held_quantity). 보유행 없으면 null. 소수점 전량/금액 매도 hold 산정용. */
    java.math.BigDecimal findAvailableQuantity(@Param("accountId") Long accountId,
                                               @Param("stockCode") String stockCode);

    /** 유저 보유종목 전체(수량>0) */
    List<Holding> findByUserId(@Param("userId") Long userId);

    /** 전체 유저 보유 종목 distinct stockCode (배치용) */
    List<String> findAllDistinctStockCodes();
}
