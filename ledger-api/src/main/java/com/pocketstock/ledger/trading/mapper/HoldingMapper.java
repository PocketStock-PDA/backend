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
     *
     * <p>{@code fractionalDelta}: 소수점(신탁) 분으로 더할 양 — 소수점 매수·웰컴리워드는 {@code qty},
     * 온주 매수는 0. fractional_qty는 갱신 시 즉시 floor 전환(정수부는 온주로 굳음, <1만 잔류, FRAC-010).
     */
    int upsertBuy(@Param("userId") Long userId,
                  @Param("accountId") Long accountId,
                  @Param("stockCode") String stockCode,
                  @Param("qty") java.math.BigDecimal qty,
                  @Param("price") java.math.BigDecimal price,
                  @Param("krwAmount") java.math.BigDecimal krwAmount,
                  @Param("currency") String currency,
                  @Param("fractionalDelta") java.math.BigDecimal fractionalDelta);

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

    /** 매도가능 수량(quantity − held_quantity). 보유행 없으면 null. (온주·총합 기준) */
    java.math.BigDecimal findAvailableQuantity(@Param("accountId") Long accountId,
                                               @Param("stockCode") String stockCode);

    /** 소수 매도가능(fractional_qty − held_quantity, 보수적 공유 held). 보유행 없으면 null. 소수점 매도 hold 산정용. */
    java.math.BigDecimal findAvailableFractional(@Param("accountId") Long accountId,
                                                 @Param("stockCode") String stockCode);

    /** 소수점 매도 hold — held_quantity += qty, 소수 매도가능(fractional_qty − held_quantity) ≥ qty 가드(소수부 초과 차단). */
    int reserveFractionalForSell(@Param("accountId") Long accountId,
                                 @Param("stockCode") String stockCode,
                                 @Param("qty") java.math.BigDecimal qty);

    /** 소수점 매도 체결 — quantity·fractional_qty 동시 차감 + 음수가드(fractional_qty ≥ qty). 0이면 소수 잔고 부족. */
    int reduceFractionalForSell(@Param("accountId") Long accountId,
                                @Param("stockCode") String stockCode,
                                @Param("qty") java.math.BigDecimal qty);

    /** 유저 보유종목 전체(수량>0) */
    List<Holding> findByUserId(@Param("userId") Long userId);

    /** 특정 종목 보유 1건(account_id·fractional_qty·held_quantity 포함). 온주 전환용. 없으면 null. */
    Holding findByUserIdAndStock(@Param("userId") Long userId, @Param("stockCode") String stockCode);

    /**
     * 온주 전환 — fractional_qty에서 정수부(wholeQty)를 덜어낸다(소수→온주, FRAC-010 #157).
     * 가드: 미체결 매도분(held_quantity) 제외하고 wholeQty만큼 남아야 함(전환 후 fractional_qty − held ≥ 0).
     * quantity는 불변(온주=quantity−fractional_qty가 자동으로 +wholeQty). 0이면 전환 가능분 변경(경합).
     */
    int reduceFractionalForConvert(@Param("accountId") Long accountId,
                                   @Param("stockCode") String stockCode,
                                   @Param("wholeQty") int wholeQty);

    /** 전체 유저 보유 종목 distinct stockCode (배치용) */
    List<String> findAllDistinctStockCodes();
}
