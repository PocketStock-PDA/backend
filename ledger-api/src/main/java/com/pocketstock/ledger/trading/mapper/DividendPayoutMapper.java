package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.DividendPayout;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DividendPayoutMapper {

    /** 배당 지급 1행 INSERT. (user_id, stock_code, pay_date) UNIQUE — 중복 지급 시 DuplicateKey(멱등). */
    int insert(DividendPayout payout);

    /** 재투자 성공 — status=REINVESTED + order_id + 실매수금액. */
    int markReinvested(@Param("id") Long id,
                       @Param("orderId") Long orderId,
                       @Param("reinvestAmount") BigDecimal reinvestAmount);

    /** 재투자 실패 — status=REINVEST_FAILED + 사유(배당금은 CMA 현금으로 잔류). */
    int markReinvestFailed(@Param("id") Long id, @Param("failReason") String failReason);

    /** 유저의 배당 지급/재투자 내역(종목명 join, 최신순). */
    List<DividendPayout> findByUserId(@Param("userId") Long userId);
}
